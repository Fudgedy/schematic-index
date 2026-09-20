package com.fudgedy.schematicindex.gui;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.mixin.RenderSetupAccessor;
import com.fudgedy.schematicindex.mixin.RenderTypeAccessor;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import net.minecraft.client.renderer.SpecialBlockModelRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.SequencedMap;
import java.util.function.Consumer;

// Draws the schematic's real blocks into a cached offscreen framebuffer the GUI blits directly. Drives
// the GL device, so it is render-thread only and every resource below is reused across frames
public final class GpuPreviewRenderer implements PreviewRenderer
{
	private static final int CLEAR_COLOR = 0xFF10151A;

	private static final float Z_NEAR = 0.05F;

	private static final int RENDER_WIDTH = 1920;
	private static final int RENDER_HEIGHT = 1080;

	private @Nullable TextureTarget target;
	private @Nullable PerspectiveProjectionMatrixBuffer projectionBuffer;
	private @Nullable ByteBufferBuilder byteBuilder;
	private @Nullable MultiBufferSource.BufferSource bufferSource;

	// The immediate BufferSource does not sort, so translucent faces reject each other angle-dependently
	private @Nullable ByteBufferBuilder translucentByteBuilder;
	private @Nullable ByteBufferBuilder sortScratch;

	private @Nullable FboTexture registered;

	// Block entities carry empty block models, so renderBatched draws nothing for them
	private @Nullable SubmitNodeStorage submitStorage;
	private @Nullable FeatureRenderDispatcher featureDispatcher;
	private @Nullable SpecialBlockModelRenderer specialRenderer;

	private @Nullable BlockEntityRenderDispatcher beDispatcher;
	private final PreviewCamera previewCamera = new PreviewCamera();
	private final CameraRenderState cameraRenderState = new CameraRenderState();
	// Cached per model so block entities and their resolved player-head skins survive across frames
	private @Nullable SchematicRenderLevel beLevelCache;
	private @Nullable SchematicPreview.Model beLevelModel;
	private @Nullable FogRenderer fogRenderer;
	// Two antipodal light vectors saturate the entity shader's diffuse term on every axis-aligned face
	private @Nullable GpuBuffer flatLightBuffer;
	private @Nullable GpuBufferSlice flatLightSlice;

	private final RandomSource random = RandomSource.create();
	private final List<BlockModelPart> parts = new ArrayList<>();

	// Opaque geometry is camera-independent, so it is meshed once into persistent GPU buffers and replayed
	// each frame; only block entities and translucent faces (order depends on the eye) are rebuilt per frame
	private final SequencedMap<RenderType, Baked> bakedTypes = new LinkedHashMap<>();
	private final List<BlockPos> beCells = new ArrayList<>();
	private final List<BlockPos> translucentCells = new ArrayList<>();
	private @Nullable SchematicPreview.Model bakedModel;
	private int bakedLayerCeiling = -1;

	// A baked render type: its uploaded vertices plus the index buffer draw() would have built for one frame
	private record Baked(GpuBuffer vertex, @Nullable GpuBuffer index,
			VertexFormat.IndexType indexType, int indexCount, VertexFormat.Mode mode)
	{
	}

	// LiquidBlockRenderer.tesselate emits fluid at pos & 15; the blocks here sit at full model coordinates
	private static final class OffsetConsumer implements VertexConsumer
	{
		private final VertexConsumer delegate;
		private final float dx;
		private final float dy;
		private final float dz;

		OffsetConsumer(VertexConsumer delegate, float dx, float dy, float dz)
		{
			this.delegate = delegate;
			this.dx = dx;
			this.dy = dy;
			this.dz = dz;
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z)
		{
			this.delegate.addVertex(x + this.dx, y + this.dy, z + this.dz);
			return this;
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a)
		{
			this.delegate.setColor(r, g, b, a);
			return this;
		}

		@Override
		public VertexConsumer setColor(int argb)
		{
			this.delegate.setColor(argb);
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v)
		{
			this.delegate.setUv(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v)
		{
			this.delegate.setUv1(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v)
		{
			this.delegate.setUv2(u, v);
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z)
		{
			this.delegate.setNormal(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float width)
		{
			this.delegate.setLineWidth(width);
			return this;
		}
	}

	private static RenderType movingBlockType(ChunkSectionLayer layer)
	{
		return switch (layer)
		{
			case SOLID -> RenderTypes.solidMovingBlock();
			case CUTOUT -> RenderTypes.cutoutMovingBlock();
			case TRIPWIRE -> RenderTypes.tripwireMovingBlock();
			default -> RenderTypes.translucentMovingBlock();
		};
	}

	@Override
	public String id()
	{
		return "minecraft";
	}

	@Override
	public boolean requiresRenderThread()
	{
		return true;
	}

	@Override
	public boolean rendersToTexture()
	{
		return true;
	}

	@Override
	public int textureWidth()
	{
		return RENDER_WIDTH;
	}

	@Override
	public int textureHeight()
	{
		return RENDER_HEIGHT;
	}

	@Override
	public NativeImage render(SchematicPreview.Model model, SchematicPreview.View view, @Nullable NativeImage reuse)
	{
		throw new UnsupportedOperationException("GpuPreviewRenderer reads frames back through renderAsync()");
	}

	// Draws into a private target so the on-screen frame survives, then reads it back once the GPU fence
	// signals: a fence created this submit cannot be waited on, so sink runs on a later frame
	@Override
	public void renderAsync(SchematicPreview.Model model, SchematicPreview.View view, @Nullable NativeImage reuse,
			Consumer<NativeImage> sink)
	{
		if (reuse != null)
		{
			reuse.close();
		}

		ensureResources();
		TextureTarget capture = new TextureTarget("schematicindex-capture", RENDER_WIDTH, RENDER_HEIGHT, true);
		GpuDevice device = RenderSystem.getDevice();
		GpuBuffer readback = device.createBuffer(() -> "schematicindex-capture/readback",
				GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
				(long) RENDER_WIDTH * RENDER_HEIGHT * capture.getColorTexture().getFormat().pixelSize());

		try
		{
			draw(model, view, capture, null);
			device.createCommandEncoder().copyTextureToBuffer(capture.getColorTexture(), readback, 0L,
					() -> sink.accept(readPixels(readback, capture)), 0);
		}
		catch (Exception e)
		{
			readback.close();
			capture.destroyBuffers();
			throw e;
		}
	}

	// Runs on the render thread from RenderSystem.executePendingTasks; the mapped rows are bottom-up
	private static @Nullable NativeImage readPixels(GpuBuffer readback, TextureTarget capture)
	{
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, RENDER_WIDTH, RENDER_HEIGHT, false);

		try (GpuBuffer.MappedView view = RenderSystem.getDevice().createCommandEncoder().mapBuffer(readback, true, false))
		{
			ByteBuffer data = view.data();

			for (int y = 0; y < RENDER_HEIGHT; y++)
			{
				int row = (RENDER_HEIGHT - 1 - y) * RENDER_WIDTH;

				for (int x = 0; x < RENDER_WIDTH; x++)
				{
					image.setPixelABGR(x, y, data.getInt((row + x) * 4) | 0xFF000000);
				}
			}

			return image;
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.warn("Preview readback failed", e);
			image.close();
			return null;
		}
		finally
		{
			readback.close();
			capture.destroyBuffers();
		}
	}

	@Override
	public boolean renderToTexture(SchematicPreview.Model model, SchematicPreview.View view, Identifier id)
	{
		ensureResources();
		draw(model, view, this.target, id);
		return true;
	}

	// id is null for a capture, which has no texture to register
	private void draw(SchematicPreview.Model model, SchematicPreview.View view, TextureTarget fbo,
			@Nullable Identifier id)
	{
		Minecraft client = Minecraft.getInstance();
		PreviewLevel level = new PreviewLevel(model);

		if (this.beLevelCache == null || this.beLevelModel != model)
		{
			this.beLevelCache = SchematicRenderLevel.create(model);
			this.beLevelModel = model;
		}

		SchematicRenderLevel beLevel = this.beLevelCache;

		// Re-mesh the opaque geometry only when the build or its cutaway height changes, never per view
		int layerCeiling = view.maxLayer() >= 1.0F
				? model.sizeY()
				: Math.max(1, Math.round(view.maxLayer() * model.sizeY()));

		if (this.bakedModel != model || this.bakedLayerCeiling != layerCeiling)
		{
			bake(client, model, level, layerCeiling);
		}

		// Same basis IndexScreen.tickSpectator moves the free camera with, or WASD flies off-axis from the look
		double yaw = Math.toRadians(view.yaw());
		double pitch = Math.toRadians(view.pitch());
		double dirX = Math.sin(yaw) * Math.cos(pitch);
		double dirY = -Math.sin(pitch);
		double dirZ = Math.cos(yaw) * Math.cos(pitch);

		double centreX = model.sizeX() / 2.0D;
		double centreY = model.sizeY() / 2.0D;
		double centreZ = model.sizeZ() / 2.0D;

		double distance = model.radius() * 2.0D / Math.max(0.05D, view.zoom());

		if (!view.cutaway())
		{
			distance = Math.max(distance, model.radius() + 2.0D);
		}

		double eyeX;
		double eyeY;
		double eyeZ;

		if (view.freeLook())
		{
			eyeX = view.eyeX();
			eyeY = view.eyeY();
			eyeZ = view.eyeZ();
		}
		else
		{
			eyeX = centreX - dirX * distance;
			eyeY = centreY - dirY * distance;
			eyeZ = centreZ - dirZ * distance;
		}

		float zFar = (float) (distance + model.radius() * 4.0D + 100.0D);
		Matrix4f projection = new Matrix4f().perspective(
				(float) Math.toRadians(view.fov()),
				(float) RENDER_WIDTH / (float) RENDER_HEIGHT,
				Z_NEAR, zFar);

		GpuTextureView previousColorOverride = RenderSystem.outputColorTextureOverride;
		GpuTextureView previousDepthOverride = RenderSystem.outputDepthTextureOverride;
		GpuBufferSlice previousFog = RenderSystem.getShaderFog();
		GpuBufferSlice previousLights = RenderSystem.getShaderLights();
		Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
		boolean projectionBackedUp = false;
		boolean modelViewPushed = false;

		try
		{
			GpuDevice device = RenderSystem.getDevice();
			CommandEncoder encoder = device.createCommandEncoder();

			encoder.clearColorAndDepthTextures(fbo.getColorTexture(), CLEAR_COLOR, fbo.getDepthTexture(), 1.0D);

			RenderSystem.outputColorTextureOverride = fbo.getColorTextureView();
			RenderSystem.outputDepthTextureOverride = fbo.getDepthTextureView();

			RenderSystem.backupProjectionMatrix();
			projectionBackedUp = true;
			GpuBufferSlice projectionSlice = this.projectionBuffer.getBuffer(projection);
			RenderSystem.setProjectionMatrix(projectionSlice, ProjectionType.PERSPECTIVE);

			// Entity render types apply the shader fog, so without this they wash toward whatever the GUI left bound
			RenderSystem.setShaderFog(this.fogRenderer.getBuffer(FogRenderer.FogMode.NONE));

			// Rows right, up, -forward: determinant +1, so back-face winding stays correct
			double fx = dirX, fy = dirY, fz = dirZ;
			double rx = fy * 0.0D - fz * 1.0D;   // forward x (0,1,0)
			double ry = fz * 0.0D - fx * 0.0D;
			double rz = fx * 1.0D - fy * 0.0D;
			double rLen = Math.sqrt(rx * rx + ry * ry + rz * rz);
			rx /= rLen; ry /= rLen; rz /= rLen;
			double ux = ry * fz - rz * fy;        // right x forward
			double uy = rz * fx - rx * fz;
			double uz = rx * fy - ry * fx;

			double tR = -(rx * eyeX + ry * eyeY + rz * eyeZ);
			double tU = -(ux * eyeX + uy * eyeY + uz * eyeZ);
			double tF = fx * eyeX + fy * eyeY + fz * eyeZ;   // row is -forward, so this is +(forward.eye)

			Matrix4f viewMatrix = new Matrix4f(
					(float) rx, (float) ux, (float) -fx, 0.0F,
					(float) ry, (float) uy, (float) -fy, 0.0F,
					(float) rz, (float) uz, (float) -fz, 0.0F,
					(float) tR, (float) tU, (float) tF, 1.0F);

			modelViewStack.pushMatrix();
			modelViewPushed = true;
			modelViewStack.set(viewMatrix);

			client.gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_FLAT);
			RenderSystem.setShaderLights(this.flatLightSlice);

			MultiBufferSource.BufferSource buffers = this.bufferSource;
			PoseStack poseStack = new PoseStack();

			RenderType translucentType = RenderTypes.translucentMovingBlock();
			BufferBuilder translucentBuilder = new BufferBuilder(
					this.translucentByteBuilder, translucentType.mode(), translucentType.format());

			if (beLevel != null)
			{
				Vec3 centre = new Vec3(centreX, centreY, centreZ);
				this.previewCamera.placeAt(centre);
				this.beDispatcher.prepare(this.previewCamera);
				this.cameraRenderState.initialized = true;
				this.cameraRenderState.pos = centre;
				this.cameraRenderState.entityPos = centre;
				this.cameraRenderState.blockPos = BlockPos.containing(centre);
				this.cameraRenderState.orientation = new Quaternionf();
			}

			if (id != null)
			{
				registerTexture(client, id, fbo);
			}

			try
			{
				for (Map.Entry<RenderType, Baked> entry : this.bakedTypes.entrySet())
				{
					drawCached(entry.getKey(), entry.getValue());
				}

				drawDynamic(client, model, level, beLevel, poseStack, translucentBuilder);

				try
				{
					this.featureDispatcher.renderAllFeatures();
				}
				catch (Throwable e)
				{
					SchematicIndexMod.LOGGER.warn("Block-entity rendering failed; drawing the preview without it", e);
				}

				buffers.endBatch();
				this.submitStorage.endFrame();

				MeshData translucentMesh = translucentBuilder.build();

				if (translucentMesh != null)
				{
					translucentMesh.sortQuads(this.sortScratch,
							VertexSorting.byDistance((float) eyeX, (float) eyeY, (float) eyeZ));
					translucentType.draw(translucentMesh);
					translucentMesh.close();
				}
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.warn("Preview draw failed; showing a partial frame", e);

				try
				{
					buffers.endBatch();
				}
				catch (Throwable ignored)
				{
				}
			}
		}
		finally
		{
			if (modelViewPushed)
			{
				modelViewStack.popMatrix();
			}

			if (projectionBackedUp)
			{
				RenderSystem.restoreProjectionMatrix();
			}

			RenderSystem.outputColorTextureOverride = previousColorOverride;
			RenderSystem.outputDepthTextureOverride = previousDepthOverride;
			RenderSystem.setShaderFog(previousFog);
			RenderSystem.setShaderLights(previousLights);
		}
	}

	// Meshes every opaque face once into per-type GPU buffers; block entities and translucent cells are only
	// recorded here and rebuilt each frame, since their output depends on the camera the bake cannot know
	private void bake(Minecraft client, SchematicPreview.Model model, PreviewLevel level, int layerCeiling)
	{
		freeBaked();
		this.beCells.clear();
		this.translucentCells.clear();

		SequencedMap<RenderType, BufferBuilder> builders = new LinkedHashMap<>();
		SequencedMap<RenderType, ByteBufferBuilder> bytes = new LinkedHashMap<>();
		PoseStack poseStack = new PoseStack();

		BlockState[] states = model.states();
		int sizeX = model.sizeX();
		int sizeY = model.sizeY();
		int sizeZ = model.sizeZ();

		for (int y = 0; y < layerCeiling; y++)
		{
			for (int z = 0; z < sizeZ; z++)
			{
				for (int x = 0; x < sizeX; x++)
				{
					int cell = model.at(x, y, z);

					if (cell == 0)
					{
						continue;
					}

					BlockState state = cell - 1 < states.length ? states[cell - 1] : null;

					if (state == null || state.isAir())
					{
						continue;
					}

					BlockPos pos = new BlockPos(x, y, z);

					try
					{
						if (state.hasBlockEntity())
						{
							this.beCells.add(pos);
						}

						boolean deferred = false;

						// The position-seeded random is what makes randomised and rotated variants match the world
						if (state.getRenderShape() != RenderShape.INVISIBLE)
						{
							ChunkSectionLayer layer = ItemBlockRenderTypes.getChunkRenderType(state);

							if (layer == ChunkSectionLayer.TRANSLUCENT)
							{
								deferred = true;
							}
							else
							{
								BufferBuilder consumer = builderFor(movingBlockType(layer), builders, bytes);
								this.random.setSeed(state.getSeed(pos));
								this.parts.clear();
								client.getBlockRenderer().getBlockModel(state).collectParts(this.random, this.parts);

								poseStack.pushPose();
								poseStack.translate(x, y, z);
								client.getBlockRenderer().renderBatched(state, pos, level, poseStack, consumer,
										true, this.parts);
								poseStack.popPose();
							}
						}

						FluidState fluid = state.getFluidState();

						if (!fluid.isEmpty())
						{
							ChunkSectionLayer fluidLayer = ItemBlockRenderTypes.getRenderLayer(fluid);

							if (fluidLayer == ChunkSectionLayer.TRANSLUCENT)
							{
								deferred = true;
							}
							else
							{
								VertexConsumer fluidConsumer = new OffsetConsumer(
										builderFor(movingBlockType(fluidLayer), builders, bytes),
										x & ~15, y & ~15, z & ~15);
								client.getBlockRenderer().renderLiquid(pos, level, fluidConsumer, state, fluid);
							}
						}

						if (deferred)
						{
							this.translucentCells.add(pos);
						}
					}
					catch (Throwable e)
					{
						// A throw mid-cell must not leave the PoseStack unbalanced for the next cell
						if (!poseStack.isEmpty())
						{
							poseStack.popPose();
						}

						SchematicIndexMod.LOGGER.debug("Skipping unsupported block {} in GPU preview", state, e);
					}
				}
			}
		}

		GpuDevice device = RenderSystem.getDevice();

		for (Map.Entry<RenderType, BufferBuilder> entry : builders.entrySet())
		{
			MeshData mesh = entry.getValue().build();

			if (mesh == null)
			{
				continue;
			}

			try
			{
				this.bakedTypes.put(entry.getKey(), upload(device, mesh));
			}
			finally
			{
				mesh.close();
			}
		}

		// The build() calls copied every vertex into the GPU buffers, so the staging memory is done
		for (ByteBufferBuilder buffer : bytes.values())
		{
			buffer.close();
		}

		this.bakedModel = model;
		this.bakedLayerCeiling = layerCeiling;
	}

	private BufferBuilder builderFor(RenderType type,
			SequencedMap<RenderType, BufferBuilder> builders,
			SequencedMap<RenderType, ByteBufferBuilder> bytes)
	{
		return builders.computeIfAbsent(type, rt ->
		{
			ByteBufferBuilder buffer = new ByteBufferBuilder(rt.bufferSize());
			bytes.put(rt, buffer);
			return new BufferBuilder(buffer, rt.mode(), rt.format());
		});
	}

	// Uploads a finished mesh to persistent device buffers, mirroring what RenderType.draw does transiently
	private Baked upload(GpuDevice device, MeshData mesh)
	{
		MeshData.DrawState draw = mesh.drawState();
		GpuBuffer vertex = device.createBuffer(() -> "schematicindex-preview-vertices",
				GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());

		ByteBuffer indices = mesh.indexBuffer();
		GpuBuffer index = indices == null ? null
				: device.createBuffer(() -> "schematicindex-preview-indices", GpuBuffer.USAGE_INDEX, indices);

		return new Baked(vertex, index, draw.indexType(), draw.indexCount(), draw.mode());
	}

	// Replays RenderType.draw against a cached vertex buffer: same pass setup, minus the per-frame upload
	private void drawCached(RenderType type, Baked baked)
	{
		RenderSetup setup = ((RenderTypeAccessor) (Object) type).schematicindex$state();
		RenderSetupAccessor state = (RenderSetupAccessor) (Object) setup;

		// draw() skips the push entirely when a type carries no layering modifier; the moving-block types do
		Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
		Consumer<Matrix4fStack> layering = state.schematicindex$layeringTransform().getModifier();
		boolean layered = layering != null;

		if (layered)
		{
			modelViewStack.pushMatrix();
			layering.accept(modelViewStack);
		}

		try
		{
			GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(
					RenderSystem.getModelViewMatrix(),
					new Vector4f(1.0F, 1.0F, 1.0F, 1.0F),
					new Vector3f(),
					state.schematicindex$textureTransform().getMatrix());

			GpuBuffer indexBuffer;
			VertexFormat.IndexType indexType;

			// draw() only builds a shared sequential index buffer when the mesh had no explicit one
			if (baked.index() != null)
			{
				indexBuffer = baked.index();
				indexType = baked.indexType();
			}
			else
			{
				var sequential = RenderSystem.getSequentialBuffer(baked.mode());
				indexBuffer = sequential.getBuffer(baked.indexCount());
				indexType = sequential.type();
			}

			RenderTarget renderTarget = state.schematicindex$outputTarget().getRenderTarget();
			GpuTextureView colorView = RenderSystem.outputColorTextureOverride != null
					? RenderSystem.outputColorTextureOverride
					: renderTarget.getColorTextureView();
			GpuTextureView depthView = renderTarget.useDepth
					? (RenderSystem.outputDepthTextureOverride != null
							? RenderSystem.outputDepthTextureOverride
							: renderTarget.getDepthTextureView())
					: null;

			CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

			try (RenderPass pass = encoder.createRenderPass(() -> "schematicindex-preview-cached",
					colorView, OptionalInt.empty(), depthView, OptionalDouble.empty()))
			{
				pass.setPipeline(state.schematicindex$pipeline());

				ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();

				if (scissor.enabled())
				{
					pass.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
				}

				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("DynamicTransforms", transform);
				pass.setVertexBuffer(0, baked.vertex());

				for (var texture : setup.getTextures().entrySet())
				{
					pass.bindTexture(texture.getKey(), texture.getValue().textureView(), texture.getValue().sampler());
				}

				pass.setIndexBuffer(indexBuffer, indexType);
				pass.drawIndexed(0, 0, baked.indexCount(), 1);
			}
		}
		finally
		{
			if (layered)
			{
				modelViewStack.popMatrix();
			}
		}
	}

	// Only the cells the bake flagged: block entities animate and translucent faces must sort against the eye
	private void drawDynamic(Minecraft client, SchematicPreview.Model model, PreviewLevel level,
			@Nullable SchematicRenderLevel beLevel, PoseStack poseStack, BufferBuilder translucentBuilder)
	{
		for (BlockPos pos : this.beCells)
		{
			BlockState state = stateAt(model, pos);

			if (state != null)
			{
				drawBlockEntity(beLevel, state, pos, pos.getX(), pos.getY(), pos.getZ(), poseStack);
			}
		}

		for (BlockPos pos : this.translucentCells)
		{
			BlockState state = stateAt(model, pos);

			if (state == null)
			{
				continue;
			}

			int x = pos.getX();
			int y = pos.getY();
			int z = pos.getZ();

			try
			{
				if (state.getRenderShape() != RenderShape.INVISIBLE
						&& ItemBlockRenderTypes.getChunkRenderType(state) == ChunkSectionLayer.TRANSLUCENT)
				{
					this.random.setSeed(state.getSeed(pos));
					this.parts.clear();
					client.getBlockRenderer().getBlockModel(state).collectParts(this.random, this.parts);

					poseStack.pushPose();
					poseStack.translate(x, y, z);
					client.getBlockRenderer().renderBatched(state, pos, level, poseStack, translucentBuilder,
							true, this.parts);
					poseStack.popPose();
				}

				FluidState fluid = state.getFluidState();

				if (!fluid.isEmpty()
						&& ItemBlockRenderTypes.getRenderLayer(fluid) == ChunkSectionLayer.TRANSLUCENT)
				{
					VertexConsumer fluidConsumer = new OffsetConsumer(translucentBuilder,
							x & ~15, y & ~15, z & ~15);
					client.getBlockRenderer().renderLiquid(pos, level, fluidConsumer, state, fluid);
				}
			}
			catch (Throwable e)
			{
				if (!poseStack.isEmpty())
				{
					poseStack.popPose();
				}

				SchematicIndexMod.LOGGER.debug("Skipping unsupported block {} in GPU preview", state, e);
			}
		}
	}

	private static @Nullable BlockState stateAt(SchematicPreview.Model model, BlockPos pos)
	{
		int cell = model.at(pos.getX(), pos.getY(), pos.getZ());

		if (cell == 0)
		{
			return null;
		}

		BlockState[] states = model.states();
		BlockState state = cell - 1 < states.length ? states[cell - 1] : null;
		return state == null || state.isAir() ? null : state;
	}

	private void freeBaked()
	{
		for (Baked baked : this.bakedTypes.values())
		{
			baked.vertex().close();

			if (baked.index() != null)
			{
				baked.index().close();
			}
		}

		this.bakedTypes.clear();
	}

	// The item-form fallback ignores facing and NBT, so it is used only when no client level exists
	private void drawBlockEntity(@Nullable SchematicRenderLevel beLevel, BlockState state, BlockPos pos,
			int x, int y, int z, PoseStack poseStack)
	{
		if (beLevel != null)
		{
			BlockEntity be = beLevel.getBlockEntity(pos);

			if (be != null)
			{
				// tryExtractRenderState gates on shouldRender, whose cull rejects everything in an offscreen pass
				try
				{
					@SuppressWarnings("rawtypes")
					BlockEntityRenderer renderer = this.beDispatcher.getRenderer(be);

					if (renderer != null && be.hasLevel() && be.getType().isValid(be.getBlockState()))
					{
						BlockEntityRenderState renderState = renderer.createRenderState();
						renderer.extractRenderState(be, renderState, 0.0F, this.cameraRenderState.pos, null);
						poseStack.pushPose();
						poseStack.translate(x, y, z);
						this.beDispatcher.submit(renderState, poseStack, this.submitStorage, this.cameraRenderState);
						poseStack.popPose();
					}
				}
				catch (Throwable e)
				{
					SchematicIndexMod.LOGGER.debug("Block entity {} failed to extract", be, e);
				}

				return;
			}
		}

		poseStack.pushPose();
		poseStack.translate(x, y, z);
		this.specialRenderer.renderByBlock(state.getBlock(), ItemDisplayContext.NONE,
				poseStack, this.submitStorage, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
		poseStack.popPose();
	}

	// Re-registered every render: a resource or pipeline reload drops manually-registered textures
	private void registerTexture(Minecraft client, Identifier id, TextureTarget fbo)
	{
		if (this.registered == null)
		{
			this.registered = new FboTexture();
		}

		client.getTextureManager().register(id, this.registered);
		this.registered.wrap(fbo);
	}

	// Borrows the framebuffer's colour texture, so close() only detaches: the TextureTarget owns it
	private static final class FboTexture extends AbstractTexture
	{
		void wrap(TextureTarget fbo)
		{
			this.texture = fbo.getColorTexture();
			this.textureView = fbo.getColorTextureView();

			if (this.sampler == null)
			{
				this.sampler = RenderSystem.getSamplerCache()
						.getClampToEdge(FilterMode.LINEAR);
			}
		}

		@Override
		public void close()
		{
			this.texture = null;
			this.textureView = null;
		}
	}

	private static final class PreviewCamera extends Camera
	{
		void placeAt(Vec3 pos)
		{
			setPosition(pos);
		}
	}

	// Every render type gets its own buffer: types not pre-registered land on the shared fallback buffer of a
	// normal immediate source and render white in this offscreen pass
	private static final class PerTypeBufferSource extends MultiBufferSource.BufferSource
	{
		private PerTypeBufferSource(ByteBufferBuilder shared,
				SequencedMap<RenderType, ByteBufferBuilder> fixed)
		{
			super(shared, fixed);
		}

		@Override
		public VertexConsumer getBuffer(RenderType renderType)
		{
			this.fixedBuffers.computeIfAbsent(renderType, rt -> new ByteBufferBuilder(rt.bufferSize()));
			return super.getBuffer(renderType);
		}
	}

	private void ensureResources()
	{
		if (this.target == null)
		{
			this.target = new TextureTarget("schematicindex-preview", RENDER_WIDTH, RENDER_HEIGHT, true);
		}

		if (this.projectionBuffer == null)
		{
			this.projectionBuffer = new PerspectiveProjectionMatrixBuffer("schematicindex-preview");
		}

		if (this.bufferSource == null)
		{
			this.bufferSource = new PerTypeBufferSource(new ByteBufferBuilder(256),
					new LinkedHashMap<>());
		}

		if (this.fogRenderer == null)
		{
			this.fogRenderer = new FogRenderer();
		}

		if (this.flatLightSlice == null)
		{
			GpuDevice device = RenderSystem.getDevice();

			try (MemoryStack stack = MemoryStack.stackPush())
			{
				ByteBuffer data = Std140Builder
						.onStack(stack, Lighting.UBO_SIZE)
						.putVec3(new Vector3f(5.0F, 5.0F, 5.0F))
						.putVec3(new Vector3f(-5.0F, -5.0F, -5.0F))
						.get();
				this.flatLightBuffer = device.createBuffer(() -> "schematicindex-flatlight",
						GpuBuffer.USAGE_UNIFORM, data);
			}

			this.flatLightSlice = this.flatLightBuffer.slice(0L, Lighting.UBO_SIZE);
		}

		if (this.translucentByteBuilder == null)
		{
			this.translucentByteBuilder = new ByteBufferBuilder(4 * 1024 * 1024);
			this.sortScratch = new ByteBufferBuilder(4 * 1024 * 1024);
		}

		if (this.featureDispatcher == null)
		{
			Minecraft client = Minecraft.getInstance();
			this.submitStorage = new SubmitNodeStorage();
			this.featureDispatcher = new FeatureRenderDispatcher(
					this.submitStorage,
					client.getBlockRenderer(),
					this.bufferSource,
					client.getAtlasManager(),
					client.renderBuffers().outlineBufferSource(),
					client.renderBuffers().crumblingBufferSource(),
					client.font);
			this.specialRenderer = client.getModelManager().specialBlockModelRenderer();
			this.beDispatcher = client.getBlockEntityRenderDispatcher();
		}
	}

	@Override
	public void close()
	{
		if (this.target != null)
		{
			this.target.destroyBuffers();
			this.target = null;
		}

		this.registered = null;

		if (this.projectionBuffer != null)
		{
			this.projectionBuffer.close();
			this.projectionBuffer = null;
		}

		if (this.byteBuilder != null)
		{
			this.byteBuilder.close();
			this.byteBuilder = null;
			this.bufferSource = null;
		}

		if (this.translucentByteBuilder != null)
		{
			this.translucentByteBuilder.close();
			this.translucentByteBuilder = null;
			this.sortScratch.close();
			this.sortScratch = null;
		}

		if (this.featureDispatcher != null)
		{
			this.featureDispatcher.close();
			this.featureDispatcher = null;
			this.submitStorage = null;
			this.specialRenderer = null;
		}

		if (this.fogRenderer != null)
		{
			this.fogRenderer.close();
			this.fogRenderer = null;
		}

		if (this.flatLightBuffer != null)
		{
			this.flatLightBuffer.close();
			this.flatLightBuffer = null;
			this.flatLightSlice = null;
		}

		freeBaked();
		this.bakedModel = null;
		this.bakedLayerCeiling = -1;

		this.beLevelCache = null;
		this.beLevelModel = null;
	}
}

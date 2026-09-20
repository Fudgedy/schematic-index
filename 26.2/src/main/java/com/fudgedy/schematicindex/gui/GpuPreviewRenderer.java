package com.fudgedy.schematicindex.gui;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import fi.dy.masa.malilib.render.MaLiLibPipelines;
import fi.dy.masa.malilib.render.uniform.ChunkFixUniform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;

// Draws the schematic's real blocks into a cached offscreen framebuffer the GUI blits directly, render thread
// only. MultiBufferSource and BlockRenderDispatcher are gone here, so geometry is meshed by hand per layer
public final class GpuPreviewRenderer implements PreviewRenderer
{
	private static final int CLEAR_COLOR = 0xFF10151A;

	private static final float Z_NEAR = 0.05F;

	private static final int RENDER_WIDTH = 1920;
	private static final int RENDER_HEIGHT = 1080;

	// Feature renderers draw to OutputTarget.MAIN_TARGET, which ignores the output override, so
	// GameRendererMixin swaps mainRenderTarget() for this. Set only for the block-entity pass
	public static volatile RenderTarget REDIRECT_MAIN_TARGET;

	private @Nullable TextureTarget target;
	private @Nullable FboTexture registered;
	private @Nullable ModelBlockRenderer modelRenderer;
	private @Nullable FluidRenderer fluidRenderer;
	private @Nullable BlockStateModelSet blockModels;
	private @Nullable FogRenderer fogRenderer;
	// Atlas dimensions, required by the LEGACY_*_TERRAIN pipelines' shader
	private final ChunkFixUniform chunkFix = new ChunkFixUniform();

	// Two antipodal light vectors saturate the entity shader's diffuse term on every axis-aligned face
	private @Nullable GpuBuffer flatLightBuffer;
	private @Nullable GpuBufferSlice flatLightSlice;

	private @Nullable BlockEntityRenderDispatcher beDispatcher;
	private @Nullable SubmitNodeStorage submitStorage;
	private @Nullable FeatureRenderDispatcher featureDispatcher;
	private @Nullable RenderBuffers beRenderBuffers;
	private @Nullable GameRenderState gameRenderState;
	private final CameraRenderState cameraRenderState = new CameraRenderState();
	private @Nullable SchematicRenderLevel beLevelCache;
	private @Nullable SchematicPreview.Model beLevelModel;
	private final List<BlockEntity> beCells = new ArrayList<>();

	private final EnumMap<ChunkSectionLayer, ByteBufferBuilder> arenas = new EnumMap<>(ChunkSectionLayer.class);
	private @Nullable ByteBufferBuilder sortScratch;

	// Only the camera moves between frames, so geometry is meshed and uploaded once per model and cutaway
	// layer. Translucent is sorted at mesh time; a stale sort while orbiting is the accepted tradeoff
	private record CachedGeometry(GpuBuffer vbo, int indexCount) {}

	private final EnumMap<ChunkSectionLayer, CachedGeometry> geometryCache = new EnumMap<>(ChunkSectionLayer.class);
	private @Nullable SchematicPreview.Model cacheModel;
	private int cacheLayerCeiling = Integer.MIN_VALUE;

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
		TextureTarget capture = new TextureTarget("schematicindex-capture", RENDER_WIDTH, RENDER_HEIGHT, true,
				GpuFormat.RGBA8_UNORM);
		GpuDevice device = RenderSystem.getDevice();
		GpuBuffer readback = device.createBuffer(() -> "schematicindex-capture/readback",
				GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST,
				(long) RENDER_WIDTH * RENDER_HEIGHT * capture.getColorTexture().getFormat().blockSize());

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

		try (GpuBufferSlice.MappedView view = readback.map(true, false))
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

	// The vanilla terrain pipelines do not render standalone; malilib's offscreen variants do
	private static RenderPipeline legacyPipeline(ChunkSectionLayer layer)
	{
		return switch (layer)
		{
			case SOLID -> MaLiLibPipelines.LEGACY_SOLID_TERRAIN;
			case TRANSLUCENT -> MaLiLibPipelines.LEGACY_TRANSLUCENT;
			default -> MaLiLibPipelines.LEGACY_CUTOUT_TERRAIN;
		};
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
		// The terrain pipeline expects the game's reversed-Z projection; a standard perspective let far
		// surfaces win the depth test and showed the back faces of blocks
		Projection projection = new Projection();
		// setupPerspective takes degrees and converts internally
		projection.setupPerspective(Z_NEAR, zFar, (float) view.fov(), RENDER_WIDTH, RENDER_HEIGHT);

		// Rows right, up, -forward: determinant +1, so back-face winding stays correct
		double fx = dirX, fy = dirY, fz = dirZ;
		double rx = -fz, ry = 0.0D, rz = fx;
		double rLen = Math.sqrt(rx * rx + ry * ry + rz * rz);
		rx /= rLen; ry /= rLen; rz /= rLen;
		double ux = ry * fz - rz * fy;
		double uy = rz * fx - rx * fz;
		double uz = rx * fy - ry * fx;

		double tR = -(rx * eyeX + ry * eyeY + rz * eyeZ);
		double tU = -(ux * eyeX + uy * eyeY + uz * eyeZ);
		double tF = fx * eyeX + fy * eyeY + fz * eyeZ;

		Matrix4f viewMatrix = new Matrix4f(
				(float) rx, (float) ux, (float) -fx, 0.0F,
				(float) ry, (float) uy, (float) -fy, 0.0F,
				(float) rz, (float) uz, (float) -fz, 0.0F,
				(float) tR, (float) tU, (float) tF, 1.0F);

		GpuBufferSlice previousFog = RenderSystem.getShaderFog();
		boolean projectionBackedUp = false;

		try
		{
			GpuDevice device = RenderSystem.getDevice();
			CommandEncoder encoder = device.createCommandEncoder();

			encoder.clearColorAndDepthTextures(fbo.getColorTexture(), argb(CLEAR_COLOR),
					fbo.getDepthTexture(), 0.0D); // 26.2 reversed-Z: far = 0.0

			RenderSystem.backupProjectionMatrix();
			projectionBackedUp = true;
			RenderSystem.setProjectionMatrix(projBuffer().getBuffer(projection), ProjectionType.PERSPECTIVE);

			RenderSystem.setShaderFog(this.fogRenderer.getBuffer(FogRenderer.FogMode.NONE));

			// Registered ahead of the draws so a throw below still leaves the GUI a real texture to blit
			if (id != null)
			{
				registerTexture(client, id, fbo);
			}

			int layerCeiling = view.maxLayer() >= 1.0F
					? model.sizeY()
					: Math.max(1, Math.round(view.maxLayer() * model.sizeY()));

			if (this.cacheModel != model || this.cacheLayerCeiling != layerCeiling || this.geometryCache.isEmpty())
			{
				rebuildGeometryCache(model, level, layerCeiling, device, eyeX, eyeY, eyeZ);
				this.cacheModel = model;
				this.cacheLayerCeiling = layerCeiling;
			}

			GpuTextureView atlasView = client.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
			GpuSampler atlasSampler = client.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getSampler();
			GpuTextureView lightmapView = client.gameRenderer.lightmap();
			GpuSampler lightmapSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
			GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(
					viewMatrix, new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(0.0F, 0.0F, 0.0F), new Matrix4f());
			GpuBufferSlice[] transforms = {transform};

			this.chunkFix.updateBuffer(atlasView.getWidth(0), atlasView.getHeight(0), 1.0F);
			GpuBufferSlice chunkFixSlice = this.chunkFix.getCurrentBufferSlice();

			RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);

			if (!this.geometryCache.isEmpty())
			{
				try (RenderPass pass = encoder.createRenderPass(() -> "schematicindex-preview",
						fbo.getColorTextureView(), Optional.empty(),
						fbo.getDepthTextureView(), OptionalDouble.empty()))
				{
					RenderSystem.bindDefaultUniforms(pass);
					pass.setUniform("ChunkFix", chunkFixSlice);
					pass.bindTexture("Sampler0", atlasView, atlasSampler);
					pass.bindTexture("Sampler2", lightmapView, lightmapSampler);

					for (ChunkSectionLayer layer : new ChunkSectionLayer[]
					{
							ChunkSectionLayer.SOLID, ChunkSectionLayer.CUTOUT, ChunkSectionLayer.TRANSLUCENT})
							{
						CachedGeometry cached = this.geometryCache.get(layer);

						if (cached != null)
						{
							GpuBuffer ibo = indices.getBuffer(cached.indexCount());
							RenderPass.Draw<GpuBufferSlice[]> draw = new RenderPass.Draw<>(0, cached.vbo(), ibo,
									indices.type(), 0, cached.indexCount(), 0,
									(slices, uploader) -> uploader.upload("DynamicTransforms", ((GpuBufferSlice[]) slices)[0]));
							pass.setPipeline(legacyPipeline(layer));
							pass.drawMultipleIndexed(List.of(draw), ibo, indices.type(),
									List.of("DynamicTransforms"), transforms);
						}
					}
				}
			}

			drawBlockEntities(model, fbo, viewMatrix, (float) centreX, (float) centreY, (float) centreZ);
		}
		catch (Throwable e)
		{
			SchematicIndexMod.LOGGER.warn("Preview draw failed; showing a partial frame", e);
		}
		finally
		{
			if (projectionBackedUp)
			{
				RenderSystem.restoreProjectionMatrix();
			}

			this.chunkFix.endFrame();
			RenderSystem.setShaderFog(previousFog);
		}
	}

	private @Nullable ProjectionMatrixBuffer projBufferField;

	private ProjectionMatrixBuffer projBuffer()
	{
		if (this.projBufferField == null)
		{
			this.projBufferField = new ProjectionMatrixBuffer("schematicindex-preview");
		}
		return this.projBufferField;
	}

	private static Vector4f argb(int argb)
	{
		float a = ((argb >> 24) & 0xFF) / 255.0F;
		float r = ((argb >> 16) & 0xFF) / 255.0F;
		float g = ((argb >> 8) & 0xFF) / 255.0F;
		float b = (argb & 0xFF) / 255.0F;
		return new Vector4f(r, g, b, a);
	}

	private void rebuildGeometryCache(SchematicPreview.Model model, PreviewLevel level, int layerCeiling,
			GpuDevice device, double eyeX, double eyeY, double eyeZ)
	{
		for (CachedGeometry cached : this.geometryCache.values())
		{
			cached.vbo().close();
		}
		this.geometryCache.clear();

		EnumMap<ChunkSectionLayer, BufferBuilder> builders = new EnumMap<>(ChunkSectionLayer.class);
		meshBlocks(model, level, layerCeiling, builders);

		for (Map.Entry<ChunkSectionLayer, BufferBuilder> entry : builders.entrySet())
		{
			ChunkSectionLayer layer = entry.getKey();
			MeshData mesh = entry.getValue().build();

			if (mesh == null)
			{
				continue;
			}

			try
			{
				if (layer == ChunkSectionLayer.TRANSLUCENT)
				{
					mesh.sortQuads(this.sortScratch,
							VertexSorting.byDistance((float) eyeX, (float) eyeY, (float) eyeZ));
				}

				int indexCount = mesh.drawState().indexCount();
				GpuBuffer vbo = device.createBuffer(() -> "schematicindex-preview/" + layer.label(),
						GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer());
				this.geometryCache.put(layer, new CachedGeometry(vbo, indexCount));
			}
			finally
			{
				mesh.close();
			}
		}
	}

	private void meshBlocks(SchematicPreview.Model model, PreviewLevel level, int layerCeiling,
			EnumMap<ChunkSectionLayer, BufferBuilder> builders)
	{
		BlockState[] states = model.states();
		int sizeX = model.sizeX();
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
						// Water and lava are INVISIBLE block models; only the fluid below draws for them
						if (state.getRenderShape() != RenderShape.INVISIBLE)
						{
							BlockStateModel blockModel = this.blockModels.get(state);
							long seed = state.getSeed(pos);
							this.modelRenderer.tesselateBlock(
									(qx, qy, qz, quad, inst) -> builderFor(builders, quad.materialInfo().layer())
											.putBlockBakedQuad(qx, qy, qz, quad, inst),
									x, y, z, level, pos, state, blockModel, seed);
						}

						FluidState fluid = state.getFluidState();

						if (!fluid.isEmpty())
						{
							final int baseX = x & ~15;
							final int baseY = y & ~15;
							final int baseZ = z & ~15;
							this.fluidRenderer.tesselate(level, pos,
									(ChunkSectionLayer l) ->
											new OffsetConsumer(builderFor(builders, l), baseX, baseY, baseZ),
									state, fluid);
						}
					}
					catch (Throwable e)
					{
						SchematicIndexMod.LOGGER.debug("Skipping unsupported block {} in GPU preview", state, e);
					}
				}
			}
		}
	}

	// Resolved once per model, since a full volume scan is far too costly per frame
	private void collectBlockEntities(SchematicPreview.Model model, @Nullable SchematicRenderLevel beLevel)
	{
		this.beCells.clear();

		if (beLevel == null)
		{
			return;
		}

		for (int y = 0; y < model.sizeY(); y++)
		{
			for (int z = 0; z < model.sizeZ(); z++)
			{
				for (int x = 0; x < model.sizeX(); x++)
				{
					if (model.at(x, y, z) == 0)
					{
						continue;
					}

					BlockEntity be = beLevel.getBlockEntity(new BlockPos(x, y, z));

					if (be != null)
					{
						this.beCells.add(be);
					}
				}
			}
		}
	}

	private void drawBlockEntities(SchematicPreview.Model model, TextureTarget fbo, Matrix4f viewMatrix,
			float cx, float cy, float cz)
	{
		if (this.beLevelCache == null || this.beLevelModel != model)
		{
			this.beLevelCache = SchematicRenderLevel.create(model);
			this.beLevelModel = model;
			collectBlockEntities(model, this.beLevelCache);
		}

		if (this.beCells.isEmpty())
		{
			return;
		}

		Vec3 cam = new Vec3(cx, cy, cz);
		this.beDispatcher.prepare(cam);
		this.cameraRenderState.initialized = true;
		this.cameraRenderState.pos = cam;
		this.cameraRenderState.blockPos = BlockPos.containing(cam);
		this.cameraRenderState.orientation = new Quaternionf();

		PoseStack poseStack = new PoseStack();
		int submitted = 0;

		for (BlockEntity be : this.beCells)
		{
			// The skull renderer only reads an already-resolved skin, so kick the async lookup here
			if (be instanceof SkullBlockEntity skull
					&& skull.getOwnerProfile() != null)
			{
				Minecraft.getInstance().playerSkinRenderCache().lookup(skull.getOwnerProfile());
			}

			try
			{
				// tryExtractRenderState gates on culls that reject everything in an offscreen pass
				@SuppressWarnings({"rawtypes", "unchecked"})
				BlockEntityRenderer renderer = this.beDispatcher.getRenderer(be);

				if (renderer != null && be.hasLevel() && be.getType().isValid(be.getBlockState()))
				{
					BlockEntityRenderState renderState = renderer.createRenderState();
					renderer.extractRenderState(be, renderState, 0.0F, cam, null);
					BlockPos pos = be.getBlockPos();
					poseStack.pushPose();
					poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
					this.beDispatcher.submit(renderState, poseStack, this.submitStorage, this.cameraRenderState);
					poseStack.popPose();
					submitted++;
				}
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.debug("Block entity {} failed to extract", be, e);
			}
		}

		if (submitted == 0)
		{
			return;
		}

		GpuTextureView prevColor = RenderSystem.outputColorTextureOverride;
		GpuTextureView prevDepth = RenderSystem.outputDepthTextureOverride;
		GpuBufferSlice prevLights = RenderSystem.getShaderLights();
		Matrix4fStack modelView = RenderSystem.getModelViewStack();

		RenderSystem.outputColorTextureOverride = fbo.getColorTextureView();
		RenderSystem.outputDepthTextureOverride = fbo.getDepthTextureView();
		REDIRECT_MAIN_TARGET = fbo;
		modelView.pushMatrix();
		modelView.set(viewMatrix);

		Minecraft client = Minecraft.getInstance();
		client.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_FLAT);
		RenderSystem.setShaderLights(this.flatLightSlice);

		try
		{
			this.featureDispatcher.renderAllFeatures(this.submitStorage);
		}
		catch (Throwable e)
		{
			SchematicIndexMod.LOGGER.warn("Block-entity rendering failed; drawing the preview without it", e);
		}
		finally
		{
			modelView.popMatrix();
			REDIRECT_MAIN_TARGET = null;
			RenderSystem.setShaderLights(prevLights);
			RenderSystem.outputColorTextureOverride = prevColor;
			RenderSystem.outputDepthTextureOverride = prevDepth;
		}
	}

	private BufferBuilder builderFor(EnumMap<ChunkSectionLayer, BufferBuilder> builders, ChunkSectionLayer layer)
	{
		return builders.computeIfAbsent(layer, l -> {
			ByteBufferBuilder arena = this.arenas.computeIfAbsent(l, k -> new ByteBufferBuilder(4 * 1024 * 1024));
			return new BufferBuilder(arena, PrimitiveTopology.QUADS, l.vertexFormat());
		});
	}

	private void registerTexture(Minecraft client, Identifier id, TextureTarget fbo)
	{
		if (this.registered == null)
		{
			this.registered = new FboTexture();
		}

		client.getTextureManager().register(id, this.registered);
		this.registered.wrap(fbo);
	}

	// FluidRenderer emits fluid at pos & 15; the blocks here sit at full model coordinates
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

	private void ensureResources()
	{
		Minecraft client = Minecraft.getInstance();

		if (this.target == null)
		{
			this.target = new TextureTarget("schematicindex-preview", RENDER_WIDTH, RENDER_HEIGHT, true,
					GpuFormat.RGBA8_UNORM);
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

		if (this.sortScratch == null)
		{
			this.sortScratch = new ByteBufferBuilder(4 * 1024 * 1024);
		}

		if (this.modelRenderer == null)
		{
			this.modelRenderer = new ModelBlockRenderer(true, false, client.getBlockColors());
			this.blockModels = client.getModelManager().getBlockStateModelSet();
			this.fluidRenderer = new FluidRenderer(
					client.getModelManager().getFluidStateModelSet());
		}

		if (this.featureDispatcher == null)
		{
			this.submitStorage = new SubmitNodeStorage();
			this.beRenderBuffers = new RenderBuffers(1);
			this.gameRenderState = new GameRenderState();
			this.featureDispatcher = new FeatureRenderDispatcher(
					this.beRenderBuffers, client.getModelManager(), client.getAtlasManager(), client.font,
					this.gameRenderState);
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

		for (CachedGeometry cached : this.geometryCache.values())
		{
			cached.vbo().close();
		}
		this.geometryCache.clear();
		this.cacheModel = null;
		this.cacheLayerCeiling = Integer.MIN_VALUE;

		for (ByteBufferBuilder arena : this.arenas.values())
		{
			arena.close();
		}
		this.arenas.clear();

		if (this.sortScratch != null)
		{
			this.sortScratch.close();
			this.sortScratch = null;
		}

		if (this.projBufferField != null)
		{
			this.projBufferField.close();
			this.projBufferField = null;
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

		try
		{
			this.chunkFix.close();
		}
		catch (Exception ignored)
		{
		}

		this.modelRenderer = null;
		this.blockModels = null;
	}
}

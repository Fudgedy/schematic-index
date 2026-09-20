package com.fudgedy.schematicindex.mapart;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.Settings;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.MaLiLibPipelines;
import fi.dy.masa.malilib.render.RenderContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import java.util.function.Supplier;

public final class MapCorners implements IRenderer
{
	private static final int MAP_SIZE = 128;
	// A scale-0 map is centred on a multiple of 128 and spans centre-64..centre+63, so edges sit at 128n + 64
	private static final int EDGE_OFFSET = 64;
	private static final float ARM_LENGTH = 3.0F;
	private static final float HEIGHT_OFFSET = 0.05F;
	private static final float POST_HALF_HEIGHT = 1.0F;
	// Half of the marker's world thickness, so each arm is a 0.12-block-wide box that scales with distance
	private static final float HALF_THICKNESS = 0.06F;
	private static final Color4f COLOR = Color4f.fromColor(0xD03FD68C);
	private static final Supplier<String> NAME = () -> SchematicIndexMod.MOD_ID + ":map_corners";
	private static final Supplier<String> PROFILER_SECTION = () -> SchematicIndexMod.MOD_ID + "_map_corners";

	private static final MapCorners INSTANCE = new MapCorners();

	private boolean failed;

	private MapCorners()
	{
	}

	public static MapCorners getInstance()
	{
		return INSTANCE;
	}

	@Override
	public void onRenderWorldLast(RenderTarget fb, Matrix4fc modelViewMatrix, CameraRenderState cameraState, Frustum culling,
			RenderBuffers buffers, GpuBufferSlice terrainFog, Vector4f fogColor, ProfilerFiller profiler)
	{
		if (this.failed || !Settings.mapCorners())
		{
			return;
		}

		Minecraft mc = Minecraft.getInstance();

		if (mc.player == null || mc.level == null || mc.gui.hud.isHidden())
		{
			return;
		}

		profiler.push("map_corners");

		Vec3 camera = mc.gameRenderer.mainCamera().position();
		int playerX = mc.player.getBlockX();
		int playerZ = mc.player.getBlockZ();
		float y = (float) (clampedHeight(mc.level) + HEIGHT_OFFSET - camera.y);

		// The edge west of the player is the last multiple of 128 at or below x - 64; the map is that edge to +128
		int minColumn = Math.floorDiv(playerX - EDGE_OFFSET, MAP_SIZE);
		int maxColumn = minColumn + 1;
		int minRow = Math.floorDiv(playerZ - EDGE_OFFSET, MAP_SIZE);
		int maxRow = minRow + 1;

		RenderContext ctx = new RenderContext(NAME, MaLiLibPipelines.POSITION_COLOR_TRANSLUCENT_NO_DEPTH_NO_CULL, 0);
		BufferBuilder buffer = ctx.getBuilder();

		for (int column = minColumn; column <= maxColumn; column++)
		{
			float x = (float) (column * MAP_SIZE + EDGE_OFFSET - camera.x);

			for (int row = minRow; row <= maxRow; row++)
			{
				float z = (float) (row * MAP_SIZE + EDGE_OFFSET - camera.z);
				this.cross(buffer, x, y, z);
			}
		}

		// Latched so a broken pipeline logs once instead of every frame
		try
		{
			MeshData mesh = buffer.build();

			if (mesh != null)
			{
				ctx.draw(mesh, false, true);
				mesh.close();
			}

			ctx.close();
		}
		catch (Exception e)
		{
			this.failed = true;
			SchematicIndexMod.LOGGER.warn("Mapart border overlay disabled after a render failure", e);
		}

		profiler.pop();
	}

	@Override
	public Supplier<String> getProfilerSectionSupplier()
	{
		return PROFILER_SECTION;
	}

	public static int minHeight(Level level)
	{
		return level == null ? -64 : level.getMinY();
	}

	public static int maxHeight(Level level)
	{
		return level == null ? 319 : level.getMaxY();
	}

	// The stored Y is kept inside whatever dimension the player is in, so a nether value never draws
	// under the overworld floor
	public static int clampedHeight(Level level)
	{
		int y = Math.max(minHeight(level), Math.min(maxHeight(level), Settings.cornerHeight()));

		if (y != Settings.cornerHeight())
		{
			Settings.setCornerHeight(y);
		}

		return y;
	}

	// Turning the markers on before any height was chosen starts them at the player's own Y
	public static void enable(boolean on, Minecraft mc)
	{
		Settings.setMapCorners(on);

		if (on && !Settings.hasCornerHeight())
		{
			useCurrentY(mc);
		}
	}

	public static boolean useCurrentY(Minecraft mc)
	{
		if (mc.player == null)
		{
			return false;
		}

		Settings.setCornerHeight(mc.player.getBlockY());
		return true;
	}

	// The north-west block of the 128x128 map containing a position: edges sit at 128n + 64
	public static int mapEdge(int coordinate)
	{
		return Math.floorDiv(coordinate - EDGE_OFFSET, MAP_SIZE) * MAP_SIZE + EDGE_OFFSET;
	}

	private void cross(BufferBuilder buffer, float x, float y, float z)
	{
		RenderUtils.drawBoxAllSidesBatchedQuads(x - ARM_LENGTH, y - HALF_THICKNESS, z - HALF_THICKNESS,
				x + ARM_LENGTH, y + HALF_THICKNESS, z + HALF_THICKNESS, COLOR, buffer);
		RenderUtils.drawBoxAllSidesBatchedQuads(x - HALF_THICKNESS, y - HALF_THICKNESS, z - ARM_LENGTH,
				x + HALF_THICKNESS, y + HALF_THICKNESS, z + ARM_LENGTH, COLOR, buffer);
		RenderUtils.drawBoxAllSidesBatchedQuads(x - HALF_THICKNESS, y - POST_HALF_HEIGHT, z - HALF_THICKNESS,
				x + HALF_THICKNESS, y + POST_HALF_HEIGHT, z + HALF_THICKNESS, COLOR, buffer);
	}
}

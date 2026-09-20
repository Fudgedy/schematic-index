package com.fudgedy.schematicindex.gui;

import com.fudgedy.schematicindex.SchematicIndexMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

public final class BlockShapes
{
	public static final int FLUID_NONE = 0;
	public static final int FLUID_WATER = 1;
	public static final int FLUID_LAVA = 2;

	// translucency is the see-through alpha, 0 meaning opaque. fluidTop is the fluid surface height within
	// the cell. waterlogged marks a normal-model block whose cell is also flooded
	public record Raw(List<RawQuad> quads, boolean cube, float translucency, int fluid, float fluidTop,
			boolean waterlogged)
			{
	}

	public record RawQuad(float[] xs, float[] ys, float[] zs, float[] us, float[] vs,
			Identifier texture, int face, int tint)
			{
	}

	public static final class Quad
	{
		final float[] xs;
		final float[] ys;
		final float[] zs;
		final float[] us;
		final float[] vs;
		final BlockTextures.Texture texture;
		final int face;
		final int tint;

		Quad(RawQuad raw, @Nullable BlockTextures.Texture texture)
		{
			this.xs = raw.xs();
			this.ys = raw.ys();
			this.zs = raw.zs();
			this.us = raw.us();
			this.vs = raw.vs();
			this.texture = texture;
			this.face = raw.face();
			this.tint = raw.tint();
		}
	}

	public static final class Shape
	{
		final boolean fullCube;
		final Quad[] quads;
		final BlockTextures.Faces faces;
		final float translucency;
		final int fluid;
		final float fluidTop;
		final boolean waterlogged;
		final boolean animated;

		Shape(boolean fullCube, @Nullable Quad[] quads, BlockTextures.Faces faces,
				float translucency, int fluid, float fluidTop, boolean waterlogged, boolean animated)
				{
			this.fullCube = fullCube;
			this.quads = quads;
			this.faces = faces;
			this.translucency = translucency;
			this.fluid = fluid;
			this.fluidTop = fluidTop;
			this.waterlogged = waterlogged;
			this.animated = animated;
		}

		public boolean fullCube()
		{
			return this.fullCube;
		}

		public int fluid()
		{
			return this.fluid;
		}

		public float fluidTop()
		{
			return this.fluidTop;
		}

		public boolean waterlogged()
		{
			return this.waterlogged;
		}

		public boolean animated()
		{
			return this.animated;
		}

		public float translucency()
		{
			return this.translucency;
		}

		public boolean invisible()
		{
			// A fluid has no quads and is not a full cube, but it must still reach the raytracer's fluid branch
			return !this.fullCube && this.quads == null && this.fluid == FLUID_NONE;
		}

		public BlockTextures.Faces faces()
		{
			return this.faces;
		}
	}

	private BlockShapes()
	{
	}

	public static Raw extract(BlockState state)
	{
		List<RawQuad> quads = new ArrayList<>();
		float translucency = translucencyFor(state);

		FluidState fluidState = state.getFluidState();
		boolean hasFluid = !fluidState.isEmpty();
		int fluidType = FLUID_NONE;
		float fluidTop = 1.0F;

		if (hasFluid)
		{
			fluidType = fluidState.is(FluidTags.LAVA) ? FLUID_LAVA : FLUID_WATER;
			fluidTop = fluidTopFor(fluidState);
		}

		try
		{
			BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
			List<BlockStateModelPart> parts = new ArrayList<>();
			model.collectParts(RandomSource.create(42L), parts);

			for (BlockStateModelPart part : parts)
			{
				for (Direction direction : Direction.values())
				{
					collect(quads, part.getQuads(direction), state);
				}

				collect(quads, part.getQuads(null), state);
			}

			if (quads.isEmpty())
			{
				if (hasFluid)
				{
					// A pure fluid has no block model, so it becomes a fluid shape rather than a cube
					return new Raw(quads, false, 0.0F, fluidType, fluidTop, false);
				}

				if (synthesize(quads, state))
				{
					return new Raw(quads, false, translucency, FLUID_NONE, 1.0F, false);
				}

				fromOutline(quads, state, model.particleMaterial().sprite().contents().name());
			}
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.debug("No model geometry for {}", state, e);
		}

		// A block with both its own model and a water fluid state is waterlogged. Lava never waterlogs
		boolean waterlogged = hasFluid && fluidType == FLUID_WATER && !quads.isEmpty();
		return new Raw(quads, false, translucency, FLUID_NONE, 1.0F, waterlogged);
	}

	// One constant top height per cell: per-corner flow-height blending is out of scope
	private static float fluidTopFor(FluidState fluid)
	{
		try
		{
			// getAmount is 1 to 8, 8 being a source, so amount/9 gives vanilla's ~0.88 for a source
			int amount = fluid.getAmount();
			return Math.max(0.11F, Math.min(0.88F, amount / 9.0F));
		}
		catch (Exception e)
		{
			return 0.88F;
		}
	}

	// Hand-tuned to read like Minecraft. Only full cubes are listed; panes and other non-cube translucent
	// shapes stay opaque
	private static float translucencyFor(BlockState state)
	{
		try
		{
			// The 16 dyed glass blocks share one class and their texture already supplies the tint
			if (state.getBlock() instanceof StainedGlassBlock)
			{
				return 0.45F;
			}

			if (state.is(Blocks.GLASS))
			{
				return 0.30F;
			}

			if (state.is(Blocks.TINTED_GLASS))
			{
				return 0.70F;
			}

			if (state.is(Blocks.ICE))
			{
				return 0.55F;
			}

			if (state.is(Blocks.SLIME_BLOCK) || state.is(Blocks.HONEY_BLOCK))
			{
				return 0.60F;
			}
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.debug("Could not classify translucency for {}", state, e);
		}

		return 0.0F;
	}

	private static void collect(List<RawQuad> out, List<BakedQuad> quads, BlockState state)
	{
		for (BakedQuad quad : quads)
		{
			TextureAtlasSprite sprite = quad.materialInfo().sprite();
			float[] xs = new float[4];
			float[] ys = new float[4];
			float[] zs = new float[4];
			float[] us = new float[4];
			float[] vs = new float[4];

			for (int i = 0; i < 4; i++)
			{
				Vector3fc position = quad.position(i);
				xs[i] = position.x();
				ys[i] = position.y();
				zs[i] = position.z();

				long packed = quad.packedUV(i);

				us[i] = normalise(UVPair.unpackU(packed), sprite.getU0(), sprite.getU1());
				vs[i] = normalise(UVPair.unpackV(packed), sprite.getV0(), sprite.getV1());
			}

			Direction direction = quad.direction();
			out.add(new RawQuad(xs, ys, zs, us, vs, sprite.contents().name(),
					direction == null ? -1 : direction.ordinal(),
					BlockTextures.tintFor(state, quad.materialInfo().tintIndex())));
		}
	}

	private record Skin(Identifier texture, float[] top, float[] side)
	{
	}

	private static final float[] CHEST_TOP = {28.0F / 64.0F, 0.0F, 42.0F / 64.0F, 14.0F / 64.0F};
	private static final float[] CHEST_SIDE = {14.0F / 64.0F, 33.0F / 64.0F, 28.0F / 64.0F, 43.0F / 64.0F};

	private static final float[] SHULKER_TOP = {16.0F / 64.0F, 0.0F, 32.0F / 64.0F, 16.0F / 64.0F};
	private static final float[] SHULKER_SIDE = {16.0F / 64.0F, 16.0F / 64.0F, 32.0F / 64.0F, 28.0F / 64.0F};

	private static final float[] BED_TOP = {6.0F / 64.0F, 6.0F / 64.0F, 22.0F / 64.0F, 22.0F / 64.0F};
	private static final float[] BED_FOOT_TOP = {6.0F / 64.0F, 28.0F / 64.0F, 22.0F / 64.0F, 44.0F / 64.0F};
	private static final float[] BED_SIDE = {0.0F, 6.0F / 64.0F, 6.0F / 64.0F, 22.0F / 64.0F};

	private static final float[] SIGN_BOARD = {2.0F / 64.0F, 2.0F / 32.0F, 26.0F / 64.0F, 14.0F / 32.0F};

	private static final float[] BANNER_CLOTH = {1.0F / 64.0F, 1.0F / 64.0F, 21.0F / 64.0F, 41.0F / 64.0F};

	private static @Nullable Skin skinFor(BlockState state)
	{
		String chest = null;

		if (state.is(Blocks.ENDER_CHEST))
		{
			chest = "ender";
		}
		else if (state.is(Blocks.TRAPPED_CHEST))
		{
			chest = "trapped";
		}
		else if (state.is(Blocks.CHEST))
		{
			chest = "normal";
		}

		if (chest != null)
		{
			try
			{
				return new Skin(Sheets.CHEST_MAPPER.defaultNamespaceApply(chest).texture(), CHEST_TOP, CHEST_SIDE);
			}
			catch (Exception e)
			{
				return null;
			}
		}

		if (state.getBlock() instanceof ShulkerBoxBlock shulker)
		{
			try
			{
				DyeColor color = shulker.getColor();
				Identifier texture = color == null
						? Sheets.DEFAULT_SHULKER_TEXTURE_LOCATION.texture()
						: Sheets.getShulkerBoxSprite(color).texture();
				return new Skin(texture, SHULKER_TOP, SHULKER_SIDE);
			}
			catch (Exception e)
			{
				return null;
			}
		}

		if (state.getBlock() instanceof BedBlock bed)
		{
			try
			{
				// Sheets.getBedMaterial is gone; beds have real block models here, so this is unreachable
				Identifier texture = Identifier.withDefaultNamespace(
						"block/" + bed.getColor().getName() + "_bed_foot");
				boolean foot = state.getValue(BedBlock.PART) == BedPart.FOOT;
				return new Skin(texture, foot ? BED_FOOT_TOP : BED_TOP, BED_SIDE);
			}
			catch (Exception e)
			{
				return null;
			}
		}

		if (state.getBlock() instanceof DecoratedPotBlock)
		{
			try
			{
				return new Skin(Sheets.DECORATED_POT_SIDE.texture(), null, null);
			}
			catch (Exception e)
			{
				return null;
			}
		}

		return null;
	}

	private static boolean synthesize(List<RawQuad> out, BlockState state)
	{
		if (state.getBlock() instanceof SignBlock sign)
		{
			try
			{
				// Sheets.getSignMaterial is gone: signs use the block-atlas sprite block/<wood>_sign
				Identifier texture = Identifier.withDefaultNamespace("block/" + sign.type().name() + "_sign");

				if (state.getBlock() instanceof WallSignBlock)
				{
					emitBox(out, texture, SIGN_BOARD, BlockTextures.NO_TINT, 0.06F, 0.3F, 0.02F, 0.94F, 0.74F, 0.16F);
				}
				else
				{
					emitBox(out, texture, SIGN_BOARD, BlockTextures.NO_TINT, 0.44F, 0.0F, 0.44F, 0.56F, 0.55F, 0.56F);
					emitBox(out, texture, SIGN_BOARD, BlockTextures.NO_TINT, 0.06F, 0.52F, 0.44F, 0.94F, 1.0F, 0.56F);
				}

				return true;
			}
			catch (Exception e)
			{
				return false;
			}
		}

		if (state.getBlock() instanceof AbstractBannerBlock banner)
		{
			try
			{
				Identifier texture = Sheets.BANNER_BASE.texture();
				int cloth = banner.getColor().getTextureDiffuseColor();

				emitBox(out, texture, BANNER_CLOTH, 0xFF3A3A3A, 0.46F, 0.0F, 0.46F, 0.54F, 0.2F, 0.54F);
				emitBox(out, texture, BANNER_CLOTH, cloth, 0.12F, 0.16F, 0.47F, 0.88F, 0.98F, 0.53F);
				return true;
			}
			catch (Exception e)
			{
				return false;
			}
		}

		return false;
	}

	private static void emitBox(List<RawQuad> out, Identifier texture, float @Nullable [] region, int tint,
			float x0, float y0, float z0, float x1, float y1, float z1)
			{
		addFace(out, texture, Direction.DOWN, region, tint,
				new float[]{x0, x1, x1, x0}, new float[]{y0, y0, y0, y0}, new float[]{z0, z0, z1, z1});
		addFace(out, texture, Direction.UP, region, tint,
				new float[]{x0, x1, x1, x0}, new float[]{y1, y1, y1, y1}, new float[]{z1, z1, z0, z0});
		addFace(out, texture, Direction.NORTH, region, tint,
				new float[]{x1, x0, x0, x1}, new float[]{y1, y1, y0, y0}, new float[]{z0, z0, z0, z0});
		addFace(out, texture, Direction.SOUTH, region, tint,
				new float[]{x0, x1, x1, x0}, new float[]{y1, y1, y0, y0}, new float[]{z1, z1, z1, z1});
		addFace(out, texture, Direction.WEST, region, tint,
				new float[]{x0, x0, x0, x0}, new float[]{y1, y1, y0, y0}, new float[]{z0, z1, z1, z0});
		addFace(out, texture, Direction.EAST, region, tint,
				new float[]{x1, x1, x1, x1}, new float[]{y1, y1, y0, y0}, new float[]{z1, z0, z0, z1});
	}

	private static void fromOutline(List<RawQuad> out, BlockState state, Identifier texture)
	{
		Skin skin = skinFor(state);
		Identifier surface = skin == null ? texture : skin.texture();
		VoxelShape shape;

		try
		{
			shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		}
		catch (Exception e)
		{
			return;
		}

		if (shape == null || shape.isEmpty())
		{
			return;
		}

		for (AABB box : shape.toAabbs())
		{
			float x0 = (float) box.minX;
			float y0 = (float) box.minY;
			float z0 = (float) box.minZ;
			float x1 = (float) box.maxX;
			float y1 = (float) box.maxY;
			float z1 = (float) box.maxZ;

			float[] top = skin == null ? null : skin.top();
			float[] side = skin == null ? null : skin.side();

			addFace(out, surface, Direction.DOWN, top,
					new float[]{x0, x1, x1, x0}, new float[]{y0, y0, y0, y0}, new float[]{z0, z0, z1, z1});
			addFace(out, surface, Direction.UP, top,
					new float[]{x0, x1, x1, x0}, new float[]{y1, y1, y1, y1}, new float[]{z1, z1, z0, z0});
			addFace(out, surface, Direction.NORTH, side,
					new float[]{x1, x0, x0, x1}, new float[]{y1, y1, y0, y0}, new float[]{z0, z0, z0, z0});
			addFace(out, surface, Direction.SOUTH, side,
					new float[]{x0, x1, x1, x0}, new float[]{y1, y1, y0, y0}, new float[]{z1, z1, z1, z1});
			addFace(out, surface, Direction.WEST, side,
					new float[]{x0, x0, x0, x0}, new float[]{y1, y1, y0, y0}, new float[]{z0, z1, z1, z0});
			addFace(out, surface, Direction.EAST, side,
					new float[]{x1, x1, x1, x1}, new float[]{y1, y1, y0, y0}, new float[]{z1, z0, z0, z1});
		}
	}

	private static void addFace(List<RawQuad> out, Identifier texture, Direction direction,
			float @Nullable [] region, float[] xs, float[] ys, float[] zs)
			{
		addFace(out, texture, direction, region, BlockTextures.NO_TINT, xs, ys, zs);
	}

	private static void addFace(List<RawQuad> out, Identifier texture, Direction direction,
			float @Nullable [] region, int tint, float[] xs, float[] ys, float[] zs)
			{
		float[] us = new float[4];
		float[] vs = new float[4];

		for (int i = 0; i < 4; i++)
		{
			switch (direction.getAxis())
			{
				case X -> {
					us[i] = zs[i];
					vs[i] = 1.0F - ys[i];
				}
				case Y -> {
					us[i] = xs[i];
					vs[i] = zs[i];
				}
				default -> {
					us[i] = xs[i];
					vs[i] = 1.0F - ys[i];
				}
			}

			if (region != null)
			{
				us[i] = region[0] + us[i] * (region[2] - region[0]);
				vs[i] = region[1] + vs[i] * (region[3] - region[1]);
			}
		}

		out.add(new RawQuad(xs, ys, zs, us, vs, texture, direction.ordinal(), tint));
	}

	private static float normalise(float value, float min, float max)
	{
		float span = max - min;
		return span == 0.0F ? 0.0F : (value - min) / span;
	}

	public static Shape build(Raw raw, BlockTextures.Faces faces)
	{
		boolean animated = faces.animated() || raw.fluid() != FLUID_NONE;

		if (raw.quads().isEmpty())
		{
			return new Shape(raw.cube(), null, faces, raw.translucency(), raw.fluid(), raw.fluidTop(),
					raw.waterlogged(), animated);
		}

		// The fast path recomputes UV from the hit fraction assuming identity UV, so a rotated mapping
		// (sideways logs, hay, bone) needs the general quad path and its real BakedQuad UVs
		if (isCubeShaped(raw.quads()) && !hasRotatedUv(raw.quads()))
		{
			// A cube face can carry a base plus overlays, and the fast-path Faces holds only the base
			addOverlays(faces, raw.quads());
			return new Shape(true, null, faces, raw.translucency(), raw.fluid(), raw.fluidTop(),
					raw.waterlogged(), animated);
		}

		Quad[] quads = new Quad[raw.quads().size()];

		for (int i = 0; i < quads.length; i++)
		{
			RawQuad source = raw.quads().get(i);
			quads[i] = new Quad(source, BlockTextures.texture(source.texture()));

			if (quads[i].texture != null && quads[i].texture.animated())
			{
				animated = true;
			}
		}

		return new Shape(false, quads, faces, raw.translucency(), raw.fluid(), raw.fluidTop(),
				raw.waterlogged(), animated);
	}

	// Conservative on purpose: a mirror-only flip does not misorient grain, so only a quarter turn counts
	private static boolean hasRotatedUv(List<RawQuad> quads)
	{
		for (RawQuad quad : quads)
		{
			int face = quad.face();

			if (face < 0 || face > 5)
			{
				continue;
			}

			if (isFaceUvRotated(face, quad))
			{
				return true;
			}
		}

		return false;
	}

	private static boolean isFaceUvRotated(int face, RawQuad quad)
	{
		int pAxis;
		int qAxis;

		switch (face)
		{
			case 0, 1 -> {
				pAxis = 0;
				qAxis = 2;
			}
			case 2, 3 -> {
				pAxis = 0;
				qAxis = 1;
			}
			default -> {
				pAxis = 2;
				qAxis = 1;
			}
		}

		int minU = 0;
		int maxU = 0;

		for (int i = 1; i < 4; i++)
		{
			if (quad.us()[i] < quad.us()[minU])
			{
				minU = i;
			}

			if (quad.us()[i] > quad.us()[maxU])
			{
				maxU = i;
			}
		}

		if (Math.abs(quad.us()[maxU] - quad.us()[minU]) < 1.0E-4F)
		{
			return false;
		}

		// If u tracks the q axis more than the p axis, the mapping is rotated a quarter turn
		double deltaP = Math.abs(axisValue(quad, maxU, pAxis) - axisValue(quad, minU, pAxis));
		double deltaQ = Math.abs(axisValue(quad, maxU, qAxis) - axisValue(quad, minU, qAxis));
		return deltaQ > deltaP + 1.0E-4D;
	}

	private static double axisValue(RawQuad quad, int vertex, int axis)
	{
		return axis == 0 ? quad.xs()[vertex] : axis == 1 ? quad.ys()[vertex] : quad.zs()[vertex];
	}

	// The first quad on a face is the base resolveSprites already stored, so only the rest become overlays
	private static void addOverlays(BlockTextures.Faces faces, List<RawQuad> quads)
	{
		boolean[] baseSeen = new boolean[6];

		for (RawQuad quad : quads)
		{
			int face = quad.face();

			if (face < 0 || face > 5)
			{
				continue;
			}

			if (!baseSeen[face])
			{
				baseSeen[face] = true;
				continue;
			}

			faces.addOverlay(face, BlockTextures.texture(quad.texture()), quad.tint());
		}
	}

	// More than six quads is allowed on purpose, so a cube with per-face overlays still takes the fast path
	private static boolean isCubeShaped(List<RawQuad> quads)
	{
		if (quads.size() < 6)
		{
			return false;
		}

		boolean[] seen = new boolean[6];

		for (RawQuad quad : quads)
		{
			if (quad.face() < 0)
			{
				return false;
			}

			for (int i = 0; i < 4; i++)
			{
				if (!isEdge(quad.xs()[i]) || !isEdge(quad.ys()[i]) || !isEdge(quad.zs()[i]))
				{
					return false;
				}
			}

			seen[quad.face()] = true;
		}

		for (boolean face : seen)
		{
			if (!face)
			{
				return false;
			}
		}

		return true;
	}

	private static boolean isEdge(float value)
	{
		return value < 0.001F || value > 0.999F;
	}
}

package com.fudgedy.schematicindex.gui;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BlockTextures
{
	public static final int NO_TINT = 0xFFFFFFFF;

	public record Resolved(Identifier[] sprites, int[] tints)
	{
	}

	public static final class Faces
	{
		private final Texture[] byFace = new Texture[6];
		private final int[] tints = new int[6];
		private int averageColor = 0xFF8A8A8A;

		// Ordered layers, base first, so sample() composites them. Null for the common single-layer block
		private List<Layer>[] overlays;

		private record Layer(Texture texture, int tint)
		{
		}

		Faces()
		{
			for (int i = 0; i < this.tints.length; i++)
			{
				this.tints[i] = NO_TINT;
			}
		}

		void addOverlay(int face, @Nullable Texture texture, int tint)
		{
			if (this.overlays == null)
			{
				@SuppressWarnings("unchecked")
				List<Layer>[] created = new List[6];
				this.overlays = created;
			}

			if (this.overlays[face] == null)
			{
				this.overlays[face] = new ArrayList<>(1);
			}

			this.overlays[face].add(new Layer(texture, tint));
		}

		public int sample(int face, double u, double v, int lod)
		{
			Texture texture = this.byFace[face];
			int color;

			if (texture == null)
			{
				color = this.averageColor;
			}
			else
			{
				int sampled = texture.sample(u, v, lod);
					color = (sampled >>> 24) < 16 ? this.averageColor : tint(sampled, this.tints[face]);
			}

			if (this.overlays != null && this.overlays[face] != null)
			{
				for (Layer layer : this.overlays[face])
				{
					if (layer.texture() == null)
					{
						continue;
					}

					int overlay = layer.texture().sample(u, v, lod);
					int overlayAlpha = overlay >>> 24;

					// Skipping near-transparent overlay texels is what lets the base show through around them
					if (overlayAlpha < 16)
					{
						continue;
					}

					color = over(tint(overlay, layer.tint()), overlayAlpha, color);
				}
			}

			return color;
		}

		// Unlike sample(), the result keeps its true alpha, so the raytracer can tell a see-through texel
		// from a real coloured face. A face with no sprite at all keeps the opaque average-colour fallback
		public int sampleARGB(int face, double u, double v, int lod)
		{
			Texture texture = this.byFace[face];
			int color;
			int alpha;

			if (texture == null)
			{
				color = this.averageColor;
				alpha = 255;
			}
			else
			{
				int sampled = texture.sample(u, v, lod);

				if ((sampled >>> 24) < 16)
				{
					color = 0;
					alpha = 0;
				}
				else
				{
					color = tint(sampled, this.tints[face]);
					alpha = 255;
				}
			}

			if (this.overlays != null && this.overlays[face] != null)
			{
				for (Layer layer : this.overlays[face])
				{
					if (layer.texture() == null)
					{
						continue;
					}

					int overlay = layer.texture().sample(u, v, lod);
					int overlayAlpha = overlay >>> 24;

					if (overlayAlpha < 16)
					{
						continue;
					}

					color = over(tint(overlay, layer.tint()), overlayAlpha, color & 0xFFFFFF);
					// An overlay contributes coverage, so the face is not fully see-through here
					alpha = Math.max(alpha, overlayAlpha);
				}
			}

			return (Math.min(255, alpha) << 24) | (color & 0xFFFFFF);
		}

		boolean animated()
		{
			for (Texture texture : this.byFace)
			{
				if (texture != null && texture.animated())
				{
					return true;
				}
			}

			if (this.overlays != null)
			{
				for (List<Layer> layers : this.overlays)
				{
					if (layers == null)
					{
						continue;
					}

					for (Layer layer : layers)
					{
						if (layer.texture() != null && layer.texture().animated())
						{
							return true;
						}
					}
				}
			}

			return false;
		}

		private static int over(int top, int topAlpha, int bottom)
		{
			if (topAlpha >= 255)
			{
				return 0xFF000000 | (top & 0xFFFFFF);
			}

			double a = topAlpha / 255.0D;
			double inverse = 1.0D - a;
			int red = (int) (((top >> 16) & 0xFF) * a + ((bottom >> 16) & 0xFF) * inverse);
			int green = (int) (((top >> 8) & 0xFF) * a + ((bottom >> 8) & 0xFF) * inverse);
			int blue = (int) ((top & 0xFF) * a + (bottom & 0xFF) * inverse);
			return 0xFF000000 | (red << 16) | (green << 8) | blue;
		}
	}

	// frames[f] is the mip pyramid for animation frame f; a static sprite has exactly one. frameTicks is the
	// per-frame duration from the sprite's .mcmeta, in game ticks of 50 ms
	record Texture(int[][][] frames, int width, int height, int frameTicks)
	{
		boolean animated()
		{
			return this.frames.length > 1;
		}

		int sample(double u, double v, int lod)
		{
			int[][] mips = currentMips();
			int level = Math.max(0, Math.min(mips.length - 1, lod));
			int w = Math.max(1, this.width >> level);
			int h = Math.max(1, this.height >> level);
			int x = Math.max(0, Math.min(w - 1, (int) (u * w)));
			int y = Math.max(0, Math.min(h - 1, (int) (v * h)));
			return mips[level][y * w + x];
		}

		private int[][] currentMips()
		{
			int count = this.frames.length;

			if (count <= 1)
			{
				return this.frames[0];
			}

			long perFrame = Math.max(50L, (long) this.frameTicks * 50L);
			int frame = (int) ((animClock / perFrame) % count);
			return this.frames[frame];
		}
	}

	// Written by beginFrame on the render thread and read from the raytrace workers, hence volatile
	private static volatile long animClock = System.currentTimeMillis();

	public static void beginFrame()
	{
		animClock = System.currentTimeMillis();
	}

	// SchematicPreview.lodFor never asks for a deeper level, so the pyramid stops here rather than at 1x1
	private static final int MAX_MIP_LEVEL = 4;

	private static int[][] buildMips(int[] base, int width, int height)
	{
		List<int[]> levels = new ArrayList<>();
		levels.add(base);

		int cw = width;
		int ch = height;
		int[] current = base;

		while ((cw > 1 || ch > 1) && levels.size() <= MAX_MIP_LEVEL)
		{
			int nw = Math.max(1, cw >> 1);
			int nh = Math.max(1, ch >> 1);
			int[] next = new int[nw * nh];

			for (int y = 0; y < nh; y++)
			{
				for (int x = 0; x < nw; x++)
				{
					int x0 = Math.min(cw - 1, x * 2);
					int x1 = Math.min(cw - 1, x * 2 + 1);
					int y0 = Math.min(ch - 1, y * 2);
					int y1 = Math.min(ch - 1, y * 2 + 1);
					next[y * nw + x] = average(current[y0 * cw + x0], current[y0 * cw + x1],
							current[y1 * cw + x0], current[y1 * cw + x1]);
				}
			}

			levels.add(next);
			current = next;
			cw = nw;
			ch = nh;
		}

		return levels.toArray(new int[0][]);
	}

	private static int average(int a, int b, int c, int d)
	{
		int aa = a >>> 24;
		int ab = b >>> 24;
		int ac = c >>> 24;
		int ad = d >>> 24;

		int c24 = (aa + ab + ac + ad) / 4;

		// Transparent texels are usually stored black, so folding their RGB in would drag glass toward a
		// dark blob at coarse mip levels
		int red = 0;
		int green = 0;
		int blue = 0;
		int count = 0;

		if (aa != 0)
		{
			red += (a >> 16) & 0xFF;
			green += (a >> 8) & 0xFF;
			blue += a & 0xFF;
			count++;
		}

		if (ab != 0)
		{
			red += (b >> 16) & 0xFF;
			green += (b >> 8) & 0xFF;
			blue += b & 0xFF;
			count++;
		}

		if (ac != 0)
		{
			red += (c >> 16) & 0xFF;
			green += (c >> 8) & 0xFF;
			blue += c & 0xFF;
			count++;
		}

		if (ad != 0)
		{
			red += (d >> 16) & 0xFF;
			green += (d >> 8) & 0xFF;
			blue += d & 0xFF;
			count++;
		}

		if (count == 0)
		{
			return c24 << 24;
		}

		return (c24 << 24) | ((red / count) << 16) | ((green / count) << 8) | (blue / count);
	}

	// Nothing frees these pyramids, so the map is an access-ordered LRU. A miss is cached as a null value,
	// so containsKey still reports a known-missing sprite
	private static final int MAX_TEXTURES = 512;
	private static final Map<Identifier, Texture> CACHE = new LinkedHashMap<>(64, 0.75F, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<Identifier, Texture> eldest)
		{
			return size() > MAX_TEXTURES;
		}
	};

	private BlockTextures()
	{
	}

	public static int tint(int color, int tint)
	{
		if (tint == NO_TINT)
		{
			return color;
		}

		int red = ((color >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255;
		int green = ((color >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255;
		int blue = (color & 0xFF) * (tint & 0xFF) / 255;
		return (color & 0xFF000000) | (red << 16) | (green << 8) | blue;
	}

	public static final int WATER_TINT = 0xFF3F76E4;

	// Resolved once from the game's own BlockColors, so the fluid tint is not a hand-typed constant
	private static Integer defaultWaterTint;

	public static int defaultWaterTint()
	{
		Integer cached = defaultWaterTint;

		if (cached != null)
		{
			return cached;
		}

		int resolved = WATER_TINT;

		try
		{
			BlockState water = Blocks.WATER.defaultBlockState();
			BlockTintSource source = Minecraft.getInstance().getBlockColors().getTintSource(water, 0);
			int color = source == null ? -1 : source.color(water);

			if (color != -1)
			{
				resolved = 0xFF000000 | (color & 0xFFFFFF);
			}
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.debug("Could not resolve default water tint", e);
		}

		defaultWaterTint = resolved;
		return resolved;
	}

	public static Resolved resolveSprites(BlockState state)
	{
		Identifier[] names = new Identifier[6];
		int[] tints = new int[6];

		for (int i = 0; i < tints.length; i++)
		{
			tints[i] = NO_TINT;
		}

		if (!state.getFluidState().isEmpty())
		{
			boolean lava = state.getFluidState().is(FluidTags.LAVA);
			Identifier sprite = Identifier.withDefaultNamespace(lava ? "block/lava_still" : "block/water_still");

			for (int i = 0; i < names.length; i++)
			{
				names[i] = sprite;
				tints[i] = lava ? NO_TINT : defaultWaterTint();
			}

			return new Resolved(names, tints);
		}

		try
		{
			BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
			List<BlockStateModelPart> parts = new ArrayList<>();
			model.collectParts(RandomSource.create(42L), parts);

			for (Direction direction : Direction.values())
			{
				BakedQuad quad = quadFor(parts, direction);

				if (quad != null)
				{
					names[direction.ordinal()] = quad.materialInfo().sprite().contents().name();
					tints[direction.ordinal()] = tintFor(state, quad.materialInfo().tintIndex());
				}
			}

			Identifier particle = model.particleMaterial().sprite().contents().name();

			for (int i = 0; i < names.length; i++)
			{
				if (names[i] == null)
				{
					names[i] = particle;
				}
			}
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.debug("No model textures for {}", state, e);
		}

		return new Resolved(names, tints);
	}

	public static int tintFor(BlockState state, int tintIndex)
	{
		if (tintIndex < 0)
		{
			return NO_TINT;
		}

		try
		{
			BlockTintSource source = Minecraft.getInstance().getBlockColors().getTintSource(state, tintIndex);

			if (source == null)
			{
				return NO_TINT;
			}

			return 0xFF000000 | (source.color(state) & 0xFFFFFF);
		}
		catch (Exception e)
		{
			return NO_TINT;
		}
	}

	private static @Nullable BakedQuad quadFor(List<BlockStateModelPart> parts, @Nullable Direction direction)
	{
		for (BlockStateModelPart part : parts)
		{
			List<BakedQuad> quads = part.getQuads(direction);

			if (!quads.isEmpty())
			{
				return quads.getFirst();
			}
		}

		if (direction == null)
		{
			return null;
		}

		return quadFor(parts, null);
	}

	public static Faces load(Resolved resolved, BlockState state)
	{
		Faces faces = new Faces();
		MapColor mapColor = state.getBlock().defaultMapColor();

		if (mapColor != null && mapColor.col != 0)
		{
			faces.averageColor = 0xFF000000 | mapColor.col;
		}

		for (int i = 0; i < resolved.sprites().length; i++)
		{
			if (resolved.sprites()[i] != null)
			{
				faces.byFace[i] = texture(resolved.sprites()[i]);
			}

			faces.tints[i] = resolved.tints()[i];
		}

		faces.averageColor = tint(faces.averageColor, resolved.tints()[Direction.UP.ordinal()]);
		return faces;
	}

	static synchronized @Nullable Texture texture(Identifier sprite)
	{
		Texture cached = CACHE.get(sprite);

		if (cached != null || CACHE.containsKey(sprite))
		{
			return cached;
		}

		Texture loaded = read(sprite);
		CACHE.put(sprite, loaded);
		return loaded;
	}

	private static @Nullable Texture read(Identifier sprite)
	{
		Identifier file = Identifier.fromNamespaceAndPath(
				sprite.getNamespace(), "textures/" + sprite.getPath() + ".png");
		Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);

		if (resource.isEmpty())
		{
			return null;
		}

		try (InputStream input = resource.get().open())
		{
			NativeImage image = NativeImage.read(input);
			int width = image.getWidth();
			int height = image.getHeight();

			boolean animated = height > width && height % width == 0;
			int frameHeight = animated ? width : height;
			int frameCount = animated ? height / frameHeight : 1;
			int frameTicks = animated ? readFrametime(sprite) : 1;

			int[][][] frames = new int[frameCount][][];

			for (int f = 0; f < frameCount; f++)
			{
				int[] pixels = new int[width * frameHeight];
				int baseY = f * frameHeight;

				for (int y = 0; y < frameHeight; y++)
				{
					for (int x = 0; x < width; x++)
					{
						pixels[y * width + x] = image.getPixel(x, baseY + y);
					}
				}

				frames[f] = buildMips(pixels, width, frameHeight);
			}

			image.close();
			return new Texture(frames, width, frameHeight, frameTicks);
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.debug("Could not read texture {}", file, e);
			return null;
		}
	}

	// A targeted parse rather than a full JSON model: the file is small and frametime is a plain integer
	private static int readFrametime(Identifier sprite)
	{
		Identifier meta = Identifier.fromNamespaceAndPath(
				sprite.getNamespace(), "textures/" + sprite.getPath() + ".png.mcmeta");

		try
		{
			Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(meta);

			if (resource.isEmpty())
			{
				return 1;
			}

			try (InputStream input = resource.get().open())
			{
				String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
				java.util.regex.Matcher matcher =
						java.util.regex.Pattern.compile("\"frametime\"\\s*:\\s*(\\d+)").matcher(text);

				if (matcher.find())
				{
					return Math.max(1, Integer.parseInt(matcher.group(1)));
				}
			}
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.debug("Could not read animation metadata {}", meta, e);
		}

		return 1;
	}

	public static void clear()
	{
		synchronized (BlockTextures.class)
		{
			CACHE.clear();
		}
	}
}

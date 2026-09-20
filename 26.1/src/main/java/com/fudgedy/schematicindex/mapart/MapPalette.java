package com.fudgedy.schematicindex.mapart;

import net.minecraft.world.level.material.MapColor;

// The 61 usable MapColor groups, each with the survival blocks that carry that colour; colours come
// straight from Minecraft's own MapColor table so the preview matches what a map item shows
public final class MapPalette
{
	// Shade indices equal MapColor.Brightness ids; LOWEST 135 is never produced by a built map
	public static final int SHADE_LOW = 0;
	public static final int SHADE_NORMAL = 1;
	public static final int SHADE_HIGH = 2;
	public static final int SHADE_LOWEST = 3;
	public static final int SHADES = 3;
	public static final int ALL_SHADES = 4;
	public static final int GROUPS = 62;
	public static final int WATER_ID = 12;

	public static final String SUPPORT_BLOCK = "minecraft:cobblestone";

	public static final int PRESET_NONE = 0;
	public static final int PRESET_EVERYTHING = 1;
	public static final int PRESET_CARPETS = 2;
	public static final int PRESET_GREYSCALE = 3;
	public static final String[] PRESET_LABELS = {"None", "Everything", "Carpets", "Greyscale"};

	private static final String CODE_PREFIX = "SIP1:";
	private static final String CODE_DIGITS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
	private static final int[] GREYSCALE_IDS = {8, 22, 21, 29, 11, 59};

	public record Option(String block, String label, boolean needsSupport)
	{
	}

	public record Group(int id, String label, Option[] options)
	{
		public String block(int index)
		{
			return this.option(index).block();
		}

		public boolean supportFor(int index)
		{
			return this.option(index).needsSupport();
		}

		public Option option(int index)
		{
			return this.options[Math.floorMod(index, this.options.length)];
		}

		public int indexOf(String block)
		{
			for (int i = 0; i < this.options.length; i++)
			{
				if (this.options[i].block().equals(block))
				{
					return i;
				}
			}

			return -1;
		}
	}

	private static final Group[] BY_ID = new Group[GROUPS];

	// Rows in the order the block picker lists them: the wool white sits with the dyed colours
	public static final int[] DISPLAY_ORDER = {
			1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13, 14, 8, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
			31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59,
			60, 61};

	static
	{
		define(1, "Grass", "grass_block", "slime_block");
		define(2, "Sand", "sand*", "sandstone", "sandstone_slab", "birch_log[axis=y]", "birch_planks", "birch_slab",
				"glowstone", "end_stone", "end_stone_bricks", "end_stone_brick_slab", "bone_block", "birch_pressure_plate*",
				"ochre_froglight", "birch_wood", "stripped_birch_log[axis=y]", "stripped_birch_wood", "chiseled_sandstone",
				"cut_sandstone", "cut_sandstone_slab", "smooth_sandstone", "smooth_sandstone_slab", "scaffolding*");
		define(3, "Wool", "cobweb", "mushroom_stem", "white_candle*");
		define(4, "Fire", "tnt|TNT", "redstone_block|Block of Redstone");
		define(5, "Ice", "ice", "packed_ice", "blue_ice");
		define(6, "Metal", "iron_block|Block of Iron", "iron_trapdoor",
				"heavy_weighted_pressure_plate*|Weighted Pressure Plate (Heavy)", "brewing_stand", "lodestone",
				"pale_oak_leaves[persistent=true]");
		define(7, "Plant", "oak_leaves[persistent=true]|Leaves (Oak)",
				"spruce_leaves[persistent=true]|Leaves (Spruce)", "birch_leaves[persistent=true]|Leaves (Birch)",
				"jungle_leaves[persistent=true]|Leaves (Jungle)", "acacia_leaves[persistent=true]|Leaves (Acacia)",
				"dark_oak_leaves[persistent=true]|Leaves (Dark Oak)", "azalea_leaves[persistent=true]|Leaves (Azalea)",
				"bamboo_block[axis=x]|Block of Bamboo (horizontal)", "mangrove_leaves[persistent=true]");
		define(8, "Snow", "white_wool", "white_carpet*", "white_stained_glass", "white_concrete",
				"white_concrete_powder*", "white_glazed_terracotta", "snow_block", "snow*|Snow Layer");
		define(9, "Clay", "clay");
		define(10, "Dirt", "jungle_log[axis=y]", "jungle_planks", "jungle_slab", "dirt", "coarse_dirt", "rooted_dirt",
				"jukebox", "granite", "granite_slab", "jungle_pressure_plate*", "packed_mud", "jungle_wood",
				"stripped_jungle_log[axis=y]", "stripped_jungle_wood", "polished_granite", "polished_granite_slab");
		define(11, "Stone", "cobblestone", "cobblestone_slab", "mossy_cobblestone", "mossy_cobblestone_slab", "stone",
				"stone_slab", "smooth_stone_slab", "stone_bricks", "stone_brick_slab", "andesite", "andesite_slab",
				"bedrock", "acacia_log[axis=x]", "gravel*", "stone_pressure_plate*", "chiseled_stone_bricks",
				"cracked_stone_bricks", "mossy_stone_bricks", "mossy_stone_brick_slab", "polished_andesite",
				"polished_andesite_slab", "smooth_stone", "pale_oak_log[axis=x]", "pale_oak_wood");
		define(12, "Water", "water*|Water (Unsupported!)");
		define(13, "Wood", "oak_log[axis=y]", "oak_planks", "oak_slab", "crafting_table", "oak_pressure_plate*",
				"oak_wood", "stripped_oak_log[axis=y]", "stripped_oak_wood", "petrified_oak_slab");
		define(14, "Quartz", "birch_log[axis=x]", "diorite", "diorite_slab", "quartz_block", "quartz_slab",
				"sea_lantern", "chiseled_quartz_block", "quartz_bricks", "quartz_pillar", "smooth_quartz",
				"smooth_quartz_slab", "polished_diorite", "polished_diorite_slab", "pale_oak_log[axis=y]", "pale_oak_planks",
				"pale_oak_slab", "stripped_pale_oak_log[axis=y]", "stripped_pale_oak_wood", "pale_oak_pressure_plate*",
				"target");
		define(15, "Orange", "orange_wool", "orange_carpet*", "orange_stained_glass", "orange_concrete",
				"orange_concrete_powder*", "orange_glazed_terracotta", "pumpkin", "acacia_log[axis=y]", "acacia_planks",
				"acacia_slab", "red_sand*", "red_sandstone", "red_sandstone_slab", "terracotta", "honey_block",
				"honeycomb_block", "raw_copper_block|Block Of Raw Copper", "waxed_copper_block|Waxed Block Of Copper",
				"waxed_cut_copper_slab", "acacia_pressure_plate*", "carved_pumpkin", "chiseled_red_sandstone",
				"cut_red_sandstone", "cut_red_sandstone_slab", "smooth_red_sandstone", "smooth_red_sandstone_slab",
				"stripped_acacia_log[axis=y]", "stripped_acacia_wood", "waxed_cut_copper", "waxed_chiseled_copper");
		define(16, "Magenta", "magenta_wool", "magenta_carpet*", "magenta_stained_glass", "magenta_concrete",
				"magenta_concrete_powder*", "magenta_glazed_terracotta", "purpur_block", "purpur_slab", "purpur_pillar");
		define(17, "Light Blue", "light_blue_wool", "light_blue_carpet*", "light_blue_stained_glass",
				"light_blue_concrete", "light_blue_concrete_powder*", "light_blue_glazed_terracotta");
		define(18, "Yellow", "yellow_wool", "yellow_carpet*", "yellow_stained_glass", "yellow_concrete",
				"yellow_concrete_powder*", "yellow_glazed_terracotta", "hay_block|Hay Bale", "sponge|Sponge (any)",
				"bamboo_block[axis=y]|Block of Bamboo (vertical)", "bamboo_planks", "bamboo_slab", "bamboo_mosaic",
				"bamboo_mosaic_slab", "bamboo_pressure_plate*", "stripped_bamboo_block[axis=y]", "sulfur", "sulfur_slab",
				"sulfur_bricks", "sulfur_brick_slab", "polished_sulfur", "polished_sulfur_slab", "chiseled_sulfur");
		define(19, "Lime", "lime_wool", "lime_carpet*", "lime_stained_glass", "lime_concrete", "lime_concrete_powder*",
				"lime_glazed_terracotta", "melon");
		define(20, "Pink", "pink_wool", "pink_carpet*", "pink_stained_glass", "pink_concrete", "pink_concrete_powder*",
				"pink_glazed_terracotta", "pearlescent_froglight", "cherry_leaves[persistent=true]");
		define(21, "Gray", "gray_wool", "gray_carpet*", "gray_stained_glass", "gray_concrete", "gray_concrete_powder*",
				"gray_glazed_terracotta", "dead_tube_coral_block", "dead_brain_coral_block", "dead_bubble_coral_block",
				"dead_fire_coral_block", "dead_horn_coral_block", "tinted_glass", "acacia_wood");
		define(22, "Light Gray", "light_gray_wool", "light_gray_carpet*", "light_gray_stained_glass",
				"light_gray_concrete", "light_gray_concrete_powder*", "light_gray_glazed_terracotta", "pale_moss_block");
		define(23, "Cyan", "cyan_wool", "cyan_carpet*", "cyan_stained_glass", "cyan_concrete", "cyan_concrete_powder*",
				"cyan_glazed_terracotta", "prismarine", "prismarine_slab");
		define(24, "Purple", "purple_wool", "purple_carpet*", "purple_stained_glass", "purple_concrete",
				"purple_concrete_powder*", "purple_glazed_terracotta", "mycelium", "amethyst_block");
		define(25, "Blue", "blue_wool", "blue_carpet*", "blue_stained_glass", "blue_concrete", "blue_concrete_powder*",
				"blue_glazed_terracotta");
		define(26, "Brown", "brown_wool", "brown_carpet*", "brown_stained_glass", "brown_concrete",
				"brown_concrete_powder*", "brown_glazed_terracotta", "dark_oak_log|Dark Oak Log (any direction)",
				"dark_oak_planks", "dark_oak_slab", "spruce_log[axis=x]", "soul_sand", "soul_soil",
				"dark_oak_pressure_plate*", "dark_oak_wood", "stripped_dark_oak_log[axis=y]", "stripped_dark_oak_wood");
		define(27, "Green", "green_wool", "green_carpet*", "green_stained_glass", "green_concrete",
				"green_concrete_powder*", "green_glazed_terracotta", "dried_kelp_block", "moss_block");
		define(28, "Red", "red_wool", "red_carpet*", "red_stained_glass", "red_concrete", "red_concrete_powder*",
				"red_glazed_terracotta", "bricks", "brick_slab|Bricks Slab", "nether_wart_block", "shroomlight",
				"mangrove_log[axis=y]", "mangrove_planks", "mangrove_slab", "mangrove_pressure_plate*", "mangrove_wood",
				"stripped_mangrove_log[axis=y]", "stripped_mangrove_wood", "cinnabar", "cinnabar_slab", "cinnabar_bricks",
				"cinnabar_brick_slab", "polished_cinnabar", "polished_cinnabar_slab", "chiseled_cinnabar");
		define(29, "Black", "black_wool", "black_carpet*", "black_stained_glass", "black_concrete",
				"black_concrete_powder*", "black_glazed_terracotta", "coal_block|Block of Coal", "obsidian",
				"crying_obsidian", "blackstone", "blackstone_slab", "basalt", "netherite_block|Block Of Netherite",
				"polished_blackstone_pressure_plate*", "sculk", "sculk_catalyst", "sculk_shrieker", "polished_blackstone",
				"polished_blackstone_slab", "polished_blackstone_bricks", "polished_blackstone_brick_slab",
				"cracked_polished_blackstone_bricks", "chiseled_polished_blackstone", "gilded_blackstone", "polished_basalt",
				"smooth_basalt", "ancient_debris");
		define(30, "Gold", "gold_block|Block of Gold",
				"light_weighted_pressure_plate*|Weighted Pressure Plate (Light)", "raw_gold_block|Block Of Raw Gold",
				"potent_sulfur");
		define(31, "Diamond", "diamond_block|Block of Diamond", "prismarine_bricks", "prismarine_brick_slab",
				"dark_prismarine", "dark_prismarine_slab", "beacon");
		define(32, "Lapis", "lapis_block|Lapis Lazuli Block");
		define(33, "Emerald", "emerald_block|Block of Emerald");
		define(34, "Podzol", "spruce_log[axis=y]", "spruce_planks", "spruce_slab", "oak_log[axis=x]",
				"jungle_log[axis=x]", "podzol", "spruce_pressure_plate*", "mangrove_log[axis=x]", "mangrove_roots",
				"spruce_wood", "stripped_spruce_log[axis=y]", "stripped_spruce_wood");
		define(35, "Nether", "netherrack", "nether_bricks|Nether Brick", "nether_brick_slab", "magma_block",
				"red_nether_bricks", "red_nether_brick_slab", "chiseled_nether_bricks", "cracked_nether_bricks");
		define(36, "White Terracotta", "white_terracotta", "calcite", "cherry_planks", "cherry_slab",
				"cherry_log[axis=y]", "stripped_cherry_log[axis=y]", "cherry_pressure_plate*");
		define(37, "Orange Terracotta", "orange_terracotta", "resin_block", "resin_bricks", "resin_brick_slab",
				"chiseled_resin_bricks", "redstone_lamp");
		define(38, "Magenta Terracotta", "magenta_terracotta");
		define(39, "Light Blue Terracotta", "light_blue_terracotta");
		define(40, "Yellow Terracotta", "yellow_terracotta");
		define(41, "Lime Terracotta", "lime_terracotta");
		define(42, "Pink Terracotta", "pink_terracotta", "stripped_cherry_log[axis=x]", "stripped_cherry_wood");
		define(43, "Gray Terracotta", "gray_terracotta", "tuff", "cherry_log[axis=x]", "cherry_wood", "tuff_slab",
				"polished_tuff", "polished_tuff_slab", "tuff_bricks", "tuff_brick_slab", "chiseled_tuff",
				"chiseled_tuff_bricks");
		define(44, "Light Gray Terracotta", "light_gray_terracotta", "waxed_exposed_copper",
				"waxed_exposed_cut_copper_slab", "mud_bricks", "mud_brick_slab", "waxed_exposed_cut_copper",
				"waxed_exposed_chiseled_copper");
		define(45, "Cyan Terracotta", "cyan_terracotta", "mud");
		define(46, "Purple Terracotta", "purple_terracotta");
		define(47, "Blue Terracotta", "blue_terracotta");
		define(48, "Brown Terracotta", "brown_terracotta", "dripstone_block", "pointed_dripstone*");
		define(49, "Green Terracotta", "green_terracotta");
		define(50, "Red Terracotta", "red_terracotta", "decorated_pot");
		define(51, "Black Terracotta", "black_terracotta");
		define(52, "Crimson Nylium", "crimson_nylium");
		define(53, "Crimson Stem", "crimson_stem", "stripped_crimson_stem", "crimson_planks", "crimson_slab",
				"crimson_pressure_plate*");
		define(54, "Crimson Hyphae", "crimson_hyphae", "stripped_crimson_hyphae");
		define(55, "Warped Nylium", "warped_nylium", "waxed_oxidized_copper", "waxed_oxidized_cut_copper_slab",
				"waxed_oxidized_cut_copper", "waxed_oxidized_chiseled_copper");
		define(56, "Warped Stem", "warped_stem", "stripped_warped_stem", "warped_planks", "warped_slab",
				"waxed_weathered_copper", "waxed_weathered_cut_copper_slab", "warped_pressure_plate*",
				"waxed_weathered_cut_copper", "waxed_weathered_chiseled_copper");
		define(57, "Warped Hyphae", "warped_hyphae", "stripped_warped_hyphae");
		define(58, "Warped Wart", "warped_wart_block");
		define(59, "Deepslate", "deepslate", "cobbled_deepslate", "cobbled_deepslate_slab", "polished_deepslate",
				"polished_deepslate_slab", "deepslate_bricks", "deepslate_brick_slab", "deepslate_tiles",
				"deepslate_tile_slab", "chiseled_deepslate", "cracked_deepslate_bricks", "cracked_deepslate_tiles");
		define(60, "Raw Iron", "raw_iron_block|Block Of Raw Iron");
		define(61, "Glow Lichen", "glow_lichen[down=true]*", "verdant_froglight");
	}

	private MapPalette()
	{
	}

	// A spec reads name[props] with a trailing '*' when the block needs one beneath it, then an
	// optional |Label where the shown name is not the block id in title case
	private static void define(int id, String label, String... specs)
	{
		Option[] options = new Option[specs.length];

		for (int i = 0; i < specs.length; i++)
		{
			String spec = specs[i];
			String shown = null;
			int bar = spec.indexOf('|');

			if (bar >= 0)
			{
				shown = spec.substring(bar + 1);
				spec = spec.substring(0, bar);
			}

			boolean support = spec.endsWith("*");
			String block = support ? spec.substring(0, spec.length() - 1) : spec;
			options[i] = new Option("minecraft:" + block, shown == null ? displayName(block) : shown, support);
		}

		BY_ID[id] = new Group(id, label, options);
	}

	public static Group group(int id)
	{
		return id >= 0 && id < GROUPS ? BY_ID[id] : null;
	}

	// Gold, emerald and water are off by default so a casual conversion does not demand ore blocks
	// or an unbuildable colour
	public static boolean enabledByDefault(int id)
	{
		return BY_ID[id] != null && id != 30 && id != 33 && id != WATER_ID;
	}

	public static int argb(int id, int shade)
	{
		return MapColor.byId(id).calculateARGBColor(MapColor.Brightness.byId(shade));
	}

	public static void applyPreset(int preset, boolean[] enabled, int[] blockChoice)
	{
		for (int id = 1; id < GROUPS; id++)
		{
			Group group = BY_ID[id];
			enabled[id] = false;
			blockChoice[id] = 0;

			if (group == null)
			{
				continue;
			}

			switch (preset)
			{
				case PRESET_EVERYTHING -> enabled[id] = id != WATER_ID;
				case PRESET_CARPETS -> {
					int carpet = carpetIndex(group);
					enabled[id] = carpet >= 0;
					blockChoice[id] = Math.max(0, carpet);
				}
				case PRESET_GREYSCALE -> {
					for (int grey : GREYSCALE_IDS)
					{
						enabled[id] |= grey == id;
					}
				}
				default -> {
				}
			}
		}
	}

	// One digit per group: 0 disabled, otherwise the option index plus one
	public static String encode(boolean[] enabled, int[] blockChoice)
	{
		StringBuilder code = new StringBuilder(CODE_PREFIX);

		for (int id = 1; id < GROUPS; id++)
		{
			int digit = BY_ID[id] == null || !enabled[id] ? 0 : Math.floorMod(blockChoice[id], BY_ID[id].options().length) + 1;
			code.append(CODE_DIGITS.charAt(Math.min(digit, CODE_DIGITS.length() - 1)));
		}

		return code.toString();
	}

	public static boolean decode(String code, boolean[] enabled, int[] blockChoice)
	{
		String trimmed = code == null ? "" : code.trim();

		if (!trimmed.startsWith(CODE_PREFIX) || trimmed.length() != CODE_PREFIX.length() + GROUPS - 1)
		{
			return false;
		}

		for (int id = 1; id < GROUPS; id++)
		{
			int digit = CODE_DIGITS.indexOf(trimmed.charAt(CODE_PREFIX.length() + id - 1));

			if (digit < 0)
			{
				return false;
			}

			Group group = BY_ID[id];
			enabled[id] = group != null && digit > 0;
			blockChoice[id] = group == null || digit == 0 ? 0 : Math.min(digit - 1, group.options().length - 1);
		}

		return true;
	}

	public static String displayName(String block)
	{
		String name = block.startsWith("minecraft:") ? block.substring(10) : block;
		int bracket = name.indexOf('[');
		String suffix = "";

		if (bracket >= 0)
		{
			String props = name.substring(bracket);
			name = name.substring(0, bracket);
			suffix = props.contains("axis=y") ? " (vertical)" : (props.contains("axis=x") ? " (horizontal)" : "");
		}

		StringBuilder out = new StringBuilder(name.length());
		boolean upper = true;

		for (int i = 0; i < name.length(); i++)
		{
			char c = name.charAt(i);

			if (c == '_')
			{
				out.append(' ');
				upper = true;
				continue;
			}

			out.append(upper ? Character.toUpperCase(c) : c);
			upper = false;
		}

		return out.append(suffix).toString();
	}

	private static int carpetIndex(Group group)
	{
		for (int i = 0; i < group.options().length; i++)
		{
			if (group.options()[i].block().endsWith("_carpet"))
			{
				return i;
			}
		}

		return -1;
	}
}

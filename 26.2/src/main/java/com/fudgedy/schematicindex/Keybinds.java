package com.fudgedy.schematicindex;

import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Toasts;
import com.fudgedy.schematicindex.mapart.MapCorners;
import com.fudgedy.schematicindex.mixin.KeyMappingCategoryAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import fi.dy.masa.malilib.event.TickHandler;
import fi.dy.masa.malilib.interfaces.IClientTickHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

// Vanilla key mappings under their own Controls category, polled once a tick; the game saves them to options.txt
public final class Keybinds implements IClientTickHandler
{
	public static final KeyMapping.Category CATEGORY = firstCategory();
	public static final KeyMapping OPEN =
			new KeyMapping("key.schematicindex.open", InputConstants.KEY_APOSTROPHE, CATEGORY);
	public static final KeyMapping TOGGLE_CORNERS =
			new KeyMapping("key.schematicindex.toggle_corners", InputConstants.UNKNOWN.getValue(), CATEGORY);

	private static final Keybinds INSTANCE = new Keybinds();

	private Keybinds()
	{
	}

	public static void register()
	{
		TickHandler.getInstance().registerClientTickHandler(INSTANCE);
	}

	// The controls screen orders categories by their position in KeyMapping.Category's sort list, and
	// register() only appends, so the fresh entry is moved to the front to sit above Movement
	private static KeyMapping.Category firstCategory()
	{
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SchematicIndexMod.MOD_ID, "main"));
		List<KeyMapping.Category> order = KeyMappingCategoryAccessor.schematicindex$sortOrder();
		order.remove(category);
		order.add(0, category);
		return category;
	}

	// Other mods may register categories later, so the Controls screen re-asserts the order as it builds
	public static void ensureCategoryFirst()
	{
		List<KeyMapping.Category> order = KeyMappingCategoryAccessor.schematicindex$sortOrder();
		order.remove(CATEGORY);
		order.add(0, CATEGORY);
	}

	public static KeyMapping[] mappings()
	{
		return new KeyMapping[]{OPEN, TOGGLE_CORNERS};
	}

	@Override
	public void onClientTick(Minecraft mc)
	{
		while (OPEN.consumeClick())
		{
			if (mc.gui.screen() == null)
			{
				mc.setScreenAndShow(new IndexScreen(null));
			}
		}

		while (TOGGLE_CORNERS.consumeClick())
		{
			boolean on = !Settings.mapCorners();
			MapCorners.enable(on, mc);
			Toasts.push("Map corners", on ? "Shown at Y " + Settings.cornerHeight() : "Hidden", new ItemStack(Items.FILLED_MAP));
		}
	}
}

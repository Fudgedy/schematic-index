package com.fudgedy.schematicindex;

import com.fudgedy.schematicindex.gui.IndexScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

// Opens the catalogue from Mod Menu's mod list. Mod Menu is compile-only, so this entrypoint is simply
// never constructed when it is absent.
public final class ModMenuIntegration implements ModMenuApi
{
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory()
	{
		return IndexScreen::new;
	}
}

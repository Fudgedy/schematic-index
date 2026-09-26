package com.fudgedy.schematicindex.catalogue;

import net.minecraft.client.Minecraft;

// Item holders aren't bound until the loading overlay clears, so anything built before then (an ItemStack,
// a toast icon) can NPE if it runs off a background callback that outraces resource loading
public final class ClientReady
{
	private ClientReady()
	{
	}

	public static boolean ready()
	{
		Minecraft mc = Minecraft.getInstance();
		return mc != null && mc.getOverlay() == null;
	}
}

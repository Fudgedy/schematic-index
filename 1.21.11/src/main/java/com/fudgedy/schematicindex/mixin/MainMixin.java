package com.fudgedy.schematicindex.mixin;

import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

// Main's static initializer sets java.awt.headless=true and AWT caches it once a toolkit class loads, so the
// only place to clear it is before main runs. Skipped on macOS, where AWT and GLFW fight over the AppKit thread
@Mixin(Main.class)
public abstract class MainMixin
{
	@Inject(method = "main", at = @At("HEAD"))
	private static void schematicindex$allowAwtClipboard(CallbackInfo info)
	{
		if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac"))
		{
			return;
		}

		System.setProperty("java.awt.headless", "false");
	}
}

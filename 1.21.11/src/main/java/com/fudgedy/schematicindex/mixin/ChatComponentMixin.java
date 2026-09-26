package com.fudgedy.schematicindex.mixin;

import com.fudgedy.schematicindex.ModTags;
import com.fudgedy.schematicindex.Settings;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// The name-only selector lands on every addMessage overload, and on 1.21.11 the public ones chain into each
// other, so a message can pass through here twice; the zero-width marker decorateNamesIn plants makes the
// second pass a no-op, which is what keeps this safe rather than the overload count
@Mixin(ChatComponent.class)
public class ChatComponentMixin
{
	@ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true, require = 0)
	private Component schematicindex$styleChatNames(Component message)
	{
		if (!Settings.chatNames())
		{
			return message;
		}

		Component decorated = ModTags.decorateNamesIn(message);
		return decorated != null ? decorated : message;
	}
}

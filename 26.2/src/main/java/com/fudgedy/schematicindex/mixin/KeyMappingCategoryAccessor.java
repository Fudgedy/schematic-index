package com.fudgedy.schematicindex.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(KeyMapping.Category.class)
public interface KeyMappingCategoryAccessor
{
	@Accessor("SORT_ORDER")
	static List<KeyMapping.Category> schematicindex$sortOrder()
	{
		throw new AssertionError();
	}
}

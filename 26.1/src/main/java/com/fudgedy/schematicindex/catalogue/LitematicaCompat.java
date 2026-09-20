package com.fudgedy.schematicindex.catalogue;

import com.fudgedy.schematicindex.SchematicIndexMod;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.malilib.util.data.tag.CompoundData;
import fi.dy.masa.malilib.util.data.tag.converter.DataConverterNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// Litematica 0.28.6 changed getBlockEntityMapForRegion's return type from vanilla CompoundTag to
// MaLiLib CompoundData, so a direct call links against one descriptor and throws on the other
public final class LitematicaCompat
{
	private static Method blockEntityMap;
	private static boolean lookedUp;

	private LitematicaCompat()
	{
	}

	public static Map<BlockPos, CompoundTag> blockEntities(LitematicaSchematic schematic, String region)
	{
		Method method = resolve();

		if (method == null)
		{
			return Collections.emptyMap();
		}

		Object raw;

		try
		{
			raw = method.invoke(schematic, region);
		}
		catch (Exception e)
		{
			SchematicIndexMod.LOGGER.debug("Block entities unavailable for region {}", region, e);
			return Collections.emptyMap();
		}

		if (!(raw instanceof Map<?, ?> map))
		{
			return Collections.emptyMap();
		}

		Map<BlockPos, CompoundTag> out = new HashMap<>(map.size());

		for (Map.Entry<?, ?> entry : map.entrySet())
		{
			if (!(entry.getKey() instanceof BlockPos pos))
			{
				continue;
			}

			if (entry.getValue() instanceof CompoundTag tag)
			{
				out.put(pos, tag);
			}
			else if (entry.getValue() instanceof CompoundData data)
			{
				out.put(pos, DataConverterNbt.toVanillaCompound(data));
			}
		}

		return out;
	}

	private static synchronized Method resolve()
	{
		if (lookedUp)
		{
			return blockEntityMap;
		}

		lookedUp = true;

		try
		{
			blockEntityMap = LitematicaSchematic.class.getMethod("getBlockEntityMapForRegion", String.class);
		}
		catch (NoSuchMethodException e)
		{
			SchematicIndexMod.LOGGER.warn("Litematica has no getBlockEntityMapForRegion; previews lose block entity data");
		}

		return blockEntityMap;
	}
}

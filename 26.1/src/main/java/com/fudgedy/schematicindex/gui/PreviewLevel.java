package com.fudgedy.schematicindex.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

// BlockPos indexes the Model's voxel grid directly and anything outside it reads as air. Real neighbour
// states are what give the block renderer its face culling and AO
final class PreviewLevel implements BlockAndTintGetter
{
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	private static final int FALLBACK_TINT = 0xFF91BD59;

	private final SchematicPreview.Model model;
	private final BlockState[] states;

	private final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();

	private @Nullable Biome biome;
	private boolean biomeResolved;

	PreviewLevel(SchematicPreview.Model model)
	{
		this.model = model;
		this.states = model.states();
	}

	@Override
	public BlockState getBlockState(BlockPos pos)
	{
		int cell = this.model.at(pos.getX(), pos.getY(), pos.getZ());

		if (cell == 0)
		{
			return AIR;
		}

		BlockState state = cell - 1 < this.states.length ? this.states[cell - 1] : null;
		return state == null ? AIR : state;
	}

	@Override
	public FluidState getFluidState(BlockPos pos)
	{
		return getBlockState(pos).getFluidState();
	}

	@Override
	public @Nullable BlockEntity getBlockEntity(BlockPos pos)
	{
		BlockState state = getBlockState(pos);

		if (!(state.getBlock() instanceof EntityBlock entityBlock))
		{
			return null;
		}

		BlockPos key = pos.immutable();
		BlockEntity existing = this.blockEntities.get(key);

		if (existing != null)
		{
			return existing;
		}

		// newBlockEntity may return null even for an EntityBlock, so only real instances are cached
		BlockEntity created = entityBlock.newBlockEntity(key, state);

		if (created != null)
		{
			this.blockEntities.put(key, created);
		}

		return created;
	}

	// CardinalLighting.DEFAULT carries the vanilla multipliers, and the renderer decides whether to apply them
	@Override
	public CardinalLighting cardinalLighting()
	{
		return CardinalLighting.DEFAULT;
	}

	@Override
	public int getBlockTint(BlockPos pos, ColorResolver resolver)
	{
		Biome resolved = biome();
		return resolved != null ? resolver.getColor(resolved, pos.getX(), pos.getZ()) : FALLBACK_TINT;
	}

	@Override
	public int getBrightness(LightLayer layer, BlockPos pos)
	{
		return 15;
	}

	@Override
	public int getRawBrightness(BlockPos pos, int ambientDarkness)
	{
		return 15;
	}

	@Override
	public int getLightEmission(BlockPos pos)
	{
		return getBlockState(pos).getLightEmission();
	}

	@Override
	public @Nullable LevelLightEngine getLightEngine()
	{
		return null;
	}

	@Override
	public int getHeight()
	{
		return this.model.sizeY();
	}

	@Override
	public int getMinY()
	{
		return 0;
	}

	private @Nullable Biome biome()
	{
		if (this.biomeResolved)
		{
			return this.biome;
		}

		this.biomeResolved = true;

		try
		{
			Minecraft client = Minecraft.getInstance();

			RegistryAccess access = client.level != null ? client.level.registryAccess() : null;

			if (access != null)
			{
				this.biome = access.lookupOrThrow(Registries.BIOME).getValue(Biomes.PLAINS);
			}
		}
		catch (Exception e)
		{
			this.biome = null;
		}

		return this.biome;
	}
}

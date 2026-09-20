package com.fudgedy.schematicindex.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

// PreviewLevel is enough for the terrain mesher, but block entities need a real Level: hasLevel() gates
// extraction, and ChestRenderer walks neighbours to combine double chests. Only the slice those renderers
// touch is implemented; the rest is stubbed. A live ClientLevel supplies the registry access and dimension
// type, so create() returns null without one and the caller falls back to the item-form path
final class SchematicRenderLevel extends Level
{
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final int FALLBACK_TINT = 0xFF91BD59;

	// Always empty, so the chest renderer's scan for a cat sitting on the lid finds nothing
	private static final LevelEntityGetter<net.minecraft.world.entity.Entity> EMPTY_ENTITIES =
			new LevelEntityGetter<>()
			{
				@Override
				public @Nullable net.minecraft.world.entity.Entity get(int id)
				{
					return null;
				}

				@Override
				public @Nullable net.minecraft.world.entity.Entity get(UUID uuid)
				{
					return null;
				}

				@Override
				public Iterable<net.minecraft.world.entity.Entity> getAll()
				{
					return java.util.List.of();
				}

				@Override
				public <U extends net.minecraft.world.entity.Entity> void get(
						EntityTypeTest<net.minecraft.world.entity.Entity, U> test,
						net.minecraft.util.AbortableIterationConsumer<U> consumer)
						{
				}

				@Override
				public void get(AABB box, Consumer<net.minecraft.world.entity.Entity> consumer)
				{
				}

				@Override
				public <U extends net.minecraft.world.entity.Entity> void get(
						EntityTypeTest<net.minecraft.world.entity.Entity, U> test, AABB box,
						net.minecraft.util.AbortableIterationConsumer<U> consumer)
						{
				}
			};

	private final SchematicPreview.Model model;
	private final BlockState[] states;
	private final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();
	private @Nullable Biome biome;
	private boolean biomeResolved;

	private SchematicRenderLevel(WritableLevelData levelData, RegistryAccess registryAccess,
			Holder<DimensionType> dimensionType, SchematicPreview.Model model)
			{
		super(levelData, Level.OVERWORLD, registryAccess, dimensionType, true, false, 0L, 0);
		this.model = model;
		this.states = model.states();
	}

	static @Nullable SchematicRenderLevel create(SchematicPreview.Model model)
	{
		ClientLevel live = Minecraft.getInstance().level;

		if (live == null)
		{
			return null;
		}

		try
		{
			WritableLevelData data = (WritableLevelData) live.getLevelData();
			return new SchematicRenderLevel(data, live.registryAccess(), live.dimensionTypeRegistration(), model);
		}
		catch (Exception e)
		{
			return null;
		}
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

		BlockEntity created = entityBlock.newBlockEntity(key, state);

		if (created != null)
		{
			created.setLevel(this);

			// Restored from the schematic NBT, so the renderer draws real contents rather than defaults
			net.minecraft.nbt.CompoundTag tag = this.model.nbtAt(pos.getX(), pos.getY(), pos.getZ());

			if (tag != null && !tag.isEmpty())
			{
				try
				{
					created.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
							net.minecraft.util.ProblemReporter.DISCARDING, registryAccess(), tag));
				}
				catch (Exception e)
				{
				}
			}

			this.blockEntities.put(key, created);
		}

		return created;
	}

	@Override
	public float getShade(Direction direction, boolean shade)
	{
		if (!shade)
		{
			return 1.0F;
		}

		return switch (direction)
		{
			case UP -> 1.0F;
			case DOWN -> 0.5F;
			case NORTH, SOUTH -> 0.8F;
			case EAST, WEST -> 0.6F;
		};
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
	public int getBlockTint(BlockPos pos, ColorResolver resolver)
	{
		Biome resolved = biome();
		return resolved != null ? resolver.getColor(resolved, pos.getX(), pos.getZ()) : FALLBACK_TINT;
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
			this.biome = registryAccess().lookupOrThrow(Registries.BIOME).getValue(Biomes.PLAINS);
		}
		catch (Exception e)
		{
			this.biome = null;
		}

		return this.biome;
	}

	@Override
	protected LevelEntityGetter<net.minecraft.world.entity.Entity> getEntities()
	{
		return EMPTY_ENTITIES;
	}

	@Override
	public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags)
	{
	}

	@Override
	public void playSeededSound(@Nullable net.minecraft.world.entity.Entity source, double x, double y, double z,
			Holder<net.minecraft.sounds.SoundEvent> sound, net.minecraft.sounds.SoundSource category, float volume,
			float pitch, long seed)
			{
	}

	@Override
	public void playSeededSound(@Nullable net.minecraft.world.entity.Entity source,
			@Nullable net.minecraft.world.entity.Entity target, Holder<net.minecraft.sounds.SoundEvent> sound,
			net.minecraft.sounds.SoundSource category, float volume, float pitch, long seed)
			{
	}

	@Override
	public void explode(@Nullable net.minecraft.world.entity.Entity source,
			@Nullable net.minecraft.world.damagesource.DamageSource damageSource,
			@Nullable net.minecraft.world.level.ExplosionDamageCalculator calculator, double x, double y, double z,
			float radius, boolean fire, Level.ExplosionInteraction interaction,
			net.minecraft.core.particles.ParticleOptions smallParticle,
			net.minecraft.core.particles.ParticleOptions largeParticle,
			net.minecraft.util.random.WeightedList<net.minecraft.core.particles.ExplosionParticleInfo> particles,
			Holder<net.minecraft.sounds.SoundEvent> sound)
			{
	}

	@Override
	public String gatherChunkSourceStats()
	{
		return "";
	}

	@Override
	public void setRespawnData(net.minecraft.world.level.storage.LevelData.RespawnData data)
	{
	}

	@Override
	public net.minecraft.world.level.storage.LevelData.RespawnData getRespawnData()
	{
		return null;
	}

	@Override
	public @Nullable net.minecraft.world.entity.Entity getEntity(int id)
	{
		return null;
	}

	@Override
	public java.util.Collection<net.minecraft.world.entity.boss.enderdragon.EnderDragonPart> dragonParts()
	{
		return java.util.List.of();
	}

	@Override
	public net.minecraft.world.TickRateManager tickRateManager()
	{
		return null;
	}

	@Override
	public @Nullable net.minecraft.world.level.saveddata.maps.MapItemSavedData getMapData(
			net.minecraft.world.level.saveddata.maps.MapId id)
			{
		return null;
	}

	@Override
	public void destroyBlockProgress(int breakerId, BlockPos pos, int progress)
	{
	}

	@Override
	public net.minecraft.world.scores.Scoreboard getScoreboard()
	{
		return null;
	}

	@Override
	public net.minecraft.world.item.crafting.RecipeAccess recipeAccess()
	{
		return null;
	}

	@Override
	public net.minecraft.world.attribute.EnvironmentAttributeSystem environmentAttributes()
	{
		return null;
	}

	@Override
	public net.minecraft.world.item.alchemy.PotionBrewing potionBrewing()
	{
		return null;
	}

	@Override
	public net.minecraft.world.level.block.entity.FuelValues fuelValues()
	{
		return null;
	}

	@Override
	public void gameEvent(Holder<net.minecraft.world.level.gameevent.GameEvent> event, net.minecraft.world.phys.Vec3 pos,
			net.minecraft.world.level.gameevent.GameEvent.Context context)
			{
	}

	@Override
	public void levelEvent(@Nullable net.minecraft.world.entity.Entity source, int type, BlockPos pos, int data)
	{
	}

	// getBlockState, getBlockEntity and getBrightness all read the model directly, so no chunk source is used
	@Override
	public net.minecraft.world.level.chunk.ChunkSource getChunkSource()
	{
		return null;
	}

	@Override
	public java.util.List<? extends net.minecraft.world.entity.player.Player> players()
	{
		return java.util.List.of();
	}

	@Override
	public net.minecraft.world.flag.FeatureFlagSet enabledFeatures()
	{
		return net.minecraft.world.flag.FeatureFlags.VANILLA_SET;
	}

	@Override
	public int getSeaLevel()
	{
		return 63;
	}

	@Override
	public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z)
	{
		return registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
	}

	private final net.minecraft.world.level.border.WorldBorder worldBorder =
			new net.minecraft.world.level.border.WorldBorder();

	@Override
	public net.minecraft.world.level.border.WorldBorder getWorldBorder()
	{
		return this.worldBorder;
	}

	@Override
	public net.minecraft.world.ticks.LevelTickAccess<net.minecraft.world.level.block.Block> getBlockTicks()
	{
		return net.minecraft.world.ticks.BlackholeTickAccess.emptyLevelList();
	}

	@Override
	public net.minecraft.world.ticks.LevelTickAccess<net.minecraft.world.level.material.Fluid> getFluidTicks()
	{
		return net.minecraft.world.ticks.BlackholeTickAccess.emptyLevelList();
	}
}

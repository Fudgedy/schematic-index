package com.fudgedy.schematicindex;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;

// Logs once per server join which nametag mechanism the server uses on the local player, so a report of
// overlapping tags can be matched to a text_display passenger, a team visibility rule or a below-name score
public final class NametagDiagnostic
{
	// Servers apply teams and spawn display entities a moment after the join, so the probe waits this long
	private static final int SETTLE_TICKS = 100;

	private static ClientPacketListener probed;

	private NametagDiagnostic()
	{
	}

	public static void probe(Minecraft mc)
	{
		ClientPacketListener connection = mc.getConnection();
		LocalPlayer player = mc.player;

		if (connection == null || connection == probed || player == null || mc.level == null
				|| player.tickCount < SETTLE_TICKS)
		{
			return;
		}

		probed = connection;
		PlayerTeam team = player.getTeam();
		Objective belowName = mc.level.getScoreboard().getDisplayObjective(DisplaySlot.BELOW_NAME);
		SchematicIndexMod.LOGGER.debug("Nametag probe: textDisplayPassenger={} team={} visibility={} prefix={} belowName={}",
				hasTextDisplayPassenger(player),
				team == null ? null : team.getName(),
				team == null ? null : team.getNameTagVisibility(),
				team == null ? null : team.getPlayerPrefix(),
				belowName == null ? null : belowName.getName());
	}

	private static boolean hasTextDisplayPassenger(Entity entity)
	{
		for (Entity passenger : entity.getPassengers())
		{
			if (passenger instanceof Display.TextDisplay)
			{
				return true;
			}
		}

		return false;
	}
}

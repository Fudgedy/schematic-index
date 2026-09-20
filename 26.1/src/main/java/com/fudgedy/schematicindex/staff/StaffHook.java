package com.fudgedy.schematicindex.staff;

import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.gui.IndexScreen;
import org.jetbrains.annotations.Nullable;

import java.util.ServiceLoader;

// The seam between the community jar and the staff jar. Main never names a staff class: the staff
// source set registers an implementation through META-INF/services, and without one the Staff tab
// and the moderation controls do not exist at all
public interface StaffHook
{
	StaffScreen create(IndexScreen screen);

	// Resolved once per launch; null in the community jar, so the screen carries no staff state
	static @Nullable StaffScreen attach(IndexScreen screen)
	{
		return Holder.HOOK == null ? null : Holder.HOOK.create(screen);
	}

	final class Holder
	{
		private static final @Nullable StaffHook HOOK = resolve();

		private Holder()
		{
		}

		private static @Nullable StaffHook resolve()
		{
			try
			{
				for (StaffHook hook : ServiceLoader.load(StaffHook.class, StaffHook.class.getClassLoader()))
				{
					SchematicIndexMod.LOGGER.info("Staff build: {}", hook.getClass().getName());
					return hook;
				}
			}
			catch (Exception e)
			{
				SchematicIndexMod.LOGGER.warn("Staff hook lookup failed", e);
			}

			return null;
		}
	}
}

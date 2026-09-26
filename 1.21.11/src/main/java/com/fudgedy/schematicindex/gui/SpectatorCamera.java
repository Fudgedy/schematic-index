package com.fudgedy.schematicindex.gui;

import java.util.HashSet;
import java.util.Set;

// Free-fly movement shared by the detail view's 3D preview and Photo Mode: WASD relative to yaw/pitch,
// Space/Shift for up/down, scroll to change speed
public final class SpectatorCamera
{
	public static final String HINT = "Spectator: drag to look, WASD to fly, Space/Shift up/down, scroll to change speed.";
	private static final double MAX_SPEED = 120.0D; // blocks/sec

	private final Set<Integer> keys = new HashSet<>(); // GLFW key codes
	private long moveNanos; // nanoTime of the previous move tick, 0 when not moving
	private float speed = 1.0F;

	public static boolean isKey(int key)
	{
		return key == 87 || key == 83 || key == 65 || key == 68 || key == 32 || key == 340 || key == 341;
	}

	public void pressKey(int key)
	{
		this.keys.add(key);
	}

	public void releaseKey(int key)
	{
		this.keys.remove(key);
	}

	public void clearKeys()
	{
		this.keys.clear();
		this.moveNanos = 0L;
	}

	public void resetSpeed()
	{
		this.speed = 1.0F;
	}

	public void adjustSpeed(double scrollY)
	{
		float factor = scrollY > 0 ? 1.3F : 1.0F / 1.3F;
		this.speed = Math.max(0.005F, Math.min(12.0F, this.speed * factor));
	}

	// Wall-clock dt, so held keys glide instead of stepping once per key-repeat; eye is mutated in place
	public void tick(double[] eye, float yaw, float pitch, double step, double sizeX, double sizeY, double sizeZ)
	{
		if (this.keys.isEmpty())
		{
			this.moveNanos = 0L;
			return;
		}

		long now = System.nanoTime();

		if (this.moveNanos == 0L)
		{
			this.moveNanos = now;
			return;
		}

		// Clamped so a hitch cannot fling the camera across the scene in one jump
		double dt = Math.min((now - this.moveNanos) / 1_000_000_000.0D, 0.1D);
		this.moveNanos = now;

		if (dt <= 0.0D)
		{
			return;
		}

		double yawRadians = Math.toRadians(yaw);
		double pitchRadians = Math.toRadians(pitch);
		double cosPitch = Math.cos(pitchRadians);
		double forwardX = Math.sin(yawRadians) * cosPitch;
		double forwardY = -Math.sin(pitchRadians);
		double forwardZ = Math.cos(yawRadians) * cosPitch;
		// Must equal the renderer's screen-right, forward x worldUp, or A and D invert against the view
		double rightX = -Math.cos(yawRadians);
		double rightZ = Math.sin(yawRadians);

		double dist = Math.min(step * 15.0D * this.speed, MAX_SPEED) * dt;

		if (this.keys.contains(87))
		{ // W: forward
			eye[0] += forwardX * dist;
			eye[1] += forwardY * dist;
			eye[2] += forwardZ * dist;
		}

		if (this.keys.contains(83))
		{ // S: back
			eye[0] -= forwardX * dist;
			eye[1] -= forwardY * dist;
			eye[2] -= forwardZ * dist;
		}

		if (this.keys.contains(65))
		{ // A: strafe left
			eye[0] -= rightX * dist;
			eye[2] -= rightZ * dist;
		}

		if (this.keys.contains(68))
		{ // D: strafe right
			eye[0] += rightX * dist;
			eye[2] += rightZ * dist;
		}

		if (this.keys.contains(32))
		{ // Space: up
			eye[1] += dist;
		}

		if (this.keys.contains(340) || this.keys.contains(341))
		{ // Shift: down
			eye[1] -= dist;
		}

		// The eye may pull back a size-scaled margin beyond each face, so an overview never shrinks the build to a speck
		double span = Math.max(sizeX, Math.max(sizeY, sizeZ));
		double margin = Math.max(span * 1.5D, 24.0D);
		eye[0] = Math.max(-margin, Math.min(sizeX + margin, eye[0]));
		eye[1] = Math.max(-margin, Math.min(sizeY + margin, eye[1]));
		eye[2] = Math.max(-margin, Math.min(sizeZ + margin, eye[2]));
	}
}

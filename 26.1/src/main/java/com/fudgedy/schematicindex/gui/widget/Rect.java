package com.fudgedy.schematicindex.gui.widget;

import com.fudgedy.schematicindex.gui.Theme;

import java.util.List;

// Rebuilt every frame by the layout pass, so identity also keys Theme's hover and press effects
public final class Rect
{
	public int x;
	public int y;
	public int width;
	public int height;

	public void set(int x, int y, int width, int height)
	{
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	public boolean contains(double mouseX, double mouseY)
	{
		return this.width > 0 && Theme.inside(mouseX, mouseY, this.x, this.y, this.width, this.height);
	}

	public static Rect pooled(List<Rect> pool, int index)
	{
		while (pool.size() <= index)
		{
			pool.add(new Rect());
		}

		return pool.get(index);
	}
}

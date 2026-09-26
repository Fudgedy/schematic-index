package com.fudgedy.schematicindex.fx;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.PlainTextRenderable;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Style;
import org.joml.Matrix4fc;

// The glyph's quad spans the whole frame: two columns left of the pen for glows, and TOP rows above the text line
// for embers and bubbles when the name floats
record EffectSprite(float x, float y, int color, Style style, int frameWidth, int frameHeight, boolean inline,
		GlyphRenderTypes types, DynamicTexture texture) implements PlainTextRenderable
{
	@Override
	public int shadowColor()
	{
		return 0;
	}

	@Override
	public float shadowOffset()
	{
		return 0.0F;
	}

	@Override
	public float width()
	{
		return this.frameWidth;
	}

	@Override
	public float height()
	{
		return this.frameHeight;
	}

	@Override
	public float ascent()
	{
		return this.inline ? 7.0F : 7.0F + Canvas.TOP;
	}

	@Override
	public float left()
	{
		return this.x - NameMask.MARGIN;
	}

	@Override
	public void renderSprite(Matrix4fc pose, VertexConsumer consumer, int light, float dx, float dy, float z, int tint)
	{
		float left = this.left() + dx;
		float right = this.right() + dx;
		float top = this.top() + dy;
		float bottom = this.bottom() + dy;
		consumer.addVertex(pose, left, top, z).setUv(0.0F, 0.0F).setColor(tint).setLight(light);
		consumer.addVertex(pose, left, bottom, z).setUv(0.0F, 1.0F).setColor(tint).setLight(light);
		consumer.addVertex(pose, right, bottom, z).setUv(1.0F, 1.0F).setColor(tint).setLight(light);
		consumer.addVertex(pose, right, top, z).setUv(1.0F, 0.0F).setColor(tint).setLight(light);
	}

	@Override
	public RenderType renderType(Font.DisplayMode mode)
	{
		return this.types.select(mode);
	}

	@Override
	public GpuTextureView textureView()
	{
		return this.texture.getTextureView();
	}

	@Override
	public RenderPipeline guiPipeline()
	{
		return this.types.guiPipeline();
	}
}

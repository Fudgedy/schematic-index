package com.fudgedy.schematicindex.gui;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import java.util.function.Consumer;
public interface PreviewRenderer
{
	String id();
	boolean requiresRenderThread();
	NativeImage render(SchematicPreview.Model model, SchematicPreview.View view, @Nullable NativeImage reuse) throws Exception;

	// A fence-deferred readback can invoke sink on a later frame, on the render thread. sink owns the image
	default void renderAsync(SchematicPreview.Model model, SchematicPreview.View view,
			@Nullable NativeImage reuse, Consumer<NativeImage> sink) throws Exception
			{
		sink.accept(render(model, view, reuse));
	}

	default boolean rendersToTexture()
	{
		return false;
	}

	default boolean renderToTexture(SchematicPreview.Model model, SchematicPreview.View view, Identifier id)
			throws Exception
			{
		return false;
	}

	default int textureWidth()
	{
		return SchematicPreview.WIDTH;
	}

	default int textureHeight()
	{
		return SchematicPreview.HEIGHT;
	}

	default void close() {}
}

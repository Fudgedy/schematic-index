package com.fudgedy.schematicindex.fx;

import com.fudgedy.schematicindex.catalogue.Json;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// A nametag effect as data: shop copy plus an ordered stack of layers, each a built-in kind with its tunables.
// tints means the whole effect takes the wearer's name colour in place of its own
public record Effect(String id, String label, String blurb, String icon, boolean tints, List<Layer> layers)
{
	// Every layer repaints the whole name each frame, so a served stack stays short
	private static final int MAX_LAYERS = 8;
	private static final String TINT_NAME = "name";

	public record Layer(Kind kind, Params params)
	{
	}

	// Null when the definition names a kind this build does not have, so an older client skips it whole
	static @Nullable Effect parse(JsonObject json, Map<String, Kind> kinds)
	{
		String id = Json.stringOf(json, "id", null);

		if (id == null || id.isBlank())
		{
			return null;
		}

		List<Layer> layers = new ArrayList<>();

		for (JsonElement element : Json.arrayOf(json, "layers"))
		{
			if (!element.isJsonObject())
			{
				return null;
			}

			JsonObject layer = element.getAsJsonObject();
			Kind kind = kinds.get(Json.stringOf(layer, "kind", ""));

			if (kind == null)
			{
				return null;
			}

			layers.add(new Layer(kind, new Params(layer)));
		}

		if (layers.isEmpty() || layers.size() > MAX_LAYERS)
		{
			return null;
		}

		return new Effect(id, Json.stringOf(json, "label", id), Json.stringOf(json, "blurb", ""),
				Json.stringOf(json, "icon", "minecraft:name_tag"), TINT_NAME.equals(Json.stringOf(json, "tint", null)),
				List.copyOf(layers));
	}
}

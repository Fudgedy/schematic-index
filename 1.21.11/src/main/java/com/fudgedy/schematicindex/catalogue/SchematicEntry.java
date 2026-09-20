package com.fudgedy.schematicindex.catalogue;

import java.util.List;

public record SchematicEntry(
		String id,
		String title,
		String thumbnailName,
		String poster,
		String designer,
		Category category,
		int sizeX,
		int sizeY,
		int sizeZ,
		int blockCount,
		int downloads,
		int likes,
		long postedAt,
		String description,
		int imageCount,
		int imageStart,
		int schematicSlot,
		boolean downloaded,
		String thumbnailUrl,
		List<String> imageUrls,
		// Full uploads behind the 1024x576 imageUrls derivatives, parallel by index; may be shorter
		List<String> originalUrls,
		String fileUrl,
		String fileHash,
		long fileSize,
		boolean liked,
		double trendScore,
		int views,
		double starAvg,
		int starCount,
		int myStars,
		List<Material> materials,
		boolean materialsLoaded,
		int[] posterStops
)
{
	// One shared empty array keeps two parses of the same lite row equal, since arrays compare by identity
	public static final int[] NO_STOPS = new int[0];

	public record Material(String name, int count)
	{
	}

	// Text and list fields are normalized so a post missing one draws blank instead of failing; id and
	// the url fields stay nullable because callers use null as "absent"
	public SchematicEntry
	{
		title = title == null ? "" : title;
		thumbnailName = thumbnailName == null ? "" : thumbnailName;
		poster = poster == null ? "" : poster;
		designer = designer == null ? "" : designer;
		description = description == null ? "" : description;
		imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
		originalUrls = originalUrls == null ? List.of() : List.copyOf(originalUrls);
		materials = materials == null ? List.of() : List.copyOf(materials);
		posterStops = posterStops == null || posterStops.length == 0 ? NO_STOPS : posterStops;
	}

	// The lite index and the offline snapshot carry no materials or description; a post fetched whole does
	public SchematicEntry(String id, String title, String thumbnailName, String poster, String designer,
			Category category, int sizeX, int sizeY, int sizeZ, int blockCount, int downloads, int likes,
			long postedAt, String description, int imageCount, int imageStart, int schematicSlot,
			boolean downloaded, String thumbnailUrl, List<String> imageUrls, List<String> originalUrls,
			String fileUrl, String fileHash, long fileSize, boolean liked, double trendScore, int views,
			double starAvg, int starCount, int myStars, List<Material> materials)
	{
		this(id, title, thumbnailName, poster, designer, category, sizeX, sizeY, sizeZ, blockCount, downloads,
				likes, postedAt, description, imageCount, imageStart, schematicSlot, downloaded, thumbnailUrl,
				imageUrls, originalUrls, fileUrl, fileHash, fileSize, liked, trendScore, views, starAvg, starCount,
				myStars, materials, false, NO_STOPS);
	}

	public SchematicEntry withDetails(String description, List<Material> materials)
	{
		return new SchematicEntry(this.id, this.title, this.thumbnailName, this.poster, this.designer,
				this.category, this.sizeX, this.sizeY, this.sizeZ, this.blockCount, this.downloads, this.likes,
				this.postedAt, description, this.imageCount, this.imageStart, this.schematicSlot,
				this.downloaded, this.thumbnailUrl, this.imageUrls, this.originalUrls, this.fileUrl, this.fileHash,
				this.fileSize, this.liked, this.trendScore, this.views, this.starAvg, this.starCount, this.myStars,
				materials, true, this.posterStops);
	}

	public SchematicEntry withLikes(int likes, boolean liked)
	{
		return new SchematicEntry(this.id, this.title, this.thumbnailName, this.poster, this.designer,
				this.category, this.sizeX, this.sizeY, this.sizeZ, this.blockCount, this.downloads, likes,
				this.postedAt, this.description, this.imageCount, this.imageStart, this.schematicSlot,
				this.downloaded, this.thumbnailUrl, this.imageUrls, this.originalUrls, this.fileUrl, this.fileHash,
				this.fileSize, liked, this.trendScore, this.views, this.starAvg, this.starCount, this.myStars,
				this.materials, this.materialsLoaded, this.posterStops);
	}

	public static SchematicEntry local(String id, String title, String thumbnailName, String poster,
			String designer, Category category, int sizeX, int sizeY, int sizeZ, int blockCount, int downloads,
			int likes, long postedAt, String description, int imageCount, int imageStart, int schematicSlot,
			boolean downloaded)
	{
		return new SchematicEntry(id, title, thumbnailName, poster, designer, category, sizeX, sizeY, sizeZ,
				blockCount, downloads, likes, postedAt, description, imageCount, imageStart, schematicSlot,
				downloaded, null, List.of(), List.of(), null, null, 0L, false, 0.0, 0, 0.0, 0, 0, List.of());
	}

	public String credit()
	{
		return this.designer.isBlank() ? this.poster : this.designer;
	}

	// The gradient belongs to the poster, so a credit naming someone else stays plain
	public boolean isCreditPoster()
	{
		return this.credit().equals(this.poster);
	}

	public boolean hasPosterStyle()
	{
		return this.posterStops.length > 0;
	}

	public String cardName()
	{
		return this.thumbnailName == null || this.thumbnailName.isBlank() ? this.title : this.thumbnailName;
	}

	public String dimensionsLabel()
	{
		return this.sizeX + "x" + this.sizeY + "x" + this.sizeZ;
	}

	public static String compact(int value)
	{
		if (value < 1000)
		{
			return Integer.toString(value);
		}

		if (value < 1_000_000)
		{
			double thousands = value / 1000.0D;
			return thousands < 10.0D
					? String.format("%.1fk", thousands)
					: Math.round(thousands) + "k";
		}

		return String.format("%.1fM", value / 1_000_000.0D);
	}

	public String blockCountLabel()
	{
		return compact(this.blockCount);
	}

	public String volumeLabel()
	{
		return compact(this.sizeX * this.sizeY * this.sizeZ);
	}

	public String downloadsLabel()
	{
		return compact(this.downloads);
	}

	public String agoLabel()
	{
		long minutes = Math.max(0L, System.currentTimeMillis() - this.postedAt) / 60_000L;

		if (minutes < 1L)
		{
			return "just now";
		}

		if (minutes < 60L)
		{
			return minutes + "m ago";
		}

		long hours = minutes / 60L;

		if (hours < 24L)
		{
			return hours + "h ago";
		}

		long days = hours / 24L;

		if (days < 30L)
		{
			return days + "d ago";
		}

		long months = days / 30L;
		return months < 12L ? months + "mo ago" : (months / 12L) + "y ago";
	}
}

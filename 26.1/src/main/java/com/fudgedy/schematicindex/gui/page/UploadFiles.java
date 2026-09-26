package com.fudgedy.schematicindex.gui.page;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.SchematicIndexMod;
import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.Json;
import com.fudgedy.schematicindex.catalogue.Net;
import com.fudgedy.schematicindex.gui.ImageStore;
import com.fudgedy.schematicindex.gui.IndexScreen;
import com.fudgedy.schematicindex.gui.Theme;
import com.fudgedy.schematicindex.gui.UploaderAccess;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.util.FileType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Vec3i;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

class UploadFiles
{
	// The server's cap on images per post
	static final int MAX_PICTURES = 5;

	private final UploadPage page;
	private final IndexScreen screen;

	UploadFiles(UploadPage page, IndexScreen screen)
	{
		this.page = page;
		this.screen = screen;
	}

	void openPicturePicker()
	{
		new Thread(() -> {
			String result;

			try (MemoryStack stack = MemoryStack.stackPush())
			{
				PointerBuffer filters = stack.mallocPointer(3);
				filters.put(stack.UTF8("*.png"));
				filters.put(stack.UTF8("*.jpg"));
				filters.put(stack.UTF8("*.jpeg"));
				filters.flip();
				result = TinyFileDialogs.tinyfd_openFileDialog("Select 1 to 5 pictures", "", filters, "Images", true);
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.warn("File picker failed", e);
				return;
			}

			if (result == null || result.isBlank())
			{
				return;
			}

			List<Path> chosen = Arrays.stream(result.split("\\|"))
					.filter(value -> !value.isBlank())
					.map(Path::of)
					.toList();
			Minecraft.getInstance().execute(() -> this.appendPictures(chosen));
		}, "schematicindex-picture-picker").start();
	}

	void openSchematicPicker()
	{
		new Thread(() -> {
			String result;

			try (MemoryStack stack = MemoryStack.stackPush())
			{
				PointerBuffer filters = stack.mallocPointer(1);
				filters.put(stack.UTF8("*.litematic"));
				filters.flip();
				result = TinyFileDialogs.tinyfd_openFileDialog(
						"Choose the schematic", "", filters, "Litematica schematic", false);
			}
			catch (Throwable e)
			{
				SchematicIndexMod.LOGGER.warn("File picker failed", e);
				return;
			}

			if (result == null || result.isBlank())
			{
				return;
			}

			Path chosen = Path.of(result.trim());
			Minecraft.getInstance().execute(() -> {
				this.page.schematic = chosen;
				this.page.status(chosen.getFileName() + " selected.");
				this.parseSchematicStats(chosen);
				this.checkDuplicate(chosen);
			});
		}, "schematicindex-schematic-picker").start();
	}

	// Removing index 0 promotes whatever is next to cover, since the cover is always the first picture
	void removePicture(int index)
	{
		if (index < 0 || index >= this.page.pictures.size())
		{
			return;
		}

		List<Path> remaining = new ArrayList<>(this.page.pictures);
		remaining.remove(index);
		int preview = remaining.isEmpty() ? 0 : Math.min(index, remaining.size() - 1);
		this.applyPictures(remaining);
		this.page.picturePreview = preview;

		if (remaining.isEmpty())
		{
			this.page.status("All pictures removed.");
		}
	}

	// Reorders the picture to the front, so it becomes the cover sent with the post
	void setCover(int index)
	{
		if (index <= 0 || index >= this.page.pictures.size())
		{
			return;
		}

		List<Path> reordered = new ArrayList<>(this.page.pictures);
		reordered.add(0, reordered.remove(index));
		this.applyPictures(reordered);
		this.page.status("Cover updated.");
	}

	void dropFiles(List<Path> paths)
	{
		Path schematic = null;
		List<Path> images = new ArrayList<>();

		for (Path path : paths)
		{
			String name = path.getFileName().toString().toLowerCase(Locale.ROOT);

			if (name.endsWith(".litematic"))
			{
				schematic = path;
			}
			else if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg"))
			{
				images.add(path);
			}
		}

		if (schematic != null)
		{
			this.page.schematic = schematic;
			this.page.status(schematic.getFileName() + " selected.");
			this.parseSchematicStats(schematic);
			this.checkDuplicate(schematic);
		}

		if (!images.isEmpty())
		{
			this.appendPictures(images);
		}

		if (schematic == null && images.isEmpty())
		{
			this.page.statusError("Drop a .litematic file or PNG/JPG images.");
		}
	}

	void openPhotoMode()
	{
		Path picked = this.page.schematic;

		if (picked == null)
		{
			this.page.statusError("Choose the .litematic file first.");
			return;
		}

		int room = MAX_PICTURES - this.page.pictures.size();

		if (room <= 0)
		{
			this.page.statusError("Remove a picture first - 5 is the maximum.");
			return;
		}

		Theme.click();
		this.screen.photoMode.open(picked, room, this.page.pictures.isEmpty());
	}

	// Photos only touch the disk here, once the player keeps them; the cover leads so it becomes the thumbnail
	void addPhotos(List<byte[]> photos, boolean coverFirst)
	{
		List<Path> saved = new ArrayList<>();
		long stamp = System.currentTimeMillis();

		for (int i = 0; i < photos.size(); i++)
		{
			try
			{
				Path file = this.pastedDir().resolve("photo-" + stamp + "-" + i + ".png");
				Files.write(file, photos.get(i));
				saved.add(file);
			}
			catch (Exception e)
			{
				SchematicIndexMod.LOGGER.warn("Saving photo {} failed", i, e);
			}
		}

		List<Path> combined = new ArrayList<>(coverFirst ? saved : this.page.pictures);
		combined.addAll(coverFirst ? this.page.pictures : saved);
		this.applyPictures(combined);

		if (saved.size() < photos.size())
		{
			int failed = photos.size() - saved.size();
			this.page.statusError("Couldn't save " + failed + " of your photos. (" + Errors.PHOTO + ")");
			return;
		}

		this.page.status(saved.size() == 1
				? "Photo added to your pictures."
				: saved.size() + " photos added to your pictures.");
	}

	void parseSchematicStats(Path file)
	{
		this.page.sizeX = 0;
		this.page.sizeY = 0;
		this.page.sizeZ = 0;
		this.page.blockCount = 0;

		// A .litematic read is a full gzip and NBT decompress, which would freeze the render thread.
		// The pick sequence lets a slow parse drop its result if the file was swapped meanwhile
		final long seq = ++this.page.schematicParseSeq;
		final String pickedName = file.getFileName().toString();
		this.page.status("Reading " + pickedName + "...");

		Net.submit(() -> {
			int sizeX = 0;
			int sizeY = 0;
			int sizeZ = 0;
			int blockCount = 0;

			try
			{
				LitematicaSchematic schematic = LitematicaSchematic.createFromFile(
						file.getParent(), file.getFileName().toString(), FileType.LITEMATICA_SCHEMATIC);

				if (schematic != null)
				{
					Vec3i size = schematic.getMetadata().getEnclosingSize();
					sizeX = Math.abs(size.getX());
					sizeY = Math.abs(size.getY());
					sizeZ = Math.abs(size.getZ());
					blockCount = (int) Math.max(0L, schematic.getMetadata().getTotalBlocks());
				}
			}
			catch (Exception e)
			{
				SchematicIndexMod.LOGGER.debug("Could not read schematic stats", e);
			}

			final int fx = sizeX;
			final int fy = sizeY;
			final int fz = sizeZ;
			final int fb = blockCount;
			Minecraft.getInstance().execute(() -> {
				if (seq != this.page.schematicParseSeq || !file.equals(this.page.schematic) || !this.page.open)
				{
					return;
				}

				this.page.sizeX = fx;
				this.page.sizeY = fy;
				this.page.sizeZ = fz;
				this.page.blockCount = fb;
				this.page.status(pickedName + " selected.");
			});
		});
	}

	// Runs the instant a schematic is picked, so a duplicate surfaces before the rest of the form is filled in.
	// Errors and rate limits stay quiet here - Post still checks again, this is only an early warning
	void checkDuplicate(Path file)
	{
		this.page.schematicDuplicate = false;
		this.page.duplicateCheckNote = "";
		this.page.duplicateChecking = true;
		final long seq = ++this.page.duplicateCheckSeq;
		String code = UploaderAccess.code();

		Net.submit(() -> {
			Backend.ApiResult result = Backend.checkDuplicateCached(code, file);

			Minecraft.getInstance().execute(() -> {
				if (seq != this.page.duplicateCheckSeq || !file.equals(this.page.schematic))
				{
					return;
				}

				this.page.duplicateChecking = false;

				if (result.status() == 200)
				{
					if (Json.boolOf(result.body(), "duplicate", false))
					{
						this.page.schematicDuplicate = true;
						this.screen.duplicateModal.open(Json.boolOf(result.body(), "pending", false),
								Json.stringOf(result.body(), "postId", null), Json.stringOf(result.body(), "title", null));
					}

					return;
				}

				if (result.status() == 401 || result.status() == 403)
				{
					this.page.duplicateCheckNote = "Verify your account to check for duplicates.";
					return;
				}

				this.page.duplicateCheckNote = "Couldn't check for duplicates yet. (" + Errors.UPLOAD_CHECK + ")";
			});
		});
	}

	void addPicture(Path path)
	{
		this.appendPictures(List.of(path));
	}

	// Newly picked or pasted pictures land after whatever is already in the list, never replacing it
	private void appendPictures(List<Path> picked)
	{
		List<Path> combined = new ArrayList<>(this.page.pictures);
		combined.addAll(picked);
		this.applyPictures(combined);
	}

	Path pastedFile(String extension) throws IOException
	{
		return this.pastedDir().resolve("paste-" + System.currentTimeMillis() + "." + extension);
	}

	// Photos and pastes are the mod's own copies, so a cleared form deletes them; picked files stay put
	void deletePasted(List<Path> paths)
	{
		Path dir = FabricLoader.getInstance().getGameDir().resolve(SchematicIndexMod.MOD_ID).resolve("pasted")
				.toAbsolutePath().normalize();

		for (Path path : paths)
		{
			if (!path.toAbsolutePath().normalize().startsWith(dir))
			{
				continue;
			}

			try
			{
				Files.deleteIfExists(path);
			}
			catch (IOException e)
			{
				SchematicIndexMod.LOGGER.debug("Could not delete {}", path, e);
			}
		}
	}

	private Path pastedDir() throws IOException
	{
		Path dir = FabricLoader.getInstance().getGameDir()
				.resolve(SchematicIndexMod.MOD_ID).resolve("pasted");
		Files.createDirectories(dir);
		return dir;
	}

	private void applyPictures(List<Path> chosen)
	{
		boolean truncated = chosen.size() > MAX_PICTURES;
		List<Path> capped = truncated ? List.copyOf(chosen.subList(0, MAX_PICTURES)) : chosen;

		this.page.pictures.clear();
		this.page.pictures.addAll(capped);
		this.page.pictureStart = ImageStore.register(capped);
		this.page.picturePreview = 0;
		this.page.status(truncated
				? "Only the first 5 pictures were kept."
				: capped.size() + (capped.size() == 1 ? " picture selected." : " pictures selected."));
	}
}

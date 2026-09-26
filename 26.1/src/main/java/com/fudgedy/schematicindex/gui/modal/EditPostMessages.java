package com.fudgedy.schematicindex.gui.modal;

import com.fudgedy.schematicindex.Errors;
import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.Json;
import com.fudgedy.schematicindex.gui.IndexScreen;

// Inline copy for the edit form: one caption per field plus the line beside the buttons
final class EditPostMessages
{
	static final int TITLE_LIMIT = 120;
	static final int THUMBNAIL_LIMIT = 64;
	static final int DESIGNER_LIMIT = 64;
	String status = "";
	boolean isError;
	String title = "";
	String thumbnail = "";
	String designer = "";
	String description = "";

	void clear()
	{
		this.status = "";
		this.isError = false;
		this.title = "";
		this.thumbnail = "";
		this.designer = "";
		this.description = "";
	}

	void fail(String message)
	{
		this.status = message;
		this.isError = true;
	}

	// The same limits the upload form and the server hold
	boolean validate(String title, String thumbnail, String designer, String description)
	{
		this.clear();

		if (title.isEmpty())
		{
			this.title = "A name is required.";
		}
		else if (title.length() > TITLE_LIMIT)
		{
			this.title = tooLong(TITLE_LIMIT);
		}

		if (thumbnail.length() > THUMBNAIL_LIMIT)
		{
			this.thumbnail = tooLong(THUMBNAIL_LIMIT);
		}

		if (designer.length() > DESIGNER_LIMIT)
		{
			this.designer = tooLong(DESIGNER_LIMIT);
		}

		if (description.length() > IndexScreen.DESC_CHAR_LIMIT)
		{
			this.description = tooLong(IndexScreen.DESC_CHAR_LIMIT);
		}

		return this.title.isEmpty() && this.thumbnail.isEmpty() && this.designer.isEmpty() && this.description.isEmpty();
	}

	// A rejected field gets the server's message under that field; every other refusal gets a reason and a code
	void refuse(Backend.ApiResult result, boolean viaStaff)
	{
		String message = result.message();

		if (result.status() != 400 || message == null)
		{
			this.fail(refusal(result, viaStaff));
			return;
		}

		this.status = "";
		String field = Json.stringOf(result.body(), "field", "");

		switch (field == null ? "" : field)
		{
			case "title" -> this.title = message;
			case "thumbnailName" -> this.thumbnail = message;
			case "designer" -> this.designer = message;
			case "description" -> this.description = message;
			default -> this.fail(message);
		}
	}

	private static String tooLong(int limit)
	{
		return "Keep it to " + limit + " characters.";
	}

	private static String refusal(Backend.ApiResult result, boolean viaStaff)
	{
		if (result.unverified())
		{
			return "Verify your account first, then save again.";
		}

		if (result.is("bad_code"))
		{
			return "Your account could not be confirmed. Verify again, then save.";
		}

		if (result.is("not_staff") || result.is("not_owner"))
		{
			return "Could not save: this account is no longer staff. (" + Errors.STAFF_DENIED + ")";
		}

		if (result.status() == 404)
		{
			return "Could not save: this post is not yours to edit.";
		}

		String code = viaStaff ? Errors.STAFF_ACTION : Errors.POST_EDIT;
		Errors.report(code);
		return "Could not save. Try again. (" + code + ")";
	}
}

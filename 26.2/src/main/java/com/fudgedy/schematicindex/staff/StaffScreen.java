package com.fudgedy.schematicindex.staff;

import com.fudgedy.schematicindex.catalogue.Backend;
import com.fudgedy.schematicindex.catalogue.SchematicEntry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

// Everything IndexScreen routes to the staff jar for one open screen: the Staff tab, its modals, and
// the post actions reached from the detail card and the grid's card menu
public interface StaffScreen
{
	// False whenever the verified account is not staff, so the owner jar hides the UI from everyone else
	boolean available();

	void buildFields();

	void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float partialTick);

	void renderModal(GuiGraphicsExtractor ctx, int mouseX, int mouseY);

	boolean isModalOpen();

	boolean mouseClicked(MouseButtonEvent event, boolean doubleClick, double mouseX, double mouseY);

	boolean modalClicked(double mouseX, double mouseY);

	boolean mouseScrolled(double mouseX, double mouseY, double scrollY);

	boolean keyPressed(KeyEvent event);

	// Draws the Staff button inside the detail card's info column; answers the height it used
	int renderDetailControls(GuiGraphicsExtractor ctx, SchematicEntry entry, int x, int y, int width, int mouseX,
			int mouseY);

	boolean clickDetailControls(double mouseX, double mouseY);

	// The card menu row's label comes from here so the community jar carries no staff copy
	String postMenuLabel();

	void openPostMenu(SchematicEntry entry);

	// The edit modal's staff path submits through here, so the community jar never carries the route
	Backend.ApiResult editPost(String postId, String title, String thumbnailName, String designer, String description,
			String category);
}

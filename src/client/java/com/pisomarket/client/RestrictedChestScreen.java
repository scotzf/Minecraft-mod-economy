package com.pisomarket.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import com.pisomarket.claims.lock.RestrictedChestMenu;

// Same vanilla chest background as PisoShopScreen — see that class for why
// it's copied by hand instead of reusing vanilla's ContainerScreen.
public class RestrictedChestScreen extends AbstractContainerScreen<RestrictedChestMenu> {
	private static final Identifier CONTAINER_BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
	private static final int CONTENT_ROWS = 3;

	public RestrictedChestScreen(final RestrictedChestMenu menu, final Inventory inventory, final Component title) {
		super(menu, inventory, title, 176, 114 + CONTENT_ROWS * 18);
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int xo = (this.width - this.imageWidth) / 2;
		int yo = (this.height - this.imageHeight) / 2;
		graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_BACKGROUND, xo, yo, 0.0F, 0.0F, this.imageWidth, CONTENT_ROWS * 18 + 17, 256, 256);
		graphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_BACKGROUND, xo, yo + CONTENT_ROWS * 18 + 17, 0.0F, 126.0F, this.imageWidth, 96, 256, 256);
	}
}

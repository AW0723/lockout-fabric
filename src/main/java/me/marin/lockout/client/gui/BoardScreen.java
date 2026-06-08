package me.marin.lockout.client.gui;

import me.marin.lockout.Lockout;
import me.marin.lockout.Utility;
import me.marin.lockout.client.LockoutClient;
import me.marin.lockout.lockout.Goal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.chat.Component;

public class BoardScreen extends AbstractContainerScreen<BoardScreenHandler> {

    public BoardScreen(BoardScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!Lockout.exists(LockoutClient.lockout)) {
            this.onClose();
            Minecraft.getInstance().setScreen(null);
            return;
        }
        this.extractBackground(context, mouseX, mouseY, delta);
        Font textRenderer = Minecraft.getInstance().font;

        Utility.drawCenterBingoBoard(context, textRenderer, mouseX, mouseY);
        Goal hoveredGoal = Utility.getBoardHoveredGoal(context, mouseX, mouseY);
        if (hoveredGoal != null) {
            Utility.drawGoalInformation(context, textRenderer, hoveredGoal, mouseX, mouseY);
        }
    }

    @Override
    public void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {

    }

}

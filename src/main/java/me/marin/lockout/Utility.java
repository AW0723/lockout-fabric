package me.marin.lockout;

import me.marin.lockout.client.LockoutBoard;
import me.marin.lockout.client.LockoutClient;
import me.marin.lockout.client.gui.BoardBuilderScreen;
import me.marin.lockout.lockout.Goal;
import me.marin.lockout.lockout.interfaces.HasTooltipInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static me.marin.lockout.Constants.*;
import static me.marin.lockout.LockoutConfig.BoardPosition.LEFT;

public class Utility {

    public static int FF000000 = 0xFF000000;

    public static void drawBingoBoard(GuiGraphicsExtractor context) {
        LockoutConfig.BoardPosition boardPosition = LockoutConfig.getInstance().boardPosition;

        // Don't render board if F3 is open with left-side board.
        if (boardPosition == LockoutConfig.BoardPosition.LEFT && Minecraft.getInstance().gui.getDebugOverlay().showDebugScreen()) {
            return;
        }

        Font textRenderer = Minecraft.getInstance().font;

        Lockout lockout = LockoutClient.lockout;
        LockoutBoard board = lockout.getBoard();

        int boardWidth = 2 * GUI_PADDING + board.size() * GUI_SLOT_SIZE;
        int boardHeight = GUI_PADDING + GUI_PADDING_BOTTOM + board.size() * GUI_SLOT_SIZE;

        int boardRightEdgeX = boardPosition == LEFT ? boardWidth : context.guiWidth();
        int boardLeftEdgeX = boardRightEdgeX - boardWidth;

        int x = boardLeftEdgeX;
        int y = 0;

        context.blitSprite(RenderPipelines.GUI_TEXTURED, Constants.GUI_IDENTIFIER, x, y, boardWidth, boardHeight);

        x += GUI_PADDING + 1;
        y += GUI_PADDING + 1;
        final int startX = x;

        for (int i = 0; i < board.size(); i++) {
            for (int j = 0; j < board.size(); j++) {
                Goal goal = board.getGoals().get(j + board.size() * i);
                if (goal != null) {
                    if (goal.isCompleted()) {
                        context.fill(x, y, x + 16, y + 16, FF000000 | goal.getCompletedTeam().getColor().getColor());
                    }

                    goal.render(context, textRenderer, x, y);

                }
                x += GUI_SLOT_SIZE;
            }
            y += GUI_SLOT_SIZE;
            x = startX;
        }
        x += 2;
        y += 1;
        List<String> pointsList = new ArrayList<>();
        for (LockoutTeam team : lockout.getTeams()) {
            pointsList.add(team.getColor() + "" + team.getPoints() + ChatFormatting.RESET);
        }

        context.text(textRenderer, String.join(ChatFormatting.RESET + "" + ChatFormatting.GRAY + "-", pointsList), x, y, FF000000, true);

        String timer = Utility.ticksToTimer(lockout.getTicks());
        context.text(textRenderer, ChatFormatting.WHITE + timer, boardRightEdgeX - textRenderer.width(timer) - 4, y, FF000000, true);

        List<String> formattedNames = new ArrayList<>();
        int maxWidth = 0;
        for (LockoutTeam team : lockout.getTeams()) {
            for (String playerName : team.getPlayerNames()) {
                formattedNames.add(team.getColor() + playerName);
                maxWidth = Math.max(maxWidth, textRenderer.width(playerName));
            }
        }

        y += 20;
        switch (boardPosition) {
            case RIGHT -> {
                context.fill(context.guiWidth() - maxWidth - 3 - 1,  y - 2, context.guiWidth() - 1, y + formattedNames.size() * textRenderer.lineHeight + 1, 0x80_00_00_00);

                for (String formattedName : formattedNames) {
                    context.text(textRenderer, formattedName, context.guiWidth() - textRenderer.width(formattedName) - 2, y, FF000000, true);
                    y += textRenderer.lineHeight;
                }
            }
            case LEFT -> {
                context.fill(1,  y - 2, 4 + maxWidth, y + formattedNames.size() * textRenderer.lineHeight + 1, 0x80_00_00_00);

                for (String formattedName : formattedNames) {
                    context.text(textRenderer, formattedName, 3, y, FF000000, true);
                    y += textRenderer.lineHeight;
                }
            }
        }

    }

    public static void drawCenterBingoBoard(GuiGraphicsExtractor context, Font textRenderer, int mouseX, int mouseY) {
        int width = context.guiWidth();
        int height = context.guiHeight();

        LockoutBoard board = LockoutClient.lockout.getBoard();

        int boardWidth = 2 * GUI_CENTER_PADDING + board.size() * GUI_CENTER_SLOT_SIZE;
        int x = width / 2 - boardWidth / 2;

        int boardHeight = 2 * GUI_CENTER_PADDING + board.size() * GUI_CENTER_SLOT_SIZE;
        int y = height / 2 - boardHeight / 2;

        context.blitSprite(RenderPipelines.GUI_TEXTURED, GUI_CENTER_IDENTIFIER, x, y, boardWidth, boardHeight);

        x += GUI_CENTER_PADDING + 1;
        y += GUI_CENTER_PADDING + 1;
        final int startX = x;

        Goal hoveredGoal = getBoardHoveredGoal(context, mouseX, mouseY);

        for (int i = 0; i < board.size(); i++) {
            for (int j = 0; j < board.size(); j++) {
                Goal goal = board.getGoals().get(j + board.size() * i);
                if (goal != null) {
                    if (goal.isCompleted()) {
                        context.fill(x, y, x + 16, y + 16, (0xFF << 24) | goal.getCompletedTeam().getColor().getColor());
                    }

                    goal.render(context, textRenderer, x, y);

                    if (goal == hoveredGoal) {
                        context.fill(x, y, x + 16, y + 16, GUI_CENTER_HOVERED_COLOR);
                    }
                }
                x += GUI_CENTER_SLOT_SIZE;
            }
            y += GUI_CENTER_SLOT_SIZE;
            x = startX;
        }
    }

    public static Optional<Integer> getBoardHoveredIndex(int size, int width, int height, int mouseX, int mouseY) {
        int x = width / 2 - (2 * GUI_CENTER_PADDING + size * GUI_CENTER_SLOT_SIZE) / 2 + GUI_CENTER_PADDING - BoardBuilderScreen.CENTER_OFFSET;
        int y = height / 2 - (2 * GUI_CENTER_PADDING + size * GUI_CENTER_SLOT_SIZE) / 2 + GUI_CENTER_PADDING;
        final int startX = x;

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (mouseX >= x-1 && mouseX < x + GUI_CENTER_SLOT_SIZE && mouseY >= y-1 && mouseY < y + GUI_CENTER_SLOT_SIZE) {
                    return Optional.of(j + i * size);
                }
                x += GUI_CENTER_SLOT_SIZE;
            }
            y += GUI_CENTER_SLOT_SIZE;
            x = startX;
        }

        return Optional.empty();
    }

    public static Goal getBoardHoveredGoal(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        Optional<Integer> hoveredIdx = getBoardHoveredIndex(LockoutClient.lockout.getBoard().size(), context.guiWidth(), context.guiHeight(), mouseX, mouseY);
        return hoveredIdx.map(integer -> LockoutClient.lockout.getBoard().getGoals().get(integer)).orElse(null);
    }

    public static void drawGoalInformation(GuiGraphicsExtractor context, Font textRenderer, Goal goal, int mouseX, int mouseY) {
        List<FormattedCharSequence> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(((goal instanceof HasTooltipInfo) ? ChatFormatting.UNDERLINE : "") + goal.getGoalName()).getVisualOrderText());
        if (goal instanceof HasTooltipInfo) {
            String s = LockoutClient.goalTooltipMap.get(goal.getId());
            if (s != null) {
                for (String t : s.split("\n")) {
                    tooltip.add(Component.literal(t).getVisualOrderText());
                }
            }
        }
        context.setTooltipForNextFrame(textRenderer, tooltip, mouseX, mouseY);
    }

    /**
     * Code from {@link GuiGraphicsExtractor#drawStackCount(Font, ItemStack, int, int, String)}, but without ItemStack argument requirement
     */
    public static void drawStackCount(GuiGraphicsExtractor context, int x, int y, String count) {
        Font textRenderer = Minecraft.getInstance().font;
        context.pose().pushMatrix();
        context.pose().translate(0.0F, 0.0F);
        context.text(textRenderer, count, x + 19 - 2 - textRenderer.width(count), y + 6 + 3, -1, true);
        context.pose().popMatrix();
    }

    public static List<ServerPlayer> getSpectators(Lockout lockout, MinecraftServer server) {
        return server.getPlayerList().getPlayers()
                .stream()
                .filter(p -> !lockout.isLockoutPlayer(p.getUUID()))
                .toList();
    }

    public static String ticksToTimer(long ticks) {
        ticks = Math.abs(ticks);
        long second = (ticks / 20) % 60;
        long minute = ((ticks / 20) / 60) % 60;
        long hour = ((ticks / 20) / 60 / 60) % 24;

        String time;
        if (hour > 0) {
            time = String.format("%02d:%02d:%02d", hour, minute, second);
        } else {
            time = String.format("%02d:%02d", minute, second);
        }

        return time;
    }

}

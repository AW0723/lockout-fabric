package me.marin.lockout.client.gui;

import me.marin.lockout.generator.GoalDataGenerator;
import me.marin.lockout.lockout.Goal;
import me.marin.lockout.lockout.GoalRegistry;
import me.marin.lockout.lockout.goals.util.GoalDataConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.apache.commons.lang3.text.WordUtils;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class BoardBuilderSearchWidget extends AbstractScrollArea {

    private static final int MARGIN_X = 3;
    private static final int MARGIN_Y = 3;
    private static final int ITEM_HEIGHT = 18;
    private static final Map<String, GoalEntry> registeredGoals = new LinkedHashMap<>();

    private int rowWidth;
    private int left;
    private int right;
    private int top;
    private static GoalEntry hovered;
    private List<GoalEntry> visibleGoals;

    public BoardBuilderSearchWidget(int x, int y, int width, int height, Component text) {
        super(x, y, width, height, text, AbstractScrollArea.defaultSettings(width));
        for (String id : GoalRegistry.INSTANCE.getRegisteredGoals()) {
            registeredGoals.putIfAbsent(id, new GoalEntry(id));
        }
        visibleGoals = new ArrayList<>(registeredGoals.values());
        searchUpdated(BoardBuilderData.INSTANCE.getSearch());
    }

    public void setScrollAmount(double scrollY) {
        super.setScrollAmount(scrollY);
    }

    public double scrollAmount() {
        return super.scrollAmount();
    }

    @Override
    protected int contentHeight() {
        return visibleGoals.size() * ITEM_HEIGHT;
    }

    @Override
    protected double scrollRate() {
        return ITEM_HEIGHT / 2.0;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        this.rowWidth = getWidth() - MARGIN_X * 2;
        this.left = getX() + MARGIN_X;
        this.top = getY();
        this.right = getX() + getWidth() - MARGIN_X;

        hovered = this.isMouseOver(mouseX, mouseY) ? this.getEntryAtPosition(mouseX, mouseY) : null;

        context.enableScissor(this.left - 1, this.top, this.right + 1, getY() + getHeight());

        int y = 4;
        for (GoalEntry goalEntry : visibleGoals) {
            int entryX = getX() + MARGIN_X;
            int entryY = getY() + y - (int) scrollAmount() - 3;
            boolean isHovered = Objects.equals(goalEntry, hovered);
            goalEntry.setX(entryX);
            goalEntry.setY(entryY);
            goalEntry.setWidth(rowWidth - 4);
            goalEntry.setHeight(ITEM_HEIGHT);
            goalEntry.extractContent(context, mouseX, mouseY, isHovered, delta);
            y += ITEM_HEIGHT;
        }

        context.disableScissor();
        this.extractScrollbar(context, mouseX, mouseY);
    }

    protected final GoalEntry getEntryAtPosition(double x, double y) {
        int halfRowWidth = this.rowWidth / 2;
        int centerX = this.left + this.width / 2;
        int left = centerX - halfRowWidth;
        int right = centerX + halfRowWidth;
        int scrolledY = Mth.floor(y - (double)this.top) + (int)scrollAmount() - MARGIN_Y + 3;
        int idx = scrolledY / ITEM_HEIGHT;
        if (x < (this.right + MARGIN_X - 6) && x >= (double) left && x <= (double) right && idx >= 0 && scrolledY >= 0 && idx < visibleGoals.size()) {
            return registeredGoals.get(visibleGoals.get(idx).goal.getId());
        }
        return null;
    }

    public void searchUpdated(String search) {
        setScrollAmount(0);
        visibleGoals = new ArrayList<>(registeredGoals.values()).stream().filter(goalEntry -> goalEntry.displayName.toLowerCase().contains(search.toLowerCase())).collect(Collectors.toList());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
        if (hovered != null && !consumed) {
            BoardBuilderData.INSTANCE.setGoal(hovered.goal);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
        if (!consumed) {
            updateScrolling(event);
        }
        return super.mouseClicked(event, consumed);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {}

    public static final class GoalEntry extends ContainerObjectSelectionList.Entry<GoalEntry> {

        private final Goal goal;
        public final String displayName;

        public GoalEntry(String id) {
            Optional<GoalDataGenerator> gen = GoalRegistry.INSTANCE.getDataGenerator(id);

            // generate random data
            String data = gen.map(g -> g.generateData(new ArrayList<>(GoalDataGenerator.ALL_DYES))).orElse(GoalDataConstants.DATA_NONE);
            this.goal = GoalRegistry.INSTANCE.newGoal(id, data);

            this.displayName = gen.isEmpty() ? goal.getGoalName() : "[*] " + WordUtils.capitalize(goal.getId().replace("_", " ").toLowerCase(), ' ');
        }

        @Override
        public java.util.List<? extends NarratableEntry> narratables() {
            return java.util.Collections.emptyList();
        }

        @Override
        public java.util.List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() {
            return java.util.Collections.emptyList();
        }

        @Override
        public void extractContent(GuiGraphicsExtractor context, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            Font textRenderer = Minecraft.getInstance().font;
            int x = getX();
            int y = getY();

            goal.render(context, textRenderer, x, y);
            context.text(textRenderer, displayName, x + 18, y + 5, Color.WHITE.getRGB());
            if (hovered) {
                context.outline(x - 1, y - 1, getWidth() + 2, getHeight(), Color.LIGHT_GRAY.getRGB());
            }
        }
    }


}

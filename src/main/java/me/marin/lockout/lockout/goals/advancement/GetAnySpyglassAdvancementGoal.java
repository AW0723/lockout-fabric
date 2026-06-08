package me.marin.lockout.lockout.goals.advancement;

import me.marin.lockout.lockout.interfaces.AdvancementGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.Identifier;

import java.util.List;

public class GetAnySpyglassAdvancementGoal extends AdvancementGoal {

    private static final ItemStack ITEM_STACK = Items.SPYGLASS.getDefaultInstance();
    private static final List<Identifier> ADVANCEMENTS = List.of(
            Identifier.fromNamespaceAndPath("minecraft", "adventure/spyglass_at_parrot"),
            Identifier.fromNamespaceAndPath("minecraft", "adventure/spyglass_at_ghast"),
            Identifier.fromNamespaceAndPath("minecraft", "adventure/spyglass_at_dragon")
            );

    public GetAnySpyglassAdvancementGoal(String id, String data) {
        super(id, data);
    }

    @Override
    public String getGoalName() {
        return "Get any Spyglass Advancement";
    }

    @Override
    public ItemStack getTextureItemStack() {
        return ITEM_STACK;
    }

    @Override
    public List<Identifier> getAdvancements() {
        return ADVANCEMENTS;
    }
}

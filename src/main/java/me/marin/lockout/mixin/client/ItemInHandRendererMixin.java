package me.marin.lockout.mixin.client;

import me.marin.lockout.CompassItemHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    // applyEquipOffset was removed in 26.1 - compass equip animation disabled
}

package com.thenathe.ssopolymer.mixin;

import me.pajic.simple_smithing_overhaul.SSO;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Publish the actual free cost before mayPickup runs, not only after taking the result. */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
    @Shadow @Final private DataSlot cost;

    @Inject(method = "createResult", at = @At("RETURN"))
    private void ssoPolymer$applyFreeCostBeforePickup(CallbackInfo ci) {
        ItemStack output = ((AnvilMenu) (Object) this).getSlot(2).getItem();
        if (SSO.CONFIG.anvilImprovements.freeUnenchantedRepairs.get()
                && !output.isEmpty() && !output.isEnchanted()
                && output.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()) {
            cost.set(0);
        }
    }
}

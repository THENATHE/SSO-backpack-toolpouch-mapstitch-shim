package com.thenathe.ssopolymer.mixin;

import me.pajic.simple_smithing_overhaul.util.ModDataComponents;
import me.pajic.simple_smithing_overhaul.util.ModUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The original in-place repair copies damage but drops the recipe's repair counter. */
@Mixin(value = ModUtil.class, remap = false)
public abstract class MendingRepairCountMixin {
    @Inject(method = "tryRepairItem", at = @At("RETURN"))
    private static void ssoPolymer$keepRepairCount(ItemStack target, Player player, Level level,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            target.set(ModDataComponents.REPAIR_COUNT, target.getOrDefault(ModDataComponents.REPAIR_COUNT, 0) + 1);
        }
    }
}

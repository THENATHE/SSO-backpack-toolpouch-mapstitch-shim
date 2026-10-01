package com.thenathe.ssopolymer.mixin;

import me.pajic.simple_smithing_overhaul.SSO;
import me.pajic.simple_smithing_overhaul.util.CompatFlags;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Clamp the final shelf count; the original local-capture hook misses the 26.3 count. */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantingPowerMixin {
    @ModifyArg(method = "lambda$slotsChanged$0", index = 2,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getEnchantmentCost(Lnet/minecraft/util/RandomSource;IILnet/minecraft/world/item/ItemStack;)I"))
    private int ssopolymer$limitBookshelves(int shelves) {
        return !CompatFlags.PENCHANT_LOADED && SSO.CONFIG.enchantmentLimits.limitEnchantingTablePower.get()
                ? Math.min(shelves, SSO.CONFIG.enchantmentLimits.enchantingTablePowerLimit.get()) : shelves;
    }
}

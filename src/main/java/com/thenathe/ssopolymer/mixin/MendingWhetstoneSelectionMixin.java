package com.thenathe.ssopolymer.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.ItemStackWithSlot;
import me.pajic.simple_smithing_overhaul.recipe.PortableItemRepairRecipe;
import me.pajic.simple_smithing_overhaul.util.ModUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.function.Predicate;

/** Selects a usable whetstone without changing the original repair and consumption logic. */
@Mixin(value = ModUtil.class, remap = false)
public abstract class MendingWhetstoneSelectionMixin {
    @WrapOperation(method = "tryRepairItem", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lme/pajic/simple_smithing_overhaul/util/ModUtil;findItemOnPlayer(Lnet/minecraft/world/entity/player/Player;Ljava/util/function/Predicate;)Lnet/minecraft/world/ItemStackWithSlot;"))
    private static ItemStackWithSlot ssoPolymer$compatibleWhetstone(Player player, Predicate<ItemStack> predicate,
            Operation<ItemStackWithSlot> original, @Local(argsOnly = true) ItemStack target,
            @Local(argsOnly = true) Level level) {
        ItemStack material = ModUtil.findItemOnPlayer(player, stack -> ModUtil.isValidRepairItem(target, stack)).stack();
        if (material.isEmpty()) return original.call(player, predicate);
        Predicate<ItemStack> compatible = candidate -> predicate.test(candidate)
                && new PortableItemRepairRecipe().matches(CraftingInput.of(2, 2,
                        List.of(target, candidate, material.copyWithCount(1), ItemStack.EMPTY)), level);
        return original.call(player, compatible);
    }
}

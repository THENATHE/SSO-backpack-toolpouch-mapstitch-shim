package com.thenathe.ssopolymer.mixin;

import me.pajic.simple_smithing_overhaul.SSO;
import me.pajic.simple_smithing_overhaul.platform.fabric.FabricEntrypoint;
import me.pajic.simple_smithing_overhaul.util.ModUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Attempts the original repair before its broken-item guard and subclass-specific item use. */
@Mixin(value = FabricEntrypoint.class, remap = false)
public abstract class MendingUseMixin {
    @Inject(method = "lambda$initEvents$4", at = @At("HEAD"), cancellable = true)
    private static void ssoPolymer$manualMending(Player player, Level level, InteractionHand hand,
                                               CallbackInfoReturnable<InteractionResult> cir) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.isSpectator()
                || !SSO.CONFIG.mendingRework.enabled.get()
                || !SSO.CONFIG.mendingRework.repairOnShiftUse.get() || !player.isShiftKeyDown()) return;
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isDamaged() && !player.getCooldowns().isOnCooldown(stack)
                && ModUtil.tryRepairItem(stack, player, level)) {
            serverPlayer.inventoryMenu.sendAllDataToRemote();
            cir.setReturnValue(InteractionResult.CONSUME);
        }
    }
}

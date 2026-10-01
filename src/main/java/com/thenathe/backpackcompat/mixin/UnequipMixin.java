package com.thenathe.backpackcompat.mixin;

import com.thenathe.backpackcompat.BackpackCompat;
import me.pajic.tiered_backpacks.util.BackpackUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BackpackUtil.class, remap = false)
public abstract class UnequipMixin {
    @Inject(method = "canUnequipBackpack", at = @At("HEAD"), cancellable = true)
    private static void backpackcompat$avoidTrappingVanilla(Player player, ItemStack backpack,
                                                           CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof ServerPlayer serverPlayer && !BackpackCompat.nativeClient(serverPlayer.connection.getPacketContext())) {
            cir.setReturnValue(true);
        }
    }
}

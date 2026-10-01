package com.thenathe.toolpouchcompat.mixin;

import com.thenathe.toolpouchcompat.ToolpouchCompat;
import me.pajic.toolpouch.util.ToolPouchUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep passive pouch effects (totems, flight, ammo, etc.) inactive on unsupported clients. */
@Mixin(value = ToolPouchUtil.class, remap = false)
public abstract class ToolpouchGameplayMixin {
    @Inject(method = "getToolPouch", at = @At("HEAD"), cancellable = true)
    private static void toolpouchcompat$nativeOnly(Player player, CallbackInfoReturnable<ItemStack> cir) {
        if (player instanceof ServerPlayer serverPlayer && !ToolpouchCompat.nativeClient(serverPlayer.connection.getPacketContext())) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}

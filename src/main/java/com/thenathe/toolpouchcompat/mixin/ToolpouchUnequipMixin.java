package com.thenathe.toolpouchcompat.mixin;

import com.thenathe.toolpouchcompat.ToolpouchCompat;
import me.pajic.toolpouch.util.GameplayUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A vanilla player cannot empty the pouch UI, so never trap them in attached leggings. */
@Mixin(value = GameplayUtil.class, remap = false)
public abstract class ToolpouchUnequipMixin {
    @Inject(method = "canUnequipToolPouch", at = @At("HEAD"), cancellable = true)
    private static void toolpouchcompat$allowVanillaUnequip(Player player, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof ServerPlayer serverPlayer && !ToolpouchCompat.nativeClient(serverPlayer.connection.getPacketContext())) {
            cir.setReturnValue(true);
        }
    }
}

package com.thenathe.toolpouchcompat.mixin;

import com.thenathe.toolpouchcompat.ToolpouchCompat;
import me.pajic.toolpouch.item.ToolPouchItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ToolPouchItem.class, remap = false)
public abstract class ToolpouchUseMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void toolpouchcompat$notice(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (player instanceof ServerPlayer serverPlayer && !ToolpouchCompat.nativeClient(serverPlayer.connection.getPacketContext())) {
            ToolpouchCompat.notice(serverPlayer);
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}

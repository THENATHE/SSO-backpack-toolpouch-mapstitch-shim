package com.thenathe.backpackcompat.mixin;

import com.thenathe.backpackcompat.BackpackCompat;
import me.pajic.tiered_backpacks.platform.fabric.FabricLoaderUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FabricLoaderUtil.class, remap = false)
public abstract class BackpackScreenMixin {
    // Also guards keybind requests, equipped/attached backpacks and accessory integrations.
    @Inject(method = "openBackpackScreen", at = @At("HEAD"), cancellable = true)
    private void backpackcompat$blockCustomScreen(Player player, ItemStack backpack, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (BackpackCompat.nativeClient(serverPlayer.connection.getPacketContext())) return;
            BackpackCompat.notice(serverPlayer);
        }
        ci.cancel();
    }
}

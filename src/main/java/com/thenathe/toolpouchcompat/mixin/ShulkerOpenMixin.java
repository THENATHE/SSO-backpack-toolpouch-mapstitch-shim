package com.thenathe.toolpouchcompat.mixin;

import me.pajic.toolpouch.network.NetworkEvents;
import me.pajic.toolpouch.util.ToolPouchUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NetworkEvents.class, remap = false)
public abstract class ShulkerOpenMixin {
    @Inject(method = "openShulkerBox", at = @At("HEAD"), cancellable = true)
    private static void multiShim$commitBeforeSnapshot(ServerPlayer player, int index, CallbackInfo ci) {
        // openMenu closes later, after the original method has already copied the child.
        if (player.containerMenu != player.inventoryMenu) player.closeContainer();
        int count = ToolPouchUtil.getItemsFromToolPouch(player, stack -> stack.is(ItemTags.SHULKER_BOXES)).size();
        if (index < 0 || index >= count) ci.cancel();
    }
}

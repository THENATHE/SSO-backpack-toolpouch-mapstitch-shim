package com.thenathe.toolpouchcompat.mixin;

import com.thenathe.toolpouchcompat.BoundShulkerContainer;
import com.thenathe.toolpouchcompat.PouchShulkerBinding;
import me.pajic.toolpouch.menu.ShulkerBoxContainerMenu;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ShulkerBoxContainerMenu.class, remap = false)
public abstract class ShulkerContainerMixin implements BoundShulkerContainer {
    @Shadow @Final private ItemStack shulker;
    @Shadow @Final private int slotInToolPouch;
    @Shadow protected NonNullList<ItemStack> items;
    @Unique private PouchShulkerBinding multiShim$binding;

    @Inject(method = "startOpen", at = @At("RETURN"))
    private void multiShim$bindOwner(ContainerUser user, CallbackInfo ci) {
        if (user instanceof ServerPlayer player) {
            multiShim$binding = PouchShulkerBinding.open(player, shulker, slotInToolPouch);
        }
    }

    @Unique private void multiShim$save() {
        if (multiShim$binding != null) multiShim$binding.save(shulker, items);
    }

    @Inject(method = {"setItem", "setChanged", "clearContent"}, at = @At("RETURN"))
    private void multiShim$saveChanges(CallbackInfo ci) { multiShim$save(); }

    @Inject(method = {"removeItem", "removeItemNoUpdate"}, at = @At("RETURN"))
    private void multiShim$saveRemoval(CallbackInfoReturnable<ItemStack> cir) { multiShim$save(); }

    @Inject(method = "stopOpen", at = @At("HEAD"), cancellable = true)
    private void multiShim$saveOriginalOwner(ContainerUser user, CallbackInfo ci) {
        multiShim$save();
        multiShim$binding = null;
        // Never run the upstream save into the currently preferred pouch.
        ci.cancel();
    }

    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true)
    private void multiShim$checkOwner(Player player, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(player instanceof ServerPlayer serverPlayer && multiShim$binding != null
                && multiShim$binding.validFor(serverPlayer));
    }

    @Override
    public boolean multiShim$isOwner(ItemStack stack) {
        return multiShim$binding != null && multiShim$binding.isOwner(stack);
    }
}

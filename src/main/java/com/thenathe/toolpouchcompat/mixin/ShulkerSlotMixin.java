package com.thenathe.toolpouchcompat.mixin;

import com.thenathe.toolpouchcompat.BoundShulkerContainer;
import net.minecraft.world.inventory.ShulkerBoxSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShulkerBoxSlot.class)
public abstract class ShulkerSlotMixin {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void multiShim$preventOwnerNesting(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (((Slot)(Object)this).container instanceof BoundShulkerContainer container && container.multiShim$isOwner(stack)) {
            cir.setReturnValue(false);
        }
    }
}

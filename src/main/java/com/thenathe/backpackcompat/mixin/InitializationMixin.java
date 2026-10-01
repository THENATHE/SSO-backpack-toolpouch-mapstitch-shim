package com.thenathe.backpackcompat.mixin;

import com.thenathe.backpackcompat.BackpackCompat;
import me.pajic.tiered_backpacks.platform.fabric.FabricEntrypoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FabricEntrypoint.class, remap = false)
public abstract class InitializationMixin {
    @Inject(method = "onInitialize", at = @At("TAIL"))
    private void backpackcompat$initialize(CallbackInfo ci) {
        BackpackCompat.initialize();
    }
}

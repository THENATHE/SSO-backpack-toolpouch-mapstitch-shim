package com.thenathe.mapstitchcompat.mixin;

import com.thenathe.mapstitchcompat.MapstitchCompat;
import me.pajic.mapstitch.platform.fabric.FabricEntrypoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FabricEntrypoint.class, remap = false)
public abstract class MapstitchInitializationMixin {
    @Inject(method = "onInitialize", at = @At("RETURN"))
    private void mapstitchcompat$overlays(CallbackInfo ci) {
        MapstitchCompat.initialize();
    }
}

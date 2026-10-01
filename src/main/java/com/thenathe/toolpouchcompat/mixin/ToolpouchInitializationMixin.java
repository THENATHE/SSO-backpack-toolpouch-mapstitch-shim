package com.thenathe.toolpouchcompat.mixin;

import com.thenathe.toolpouchcompat.ToolpouchCompat;
import me.pajic.toolpouch.platform.fabric.FabricEntrypoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FabricEntrypoint.class, remap = false)
public abstract class ToolpouchInitializationMixin {
    @Inject(method = "onInitialize", at = @At("RETURN"))
    private void toolpouchcompat$initialize(CallbackInfo ci) { ToolpouchCompat.initialize(); }
}

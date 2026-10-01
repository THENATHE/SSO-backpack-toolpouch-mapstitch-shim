package com.thenathe.ssopolymer.mixin;

import com.thenathe.ssopolymer.SmithingPolymer;
import me.pajic.simple_smithing_overhaul.platform.fabric.FabricEntrypoint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FabricEntrypoint.class, remap = false)
public abstract class InitializationMixin {
    @Inject(method = "onInitialize", at = @At("RETURN"))
    private void ssopolymer$initialize(CallbackInfo ci) { SmithingPolymer.initialize(); }
}

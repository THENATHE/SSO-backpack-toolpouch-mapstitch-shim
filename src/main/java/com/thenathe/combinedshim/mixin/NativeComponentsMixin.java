package com.thenathe.combinedshim.mixin;

import com.thenathe.combinedshim.NativeClients;
import eu.pb4.polymer.core.api.other.PolymerComponent;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PolymerComponent.class, remap = false)
public interface NativeComponentsMixin {
    @Inject(method = "canSync", at = @At("HEAD"), cancellable = true)
    private static void combinedshim$preserveNative(DataComponentType<?> type, Object value,
                                                     PacketContext context, CallbackInfoReturnable<Boolean> cir) {
        if (NativeClients.isNativeEntry(context, BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type))) {
            cir.setReturnValue(true);
        }
    }
}

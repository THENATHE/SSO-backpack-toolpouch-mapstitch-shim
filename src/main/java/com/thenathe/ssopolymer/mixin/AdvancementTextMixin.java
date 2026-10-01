package com.thenathe.ssopolymer.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.thenathe.ssopolymer.TextFallbacks;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Advancement titles remain readable even when the optional artwork pack is declined. */
@Mixin(DisplayInfo.class)
public abstract class AdvancementTextMixin {
    @ModifyReturnValue(method = {"title", "description"}, at = @At("RETURN"))
    private Component ssopolymer$readableText(Component original) {
        return TextFallbacks.withFallback(original);
    }
}

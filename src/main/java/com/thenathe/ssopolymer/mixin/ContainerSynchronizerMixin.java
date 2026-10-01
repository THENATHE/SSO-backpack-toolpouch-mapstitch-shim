package com.thenathe.ssopolymer.mixin;

import me.pajic.simple_smithing_overhaul.SSO;
import com.thenathe.combinedshim.NativeClients;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Suppress vanilla's hardcoded Too Expensive label; MenuGuidance shows the real cost. */
@Mixin(targets = "net.minecraft.server.level.ServerPlayer$1")
public abstract class ContainerSynchronizerMixin {
    @Shadow @Final ServerPlayer this$0;

    @ModifyVariable(method = "broadcastDataValue", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int ssoPolymer$displayActualUnlimitedCost(int value, AbstractContainerMenu menu, int index, int originalValue) {
        if (NativeClients.isNative(this$0.connection.getPacketContext(), "simple_smithing_overhaul")) return value;
        if (menu instanceof AnvilMenu && index == 0 && value >= 40
                && SSO.CONFIG.anvilImprovements.noTooExpensive.get()) {
            // A zero display value hides the vanilla label without inventing a cheaper cost.
            // The server retains its real DataSlot and checks/pays the full cost. Vanilla
            // still sends normal and shift-click packets when its local prediction denies pickup.
            return 0;
        }
        return value;
    }
}

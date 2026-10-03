package shulkerqa.mixin;

import java.nio.file.*;
import me.pajic.toolpouch.network.NetworkEvents;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records entry to the actual handler, before compatibility code can cancel it. */
@Mixin(value=NetworkEvents.class, remap=false, priority=2000)
public class ShulkerServerTraceMixin {
    @Inject(method="openShulkerBox", at=@At("HEAD"))
    private static void trace(ServerPlayer player, int index, CallbackInfo ci) {
        try {
            Files.writeString(Path.of(System.getProperty("shulker.qa.control"),"payload-trace.txt"),
                "open request index=" + index + " oldMenu=" + player.containerMenu.containerId + " type=" + player.containerMenu.getClass().getSimpleName() + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
}

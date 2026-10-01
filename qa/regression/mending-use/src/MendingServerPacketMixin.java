package mending.mixin;
import mending.MendingServerQa;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MendingServerPacketMixin {
 @Inject(method="handleUseItem",at=@At("HEAD")) private void count(ServerboundUseItemPacket packet,CallbackInfo ci){MendingServerQa.usePackets++;}
}

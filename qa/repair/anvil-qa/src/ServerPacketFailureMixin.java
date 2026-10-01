package qa.mixin;
import net.minecraft.network.Connection;
import io.netty.channel.ChannelHandlerContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Connection.class)
public class ServerPacketFailureMixin {
 @Inject(method="exceptionCaught",at=@At("HEAD")) private void qa$record(ChannelHandlerContext ctx,Throwable cause,CallbackInfo ci){cause.printStackTrace();}
}

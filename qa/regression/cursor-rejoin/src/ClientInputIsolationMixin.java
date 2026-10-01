package qa.mixin;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Isolate this disposable automation window from unrelated physical mouse input. */
@Mixin(MouseHandler.class)
public class ClientInputIsolationMixin {
 @Inject(method="onButton",at=@At("HEAD"),cancellable=true)
 private void qa$ignorePhysical(long window,MouseButtonInfo info,int action,CallbackInfo ci){ci.cancel();}
}

package qa.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.nio.file.*;

/** Observational only: records clicks on the game thread without changing menu state. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerCursorTraceMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="handleContainerClick", at=@At("HEAD"))
    private void cursorqa$before(ServerboundContainerClickPacket packet, CallbackInfo ci) { cursorqa$trace("BEFORE", packet); }
    @Inject(method="handleContainerClick", at=@At("RETURN"))
    private void cursorqa$after(ServerboundContainerClickPacket packet, CallbackInfo ci) { cursorqa$trace("AFTER", packet); }
    @Unique private void cursorqa$trace(String phase, ServerboundContainerClickPacket packet) {
        if (player == null || !player.level().getServer().isSameThread()) return;
        var menu=player.containerMenu; int slot=packet.slotNum();
        String item=slot>=0&&slot<menu.slots.size()?cursorqa$stack(menu.getSlot(slot).getItem()):"outside";
        cursorqa$write(phase+" packetContainer="+packet.containerId()+" packetState="+packet.stateId()
            +" menu="+menu.containerId+" menuState="+menu.getStateId()+" slot="+slot+" button="+packet.buttonNum()
            +" input="+packet.containerInput()+" slotItem="+item+" carried="+cursorqa$stack(menu.getCarried())
            +" changed="+packet.changedSlots().keySet()+" predictedCarried="+packet.carriedItem());
    }
    @Unique private static String cursorqa$stack(ItemStack stack) {
        return stack.isEmpty()?"EMPTY":BuiltInRegistries.ITEM.getKey(stack.getItem())+"x"+stack.getCount()+" damage="+stack.getDamageValue();
    }
    @Unique private static void cursorqa$write(String line) {
        String root=System.getProperty("creative.qa.control"); if(root==null)return;
        try { Files.writeString(Path.of(root,"cursor-trace-server"),System.currentTimeMillis()+" "+line+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND); }
        catch(java.io.IOException e) { System.err.println("CURSOR_QA_TRACE_IO "+e); }
    }
}

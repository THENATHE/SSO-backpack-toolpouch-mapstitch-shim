package qa.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.nio.file.*;

/** Observational only: records packet application and cursor state on the render thread. */
@Mixin(ClientPacketListener.class)
public abstract class ClientCursorTraceMixin {
    @Inject(method="handleSetCursorItem", at=@At("HEAD"))
    private void cursorqa$cursorBefore(ClientboundSetCursorItemPacket p,CallbackInfo ci){cursorqa$trace("CURSOR BEFORE item="+cursorqa$stack(p.contents()));}
    @Inject(method="handleSetCursorItem", at=@At("RETURN"))
    private void cursorqa$cursorAfter(ClientboundSetCursorItemPacket p,CallbackInfo ci){cursorqa$trace("CURSOR AFTER item="+cursorqa$stack(p.contents()));}
    @Inject(method="handleContainerSetSlot", at=@At("HEAD"))
    private void cursorqa$slotBefore(ClientboundContainerSetSlotPacket p,CallbackInfo ci){cursorqa$trace("SLOT BEFORE container="+p.getContainerId()+" state="+p.getStateId()+" slot="+p.getSlot()+" item="+cursorqa$stack(p.getItem()));}
    @Inject(method="handleContainerSetSlot", at=@At("RETURN"))
    private void cursorqa$slotAfter(ClientboundContainerSetSlotPacket p,CallbackInfo ci){cursorqa$trace("SLOT AFTER container="+p.getContainerId()+" state="+p.getStateId()+" slot="+p.getSlot()+" item="+cursorqa$stack(p.getItem()));}
    @Inject(method="handleContainerContent", at=@At("HEAD"))
    private void cursorqa$contentBefore(ClientboundContainerSetContentPacket p,CallbackInfo ci){cursorqa$trace("CONTENT BEFORE container="+p.containerId()+" state="+p.stateId()+" slots="+p.items().size()+" item="+cursorqa$stack(p.carriedItem()));}
    @Inject(method="handleContainerContent", at=@At("RETURN"))
    private void cursorqa$contentAfter(ClientboundContainerSetContentPacket p,CallbackInfo ci){cursorqa$trace("CONTENT AFTER container="+p.containerId()+" state="+p.stateId()+" slots="+p.items().size()+" item="+cursorqa$stack(p.carriedItem()));}
    @Unique private static void cursorqa$trace(String line) {
        var client=Minecraft.getInstance(); if(!client.isSameThread()||client.player==null)return;
        var menu=client.player.containerMenu;
        cursorqa$write(line+" menu="+menu.containerId+" menuState="+menu.getStateId()+" carried="+cursorqa$stack(menu.getCarried()));
    }
    @Unique private static String cursorqa$stack(ItemStack stack) {
        return stack.isEmpty()?"EMPTY":BuiltInRegistries.ITEM.getKey(stack.getItem())+"x"+stack.getCount()+" damage="+stack.getDamageValue();
    }
    @Unique private static void cursorqa$write(String line) {
        String root=System.getProperty("creative.qa.control");if(root==null)return;
        try { Files.writeString(Path.of(root,"cursor-trace-client"),System.currentTimeMillis()+" "+line+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND); }
        catch(java.io.IOException e) { System.err.println("CURSOR_QA_TRACE_IO "+e); }
    }
}

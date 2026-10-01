package pouchqa;
import java.nio.file.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;

public class PouchClientQa implements ClientModInitializer {
    final Path control=Path.of(System.getProperty("pouch.qa.control"));
    final boolean nativeClient=Boolean.getBoolean("pouch.qa.native");
    int phase,ticks,movedSlot;boolean done;
    void check(boolean value,String message)throws Exception{if(!value)throw new AssertionError(message);Files.writeString(control.resolve("assertions.txt"),message+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
    String id(ItemStack stack){return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();}
    public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(c->{if(done||c.player==null||c.level==null)return;try{
        if(++ticks>900)throw new AssertionError("timeout phase="+phase);
        switch(phase){
            case 0->{if(!Files.exists(control.resolve("seed"))||ticks<140)return;
                for(int i=0;i<2;i++){var stack=c.player.getInventory().getItem(i);check(!stack.isEmpty(),"slot "+i+" visible");
                    if(nativeClient){check(id(stack).equals(i==0?"toolpouch:tool_pouch":"toolpouch:netherite_tool_pouch"),"native pouch item "+i);check(stack.get(DataComponents.CONTAINER)!=null&&stack.get(DataComponents.CONTAINER).nonEmptyItemCopyStream().count()==(Boolean.getBoolean("pouch.qa.mapstitch")?4:3),"native contents synchronized "+i);if(Boolean.getBoolean("pouch.qa.mapstitch")){var nested=stack.get(DataComponents.CONTAINER).itemCopies().toList().get(3);check(Boolean.getBoolean("pouch.qa.client-mapstitch")?id(nested).equals("mapstitch:atlas"):id(nested).startsWith("minecraft:"),"nested Atlas independently translated: "+id(nested));}}
                    else{check(id(stack).startsWith("minecraft:"),"vanilla placeholder "+i+" = "+id(stack));var lore=stack.get(DataComponents.LORE);check(lore!=null&&lore.lines().stream().anyMatch(line->line.getString().toLowerCase().contains("mod")),"placeholder explains client mod requirement "+i);check(stack.get(DataComponents.CONTAINER)==null||stack.get(DataComponents.CONTAINER).nonEmptyItemCopyStream().allMatch(s->id(s).startsWith("minecraft:")||(Boolean.getBoolean("pouch.qa.client-mapstitch")&&id(s).equals("mapstitch:atlas"))),"placeholder nested contents use safe item IDs "+i);}
                }
                check(c.player.getInventory().getItem(2).is(Items.IRON_LEGGINGS),"ordinary leggings remain visible");
                if(Boolean.getBoolean("pouch.qa.mapstitch")){var atlas=c.player.getInventory().getItem(3);check(!atlas.isEmpty(),"coexisting Atlas item transmitted");check(Boolean.getBoolean("pouch.qa.client-mapstitch")?id(atlas).equals("mapstitch:atlas"):id(atlas).startsWith("minecraft:"),"independent MapStitch capability maps atlas correctly: "+id(atlas));}
                c.player.getInventory().setSelectedSlot(0);c.gameMode.useItem(c.player,InteractionHand.MAIN_HAND);phase=1;ticks=0;
            }
            case 1->{if(ticks<20)return;
                if(nativeClient){var noticeField=c.gui.hud.getClass().getDeclaredField("overlayMessageString");noticeField.setAccessible(true);var notice=(Component)noticeField.get(c.gui.hud);check(notice==null||!notice.getString().contains("need the Tool Pouch mod"),"native client has no missing-mod notice");check(c.player.containerMenu!=c.player.inventoryMenu,"native Toolpouch menu opens");check(c.gui.screen()!=null&&c.gui.screen().getClass().getName().contains("ToolPouch"),"native Toolpouch screen class");check(c.player.containerMenu.getSlot(0).getItem().is(Items.COMPASS),"native menu exposes stored compass");c.gameMode.handleContainerInput(c.player.containerMenu.containerId,0,0,net.minecraft.world.inventory.ContainerInput.QUICK_MOVE,c.player);phase=3;ticks=0;return;}
                else{check(c.player.containerMenu==c.player.inventoryMenu,"no custom menu sent to unsupported client");check(c.gui.screen()==null||!c.gui.screen().getClass().getName().contains("ToolPouch"),"no Toolpouch screen opened: "+(c.gui.screen()==null?"null":c.gui.screen().getClass().getName()));var field=c.gui.hud.getClass().getDeclaredField("overlayMessageString");field.setAccessible(true);var message=(Component)field.get(c.gui.hud);check(message!=null&&message.getString().toLowerCase().contains("mod"),"actual actionbar received: "+(message==null?"null":message.getString()));}
                Files.writeString(control.resolve("command"),"inspect");phase=2;ticks=0;
            }
            case 3->{if(ticks<20)return;check(c.player.containerMenu.getSlot(0).getItem().isEmpty(),"native menu extraction consumed pouch slot");movedSlot=-1;for(int i=9;i<c.player.containerMenu.slots.size();i++)if(c.player.containerMenu.getSlot(i).getItem().is(Items.COMPASS))movedSlot=i;check(movedSlot>=0,"native menu extraction reached inventory");c.gameMode.handleContainerInput(c.player.containerMenu.containerId,movedSlot,0,net.minecraft.world.inventory.ContainerInput.QUICK_MOVE,c.player);phase=4;ticks=0;}
            case 4->{if(ticks<20)return;check(c.player.containerMenu.getSlot(0).getItem().is(Items.COMPASS),"native menu reinsertion restored stored item");c.player.closeContainer();phase=5;ticks=0;}
            case 5->{if(ticks<20)return;check(c.player.getInventory().getItem(0).get(DataComponents.CONTAINER).nonEmptyItemCopyStream().count()==(Boolean.getBoolean("pouch.qa.mapstitch")?4:3),"native menu edits saved to pouch");Files.writeString(control.resolve("command"),"inspect");phase=2;ticks=0;}
            case 2->{if(!Files.exists(control.resolve("ack")))return;check(Files.readString(control.resolve("ack")).startsWith("PASS"),"server contents preserved after interaction");if(!nativeClient){var field=c.gui.hud.getClass().getDeclaredField("overlayMessageString");field.setAccessible(true);field.set(c.gui.hud,null);c.player.getInventory().setSelectedSlot(4);Files.writeString(control.resolve("command"),"armor");phase=6;ticks=0;return;}Files.writeString(control.resolve("result.txt"),"PASS "+(nativeClient?"native":"unsupported")+" client item synchronization, use and preservation\n");done=true;}
            case 6->{if(!Files.exists(control.resolve("armor-ready"))||ticks<20)return;var field=c.gui.hud.getClass().getDeclaredField("overlayMessageString");field.setAccessible(true);var message=(Component)field.get(c.gui.hud);if(message==null)return;check(c.player.getMainHandItem().isEmpty()&&c.player.getOffhandItem().isEmpty(),"armor notice while both hands empty");check(c.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).is(Items.IRON_LEGGINGS),"attached pouch leggings equipped");check(message.getString().contains("Tool Pouch mod"),"attached armor passive notice: "+message.getString());Files.writeString(control.resolve("result.txt"),"PASS unsupported client item synchronization, blocked use, preservation and equipped armor notice\n");done=true;}
        }
    }catch(Throwable t){t.printStackTrace();done=true;try{Files.writeString(control.resolve("result.txt"),"FAIL phase="+phase+" "+t);}catch(Exception ignored){}}});}
}

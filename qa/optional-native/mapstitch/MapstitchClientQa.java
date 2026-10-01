package qa;

import me.pajic.mapstitch.component.ModDataComponents;
import me.pajic.mapstitch.item.ModItems;
import me.pajic.mapstitch.minimap.MinimapOverlay;
import me.pajic.mapstitch.worldmap.WorldMapScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.level.saveddata.maps.MapId;
import java.nio.file.*;

public class MapstitchClientQa implements ClientModInitializer {
    int ticks=0,step=0,wait=0;boolean done=false;MapId mapId;
    Path result(Minecraft c){return Path.of(System.getProperty("mapstitch.qa.control"));}
    void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    ItemStack atlas(Minecraft c){for(int i=0;i<c.player.getInventory().getContainerSize();i++){var item=c.player.getInventory().getItem(i);if(item.is(ModItems.ATLAS))return item;}return ItemStack.EMPTY;}
    void validate(ItemStack stack){
        check(stack.is(ModItems.ATLAS),"native atlas item missing");
        check(stack.getOrDefault(ModDataComponents.ATLAS_SCALE,-1)==0,"native scale component missing");
        var contents=stack.get(DataComponents.BUNDLE_CONTENTS);check(contents!=null,"bundle contents missing");
        check(contents.items().stream().anyMatch(s->mapId.equals(s.get(DataComponents.MAP_ID))),"nested native filled-map ID missing");
    }
    @Override public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(c->{if(done||c.player==null||c.level==null)return;
        try{ticks++;wait++;if(ticks>1800)throw new AssertionError("timeout step="+step);
            switch(step){
                case 0->{Path file=result(c).resolve("map-id.txt");if(Files.exists(file)&&c.player.containerMenu!=c.player.inventoryMenu){mapId=new MapId(Integer.parseInt(Files.readString(file).trim()));validate(c.player.containerMenu.getSlot(0).getItem());c.gameMode.handleContainerInput(c.player.containerMenu.containerId,0,0,ContainerInput.QUICK_MOVE,c.player);step=1;wait=0;}}
                case 1->{if(wait>20){validate(atlas(c));check(c.player.containerMenu.getSlot(1).getItem().isEmpty()&&c.player.containerMenu.getSlot(2).getItem().isEmpty(),"crafting did not consume ingredients");c.player.closeContainer();step=2;wait=0;}}
                case 2->{if(wait>100){validate(atlas(c));check(c.level.getMapData(mapId)!=null,"map data never synchronized");check(atlas(c).getOrDefault(ModDataComponents.ATLAS_ACTIVE_MAP_ID,-1)==mapId.id(),"active map ID missing");
                    var field=MinimapOverlay.class.getDeclaredField("lastAtlas");field.setAccessible(true);check(((ItemStack)field.get(null)).is(ModItems.ATLAS),"minimap did not render native atlas");
                    int slot=-1;for(int i=0;i<9;i++)if(c.player.getInventory().getItem(i).is(ModItems.ATLAS))slot=i;
                    check(slot>=0,"crafted atlas not in hotbar");c.player.getInventory().setSelectedSlot(slot);c.gameMode.useItem(c.player,InteractionHand.MAIN_HAND);step=3;wait=0;}}
                case 3->{if(wait>30){check(c.gui.screen() instanceof WorldMapScreen,"real item-use did not open native worldmap");validate(atlas(c));Files.writeString(result(c).resolve("result.txt"),"PASS: native crafting network preview/result, ingredients consumed, atlas components/nested map, active map sync, minimap render state and server-opened worldmap\n");System.out.println("MAPSTITCH_NATIVE_QA_PASS");done=true;c.gui.screen().onClose();}}
            }
        }catch(Throwable t){t.printStackTrace();try{Files.createDirectories(result(c));Files.writeString(result(c).resolve("result.txt"),"FAIL step="+step+" "+t+"\n");}catch(Exception ignored){}done=true;}
    });}
}

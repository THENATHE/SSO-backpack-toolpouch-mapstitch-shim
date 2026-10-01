package qa;

import me.pajic.mapstitch.item.ModItems;
import me.pajic.mapstitch.component.ModDataComponents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapId;
import java.nio.file.*;
import java.util.*;

public class MapstitchServerQa implements ModInitializer {
    final Map<UUID,Integer> ages=new HashMap<>();
    final Path control=Path.of(System.getProperty("mapstitch.qa.control"));
    @Override public void onInitialize(){
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{ages.put(handler.player.getUUID(),0);System.out.println("MAPSTITCH_QA_JOIN "+handler.player.getGameProfile().name());});
        ServerTickEvents.END_SERVER_TICK.register(server->{for(ServerPlayer p:server.getPlayerList().getPlayers()){
            int age=ages.merge(p.getUUID(),1,Integer::sum);
            if(age==100)try{seed(p);}catch(Throwable t){t.printStackTrace();write(p,"server-failure.txt",t.toString());}
            if(age==700)System.out.println("MAPSTITCH_QA_SURVIVED_30_SECONDS "+p.getGameProfile().name());
        }});
    }
    void write(ServerPlayer p,String file,String text){try{Path dir=control;Files.createDirectories(dir);Files.writeString(dir.resolve(file),text+"\n");}catch(Exception e){throw new RuntimeException(e);}}
    void seed(ServerPlayer p){
        p.getInventory().clearContent();p.getInventory().setItem(8,new ItemStack(Items.COMPASS));p.getInventory().setItem(7,new ItemStack(Items.CLOCK));
        ItemStack map=MapItem.create(p.level(),p.getBlockX(),p.getBlockZ(),(byte)0,true,false);
        MapId id=map.get(DataComponents.MAP_ID);
        var data=p.level().getMapData(id);for(int i=0;i<data.colors.length;i++)data.colors[i]=(byte)(16+i%4);data.setDirty();
        write(p,"map-id.txt",Integer.toString(id.id()));
        if(p.getGameProfile().name().equals("StitchVanillaQA")){
            ItemStack atlas=new ItemStack(ModItems.ATLAS);atlas.set(ModDataComponents.ATLAS_SCALE,0);
            p.getInventory().setItem(0,atlas);p.getInventory().setItem(1,map);p.inventoryMenu.broadcastFullState();
            write(p,"server-seed.txt","PASS: vanilla joined; native atlas and filled map transmitted");return;
        }
        var pos=p.blockPosition().offset(1,0,0);p.level().setBlockAndUpdate(pos,Blocks.CRAFTING_TABLE.defaultBlockState());
        p.openMenu(new SimpleMenuProvider((menuId,inv,player)->new CraftingMenu(menuId,inv,ContainerLevelAccess.create(p.level(),pos)),Component.literal("Mapstitch QA crafting")));
        p.containerMenu.getSlot(1).set(new ItemStack(Items.BOOK));p.containerMenu.getSlot(2).set(map);
        p.containerMenu.broadcastFullState();
        if(!p.containerMenu.getSlot(0).getItem().is(ModItems.ATLAS))throw new AssertionError("Server crafting preview is not atlas");
        write(p,"server-seed.txt","PASS: native crafting recipe preview and map "+id.id());
    }
}

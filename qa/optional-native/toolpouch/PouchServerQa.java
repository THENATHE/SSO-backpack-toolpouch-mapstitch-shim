package pouchqa;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import me.pajic.toolpouch.item.ModItems;

public class PouchServerQa implements ModInitializer {
    final Path control=Path.of(System.getProperty("pouch.qa.control"));
    final Map<UUID,Integer> ages=new HashMap<>();
    String previous="";
    void write(String name,String value)throws Exception {Files.writeString(control.resolve(name),value);}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public void onInitialize(){
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->ages.put(handler.player.getUUID(),0));
        ServerTickEvents.END_SERVER_TICK.register(server->{try{
            for(var p:server.getPlayerList().getPlayers()){
                int age=ages.merge(p.getUUID(),1,Integer::sum);
                if(age==60){
                    p.getInventory().clearContent();p.setPermanentlyInvulnerable(true);
                    for(int i=0;i<2;i++){
                        var pouch=new ItemStack(i==0?ModItems.TOOL_POUCH:ModItems.NETHERITE_TOOL_POUCH);
                        pouch.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(new ItemStack(Items.COMPASS),new ItemStack(Items.ELYTRA),new ItemStack(Items.FIREWORK_ROCKET,16))));
                        pouch.set(DataComponents.DYED_COLOR,new DyedItemColor(0x2468ac));
                        p.getInventory().setItem(i,pouch);
                    }
                    var leggings=new ItemStack(Items.IRON_LEGGINGS);
                    leggings.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND_AXE))));
                    p.getInventory().setItem(2,leggings);
                    var atlasItem=BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("mapstitch","atlas"));
                    if(atlasItem!=null&&atlasItem!=Items.AIR){var atlas=new ItemStack(atlasItem);atlas.set(DataComponents.BUNDLE_CONTENTS,new BundleContents(List.of(ItemStackTemplate.fromNonEmptyStack(MapItem.create(p.level(),p.getBlockX(),p.getBlockZ(),(byte)0,true,false)))));p.getInventory().setItem(3,atlas);for(int slot=0;slot<2;slot++){var contents=new java.util.ArrayList<ItemStack>(p.getInventory().getItem(slot).get(DataComponents.CONTAINER).itemCopies().toList());contents.add(atlas.copy());p.getInventory().getItem(slot).set(DataComponents.CONTAINER,ItemContainerContents.fromItems(contents));}}
                    p.getInventory().setSelectedSlot(0);p.inventoryMenu.broadcastFullState();write("seed","ready");
                }
                if(Files.exists(control.resolve("command"))){String command=Files.readString(control.resolve("command")).trim();if(!command.equals(previous)){
                    if(command.equals("inspect")){
                        for(int i=0;i<2;i++){var stack=p.getInventory().getItem(i);check(stack.is(i==0?ModItems.TOOL_POUCH:ModItems.NETHERITE_TOOL_POUCH),"server native pouch lost");var c=stack.get(DataComponents.CONTAINER);check(c!=null&&c.nonEmptyItemCopyStream().count()==(Boolean.getBoolean("pouch.qa.mapstitch")?4:3),"server stored contents lost");check(c.nonEmptyItemCopyStream().anyMatch(s->s.is(Items.FIREWORK_ROCKET)&&s.getCount()==16),"rocket count changed");check(c.nonEmptyItemCopyStream().anyMatch(s->s.is(Items.COMPASS)),"compass roundtrip lost");if(Boolean.getBoolean("pouch.qa.mapstitch"))check(c.nonEmptyItemCopyStream().anyMatch(s->BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals("mapstitch:atlas")),"nested server Atlas identity lost");}
                        check(p.getInventory().getItem(2).get(DataComponents.CONTAINER).nonEmptyItemCopyStream().anyMatch(s->s.is(Items.DIAMOND_AXE)),"leggings container lost");
                        var method=net.minecraft.world.entity.LivingEntity.class.getDeclaredMethod("canGlide");method.setAccessible(true);boolean grounded=p.onGround();p.setOnGround(false);boolean glide;try{glide=(boolean)method.invoke(p);}finally{p.setOnGround(grounded);}check(glide==Boolean.getBoolean("pouch.qa.native"),"server passive pouch Elytra eligibility expected="+Boolean.getBoolean("pouch.qa.native")+" actual="+glide);
                        write("ack","PASS server real pouch IDs and container data preserved; passive Elytra="+glide);
                    }
                    if(command.equals("armor")){p.getInventory().setSelectedSlot(4);p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS,p.getInventory().getItem(2).copy());p.getInventory().setItem(2,ItemStack.EMPTY);p.inventoryMenu.broadcastFullState();write("armor-ready","ready");}
                    previous=command;
                }}
            }
        }catch(Throwable t){t.printStackTrace();try{write("failure","FAIL server "+t);}catch(Exception ignored){}}});
    }
}

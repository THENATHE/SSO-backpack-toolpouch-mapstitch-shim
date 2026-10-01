package qa;

import me.pajic.tiered_backpacks.component.ModDataComponents;
import me.pajic.tiered_backpacks.item.ModItems;
import me.pajic.tiered_backpacks.ui.BackpackMenu;
import me.pajic.tiered_backpacks.ui.BackpackScreen;
import me.pajic.tiered_backpacks.util.BackpackTier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import me.pajic.tiered_backpacks.network.ModNetworking;
import net.minecraft.world.entity.EquipmentSlot;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import java.nio.file.*;

/** Real original-mod client; this fixture observes and drives ordinary gameplay only. */
public class BackpackClientQa implements ClientModInitializer {
    int ticks,step,wait,tier;boolean done;
    static final Item[] TIERS={ModItems.LEATHER_BACKPACK,ModItems.COPPER_BACKPACK,ModItems.IRON_BACKPACK,ModItems.GOLDEN_BACKPACK,ModItems.DIAMOND_BACKPACK,ModItems.NETHERITE_BACKPACK};
    Path result(Minecraft c){return Path.of(System.getProperty("backpack.qa.control"),c.getUser().getName());}
    void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    void open(Minecraft c){c.player.getInventory().setSelectedSlot(tier);c.gameMode.useItem(c.player,InteractionHand.MAIN_HAND);}
    void menu(Minecraft c){check(c.gui.screen() instanceof BackpackScreen,"native BackpackScreen missing tier="+tier);check(c.player.containerMenu instanceof BackpackMenu,"native BackpackMenu missing");}
    @Override public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(c->{if(done||c.player==null||c.level==null)return;
        try{ticks++;wait++;if(ticks>3600)throw new AssertionError("timeout step="+step+" tier="+tier);
            switch(step){
                case 0->{if(Files.exists(result(c).resolve("seed-ready.txt"))&&wait>140){
                    for(int i=0;i<TIERS.length;i++){
                        ItemStack s=c.player.getInventory().getItem(i);
                        check(s.is(TIERS[i]),"native tier item missing "+i);
                        check(s.get(ModDataComponents.BACKPACK_TIER)==BackpackTier.values()[i],"native tier component missing "+i);
                        check(s.get(DataComponents.CONTAINER).itemCopies().anyMatch(x->x.is(Items.DIAMOND)&&x.getCount()==7),"native stored diamonds missing "+i);
                        check(s.get(ModDataComponents.STORED_BACKPACK_DYE).rgb()==0xabcdef,"native stored dye missing "+i);
                    }
                    check(c.player.getInventory().getItem(6).get(ModDataComponents.BACKPACK_TIER)==BackpackTier.IRON,"attached armor tier missing");
                    open(c);step=1;wait=0;
                }}
                case 1->{if(wait>25){menu(c);check(c.player.containerMenu.getSlot(0).getItem().is(Items.DIAMOND),"native content slot missing");
                    c.gameMode.handleContainerInput(c.player.containerMenu.containerId,0,0,ContainerInput.QUICK_MOVE,c.player);step=2;wait=0;}}
                case 2->{if(wait>25){menu(c);check(c.player.containerMenu.getSlot(0).getItem().isEmpty(),"quick move did not remove diamonds");c.player.closeContainer();step=3;wait=0;}}
                case 3->{if(wait>25){open(c);step=4;wait=0;}}
                case 4->{if(wait>25){menu(c);check(c.player.containerMenu.getSlot(0).getItem().isEmpty(),"removed contents returned after reopen");check(c.player.containerMenu.getSlot(1).getItem().is(Items.EMERALD)&&c.player.containerMenu.getSlot(1).getItem().getCount()==3,"untouched contents lost after reopen");c.player.closeContainer();tier++;step=5;wait=0;}}
                case 5->{if(wait>25){if(tier<TIERS.length){open(c);step=1;wait=0;}else{
                    int diamonds=0;for(int i=0;i<c.player.getInventory().getContainerSize();i++){var s=c.player.getInventory().getItem(i);if(s.is(Items.DIAMOND))diamonds+=s.getCount();}
                    check(diamonds==42,"expected all 42 withdrawn diamonds, got "+diamonds);
                    c.player.getInventory().setSelectedSlot(8);
                    ClientPlayNetworking.send(new ModNetworking.C2SOpenBackpackPayload(0));step=6;wait=0;
                }}}
                case 6->{if(wait>25){menu(c);check(c.player.containerMenu.getSlot(1).getItem().is(Items.EMERALD),"inventory keybind payload lost content");c.player.closeContainer();step=7;wait=0;}}
                case 7->{if(wait>25){c.player.getInventory().setSelectedSlot(6);c.gameMode.useItem(c.player,InteractionHand.MAIN_HAND);step=8;wait=0;}}
                case 8->{if(wait>25){check(c.player.getItemBySlot(EquipmentSlot.CHEST).get(ModDataComponents.BACKPACK_TIER)==BackpackTier.IRON,"attached chestplate did not equip natively");ClientPlayNetworking.send(new ModNetworking.C2SOpenBackpackPayload(1));step=9;wait=0;}}
                case 9->{if(wait>25){menu(c);check(c.player.containerMenu.getSlot(0).getItem().is(Items.GOLD_INGOT),"attached armor keybind contents missing");c.gameMode.handleContainerInput(c.player.containerMenu.containerId,0,0,ContainerInput.QUICK_MOVE,c.player);step=10;wait=0;}}
                case 10->{if(wait>25){check(c.player.containerMenu.getSlot(0).getItem().isEmpty(),"attached armor withdrawal failed");c.player.closeContainer();step=11;wait=0;}}
                case 11->{if(wait>25){ClientPlayNetworking.send(new ModNetworking.C2SOpenBackpackPayload(1));step=12;wait=0;}}
                case 12->{if(wait>25){menu(c);check(c.player.containerMenu.getSlot(0).getItem().isEmpty(),"attached armor contents returned after reopen");c.player.closeContainer();step=13;wait=0;}}
                case 13->{if(wait>25){
                    int gold=0;for(int i=0;i<c.player.getInventory().getContainerSize();i++){var s=c.player.getInventory().getItem(i);if(s.is(Items.GOLD_INGOT))gold+=s.getCount();}
                    check(gold==4,"expected all four withdrawn attached-armor ingots");
                    if(Files.exists(result(c).resolve("atlas-ready.txt"))) {
                        check(BuiltInRegistries.ITEM.getKey(c.player.getInventory().getItem(7).getItem()).equals(Identifier.parse("mapstitch:atlas")),"original native Atlas item missing in combined setup");
                        c.player.getInventory().setSelectedSlot(7);c.gameMode.useItem(c.player,InteractionHand.MAIN_HAND);step=14;wait=0;
                    }else finish(c,false);
                }}
                case 14->{if(wait>25){check(c.gui.screen()!=null&&c.gui.screen().getClass().getName().equals("me.pajic.mapstitch.worldmap.WorldMapScreen"),"native Atlas worldmap failed in combined setup");c.gui.screen().onClose();finish(c,true);}}
            }
        }catch(Throwable t){t.printStackTrace();try{Files.createDirectories(result(c));Files.writeString(result(c).resolve("client-result.txt"),"FAIL step="+step+" tier="+tier+" "+t+"\n");}catch(Exception ignored){}done=true;}
    });}
    void finish(Minecraft c,boolean atlas) throws Exception {
        Files.writeString(result(c).resolve("client-result.txt"),"PASS: all six native item tiers, tier/dye/content components and attached armor; real native right-click screens, quick-move withdrawal, close/reopen persistence; 42 diamonds retained; inventory keybind payload; native attached chestplate equip, equipped keybind payload and four gold ingots withdrawn with reopen persistence\n");
        if(atlas)Files.writeString(result(c).resolve("atlas-result.txt"),"PASS: original native Atlas item and worldmap coexist with full native Tiered Backpacks menus and components\n");
        System.out.println("BACKPACK_NATIVE_QA_PASS");done=true;
    }
}

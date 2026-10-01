package qa;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import java.nio.file.*;
public class CreativeClient implements ClientModInitializer {
 final Path control=Path.of(System.getProperty("creative.qa.control"));final boolean table=System.getProperty("whetstone.qa.scenario", "inventory").equals("table");int ticks=0,stage=0,move=0;boolean done=false;
 void write(String name,String text)throws Exception{Files.writeString(control.resolve(name),text);}
 public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(c->{if(done||c.player==null)return;try{
 ticks++;var m=c.player.containerMenu;
 if(stage==0&&ticks>80){if(!table)c.gui.setScreen(new InventoryScreen(c.player));write("ready","yes");stage=1;ticks=0;}
 else if(stage==1&&Files.exists(control.resolve("prepared"))&&ticks>60){stage=2;ticks=0;}
 else if(stage==2&&ticks>10){int[] moves=table?new int[]{37,1,38,2,39,3}:new int[]{36,1,37,2,38,3};c.gameMode.handleContainerInput(m.containerId,moves[move++],0,ContainerInput.PICKUP,c.player);ticks=0;if(move==moves.length){write("placed","yes");stage=3;}}
 else if(stage==3&&ticks>100){var out=m.getSlot(0).getItem();write("client-output","result="+out+" damage="+out.getDamageValue()+" stone="+m.getSlot(3).getItem());if(!out.is(Items.DIAMOND_PICKAXE)||out.getDamageValue()!=(System.getProperty("whetstone.qa.scenario", "inventory").equals("commands")?259:479))throw new AssertionError("no repaired output: "+out);c.gameMode.handleContainerInput(m.containerId,0,0,ContainerInput.PICKUP,c.player);stage=4;ticks=0;}
 else if(stage==4&&ticks>20){c.gameMode.handleContainerInput(m.containerId,table?40:39,0,ContainerInput.PICKUP,c.player);write("taken","yes");stage=5;ticks=0;}
 else if(stage==5&&ticks>60&&Files.exists(control.resolve("server-result"))){if(!Files.readString(control.resolve("server-result")).startsWith("PASS"))throw new AssertionError(Files.readString(control.resolve("server-result")));var out=m.getSlot(table?40:39).getItem();if(!out.is(Items.DIAMOND_PICKAXE)||out.getDamageValue()!=(System.getProperty("whetstone.qa.scenario", "inventory").equals("commands")?259:479))throw new AssertionError("client retained result incorrect");write("client-result","PASS: real inventory clicks produced and took enchanted diamond pickaxe repair, damage="+out.getDamageValue());done=true;}
 }catch(Throwable e){e.printStackTrace();try{write("client-result","FAIL: "+e);}catch(Exception ignored){}done=true;}});}
}

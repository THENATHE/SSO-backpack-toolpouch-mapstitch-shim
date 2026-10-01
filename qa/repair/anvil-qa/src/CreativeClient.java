package qa;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantments;
import java.nio.file.*;
public class CreativeClient implements ClientModInitializer {
 final Path control=Path.of(System.getProperty("creative.qa.control"));int ticks=0,stage=0,move=0;boolean done=false;
 void write(String name,String text)throws Exception{Files.writeString(control.resolve(name),text);}
 public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(c->{if(done||c.player==null)return;try{
 ticks++;var m=c.player.containerMenu;
 if(stage==0&&ticks>80){write("ready","yes");stage=1;ticks=0;}
 else if(stage==1&&Files.exists(control.resolve("prepared"))&&ticks>60){stage=2;ticks=0;}
 else if(stage==2&&ticks>15){boolean shift=System.getProperty("anvil.qa.clicks","pickup").equals("shift");int[] moves=shift?new int[]{30,31}:new int[]{30,0,31,1};int slot=moves[move++];write("client-progress","click slot="+slot+" before carried="+m.getCarried().getComponentsPatch());c.gameMode.handleContainerInput(m.containerId,slot,0,shift?ContainerInput.QUICK_MOVE:ContainerInput.PICKUP,c.player);ticks=0;if(move==moves.length){write("placed","yes");stage=3;}}
 else if(stage==3&&ticks>100){if(Boolean.getBoolean("anvil.qa.highCost")&&((net.minecraft.world.inventory.AnvilMenu)m).getCost()!=41)throw new AssertionError("native client lost real cost: "+((net.minecraft.world.inventory.AnvilMenu)m).getCost());var out=m.getSlot(2).getItem();write("client-output","result="+out+" components="+out.getComponentsPatch());if(!out.is(Items.DIAMOND_PICKAXE))throw new AssertionError("no output: "+out);write("client-progress","taking result");c.gameMode.handleContainerInput(m.containerId,2,0,ContainerInput.PICKUP,c.player);stage=4;ticks=0;}
 else if(stage==4&&ticks>20){write("client-progress","storing result");c.gameMode.handleContainerInput(m.containerId,32,0,ContainerInput.PICKUP,c.player);write("taken","yes");stage=5;ticks=0;}
 else if(stage==5&&ticks>60&&Files.exists(control.resolve("server-result"))){if(!Files.readString(control.resolve("server-result")).startsWith("PASS"))throw new AssertionError(Files.readString(control.resolve("server-result")));var out=m.getSlot(32).getItem();var mend=c.player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING);if(!out.is(Items.DIAMOND_PICKAXE)||out.getEnchantments().getLevel(mend)!=1)throw new AssertionError("client result incorrect");write("client-result","PASS: native anvil clicks applied and took Mending pickaxe");done=true;}
 }catch(Throwable e){e.printStackTrace();try{write("client-result","FAIL: "+e);}catch(Exception ignored){}done=true;}});}
}

package acquisition;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import me.pajic.simple_smithing_overhaul.items.ModItems;
public class AcquisitionClientQa implements ClientModInitializer {
 final Path control=Path.of(System.getProperty("acquisition.qa.control"));
 String current="",completed="";int ticks,step;boolean failed,creativeSent;List<int[]> moves=new ArrayList<>();
 void write(String file,String text)throws Exception{Files.writeString(control.resolve(file),text);}
 void log(String text)throws Exception{Files.writeString(control.resolve("client-evidence.txt"),text+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
 void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
 void click(Minecraft c,int slot,int button){c.gameMode.handleContainerInput(c.player.containerMenu.containerId,slot,button,ContainerInput.PICKUP,c.player);}
 String detail(ItemStack stack){var repair=stack.get(net.minecraft.core.component.DataComponents.REPAIRABLE);return " stack="+stack+" repairable="+repair+" diamond="+(repair!=null&&repair.isValidRepairItem(new ItemStack(Items.DIAMOND)))+" calcite="+(repair!=null&&repair.isValidRepairItem(new ItemStack(Items.CALCITE)))+" prototype="+stack.getPrototype().get(net.minecraft.core.component.DataComponents.REPAIRABLE)+" patch="+stack.getComponentsPatch();}
 void material(ItemStack stack,boolean pickaxe){if(!Boolean.getBoolean("acquisition.expect-fixed"))return;var repair=stack.get(net.minecraft.core.component.DataComponents.REPAIRABLE);check(repair!=null&&repair.isValidRepairItem(new ItemStack(pickaxe?Items.DIAMOND:Items.QUARTZ))&&!repair.isValidRepairItem(new ItemStack(Items.CALCITE)),"wrong client repair material "+detail(stack));}
 void plan(String action,String origin){moves.clear();step=0;creativeSent=false;
  if(origin.equals("crafted")){
   moves.add(new int[]{37,0});
   for(int slot:action.equals("tool")?new int[]{1,2,3}:new int[]{1,2,3,4,5,6})moves.add(new int[]{slot,1});
   if(action.equals("tool")){moves.add(new int[]{38,0});moves.add(new int[]{5,1});moves.add(new int[]{8,1});}
   moves.add(new int[]{0,0});moves.add(new int[]{action.equals("tool")?40:41,0});
  }else if(origin.equals("anvil")){for(int slot:new int[]{30,0,31,1,2,action.equals("enchant-tool")?33:34})moves.add(new int[]{slot,0});
  }else if(action.equals("repair")){for(int slot:new int[]{36,1,37,2,38,3})moves.add(new int[]{slot,0});if(!origin.equals("overstack9")){moves.add(new int[]{0,0});moves.add(new int[]{39,0});}}
 }
 public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(c->{if(failed||c.player==null||c.level==null)return;try{
  if(!Files.exists(control.resolve("command")))return;String next=Files.readString(control.resolve("command"));if(next.equals(completed))return;
  String[] parts=next.split(":");String action=parts[1],origin=parts[2];
  if(!next.equals(current)){current=next;ticks=0;plan(action,origin);material(new ItemStack(Items.DIAMOND_PICKAXE),true);log("CLIENT DEFAULTS "+current+detail(new ItemStack(Items.DIAMOND_PICKAXE))+" chalkTagSize="+net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,net.minecraft.resources.Identifier.parse("chalk:chalks"))).map(net.minecraft.core.HolderSet::size).orElse(-1));if(action.equals("repair"))c.gui.setScreen(new InventoryScreen(c.player));}
  if(++ticks>1000)throw new AssertionError("timeout "+current+" step="+step);
  if(ticks<30)return;
  if(origin.equals("creative")&&!creativeSent){c.gameMode.handleCreativeModeItemAdd(new ItemStack(action.equals("tool")?Items.DIAMOND_PICKAXE:ModItems.WHETSTONE),action.equals("tool")?39:40);creativeSent=true;ticks=0;return;}
  if(step<moves.size()){
   if(ticks%10!=0)return;var move=moves.get(step);var menu=c.player.containerMenu;
   if((!origin.equals("anvil")&&move[0]==0)||(origin.equals("anvil")&&step==4)){var expected=action.endsWith("stone")?ModItems.WHETSTONE:Items.DIAMOND_PICKAXE;var output=menu.getSlot(origin.equals("anvil")?2:0).getItem();
    if(!output.is(expected)){if(ticks>200)throw new AssertionError("missing "+action+" output origin="+origin+" actual="+output+" inputs="+menu.getSlot(1).getItem()+","+menu.getSlot(2).getItem()+","+menu.getSlot(3).getItem());return;}
    if(action.equals("repair"))check(output.getDamageValue()==0,"preview not repaired");log("PREVIEW "+current+" result="+output+" damage="+output.getDamageValue());
   }
   click(c,move[0],move[1]);step++;if(step==moves.size())ticks=0;return;
  }
  if(ticks<30)return;
  if(origin.equals("overstack9")){check(c.player.containerMenu.getSlot(0).getItem().isEmpty(),"oversized repair output should be absent");log("PASS CLIENT oversized9 no output, input count="+c.player.containerMenu.getSlot(2).getItem().getCount());write("ack",current);completed=current;return;}
  int slot=action.endsWith("stone")?4:3;var acquired=c.player.getInventory().getItem(slot);check(acquired.is(action.endsWith("stone")?ModItems.WHETSTONE:Items.DIAMOND_PICKAXE),"wrong retained item "+current+" "+acquired);
  if(action.equals("repair"))check(acquired.getDamageValue()==0,"retained item not repaired");
  material(acquired,!action.endsWith("stone"));log("PASS CLIENT "+current+detail(acquired)+" damage="+acquired.getDamageValue());write("ack",current);completed=current;
 }catch(Throwable failure){failed=true;failure.printStackTrace();try{write("result.txt","FAIL client "+current+" step="+step+" "+failure);}catch(Exception ignored){}}});}
}

package acquisition;
import java.nio.file.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.network.chat.Component;
import me.pajic.simple_smithing_overhaul.items.ModItems;
public class AcquisitionServerQa implements ModInitializer {
 final Path control=Path.of(System.getProperty("acquisition.qa.control"));
 final String[] origins={"crafted","give","creative"};
 final boolean enchanted=Boolean.parseBoolean(System.getProperty("acquisition.enchanted","true"));
 int scenario,phase,ticks,sequence; boolean waiting,done; String command; ItemStack tool,stone;
 void write(String file,String text)throws Exception{Files.writeString(control.resolve(file),text);}
 void log(String text)throws Exception{Files.writeString(control.resolve("server-evidence.txt"),text+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
 void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 void issue(String action,String origin)throws Exception{command=(++sequence)+":"+action+":"+origin;write("command",command);waiting=true;ticks=0;}
 void acquire(ServerPlayer p,boolean pickaxe,String origin)throws Exception{
  p.closeContainer();p.getInventory().clearContent();p.setGameMode(origin.equals("creative")?GameType.CREATIVE:GameType.SURVIVAL);p.setPermanentlyInvulnerable(true);
  if(origin.equals("crafted")){
   p.getInventory().setItem(0,new ItemStack(pickaxe?Items.DIAMOND:Items.QUARTZ,pickaxe?3:6));
   if(pickaxe)p.getInventory().setItem(1,new ItemStack(Items.STICK,2));
   var pos=p.blockPosition().below();p.level().setBlock(pos,Blocks.CRAFTING_TABLE.defaultBlockState(),3);
   p.openMenu(new SimpleMenuProvider((id,inv,player)->new CraftingMenu(id,inv,ContainerLevelAccess.create(p.level(),pos)),Component.literal("Acquisition QA crafting")));
  }else if(origin.equals("give")){
   String id=pickaxe?"minecraft:diamond_pickaxe":"simple_smithing_overhaul:whetstone";
   p.level().getServer().getCommands().performPrefixedCommand(p.level().getServer().createCommandSourceStack(),"give "+p.getScoreboardName()+" "+id+" 1");
   var acquired=p.getInventory().getItem(0);check(acquired.is(pickaxe?Items.DIAMOND_PICKAXE:ModItems.WHETSTONE),"give failed "+id);
   p.getInventory().setItem(pickaxe?3:4,acquired);p.getInventory().setItem(0,ItemStack.EMPTY);
  }
  p.containerMenu.broadcastFullState();issue(pickaxe?"tool":"stone",origin);
 }
 void enchant(ServerPlayer p,boolean pickaxe)throws Exception{
  p.closeContainer();p.getInventory().clearContent();p.setGameMode(GameType.SURVIVAL);p.setExperienceLevels(100);
  p.getInventory().setItem(0,pickaxe?tool:stone);
  p.level().getServer().getCommands().performPrefixedCommand(p.level().getServer().createCommandSourceStack(),"give "+p.getScoreboardName()+" minecraft:enchanted_book[minecraft:stored_enchantments={\"minecraft:efficiency\":1}] 1");
  check(p.getInventory().getItem(1).is(Items.ENCHANTED_BOOK),"actual give book missing");
  var pos=p.blockPosition().below();p.level().setBlock(pos,Blocks.ANVIL.defaultBlockState(),3);
  p.openMenu(new SimpleMenuProvider((id,inv,player)->new AnvilMenu(id,inv,ContainerLevelAccess.create(p.level(),pos)),Component.literal("Acquisition QA anvil")));
  p.containerMenu.broadcastFullState();issue(pickaxe?"enchant-tool":"enchant-stone","anvil");
 }
 String detail(ItemStack stack){var repair=stack.get(net.minecraft.core.component.DataComponents.REPAIRABLE);return " stack="+stack+" repairable="+repair+" diamond="+(repair!=null&&repair.isValidRepairItem(new ItemStack(Items.DIAMOND)))+" calcite="+(repair!=null&&repair.isValidRepairItem(new ItemStack(Items.CALCITE)))+" patch="+stack.getComponentsPatch();}
 void material(ItemStack stack,boolean pickaxe){if(!Boolean.getBoolean("acquisition.expect-fixed"))return;var repair=stack.get(net.minecraft.core.component.DataComponents.REPAIRABLE);check(repair!=null&&repair.isValidRepairItem(new ItemStack(pickaxe?Items.DIAMOND:Items.QUARTZ))&&!repair.isValidRepairItem(new ItemStack(Items.CALCITE)),"wrong server repair material "+detail(stack));}
 void repair(ServerPlayer p)throws Exception{
  p.closeContainer();p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();
  // Damage is a controlled fixture condition applied only after genuine acquisition.
  tool.setDamageValue(3);log("PREPARE REPAIR tool"+detail(tool)+" stone"+detail(stone));p.getInventory().setItem(0,tool);p.getInventory().setItem(1,new ItemStack(Items.DIAMOND,scenario==9?9:1));p.getInventory().setItem(2,stone);
  p.inventoryMenu.broadcastFullState();issue("repair",scenario==9?"overstack9":origins[scenario/3]+"+"+origins[scenario%3]);
 }
 public void onInitialize(){ServerTickEvents.END_SERVER_TICK.register(server->{if(done||server.getPlayerList().getPlayers().isEmpty())return;try{
  var p=server.getPlayerList().getPlayers().getFirst();if(++ticks<40)return;
  if(waiting){if(command.endsWith(":creative"))p.inventoryMenu.broadcastFullState();if(ticks>1200)throw new AssertionError("timeout "+command);if(!Files.exists(control.resolve("ack"))||!Files.readString(control.resolve("ack")).equals(command))return;waiting=false;
   if(phase==1){tool=p.getInventory().getItem(3).copy();check(tool.is(Items.DIAMOND_PICKAXE)&&!tool.isEnchanted(),"tool acquisition wrong "+tool);material(tool,true);log("ACQUIRED tool origin="+origins[scenario/3]+detail(tool));phase=2;}
   else if(phase==3){stone=p.getInventory().getItem(4).copy();check(stone.is(ModItems.WHETSTONE),"stone acquisition wrong "+stone);material(stone,false);log("ACQUIRED whetstone origin="+origins[scenario%3]+detail(stone));phase=enchanted?6:4;}
   else if(phase==7){tool=p.getInventory().getItem(3).copy();check(tool.isEnchanted(),"anvil tool not enchanted");material(tool,true);log("ANVIL TOOL"+detail(tool));phase=8;}
   else if(phase==9){stone=p.getInventory().getItem(4).copy();material(stone,false);check(stone.getOrDefault(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS,net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY).getLevel(p.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY))==1,"anvil stone Efficiency missing");log("ANVIL STONE"+detail(stone));phase=4;}
   else if(phase==5){if(scenario==9){check(p.inventoryMenu.getSlot(0).getItem().isEmpty(),"unexpected oversized material repair output");check(p.inventoryMenu.getSlot(2).getItem().getCount()==9,"rejected diamonds consumed");log("PASS oversized9 rejected under original recipe count limit; no consumption");write("result.txt","PASS 9 genuinely anvil-enchanted acquisition pairs at damage3 plus oversized9 rejection; actual crafting clicks, give commands, native Creative packets and mixed origins; 2x2 repair clicks and consumption\n");done=true;return;}var result=p.getInventory().getItem(3);check(result.is(Items.DIAMOND_PICKAXE)&&result.getDamageValue()==0,"no repair retained "+result);material(result,true);check(result.getEnchantments().getLevel(p.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY))==1,"repair lost Efficiency");check(p.inventoryMenu.getSlot(2).getItem().isEmpty(),"diamond not consumed");check(p.inventoryMenu.getSlot(3).getItem().is(ModItems.WHETSTONE),"stone lost");log("PASS case="+scenario+" tool="+origins[scenario/3]+" stone="+origins[scenario%3]+" repairedDamage="+result.getDamageValue()+" stoneDamage="+p.inventoryMenu.getSlot(3).getItem().getDamageValue()+" diamondConsumed=true");scenario++;phase=0;if(scenario==9){tool=result.copy();stone=p.inventoryMenu.getSlot(3).getItem().copy();phase=4;}}
  }
  if(phase==0){acquire(p,true,origins[scenario/3]);phase=1;}
  else if(phase==2){acquire(p,false,origins[scenario%3]);phase=3;}
  else if(phase==4){repair(p);phase=5;}
  else if(phase==6){enchant(p,true);phase=7;}
  else if(phase==8){enchant(p,false);phase=9;}
 }catch(Throwable failure){done=true;failure.printStackTrace();try{write("result.txt","FAIL server case="+scenario+" phase="+phase+" "+failure);}catch(Exception ignored){}}});}
}

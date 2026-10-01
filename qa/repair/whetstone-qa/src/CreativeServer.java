package qa;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.network.chat.Component;
import me.pajic.simple_smithing_overhaul.items.ModItems;
import java.nio.file.*;
public class CreativeServer implements ModInitializer {
 final Path control=Path.of(System.getProperty("creative.qa.control")); final boolean table=System.getProperty("whetstone.qa.scenario", "inventory").equals("table"); int stage=0,ticks=0,placedTicks=0,takenTicks=0; boolean done=false;
 void write(String name,String text)throws Exception{Files.writeString(control.resolve(name),text);}
 public void onInitialize(){ServerTickEvents.END_SERVER_TICK.register(s->{if(done||s.getPlayerList().getPlayers().isEmpty())return;try{
 var p=s.getPlayerList().getPlayers().get(0);ticks++;
 if(stage==0&&Files.exists(control.resolve("ready"))){
  p.getInventory().clearContent(); var tool=new ItemStack(Items.DIAMOND_PICKAXE);tool.setDamageValue(1000);
  var stone=new ItemStack(ModItems.WHETSTONE);
  for(var key:java.util.List.of(Enchantments.EFFICIENCY,Enchantments.UNBREAKING)){var e=s.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);tool.enchant(e,1);EnchantmentHelper.updateEnchantments(stone,m->m.set(e,1));}
  if(System.getProperty("whetstone.qa.scenario", "inventory").equals("commands")){
   var source=s.createCommandSourceStack();String target=p.getScoreboardName();
   s.getCommands().performPrefixedCommand(source,"give "+target+" minecraft:diamond_pickaxe{damage:780} 1");
   if(!p.getInventory().getItem(0).isEmpty())throw new AssertionError("Unexpected acceptance of old syntax");
   s.getCommands().performPrefixedCommand(source,"give "+target+" minecraft:diamond_pickaxe[minecraft:damage=780,minecraft:enchantments={\"minecraft:efficiency\":1,\"minecraft:unbreaking\":1}] 1");
   s.getCommands().performPrefixedCommand(source,"give "+target+" minecraft:diamond 1");
   s.getCommands().performPrefixedCommand(source,"give "+target+" simple_smithing_overhaul:whetstone[minecraft:stored_enchantments={\"minecraft:efficiency\":1,\"minecraft:unbreaking\":1}] 1");
   if(p.getInventory().getItem(0).getDamageValue()!=780||!p.getInventory().getItem(2).is(ModItems.WHETSTONE))throw new AssertionError("give commands did not produce test inputs");
  }else{p.getInventory().setItem(0,tool);p.getInventory().setItem(1,new ItemStack(Items.DIAMOND));p.getInventory().setItem(2,stone);}
  p.inventoryMenu.broadcastChanges();if(table){var pos=p.blockPosition();p.level().setBlock(pos,net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState(),2);p.openMenu(new SimpleMenuProvider((id,inv,player)->new CraftingMenu(id,inv,ContainerLevelAccess.create(p.level(),pos)),Component.literal("Whetstone QA")));}write("prepared","inventory");stage=1;ticks=0;
 }else if(stage==1&&Files.exists(control.resolve("placed"))&&++placedTicks>20){
  var out=p.containerMenu.getSlot(0).getItem();write("server-output","result="+out+" damage="+out.getDamageValue()+" inputs="+p.containerMenu.getSlot(1).getItem()+","+p.containerMenu.getSlot(2).getItem()+","+p.containerMenu.getSlot(3).getItem());stage=2;ticks=0;
 }else if(stage==2&&Files.exists(control.resolve("taken"))&&++takenTicks>20){
  var out=p.getInventory().getItem(3);if(!out.is(Items.DIAMOND_PICKAXE)||out.getDamageValue()!=(System.getProperty("whetstone.qa.scenario", "inventory").equals("commands")?259:479))throw new AssertionError("repair not taken "+out);
  if(!p.containerMenu.getSlot(2).getItem().isEmpty())throw new AssertionError("diamond not consumed");
  if(!p.containerMenu.getSlot(3).getItem().is(ModItems.WHETSTONE))throw new AssertionError("stone not retained");
  for(var key:java.util.List.of(Enchantments.EFFICIENCY,Enchantments.UNBREAKING)){var e=s.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);if(out.getEnchantments().getLevel(e)!=1)throw new AssertionError("lost enchantment");}
  write("server-result","PASS: repaired diamond pickaxe damage="+out.getDamageValue()+", both enchants retained, diamond consumed, whetstone retained");done=true;
 }
 }catch(Throwable e){e.printStackTrace();try{write("server-result","FAIL: "+e);}catch(Exception ignored){}done=true;}});}
}

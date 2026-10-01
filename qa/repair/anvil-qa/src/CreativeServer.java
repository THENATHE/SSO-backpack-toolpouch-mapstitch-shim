package qa;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import java.nio.file.*;
public class CreativeServer implements ModInitializer {
 final Path control=Path.of(System.getProperty("creative.qa.control"));int stage=0,ticks=0,placedTicks=0,takenTicks=0;boolean done=false;
 void write(String name,String text)throws Exception{Files.writeString(control.resolve(name),text);}
 public void onInitialize(){ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{if(!done)try{write("disconnect","Disconnected at server stage "+stage);}catch(Exception ignored){}});
 ServerTickEvents.END_SERVER_TICK.register(s->{if(done||s.getPlayerList().getPlayers().isEmpty())return;try{
 var p=s.getPlayerList().getPlayers().get(0);ticks++;
 if(stage==0&&Files.exists(control.resolve("ready"))){
  p.setGameMode(System.getProperty("anvil.qa.mode").equals("creative")?GameType.CREATIVE:GameType.SURVIVAL);p.setExperienceLevels(100);
  p.getInventory().clearContent();var tool=new ItemStack(Items.DIAMOND_PICKAXE);tool.setDamageValue(780);
  if(System.getProperty("anvil.qa.input","fresh").equals("repaired")){tool.set(me.pajic.simple_smithing_overhaul.util.ModDataComponents.REPAIR_COUNT,1);}
  if(System.getProperty("anvil.qa.input","fresh").equals("broken")){tool.setDamageValue(tool.getMaxDamage());tool.set(me.pajic.simple_smithing_overhaul.util.ModDataComponents.BROKEN,true);}
  for(var key:java.util.List.of(Enchantments.EFFICIENCY,Enchantments.UNBREAKING)){tool.enchant(s.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key),1);}
  var book=new ItemStack(Items.ENCHANTED_BOOK);EnchantmentHelper.updateEnchantments(book,m->m.set(s.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING),1));
  p.getInventory().setItem(0,tool);p.getInventory().setItem(1,book);p.inventoryMenu.broadcastChanges();
  var pos=p.blockPosition();p.level().setBlock(pos,net.minecraft.world.level.block.Blocks.ANVIL.defaultBlockState(),2);
  p.openMenu(new SimpleMenuProvider((id,inv,player)->new AnvilMenu(id,inv,ContainerLevelAccess.create(p.level(),pos)),Component.literal("Mending QA")));
  write("prepared","anvil");stage=1;ticks=0;
 }else if(stage==1&&Files.exists(control.resolve("placed"))&&++placedTicks>20){
  if(Boolean.getBoolean("anvil.qa.highCost")){p.containerMenu.setData(0,41);p.containerMenu.broadcastChanges();}
  var out=p.containerMenu.getSlot(2).getItem();write("server-output","result="+out+" components="+out.getComponentsPatch()+" cost="+((AnvilMenu)p.containerMenu).getCost());stage=2;ticks=0;
 }else if(stage==2&&Files.exists(control.resolve("taken"))&&++takenTicks>20){
  var out=p.getInventory().getItem(2);var mending=s.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING);
  if(!out.is(Items.DIAMOND_PICKAXE)||out.getEnchantments().getLevel(mending)!=1)throw new AssertionError("Mending not taken: "+out);
  if(!p.containerMenu.getSlot(0).getItem().isEmpty()||!p.containerMenu.getSlot(1).getItem().isEmpty())throw new AssertionError("inputs not consumed");
  if(Boolean.getBoolean("anvil.qa.highCost")&&p.experienceLevel!=59)throw new AssertionError("native high cost not paid: "+p.experienceLevel);
  write("server-result","PASS: took Mending pickaxe, inputs consumed, levels="+p.experienceLevel);done=true;
 }
 }catch(Throwable e){e.printStackTrace();try{write("server-result","FAIL: "+e);}catch(Exception ignored){}done=true;}});}
}

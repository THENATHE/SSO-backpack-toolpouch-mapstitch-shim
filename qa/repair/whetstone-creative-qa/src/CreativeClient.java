package qa;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.core.registries.*;
import net.minecraft.resources.Identifier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import java.nio.file.*;
public class CreativeClient implements ClientModInitializer {
 Path control=Path.of(System.getProperty("creative.qa.control")); int ticks=0,stage=0,wait=0;boolean done=false;
 boolean enchanted=Boolean.getBoolean("whetstone.enchanted");
 void write(String file,String text)throws Exception{Files.writeString(control.resolve(file),text);}
 boolean ack(String text)throws Exception{return Files.exists(control.resolve("ack"))&&Files.readString(control.resolve("ack")).equals(text);}
 public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(c->{if(done||c.player==null||c.level==null)return;try{
 ticks++;wait++;if(wait>500)throw new AssertionError("timeout stage="+stage);
 switch(stage){
 case 0->{if(ticks<80)return;write("request","setup");stage=1;wait=0;}
 case 1->{if(ack("setup")&&wait>20){
 ItemStack stack=new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("simple_smithing_overhaul:whetstone")));
 if(stack.isEmpty())throw new AssertionError("missing native whetstone");
 if(enchanted){var lookup=c.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);var m=new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);m.set(lookup.getOrThrow(Enchantments.EFFICIENCY),1);m.set(lookup.getOrThrow(Enchantments.UNBREAKING),1);stack.set(DataComponents.STORED_ENCHANTMENTS,m.toImmutable());}
 write("client-before-send","id="+BuiltInRegistries.ITEM.getKey(stack.getItem())+" raw="+BuiltInRegistries.ITEM.getId(stack.getItem())+" enchanted="+enchanted+" stored="+stack.get(DataComponents.STORED_ENCHANTMENTS));
 c.gameMode.handleCreativeModeItemAdd(stack,36);write("request","check");stage=2;wait=0;
 }}
 case 2->{if(ack("check")&&wait>20){write("client-result","PASS native Creative whetstone packet enchanted="+enchanted);done=true;}}
 }
 }catch(Throwable t){t.printStackTrace();try{write("client-result","FAIL "+t);}catch(Exception ignored){}done=true;}});}
}

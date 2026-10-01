package qa;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.enchantment.*;
import java.nio.file.*;
public class CreativeServer implements ModInitializer {
 Path control=Path.of(System.getProperty("creative.qa.control"));String last="";int attempts=0,passed=0;boolean enchanted=Boolean.getBoolean("whetstone.enchanted");
 void write(String file,String text)throws Exception{Files.writeString(control.resolve(file),text);}
 void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
 public void onInitialize(){ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{try{write("disconnect","Disconnected after "+passed+" verified Creative SSO cases");}catch(Exception ignored){}});
 ServerTickEvents.END_SERVER_TICK.register(s->{if(s.getPlayerList().getPlayers().isEmpty())return;var p=s.getPlayerList().getPlayers().getFirst();try{
 if(!Files.exists(control.resolve("request")))return;String request=Files.readString(control.resolve("request"));if(request.equals(last))return;
 if(request.equals("setup")){p.closeContainer();p.setPermanentlyInvulnerable(true);p.getInventory().clearContent();p.setGameMode(GameType.CREATIVE);p.inventoryMenu.broadcastFullState();}
 else{var stack=p.getInventory().getItem(0);check(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("simple_smithing_overhaul:whetstone"),"server decoded wrong item "+stack);
 var ench=stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS,ItemEnchantments.EMPTY);var lookup=s.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
 if(enchanted){check(ench.getLevel(lookup.getOrThrow(Enchantments.EFFICIENCY))==1,"Efficiency lost");check(ench.getLevel(lookup.getOrThrow(Enchantments.UNBREAKING))==1,"Unbreaking lost");}else check(ench.isEmpty(),"unexpected enchantments");
 passed++;write("server-result","PASS original whetstone identity and stored enchantments enchanted="+enchanted);p.inventoryMenu.broadcastFullState();}
 write("ack",request);last=request;attempts=0;
 }catch(Throwable t){if(++attempts<100)return;t.printStackTrace();try{write("server-result","FAIL "+t);last=Files.readString(control.resolve("request"));}catch(Exception ignored){}}});}
}

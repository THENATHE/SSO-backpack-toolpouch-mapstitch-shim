package qa;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import java.nio.file.*;
import java.util.Optional;
public class CreativeServer implements ModInitializer {
 final Path control=Path.of(System.getProperty("creative.qa.control"));boolean marked=false;
 public void onInitialize(){ServerPlayConnectionEvents.JOIN.register((h,sender,server)->{
  server.getPlayerList().op(new NameAndId(h.player.getGameProfile()),Optional.of(LevelBasedPermissionSet.OWNER),Optional.of(true));
 });ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{try{Files.writeString(control.resolve("disconnect"),"Disconnected during recipe unlock");}catch(Exception ignored){}});
 ServerTickEvents.END_SERVER_TICK.register(server->{try{if(!marked&&Files.exists(control.resolve("client-result"))){Files.writeString(control.resolve("server-result"),"PASS: client remained connected after all recipe unlock");marked=true;}}catch(Exception e){throw new RuntimeException(e);}});
 }
}

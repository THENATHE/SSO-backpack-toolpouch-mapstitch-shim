package qa;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.Relative;
import java.nio.file.*;
import java.util.*;
public class CreativeServer implements ModInitializer {
    Path control=Path.of(System.getProperty("creative.qa.control")); String last="";int attempts=0,passed=0;
    void write(String file,String text)throws Exception{Files.writeString(control.resolve(file),text);}
    void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
    public void onInitialize(){
      ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{try{write("disconnect","Disconnected after "+passed+" verified cases");}catch(Exception ignored){}});
      ServerTickEvents.END_SERVER_TICK.register(server->{if(server.getPlayerList().getPlayers().isEmpty())return;
       ServerPlayer p=server.getPlayerList().getPlayers().getFirst();
       try{if(!Files.exists(control.resolve("request")))return;String request=Files.readString(control.resolve("request"));if(request.equals(last))return;
        String[] parts=request.split(" ");int n=Integer.parseInt(parts[1]);
        if(parts[0].equals("setup")){
          int dim=n%4,mode=n/4;
          var target=server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(new String[]{"minecraft:overworld","minecraft:the_nether","minecraft:the_end","qa:extra"}[dim])));
          check(target!=null,"dimension missing "+dim);
          for(int x=0;x<3;x++)for(int z=0;z<3;z++){
            target.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.BEDROCK.defaultBlockState());
            for(int y=100;y<104;y++)target.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
          }
          p.closeContainer();p.setPermanentlyInvulnerable(true);p.getInventory().clearContent();
          var identity=new NameAndId(p.getGameProfile());
          if(mode>0)server.getPlayerList().op(identity,Optional.of(LevelBasedPermissionSet.OWNER),Optional.of(true));else server.getPlayerList().deop(identity);
          p.setGameMode(mode<2?GameType.CREATIVE:GameType.SURVIVAL);
          check(p.teleportTo(target,1.5,100,1.5,EnumSet.noneOf(Relative.class),0,0,true),"teleport failed");
          p.inventoryMenu.broadcastFullState();
        }else{
          var stack=p.getInventory().getItem(0);check(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(parts[2]),"server decoded wrong item "+request+" got "+stack);
          if(n<48){
            check(stack.getHoverName().getString().equals("creative-regression-"+n),"name changed "+n);
            check(stack.get(DataComponents.DYED_COLOR)!=null&&stack.get(DataComponents.DYED_COLOR).rgb()==0x123456,"dye lost "+n);
            check(stack.get(DataComponents.CONTAINER)!=null&&stack.get(DataComponents.CONTAINER).itemCopies().anyMatch(s->s.is(Items.DIAMOND)&&s.getCount()==7),"contents lost "+n);
          }
          passed++;write("server-progress","PASS "+passed+" "+request+" dimension="+p.level().dimension().identifier());
          p.inventoryMenu.broadcastFullState();
          if(passed==72)write("server-result","PASS "+passed+" real network cases; six tiers; Creative op/nonop + Survival player /give; four dimensions");
        }
        write("ack",request);last=request;attempts=0;
       }catch(Throwable t){if(++attempts<160)return;t.printStackTrace();try{write("server-result","FAIL "+t);}catch(Exception ignored){}last=Files.exists(control.resolve("request"))?read():"";}
      });
    }
    String read(){try{return Files.readString(control.resolve("request"));}catch(Exception e){return "";}}
}

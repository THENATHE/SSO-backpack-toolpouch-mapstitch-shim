package qa;

import java.nio.file.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;

/** Test-only vanilla-wire simulator: no content mods; registry-sync advertisement deliberately removed. */
public class NetworkClientQa implements ClientModInitializer {
    private int ticks, wait, insertion;
    private String current="", clicked="";
    private boolean failed;
    @Override public void onInitializeClient() {
        var registrySync = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(net.minecraft.resources.Identifier.parse("fabric:registry/sync"));
        net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking.unregisterGlobalReceiver(registrySync);
        if (net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking.getGlobalReceivers().contains(registrySync.id()))
            throw new AssertionError("Registry sync receiver still advertised");
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (failed || client.player == null || client.level == null) return;
            Path control=Path.of(System.getProperty("sso.network.control"));
            try {
                ticks++;
                if (ticks == 1) {
                    if (BuiltInRegistries.ITEM.keySet().stream().anyMatch(id -> !id.getNamespace().equals("minecraft")))
                        throw new AssertionError("Client item registry contains modded items");
                    Files.writeString(control.resolve("client-joined"), "PASS: vanilla-only item registry, no content mods\n");
                }
                Path command=control.resolve("command");
                if (!Files.exists(command)) return;
                String next=Files.readString(command).trim();
                if (!next.equals(current)) {current=next;wait=0;insertion=0;}
                if (current.equals(clicked) || ++wait < 40) return;
                String[] parts=current.split(":");
                int slot=Integer.parseInt(parts[1]);
                var menu=client.player.containerMenu;
                int[] moves=parts[0].equals("smithing")?new int[]{31,0,32,1,33,2}
                        :parts[0].equals("grindstone")?new int[]{30,1}:new int[0];
                if(insertion<moves.length){
                    if(wait%12==0&&menu!=client.player.inventoryMenu){
                        client.gameMode.handleContainerInput(menu.containerId,moves[insertion],0,ContainerInput.PICKUP,client.player);
                        Files.writeString(control.resolve("client-evidence.txt"),current+" input click slot="+moves[insertion]+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
                        insertion++;
                    }
                    return;
                }
                if (menu == client.player.inventoryMenu || !menu.getSlot(slot).hasItem()) {
                    if (wait>200) throw new AssertionError("Missing menu result for "+current);
                    return;
                }
                if (menu instanceof AnvilMenu anvil && (parts[0].equals("free") || parts[0].equals("expensive")))
                    if(anvil.getCost()!=0) throw new AssertionError("Expected zero wire display cost: "+anvil.getCost());
                String evidence=current+" result="+menu.getSlot(slot).getItem()+" menu="+menu.getClass().getSimpleName()+"\n";
                Files.writeString(control.resolve("client-evidence.txt"),evidence,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
                client.gameMode.handleContainerInput(menu.containerId,slot,0,ContainerInput.PICKUP,client.player);
                clicked=current;
                Files.writeString(control.resolve("clicked"),current);
            } catch(Throwable error) {
                failed=true;error.printStackTrace();
                try {Files.writeString(control.resolve("client-failure"),error.toString());}catch(Exception ignored){}
            }
        });
    }
}

package qa;

import com.thenathe.backpackcompat.BackpackCompat;
import eu.pb4.polymer.common.api.PolymerCommonUtils;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import me.pajic.tiered_backpacks.component.ModDataComponents;
import me.pajic.tiered_backpacks.item.ModItems;
import me.pajic.tiered_backpacks.platform.MultiLoaderUtil;
import me.pajic.tiered_backpacks.util.BackpackTier;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.context.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import java.nio.file.*;
import java.util.*;

/** Test-only runtime and real-connection assertions. Never distributed with the shim. */
public class BackpackServerQa implements ModInitializer {
    final Map<UUID,Integer> ages = new HashMap<>();
    final Path control = Path.of(System.getProperty("backpack.qa.control"));
    static final Item[] TIERS = {ModItems.LEATHER_BACKPACK,ModItems.COPPER_BACKPACK,ModItems.IRON_BACKPACK,ModItems.GOLDEN_BACKPACK,ModItems.DIAMOND_BACKPACK,ModItems.NETHERITE_BACKPACK};
    int checks;
    void check(boolean ok,String message) { if(!ok)throw new AssertionError(message);checks++; }
    void write(Path path,String text) { try { Files.createDirectories(path.getParent());Files.writeString(path,text+"\n"); }catch(Exception e){throw new RuntimeException(e);} }
    void write(ServerPlayer p,String file,String text) { write(control.resolve(p.getGameProfile().name()).resolve(file),text); }
    static ItemStack backpack(int tier) {
        ItemStack s = new ItemStack(TIERS[tier]);
        s.set(DataComponents.CUSTOM_NAME,Component.literal("QA tier "+tier));
        s.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND,7),new ItemStack(Items.EMERALD,3))));
        s.set(DataComponents.DYED_COLOR,new DyedItemColor(0x123456));
        s.set(ModDataComponents.STORED_BACKPACK_DYE,new DyedItemColor(0xabcdef));
        return s;
    }
    /** Test-only adaptation of historical synthetic contexts; real connections still use protocol detection. */
    @SuppressWarnings("unchecked")
    static void setSyntheticNative(PacketContext context, boolean value) throws Exception {
        var field=Class.forName("com.thenathe.combinedshim.NativeClients").getDeclaredField("MODULES");
        field.setAccessible(true);
        context.set((PacketContext.Key<Set<String>>)field.get(null),value?Set.of("tiered_backpacks"):Set.of());
    }
    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                PacketContext context=((PacketContextProvider)(Object)new Connection(PacketFlow.SERVERBOUND)).getPacketContext();
                var lookup=server.registryAccess();
                for(int tier=0;tier<TIERS.length;tier++) {
                    ItemStack s=backpack(tier);
                    var saved=ItemStack.CODEC.encodeStart(lookup.createSerializationContext(NbtOps.INSTANCE),s).getOrThrow();
                    for(String state:List.of("none","accepted","declined")) {
                        PolymerCommonUtils.setHasResourcePack(context,PolymerResourcePackUtils.getMainUuid(),state.equals("accepted"));
                        setSyntheticNative(context,false);
                        var wire=PolymerItemUtils.getPolymerItemStack(s,TooltipFlag.NORMAL,context,lookup);
                        check(wire.is(Items.LEATHER),"fallback leather tier="+tier+" pack="+state);
                        check(wire.get(ModDataComponents.BACKPACK_TIER)==null,"native tier removed from vanilla wire");
                        check(wire.get(ModDataComponents.STORED_BACKPACK_DYE)==null,"native dye removed from vanilla wire");
                        check(wire.get(DataComponents.CONTAINER)==null,"contents hidden from vanilla wire");
                        check(wire.get(DataComponents.EQUIPPABLE)==null,"equipment interaction removed from vanilla wire");
                        check(wire.get(DataComponents.LORE).lines().stream().anyMatch(c->c.getString().contains("need the Tiered Backpacks mod")),"mod-required notice present");
                        var restored=PolymerItemUtils.getRealItemStack(wire,context,lookup);
                        check(saved.equals(ItemStack.CODEC.encodeStart(lookup.createSerializationContext(NbtOps.INSTANCE),restored).getOrThrow()),"complete fallback roundtrip preserves contents and components");
                        setSyntheticNative(context,true);
                        check(PolymerItemUtils.getPolymerItemStack(s,TooltipFlag.NORMAL,context,lookup)==s,"native original item identity tier="+tier);
                        check(saved.equals(ItemStack.CODEC.encodeStart(lookup.createSerializationContext(NbtOps.INSTANCE),s).getOrThrow()),"source unchanged");
                    }
                }
                write(control.resolve("runtime-result.txt"),"PASS: "+checks+" assertions; all six tiers, pack absent/accepted/declined, native identity, vanilla fallback and complete serialized roundtrips");
                System.out.println("BACKPACK_RUNTIME_QA_PASS "+checks);
            }catch(Throwable t){t.printStackTrace();write(control.resolve("runtime-result.txt"),"FAIL: "+t);}
        });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->ages.put(handler.player.getUUID(),0));
        ServerTickEvents.END_SERVER_TICK.register(server->{for(ServerPlayer p:server.getPlayerList().getPlayers()) {
            int age=ages.merge(p.getUUID(),1,Integer::sum);
            if(age==100)try{seed(p);}catch(Throwable t){t.printStackTrace();write(p,"server-result.txt","FAIL: "+t);}
            if(age==300&&!p.getGameProfile().name().equals("PackVanillaQA"))try{
                var field=BackpackCompat.class.getDeclaredField("NEXT_NOTICE");field.setAccessible(true);
                check(!((Map<?,?>)field.get(null)).containsKey(p.getUUID()),"native player receives no fallback notice");
                write(p,"native-notice-result.txt","PASS: native player absent from fallback notice schedule");
            }catch(Throwable t){t.printStackTrace();write(p,"native-notice-result.txt","FAIL: "+t);}
            if(age==800)write(p,"survived.txt","PASS: connected through 40 seconds of gameplay ticks");
        }});
    }
    void seed(ServerPlayer p) {
        boolean vanilla=p.getGameProfile().name().equals("PackVanillaQA");
        var context=((PacketContextProvider)p.connection).getPacketContext();
        check(BackpackCompat.nativeClient(context)!=vanilla,"correct real connection classification");
        p.getInventory().clearContent();
        for(int i=0;i<TIERS.length;i++)p.getInventory().setItem(i,backpack(i));
        ItemStack armor=new ItemStack(Items.IRON_CHESTPLATE);
        armor.set(ModDataComponents.BACKPACK_TIER,BackpackTier.IRON);
        armor.set(ModDataComponents.STORED_BACKPACK_DYE,new DyedItemColor(0xabcdef));
        armor.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(new ItemStack(Items.GOLD_INGOT,4))));
        p.getInventory().setItem(6,armor);
        var atlas=BuiltInRegistries.ITEM.getValue(Identifier.parse("mapstitch:atlas"));
        if(atlas!=null&&atlas!=Items.AIR) {
            var atlasStack=new ItemStack(atlas);
            var map=MapItem.create(p.level(),p.getBlockX(),p.getBlockZ(),(byte)0,true,false);
            var contents=BundleContents.EMPTY.asMutable();contents.tryInsert(map);
            atlasStack.set(DataComponents.BUNDLE_CONTENTS,contents.toImmutable());
            p.getInventory().setItem(7,atlasStack);
            write(p,"atlas-ready.txt","ready");
        }
        p.getInventory().setSelectedSlot(0);
        p.inventoryMenu.broadcastFullState();
        if(vanilla) {
            check(BackpackCompat.needsNotice(p),"held backpack requires notice");
            p.getInventory().setSelectedSlot(8);
            check(!BackpackCompat.needsNotice(p),"inventory-only backpacks need no notice");
            p.setItemSlot(EquipmentSlot.CHEST,backpack(0));
            check(BackpackCompat.needsNotice(p),"equipped backpack requires notice");
            p.setItemSlot(EquipmentSlot.CHEST,armor.copy());
            check(BackpackCompat.needsNotice(p),"equipped armor with attached backpack requires notice");
            p.setItemSlot(EquipmentSlot.CHEST,ItemStack.EMPTY);
            p.setItemSlot(EquipmentSlot.OFFHAND,backpack(0));
            check(BackpackCompat.needsNotice(p),"offhand backpack requires notice");
            p.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);
            p.getInventory().setSelectedSlot(0);
            var saved=p.getMainHandItem().copy();
            p.getMainHandItem().getItem().use(p.level(),p,InteractionHand.MAIN_HAND);
            check(p.containerMenu==p.inventoryMenu,"vanilla item use cannot open native menu");
            MultiLoaderUtil.INSTANCE.openBackpackScreen(p,p.getMainHandItem());
            check(p.containerMenu==p.inventoryMenu,"vanilla direct screen request cannot open native menu");
            check(ItemStack.matches(saved,p.getMainHandItem()),"vanilla attempted interaction preserves original contents/components");
            write(p,"server-result.txt","PASS: actual vanilla connection, six tiers and attached armor transmitted; held/offhand/equipped/attached-armor notice detection; item-use and screen-open denied without changing original contents");
        }else write(p,"server-result.txt","PASS: actual native connection classified; all six native tiers and attached armor transmitted");
        write(p,"seed-ready.txt","ready");
    }
}

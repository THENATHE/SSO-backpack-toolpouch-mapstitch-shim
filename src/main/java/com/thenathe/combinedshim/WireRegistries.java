package com.thenathe.combinedshim;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Per-connection dense IDs; Polymer-only items must not leave gaps in Fabric's registry map. */
public final class WireRegistries {
    private static final Set<Identifier> REGISTRIES = Set.of(Identifier.parse("minecraft:item"),
            Identifier.parse("minecraft:data_component_type"), Identifier.parse("minecraft:recipe_serializer"), Identifier.parse("minecraft:menu"));
    private static final PacketContext.Key<Map<Identifier, Ids>> KEY = PacketContext.key(
            Identifier.parse("combined_polymer_shim:wire_ids"));
    private static final ScopedValue<Boolean> WRITE = ScopedValue.newInstance();
    private static final ScopedValue<Boolean> READ = ScopedValue.newInstance();

    private WireRegistries() {}

    public static boolean hasMapping(PacketContext context) {
        var map = context == null ? null : context.get(KEY);
        return map != null && !map.isEmpty();
    }

    public static boolean handles(Identifier id) { return REGISTRIES.contains(id); }
    public static boolean writing() { return WRITE.orElse(false); }
    public static boolean reading() { return READ.orElse(false); }
    public static void encode(boolean tags, Runnable operation) {
        ScopedValue.where(WRITE, true).where(READ, tags).run(operation);
    }
    public static void decode(Runnable operation) {
        ScopedValue.where(WRITE, false).where(READ, true).run(operation);
    }

    public static Map<Identifier, Object2IntMap<Identifier>> prepare(
            Map<Identifier, Object2IntMap<Identifier>> original, PacketContext context) {
        if (original == null) { context.set(KEY, Map.of()); return null; }
        var result = new LinkedHashMap<Identifier, Object2IntMap<Identifier>>();
        var mappings = new LinkedHashMap<Identifier, Ids>();
        original.forEach((key, entries) -> {
            Registry<?> registry = BuiltInRegistries.REGISTRY.getValue(key);
            if (!handles(key) || registry == null) {
                result.put(key, entries);
                return;
            }
            var wire = new Object2IntLinkedOpenHashMap<Identifier>();
            var ids = new Ids();
            entries.keySet().stream().sorted(Comparator.comparingInt(entries::getInt)).forEach(id -> {
                int next = wire.size();
                int raw = rawId(registry, id);
                wire.put(id, next);
                ids.out.put(raw, next);
                ids.in.put(next, raw);
            });
            result.put(key, wire);
            mappings.put(key, ids);
        });
        context.set(KEY, Map.copyOf(mappings));
        return result;
    }

    private static <T> int rawId(Registry<T> registry, Identifier id) {
        return registry.getId(registry.getValue(id));
    }

    public static int toWire(Identifier registry, int raw, PacketContext context) {
        Ids ids = mapping(registry, context);
        return ids == null ? raw : ids.out.get(raw);
    }
    public static int toServer(Identifier registry, int wire, PacketContext context) {
        Ids ids = mapping(registry, context);
        return ids == null ? wire : ids.in.get(wire);
    }
    /** Test visibility using server IDs even when called from inside a packet codec. */
    public static <T> boolean isVisible(Registry<T> registry, T value, PacketContext context) {
        var ids = mapping(registry.key().identifier(), context);
        return ids == null || ScopedValue.where(WRITE, false)
                .call(() -> ids.out.containsKey(registry.getId(value)));
    }
    private static Ids mapping(Identifier registry, PacketContext context) {
        var map = context == null ? null : context.get(KEY);
        return map == null ? null : map.get(registry);
    }
    private static final class Ids {
        final Int2IntOpenHashMap out = new Int2IntOpenHashMap();
        final Int2IntOpenHashMap in = new Int2IntOpenHashMap();
        Ids() { out.defaultReturnValue(-1); in.defaultReturnValue(-1); }
    }
}

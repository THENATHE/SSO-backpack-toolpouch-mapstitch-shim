package com.thenathe.mapstitchcompat;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import me.pajic.mapstitch.component.ModDataComponents;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Reuses the installed upstream artwork, without exposing mod-only model predicates. */
public final class AtlasResources {
    private static final String NAMESPACE = "mapstitch_polymer_compat";
    private static final String[] VARIANTS = {
            "atlas_empty", "atlas_full_0", "atlas_full_1", "atlas_full_2", "atlas_full_3"
    };

    private AtlasResources() {}

    public static void initialize() {
        // Copy only the five sprites. MapStitch's original item definition needs a
        // modded component codec that an unmodified client cannot load.
        PolymerResourcePackUtils.addModAssetsWithoutCopy("mapstitch");
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(AtlasResources::build);
    }

    private static void build(ResourcePackBuilder builder) {
        builder.addModToCredits("mapstitch");
        // The released upstream JAR omits its source license; retain that notice
        // explicitly when its artwork is served to clients in a generated pack.
        try (var license = AtlasResources.class.getResourceAsStream("/licenses/mapstitch-LICENSE.txt")) {
            if (license == null) throw new IllegalStateException("Missing bundled MapStitch license notice");
            builder.addData("licenses/mapstitch/LICENSE", license.readAllBytes());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        for (var variant : VARIANTS) {
            var texture = builder.getDataOrSource("assets/mapstitch/textures/item/" + variant + ".png");
            if (texture == null) throw new IllegalStateException("Missing MapStitch artwork: " + variant);
            builder.addData("assets/" + NAMESPACE + "/textures/item/" + variant + ".png", texture);
            builder.addStringData("assets/" + NAMESPACE + "/models/item/" + variant + ".json",
                    "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\""
                            + NAMESPACE + ":item/" + variant + "\"}}");
            builder.addStringData("assets/" + NAMESPACE + "/items/" + variant + ".json",
                    "{\"model\":{\"type\":\"minecraft:model\",\"model\":\""
                            + NAMESPACE + ":item/" + variant + "\"}}");
        }
    }

    public static Identifier model(ItemStack stack, PacketContext context) {
        if (MapstitchCompat.nativeClient(context)) {
            return stack.get(DataComponents.ITEM_MODEL);
        }
        if (context == null || !PolymerResourcePackUtils.hasMainPack(context)) {
            return Identifier.withDefaultNamespace("book");
        }
        int fullness = stack.getOrDefault(ModDataComponents.ATLAS_FULLNESS, 0);
        // Match the original select model's fallback for values outside 0..4.
        if (fullness < 0 || fullness >= VARIANTS.length) fullness = 0;
        return Identifier.fromNamespaceAndPath(NAMESPACE, VARIANTS[fullness]);
    }
}

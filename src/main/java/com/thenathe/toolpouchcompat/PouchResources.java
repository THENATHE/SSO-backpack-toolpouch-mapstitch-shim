package com.thenathe.toolpouchcompat;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.io.IOException;
import java.io.UncheckedIOException;

public final class PouchResources {
    private PouchResources() {}
    public static void initialize() {
        // Tool Pouch's item models use only vanilla dye tint codecs.
        PolymerResourcePackUtils.addModAssets("toolpouch");
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(builder -> {
            builder.addModToCredits("toolpouch");
            try (var in = PouchResources.class.getResourceAsStream("/licenses/toolpouch-LICENSE.txt")) {
                if (in == null) throw new IllegalStateException("Missing Tool Pouch license");
                builder.addData("licenses/toolpouch/LICENSE", in.readAllBytes());
            } catch (IOException e) { throw new UncheckedIOException(e); }
        });
    }
    public static Identifier model(ItemStack stack, PacketContext context) {
        if (ToolpouchCompat.nativeClient(context)
                || context != null && PolymerResourcePackUtils.hasMainPack(context)) {
            return stack.get(DataComponents.ITEM_MODEL);
        }
        return Identifier.withDefaultNamespace("leather");
    }
}

package com.thenathe.ssopolymer;

import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/** Keep resource-pack translations usable, while providing readable pack-free names. */
public final class TextFallbacks {
    private static final Map<String, String> ENGLISH = new HashMap<>();
    private TextFallbacks() {}

    public static void initialize() {
        var path = FabricLoader.getInstance().getModContainer("simple_smithing_overhaul").orElseThrow()
                .findPath("assets/simple_smithing_overhaul/lang/en_us.json").orElseThrow();
        try (var reader = Files.newBufferedReader(path)) {
            JsonParser.parseReader(reader).getAsJsonObject().asMap()
                    .forEach((key, value) -> ENGLISH.put(key, value.getAsString()));
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }

    public static Component withFallback(Component input) {
        MutableComponent output;
        if (input.getContents() instanceof TranslatableContents translated) {
            var args = translated.getArgs().clone();
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Component component) args[i] = withFallback(component);
            }
            output = Component.translatableWithFallback(translated.getKey(),
                    ENGLISH.getOrDefault(translated.getKey(), translated.getFallback()), args);
        } else output = input.plainCopy();
        output.setStyle(input.getStyle());
        input.getSiblings().forEach(sibling -> output.append(withFallback(sibling)));
        return output;
    }
}

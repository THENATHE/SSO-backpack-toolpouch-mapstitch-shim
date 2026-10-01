package com.thenathe.ssopolymer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Converts only the original mod's legacy loot pools, leaving other mods' data alone. */
public final class LootCompatibility {
    private LootCompatibility() {}

    public static JsonElement preparePool(JsonElement source, boolean configuredPercentage) {
        JsonObject pool = source.deepCopy().getAsJsonObject();
        JsonElement conditions = pool.remove("conditions");
        if (conditions != null) {
            for (JsonElement element : conditions.getAsJsonArray()) {
                JsonObject condition = element.getAsJsonObject();
                condition.add("type", condition.remove("condition"));
                if (configuredPercentage && "minecraft:random_chance".equals(condition.get("type").getAsString())) {
                    condition.addProperty("chance", Math.clamp(condition.get("chance").getAsDouble() / 100.0, 0.0, 1.0));
                }
            }
            if (conditions.getAsJsonArray().size() == 1) {
                pool.add("condition", conditions.getAsJsonArray().get(0));
            } else {
                JsonObject combined = new JsonObject();
                combined.addProperty("type", "minecraft:all_of");
                combined.add("terms", conditions);
                pool.add("condition", combined);
            }
        }
        for (JsonElement element : pool.getAsJsonArray("entries")) {
            JsonObject entry = element.getAsJsonObject();
            JsonElement functions = entry.remove("functions");
            if (functions == null) continue;
            for (JsonElement function : functions.getAsJsonArray()) {
                JsonObject modifier = function.getAsJsonObject();
                modifier.add("type", modifier.remove("function"));
            }
            entry.add("modifier", functions);
        }
        return pool;
    }
}

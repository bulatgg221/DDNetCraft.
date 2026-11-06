package dev.aibuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Set;

/**
 * Enforces actual AI-created placement instructions in freeform mode.
 * Fixed house/castle blueprints belong exclusively to explicit /ai demo commands.
 */
public final class GeneratedPlanGuard {
    private GeneratedPlanGuard() {}

    private static final Set<String> PREBUILT = Set.of(
        "house", "cottage", "detailed_house", "manor", "villa", "farmhouse",
        "chalet", "longhouse", "castle", "fortress", "citadel");

    public static String requireOriginalGeometry(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray operations = root.getAsJsonArray("operations");
        if (operations == null || operations.isEmpty())
            throw new IllegalArgumentException("ИИ не прислал строительных операций.");

        for (JsonElement element : operations) {
            if (!element.isJsonObject())
                throw new IllegalArgumentException("ИИ прислал некорректную операцию.");
            JsonObject op = element.getAsJsonObject();
            if (!op.has("type") || !op.get("type").isJsonPrimitive())
                throw new IllegalArgumentException("Операция ИИ не содержит тип.");
            String type = op.get("type").getAsString().strip().toLowerCase(java.util.Locale.ROOT);
            if (PREBUILT.contains(type)) {
                throw new IllegalArgumentException("ИИ вернул готовый шаблон " + type
                    + " вместо индивидуального чертежа. Нужны реальные операции с координатами.");
            }
        }
        return json;
    }

    public static boolean usesPrebuilt(String type) {
        return type != null && PREBUILT.contains(type.strip().toLowerCase(java.util.Locale.ROOT));
    }
}

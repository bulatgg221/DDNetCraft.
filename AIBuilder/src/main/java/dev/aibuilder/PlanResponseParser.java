package dev.aibuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.regex.Pattern;
import java.util.Set;
import java.util.Map;

/**
 * Normalizes common LLM JSON formatting mistakes. Never executes model text,
 * invents blocks, discards individual operations, or silently builds a partial plan.
 * A corrupt/truncated plan still throws, allowing the planner to request a fresh plan.
 */
public final class PlanResponseParser {
    private PlanResponseParser() {}

    private static final Set<String> NUMERIC_FIELDS = Set.of(
        "x","y","z","x2","y2","z2","w","h","d","r");
    private static final Set<String> STRING_FIELDS = Set.of(
        "type","stage","block","facing","hinge","half","slab_type","axis",
        "shape","leg","leaves","name");
    private static final Pattern MISSING_COLON = Pattern.compile(
        "(\\\"(?:name|operations|type|stage|block|x|y|z|x2|y2|z2|w|h|d|r|facing|hinge|half|slab_type|axis|open|shape|waterlogged|leg|leaves)\\\")\\s+(?=\\\"|\\{|\\[|-?[0-9]|true\\b|false\\b|null\\b)");

    public static String normalize(String response) {
        if (response == null || response.isBlank() || response.length() > 250_000)
            throw new IllegalArgumentException("Пустой или слишком длинный JSON-план.");
        String json = extractObject(response.strip());
        try {
            return validate(json).toString();
        } catch (RuntimeException ignored) {
            // Retry with non-destructive, strictly syntactic normalization only.
        }
        String repaired = removeTrailingCommas(json);
        repaired = addMissingObjectCommas(repaired);
        repaired = MISSING_COLON.matcher(repaired).replaceAll("$1:");
        try {
            return validate(repaired).toString();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(
                "Нейросеть прислала повреждённый JSON-план. Попробуй упростить запрос.");
        }
    }

    private static JsonObject validate(String json) {
        JsonElement value = JsonParser.parseString(json);
        if (!value.isJsonObject()) throw new IllegalArgumentException("Нужен JSON-объект.");
        JsonObject root = value.getAsJsonObject();
        JsonArray ops = root.getAsJsonArray("operations");
        if (ops == null || ops.isEmpty() || ops.size() > 120)
            throw new IllegalArgumentException("Некорректный массив операций.");
        for (JsonElement operation : ops) {
            if (!operation.isJsonObject()) throw new IllegalArgumentException("Операция не является объектом.");
            JsonObject object = operation.getAsJsonObject();
            if (!object.has("type") || !object.get("type").isJsonPrimitive()
                || !object.getAsJsonPrimitive("type").isString())
                throw new IllegalArgumentException("Не указан тип операции.");
            for (Map.Entry<String,JsonElement> entry : object.entrySet()) {
                String name = entry.getKey();
                JsonElement fieldValue = entry.getValue();
                if (NUMERIC_FIELDS.contains(name)) {
                    if (!fieldValue.isJsonPrimitive() || !fieldValue.getAsJsonPrimitive().isNumber())
                        throw new IllegalArgumentException("Координата " + name + " должна быть числом.");
                    double numeric = fieldValue.getAsDouble();
                    if (!Double.isFinite(numeric) || numeric != Math.rint(numeric))
                        throw new IllegalArgumentException("Координата " + name + " должна быть целым числом.");
                } else if (STRING_FIELDS.contains(name)) {
                    if (!fieldValue.isJsonPrimitive() || !fieldValue.getAsJsonPrimitive().isString())
                        throw new IllegalArgumentException("Поле " + name + " должно быть строкой.");
                }
            }
        }
        return root;
    }

    /** Removes markdown fences or harmless text surrounding one fully balanced object. */
    private static String extractObject(String source) {
        int first = source.indexOf('{');
        if (first < 0) throw new IllegalArgumentException("JSON-объект не найден.");
        int depth = 0;
        boolean inString = false, escaped = false;
        for (int i = first; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if (ch == '"') inString = false;
            } else {
                if (ch == '"') inString = true;
                else if (ch == '{') depth++;
                else if (ch == '}' && --depth == 0)
                    return source.substring(first, i + 1);
            }
        }
        throw new IllegalArgumentException("Ответ ИИ обрезан: JSON не закрыт.");
    }

    /** Only remove commas immediately before JSON closing brackets, outside strings. */
    private static String removeTrailingCommas(String source) {
        StringBuilder result = new StringBuilder(source.length());
        boolean inString = false, escaped = false;
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (inString) {
                result.append(ch);
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if (ch == '"') inString = false;
            } else if (ch == '"') {
                inString = true;
                result.append(ch);
            } else if (ch == ',') {
                int next = i + 1;
                while (next < source.length() && Character.isWhitespace(source.charAt(next))) next++;
                if (next >= source.length() || (source.charAt(next) != '}' && source.charAt(next) != ']'))
                    result.append(ch);
            } else result.append(ch);
        }
        return result.toString();
    }

    /** Two adjacent operation objects with no comma are unambiguous in JSON arrays. */
    private static String addMissingObjectCommas(String source) {
        StringBuilder result = new StringBuilder(source.length());
        boolean inString = false, escaped = false;
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (inString) {
                result.append(ch);
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if (ch == '"') inString = false;
            } else if (ch == '"') {
                inString = true;
                result.append(ch);
            } else {
                if (ch == '{') {
                    int previous = i - 1;
                    while (previous >= 0 && Character.isWhitespace(source.charAt(previous))) previous--;
                    if (previous >= 0 && source.charAt(previous) == '}') result.append(',');
                }
                result.append(ch);
            }
        }
        return result.toString();
    }
}

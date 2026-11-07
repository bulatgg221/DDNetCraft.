package dev.aibuilder;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Read the original textual building requirements when a named blueprint is
 * reloaded, so saving/loading a plan cannot accidentally turn off checks that
 * depend on the requested castle/towers/gate.
 */
public final class SavedBlueprintBrief {
    private SavedBlueprintBrief(){}

    public static String extract(String json){
        JsonObject root=JsonParser.parseString(json).getAsJsonObject();
        if(root.has("sourcePrompt")&&root.get("sourcePrompt").isJsonPrimitive()
            &&root.getAsJsonPrimitive("sourcePrompt").isString()) {
            String prompt=root.get("sourcePrompt").getAsString().strip();
            if(!prompt.isBlank())
                return prompt.substring(0,Math.min(prompt.length(),PromptLimits.MAX_CHARS));
        }
        if(root.has("name")&&root.get("name").isJsonPrimitive() &&
            root.getAsJsonPrimitive("name").isString())
            return root.get("name").getAsString();
        return "";
    }
}

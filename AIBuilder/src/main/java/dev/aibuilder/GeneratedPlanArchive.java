package dev.aibuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Stores only the most recent model-generated placement plan, never an API key. */
public final class GeneratedPlanArchive {
    private GeneratedPlanArchive() {}

    /**
     * The original user brief is archived with the blueprint, so a saved
     * project cannot bypass castle integrity checks just because the player
     * reloads it later. No API key or provider credentials are included.
     */
    public static void trySaveWithPrompt(String plan,String userPrompt) {
        if(userPrompt==null || userPrompt.isBlank()){
            trySave(plan);
            return;
        }
        try {
            com.google.gson.JsonObject root=
                com.google.gson.JsonParser.parseString(plan).getAsJsonObject();
            // User text has already been capped by F7's PromptLimits.
            root.addProperty("sourcePrompt",userPrompt.substring(
                0,Math.min(userPrompt.length(),PromptLimits.MAX_CHARS)));
            trySave(root.toString());
        }catch(RuntimeException badPlan){
            AIBuilderMod.LOG.warn("Cannot archive prompt metadata: {}",
                badPlan.getClass().getSimpleName());
            trySave(plan);
        }
    }

    public static void trySave(String plan) {
        try {
            Files.createDirectories(BuilderConfig.DIRECTORY);
            Path output = BuilderConfig.DIRECTORY.resolve("last-generated-plan.json");
            Files.writeString(output, plan + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        } catch (Exception e) {
            AIBuilderMod.LOG.warn("Could not save generated plan for inspection: {}",e.toString());
        }
    }
}

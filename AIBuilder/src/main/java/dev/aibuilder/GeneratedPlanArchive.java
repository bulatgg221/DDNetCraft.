package dev.aibuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Stores only the most recent model-generated placement plan, never an API key. */
public final class GeneratedPlanArchive {
    private GeneratedPlanArchive() {}

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

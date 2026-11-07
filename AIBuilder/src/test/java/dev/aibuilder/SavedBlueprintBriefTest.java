package dev.aibuilder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SavedBlueprintBriefTest {
    @Test void savedCastleReloadPreservesExactQualityRequirements(){
        String json="""
            {"name":"Royal castle","sourcePrompt":
             "Построй готический замок с четырьмя мощными угловыми башнями и главными воротами",
             "operations":[{"type":"box","x":6,"y":0,"z":6,"w":5,"h":2,"d":5}]}
            """;
        String prompt=SavedBlueprintBrief.extract(json);
        assertTrue(CastleStructureAudit.isCastle(prompt));
        assertEquals(4,RequestedFeatures.towers(prompt));
        assertTrue(prompt.contains("воротами"));
    }

    @Test void legacyPlansFallbackToNameWithoutMakingUpUserDetails(){
        assertEquals("Gothic Castle",SavedBlueprintBrief.extract(
            "{\"name\":\"Gothic Castle\",\"operations\":[]}"));
        assertEquals("",SavedBlueprintBrief.extract(
            "{\"operations\":[]}"));
    }

    @Test void savedPromptIsTrimmedToPublicEditorLimit(){
        String longPrompt="Z".repeat(PromptLimits.MAX_CHARS+100);
        String json="{\"sourcePrompt\":\""+longPrompt+"\"}";
        assertEquals(PromptLimits.MAX_CHARS,
            SavedBlueprintBrief.extract(json).length());
    }
}

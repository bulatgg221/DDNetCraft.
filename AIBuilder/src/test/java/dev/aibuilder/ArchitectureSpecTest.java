package dev.aibuilder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ArchitectureSpecTest {
    private static final String VALID = """
        {"architecture":{"regions":[
          {"id":"gate","kind":"entrance","bounds":{
            "x":6,"y":1,"z":6,"width":4,"height":5,"depth":4},
            "connectedTo":["hall"]},
          {"id":"hall","kind":"hall","bounds":{
            "x":10,"y":1,"z":6,"width":12,"height":7,"depth":10},
            "connectedTo":["gate","north_tower"]},
          {"id":"north_tower","kind":"tower","bounds":{
            "x":19,"y":1,"z":14,"width":7,"height":20,"depth":7},
            "connectedTo":["hall"]}
        ]},"operations":[]}
        """;

    @Test void parsesGenuineSemanticAreasWithoutGeneratingPrefabGeometry(){
        var result=ArchitectureSpec.inspect(VALID);
        assertEquals(3,result.regions().size());
        assertEquals(1,result.declaredTowers());
        assertTrue(result.warnings().isEmpty(),result.warnings().toString());
        assertTrue(result.regions().get(0).near(result.regions().get(1)));
        assertTrue(result.regions().get(1).near(result.regions().get(2)));
    }

    @Test void unconnectedRoomsAreReportedWithoutDestroyingTheVoxelPlan(){
        String json=VALID.replace("\"connectedTo\":[\"hall\"]",
            "\"connectedTo\":[]")
            .replace("\"connectedTo\":[\"gate\",\"north_tower\"]",
                "\"connectedTo\":[\"north_tower\"]");
        var result=ArchitectureSpec.inspect(json);
        assertEquals(3,result.regions().size());
        assertTrue(result.warnings().stream().anyMatch(s->s.contains("не связаны")),
            result.warnings().toString());
    }

    @Test void claimsOfImpossibleFarAwayConnectionsAreFlagged(){
        String json=VALID.replace("\"x\":19", "\"x\":80");
        var result=ArchitectureSpec.inspect(json);
        assertTrue(result.warnings().stream().anyMatch(s->s.contains("далеко")),
            result.warnings().toString());
    }

    @Test void unknownTargetRoomAndDuplicateNamesDoNotCrashCompiler(){
        String missing=VALID.replace("\"north_tower\"", "\"missing_tower\"");
        var result=ArchitectureSpec.inspect(missing);
        assertTrue(result.warnings().stream().anyMatch(s->s.contains("несуществующую")),
            result.warnings().toString());
        var absent=ArchitectureSpec.inspect("{\"operations\":[]}");
        assertTrue(absent.regions().isEmpty());
        assertFalse(absent.warnings().isEmpty());
    }

    @Test void allRegionCoordinatesAreExplicitAndBounded(){
        String malformed=VALID.replace("\"width\":12","\"width\":0");
        var result=ArchitectureSpec.inspect(malformed);
        assertEquals(2,result.regions().size());
        assertTrue(result.warnings().stream().anyMatch(s->s.contains("Некорректная")));
    }

    @Test void boxesHaveExclusiveMaximumAndDetectMembershipCorrectly(){
        var r=ArchitectureSpec.inspect(VALID).regions().get(0);
        assertTrue(r.contains(6,1,6));
        assertTrue(r.contains(9,5,9));
        assertFalse(r.contains(10,1,6));
        assertFalse(r.contains(6,6,6));
    }
}

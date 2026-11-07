package dev.aibuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class ArtworkModeTest {
    private static final List<String> COLORS=List.of(
        "black_concrete","gray_concrete","light_gray_concrete","white_concrete",
        "brown_terracotta","orange_terracotta","red_terracotta","yellow_terracotta",
        "pink_concrete","blue_concrete","cyan_concrete","purple_concrete");

    private static String makeImage(int width,int height,int colors) {
        JsonObject root=new JsonObject();
        root.addProperty("name","LLM-composed artwork for test");
        JsonObject op=new JsonObject();
        op.addProperty("type","pixel_art");
        op.addProperty("x",32);op.addProperty("y",0);op.addProperty("z",8);
        op.addProperty("facing","north");
        JsonObject palette=new JsonObject();
        for(int i=0;i<colors;i++)
            palette.addProperty(String.valueOf((char)('A'+i)),COLORS.get(i));
        op.add("palette",palette);
        JsonArray rows=new JsonArray();
        for(int r=0;r<height;r++) {
            StringBuilder row=new StringBuilder();
            for(int c=0;c<width;c++)
                row.append((char)('A'+((c/2+r/3)%colors)));
            rows.add(row.toString());
        }
        op.add("rows",rows);
        JsonArray operations=new JsonArray();operations.add(op);
        root.add("operations",operations);
        return root.toString();
    }

    @Test void portraitRequestsAreIdentifiedButCastlesAreNot() {
        assertTrue(ArtworkMode.isPortrait("нарисуй красивую картину человека"));
        assertTrue(ArtworkMode.isPortrait("построй портрет человека"));
        assertTrue(ArtworkMode.isPortrait("make a pixel art face"));
        assertTrue(ArtworkMode.isArtwork("сделай мозаику солнца"));
        assertFalse(ArtworkMode.isPortrait("нарисуй дракона"));
        assertFalse(ArtworkMode.isArtwork("построй дом с башнями"));
        assertFalse(ArtworkMode.isArtwork("построй замок и двор"));
    }

    @Test void refusesSameCrudeFiveColorFaceFromScreenshot() {
        String crude=makeImage(32,32,5);
        IllegalArgumentException err=assertThrows(IllegalArgumentException.class,
            ()->ArtworkMode.requireArtworkGeometry(
                "нарисуй картину человека",crude,MaterialCatalog.allowedIds()));
        assertTrue(err.getMessage().contains("цветов"),err.getMessage());
    }

    @Test void refusesBlankSmallPortraitEvenWithValidSchema() {
        String tiny=makeImage(12,12,12);
        assertThrows(IllegalArgumentException.class,
            ()->ArtworkMode.requireArtworkGeometry(
                "портрет человека",tiny,MaterialCatalog.allowedIds()));
    }

    @Test void sufficientlyDetailedOriginalMatrixPassesWithoutTemplates() {
        String detailed=makeImage(32,32,12);
        assertEquals(detailed,ArtworkMode.requireArtworkGeometry(
            "нарисуй картину человека",detailed,MaterialCatalog.allowedIds()));
        assertEquals(detailed,GeneratedPlanGuard.requireOriginalGeometry(detailed));
    }

    @Test void pictureOnlyMadeOfBigRectanglesMustBeReplanned() {
        String bad="{\"name\":\"crude face\",\"operations\":["
            +"{\"type\":\"box\",\"stage\":\"walls\","
            +"\"block\":\"white_concrete\",\"x\":6,\"y\":0,\"z\":8,"
            +"\"w\":30,\"h\":30,\"d\":1}]}";
        assertThrows(IllegalArgumentException.class,
            ()->ArtworkMode.requireArtworkGeometry(
                "картину человека",bad,MaterialCatalog.allowedIds()));
    }

    @Test void artworkSafetyChecksDoNotPreventRegularArchitecturalDesign() {
        String validBuilding="{\"operations\":[{\"type\":\"box\","
            +"\"block\":\"stone_bricks\",\"x\":6,\"y\":0,\"z\":6,"
            +"\"w\":10,\"h\":1,\"d\":8}]}";
        assertEquals(validBuilding,ArtworkMode.requireArtworkGeometry(
            "построй средневековый дом",validBuilding,MaterialCatalog.allowedIds()));
    }
}

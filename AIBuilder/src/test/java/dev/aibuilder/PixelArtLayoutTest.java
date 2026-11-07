package dev.aibuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class PixelArtLayoutTest {
    private static JsonObject art(String facing,String... rows) {
        JsonObject op=new JsonObject();
        op.addProperty("type","pixel_art");
        op.addProperty("x",32);op.addProperty("y",0);op.addProperty("z",8);
        op.addProperty("facing",facing);
        JsonObject colors=new JsonObject();
        colors.addProperty("A","black_concrete");
        colors.addProperty("B","white_concrete");
        op.add("palette",colors);
        JsonArray lines=new JsonArray();
        for(String line:rows)lines.add(line);
        op.add("rows",lines);
        return op;
    }

    @Test void imageRowsAreTopToBottomAndNorthFacesPlayer() {
        PixelArtLayout.Canvas canvas=PixelArtLayout.parse(
            art("north","AB.",".BA"),MaterialCatalog.allowedIds());
        assertEquals(3,canvas.width());
        assertEquals(2,canvas.height());
        assertEquals(4,canvas.paintedPixels());
        List<String> placed=new ArrayList<>();
        canvas.forEachPixel((x,y,z,id)->
            placed.add(x+","+y+","+z+"="+id));
        assertEquals(List.of(
            "0,1,0=black_concrete",
            "-1,1,0=white_concrete",
            "-1,0,0=white_concrete",
            "-2,0,0=black_concrete"),placed);
    }

    @Test void canvasOrientationHasCorrectFrontAndNoMirror() {
        for(String face:List.of("north","south","east","west")){
            PixelArtLayout.Canvas canvas=PixelArtLayout.parse(
                art(face,"AB"),MaterialCatalog.allowedIds());
            List<String> offsets=new ArrayList<>();
            canvas.forEachPixel((dx,dy,dz,id)->offsets.add(dx+":"+dy+":"+dz));
            String second=switch(face){
                case "north" -> "-1:0:0";
                case "south" -> "1:0:0";
                case "east" -> "0:0:-1";
                case "west" -> "0:0:1";
                default -> throw new IllegalStateException();
            };
            assertEquals(List.of("0:0:0",second),offsets,face);
        }
    }

    @Test void convertsThinGlassPaneToFullVoxelPixel() {
        JsonObject op=art("north","A");
        op.getAsJsonObject("palette").addProperty("A","purple_stained_glass_pane");
        PixelArtLayout.Canvas canvas=PixelArtLayout.parse(
            op,MaterialCatalog.allowedIds());
        assertEquals("purple_stained_glass",canvas.palette().get('A'));
        assertEquals("glass",PixelArtLayout.fullPixelBlock(
            "glass_pane",MaterialCatalog.allowedIds()));
        assertEquals("oak_planks",PixelArtLayout.fullPixelBlock(
            "oak_stairs",MaterialCatalog.allowedIds()));
    }

    @Test void refusesUnsafeAndNonSolidPixelMaterials() {
        for(String invalid:List.of(
            "air","water","oak_door","iron_bars","oak_fence",
            "torch","purple_banner","lantern","tnt","command_block")){
            JsonObject op=art("north","A");
            op.getAsJsonObject("palette").addProperty("A",invalid);
            assertThrows(IllegalArgumentException.class,
                ()->PixelArtLayout.parse(op,MaterialCatalog.allowedIds()),invalid);
        }
    }

    @Test void refusesJaggedRowsUnknownCharactersAndMismatchedDimensions() {
        JsonObject jagged=art("north","AB","A");
        assertThrows(IllegalArgumentException.class,
            ()->PixelArtLayout.parse(jagged,MaterialCatalog.allowedIds()));
        JsonObject unknown=art("north","ABZ");
        assertThrows(IllegalArgumentException.class,
            ()->PixelArtLayout.parse(unknown,MaterialCatalog.allowedIds()));
        JsonObject mismatch=art("north","AB","BA");
        mismatch.addProperty("w",3);
        assertThrows(IllegalArgumentException.class,
            ()->PixelArtLayout.parse(mismatch,MaterialCatalog.allowedIds()));
    }

    @Test void noArtificialGridAbove64PixelsOrExcessivePalette() {
        String longLine="A".repeat(65);
        assertThrows(IllegalArgumentException.class,
            ()->PixelArtLayout.parse(art("north",longLine),MaterialCatalog.allowedIds()));
        String[] tooTall=new String[65];java.util.Arrays.fill(tooTall,"A");
        assertThrows(IllegalArgumentException.class,
            ()->PixelArtLayout.parse(art("north",tooTall),MaterialCatalog.allowedIds()));
    }

    @Test void identicalColorUnderDifferentSymbolsDoesNotPretendToBeDetailed() {
        JsonObject op=art("north","ABAB","BABA");
        op.getAsJsonObject("palette").addProperty("B","black_concrete");
        PixelArtLayout.Canvas canvas=PixelArtLayout.parse(op,MaterialCatalog.allowedIds());
        assertEquals(1,canvas.colorCount());
        assertEquals(0,canvas.transitions());
        assertEquals(8,canvas.maxColorPixels());
    }
}

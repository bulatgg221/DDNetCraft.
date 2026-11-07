package dev.aibuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Regression coverage for imperfect real GPT/Polza pixel row strings. */
final class PixelArtRowsTest {
    private static JsonObject matrix(String... rows) {
        JsonObject op=new JsonObject();
        op.addProperty("type","pixel_art");
        op.addProperty("x",32);op.addProperty("y",0);op.addProperty("z",8);
        JsonObject palette=new JsonObject();
        palette.addProperty("A","black_concrete");
        palette.addProperty("B","white_concrete");
        op.add("palette",palette);
        JsonArray lines=new JsonArray();
        for(String line:rows)lines.add(line);
        op.add("rows",lines);
        return op;
    }

    private static String[] rows(int n,int w) {
        String[] result=new String[n];
        for(int y=0;y<n;y++)result[y]="AB".repeat(w/2)+(w%2!=0?"A":"");
        return result;
    }

    @Test void rows31And33PixelsAreRepairedLocallyTo32() {
        String[] original=rows(32,32);
        original[2]=original[2].substring(0,31);
        original[18]=original[18]+"A";
        JsonObject op=matrix(original);
        PixelArtRows.Fixed fixed=PixelArtRows.repair(op,true);
        assertEquals(32,fixed.width());
        assertEquals(32,fixed.height());
        assertEquals(2,fixed.alteredRows());
        assertFalse(fixed.resizedHeight());
        assertTrue(fixed.rows().stream().allMatch(line->line.length()==32));
        assertEquals(32,PixelArtLayout.parse(op,MaterialCatalog.allowedIds()).width());
        assertTrue(PixelArtRows.normalizeInPlace(op,true));
        assertEquals(32,op.getAsJsonArray("rows").get(2).getAsString().length());
        assertEquals(32,op.getAsJsonArray("rows").get(18).getAsString().length());
        assertFalse(PixelArtRows.normalizeInPlace(op,true));
    }

    @Test void spacesAndVisualSeparatorsAreRemovedWithoutDiscardingColors() {
        JsonObject op=matrix("AB | AB","BA | BA","AB | AB");
        PixelArtRows.Fixed fixed=PixelArtRows.repair(op,false);
        assertEquals(4,fixed.width());
        assertEquals(List.of("ABAB","BABA","ABAB"),fixed.rows());
        assertEquals(3,fixed.alteredRows());
    }

    @Test void portraitWith30RowsIsScaledTo32WhenRequested() {
        JsonObject op=matrix(rows(30,32));
        PixelArtRows.Fixed fixed=PixelArtRows.repair(op,true);
        assertEquals(32,fixed.width());
        assertEquals(32,fixed.height());
        assertTrue(fixed.resizedHeight());
        assertTrue(PixelArtRows.normalizeInPlace(op,true));
        assertEquals(32,op.getAsJsonArray("rows").size());
        var canvas=PixelArtLayout.parse(op,MaterialCatalog.allowedIds());
        assertEquals(32,canvas.width());
        assertEquals(32,canvas.height());
        assertEquals(1024,canvas.paintedPixels());
    }

    @Test void explicitWidthHeightAreHonoredAndRepaired() {
        JsonObject op=matrix(rows(31,31));
        op.addProperty("w",32);op.addProperty("h",32);
        PixelArtRows.Fixed fixed=PixelArtRows.repair(op,true);
        assertEquals(32,fixed.width());
        assertEquals(32,fixed.height());
        assertEquals(31,fixed.alteredRows());
        assertTrue(fixed.resizedHeight());
        assertTrue(PixelArtRows.normalizeInPlace(op,true));
        assertEquals(32,op.get("w").getAsInt());
        assertEquals(32,op.get("h").getAsInt());
        assertEquals(32,op.getAsJsonArray("rows").size());
    }

    @Test void paintingsWithoutPortraitKeepTheirNaturalDimensions() {
        JsonObject op=matrix("ABA","BAB");
        PixelArtRows.Fixed fixed=PixelArtRows.repair(op,false);
        assertEquals(3,fixed.width());
        assertEquals(2,fixed.height());
        assertFalse(fixed.changed());
    }

    @Test void trulyBrokenRowsStillFailInsteadOfMakingUpAWholePicture() {
        String[] rows=rows(32,32);
        rows[10]="AB";
        JsonObject op=matrix(rows);
        var error=assertThrows(IllegalArgumentException.class,
            ()->PixelArtRows.repair(op,true));
        assertTrue(error.getMessage().contains("строка 11"),error.getMessage());
    }

    @Test void unknownPaletteSymbolsCannotDisappearInResampling() {
        String[] source=rows(32,32);
        source[9]=source[9].substring(0,30)+"!A";
        JsonObject op=matrix(source);
        assertThrows(IllegalArgumentException.class,
            ()->PixelArtRows.repair(op,true));
    }

    @Test void excessivelyLargeRawRowsAndOverwideCanvasAreRejected() {
        JsonObject tooLong=matrix("A".repeat(257));
        assertThrows(IllegalArgumentException.class,
            ()->PixelArtRows.repair(tooLong,false));
        JsonObject width=matrix("A".repeat(32));
        width.addProperty("w",65);
        assertThrows(IllegalArgumentException.class,
            ()->PixelArtRows.repair(width,true));
    }

    @Test void originalPerfect32x32MatrixIsNotModified() {
        JsonObject op=matrix(rows(32,32));
        assertFalse(PixelArtRows.normalizeInPlace(op,true));
        assertFalse(op.has("w"));
        assertFalse(op.has("h"));
    }
}

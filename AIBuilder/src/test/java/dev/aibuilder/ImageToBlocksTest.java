package dev.aibuilder;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class ImageToBlocksTest {
    @Test void imagePixelsConvertToActualWhitelistedMinecraftMaterials(){
        BufferedImage image=new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<32;y++)for(int x=0;x<32;x++){
            int rgb=x<16?0xFFFFFFFF:0xFF080A0F;
            image.setRGB(x,y,rgb);
        }
        JsonObject plan=ImageToBlocks.convert(image,"Person from PNG",32);
        JsonObject op=plan.getAsJsonArray("operations").get(0).getAsJsonObject();
        assertEquals("pixel_art",op.get("type").getAsString());
        assertEquals(32,op.getAsJsonArray("rows").size());
        assertEquals(32,op.getAsJsonArray("rows").get(0).getAsString().length());
        assertEquals(2,op.getAsJsonObject("palette").size());
        PixelArtLayout.Canvas canvas=PixelArtLayout.parse(
            op,MaterialCatalog.allowedIds());
        assertEquals(1024,canvas.paintedPixels());
        assertEquals(2,canvas.colorCount());
        Set<String> used=new HashSet<>(canvas.palette().values());
        assertTrue(MaterialCatalog.allowedIds().containsAll(used));
    }

    @Test void preservesTransparencyFromUserImage() {
        BufferedImage image=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<8;y++)for(int x=0;x<8;x++)
            image.setRGB(x,y,0x00000000);
        image.setRGB(2,3,0xFF88AA33);
        JsonObject op=ImageToBlocks.convert(image,"transparency",8)
            .getAsJsonArray("operations").get(0).getAsJsonObject();
        PixelArtLayout.Canvas canvas=PixelArtLayout.parse(
            op,MaterialCatalog.allowedIds());
        assertEquals(1,canvas.paintedPixels());
        assertEquals(63,canvas.width()*canvas.height()-canvas.paintedPixels());
    }

    @Test void pureWhiteAndBlackMapPredictablyToVanillaBlocks() {
        assertEquals("white_wool",ImageToBlocks.closestMaterial(0xEEEEEE));
        assertEquals("black_concrete",ImageToBlocks.closestMaterial(0x080A0F));
    }

    @Test void noOutOfBoundResolutionOrOversizedOriginalSource() {
        BufferedImage image=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);
        assertThrows(IllegalArgumentException.class,
            ()->ImageToBlocks.convert(image,"too small",7));
        assertThrows(IllegalArgumentException.class,
            ()->ImageToBlocks.convert(image,"too big",65));
        assertDoesNotThrow(()->ImageToBlocks.convert(image,"valid",64));
    }

    @Test void imageImportNamesCannotInjectFilePaths() {
        assertThrows(IllegalArgumentException.class,
            ()->ProjectStore.safeId("../private"));
        assertThrows(IllegalArgumentException.class,
            ()->ProjectStore.safeId("profile.png"));
    }

    @Test void alphaThresholdIsRespected(){
        BufferedImage img=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<8;y++)for(int x=0;x<8;x++)
            img.setRGB(x,y,0x80DDCCBB);
        img.setRGB(3,5,0x00DDCCBB);
        JsonObject op=ImageToBlocks.convert(img,"alpha",8)
            .getAsJsonArray("operations").get(0).getAsJsonObject();
        assertEquals(63,PixelArtLayout.parse(op,MaterialCatalog.allowedIds())
            .paintedPixels());
    }
}

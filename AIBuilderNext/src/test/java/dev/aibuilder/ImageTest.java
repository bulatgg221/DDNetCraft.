package dev.aibuilder;
import dev.aibuilder.artwork.*;
import dev.aibuilder.planning.*;
import com.google.gson.*;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ImageTest {
    @Test void preservesAspectAndTransparency(){var image=new BufferedImage(16,8,BufferedImage.TYPE_INT_ARGB);for(int x=0;x<16;x++)for(int y=0;y<8;y++)image.setRGB(x,y,0xffcfd5d6);var bp=PlanParser.parse(ImageToBlocksConverter.convert(image,16),new Limits(400,30000,64,64,300));var c=PixelArtNormalizer.parse(bp.operations().get(0));assertEquals(".".repeat(16),c.rows().get(0));assertEquals("A".repeat(16),c.rows().get(8));}
    @Test void transparentPixelsRemainTransparent(){var image=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB);var c=PixelArtNormalizer.parse(PlanParser.parse(ImageToBlocksConverter.convert(image,8),Limits.defaults()).operations().get(0));assertEquals(".".repeat(8),c.rows().get(3));}
}

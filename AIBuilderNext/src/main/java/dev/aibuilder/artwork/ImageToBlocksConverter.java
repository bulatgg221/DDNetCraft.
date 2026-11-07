package dev.aibuilder.artwork;
import com.google.gson.*;
import dev.aibuilder.config.BuilderConfig;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/** Local PNG/JPEG conversion is independent from text or vision model capabilities. */
public final class ImageToBlocksConverter {
    private static final String[] COLORS={"white","orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black"};
    // Approximate average vanilla concrete sRGB swatches; resource packs may differ.
    private static final int[] RGB={0xcfd5d6,0xe06100,0xa9309f,0x2389c6,0xf1af15,0x5ea818,0xd5658e,0x36393d,0x7d7d73,0x157788,0x64209c,0x2c2e8f,0x603b1f,0x495b24,0x8e2020,0x080a0f};
    public static String load(int size){Path p=BuilderConfig.directory().resolve("import.png");try{if(!Files.isRegularFile(p)||Files.size(p)>8_000_000)throw new IllegalArgumentException("Поместите PNG до 8 МБ в config/aibuilder-next/import.png.");try(ImageInputStream in=ImageIO.createImageInputStream(p.toFile())){var readers=ImageIO.getImageReaders(in);if(!readers.hasNext())throw new IllegalArgumentException("Файл не является поддерживаемым изображением.");ImageReader r=readers.next();try{r.setInput(in);int w=r.getWidth(0),h=r.getHeight(0);if(w>4096||h>4096||w<1||h<1)throw new IllegalArgumentException("Изображение: максимум 4096×4096.");return convert(r.read(0),size);}finally{r.dispose();}}}catch(java.io.IOException e){throw new IllegalArgumentException("Не удалось прочитать import.png.");}}
    public static String convert(BufferedImage image,int size){if(size<8||size>64)throw new IllegalArgumentException("Размер рисунка: 8..64.");JsonObject op=new JsonObject();op.addProperty("type","pixel_art");op.addProperty("w",size);op.addProperty("h",size);op.addProperty("facing","north");op.addProperty("y",0);
        JsonObject palette=new JsonObject();for(int i=0;i<16;i++)palette.addProperty(""+(char)('A'+i),COLORS[i]+"_concrete");palette.addProperty(".","transparent");op.add("palette",palette);JsonArray rows=new JsonArray();
        double scale=Math.min(size/(double)image.getWidth(),size/(double)image.getHeight());double dw=image.getWidth()*scale,dh=image.getHeight()*scale;
        for(int y=0;y<size;y++){StringBuilder row=new StringBuilder();for(int x=0;x<size;x++){double sx=(x+0.5-(size-dw)/2)/scale,sy=(y+0.5-(size-dh)/2)/scale;if(sx<0||sy<0||sx>=image.getWidth()||sy>=image.getHeight()){row.append('.');continue;}int pixel=image.getRGB((int)sx,(int)sy);if((pixel>>>24)<128){row.append('.');continue;}row.append((char)('A'+nearest(pixel)));}rows.add(row.toString());}op.add("rows",rows);
        JsonObject root=new JsonObject();root.addProperty("format",1);root.addProperty("name","Изображение "+size+"×"+size);root.addProperty("mode","pixel_art");root.add("components",new JsonArray());JsonArray ops=new JsonArray();ops.add(op);root.add("operations",ops);return root.toString();}
    private static int nearest(int argb){double best=Double.MAX_VALUE;int index=0;for(int i=0;i<RGB.length;i++){double distance=0;for(int shift:new int[]{16,8,0}){double d=linear((argb>>shift)&255)-linear((RGB[i]>>shift)&255);distance+=d*d;}if(distance<best){best=distance;index=i;}}return index;}
    private static double linear(int c){double s=c/255.0;return s<=0.04045?s/12.92:Math.pow((s+0.055)/1.055,2.4);}
}

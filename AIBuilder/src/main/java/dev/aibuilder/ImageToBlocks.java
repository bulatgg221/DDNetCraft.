package dev.aibuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REAL local PNG-to-Minecraft voxel art conversion. Unlike text LLM pixel-art,
 * this reads source pixels, matches them to actual approved Minecraft blocks
 * with perceptual CIELAB color distance, and then writes the pixel_art schema.
 * No pretrained face template, AI API call or external network access.
 *
 * Color constants represent approximate vanilla block colors; final look
 * depends on resource packs, game shading and lighting.
 */
public final class ImageToBlocks {
    private ImageToBlocks() {}
    private record Swatch(String id,int rgb,double l,double a,double b){}
    private static final List<Swatch> COLORS=createPalette();

    private static List<Swatch> createPalette(){
        List<Swatch> entries=new ArrayList<>();
        String[] names={"white","orange","magenta","light_blue","yellow","lime",
            "pink","gray","light_gray","cyan","purple","blue","brown","green","red","black"};
        int[] concrete={
            0xCFD5D6,0xE06100,0xA9309F,0x2389C6,0xF1AF16,0x70B922,
            0xD5658E,0x36393D,0x7D7D73,0x157788,0x65399D,0x2C2E8D,
            0x603C20,0x4C7229,0x8D2121,0x080A0F};
        int[] terracotta={
            0xD5BDAA,0xA65326,0x98586E,0x737C89,0xBA8524,0x677D31,
            0xA16C64,0x3A3030,0x876B62,0x575C5D,0x764657,0x4A3C5B,
            0x4C3323,0x48543A,0x8E3D2D,0x251610};
        int[] wool={
            0xE9ECEC,0xF07614,0xBD45B6,0x3AA7D2,0xF7C426,0x79C128,
            0xE68AAC,0x434950,0x9C9D98,0x169AAB,0x8934B8,0x4147A0,
            0x835432,0x5C8A2E,0xAE3031,0x17191C};
        for(int family=0;family<3;family++){
            String suffix=family==0?"_concrete":family==1?"_terracotta":"_wool";
            int[] rgbs=family==0?concrete:family==1?terracotta:wool;
            for(int i=0;i<names.length;i++){
                String id=names[i]+suffix;
                if(!MaterialCatalog.allowedIds().contains(id))
                    throw new IllegalStateException("Image palette missing "+id);
                double[] lab=lab(rgbs[i]);
                entries.add(new Swatch(id,rgbs[i],lab[0],lab[1],lab[2]));
            }
        }
        return List.copyOf(entries);
    }

    private static double srgb(int channel){
        double value=channel/255.0;
        return value<=0.04045 ? value/12.92 : Math.pow((value+0.055)/1.055,2.4);
    }

    static double[] lab(int rgb){
        double r=srgb((rgb>>>16)&255);
        double g=srgb((rgb>>>8)&255);
        double b=srgb(rgb&255);
        double x=(0.4124564*r+0.3575761*g+0.1804375*b)/0.95047;
        double y=(0.2126729*r+0.7151522*g+0.0721750*b);
        double z=(0.0193339*r+0.1191920*g+0.9503041*b)/1.08883;
        x=f(x);y=f(y);z=f(z);
        return new double[]{116*y-16,500*(x-y),200*(y-z)};
    }
    private static double f(double t){
        return t>0.008856451679 ? Math.cbrt(t) : (903.2962963*t+16)/116;
    }

    public static String closestMaterial(int rgb) {
        double[] target=lab(rgb);
        Swatch best=null;
        double score=Double.POSITIVE_INFINITY;
        for(Swatch swatch:COLORS){
            double dl=target[0]-swatch.l();
            double da=target[1]-swatch.a();
            double db=target[2]-swatch.b();
            double distance=dl*dl+da*da+db*db;
            if(distance<score){score=distance;best=swatch;}
        }
        if(best==null)throw new IllegalStateException("Empty color palette");
        return best.id();
    }

    /**
     * Center crop (cover), preserving aspect ratio. Every image cell maps to
     * an actual input pixel before choosing nearest Minecraft block color.
     */
    public static JsonObject convert(BufferedImage source,String title,int size) {
        if(source==null)throw new IllegalArgumentException("Изображение пустое.");
        if(size<8||size>64)throw new IllegalArgumentException(
            "Картину можно строить размером 8–64 пикселя.");
        int srcW=source.getWidth(),srcH=source.getHeight();
        if(srcW<1||srcH<1 || srcW>4096 || srcH>4096)
            throw new IllegalArgumentException("Размер исходного PNG вне диапазона.");
        Map<String,Character> palette=new LinkedHashMap<>();
        List<String> rows=new ArrayList<>(size);
        double crop=Math.min(srcW,srcH);
        double left=(srcW-crop)/2.0,top=(srcH-crop)/2.0;
        for(int y=0;y<size;y++){
            StringBuilder row=new StringBuilder(size);
            for(int x=0;x<size;x++){
                int ix=Math.min(srcW-1,Math.max(0,
                    (int)(left+((x+0.5)*crop/size))));
                int iy=Math.min(srcH-1,Math.max(0,
                    (int)(top+((y+0.5)*crop/size))));
                int argb=source.getRGB(ix,iy);
                if(((argb>>>24)&255)<128){row.append('.');continue;}
                String block=closestMaterial(argb&0xFFFFFF);
                Character code=palette.get(block);
                if(code==null){
                    code=symbol(palette.size());
                    palette.put(block,code);
                }
                row.append(code.charValue());
            }
            rows.add(row.toString());
        }
        JsonObject colorMap=new JsonObject();
        for(Map.Entry<String,Character> entry:palette.entrySet())
            colorMap.addProperty(entry.getValue().toString(),entry.getKey());
        JsonArray lines=new JsonArray();
        for(String row:rows)lines.add(row);
        JsonObject pixel=new JsonObject();
        pixel.addProperty("type","pixel_art");
        pixel.addProperty("stage","details");
        pixel.addProperty("x",Math.max(6,size));
        pixel.addProperty("y",0);
        pixel.addProperty("z",8);
        pixel.addProperty("facing","north");
        pixel.add("palette",colorMap);
        pixel.add("rows",lines);
        JsonArray operations=new JsonArray();
        operations.add(pixel);
        JsonObject plan=new JsonObject();
        String name=title==null||title.isBlank()?"PNG pixel art":title;
        plan.addProperty("name",name.substring(0,Math.min(80,name.length())));
        plan.add("operations",operations);
        return plan;
    }

    private static char symbol(int i){
        String alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuv";
        if(i<0||i>=alphabet.length())
            throw new IllegalArgumentException("Слишком много разных цветов в пиксель-арте.");
        return alphabet.charAt(i);
    }

    /**
     * Load ONLY from our dedicated import directory; no absolute paths,
     * symlinks, nested directories or user-supplied extensions are accepted.
     */
    public static BufferedImage readImportedPng(String name) throws IOException {
        ProjectStore.safeId(name);
        Path directory=BuilderConfig.DIRECTORY.resolve("imports");
        Files.createDirectories(directory);
        Path file=directory.resolve(name+".png");
        if(Files.isSymbolicLink(file))
            throw new IllegalArgumentException("Символические ссылки запрещены.");
        if(!Files.isRegularFile(file))
            throw new IllegalArgumentException("Нет PNG: "+file);
        if(Files.size(file)>8_000_000)
            throw new IllegalArgumentException("Размер PNG превышает 8 МБ.");
        try(ImageInputStream input=ImageIO.createImageInputStream(file.toFile())){
            if(input==null)throw new IOException("Не удалось открыть PNG.");
            Iterator<ImageReader> readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new IOException("Не распознан формат PNG.");
            ImageReader reader=readers.next();
            try {
                if(!reader.getFormatName().equalsIgnoreCase("png"))
                    throw new IOException("Разрешён только формат PNG.");
                reader.setInput(input,true,true);
                int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<1||h<1||w>4096||h>4096||((long)w*h)>8_000_000)
                    throw new IOException("Изображение слишком большое.");
                BufferedImage image=reader.read(0);
                if(image==null)throw new IOException("PNG не содержит изображения.");
                return image;
            }finally{reader.dispose();}
        }
    }
}

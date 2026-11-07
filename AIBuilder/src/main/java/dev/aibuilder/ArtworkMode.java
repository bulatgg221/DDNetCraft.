package dev.aibuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Special handling of textual requests for DRAWINGS rather than 3D buildings.
 * No predefined pictures are stored: all pixel characters/rows are authored
 * by the chosen language model for the current user prompt.
 */
public final class ArtworkMode {
    private ArtworkMode() {}

    private static final Pattern ARTWORK = Pattern.compile(
        "портрет|картин|рисунк|нарисуй|изображени|пиксел|пиксель|мозаик|"
        + "pixel.?art|portrait|painting|picture|drawing|mosaic",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern HUMAN = Pattern.compile(
        "человек|человека|человеку|люд|лиц[оа]|персонаж|портрет|голов|"
        + "person|human|face|portrait|character|head",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public static boolean isArtwork(String prompt) {
        return prompt != null && ARTWORK.matcher(prompt).find();
    }

    public static boolean isPortrait(String prompt) {
        return isArtwork(prompt) && HUMAN.matcher(prompt).find();
    }

    /**
     * Reject recognizably crude portraits without substituting a prefab:
     * the next model retry must draw its own higher-resolution color grid.
     */
    public static String requireArtworkGeometry(String prompt, String plan,
                                                Set<String> allowed) {
        if(!isArtwork(prompt))return plan;
        JsonObject root=JsonParser.parseString(plan).getAsJsonObject();
        JsonArray ops=root.getAsJsonArray("operations");
        if(ops==null)throw new IllegalArgumentException("План рисунка без operations.");
        boolean found=false;
        boolean portrait=isPortrait(prompt);
        for(JsonElement element:ops){
            if(!element.isJsonObject())continue;
            JsonObject op=element.getAsJsonObject();
            if(!op.has("type")||!op.get("type").isJsonPrimitive())continue;
            boolean pixel=op.get("type").getAsString().equalsIgnoreCase("pixel_art");
            if(!pixel){
                if(portrait)throw new IllegalArgumentException(
                    "Для портрета используй только pixel_art: крупные box и "
                    + "другие примитивы могут перекрыть готовое изображение.");
                continue;
            }
            found=true;
            PixelArtLayout.Canvas canvas=PixelArtLayout.parse(op,allowed);
            if(portrait)requireDetailedPortrait(canvas);
        }
        if(!found)throw new IllegalArgumentException(
            "Для картины нужен оригинальный pixel_art с матрицей цветов, "
            +"а не несколько больших прямоугольников.");
        return plan;
    }

    public static void requireDetailedPortrait(PixelArtLayout.Canvas canvas) {
        int area=canvas.width()*canvas.height();
        if(canvas.width()<24||canvas.height()<24)
            throw new IllegalArgumentException(
                "Портрет слишком маленький: минимум 24×24, лучше 32×32.");
        if(canvas.colorCount()<9)
            throw new IllegalArgumentException(
                "Портрет недостаточно детализирован: нужно минимум 9 цветов/оттенков.");
        if(canvas.paintedPixels()*10L < area*7L)
            throw new IllegalArgumentException(
                "Портрет почти пустой: заполни фон и человека цветными блоками.");
        if(canvas.transitions()<Math.max(70,area/12))
            throw new IllegalArgumentException(
                "Портрет чрезмерно упрощён: нужны тени, контуры и детали лица.");
        if(canvas.maxColorPixels()*4L > canvas.paintedPixels()*3L)
            throw new IllegalArgumentException(
                "Портрет почти одноцветный: используй больше тонов кожи, одежды и фона.");
    }
}

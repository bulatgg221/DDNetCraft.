package dev.aibuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An original 2D voxel painting, designed entirely by the LLM as pixel rows.
 * The engine NEVER chooses the composition, a human silhouette or preset art:
 * it only validates/translates the model's color matrix into world coordinates.
 *
 * 'rows' are ordered TOP to BOTTOM. x/y/z is the lower-left corner as seen
 * from the front of the painting. 'facing' is the outward-facing front side.
 * '.' is optional transparent/empty background (skips placement).
 */
public final class PixelArtLayout {
    private PixelArtLayout() {}

    @FunctionalInterface
    public interface PixelConsumer {
        void accept(int dx,int dy,int dz,String blockId);
    }

    public record Canvas(int width,int height,String facing,
                         Map<Character,String> palette,List<String> rows,
                         int paintedPixels,int colorCount,int transitions,
                         int maxColorPixels) {
        public void forEachPixel(PixelConsumer consumer) {
            for(int row=0;row<height;row++){
                String line=rows.get(row);
                int dy=height-1-row;
                for(int col=0;col<width;col++){
                    char code=line.charAt(col);
                    if(code=='.')continue;
                    String block=palette.get(code);
                    int dx=0,dz=0;
                    switch(facing){
                        case "north" -> dx=-col;
                        case "south" -> dx=col;
                        case "east" -> dz=-col;
                        case "west" -> dz=col;
                        default -> throw new IllegalStateException("Unvalidated canvas facing");
                    }
                    consumer.accept(dx,dy,dz,block);
                }
            }
        }
    }

    public static Canvas parse(JsonObject op, Set<String> allowed) {
        if(!op.has("rows") || !op.get("rows").isJsonArray())
            throw new IllegalArgumentException(
                "pixel_art: нужен массив rows — строки одинаковой ширины, сверху вниз.");
        JsonArray jsonRows=op.getAsJsonArray("rows");
        if(jsonRows.isEmpty()||jsonRows.size()>64)
            throw new IllegalArgumentException("pixel_art: высота 1–64 пикселя.");
        List<String> rows=new ArrayList<>(jsonRows.size());
        int width=-1;
        for(JsonElement element:jsonRows){
            if(!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("pixel_art: каждая строка rows должна быть текстом.");
            String line=element.getAsString();
            if(width<0)width=line.length();
            if(width<1 || width>64 || line.length()!=width)
                throw new IllegalArgumentException(
                    "pixel_art: все строки обязаны иметь одинаковую ширину 1–64.");
            rows.add(line);
        }
        int height=rows.size();
        if(op.has("w")&&op.get("w").getAsInt()!=width)
            throw new IllegalArgumentException("pixel_art: w не совпадает с длиной строк.");
        if(op.has("h")&&op.get("h").getAsInt()!=height)
            throw new IllegalArgumentException("pixel_art: h не совпадает с числом строк.");
        if(!op.has("palette") || !op.get("palette").isJsonObject())
            throw new IllegalArgumentException(
                "pixel_art: нужен объект palette, например A=white_concrete.");
        JsonObject jsonPalette=op.getAsJsonObject("palette");
        if(jsonPalette.size()<1 || jsonPalette.size()>48)
            throw new IllegalArgumentException("pixel_art: палитра должна содержать 1–48 цветов.");
        Map<Character,String> palette=new LinkedHashMap<>();
        for(Map.Entry<String,JsonElement> entry:jsonPalette.entrySet()){
            String key=entry.getKey();
            if(key.length()!=1 || !key.matches("[a-zA-Z0-9]"))
                throw new IllegalArgumentException(
                    "pixel_art: ключ цвета — одна латинская буква или цифра: "+key);
            JsonElement e=entry.getValue();
            if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("pixel_art: материал цвета "+key+" должен быть строкой.");
            String material=MaterialAliases.resolve(e.getAsString(),allowed);
            palette.put(key.charAt(0), fullPixelBlock(material,allowed));
        }
        int[] counts=new int[128];
        int painted=0,transitions=0;
        java.util.HashSet<Character> used=new java.util.HashSet<>();
        for(int row=0;row<height;row++){
            String line=rows.get(row);
            for(int col=0;col<width;col++){
                char symbol=line.charAt(col);
                if(symbol!='.' && !palette.containsKey(symbol))
                    throw new IllegalArgumentException(
                        "pixel_art: символ '"+symbol+"' отсутствует в palette.");
                if(symbol!='.'){
                    painted++;
                    used.add(symbol);
                    counts[symbol]++;
                }
                if(col>0 && line.charAt(col-1)!=symbol)transitions++;
                if(row>0 && rows.get(row-1).charAt(col)!=symbol)transitions++;
            }
        }
        if(painted==0)throw new IllegalArgumentException("pixel_art: пустое изображение.");
        int max=0;
        for(int count:counts)max=Math.max(max,count);
        String facing=FacingHints.horizontal(op.has("facing")
            ?op.get("facing").getAsString():"north");
        return new Canvas(width,height,facing,Collections.unmodifiableMap(palette),
            List.copyOf(rows),painted,used.size(),transitions,max);
    }

    /**
     * A portrait needs solid voxels, not doors, thin panes or furniture.
     * Convert glass panes to their real full-block equivalents when possible;
     * refuse other thin/non-solid choices rather than placing broken halves.
     */
    public static String fullPixelBlock(String material,Set<String> allowed) {
        String id=material;
        if(id.equals("glass_pane"))id="glass";
        else if(id.endsWith("_stained_glass_pane"))
            id=id.substring(0,id.length()-"_pane".length());
        else if(id.endsWith("_stairs")||id.endsWith("_slab"))
            id=solidFromShape(id,allowed);
        if(!allowed.contains(id))
            throw new IllegalArgumentException("pixel_art: неподдерживаемый материал: "+id);
        for(String suffix:List.of("_door","_trapdoor","_fence","_fence_gate",
            "_wall","_button","_pressure_plate","_carpet","_banner",
            "_bed","_candle","_leaves","_log")) {
            // Logs and wool can be good full blocks; logs are intentionally allowed.
            if(suffix.equals("_log"))continue;
            if(id.endsWith(suffix))
                throw new IllegalArgumentException(
                    "pixel_art: используй полный блок вместо "+id+
                    " (предпочтительно concrete, terracotta, wool).");
        }
        if(Set.of("air","water","torch","wall_torch","lantern","soul_lantern",
                  "chain","iron_bars","glass_pane","short_grass","tall_grass",
                  "fern","flower_pot","ladder","scaffolding").contains(id))
            throw new IllegalArgumentException("pixel_art: "+id+
                " не является подходящим полноцветным пикселем.");
        return id;
    }

    private static String solidFromShape(String id,Set<String> allowed) {
        String suffix=id.endsWith("_stairs")?"_stairs":"_slab";
        String stem=id.substring(0,id.length()-suffix.length());
        for(String candidate:List.of(stem+"_planks",stem+"s",stem,stem+"_block")){
            if(allowed.contains(candidate))return candidate;
        }
        throw new IllegalArgumentException(
            "pixel_art: замени "+id+" на цветной полноразмерный блок.");
    }
}

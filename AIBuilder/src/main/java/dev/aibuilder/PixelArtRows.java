package dev.aibuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Repair small LLM color-matrix formatting errors locally, with ZERO API calls.
 *
 * All source color symbols remain model-authored; a nearest-neighbor adjustment
 * only duplicates/removes adjacent source pixels where the requested width
 * differs slightly. Impossible/grossly corrupt inputs are explicitly rejected,
 * not silently changed into made-up art.
 */
public final class PixelArtRows {
    private PixelArtRows() {}

    public record Fixed(List<String> rows,int width,int height,
                        int alteredRows,boolean resizedHeight){
        public boolean changed(){return alteredRows>0 || resizedHeight;}
    }

    public static Fixed repair(JsonObject op,boolean preferPortrait32) {
        if(!op.has("rows") || !op.get("rows").isJsonArray())
            throw new IllegalArgumentException("pixel_art: нужен массив строк rows.");
        JsonArray jsonRows=op.getAsJsonArray("rows");
        if(jsonRows.size()<1 || jsonRows.size()>64)
            throw new IllegalArgumentException("pixel_art: исходная высота 1–64.");

        if(!op.has("palette") || !op.get("palette").isJsonObject())
            throw new IllegalArgumentException("pixel_art: нужен palette с буквами и цветами.");
        Set<String> symbols=op.getAsJsonObject("palette").keySet();

        List<String> source=new ArrayList<>(jsonRows.size());
        Map<Integer,Integer> frequency=new HashMap<>();
        for(JsonElement element:jsonRows) {
            if(!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("pixel_art: строки rows должны быть текстом.");
            String raw=element.getAsString();
            if(raw.length()>256)
                throw new IllegalArgumentException("pixel_art: слишком длинная строка.");
            StringBuilder cleaned=new StringBuilder(raw.length());
            for(int i=0;i<raw.length();i++){
                char ch=raw.charAt(i);
                // Spaces and optional | visual separators are not palette colors.
                if(Character.isWhitespace(ch) || ch=='|')continue;
                if(ch!='.' && !symbols.contains(String.valueOf(ch)))
                    throw new IllegalArgumentException(
                        "pixel_art: символ '"+ch+"' отсутствует в palette.");
                cleaned.append(ch);
            }
            if(cleaned.isEmpty())
                throw new IllegalArgumentException("pixel_art: встречена пустая строка.");
            String line=cleaned.toString();
            if(line.length()>128)
                throw new IllegalArgumentException("pixel_art: ширина строки более 128.");
            source.add(line);
            frequency.merge(line.length(),1,Integer::sum);
        }

        int prevalentWidth=source.getFirst().length(),prevalentCount=0;
        for(Map.Entry<Integer,Integer> entry:frequency.entrySet()){
            int w=entry.getKey(),count=entry.getValue();
            if(count>prevalentCount || (count==prevalentCount
                && Math.abs(w-32)<Math.abs(prevalentWidth-32))){
                prevalentWidth=w;prevalentCount=count;
            }
        }
        int width=dimension(op,"w");
        if(width==0)width=preferPortrait32 && prevalentWidth>=24 && prevalentWidth<=40
            ?32:prevalentWidth;
        if(width<1||width>64)
            throw new IllegalArgumentException("pixel_art: итоговая ширина должна быть 1–64.");

        List<String> fixedRows=new ArrayList<>(source.size());
        int changed=0;
        for(int row=0;row<source.size();row++){
            String text=source.get(row);
            String original=jsonRows.get(row).getAsString();
            int length=text.length();
            if(length!=width){
                int diff=Math.abs(length-width);
                // A model may miss a few chars but not replace half its drawing.
                // Keep the repair local and bounded for portrait proportions.
                if(diff>Math.max(1,width/4) || diff*4L>width
                    || diff*4L>length)
                    throw new IllegalArgumentException(
                        "pixel_art: строка "+(row+1)+" имеет "+length+
                        " пикселей вместо "+width+" (слишком большая разница).");
                text=resizeLine(text,width);
            }
            if(!text.equals(original))changed++;
            fixedRows.add(text);
        }

        int height=dimension(op,"h");
        if(height==0)height=preferPortrait32 && source.size()>=24
            && source.size()<=40?32:source.size();
        if(height<1||height>64)
            throw new IllegalArgumentException("pixel_art: итоговая высота должна быть 1–64.");
        boolean heightChanged=height!=fixedRows.size();
        if(heightChanged){
            int diff=Math.abs(height-fixedRows.size());
            if(diff*4L>height || diff*4L>fixedRows.size())
                throw new IllegalArgumentException(
                    "pixel_art: "+fixedRows.size()+" строк вместо "+height+
                    " (слишком большая разница для безопасного исправления).");
            List<String> scaled=new ArrayList<>(height);
            for(int row=0;row<height;row++)
                scaled.add(fixedRows.get(Math.min(fixedRows.size()-1,
                    (int)(((long)(2*row+1)*fixedRows.size())/(2L*height)))));
            fixedRows=scaled;
        }
        return new Fixed(List.copyOf(fixedRows),width,height,changed,heightChanged);
    }

    /**
     * Apply repaired rows to the actual returned JSON, so the server compiles
     * and archives exactly the same matrix that the planner validated.
     */
    public static boolean normalizeInPlace(JsonObject op,boolean portrait) {
        Fixed result=repair(op,portrait);
        if(!result.changed())return false;
        JsonArray fixed=new JsonArray();
        for(String line:result.rows())fixed.add(line);
        op.add("rows",fixed);
        if(op.has("w"))op.addProperty("w",result.width());
        if(op.has("h"))op.addProperty("h",result.height());
        return true;
    }

    private static int dimension(JsonObject op,String key) {
        if(!op.has(key)||op.get(key).isJsonNull())return 0;
        try {
            int value=op.get(key).getAsInt();
            if(value<1||value>64)
                throw new IllegalArgumentException(
                    "pixel_art: "+key+" должен быть от 1 до 64.");
            return value;
        }catch(NumberFormatException | IllegalStateException error){
            throw new IllegalArgumentException(
                "pixel_art: "+key+" должен быть целым числом.",error);
        }
    }

    private static String resizeLine(String line,int width) {
        int length=line.length();
        StringBuilder result=new StringBuilder(width);
        for(int x=0;x<width;x++) {
            int index=(int)(((long)(2*x+1)*length)/(2L*width));
            result.append(line.charAt(Math.min(length-1,index)));
        }
        return result.toString();
    }
}

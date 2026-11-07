package dev.aibuilder.artwork;
import com.google.gson.*;
import dev.aibuilder.planning.PlanParser;
import java.util.*;
import static dev.aibuilder.planning.PlanParser.*;

public final class PixelArtNormalizer {
    public record Canvas(List<String> rows,Map<Character,String> palette,int width,int height,int repaired) {}
    public static Canvas parse(JsonObject op) {
        JsonArray raw=array(op,"rows"); if(raw.size()<7||raw.size()>65)throw error("pixel_art: число строк вне 8..64 ±1.");
        List<String> rows=new ArrayList<>();for(var e:raw){if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())throw error("pixel_art: нужны строки.");rows.add(e.getAsString());}
        List<Integer> widths=rows.stream().map(String::length).sorted().toList();
        int w=integer(op,"w",widths.get(widths.size()/2)),h=integer(op,"h",rows.size());
        if(w<8||w>64||h<8||h>64)throw error("pixel_art: размер должен быть 8..64.");
        if(Math.abs(rows.size()-h)>1)throw error("pixel_art: слишком много потерянных строк.");
        var palette=new LinkedHashMap<Character,String>();JsonObject p=op.getAsJsonObject("palette");
        if(p==null||p.size()<1||p.size()>64)throw error("pixel_art: нужна палитра 1..64.");
        for(var e:p.entrySet()) {String k=e.getKey();if(k.length()!=1||Character.isSurrogate(k.charAt(0)))throw error("pixel_art: ключ палитры — один символ.");palette.put(k.charAt(0),string(p,k,null));}
        int repaired=rows.size()==h?0:1;List<String> fixed=new ArrayList<>();
        for(String row:rows){if(Math.abs(row.length()-w)>1)throw error("pixel_art: строка сильно повреждена.");for(char c:row.toCharArray())if(!palette.containsKey(c))throw error("pixel_art: неизвестный символ "+c);
            if(row.length()!=w)repaired++;StringBuilder out=new StringBuilder();for(int x=0;x<w;x++)out.append(row.charAt(nearest(x,w,row.length())));fixed.add(out.toString());}
        List<String> finalRows=new ArrayList<>();for(int y=0;y<h;y++)finalRows.add(fixed.get(nearest(y,h,fixed.size())));
        return new Canvas(List.copyOf(finalRows),Map.copyOf(palette),w,h,repaired);
    }
    public static int nearest(int i,int target,int source){return Math.min(source-1,(int)Math.floor((i+0.5)*source/target));}
}

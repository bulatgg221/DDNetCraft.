package dev.aibuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies user-specified house style / dimensions to parametric house operations.
 * The model can still choose a style when the user does not; explicit prompt
 * requests override the example template the model might copy verbatim.
 * This is local and does not make another billable API request.
 */
public final class HousePromptStyle {
    private HousePromptStyle() {}
    private static final Set<String> HOUSE_TYPES=Set.of(
        "house","cottage","detailed_house","manor","villa");
    private static final Pattern SIZES=Pattern.compile(
        "(?<!\\d)(\\d{2})\\s*[xх×*]\\s*(\\d{2})(?!\\d)");

    public static String apply(String json,String description){
        JsonObject root=JsonParser.parseString(json).getAsJsonObject();
        JsonArray ops=root.getAsJsonArray("operations");
        if(ops==null || description==null || description.isBlank()) return json;
        String prompt=description.toLowerCase(Locale.ROOT);
        String chosenStyle=styleFor(prompt);
        Matcher dimensions=SIZES.matcher(prompt);
        int width=-1,depth=-1;
        if(dimensions.find()){
            int a=Integer.parseInt(dimensions.group(1));
            int b=Integer.parseInt(dimensions.group(2));
            if(a>=11 && a<=25 && b>=9 && b<=23){width=a;depth=b;}
        }
        Integer floorCount=explicitFloors(prompt);
        String roof=roofFor(prompt);
        Boolean porch=mentionBoolean(prompt,new String[]{"крыльц","веранд","porch","veranda"},
            new String[]{"без крыльца","без веранды","no porch","without porch"});
        Boolean balcony=mentionBoolean(prompt,new String[]{"балкон","balcony"},
            new String[]{"без балкона","no balcony","without balcony"});
        Boolean chimney=mentionBoolean(prompt,new String[]{"каминная труба","дымоход","chimney"},
            new String[]{"без трубы","без дымохода","no chimney","without chimney"});
        Boolean garden=mentionBoolean(prompt,new String[]{"сад","цвет","клумб","garden","flowerbed"},
            new String[]{"без сада","без цветов","no garden","without garden"});
        boolean changed=false;
        for(JsonElement entry:ops) {
            if(!entry.isJsonObject())continue;
            JsonObject op=entry.getAsJsonObject();
            if(!HOUSE_TYPES.contains(string(op,"type","")))continue;
            if(chosenStyle!=null){op.addProperty("style",chosenStyle);changed=true;}
            if(width>0){op.addProperty("w",width);op.addProperty("d",depth);changed=true;}
            if(floorCount!=null){op.addProperty("floors",floorCount);changed=true;}
            if(roof!=null){op.addProperty("roof_style",roof);changed=true;}
            if(porch!=null){op.addProperty("porch",porch);changed=true;}
            if(balcony!=null){op.addProperty("balcony",balcony);changed=true;}
            if(chimney!=null){op.addProperty("chimney",chimney);changed=true;}
            if(garden!=null){op.addProperty("garden",garden);changed=true;}
        }
        return changed?root.toString():json;
    }

    private static String styleFor(String p){
        if(has(p,"япон","самура","пагод","japan","zen house"))return "japanese";
        if(has(p,"современн","модерн","минимал","хай-тек","high tech","modern","minimalist","contemporary"))return "modern";
        if(has(p,"скандинав","викинг","норвеж","nordic","scandinavian","viking","norse"))return "nordic";
        if(has(p,"шале","альп","швейцар","alpine","chalet"))return "alpine";
        if(has(p,"кирпичн","brick mansion","brick manor","colonial"))return "brick";
        if(has(p,"деревенск","лесн","избушк","сельск","фермерск","rustic","forest cottage","farmhouse"))return "rustic";
        if(has(p,"средневек","готич","фахверк","тюдор","medieval","tudor"))return "medieval";
        return null;
    }
    private static String roofFor(String p){
        if(has(p,"плоск","flat roof"))return "flat";
        if(has(p,"вальмов","четырёхскат","четырехскат","hipped roof","hip roof"))return "hip";
        if(has(p,"двускат","gable roof","pitched roof"))return "gable";
        return null;
    }
    private static Integer explicitFloors(String p){
        if(has(p,"одноэтаж","один этаж","1 этаж","single-storey","single-story",
            "one-storey","one-story","one floor"))return 1;
        if(has(p,"двухэтаж","двух этаж","два этажа","2 этажа","two-storey",
            "two-story","two floors","two story"))return 2;
        return null;
    }
    private static Boolean mentionBoolean(String p,String[] positive,String[] negative){
        if(has(p,negative))return false;
        return has(p,positive)?true:null;
    }
    private static boolean has(String s,String... patterns){
        for(String pattern:patterns)if(s.contains(pattern))return true;
        return false;
    }
    private static String string(JsonObject obj,String name,String fallback){
        return obj.has(name)&&!obj.get(name).isJsonNull()?obj.get(name).getAsString():fallback;
    }
}

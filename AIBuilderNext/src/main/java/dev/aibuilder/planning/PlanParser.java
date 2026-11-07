package dev.aibuilder.planning;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.*;

/** Only fences and trailing commas are repaired; incomplete documents are never guessed. */
public final class PlanParser {
    public static final int MAX_JSON = 2_000_000;
    public static Blueprint parse(String raw, Limits limits) {
        if(raw==null || raw.length()>MAX_JSON) throw error("Ответ пустой или слишком большой.");
        String json=normalize(raw);
        JsonObject root;
        try { JsonReader reader=new JsonReader(new StringReader(json)); reader.setLenient(false);
            root=readStrict(reader,0).getAsJsonObject();
            if(reader.peek()!=JsonToken.END_DOCUMENT) throw error("Лишние данные после JSON.");
        } catch(Exception ex) { throw error("Неполный или некорректный JSON: "+ex.getMessage()); }
        if(integer(root,"format",-1)!=1) throw error("Нужен формат плана 1.");
        String name=string(root,"name",null), mode=string(root,"mode",null);
        if(name.length()>120 || !Set.of("structure","pixel_art").contains(mode)) throw error("Неверные имя или режим.");
        JsonArray ops=array(root,"operations");
        if(ops.isEmpty() || ops.size()>limits.operations()) throw error("Число операций: "+ops.size()+", лимит: "+limits.operations());
        List<JsonObject> operations=new ArrayList<>();
        for(JsonElement el:ops) { if(!el.isJsonObject()) throw error("Операция должна быть объектом."); operations.add(el.getAsJsonObject()); }
        List<Blueprint.Component> components=new ArrayList<>(); Set<String> ids=new HashSet<>();
        JsonArray cs=root.has("components")?array(root,"components"):new JsonArray();
        if(cs.size()>512) throw error("Слишком много компонентов.");
        for(JsonElement e:cs) {
            JsonObject c=e.getAsJsonObject(), b=c.getAsJsonObject("bounds"); String id=string(c,"id",null);
            if(!ids.add(id)) throw error("Повтор ID компонента: "+id);
            int x=integer(b,"x",0), y=integer(b,"y",0), z=integer(b,"z",0),w=positive(b,"w"),h=positive(b,"h"),d=positive(b,"d");
            limits.position(new Pos(x,y,z)); limits.position(new Pos(Math.addExact(x,w-1),Math.addExact(y,h-1),Math.addExact(z,d-1)));
            Pos access=c.has("access")?pos(c.getAsJsonObject("access")):null;
            var bounds=new Blueprint.Bounds(x,y,z,w,h,d);
            if(access!=null && !bounds.contains(access)) throw error("Точка доступа вне компонента: "+id);
            components.add(new Blueprint.Component(id,string(c,"kind",null),bounds,strings(c,"connectedTo"),access));
        }
        for(var c:components) for(String id:c.connectedTo()) if(!ids.contains(id)) throw error("Неизвестная связь: "+id);
        List<Blueprint.Section> sections=new ArrayList<>();
        if(root.has("sections")) for(var e:array(root,"sections")) {var s=e.getAsJsonObject();sections.add(new Blueprint.Section(string(s,"id",null),strings(s,"dependsOn")));}
        orderSections(sections);
        var spec=root.has("specification")?root.getAsJsonObject("specification"):new JsonObject();
        return new Blueprint(name,mode,spec,List.copyOf(components),List.copyOf(sections),List.copyOf(operations),json);
    }
    public static List<String> orderSections(List<Blueprint.Section> sections) {
        if(sections.size()>64) throw error("Максимум 64 раздела.");
        Map<String,Blueprint.Section> all=new LinkedHashMap<>();
        for(var s:sections) if(all.put(s.id(),s)!=null) throw error("Повтор раздела.");
        List<String> out=new ArrayList<>();
        while(out.size()<all.size()) {int before=out.size();for(var s:sections) {
            for(var dep:s.dependsOn()) if(!all.containsKey(dep)) throw error("Неизвестная зависимость: "+dep);
            if(!out.contains(s.id()) && out.containsAll(s.dependsOn())) out.add(s.id());
        } if(before==out.size()) throw error("Цикл зависимостей разделов.");}
        return out;
    }
    private static JsonElement readStrict(JsonReader r,int depth) throws java.io.IOException {
        if(depth>32) throw error("JSON вложен слишком глубоко.");
        return switch(r.peek()) {
            case BEGIN_OBJECT -> {r.beginObject();JsonObject o=new JsonObject();while(r.hasNext()){String k=r.nextName();if(o.has(k))throw error("Повтор поля "+k);o.add(k,readStrict(r,depth+1));}r.endObject();yield o;}
            case BEGIN_ARRAY -> {r.beginArray();JsonArray a=new JsonArray();while(r.hasNext())a.add(readStrict(r,depth+1));r.endArray();yield a;}
            case STRING -> new JsonPrimitive(r.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(r.nextString()));
            case BOOLEAN -> new JsonPrimitive(r.nextBoolean());
            case NULL -> {r.nextNull();yield JsonNull.INSTANCE;}
            default -> throw error("Неожиданный конец JSON.");
        };
    }
    public static String normalize(String raw) {
        String s=raw.strip();if(s.startsWith("```")){int n=s.indexOf('\n');if(n<0||!s.endsWith("```"))throw error("Обрезанный блок JSON.");s=s.substring(n+1,s.length()-3).strip();}
        StringBuilder b=new StringBuilder();boolean quote=false,escape=false;
        for(int i=0;i<s.length();i++){char c=s.charAt(i);if(quote){b.append(c);if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='"')quote=false;continue;}
            if(c=='"')quote=true;
            if(c==','){int j=i+1;while(j<s.length()&&Character.isWhitespace(s.charAt(j)))j++;if(j<s.length()&&(s.charAt(j)==']'||s.charAt(j)=='}'))continue;}
            b.append(c);
        } return b.toString();
    }
    public static Pos pos(JsonObject o){return new Pos(integer(o,"x",0),integer(o,"y",0),integer(o,"z",0));}
    public static int positive(JsonObject o,String k){int n=integer(o,k,-1);if(n<1||n>257)throw error("Размер "+k+" должен быть 1..257.");return n;}
    public static int integer(JsonObject o,String k,int fallback){if(o==null)throw error("Нет объекта с полем "+k);if(!o.has(k))return fallback;try{var p=o.getAsJsonPrimitive(k);if(p.isBoolean())throw new Exception();return new BigDecimal(p.getAsString()).intValueExact();}catch(Exception e){throw error("Поле "+k+" должно быть целым числом.");}}
    public static String string(JsonObject o,String k,String fallback){if(o!=null&&o.has(k)&&o.get(k).isJsonPrimitive()&&o.getAsJsonPrimitive(k).isString()){String s=o.get(k).getAsString();if(s.length()>16000)throw error("Слишком длинное поле "+k);return s;}if(fallback!=null)return fallback;throw error("Нет строкового поля "+k);}
    public static JsonArray array(JsonObject o,String k){if(o!=null&&o.has(k)&&o.get(k).isJsonArray())return o.getAsJsonArray(k);throw error("Нет массива "+k);}
    public static List<String> strings(JsonObject o,String k){if(!o.has(k))return List.of();List<String> out=new ArrayList<>();for(var v:array(o,k)){if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())throw error("Нужны строки в "+k);out.add(v.getAsString());}return List.copyOf(out);}
    public static IllegalArgumentException error(String s){return new IllegalArgumentException(s);}
}

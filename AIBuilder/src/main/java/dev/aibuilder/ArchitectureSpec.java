package dev.aibuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LLM-authored semantic architecture. The model invents all rooms, dimensions,
 * boundaries and adjacency. Nothing here generates a preset house or castle.
 *
 * Semantic validation is separate from Minecraft block-state compilation:
 * malformed metadata produces diagnostics instead of throwing away a usable
 * block plan; invalid/corrupt geometry is still rejected by PlanCompiler.
 */
public final class ArchitectureSpec {
    private ArchitectureSpec() {}

    public record Region(String id,String kind,int x,int y,int z,int width,int height,
                         int depth,List<String> connectedTo) {
        public boolean contains(int px,int py,int pz) {
            return px>=x && px<x+width && py>=y && py<y+height
                && pz>=z && pz<z+depth;
        }
        public boolean near(Region other) {
            int gapX=Math.max(0,Math.max(other.x-(x+width),x-(other.x+other.width)));
            int gapY=Math.max(0,Math.max(other.y-(y+height),y-(other.y+other.height)));
            int gapZ=Math.max(0,Math.max(other.z-(z+depth),z-(other.z+other.depth)));
            return gapX<=2 && gapY<=3 && gapZ<=2;
        }
    }

    public record Parsed(List<Region> regions,List<String> warnings) {
        public int declaredTowers() {
            return (int)regions.stream().filter(r ->
                r.kind().contains("tower") || r.kind().contains("башн")).count();
        }
    }

    public static Parsed inspect(String json) {
        JsonObject root=JsonParser.parseString(json).getAsJsonObject();
        JsonObject architecture=root.has("architecture") &&
            root.get("architecture").isJsonObject()
            ?root.getAsJsonObject("architecture"):null;
        if(architecture==null)
            return new Parsed(List.of(),List.of(
                "Модель не прислала архитектурные зоны: связность комнат не проверена."));
        JsonArray array=architecture.has("regions") &&
            architecture.get("regions").isJsonArray()
            ?architecture.getAsJsonArray("regions"):null;
        if(array==null || array.isEmpty())
            return new Parsed(List.of(),List.of(
                "В architecture нет regions; проверка связности пропущена."));
        if(array.size()>128)
            return new Parsed(List.of(),List.of(
                "Слишком много архитектурных зон (максимум 128)."));
        List<String> warnings=new ArrayList<>();
        Map<String,Region> regions=new LinkedHashMap<>();
        for(JsonElement element:array) {
            if(!element.isJsonObject()){
                warnings.add("Архитектурная зона не является объектом.");
                continue;
            }
            try {
                JsonObject obj=element.getAsJsonObject();
                String id=requiredText(obj,"id");
                String kind=requiredText(obj,"kind").toLowerCase(java.util.Locale.ROOT);
                if(id.length()>64 || kind.length()>64 || regions.containsKey(id)){
                    warnings.add("Повторяющаяся или слишком длинная зона: "+id);
                    continue;
                }
                JsonObject b=obj.getAsJsonObject("bounds");
                if(b==null)throw new IllegalArgumentException("отсутствуют bounds");
                int x=coordinate(b,"x"),y=coordinate(b,"y"),z=coordinate(b,"z");
                int w=extent(b,"width"),h=extent(b,"height"),d=extent(b,"depth");
                List<String> connections=new ArrayList<>();
                if(obj.has("connectedTo")&&obj.get("connectedTo").isJsonArray()){
                    for(JsonElement other:obj.getAsJsonArray("connectedTo")){
                        if(other.isJsonPrimitive()&&other.getAsJsonPrimitive().isString())
                            connections.add(other.getAsString());
                    }
                }
                regions.put(id,new Region(id,kind,x,y,z,w,h,d,
                    List.copyOf(connections)));
            }catch(RuntimeException invalid){
                warnings.add("Некорректная архитектурная зона: "+invalid.getMessage());
            }
        }
        for(Region region:regions.values()){
            for(String link:region.connectedTo()){
                if(link.equals(region.id())) {
                    warnings.add("Зона "+region.id()+" соединена сама с собой.");
                    continue;
                }
                Region target=regions.get(link);
                if(target==null){
                    warnings.add("В "+region.id()+" ссылка на несуществующую зону "+link);
                    continue;
                }
                if(!region.near(target))
                    warnings.add("Зоны "+region.id()+" и "+link+
                        " заявлены соединёнными, но находятся далеко.");
            }
        }
        if(regions.size()>1){
            // Treat adjacency declarations as undirected: a room's connections
            // to the central hall are sufficient even if only one side lists it.
            Map<String,Set<String>> graph=new HashMap<>();
            for(String id:regions.keySet())graph.put(id,new HashSet<>());
            for(Region r:regions.values())for(String link:r.connectedTo()){
                if(graph.containsKey(link)&&!link.equals(r.id())){
                    graph.get(r.id()).add(link);
                    graph.get(link).add(r.id());
                }
            }
            String entrance=regions.values().stream()
                .filter(r->r.kind().contains("entrance") ||
                    r.kind().contains("entry") || r.kind().contains("вход"))
                .map(Region::id).findFirst().orElse(regions.keySet().iterator().next());
            Set<String> visited=new HashSet<>();
            ArrayDeque<String> frontier=new ArrayDeque<>();
            visited.add(entrance);frontier.add(entrance);
            while(!frontier.isEmpty())
                for(String next:graph.get(frontier.removeFirst()))
                    if(visited.add(next))frontier.addLast(next);
            if(visited.size()!=regions.size())
                warnings.add("Архитектурные зоны не связаны от входа: "
                    +(regions.size()-visited.size())+" из "+regions.size());
        }
        return new Parsed(List.copyOf(regions.values()),List.copyOf(warnings));
    }

    private static String requiredText(JsonObject obj,String key) {
        if(!obj.has(key)||!obj.get(key).isJsonPrimitive()
            ||!obj.getAsJsonPrimitive(key).isString())
            throw new IllegalArgumentException("отсутствует текст "+key);
        String result=obj.get(key).getAsString().strip();
        if(result.isEmpty())throw new IllegalArgumentException("пустое "+key);
        return result;
    }

    private static int coordinate(JsonObject b,String key){
        if(!b.has(key))throw new IllegalArgumentException("нет "+key);
        int value=b.get(key).getAsInt();
        if(value<-512 || value>512)
            throw new IllegalArgumentException("недопустимое "+key);
        return value;
    }
    private static int extent(JsonObject b,String key){
        if(!b.has(key))throw new IllegalArgumentException("нет "+key);
        int value=b.get(key).getAsInt();
        if(value<1 || value>256)
            throw new IllegalArgumentException("недопустимый размер "+key);
        return value;
    }
}

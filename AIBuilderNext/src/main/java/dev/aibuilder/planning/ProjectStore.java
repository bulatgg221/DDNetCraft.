package dev.aibuilder.planning;
import com.google.gson.*;
import dev.aibuilder.config.BuilderConfig;
import dev.aibuilder.geometry.PlacementPlan;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public final class ProjectStore {
    private static Path directory(){return BuilderConfig.directory().resolve("projects");}
    private static Path path(String name){if(name==null||!name.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Имя: латинские буквы, цифры, _ и -, до 64 символов.");return directory().resolve(name+".json");}
    public static void save(String name,String prompt,String model,PlacementPlan plan){try{Files.createDirectories(directory());JsonObject out=new JsonObject();out.addProperty("created",Instant.now().toString());out.addProperty("prompt",prompt);out.addProperty("model",model);out.add("blueprint",JsonParser.parseString(plan.blueprint().json()));out.add("geometry",BuilderConfig.GSON.toJsonTree(plan.voxels().entrySet().stream().map(e->Map.of("position",e.getKey(),"voxel",e.getValue())).toList()));Path target=path(name),tmp=target.resolveSibling(target.getFileName()+".tmp");Files.writeString(tmp,BuilderConfig.GSON.toJson(out));Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){throw new IllegalArgumentException("Не удалось сохранить проект: "+e.getMessage());}}
    public static String load(String name){try{Path p=path(name);if(Files.size(p)>20_000_000)throw new IllegalArgumentException("Проект слишком большой.");return JsonParser.parseString(Files.readString(p)).getAsJsonObject().get("blueprint").toString();}catch(Exception e){throw new IllegalArgumentException("Не удалось прочитать проект: "+e.getMessage());}}
    public static List<String> list(){try{if(!Files.exists(directory()))return List.of();try(var s=Files.list(directory())){return s.filter(p->p.getFileName().toString().endsWith(".json")).map(p->p.getFileName().toString()).sorted().limit(100).toList();}}catch(Exception e){throw new IllegalArgumentException("Не удалось прочитать историю.");}}
}

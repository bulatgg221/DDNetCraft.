package dev.aibuilder;

import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reusable user-named original AI blueprints. No arbitrary input paths,
 * no model-provided filesystem paths, no key material and NO API calls.
 */
public final class ProjectStore {
    private ProjectStore() {}

    private static final Pattern SAFE_ID=Pattern.compile("[a-zA-Z0-9_-]{1,48}");
    private static final int MAX_SIZE=250_000;
    private static Path folder(){return BuilderConfig.DIRECTORY.resolve("projects");}

    public static String safeId(String id){
        if(id==null||!SAFE_ID.matcher(id).matches())
            throw new IllegalArgumentException(
                "Имя проекта: 1–48 латинских букв, цифр, _ или -.");
        return id;
    }

    private static Path target(String id){
        return folder().resolve(safeId(id)+".json");
    }

    public static void saveLast(String name) throws IOException {
        String json=Files.readString(
            BuilderConfig.DIRECTORY.resolve("last-generated-plan.json"),
            StandardCharsets.UTF_8);
        save(name,json);
    }

    public static void save(String name,String json) throws IOException {
        Path destination=target(name);
        if(json==null||json.isBlank()||json.length()>MAX_SIZE)
            throw new IllegalArgumentException("Чертёж пуст или превышает лимит 250 КБ.");
        // Reject incomplete JSON; the actual geometry is validated again on load.
        if(!JsonParser.parseString(json).isJsonObject())
            throw new IllegalArgumentException("Чертёж должен быть JSON-объектом.");
        Files.createDirectories(folder());
        Path temp=Files.createTempFile(folder(),"plan-",".tmp");
        boolean moved=false;
        try {
            Files.writeString(temp,json,StandardCharsets.UTF_8,
                StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temp,destination,StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            }catch(java.nio.file.AtomicMoveNotSupportedException ignored){
                Files.move(temp,destination,StandardCopyOption.REPLACE_EXISTING);
            }
            moved=true;
        }finally{
            if(!moved)Files.deleteIfExists(temp);
        }
    }

    public static String load(String name) throws IOException {
        Path file=target(name);
        if(Files.notExists(file))
            throw new IllegalArgumentException("Сохранённый проект '"+name+"' не найден.");
        if(Files.size(file)>MAX_SIZE)
            throw new IllegalArgumentException("Файл проекта превышает лимит 250 КБ.");
        String plan=Files.readString(file,StandardCharsets.UTF_8);
        if(!JsonParser.parseString(plan).isJsonObject())
            throw new IllegalArgumentException("Сохранённый план содержит неверный JSON.");
        return plan;
    }

    public static List<String> list() throws IOException {
        if(Files.notExists(folder()))return List.of();
        try(Stream<Path> paths=Files.list(folder())){
            return paths.filter(Files::isRegularFile)
                .map(p->p.getFileName().toString())
                .filter(s->s.endsWith(".json"))
                .map(s->s.substring(0,s.length()-5))
                .filter(s->SAFE_ID.matcher(s).matches())
                .sorted(Comparator.naturalOrder())
                .limit(30).toList();
        }
    }
}

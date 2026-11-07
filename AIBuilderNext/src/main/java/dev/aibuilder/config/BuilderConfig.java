package dev.aibuilder.config;
import com.google.gson.*;
import dev.aibuilder.planning.Limits;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
public final class BuilderConfig {
    public String provider="polza",model="openai/gpt-4.1";
    public int maxOperations=400,maxBlocks=30000,horizontal=48,vertical=32,blocksPerTick=300,maxTokens=16000;
    public boolean retryMalformedJson=false,retryArtwork=false;public int maxApiRetries=0;
    public static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    public static Path directory(){return FabricLoader.getInstance().getConfigDir().resolve("aibuilder-next");}
    public Limits limits(){return new Limits(maxOperations,maxBlocks,horizontal,vertical,blocksPerTick);}
    public void validate(){limits();if(!provider.equals("polza")&&!provider.equals("openai"))throw new IllegalArgumentException("Неверный провайдер.");if(model==null||!model.matches("[A-Za-z0-9_./:-]{1,160}"))throw new IllegalArgumentException("Неверный ID модели.");if(maxTokens<512||maxTokens>64000)throw new IllegalArgumentException("Токены: 512..64000.");}
    public BuilderConfig copy(){return GSON.fromJson(GSON.toJson(this),BuilderConfig.class);}
    public static BuilderConfig load(){try{Path p=directory().resolve("config.json");BuilderConfig c=Files.exists(p)?GSON.fromJson(Files.readString(p),BuilderConfig.class):new BuilderConfig();c.validate();return c;}catch(Exception e){throw new IllegalArgumentException("Не удалось прочитать config.json: "+e.getMessage());}}
    public void save(){validate();try{Files.createDirectories(directory());Path tmp=directory().resolve("config.tmp");Files.writeString(tmp,GSON.toJson(this),StandardCharsets.UTF_8);Files.move(tmp,directory().resolve("config.json"),StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){throw new IllegalArgumentException("Не удалось сохранить настройки.");}}
    public String base(){return provider.equals("polza")?"https://polza.ai/api/v1":"https://api.openai.com/v1";}
}

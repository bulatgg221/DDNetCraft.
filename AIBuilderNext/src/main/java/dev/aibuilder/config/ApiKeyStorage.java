package dev.aibuilder.config;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Session/env keys everywhere; optional Windows DPAPI CurrentUser persistence. */
public final class ApiKeyStorage {
    private static final Map<String,String> SESSION=new java.util.concurrent.ConcurrentHashMap<>();
    public static boolean windows(){return System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win");}
    public static void set(String provider,String key,boolean persist){if(key==null||key.isBlank())return;String cleaned=key.strip();if(cleaned.length()>4096||cleaned.contains("\n")||cleaned.contains("\r"))throw new IllegalArgumentException("Некорректный ключ.");
        if(persist){if(!windows())throw new IllegalArgumentException("Постоянное хранение доступно через Windows DPAPI.");try{Files.createDirectories(BuilderConfig.directory());Files.writeString(path(provider),protect(cleaned,true));}catch(Exception e){throw new IllegalArgumentException("Не удалось сохранить ключ через DPAPI.");}}SESSION.put(provider,cleaned);}
    private static Path path(String provider){if(!Set.of("polza","openai").contains(provider))throw new IllegalArgumentException("Провайдер.");return BuilderConfig.directory().resolve(provider+".dpapi");}
    public static String get(String provider){String key=SESSION.get(provider);if(key!=null)return key;String env=System.getenv(provider.equals("polza")?"POLZA_API_KEY":"OPENAI_API_KEY");if(env!=null&&!env.isBlank())return env.strip();
        if(windows()&&Files.exists(path(provider)))try{key=protect(Files.readString(path(provider)),false);SESSION.put(provider,key);return key;}catch(Exception ignored){}return "";}
    private static String protect(String value,boolean encrypt)throws Exception {
        String script="Add-Type -AssemblyName System.Security; $v=[Console]::In.ReadToEnd(); "+(encrypt?
            "$b=[Text.Encoding]::UTF8.GetBytes($v); [Console]::Out.Write([Convert]::ToBase64String([Security.Cryptography.ProtectedData]::Protect($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)))":
            "$b=[Convert]::FromBase64String($v); [Console]::Out.Write([Text.Encoding]::UTF8.GetString([Security.Cryptography.ProtectedData]::Unprotect($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser)))");
        Process p=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-Command",script).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {p.getOutputStream().write(value.getBytes(StandardCharsets.UTF_8));p.getOutputStream().close();if(!p.waitFor(8,TimeUnit.SECONDS))throw new IllegalStateException();if(p.exitValue()!=0)throw new IllegalStateException();return new String(p.getInputStream().readNBytes(16384),StandardCharsets.UTF_8).strip();}finally{p.destroyForcibly();}
    }
}

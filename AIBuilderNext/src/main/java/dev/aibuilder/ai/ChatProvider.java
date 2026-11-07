package dev.aibuilder.ai;
import com.google.gson.*;
import dev.aibuilder.config.BuilderConfig;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.io.ByteArrayOutputStream;

public final class ChatProvider {
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
    private volatile CompletableFuture<?> active;
    private volatile long retryAfter;
    public void cancel(){var f=active;if(f!=null)f.cancel(true);}
    public long cooldownSeconds(){return Math.max(0,(retryAfter-System.currentTimeMillis()+999)/1000);}
    public String request(BuilderConfig c,String key,String instructions,String prompt) {
        if(key.isBlank())throw new IllegalArgumentException("API-ключ не задан. Нажмите F8.");
        if(cooldownSeconds()>0)throw new IllegalArgumentException("Ограничение API: подождите "+cooldownSeconds()+" с.");
        JsonObject body=new JsonObject();body.addProperty("model",c.model);body.addProperty(c.provider.equals("openai")?"max_completion_tokens":"max_tokens",c.maxTokens);
        JsonArray messages=new JsonArray();messages.add(message("system",instructions));messages.add(message("user",prompt));body.add("messages",messages);
        HttpRequest req=HttpRequest.newBuilder(URI.create(c.base()+"/chat/completions")).timeout(Duration.ofSeconds(180)).header("Authorization","Bearer "+key).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        HttpResponse<String> response=send(req,180);
        requireSuccess(response);
        try {JsonObject r=JsonParser.parseString(response.body()).getAsJsonObject();JsonArray choices=r.getAsJsonArray("choices");if(choices==null||choices.isEmpty())throw new IllegalArgumentException("API вернул пустой список ответов.");JsonObject choice=choices.get(0).getAsJsonObject();String reason=choice.has("finish_reason")?choice.get("finish_reason").getAsString():"unknown";
            if(!reason.equals("stop"))throw new IllegalArgumentException("API не завершил план: finish_reason="+reason+". Автоматического повтора не будет.");
            JsonElement content=choice.getAsJsonObject("message").get("content");if(content==null||content.isJsonNull()||!content.isJsonPrimitive()||content.getAsString().isBlank())throw new IllegalArgumentException("API вернул пустой текст.");return content.getAsString();
        }catch(IllegalArgumentException ex){throw ex;}catch(Exception ex){throw new IllegalArgumentException("Некорректная структура ответа API.");}
    }
    public void test(BuilderConfig c,String key){if(key.isBlank())throw new IllegalArgumentException("API-ключ не задан.");var req=HttpRequest.newBuilder(URI.create(c.base()+"/models")).timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+key).GET().build();requireSuccess(send(req,30));}
    private HttpResponse<String> send(HttpRequest req,int seconds){var f=HTTP.sendAsync(req,info->new CappedBody());active=f;try{return f.get(seconds,TimeUnit.SECONDS);}catch(TimeoutException e){f.cancel(true);throw new IllegalArgumentException("API: истёк срок ожидания.");}catch(InterruptedException e){f.cancel(true);Thread.currentThread().interrupt();throw new IllegalArgumentException("Запрос отменён.");}catch(Exception e){throw new IllegalArgumentException("API: ошибка соединения или ответ превышает 2 МБ.");}finally{active=null;}}
    private void requireSuccess(HttpResponse<?> r){int status=r.statusCode();if(status>=200&&status<300)return;
        if(status==429){long sec=60;try{sec=Long.parseLong(r.headers().firstValue("Retry-After").orElse("60"));}catch(Exception ignored){}retryAfter=System.currentTimeMillis()+Math.max(1,Math.min(sec,3600))*1000;}
        throw new IllegalArgumentException(switch(status){case 400->"API 400: проверьте модель и лимит токенов.";case 401,403->"API: ключ отклонён или недостаточно прав.";case 402->"API: недостаточно средств.";case 404->"API: модель или endpoint не найдены.";case 429->"API: лимит запросов. Повтор доступен через "+cooldownSeconds()+" с.";default->"Ошибка API HTTP "+status;});}
    private static JsonObject message(String role,String content){JsonObject o=new JsonObject();o.addProperty("role",role);o.addProperty("content",content);return o;}
    static final class CappedBody implements HttpResponse.BodySubscriber<String> {
        private final CompletableFuture<String> body=new CompletableFuture<>();private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();private Flow.Subscription subscription;
        public CompletionStage<String> getBody(){return body;}
        public void onSubscribe(Flow.Subscription s){subscription=s;s.request(1);}
        public void onNext(List<ByteBuffer> data){try{for(var b:data){if((long)bytes.size()+b.remaining()>2_000_000)throw new IllegalArgumentException("Ответ слишком большой.");byte[] part=new byte[b.remaining()];b.get(part);bytes.write(part);}subscription.request(1);}catch(Exception e){subscription.cancel();body.completeExceptionally(e);}}
        public void onError(Throwable e){body.completeExceptionally(e);}public void onComplete(){body.complete(bytes.toString(StandardCharsets.UTF_8));}
    }
}

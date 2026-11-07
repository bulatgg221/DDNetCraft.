package dev.aibuilder.ai;
import dev.aibuilder.config.BuilderConfig;
import java.util.Locale;
public final class RetryPolicy {
    public static boolean canRetry(int retries,String prompt,BuilderConfig c){if(retries>=c.maxApiRetries)return false;String s=prompt.toLowerCase(Locale.ROOT);boolean art=s.contains("пиксель")||s.contains("pixel")||s.contains("портрет")||s.contains("нарисуй");return art?c.retryArtwork:c.retryMalformedJson;}
}

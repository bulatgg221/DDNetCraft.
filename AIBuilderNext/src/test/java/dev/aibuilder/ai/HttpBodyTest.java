package dev.aibuilder.ai;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HttpBodyTest {
    static class Sub implements Flow.Subscription {boolean cancelled;public void request(long n){}public void cancel(){cancelled=true;}}
    @Test void responseLimitCancelsSubscription(){var b=new ChatProvider.CappedBody();var s=new Sub();b.onSubscribe(s);b.onNext(List.of(ByteBuffer.allocate(2_000_001)));assertTrue(s.cancelled);assertTrue(b.getBody().toCompletableFuture().isCompletedExceptionally());}
    @Test void utf8AcrossBuffers(){var b=new ChatProvider.CappedBody();b.onSubscribe(new Sub());byte[] bytes="Привет".getBytes(java.nio.charset.StandardCharsets.UTF_8);b.onNext(List.of(ByteBuffer.wrap(bytes,0,1),ByteBuffer.wrap(bytes,1,bytes.length-1)));b.onComplete();assertEquals("Привет",b.getBody().toCompletableFuture().join());}
}

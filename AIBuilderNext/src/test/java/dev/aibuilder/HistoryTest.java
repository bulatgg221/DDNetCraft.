package dev.aibuilder;
import dev.aibuilder.world.TransactionHistory;
import dev.aibuilder.ai.RetryPolicy;
import dev.aibuilder.config.BuilderConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HistoryTest {
    @Test void undoRedoOnceAndAlternate(){var h=new TransactionHistory<String,Integer>();h.record("world",List.of(1,2));assertEquals(List.of(1,2),h.get("world",false));h.completed(false);assertThrows(IllegalArgumentException.class,()->h.get("world",false));assertEquals(List.of(1,2),h.get("world",true));h.completed(true);assertThrows(IllegalArgumentException.class,()->h.get("world",true));assertEquals(List.of(1,2),h.get("world",false));}
    @Test void cancelledPartialJobReplacesHistory(){var h=new TransactionHistory<String,Integer>();h.record("world",List.of(1,2));h.completed(false);h.record("world",List.of(3));assertThrows(IllegalArgumentException.class,()->h.get("world",true));assertEquals(List.of(3),h.get("world",false));}
    @Test void differentWorldAndUnloadCannotUseHistory(){var h=new TransactionHistory<String,Integer>();h.record("world1",List.of(1));assertThrows(IllegalArgumentException.class,()->h.get("world2",false));h.clear();assertThrows(IllegalArgumentException.class,()->h.get("world1",false));}
    @Test void paidRetriesRequireOptInAndAreBounded(){var c=new BuilderConfig();assertFalse(RetryPolicy.canRetry(0,"замок",c));c.maxApiRetries=1;c.retryMalformedJson=true;assertTrue(RetryPolicy.canRetry(0,"замок",c));assertFalse(RetryPolicy.canRetry(1,"замок",c));assertFalse(RetryPolicy.canRetry(0,"портрет",c));c.retryArtwork=true;assertTrue(RetryPolicy.canRetry(0,"портрет",c));}
}

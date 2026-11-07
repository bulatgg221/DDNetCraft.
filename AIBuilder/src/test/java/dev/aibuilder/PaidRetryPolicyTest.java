package dev.aibuilder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class PaidRetryPolicyTest {
    @Test void imageRequestsDoNotDoubleChargeWhenGenericRetryIsEnabled() {
        for(String prompt:new String[]{
            "нарисуй портрет человека 32x32",
            "сделай картину человека",
            "make a 32x32 pixel art face",
            "нарисуй мозаику солнца"}) {
            assertFalse(PaidRetryPolicy.shouldRetry(prompt,true,false),prompt);
        }
    }

    @Test void extraPaidArtworkAttemptRequiresBothExplicitSwitches() {
        String prompt="нарисуй пиксель-арт портрет";
        assertFalse(PaidRetryPolicy.shouldRetry(prompt,false,true));
        assertFalse(PaidRetryPolicy.shouldRetry(prompt,false,false));
        assertTrue(PaidRetryPolicy.shouldRetry(prompt,true,true));
    }

    @Test void architecturalRetryPolicyRemainsUnchanged() {
        assertTrue(PaidRetryPolicy.shouldRetry(
            "построй оригинальный готический замок",true,false));
        assertFalse(PaidRetryPolicy.shouldRetry(
            "построй оригинальный готический замок",false,false));
    }
}

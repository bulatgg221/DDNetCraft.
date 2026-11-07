package dev.aibuilder;

/**
 * Policy for potentially billable second model requests.
 * Image-only construction mistakes are repaired locally first. A second paid
 * artwork request requires explicit opt-in, independently from the generic
 * JSON retry setting retained for architecture plans.
 */
public final class PaidRetryPolicy {
    private PaidRetryPolicy() {}

    public static boolean shouldRetry(String prompt,boolean retryMalformedJson,
                                      boolean retryArtwork) {
        return retryMalformedJson && (!ArtworkMode.isArtwork(prompt)||retryArtwork);
    }
}

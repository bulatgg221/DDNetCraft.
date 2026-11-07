package dev.aibuilder;

/**
 * One source of truth for safe freeform operation limits.
 * Operation count is not block count: a detailed castle can need hundreds
 * of independent windows, columns and decorative elements while keeping
 * the original physical block limit unchanged.
 */
public final class BuildLimits {
    private BuildLimits() {}

    public static final int DEFAULT_OPERATIONS = 400;
    public static final int HARD_MAX_OPERATIONS = 512;
    public static final int LEGACY_DEFAULT_OPERATIONS = 100;

    /**
     * Old users already have maxOperations=100 in config.json.
     * Upgrade only the prior shipped default; keep user-set lower ceilings.
     * A marker is persisted so the user can subsequently opt back into 100.
     */
    public static int configuredOperations(int configured, boolean legacySettings) {
        if (legacySettings && configured == LEGACY_DEFAULT_OPERATIONS)
            return DEFAULT_OPERATIONS;
        return Math.clamp(configured, 1, HARD_MAX_OPERATIONS);
    }

    /** Upper bound on attempted placements, including repeated overwritten cells. */
    public static long maxPlacementAttempts(int maxBlocks) {
        return Math.max(20_000L, (long)Math.max(100,maxBlocks) * 12L);
    }
}

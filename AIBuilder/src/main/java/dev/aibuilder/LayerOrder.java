package dev.aibuilder;

/** Pure layout conflict policy, independent of Minecraft runtime state. */
public final class LayerOrder {
    private LayerOrder() {}

    /**
     * Named construction stages are a geometric priority, not the ordering
     * of JSON objects in the LLM response.
     */
    public static boolean mayReplace(int previousStage, boolean previousFunctional,
                                     boolean sameBlock, int newStage) {
        return newStage >= previousStage && (!previousFunctional || sameBlock);
    }
}

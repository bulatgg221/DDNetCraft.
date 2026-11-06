package dev.aibuilder;

/** Shared bounds for the local multiline prompt editor, server, and API layer. */
public final class PromptLimits {
    private PromptLimits() {}

    /** Long prompts are sent directly to the integrated server, not Minecraft chat. */
    public static final int MAX_CHARS = 12000;

    public static boolean valid(String prompt) {
        return prompt != null && !prompt.isBlank()
            && prompt.length() <= MAX_CHARS;
    }
}

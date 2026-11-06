package dev.aibuilder.client;

import dev.aibuilder.BuilderConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.io.IOException;

/** Local secret input. The token is masked and never submitted as a chat command. */
public final class ApiKeyScreen extends Screen {
    private TextFieldWidget secretInput;
    private String status = "";
    private int statusColor = 0xFFD0D0D0;
    private boolean saved;

    public ApiKeyScreen() {
        super(Text.literal("AI Builder — OpenAI API"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        secretInput = new TextFieldWidget(this.textRenderer, cx - 150, cy - 28, 300, 20,
            Text.literal("API key"));
        secretInput.setMaxLength(256);
        // The rendering callback receives only a visible slice of the input.
        secretInput.setRenderTextProvider((slice, cursor) ->
            OrderedText.styledForwardsVisitedString("•".repeat(slice.length()), Style.EMPTY));
        secretInput.setPlaceholder(Text.literal("Вставь ключ, начинающийся с sk-"));
        secretInput.setChangedListener(value -> {
            saved = false;
            status = "";
        });
        this.addDrawableChild(secretInput);
        this.setInitialFocus(secretInput);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить ключ"), button -> save())
            .dimensions(cx - 150, cy + 6, 145, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), button -> close())
            .dimensions(cx + 5, cy + 6, 145, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Удалить сохранённый ключ"), button -> remove())
            .dimensions(cx - 150, cy + 34, 300, 20).build());

        status = BuilderConfig.hasStoredKey()
            ? "Локальный API-ключ уже сохранён. Вставь другой для замены."
            : "Локальный API-ключ пока не найден.";
        statusColor = 0xFFD0D0D0;
    }

    private void save() {
        String raw = secretInput.getText();
        try {
            BuilderConfig.storeKey(raw);
            secretInput.setText("");
            saved = true;
            status = "Готово! Ключ сохранён в папку config/aibuilder.";
            statusColor = 0xFF6DF59A;
        } catch (Exception e) {
            // Never print the supplied token or exception details (could contain paths).
            status = "Не удалось сохранить. Проверь формат ключа и доступ к config.";
            statusColor = 0xFFFF8484;
        }
    }

    private void remove() {
        try {
            BuilderConfig.deleteStoredKey();
            secretInput.setText("");
            saved = false;
            status = "Локальный API-ключ удалён.";
            statusColor = 0xFF6DF59A;
        } catch (IOException e) {
            status = "Не удалось удалить файл с ключом.";
            statusColor = 0xFFFF8484;
        }
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

    @Override
    public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        super.render(draw, mouseX, mouseY, delta);
        int cx = this.width / 2;
        int cy = this.height / 2;
        draw.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("AI Builder — настройка OpenAI API"), cx, cy - 88, 0xFFFFFFFF);
        draw.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("Ключ вводится в окне, НЕ в чат Minecraft."), cx, cy - 70, 0xFFFFD38D);
        draw.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("Вставь ключ (Ctrl+V), затем нажми «Сохранить»."), cx, cy - 53, 0xFFE0E0E0);
        draw.drawCenteredTextWithShadow(this.textRenderer, Text.literal(status),
            cx, cy + 65, statusColor);
        draw.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("Для проверки: /ai build небольшой деревянный дом"), cx, cy + 84, 0xFFB5B5B5);
        if (saved) {
            draw.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("API оплачивается отдельно от ChatGPT."), cx, cy + 101, 0xFFB5B5B5);
        }
    }

    @Override
    public void close() {
        if (secretInput != null) secretInput.setText("");
        if (this.client != null) this.client.setScreen(null);
    }
}

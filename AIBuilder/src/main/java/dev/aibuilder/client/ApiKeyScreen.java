package dev.aibuilder.client;

import dev.aibuilder.AIBuilderMod;
import dev.aibuilder.BuilderConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.io.IOException;

/** Local GUI: selects API provider and model, saves secrets without putting them in chat. */
public final class ApiKeyScreen extends Screen {
    private TextFieldWidget secretInput;
    private TextFieldWidget modelInput;
    private ButtonWidget providerButton;
    private String selectedProvider;
    private String status = "";
    private int statusColor = 0xFFCCCCCC;

    public ApiKeyScreen() {
        super(Text.literal("AI Builder — API settings"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        BuilderConfig current = AIBuilderMod.config();
        selectedProvider = current.provider;
        if (!"openai".equals(selectedProvider) && !"polza".equals(selectedProvider))
            selectedProvider = "polza";

        providerButton = this.addDrawableChild(ButtonWidget.builder(
            Text.literal(providerTitle()), btn -> switchProvider()
        ).dimensions(cx - 150, cy - 85, 300, 20).build());

        // One-click model presets for Polza and official OpenAI.
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Эконом 4.1 mini"),
            btn -> chooseModel("gpt-4.1-mini"))
            .dimensions(cx - 150, cy - 62, 96, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Лучше 5.4 mini"),
            btn -> chooseModel("gpt-5.4-mini"))
            .dimensions(cx - 50, cy - 62, 96, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Точно 4.1"),
            btn -> chooseModel("gpt-4.1"))
            .dimensions(cx + 50, cy - 62, 100, 20).build());

        modelInput = new TextFieldWidget(this.textRenderer, cx - 150, cy - 35, 300, 20,
            Text.literal("Model ID"));
        modelInput.setMaxLength(100);
        modelInput.setText(current.model);
        this.addDrawableChild(modelInput);

        secretInput = new TextFieldWidget(this.textRenderer, cx - 150, cy + 10, 300, 20,
            Text.literal("API key"));
        secretInput.setMaxLength(256);
        // This is client-local editable text. Only bullets are rendered on screen.
        secretInput.setRenderTextProvider((slice, cursor) ->
            OrderedText.styledForwardsVisitedString("•".repeat(slice.length()), Style.EMPTY));
        secretInput.setPlaceholder(Text.literal("Вставь ключ выбранного провайдера (Ctrl+V)"));
        secretInput.setChangedListener(value -> status = "");
        this.addDrawableChild(secretInput);
        this.setInitialFocus(secretInput);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить ключ и настройки"),
            btn -> saveKey()).dimensions(cx - 150, cy + 39, 300, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Только настройки"),
            btn -> saveSettings()).dimensions(cx - 150, cy + 65, 145, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Удалить ключ"),
            btn -> remove()).dimensions(cx + 5, cy + 65, 145, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"),
            btn -> close()).dimensions(cx - 150, cy + 93, 300, 20).build());
        showStoredKeyStatus();
    }

    private void chooseModel(String model) {
        String fullId = "polza".equals(selectedProvider) ? "openai/" + model : model;
        modelInput.setText(fullId);
        status = "Выбрана модель " + fullId + ". Нажми «Только настройки», чтобы сохранить.";
        statusColor = 0xFFD0D0D0;
    }

    private String providerTitle() {
        return "Провайдер: " + BuilderConfig.providerName(selectedProvider) + "  (нажми для смены)";
    }
    private void switchProvider() {
        selectedProvider = "polza".equals(selectedProvider) ? "openai" : "polza";
        providerButton.setMessage(Text.literal(providerTitle()));
        modelInput.setText(BuilderConfig.defaultModel(selectedProvider));
        secretInput.setText("");
        showStoredKeyStatus();
    }
    private void showStoredKeyStatus() {
        status = BuilderConfig.hasStoredKey(selectedProvider)
            ? "Ключ выбранного сервиса сохранён. Можешь заменить его."
            : "Ключ для выбранного сервиса ещё не сохранён.";
        statusColor = 0xFFD0D0D0;
    }
    private boolean saveSettings() {
        try {
            BuilderConfig updated = BuilderConfig.saveProviderAndModel(selectedProvider, modelInput.getText().strip());
            AIBuilderMod.applyConfig(updated);
            status = "Настройки сохранены: " + BuilderConfig.providerName(selectedProvider)
                + " / " + updated.model;
            statusColor = 0xFF7CF9A6;
            return true;
        } catch (Exception e) {
            status = "Не удалось сохранить. Проверь идентификатор модели.";
            statusColor = 0xFFFF7777;
            return false;
        }
    }
    private void saveKey() {
        if (!saveSettings()) return;
        try {
            BuilderConfig.storeKey(selectedProvider, secretInput.getText());
            secretInput.setText("");
            status = "Ключ " + BuilderConfig.providerName(selectedProvider) + " сохранён локально!";
            statusColor = 0xFF7CF9A6;
        } catch (Exception e) {
            status = "Ошибка ключа: Polza — pza_…; OpenAI — sk-… (проверь формат).";
            statusColor = 0xFFFF7777;
        }
    }
    private void remove() {
        try {
            BuilderConfig.deleteStoredKey(selectedProvider);
            secretInput.setText("");
            status = "Локальный ключ " + BuilderConfig.providerName(selectedProvider) + " удалён.";
            statusColor = 0xFF7CF9A6;
        } catch (IOException e) {
            status = "Ошибка удаления файла ключа.";
            statusColor = 0xFFFF7777;
        }
    }

    @Override
    public boolean shouldPause() { return true; }

    @Override
    public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        super.render(draw, mouseX, mouseY, delta);
        int cx = this.width / 2, cy = this.height / 2;
        draw.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("AI Builder — сервис и API-ключ"), cx, cy - 115, 0xFFFFFFFF);
        draw.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("Ключ НЕ нужно писать в игровой чат!"), cx, cy - 100, 0xFFFFC879);
        draw.drawTextWithShadow(this.textRenderer, "ID модели:", cx - 150, cy - 48, 0xFFD9D9D9);
        draw.drawTextWithShadow(this.textRenderer, "API-ключ (скрытый ввод):",
            cx - 150, cy - 1, 0xFFD9D9D9);
        draw.drawCenteredTextWithShadow(this.textRenderer, Text.literal(status),
            cx, cy + 122, statusColor);
        draw.drawCenteredTextWithShadow(this.textRenderer,
            Text.literal("После сохранения: /ai build красивый деревянный дом"), cx,
            cy + 136, 0xFFBEBEBE);
    }

    @Override
    public void close() {
        if (secretInput != null) secretInput.setText("");
        if (this.client != null) this.client.setScreen(null);
    }
}

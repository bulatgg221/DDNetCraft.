package dev.aibuilder.client;

import dev.aibuilder.AIBuilderMod;
import dev.aibuilder.PromptLimits;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

/**
 * A native multiline prompt editor. Calls the integrated singleplayer server directly;
 * user prompts aren't constrained by chat command length or sent over modded networking.
 */
public final class PromptScreen extends Screen {
    private EditBoxWidget editor;
    private String warning = "";

    public PromptScreen() {
        super(Text.literal("AI Builder — Detailed Build Prompt"));
    }

    @Override
    protected void init() {
        int left = Math.max(12, (width - Math.min(520,width-24))/2);
        int panelWidth = Math.min(520,width-24);
        int boxHeight = Math.max(70,height-135);

        editor = new EditBoxWidget(textRenderer,left,39,panelWidth,boxHeight,
            Text.literal("Опиши здание: размеры, двери, окна, интерьер, крышу, участок"),
            Text.literal("Подробный промпт строительства"));
        editor.setMaxLength(PromptLimits.MAX_CHARS);
        editor.setText("");
        this.addDrawableChild(editor);
        this.setInitialFocus(editor);

        int buttonY = Math.min(height-52,39+boxHeight+9);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Построить по промпту"), b -> submit())
            .dimensions(left,buttonY,(panelWidth-8)/2,20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Отмена"), b -> close())
            .dimensions(left+(panelWidth+8)/2,buttonY,(panelWidth-8)/2,20).build());
    }

    private void submit() {
        if (client == null || client.player == null || client.getServer() == null) {
            warning = "Доступно только в одиночном мире.";
            return;
        }
        String description = editor.getText().strip();
        if (description.length() < 10) {
            warning = "Опиши постройку подробнее (от 10 символов).";
            return;
        }
        if (description.length() > PromptLimits.MAX_CHARS) {
            warning = "Лимит: " + PromptLimits.MAX_CHARS + " символов.";
            return;
        }
        MinecraftServer server = client.getServer();
        UUID playerId = client.player.getUuid();
        // Minecraft's integrated server owns the world and block placement.
        server.execute(() -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) AIBuilderMod.startBuild(player, description);
        });
        close();
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext draw,int mouseX,int mouseY,float delta) {
        super.render(draw,mouseX,mouseY,delta);
        draw.drawCenteredTextWithShadow(textRenderer,
            Text.literal("AI Builder — подробная постройка"),width/2,10,0xFFFFFFFF);
        draw.drawCenteredTextWithShadow(textRenderer,
            Text.literal("Двери, крыши, мебель, окна, лестницы — опиши всё здесь."),
            width/2,24,0xFFB4E5FF);
        int n = editor == null ? 0 : editor.getText().length();
        draw.drawCenteredTextWithShadow(textRenderer,
            Text.literal(n+"/"+PromptLimits.MAX_CHARS+" символов  |  Ctrl+V — вставить промпт"),
            width/2,height-27,0xFFC6C6C6);
        if(!warning.isBlank())draw.drawCenteredTextWithShadow(textRenderer,
            Text.literal(warning),width/2,height-14,0xFFFF6666);
    }
}

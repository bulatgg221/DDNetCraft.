package dev.aibuilder.client;

import dev.aibuilder.BuildService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;

/**
 * Actual GUI confirmation for the AI-authored read-only preview.
 * No world changes happen until the player explicitly clicks Build.
 */
public final class PreviewScreen extends Screen {
    public record Data(String name,String size,int blocks,int operations,
                       List<String> warnings,List<String> materials) {}
    private final Data preview;

    public PreviewScreen(Data preview) {
        super(Text.literal("AI Builder — Проверка чертежа"));
        this.preview=preview;
    }

    @Override
    protected void init(){
        int panelWidth=Math.min(500,width-32);
        int left=(width-panelWidth)/2;
        int bottom=Math.max(95,height-58);
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Подтвердить строительство"),button->finish(true))
            .dimensions(left,bottom,(panelWidth-12)/2,22).build());
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Отменить без строительства"),button->finish(false))
            .dimensions(left+(panelWidth+12)/2,bottom,(panelWidth-12)/2,22).build());
    }

    private void finish(boolean build){
        MinecraftClient client=MinecraftClient.getInstance();
        MinecraftServer server=client.getServer();
        if(server!=null && client.player!=null) {
            UUID id=client.player.getUuid();
            server.execute(()->{
                ServerPlayerEntity player=server.getPlayerManager().getPlayer(id);
                if(player!=null){
                    if(build)BuildService.INSTANCE.confirm(player);
                    else BuildService.INSTANCE.discard(player);
                }
            });
        }
        client.setScreen(null);
    }

    @Override
    public void close(){finish(false);}

    @Override
    public boolean shouldPause(){return false;}

    @Override
    public void render(DrawContext draw,int mouseX,int mouseY,float delta){
        super.render(draw,mouseX,mouseY,delta);
        int mid=width/2;
        int maxWidth=Math.min(510,width-36);
        draw.drawCenteredTextWithShadow(textRenderer,
            Text.literal("AI BUILDER • ПРЕДПРОСМОТР"),mid,18,0xA8E3FF);
        draw.drawCenteredTextWithShadow(textRenderer,
            Text.literal(preview.name()),mid,38,0xFFFFFF);
        draw.drawCenteredTextWithShadow(textRenderer,
            Text.literal(preview.size()+" блоков по осям • "
                +preview.blocks()+" размещений • "+preview.operations()+" операций"),
            mid,58,0xC6EAB0);

        int y=82;
        draw.drawCenteredTextWithShadow(textRenderer,
            Text.literal("Материалы: "+String.join(", ",preview.materials())),
            mid,y,0xB9B9B9);
        y+=22;
        if(preview.warnings().isEmpty()){
            draw.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Грубых проблем не обнаружено (это не визуальная проверка)."),
                mid,y,0xB7EAB9);
        }else{
            draw.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Предупреждения анализа: "+preview.warnings().size()),
                mid,y,0xF4D18B);
            y+=15;
            for(String warning:preview.warnings()){
                if(y>height-90)break;
                String truncated=warning.length()>90
                    ?warning.substring(0,87)+"...":warning;
                draw.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("• "+truncated),mid,y,0xE8B9B9);
                y+=13;
            }
        }
        draw.drawCenteredTextWithShadow(textRenderer,
            Text.literal("Мир пока НЕ изменён. Esc = отмена."),
            mid,height-72,0xB9D4F8);
    }
}

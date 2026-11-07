package dev.aibuilder.client;
import dev.aibuilder.AIBuilderMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;

public final class PromptScreen extends Screen {
    private EditBoxWidget editor;private static String draft="";private int mode=0,size=32,detail=1;
    private static final String[] MODES={"Авто","3D-постройка","Пиксель-арт","Из import.png"},DETAIL={"Низкая","Средняя","Высокая"};
    public PromptScreen(){super(Text.literal("AI Builder"));}
    protected void init(){int w=Math.min(640,width-24),left=(width-w)/2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Режим: "+MODES[mode]),b->{mode=(mode+1)%4;b.setMessage(Text.literal("Режим: "+MODES[mode]));}).dimensions(left,28,w/3-3,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Размер: "+size),b->{size=size==64?16:size+16;b.setMessage(Text.literal("Размер: "+size));}).dimensions(left+w/3,28,w/3-3,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Детали: "+DETAIL[detail]),b->{detail=(detail+1)%3;b.setMessage(Text.literal("Детали: "+DETAIL[detail]));}).dimensions(left+w*2/3,28,w/3,20).build());
        editor=addDrawableChild(new EditBoxWidget(textRenderer,left,54,w,Math.max(40,height-163),Text.literal("Опишите архитектуру, помещения, материалы, детали…"),Text.literal("Описание")));editor.setMaxLength(16000);editor.setText(draft);setInitialFocus(editor);
        int y=height-99;addDrawableChild(ButtonWidget.builder(Text.literal("Создать план (PNG — без API)"),b->{draft=editor.getText();String prompt=draft+"\nРежим: "+MODES[mode]+". Желаемый размер по горизонтали: "+size+". Детализация: "+DETAIL[detail];AIBuilderClient.server(p->{if(mode==3)AIBuilderMod.service.image(p,size,AIBuilderMod.config);else AIBuilderMod.service.generate(p,prompt,AIBuilderMod.config);});}).dimensions(left,y,w/2-3,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Предпросмотр"),b->{draft=editor.getText();if(AIBuilderMod.service.preview!=null)client.setScreen(new PreviewScreen());}).dimensions(left+w/2,y,w/2,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Остановить"),b->AIBuilderClient.server(p->AIBuilderMod.service.cancel())).dimensions(left,y+24,w/3-3,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Настройки F8"),b->{draft=editor.getText();client.setScreen(new SettingsScreen());}).dimensions(left+w/3,y+24,w/3-3,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"),b->close()).dimensions(left+w*2/3,y+24,w/3,20).build());}
    public void close(){if(editor!=null)draft=editor.getText();super.close();}
    public boolean shouldPause(){return false;}
    public void render(DrawContext c,int x,int y,float delta){super.render(c,x,y,delta);c.drawCenteredTextWithShadow(textRenderer,"AI Builder — новый архитектурный проект",width/2,10,0xffffff);c.drawTextWithShadow(textRenderer,Text.literal((editor==null?0:editor.getText().length())+" / 16000 · Ctrl+V"),14,height-48,0xaaaaaa);c.drawTextWrapped(textRenderer,Text.literal(AIBuilderMod.service.status),14,height-34,width-28,0xffd080);}
}

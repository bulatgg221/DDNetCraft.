package dev.aibuilder.client;
import dev.aibuilder.AIBuilderMod;
import dev.aibuilder.config.*;
import dev.aibuilder.ai.ChatProvider;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class SettingsScreen extends Screen {
    private BuilderConfig config;private TextFieldWidget model,key;private final Map<String,TextFieldWidget> numbers=new LinkedHashMap<>();private String message="Ключ скрыт. Пустое поле сохраняет ранее заданный ключ.";private boolean persist=false;
    public SettingsScreen(){super(Text.literal("API настройки"));config=AIBuilderMod.config.copy();}
    private TextFieldWidget field(int x,int y,int w,String value){var f=addDrawableChild(new TextFieldWidget(textRenderer,x,y,w,18,Text.empty()));f.setMaxLength(4096);f.setText(value);return f;}
    protected void init(){numbers.clear();int left=16,w=(width-40)/2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Провайдер: "+config.provider),b->{config.provider=config.provider.equals("polza")?"openai":"polza";b.setMessage(Text.literal("Провайдер: "+config.provider));model.setText(config.provider.equals("polza")?"openai/gpt-4.1":"gpt-4.1");key.setText("");}).dimensions(left,30,w,20).build());
        model=field(left+w+8,30,w,config.model);key=field(left,66,width-32,"");key.setMaxLength(4096);key.setRenderTextProvider((s,start)->Text.literal("•".repeat(s.length())).asOrderedText());
        String[] labels={"Операции","Блоки","Блоков/тик","Радиус XZ","Высота ±Y","Выходные токены"};int[] values={config.maxOperations,config.maxBlocks,config.blocksPerTick,config.horizontal,config.vertical,config.maxTokens};
        for(int i=0;i<labels.length;i++)numbers.put(labels[i],field(left+(i%2)*(w+8),106+(i/2)*34,w,""+values[i]));
        int y=height-76;addDrawableChild(ButtonWidget.builder(Text.literal("Хранение: сессия"),b->{persist=ApiKeyStorage.windows()&&!persist;b.setMessage(Text.literal(persist?"Хранение: Windows DPAPI":"Хранение: сессия"));}).dimensions(left,y,w,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Проверить API /models"),b->{try{read();String entered=key.getText().strip(),secret=entered.isBlank()?ApiKeyStorage.get(config.provider):entered;BuilderConfig snap=config.copy();message="Проверка соединения…";CompletableFuture.runAsync(()->{String result;try{new ChatProvider().test(snap,secret);result="Соединение установлено. Генерация выбранной моделью не проверялась.";}catch(Exception e){result=e.getMessage();}String finalResult=result;client.execute(()->message=finalResult);});}catch(Exception e){message=e.getMessage();}}).dimensions(left+w+8,y,w,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить"),b->{try{read();ApiKeyStorage.set(config.provider,key.getText(),persist);config.save();AIBuilderMod.config=config.copy();key.setText("");message="Сохранено. Автоматических платных повторов нет.";}catch(Exception e){message=e.getMessage();}}).dimensions(left,y+24,w,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Назад"),b->client.setScreen(new PromptScreen())).dimensions(left+w+8,y+24,w,20).build());}
    private void read(){config.model=model.getText().strip();try{int[] v=numbers.values().stream().mapToInt(f->Integer.parseInt(f.getText().strip())).toArray();config.maxOperations=v[0];config.maxBlocks=v[1];config.blocksPerTick=v[2];config.horizontal=v[3];config.vertical=v[4];config.maxTokens=v[5];}catch(Exception e){throw new IllegalArgumentException("Числовые настройки должны быть целыми.");}config.validate();}
    public boolean shouldPause(){return false;}
    public void render(DrawContext c,int x,int y,float delta){super.render(c,x,y,delta);c.drawCenteredTextWithShadow(textRenderer,"AI Builder — настройки API",width/2,10,0xffffff);c.drawTextWithShadow(textRenderer,"API-ключ (не отправляется в чат и не сохраняется в проект)",16,55,0xbbbbbb);int i=0,w=(width-40)/2;for(String name:numbers.keySet()){c.drawTextWithShadow(textRenderer,name,16+(i%2)*(w+8),96+(i/2)*34,0xbbbbbb);i++;}c.drawTextWrapped(textRenderer,Text.literal(message),16,height-26,width-32,0xffd080);}
}

package dev.aibuilder.client;
import dev.aibuilder.AIBuilderMod;
import dev.aibuilder.world.BuildService;
import dev.aibuilder.planning.Pos;
import dev.aibuilder.validation.SpatialValidator;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import java.util.*;

public final class PreviewScreen extends Screen {
    private final BuildService.Preview preview;private int level;private int minLevel,maxLevel;private EditBoxWidget report;
    public PreviewScreen(){super(Text.literal("Предпросмотр"));preview=AIBuilderMod.service.preview;}
    protected void init(){if(preview==null){addDrawableChild(ButtonWidget.builder(Text.literal("Нет плана · назад"),b->client.setScreen(new PromptScreen())).dimensions(10,30,220,20).build());return;}
        minLevel=preview.plan().voxels().keySet().stream().mapToInt(Pos::y).min().orElse(0);maxLevel=preview.plan().voxels().keySet().stream().mapToInt(Pos::y).max().orElse(0);level=Math.min(maxLevel,minLevel+1);
        int right=width/2+8;report=addDrawableChild(new EditBoxWidget(textRenderer,right,34,width-right-12,Math.max(40,height-100),Text.empty(),Text.literal("Отчёт")));
        Map<String,Long> counts=new TreeMap<>();preview.plan().voxels().values().forEach(v->counts.merge(v.block(),1L,Long::sum));
        String materials=counts.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(6).map(e->e.getKey()+": "+e.getValue()).reduce("",(a,b)->a+b+"\n");
        var positions=preview.plan().voxels().keySet();int dx=positions.stream().mapToInt(Pos::x).max().orElse(0)-positions.stream().mapToInt(Pos::x).min().orElse(0)+1;int dz=positions.stream().mapToInt(Pos::z).max().orElse(0)-positions.stream().mapToInt(Pos::z).min().orElse(0)+1;
        String summary=preview.plan().blueprint().name()+"\nРазмер: "+dx+" × "+(maxLevel-minLevel+1)+" × "+dz+"\nБлоков: "+positions.size()+"\nОпераций: "+preview.plan().blueprint().operations().size()+"\nПопыток: "+preview.plan().attempts()+"\nНачало: "+preview.origin().toShortString()+"\n\n"+materials+"\n"+String.join("\n",preview.report().warnings());report.setMaxLength(20000);report.setText(summary);
        addDrawableChild(ButtonWidget.builder(Text.literal("Слой −"),b->level=Math.max(minLevel,level-1)).dimensions(12,height-62,90,20).build());addDrawableChild(ButtonWidget.builder(Text.literal("Слой +"),b->level=Math.min(maxLevel,level+1)).dimensions(108,height-62,90,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Подтвердить строительство"),b->{AIBuilderClient.server(p->AIBuilderMod.service.confirm(p));client.setScreen(new PromptScreen());}).dimensions(width/2+8,height-62,width/2-20,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Назад"),b->client.setScreen(new PromptScreen())).dimensions(12,height-36,90,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Сохранить чертёж"),b->AIBuilderClient.server(p->{dev.aibuilder.planning.ProjectStore.save("project_"+System.currentTimeMillis(),preview.prompt(),preview.model(),preview.plan());AIBuilderMod.tell(p,"Чертёж сохранён; /ai history.");})).dimensions(width/2+8,height-36,width/2-20,20).build());}
    public boolean shouldPause(){return false;}
    public void render(DrawContext c,int mouseX,int mouseY,float delta){super.render(c,mouseX,mouseY,delta);c.drawCenteredTextWithShadow(textRenderer,"Схема слоя сверху · это не 3D-рендер",width/2,12,0xffffff);if(preview==null)return;
        var vs=preview.plan().voxels();int minX=vs.keySet().stream().mapToInt(Pos::x).min().orElse(0),maxX=vs.keySet().stream().mapToInt(Pos::x).max().orElse(0),minZ=vs.keySet().stream().mapToInt(Pos::z).min().orElse(0),maxZ=vs.keySet().stream().mapToInt(Pos::z).max().orElse(0);
        float scale=Math.max(0.5f,Math.min((width/2f-24)/(maxX-minX+1),(height-120f)/(maxZ-minZ+1)));c.fill(12,34,width/2-8,height-78,0xff101827);
        vs.forEach((p,v)->{if(p.y()!=level||SpatialValidator.passable(v))return;int x=12+(int)((p.x()-minX)*scale),z=34+(int)((p.z()-minZ)*scale);int color=0xff000000|((v.block().hashCode()&0x7f7f7f)|0x606060);c.fill(x,z,x+Math.max(1,(int)scale),z+Math.max(1,(int)scale),color);});
        c.drawTextWithShadow(textRenderer,"Y = "+level+" · цвета условные",12,height-75,0xffffff);}
}

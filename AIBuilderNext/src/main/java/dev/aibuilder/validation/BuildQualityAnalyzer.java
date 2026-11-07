package dev.aibuilder.validation;
import dev.aibuilder.geometry.*;
import dev.aibuilder.planning.*;
import java.util.*;
import java.util.regex.*;
import static dev.aibuilder.planning.PlanParser.error;

/** Measurable checks are errors; architectural aesthetics remain labelled warnings. */
public final class BuildQualityAnalyzer {
    private static final Map<String,Integer> WORD_NUMBERS=Map.ofEntries(Map.entry("одна",1),Map.entry("одной",1),Map.entry("один",1),Map.entry("две",2),Map.entry("двумя",2),Map.entry("два",2),Map.entry("три",3),Map.entry("тремя",3),Map.entry("четыре",4),Map.entry("четырьмя",4),Map.entry("пять",5),Map.entry("пятью",5),Map.entry("шесть",6),Map.entry("шестью",6),Map.entry("семь",7),Map.entry("восемь",8),Map.entry("девять",9),Map.entry("десять",10));
    public static List<String> analyze(String prompt,PlacementPlan plan){List<String> warnings=new ArrayList<>();if(plan.blueprint().mode().equals("pixel_art")){var v=plan.voxels();Map<String,Integer> count=new HashMap<>();v.values().forEach(b->count.merge(b.block(),1,Integer::sum));int max=count.values().stream().mapToInt(i->i).max().orElse(0);if((double)max/v.size()>0.9)warnings.add("Эвристика рисунка: более 90% клеток одного цвета.");if(prompt.toLowerCase(Locale.ROOT).matches("(?s).*(портрет|лицо|portrait).*"))warnings.add("Сходство и анатомическая точность портрета не проверены.");return warnings;}
        String lower=prompt.toLowerCase(Locale.ROOT);Matcher tower=Pattern.compile("(\\d+|"+String.join("|",WORD_NUMBERS.keySet())+")\\s+(?:разн\\S*\\s+)?баш(?:ен|ни|н(?:ями|ю|ей))").matcher(lower);
        if(tower.find()){String n=tower.group(1);int required=WORD_NUMBERS.containsKey(n)?WORD_NUMBERS.get(n):Integer.parseInt(n);long actual=plan.blueprint().components().stream().filter(c->c.kind().equals("tower")).count();if(actual!=required)throw error("Запрошено башен: "+required+", в проекте: "+actual);}
        if(lower.contains("бассейн")&&plan.blueprint().components().stream().noneMatch(c->c.kind().equals("pool")))throw error("В запросе есть бассейн, но в плане его нет.");
        Map<String,Long> sizes=new HashMap<>();plan.voxels().values().stream().filter(SpatialValidator::support).forEach(v->sizes.merge(v.component(),1L,Long::sum));long total=sizes.values().stream().mapToLong(i->i).sum();
        if(total>0&&sizes.values().stream().mapToLong(i->i).max().orElse(0)>total*0.9&&!lower.contains("минимал"))warnings.add("Эвристика: более 90% твёрдых блоков относятся к одному компоненту.");
        for(var c:plan.blueprint().components())if(c.kind().equals("roof")){boolean overBuilding=plan.blueprint().components().stream().anyMatch(o->Set.of("room","hall","tower").contains(o.kind())&&c.bounds().y()>=o.bounds().y()+o.bounds().h()-1&&overlaps(c.bounds(),o.bounds()));if(!overBuilding)throw error("Крыша не находится над помещением: "+c.id());}
        var towers=plan.blueprint().components().stream().filter(c->c.kind().equals("tower")).toList();if(lower.contains("разной высоты")&&towers.size()>1&&towers.stream().map(c->c.bounds().h()).distinct().count()==1)throw error("Запрошены башни разной высоты, но их высоты одинаковы.");
        warnings.add("Количество этажей и стиль требуют проверки схемы; полное смысловое соответствие не гарантируется.");return List.copyOf(warnings);
    }
    private static boolean overlaps(Blueprint.Bounds a,Blueprint.Bounds b){return a.x()<b.x()+b.w()&&a.x()+a.w()>b.x()&&a.z()<b.z()+b.d()&&a.z()+a.d()>b.z();}
}

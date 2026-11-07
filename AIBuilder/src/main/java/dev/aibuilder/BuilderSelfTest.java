package dev.aibuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game read-only QA. Runs under Fabric runtime where access wideners are
 * active, so actual block registry AND block-state operations can be tested.
 * Nothing is placed in the world. No API calls or API key access.
 */
public final class BuilderSelfTest {
    private BuilderSelfTest() {}

    public record Result(int paletteChecked,int scenariosChecked,List<String> failures){
        public boolean success(){return failures.isEmpty();}
    }

    public static Result run(BuilderConfig config){
        List<String> failures=new ArrayList<>();
        int checked=0;
        for(String name:MaterialCatalog.allowedIds()){
            checked++;
            Identifier id=Identifier.of("minecraft",name);
            var registered=Registries.BLOCK.getOrEmpty(id);
            if(registered.isEmpty()) {
                failures.add("Palette ID missing from runtime Minecraft registry: "+name);
                continue;
            }
            if(name.equals("air")?registered.get()!=Blocks.AIR:
                    registered.get()==Blocks.AIR) {
                failures.add("Palette ID resolved to the wrong block: "+name);
                continue;
            }
            try {
                // Unlike an ID-existence check, this invokes the real runtime
                // geometry compiler and block-state defaults for EVERY palette ID.
                JsonObject testOp=new JsonObject();
                testOp.addProperty("type","block");
                testOp.addProperty("block",name);
                testOp.addProperty("x",6);testOp.addProperty("y",0);testOp.addProperty("z",6);
                JsonObject testPlan=new JsonObject();
                testPlan.addProperty("name","Material QA "+name);
                JsonArray testOps=new JsonArray();testOps.add(testOp);
                testPlan.add("operations",testOps);
                int count=PlanCompiler.compile(testPlan.toString(),
                    new BlockPos(0,100,0),config).blocks().size();
                int expected=name.endsWith("_bed")
                    || (name.endsWith("_door")&&!name.endsWith("_trapdoor"))
                    || List.of("tall_grass","large_fern","sunflower",
                               "rose_bush","peony","lilac").contains(name)?2:1;
                if(count!=expected)failures.add(
                    "Wrong block-state layout "+name+": "+count+" != "+expected);
            }catch(Exception ex){
                failures.add("Cannot compile "+name+": "+ex.getMessage());
            }
        }

        int scenarios=0;
        BlockPos origin=new BlockPos(0,100,0);
        for(Scenario scenario: scenarios()) {
            scenarios++;
            try{
                JsonObject plan=new JsonObject();
                plan.addProperty("name","QA "+scenario.name());
                JsonArray ops=new JsonArray();ops.add(scenario.op());
                plan.add("operations",ops);
                PlanCompiler.CompiledPlan result=PlanCompiler.compile(
                    plan.toString(),origin,config);
                if(result.blocks().size()!=scenario.expectedBlocks())
                    failures.add(scenario.name()+": expected "
                        +scenario.expectedBlocks()+" blocks, got "+result.blocks().size());
            }catch(Exception ex){
                failures.add(scenario.name()+": "+ex.getMessage());
            }
        }
        // A complete 400-operation freeform design should pass the same
        // parser/compiler path used by F7, without placing anything in-game.
        scenarios++;
        try {
            int requested=Math.min(config.maxOperations,BuildLimits.DEFAULT_OPERATIONS);
            JsonObject large=new JsonObject();
            large.addProperty("name","Large freeform operation limit QA");
            JsonArray ops=new JsonArray();
            for(int i=0;i<requested;i++){
                JsonObject op=new JsonObject();
                op.addProperty("type","block");
                op.addProperty("stage","details");
                op.addProperty("block",i%2==0?"stone_bricks":"purple_stained_glass_pane");
                op.addProperty("x",6+i%20);
                op.addProperty("y",0);
                op.addProperty("z",6+i/20);
                ops.add(op);
            }
            large.add("operations",ops);
            String validated=PlanResponseParser.normalize(large.toString());
            PlanCompiler.CompiledPlan result=PlanCompiler.compile(
                validated,origin,config);
            if(result.operations()!=requested || result.blocks().size()!=requested)
                failures.add("Long plan QA: expected "+requested+
                    " operations/blocks, got "+result.operations()+"/"+result.blocks().size());
        }catch(Exception ex){
            failures.add("Large freeform plan (100+ operations): "+ex.getMessage());
        }
        return new Result(checked,scenarios,List.copyOf(failures));
    }

    private record Scenario(String name, JsonObject op, int expectedBlocks){}

    private static List<Scenario> scenarios(){
        List<Scenario> list=new ArrayList<>();
        list.add(s("purple stained glass pane","block","purple_stained_glass_pane",1));
        list.add(s("purple stained glass","block","purple_stained_glass",1));
        list.add(s("cyan stained glass pane","block","cyan_stained_glass_pane",1));
        list.add(s("oak_planks tabletop","table","oak_planks",10,3,1,2));
        list.add(s("spruce_planks chair","chair","spruce_planks",1));
        list.add(s("planks as two-block door","door","spruce_planks",2));
        list.add(s("wool as two-block bed","bed","purple_wool",2));
        list.add(s("colored door as raw block","block","cherry_door",2));
        list.add(s("colored bed as raw block","block","blue_bed",2));
        list.add(s("rose_bush with both halves","block","rose_bush",2));
        list.add(s("tall_grass with both halves","block","tall_grass",2));
        list.add(s("spruce_planks roof stairs","roof_stairs","spruce_planks",5,5,1,1));
        list.add(s("oak_planks hip roof","hip_roof","oak_planks",25,5,1,5));
        list.add(s("stairs_run planks","stairs_run","oak_planks",5,1,5,1));
        list.add(s("straight wall","box","stone_bricks",36,6,3,2));
        list.add(s("hollow room","hollow_box","stone_bricks",54,5,3,4));
        list.add(s("round stone tower","hollow_cylinder","deepslate_bricks",100,1,5,1));
        list.add(s("solid watch tower base","cylinder","stone_bricks",65,1,5,1));
        list.add(s("archway","arch","stone_bricks",13,5,5,1));
        list.add(s("gable facade","gable_fill","stone_bricks",16,7,1,1));
        list.add(s("small sandstone sphere","sphere","sandstone",33,1,1,1));
        // A synthetic 32x32 color matrix tests all 1024 pixel placements.
        // It is for geometry QA only: NOT a fixed face or generated design.
        JsonObject art=new JsonObject();
        art.addProperty("type","pixel_art");
        art.addProperty("stage","details");
        art.addProperty("x",32);art.addProperty("y",0);art.addProperty("z",8);
        art.addProperty("facing","north");
        JsonObject artPalette=new JsonObject();
        String[] colors={"black_concrete","gray_concrete","light_gray_concrete",
            "white_concrete","brown_terracotta","orange_terracotta",
            "red_terracotta","yellow_terracotta","pink_concrete",
            "blue_concrete","cyan_concrete","purple_concrete"};
        for(int i=0;i<colors.length;i++)
            artPalette.addProperty(""+(char)('A'+i),colors[i]);
        art.add("palette",artPalette);
        JsonArray artRows=new JsonArray();
        for(int row=0;row<32;row++){
            StringBuilder pixels=new StringBuilder();
            for(int col=0;col<32;col++)
                pixels.append((char)('A'+(col/2+row/3)%colors.length));
            artRows.add(pixels.toString());
        }
        art.add("rows",artRows);
        list.add(new Scenario("32x32 original-color-grid renderer",art,1024));
        return list;
    }

    private static Scenario s(String label,String type,String block,int blocks){
        return s(label,type,block,blocks,1,1,1);
    }

    private static Scenario s(String label,String type,String block,int expected,
                              int w,int h,int d){
        JsonObject op=new JsonObject();
        op.addProperty("type",type);op.addProperty("block",block);
        op.addProperty("x",6);op.addProperty("y",0);op.addProperty("z",6);
        op.addProperty("w",w);op.addProperty("h",h);op.addProperty("d",d);
        if(type.equals("cylinder")||type.equals("hollow_cylinder")||type.equals("sphere"))
            op.addProperty("r",type.equals("cylinder")?2:type.equals("sphere")?2:3);
        if(type.equals("stairs_run"))op.addProperty("facing","north");
        if(type.equals("door")||type.equals("bed"))op.addProperty("facing","north");
        return new Scenario(label,op,expected);
    }
}

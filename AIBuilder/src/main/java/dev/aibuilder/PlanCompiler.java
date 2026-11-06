package dev.aibuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates untrusted model JSON, expands architecture primitives and orders placements. */
public final class PlanCompiler {
    private PlanCompiler() {}

    public static final Set<String> MATERIALS = Set.of((
        "stone_bricks mossy_stone_bricks cracked_stone_bricks chiseled_stone_bricks cobblestone " +
        "mossy_cobblestone stone smooth_stone andesite polished_andesite deepslate_bricks " +
        "polished_deepslate calcite tuff tuff_bricks oak_planks spruce_planks dark_oak_planks " +
        "birch_planks stripped_oak_log oak_log spruce_log dark_oak_log oak_leaves spruce_leaves " +
        "birch_leaves bricks mud_bricks terracotta white_terracotta orange_terracotta red_terracotta " +
        "white_concrete gray_concrete light_gray_concrete black_concrete blue_concrete cyan_concrete " +
        "red_concrete green_concrete yellow_concrete quartz_block smooth_quartz sandstone smooth_sandstone " +
        "red_sandstone prismarine_bricks dark_prismarine sea_lantern glowstone glass white_stained_glass " +
        "blue_stained_glass light_blue_stained_glass purple_stained_glass iron_bars gold_block emerald_block " +
        "diamond_block grass_block dirt coarse_dirt gravel sand red_sand clay snow_block packed_ice " +
        "blue_ice obsidian blackstone polished_blackstone_bricks nether_bricks red_nether_bricks " +
        "warped_planks crimson_planks water air " +
        "oak_door spruce_door birch_door dark_oak_door iron_door cherry_door warped_door crimson_door " +
        "oak_stairs spruce_stairs dark_oak_stairs birch_stairs stone_brick_stairs cobblestone_stairs " +
        "deepslate_brick_stairs brick_stairs quartz_stairs sandstone_stairs polished_andesite_stairs " +
        "oak_slab spruce_slab dark_oak_slab birch_slab stone_brick_slab smooth_stone_slab " +
        "brick_slab quartz_slab sandstone_slab deepslate_brick_slab " +
        "oak_fence spruce_fence dark_oak_fence birch_fence oak_fence_gate spruce_fence_gate " +
        "oak_trapdoor spruce_trapdoor dark_oak_trapdoor birch_trapdoor iron_trapdoor " +
        "glass_pane white_stained_glass_pane blue_stained_glass_pane light_blue_stained_glass_pane " +
        "brown_stained_glass_pane black_stained_glass_pane " +
        "stone_brick_wall cobblestone_wall deepslate_brick_wall " +
        "lantern soul_lantern chain torch wall_torch redstone_lamp bookshelf crafting_table " +
        "flower_pot potted_poppy potted_dandelion potted_blue_orchid " +
        "poppy dandelion blue_orchid rose_bush peony lilac sunflower short_grass fern " +
        "red_carpet white_carpet blue_carpet green_carpet brown_carpet " +
        "red_bed white_bed blue_bed green_bed black_bed yellow_bed").split(" "));

    public record Placement(BlockPos pos, BlockState state) {}
    public record CompiledPlan(String name, List<Placement> blocks, int operations) {}
    private record Staged(BlockPos pos, BlockState state, int stage) {}

    public static CompiledPlan compile(String json, BlockPos origin, BuilderConfig cfg) {
        JsonElement rootElement = JsonParser.parseString(json);
        if (!rootElement.isJsonObject()) throw new IllegalArgumentException("План должен быть JSON-объектом.");
        JsonObject root = rootElement.getAsJsonObject();
        String name = string(root, "name", "AI Building");
        if (name.length() > 80) name = name.substring(0,80);
        JsonArray ops = root.getAsJsonArray("operations");
        if (ops == null || ops.isEmpty() || ops.size() > cfg.maxOperations)
            throw new IllegalArgumentException("Слишком много операций: макс. " + cfg.maxOperations);
        Map<BlockPos, Staged> blocks = new LinkedHashMap<>();
        for (JsonElement entry : ops) {
            if (!entry.isJsonObject()) throw new IllegalArgumentException("Каждая операция должна быть объектом.");
            JsonObject op = entry.getAsJsonObject();
            OperationTypes.Resolved resolved = OperationTypes.resolve(
                string(op,"type","box"), string(op,"block",null), MATERIALS);
            String type = resolved.type();
            int x = number(op,"x",0), y = number(op,"y",0), z = number(op,"z",0);
            int w = number(op,"w",1), h = number(op,"h",1), d = number(op,"d",1);
            int r = number(op,"r",2);
            if (w < 1 || w > 80 || d < 1 || d > 80 || h < 1 || h > 48 || r < 1 || r > 20)
                throw new IllegalArgumentException("Недопустимый размер операции.");
            if (Math.abs(x)>96 || Math.abs(z)>96 || Math.abs(y)>80)
                throw new IllegalArgumentException("Смещение операции слишком велико.");
            int stage = stage(op, type);
            BlockState material = block(resolved.block() == null
                ? fallback(type) : resolved.block());
            switch(type) {
                case "box", "hollow_box" -> {
                    material = applyProperties(material,op);
                    for(int dx=0;dx<w;dx++) for(int dy=0;dy<h;dy++) for(int dz=0;dz<d;dz++)
                        if(type.equals("box") || dx==0 || dx==w-1 || dy==0 || dy==h-1 || dz==0 || dz==d-1)
                            add(blocks,origin,cfg,x+dx,y+dy,z+dz,material,stage);
                }
                case "cylinder","hollow_cylinder" -> {
                    material=applyProperties(material,op);
                    for(int dx=-r;dx<=r;dx++) for(int dz=-r;dz<=r;dz++){
                        int ds=dx*dx+dz*dz;
                        if(ds>r*r || (type.equals("hollow_cylinder") && ds<(r-1)*(r-1)))continue;
                        for(int dy=0;dy<h;dy++)add(blocks,origin,cfg,x+dx,y+dy,z+dz,material,stage);
                    }
                }
                case "sphere" -> {
                    material=applyProperties(material,op);
                    for(int dx=-r;dx<=r;dx++)for(int dy=-r;dy<=r;dy++)for(int dz=-r;dz<=r;dz++)
                        if(dx*dx+dy*dy+dz*dz<=r*r)add(blocks,origin,cfg,x+dx,y+dy,z+dz,material,stage);
                }
                case "pyramid" -> {
                    for(int dy=0;dy<h;dy++){
                        int sx=Math.min(dy,(w-1)/2),sz=Math.min(dy,(d-1)/2);
                        for(int dx=sx;dx<w-sx;dx++)for(int dz=sz;dz<d-sz;dz++)
                            add(blocks,origin,cfg,x+dx,y+dy,z+dz,material,stage);
                    }
                }
                case "gable_roof" -> {
                    for(int dx=0;dx<w;dx++)for(int dz=0;dz<d;dz++)
                        add(blocks,origin,cfg,x+dx,y+Math.min(dx,w-1-dx),z+dz,material,stage);
                }
                case "roof_stairs" -> {
                    if(!material.getBlock().getTranslationKey().contains("stairs"))
                        throw new IllegalArgumentException("roof_stairs требует блок *_stairs");
                    for(int dx=0;dx<w;dx++){
                        int rise=Math.min(dx,w-1-dx);
                        String facing=dx<w/2?"east":"west";
                        BlockState stair=setProperty(material,"facing",facing);
                        for(int dz=0;dz<d;dz++)add(blocks,origin,cfg,x+dx,y+rise,z+dz,stair,stage);
                    }
                }
                case "line" -> {
                    material=applyProperties(material,op);
                    int x2=number(op,"x2",x),y2=number(op,"y2",y),z2=number(op,"z2",z);
                    int n=Math.max(1,Math.max(Math.abs(x2-x),Math.max(Math.abs(y2-y),Math.abs(z2-z))));
                    if(n>120)throw new IllegalArgumentException("Слишком длинная линия.");
                    for(int i=0;i<=n;i++){
                        double t=i/(double)n;
                        add(blocks,origin,cfg,(int)Math.round(x+(x2-x)*t),
                            (int)Math.round(y+(y2-y)*t),(int)Math.round(z+(z2-z)*t),material,stage);
                    }
                }
                case "arch" -> {
                    if(w<3||h<3)throw new IllegalArgumentException("Арка требует w и h >= 3");
                    for(int dy=0;dy<h;dy++)for(int dz=0;dz<d;dz++){
                        add(blocks,origin,cfg,x,y+dy,z+dz,material,stage);
                        add(blocks,origin,cfg,x+w-1,y+dy,z+dz,material,stage);
                    }
                    for(int dx=0;dx<w;dx++)for(int dz=0;dz<d;dz++)
                        add(blocks,origin,cfg,x+dx,y+h-1,z+dz,material,stage);
                }
                case "tree" -> {
                    if(h<3||h>14||r>5)throw new IllegalArgumentException("Дерево: h 3–14, r <=5.");
                    BlockState leaves=block(string(op,"leaves","oak_leaves"));
                    for(int dx=-r;dx<=r;dx++)for(int dy=-r;dy<=r;dy++)for(int dz=-r;dz<=r;dz++)
                        if(dx*dx+dy*dy+dz*dz<=r*r && dy>=-r+1)
                            add(blocks,origin,cfg,x+dx,y+h-1+dy,z+dz,leaves,stage);
                    BlockState log=block(string(op,"block","oak_log"));
                    for(int dy=0;dy<h;dy++)add(blocks,origin,cfg,x,y+dy,z,log,stage);
                }
                case "block","chair" -> {
                    BlockState state=type.equals("chair")
                        ? block(string(op,"block","oak_stairs")) : material;
                    state=applyProperties(state,op);
                    if(type.equals("chair"))state=setProperty(state,"facing",string(op,"facing","north"));
                    if(type.equals("block") && (isDoor(state)||isBed(state)))
                        throw new IllegalArgumentException("Для дверей и кроватей используй type=door/bed.");
                    add(blocks,origin,cfg,x,y,z,state,stage);
                }
                case "door" -> {
                    if(!isDoor(material))throw new IllegalArgumentException("door требует материал *_door.");
                    BlockState bottom=setProperty(material,"facing",string(op,"facing","north"));
                    bottom=setProperty(bottom,"hinge",string(op,"hinge","left"));
                    bottom=setProperty(bottom,"half","lower");
                    bottom=setProperty(bottom,"open","false");
                    BlockState top=setProperty(bottom,"half","upper");
                    add(blocks,origin,cfg,x,y,z,bottom,stage);
                    add(blocks,origin,cfg,x,y+1,z,top,stage);
                }
                case "bed" -> {
                    if(!isBed(material))throw new IllegalArgumentException("bed требует материал *_bed.");
                    String dir=string(op,"facing","north");
                    BlockState foot=setProperty(setProperty(material,"facing",dir),"part","foot");
                    BlockState head=setProperty(foot,"part","head");
                    Direction facing=Direction.byName(dir);
                    if(facing==null||!facing.getAxis().isHorizontal())throw new IllegalArgumentException("Направление bed: north/south/east/west.");
                    add(blocks,origin,cfg,x,y,z,foot,stage);
                    add(blocks,origin,cfg,x+facing.getOffsetX(),y,z+facing.getOffsetZ(),head,stage);
                }
                case "table" -> {
                    if(w>8||d>6)throw new IllegalArgumentException("Стол максимум 8x6.");
                    BlockState legs=block(string(op,"leg","oak_fence"));
                    BlockState top=block(string(op,"block","oak_slab"));
                    top=setProperty(top,"type","top");
                    for(int dx=0;dx<w;dx++)for(int dz=0;dz<d;dz++)
                        add(blocks,origin,cfg,x+dx,y+1,z+dz,top,stage);
                    for(int dx: new int[]{0,w-1})for(int dz: new int[]{0,d-1})
                        add(blocks,origin,cfg,x+dx,y,z+dz,legs,stage);
                }
                case "stairs_run" -> {
                    String dir=string(op,"facing","north");
                    Direction facing=Direction.byName(dir);
                    if(facing==null||!facing.getAxis().isHorizontal())throw new IllegalArgumentException("stairs_run: неверный facing");
                    BlockState stair=setProperty(material,"facing",dir);
                    if(!material.getBlock().getTranslationKey().contains("stairs"))
                        throw new IllegalArgumentException("stairs_run требует *_stairs.");
                    if(h>20)throw new IllegalArgumentException("Лестничный марш максимум 20 ступеней.");
                    for(int i=0;i<h;i++)for(int side=0;side<w;side++){
                        int ox=facing.getOffsetZ()!=0?side:0;
                        int oz=facing.getOffsetX()!=0?side:0;
                        add(blocks,origin,cfg,x+i*facing.getOffsetX()+ox,y+i,z+i*facing.getOffsetZ()+oz,stair,stage);
                    }
                }
                default -> throw new IllegalArgumentException("Неизвестная форма: "+type);
            }
        }
        if(blocks.isEmpty())throw new IllegalArgumentException("Пустой план.");
        List<Staged> sequence=new ArrayList<>(blocks.values());
        sequence.sort(Comparator.comparingInt(Staged::stage).thenComparingInt(s->s.pos().getY()));
        List<Placement> result=new ArrayList<>(sequence.size());
        for(Staged state:sequence)result.add(new Placement(state.pos(),state.state()));
        return new CompiledPlan(name,result,ops.size());
    }

    private static String fallback(String type){
        return switch(type){
            case "door"->"oak_door";
            case "bed"->"red_bed";
            case "chair","roof_stairs","stairs_run"->"oak_stairs";
            case "table"->"oak_slab";
            case "tree"->"oak_log";
            default ->"stone_bricks";
        };
    }
    private static int stage(JsonObject op,String type){
        String name=string(op,"stage",switch(type){
            case "roof_stairs","gable_roof"->"roof";
            case "door","block","line","arch"->"details";
            case "bed","table","chair","stairs_run"->"interior";
            case "tree"->"landscape";
            default ->"walls";
        });
        return switch(name){
            case "foundation"->0; case "walls","shell"->1; case "roof"->2;
            case "openings"->3; case "details"->4; case "interior"->5;
            case "landscape"->6;
            default ->throw new IllegalArgumentException("Неизвестный этап: "+name);
        };
    }
    private static boolean isDoor(BlockState b){
        return Registries.BLOCK.getId(b.getBlock()).getPath().endsWith("_door");
    }
    private static boolean isBed(BlockState b){
        return Registries.BLOCK.getId(b.getBlock()).getPath().endsWith("_bed");
    }
    private static BlockState block(String material){
        String name=MaterialAliases.resolve(material,MATERIALS);
        if (!name.equals(material) && !("minecraft:" + name).equals(material)) {
            AIBuilderMod.LOG.info("AI Builder: corrected generated block '{}' to '{}'", material, name);
        }
        Block block=Registries.BLOCK.get(Identifier.of("minecraft",name));
        BlockState state=block.getDefaultState();
        if(block instanceof LeavesBlock)state=state.with(LeavesBlock.PERSISTENT,true);
        return state;
    }
    /**
     * Model-provided decorative block properties are optional hints, not requirements.
     * Ignore hints unsupported by the actual Minecraft block state, such as
     * axis=x on smooth_stone_slab, instead of rejecting the entire plan.
     *
     * Internally required properties (door halves, bed parts, roof stair facing)
     * continue to use the strict setProperty() below so invalid structures fail.
     */
    private static BlockState applyProperties(BlockState state, JsonObject op){
        Set<String> available = new HashSet<>();
        for (Property<?> property : state.getProperties()) available.add(property.getName());
        for (String key : List.of("facing","half","hinge","open","axis","shape",
                                  "waterlogged","slab_type","hanging")) {
            if (!op.has(key) || op.get(key).isJsonNull()) continue;
            String propertyName = BlockStateHints.compatibleProperty(key, available);
            if (propertyName == null) {
                AIBuilderMod.LOG.debug("Ignoring inapplicable block state property {} for {}",
                    key, Registries.BLOCK.getId(state.getBlock()));
                continue;
            }
            String value = op.get(key).getAsString();
            for (Property<?> property : state.getProperties()) {
                if (!property.getName().equals(propertyName)) continue;
                try {
                    state = typedProperty(state, property, value);
                } catch (IllegalArgumentException ignored) {
                    AIBuilderMod.LOG.debug(
                        "Ignoring unsupported block state value {}={} for {}",
                        propertyName, value, Registries.BLOCK.getId(state.getBlock()));
                }
                break;
            }
        }
        return state;
    }
    private static BlockState setProperty(BlockState state,String name,String value){
        for(Property<?> property:state.getProperties()){
            if(property.getName().equals(name))return typedProperty(state,property,value);
        }
        throw new IllegalArgumentException(Registries.BLOCK.getId(state.getBlock())+" не имеет свойства "+name);
    }
    private static <T extends Comparable<T>> BlockState typedProperty(BlockState state,Property<T> prop,String raw){
        T value=prop.parse(raw).orElseThrow(()->new IllegalArgumentException(
            "Недопустимое значение "+raw+" для "+prop.getName()));
        return state.with(prop,value);
    }
    private static String string(JsonObject o,String k,String fallback){
        return o.has(k) && !o.get(k).isJsonNull()?o.get(k).getAsString():fallback;
    }
    private static int number(JsonObject o,String k,int fallback){
        return o.has(k) && !o.get(k).isJsonNull()?o.get(k).getAsInt():fallback;
    }
    private static void add(Map<BlockPos,Staged> out,BlockPos origin,BuilderConfig cfg,
        int x,int y,int z,BlockState state,int stage){
        if(Math.abs(x)>cfg.maxHorizontalDistance || Math.abs(z)>cfg.maxHorizontalDistance ||
            Math.abs(y)>cfg.maxVerticalDistance)
            throw new IllegalArgumentException("Постройка вышла за границы (настройки config).");
        BlockPos pos=origin.add(x,y,z);
        if(pos.getY() < -64 || pos.getY()>319)throw new IllegalArgumentException("Выход за высоту мира.");
        // Preserve the latest operation order for dependent multi-block structures.
        out.remove(pos);
        out.put(pos,new Staged(pos,state,stage));
        if(out.size()>cfg.maxBlocks)
            throw new IllegalArgumentException("Превышен лимит "+cfg.maxBlocks+" блоков.");
    }
}

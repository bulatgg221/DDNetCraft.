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

    public static final Set<String> MATERIALS = MaterialCatalog.allowedIds();

    public record Placement(BlockPos pos, BlockState state) {}
    public record CompiledPlan(String name, List<Placement> blocks,
                               int operations, int adjustedOperations) {}
    private record Staged(BlockPos pos, BlockState state, int stage, boolean critical) {}

    public static CompiledPlan compile(String json, BlockPos origin, BuilderConfig cfg) {
        JsonElement rootElement = JsonParser.parseString(json);
        if (!rootElement.isJsonObject()) throw new IllegalArgumentException("План должен быть JSON-объектом.");
        JsonObject root = rootElement.getAsJsonObject();
        String name = string(root, "name", "AI Building");
        if (name.length() > 80) name = name.substring(0,80);
        JsonArray ops = root.getAsJsonArray("operations");
        if (ops == null || ops.isEmpty())
            throw new IllegalArgumentException("Пустой список строительных операций.");
        BuildLimits.requireConfiguredOperations(ops.size(),cfg.maxOperations);
        // Expand a parametric house to a deterministic, internally consistent design.
        // Model output remains bounded by the same overall operation and block limits.
        List<JsonElement> expanded = new ArrayList<>();
        for (JsonElement operation : ops) {
            if (operation.isJsonObject() && Set.of("house","cottage","detailed_house","manor","villa","farmhouse","chalet","longhouse")
                    .contains(string(operation.getAsJsonObject(),"type",""))) {
                expanded.addAll(HouseLayouts.expand(operation.getAsJsonObject()));
            } else if (operation.isJsonObject() && Set.of("castle","fortress","citadel")
                    .contains(string(operation.getAsJsonObject(),"type",""))) {
                expanded.addAll(CastleLayouts.expand(operation.getAsJsonObject()));
            } else expanded.add(operation);
            BuildLimits.requireConfiguredOperations(expanded.size(),cfg.maxOperations);
        }
        BudgetedPlacements blocks=new BudgetedPlacements(
            BuildLimits.maxPlacementAttempts(cfg.maxBlocks));
        int adjustedOperations = 0;
        int operationIndex = 0;
        for (JsonElement entry : expanded) {
            operationIndex++;
            if (!entry.isJsonObject()) throw new IllegalArgumentException("Каждая операция должна быть объектом.");
            JsonObject op = entry.getAsJsonObject();
            OperationTypes.Resolved resolved = OperationTypes.resolve(
                string(op,"type","box"), string(op,"block",null), MATERIALS);
            String type = resolved.type();
            int x = number(op,"x",0), y = number(op,"y",0), z = number(op,"z",0);
            int rawW = number(op,"w",1), rawH = number(op,"h",1), rawD = number(op,"d",1);
            int rawR = number(op,"r",2);
            OperationSizes.Fitted size = OperationSizes.fit(
                type,x,y,z,rawW,rawH,rawD,rawR,
                cfg.maxHorizontalDistance,cfg.maxVerticalDistance,cfg.maxBlocks,
                FacingHints.horizontal(string(op,"facing","north")));
            int w=size.w(), h=size.h(), d=size.d(), r=size.r();
            if(size.adjusted()) {
                adjustedOperations++;
                AIBuilderMod.LOG.warn("AI Builder adjusted operation {} ({}) dimensions: "
                    + "{}x{}x{} r{} -> {}x{}x{} r{}",
                    operationIndex,type,rawW,rawH,rawD,rawR,w,h,d,r);
            }
            int stage = stage(op, type);
            BlockState material = block(resolved.block() == null
                ? fallback(type) : resolved.block());
            switch(type) {
                case "pixel_art" -> {
                    PixelArtLayout.Canvas canvas=PixelArtLayout.parse(op,MATERIALS);
                    Map<String,BlockState> pixelMaterials=new java.util.HashMap<>();
                    canvas.forEachPixel((dx,dy,dz,id) -> {
                        BlockState pixel=pixelMaterials.computeIfAbsent(id,PlanCompiler::block);
                        add(blocks,origin,cfg,x+dx,y+dy,z+dz,pixel,stage);
                    });
                }
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
                case "gable_fill" -> {
                    // Solid triangular facade beneath a 45-degree stair roof.
                    // Width is the building's wall width; roof_stairs uses w+2 overhang.
                    if(w < 5 || d > 3) throw new IllegalArgumentException(
                        "gable_fill требует w>=5 и d<=3.");
                    for(int dx=0;dx<w;dx++) {
                        int rise=Math.min(dx+1,w-dx);
                        for(int dy=0;dy<rise;dy++)for(int dz=0;dz<d;dz++)
                            add(blocks,origin,cfg,x+dx,y+dy,z+dz,material,stage);
                    }
                }
                case "roof_stairs" -> {
                    material=stairMaterial(material,"roof_stairs");
                    for(int dx=0;dx<w;dx++){
                        int rise=Math.min(dx,w-1-dx);
                        String facing=dx<w/2?"east":"west";
                        BlockState stair=setProperty(material,"facing",facing);
                        for(int dz=0;dz<d;dz++)add(blocks,origin,cfg,x+dx,y+rise,z+dz,stair,stage);
                    }
                }
                case "hip_roof" -> {
                    material=stairMaterial(material,"hip_roof");
                    for(int dx=0;dx<w;dx++)for(int dz=0;dz<d;dz++){
                        int sideX=Math.min(dx,w-1-dx),sideZ=Math.min(dz,d-1-dz);
                        int rise=Math.min(sideX,sideZ);
                        String facing=sideX<=sideZ
                            ? (dx<w/2?"east":"west")
                            : (dz<d/2?"south":"north");
                        BlockState stair=setProperty(material,"facing",facing);
                        add(blocks,origin,cfg,x+dx,y+rise,z+dz,stair,stage);
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
                        ? stairMaterial(material,"chair") : material;
                    state=applyProperties(state,op);
                    if(type.equals("chair"))state=setProperty(state,"facing",
                        FacingHints.horizontal(string(op,"facing","north")));
                    if(type.equals("block") && (isDoor(state)||isBed(state)))
                        throw new IllegalArgumentException("Для дверей и кроватей используй type=door/bed.");
                    String actual=Registries.BLOCK.getId(state.getBlock()).getPath();
                    if(type.equals("block") && Set.of("tall_grass","large_fern",
                        "sunflower","rose_bush","peony","lilac").contains(actual)) {
                        // Vanilla double-height vegetation has real lower and upper
                        // states. A single lower half usually disappears on update.
                        add(blocks,origin,cfg,x,y,z,setProperty(state,"half","lower"),stage);
                        add(blocks,origin,cfg,x,y+1,z,setProperty(state,"half","upper"),stage);
                    } else add(blocks,origin,cfg,x,y,z,state,stage);
                }
                case "door" -> {
                    String original=Registries.BLOCK.getId(material.getBlock()).getPath();
                    String corrected=FurnitureMaterials.door(original,MATERIALS);
                    if(corrected==null)throw new IllegalArgumentException(
                        "door требует материал *_door или подходящую древесину: "+original);
                    if(!corrected.equals(original))AIBuilderMod.LOG.info(
                        "Door material '{}' normalized to '{}'",original,corrected);
                    material=corrected.equals(original)?material:block(corrected);
                    BlockState bottom=setProperty(material,"facing",
                        FacingHints.horizontal(string(op,"facing","north")));
                    bottom=setProperty(bottom,"hinge",
                        FacingHints.hinge(string(op,"hinge","left")));
                    bottom=setProperty(bottom,"half","lower");
                    bottom=setProperty(bottom,"open","false");
                    BlockState top=setProperty(bottom,"half","upper");
                    add(blocks,origin,cfg,x,y,z,bottom,stage);
                    add(blocks,origin,cfg,x,y+1,z,top,stage);
                }
                case "bed" -> {
                    String original=Registries.BLOCK.getId(material.getBlock()).getPath();
                    String corrected=FurnitureMaterials.bed(original,MATERIALS);
                    if(corrected==null)throw new IllegalArgumentException(
                        "bed требует материал *_bed или цветную шерсть: "+original);
                    if(!corrected.equals(original))AIBuilderMod.LOG.info(
                        "Bed material '{}' normalized to '{}'",original,corrected);
                    material=corrected.equals(original)?material:block(corrected);
                    String dir=FacingHints.horizontal(string(op,"facing","north"));
                    BlockState foot=setProperty(setProperty(material,"facing",dir),"part","foot");
                    BlockState head=setProperty(foot,"part","head");
                    Direction facing=Direction.byName(dir);
                    if(facing==null||!facing.getAxis().isHorizontal())throw new IllegalArgumentException("Направление bed: north/south/east/west.");
                    add(blocks,origin,cfg,x,y,z,foot,stage);
                    add(blocks,origin,cfg,x+facing.getOffsetX(),y,z+facing.getOffsetZ(),head,stage);
                }
                case "table" -> {
                    if(w>8||d>6)throw new IllegalArgumentException("Стол максимум 8x6.");
                    BlockState legs=block(FurnitureMaterials.tableLeg(string(op,"leg","oak_fence"),MATERIALS));
                    String requested=string(op,"block","oak_slab");
                    String tabletop=FurnitureMaterials.tabletop(requested,MATERIALS);
                    if(!tabletop.equals(MaterialAliases.resolve(requested,MATERIALS)))
                        AIBuilderMod.LOG.info("Corrected table material '{}' to slab '{}'",requested,tabletop);
                    BlockState top=block(tabletop);
                    // A generic full block such as oak_planks/stone_bricks has NO
                    // Minecraft block-state property "type". Only apply it to slabs.
                    if(top.getProperties().stream().anyMatch(p -> p.getName().equals("type")))
                        top=setProperty(top,"type","top");
                    for(int dx=0;dx<w;dx++)for(int dz=0;dz<d;dz++)
                        add(blocks,origin,cfg,x+dx,y+1,z+dz,top,stage);
                    for(int dx: new int[]{0,w-1})for(int dz: new int[]{0,d-1})
                        add(blocks,origin,cfg,x+dx,y,z+dz,legs,stage);
                }
                case "stairs_run" -> {
                    String dir=FacingHints.horizontal(string(op,"facing","north"));
                    Direction facing=Direction.byName(dir);
                    if(facing==null||!facing.getAxis().isHorizontal())throw new IllegalArgumentException("stairs_run: неверный facing");
                    BlockState stair=setProperty(stairMaterial(material,"stairs_run"),"facing",dir);
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
        return new CompiledPlan(name,result,expanded.size(),adjustedOperations);
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
        String fallback=switch(type){
            case "roof_stairs","hip_roof","gable_roof","gable_fill" -> "roof";
            case "door","block","line","arch","pixel_art" -> "details";
            case "bed","table","chair","stairs_run" -> "interior";
            case "tree" -> "landscape";
            default -> "walls";
        };
        String stage=StageAliases.normalize(string(op,"stage",fallback));
        return switch(stage){
            case "foundation" -> 0;
            case "walls" -> 1;
            case "roof" -> 2;
            case "openings" -> 3;
            case "details" -> 4;
            case "interior" -> 5;
            case "landscape" -> 6;
            default -> throw new IllegalArgumentException("Неизвестный этап: "+stage);
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
        Identifier id=Identifier.of("minecraft",name);
        // Do not trust the default registry fallback: unknown IDs otherwise turn
        // silently into minecraft:air. A palette entry must be a real registered block.
        Block block=Registries.BLOCK.getOrEmpty(id).orElseThrow(() ->
            new IllegalArgumentException("Блок отсутствует в Minecraft 1.21.1: "+name));
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
    private static BlockState stairMaterial(BlockState original,String operation){
        String id=Registries.BLOCK.getId(original.getBlock()).getPath();
        String replacement=FurnitureMaterials.stair(id,MATERIALS);
        if(replacement==null) {
            if(operation.equals("chair")) {
                AIBuilderMod.LOG.warn("Chair material '{}' is not stairs; using oak_stairs",id);
                return block("oak_stairs");
            }
            throw new IllegalArgumentException(
                operation+" требует *_stairs; выбран блок "+id+" без совместимых ступеней.");
        }
        if(!replacement.equals(id))
            AIBuilderMod.LOG.info("Corrected {} material '{}' to '{}'",operation,id,replacement);
        return replacement.equals(id)?original:block(replacement);
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
    /**
     * Bounds attempted placements as well as unique changed blocks.
     * 400+ overlapping small operations must not freeze the integrated server.
     */
    private static final class BudgetedPlacements extends LinkedHashMap<BlockPos,Staged> {
        private final long limit;
        private long attempts;
        BudgetedPlacements(long limit){this.limit=limit;}
        void countAttempt(){
            if(++attempts>limit)
                throw new IllegalArgumentException(
                    "Слишком много наложений блоков в одном плане (предел "
                    +limit+"). Уменьши число повторяющихся/перекрывающихся деталей.");
        }
    }

    private static void add(BudgetedPlacements out,BlockPos origin,BuilderConfig cfg,
        int x,int y,int z,BlockState state,int stage){
        out.countAttempt();
        if(Math.abs(x)>cfg.maxHorizontalDistance || Math.abs(z)>cfg.maxHorizontalDistance ||
            Math.abs(y)>cfg.maxVerticalDistance)
            throw new IllegalArgumentException("Постройка вышла за границы (настройки config).");
        BlockPos pos=origin.add(x,y,z);
        if(pos.getY() < -64 || pos.getY()>319)throw new IllegalArgumentException("Выход за высоту мира.");
        Staged prior=out.get(pos);
        boolean critical=isDoor(state)||isBed(state);
        // A later-printed wall must not overwrite a higher-priority opening or roof.
        // Keep both parts of a functional door/bed from being overwritten by furniture.
        if(prior != null && !LayerOrder.mayReplace(prior.stage(),prior.critical(),
            prior.state().equals(state),stage))
            return;
        // Preserve the latest operation order for dependent multi-block structures.
        out.remove(pos);
        out.put(pos,new Staged(pos,state,stage,critical));
        if(out.size()>cfg.maxBlocks)
            throw new IllegalArgumentException("Превышен лимит "+cfg.maxBlocks+" блоков.");
    }
}

package dev.aibuilder.validation;
import dev.aibuilder.geometry.Voxel;
import net.minecraft.block.*;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static dev.aibuilder.planning.PlanParser.error;

/** Runtime registry is the sole source of IDs; the prompt uses exactly this filtered set. */
public final class MaterialRegistry {
    private final Set<String> allowed;
    private final Map<Voxel,BlockState> states=new ConcurrentHashMap<>();
    public MaterialRegistry(){TreeSet<String> ids=new TreeSet<>();for(Identifier id:Registries.BLOCK.getIds())if(id.getNamespace().equals("minecraft")&&safe(id.getPath(),Registries.BLOCK.get(id)))ids.add(id.toString());allowed=Collections.unmodifiableSet(ids);}
    private static boolean safe(String s,Block b){
        if(b instanceof FallingBlock || s.contains("infested") || s.contains("sculk") || s.contains("coral") || s.contains("ice") || s.contains("copper_bulb"))return false;
        if(b instanceof BlockWithEntity && !(b instanceof BedBlock))return false;
        return Set.of("air","water","stone","cobblestone","mossy_cobblestone","dirt","grass_block","coarse_dirt","clay","terracotta","glass","glass_pane","iron_bars","bookshelf","crafting_table","glowstone","sea_lantern","lantern","soul_lantern","torch","wall_torch","chain","calcite","tuff","deepslate","obsidian","blackstone","bricks","mud_bricks","sandstone","smooth_sandstone","red_sandstone","smooth_red_sandstone","prismarine","dark_prismarine").contains(s)
            || s.matches(".*_(concrete|wool|terracotta|stained_glass|stained_glass_pane|stairs|slab|planks|log|wood|stem|hyphae|leaves|door|trapdoor|fence|fence_gate|wall|bricks|tiles|pillar|carpet|bed)$")
            || s.matches("(stripped_)?bamboo_block") || s.matches("(waxed_)?(exposed_|weathered_|oxidized_)?(cut_|chiseled_)?copper(_block|_grate)?")
            || s.matches("(polished_|smooth_)?(andesite|diorite|granite|basalt|quartz|stone|blackstone|deepslate)")
            || s.matches("(gold|iron|diamond|emerald|quartz|lapis|amethyst|netherite)_block");
    }
    public void validateFluids(dev.aibuilder.geometry.PlacementPlan plan){
        for(var e:plan.voxels().entrySet())if(e.getValue().block().equals("minecraft:water")){
            for(int[] offset:new int[][]{{0,-1,0},{1,0,0},{-1,0,0},{0,0,1},{0,0,-1}}){
                var other=plan.voxels().get(e.getKey().add(offset[0],offset[1],offset[2]));
                if(other!=null&&other.block().equals("minecraft:water"))continue;
                if(other==null||!Block.isShapeFullCube(state(other).getCollisionShape(net.minecraft.world.EmptyBlockView.INSTANCE,net.minecraft.util.math.BlockPos.ORIGIN)))throw error("Вода должна иметь герметичные стены и дно внутри плана.");
            }
        }
    }
    public Set<String> ids(){return allowed;}
    public Voxel resolve(Voxel v){String id=v.block().strip().toLowerCase(Locale.ROOT).replace(' ','_').replace('-','_');if(!id.contains(":"))id="minecraft:"+id;
        if(!allowed.contains(id))throw error("Неизвестный или запрещённый блок: "+id);
        Voxel normalized=new Voxel(id,v.properties(),v.stage(),v.component());
        Voxel cacheKey=new Voxel(id,v.properties(),0,"");if(states.size()>10000)states.clear();states.computeIfAbsent(cacheKey,this::create);return normalized;}
    public BlockState state(Voxel v){Voxel n=resolve(v);return states.computeIfAbsent(new Voxel(n.block(),n.properties(),0,""),this::create);}
    private BlockState create(Voxel v){BlockState s=Registries.BLOCK.get(Identifier.of(v.block())).getDefaultState();
        if(s.getBlock() instanceof LeavesBlock)s=s.with(LeavesBlock.PERSISTENT,true);
        for(var e:v.properties().entrySet())s=property(s,e.getKey(),e.getValue());return s;}
    private static <T extends Comparable<T>> BlockState assign(BlockState s,Property<T> p,String v){return s.with(p,p.parse(v).orElseThrow(()->error("Недопустимое значение свойства "+p.getName()+": "+v)));}
    private static BlockState property(BlockState s,String key,String v){Property<?> p=s.getBlock().getStateManager().getProperty(key);if(p==null)throw error(Registries.BLOCK.getId(s.getBlock())+" не имеет свойства "+key);return assign(s,p,v);}
}

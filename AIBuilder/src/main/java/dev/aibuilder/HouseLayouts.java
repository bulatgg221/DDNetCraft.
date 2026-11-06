package dev.aibuilder;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Bounded parametric house geometry. A model chooses an architectural style
 * and palette; the engine computes all critical block positions.
 * Different styles change the actual silhouette and facade, not just colors.
 */
public final class HouseLayouts {
    private HouseLayouts() {}
    private static final Set<String> FRONTS = Set.of("north","south","east","west");
    private static final Set<String> ROOFS = Set.of("gable","hip","flat");
    private static final Set<String> WINDOWS = Set.of("classic","narrow","wide","tall");

    public static String normalizedStyle(String name) {
        String style = name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
        return switch(style) {
            case "japanese","asian","zen","oriental" -> "japanese";
            case "modern","minimalist","contemporary","villa" -> "modern";
            case "nordic","scandinavian","viking" -> "nordic";
            case "alpine","chalet","swiss" -> "alpine";
            case "rustic","forest","cottage","farmhouse" -> "rustic";
            case "brick","classical","colonial" -> "brick";
            default -> "medieval";
        };
    }

    public static List<JsonObject> expand(JsonObject input) {
        int x = integer(input,"x",6), y = integer(input,"y",0), z = integer(input,"z",6);
        int w = integer(input,"w",15), d = integer(input,"d",13);
        int floors = integer(input,"floors",2);
        String front = string(input,"front","north").toLowerCase(Locale.ROOT);
        if (!FRONTS.contains(front))
            throw new IllegalArgumentException("Дом: front должен быть north/south/east/west");
        if (w < 11 || w > 25 || d < 9 || d > 23)
            throw new IllegalArgumentException("Дом: размеры w=11–25, d=9–23.");
        if (floors != 1 && floors != 2)
            throw new IllegalArgumentException("Дом: floors=1 или 2.");

        String style = normalizedStyle(string(input,"style","medieval"));
        String roofKind = string(input,"roof_style", switch(style){
            case "japanese","brick" -> "hip";
            case "modern" -> "flat";
            default -> "gable";
        }).toLowerCase(Locale.ROOT);
        if (!ROOFS.contains(roofKind))
            throw new IllegalArgumentException("Дом: roof_style = gable, hip или flat.");
        String windowKind = string(input,"window_style",switch(style){
            case "nordic" -> "narrow";
            case "modern","japanese","alpine" -> "wide";
            default -> "classic";
        }).toLowerCase(Locale.ROOT);
        if (!WINDOWS.contains(windowKind))
            throw new IllegalArgumentException("Дом: window_style = classic/narrow/wide/tall.");

        String foundation = string(input,"foundation","stone_bricks");
        String floor = string(input,"floor",style.equals("modern")?"smooth_quartz":"oak_planks");
        String wall = string(input,"wall",switch(style) {
            case "modern" -> "white_concrete";
            case "nordic","rustic" -> "spruce_planks";
            case "brick" -> "bricks";
            default -> "birch_planks";
        });
        String beam = string(input,"beam",switch(style){
            case "modern","brick" -> "polished_andesite";
            default -> "stripped_oak_log";
        });
        String roof = string(input,"roof",style.equals("modern")?"quartz_stairs":"dark_oak_stairs");
        String roofBlock = string(input,"roof_block",style.equals("modern")?"smooth_quartz":"stone_bricks");
        String doorMaterial = string(input,"door",style.equals("modern")?"iron_door":"spruce_door");
        String glass = string(input,"glass","glass_pane");

        boolean porch = bool(input,"porch",Set.of("rustic","japanese","alpine","medieval").contains(style));
        boolean balcony = floors==2 && bool(input,"balcony",Set.of("alpine","modern").contains(style));
        boolean chimney = bool(input,"chimney",Set.of("medieval","nordic","rustic").contains(style));
        boolean garden = bool(input,"garden",Set.of("rustic","japanese").contains(style));

        int cx=x+w/2,cz=z+d/2;
        int roofY=y+floors*5;
        List<JsonObject> out = new ArrayList<>();

        // y=0 is the walkable floor. Doors start at y=1.
        add(out,"box","foundation",foundation,x,y-1,z,w,1,d);
        add(out,"box","foundation",floor,x,y,z,w,1,d);
        for (int f=0;f<floors;f++) {
            int wallY=y+1+f*5;
            add(out,"box","walls",wall,x,wallY,z,w,4,1);
            add(out,"box","walls",wall,x,wallY,z+d-1,w,4,1);
            add(out,"box","walls",wall,x,wallY,z+1,1,4,d-2);
            add(out,"box","walls",wall,x+w-1,wallY,z+1,1,4,d-2);
            if (f==1) add(out,"box","walls",floor,x,y+5,z,w,1,d);
        }
        for (int xx : new int[]{x,x+w-1})
            for (int zz : new int[]{z,z+d-1})
                add(out,"box","walls",beam,xx,y+1,zz,1,floors*5-1,1);
        // Fully supported ceiling below the chosen roof profile.
        add(out,"box","walls",floor,x,roofY,z,w,1,d);

        switch(roofKind){
            case "gable" -> {
                add(out,"gable_fill","walls",wall,x,roofY,z,w,1,1);
                add(out,"gable_fill","walls",wall,x,roofY,z+d-1,w,1,1);
                add(out,"roof_stairs","roof",roof,x-1,roofY,z-1,w+2,1,d+2);
            }
            case "hip" -> add(out,"hip_roof","roof",roof,x-1,roofY,z-1,w+2,1,d+2);
            case "flat" -> {
                add(out,"box","roof",roofBlock,x-1,roofY,z-1,w+2,1,d+2);
                add(out,"box","roof",beam,x-1,roofY+1,z-1,w+2,1,1);
                add(out,"box","roof",beam,x-1,roofY+1,z+d,w+2,1,1);
                add(out,"box","roof",beam,x-1,roofY+1,z,1,1,d);
                add(out,"box","roof",beam,x+w,roofY+1,z,1,1,d);
            }
        }

        // Windows: automatic positions never collide with central front/back doors.
        int span = windowKind.equals("narrow")?1:windowKind.equals("wide")?3:2;
        int high = windowKind.equals("tall")?3:2;
        for (int f=0;f<floors;f++) {
            int yy=y+2+f*5;
            int left=span==3?x+1:x+2;
            int right=span==3?x+w-4:span==1?x+w-3:x+w-4;
            for(int xx : new int[]{left,right}) {
                window(out,glass,xx,yy,z,span,1,high);
                window(out,glass,xx,yy,z+d-1,span,1,high);
            }
            int near=span==3?z+1:z+2;
            int far=span==3?z+d-4:span==1?z+d-3:z+d-4;
            for(int zz : new int[]{near,far}) {
                window(out,glass,x,yy,zz,1,span,high);
                window(out,glass,x+w-1,yy,zz,1,span,high);
            }
        }

        // Doors, correctly carved in the front and rear walls.
        if(front.equals("north") || front.equals("south")) {
            int entranceZ=front.equals("north")?z:z+d-1;
            int rearZ=front.equals("north")?z+d-1:z;
            door(out,doorMaterial,cx,y+1,entranceZ,front);
            door(out,doorMaterial,cx,y+1,rearZ,front.equals("north")?"south":"north");
            if(front.equals("north")) add(out,"box","landscape","gravel",cx,y,z-5,1,1,5);
            else add(out,"box","landscape","gravel",cx,y,z+d,1,1,5);
        } else {
            int entranceX=front.equals("west")?x:x+w-1;
            int rearX=front.equals("west")?x+w-1:x;
            door(out,doorMaterial,entranceX,y+1,cz,front);
            door(out,doorMaterial,rearX,y+1,cz,front.equals("west")?"east":"west");
            if(front.equals("west")) add(out,"box","landscape","gravel",x-5,y,cz,5,1,1);
            else add(out,"box","landscape","gravel",x+w,y,cz,5,1,1);
        }

        // Style-specific framing: framed medieval/alpine homes differ visibly
        // from minimalist houses even if an AI supplies an identical palette.
        if(!style.equals("modern")) {
            for(int f=0;f<floors;f++) {
                add(out,"box","walls",beam,x,y+4+f*5,z,w,1,1);
                add(out,"box","walls",beam,x,y+4+f*5,z+d-1,w,1,1);
            }
        }
        if(Set.of("medieval","alpine","nordic","rustic").contains(style)){
            for(int xx : new int[]{x+4,x+w-5}) {
                add(out,"box","walls",beam,xx,y+1,z,1,floors*5-1,1);
                add(out,"box","walls",beam,xx,y+1,z+d-1,1,floors*5-1,1);
            }
        }

        // Entrance porch attached outside the front; no blocks inside the doorway.
        if(porch) {
            if(front.equals("north") || front.equals("south")){
                int outside=front.equals("north")?z-2:z+d;
                int supports=front.equals("north")?z-2:z+d+1;
                add(out,"box","landscape",floor,cx-2,y,outside,5,1,2);
                for(int xx : new int[]{cx-2,cx+2})
                    add(out,"box","walls",beam,xx,y+1,supports,1,3,1);
                add(out,"box","roof","spruce_slab",cx-3,y+4,
                    front.equals("north")?z-3:z+d,7,1,3);
            } else {
                int outside=front.equals("west")?x-2:x+w;
                int supports=front.equals("west")?x-2:x+w+1;
                add(out,"box","landscape",floor,outside,y,cz-2,2,1,5);
                for(int zz : new int[]{cz-2,cz+2})
                    add(out,"box","walls",beam,supports,y+1,zz,1,3,1);
                add(out,"box","roof","spruce_slab",
                    front.equals("west")?x-3:x+w,y+4,cz-3,3,1,7);
            }
        }

        // Distinct upper-level balconies for alpine and contemporary houses.
        if(balcony) {
            if(front.equals("north") || front.equals("south")){
                int outside=front.equals("north")?z-2:z+d;
                int edge=front.equals("north")?z-2:z+d+1;
                int wallZ=front.equals("north")?z:z+d-1;
                add(out,"box","details",floor,cx-2,y+5,outside,5,1,2);
                add(out,"box","details","oak_fence",cx-2,y+6,edge,5,1,1);
                door(out,doorMaterial,cx,y+6,wallZ,front);
                if(!porch)for(int xx:new int[]{cx-2,cx+2})
                    add(out,"box","walls",beam,xx,y+1,edge,1,4,1);
            } else {
                int outside=front.equals("west")?x-2:x+w;
                int edge=front.equals("west")?x-2:x+w+1;
                int wallX=front.equals("west")?x:x+w-1;
                add(out,"box","details",floor,outside,y+5,cz-2,2,1,5);
                add(out,"box","details","oak_fence",edge,y+6,cz-2,1,1,5);
                door(out,doorMaterial,wallX,y+6,cz,front);
                if(!porch)for(int zz:new int[]{cz-2,cz+2})
                    add(out,"box","walls",beam,edge,y+1,zz,1,4,1);
            }
        }

        if(chimney) {
            // Starts above the roof ceiling and rises beyond the roof slope.
            add(out,"box","details","bricks",x+w-4,roofY+1,z+d-4,2,9,2);
        }

        if(garden) {
            if(front.equals("north") || front.equals("south")){
                int zz=front.equals("north")?z-4:z+d+2;
                for(int xx:new int[]{cx-5,cx+4}){
                    add(out,"box","landscape","grass_block",xx,y,zz,2,1,2);
                    add(out,"box","landscape","poppy",xx,y+1,zz,2,1,2);
                }
            } else {
                int xx=front.equals("west")?x-4:x+w+2;
                for(int zz:new int[]{cz-5,cz+4}){
                    add(out,"box","landscape","grass_block",xx,y,zz,2,1,2);
                    add(out,"box","landscape","poppy",xx,y+1,zz,2,1,2);
                }
            }
        }

        // Basic furnishing always belongs inside the same automatically aligned shell.
        add(out,"table","interior","spruce_slab",x+3,y+1,z+3,3,1,2);
        chair(out,x+4,y+1,z+2,"south");
        chair(out,x+4,y+1,z+5,"north");
        add(out,"box","interior","bookshelf",x+1,y+1,z+d-3,1,2,2);
        add(out,"block","interior","glowstone",x,y+4,cz,1,1,1);
        if (floors==1){
            JsonObject bed=add(out,"bed","interior","red_bed",
                x+w-4,y+1,z+d-3,1,1,1);
            bed.addProperty("facing","north");
        } else {
            int stairX=x+w-4,stairZ=z+d-3;
            add(out,"box","openings","air",stairX,y+5,stairZ-4,1,1,5);
            JsonObject steps=add(out,"stairs_run","interior","oak_stairs",
                stairX,y+1,stairZ,1,5,1);
            steps.addProperty("facing","north");
            JsonObject bed=add(out,"bed","interior","red_bed",
                x+3,y+6,z+d-3,1,1,1);
            bed.addProperty("facing","north");
            add(out,"box","interior","bookshelf",x+1,y+6,z+2,1,2,2);
            add(out,"block","interior","glowstone",x,y+9,cz,1,1,1);
        }
        if (out.size()>100) throw new IllegalArgumentException(
            "Дом слишком детализирован для лимита операций: "+out.size());
        return out;
    }

    private static void window(List<JsonObject> out,String glass,int x,int y,int z,
                               int w,int d,int height) {
        add(out,"box","openings","air",x,y,z,w,height,d);
        add(out,"box","details",glass,x,y,z,w,height,d);
    }
    private static void door(List<JsonObject> out,String material,int x,int y,int z,String facing){
        add(out,"box","openings","air",x,y,z,1,2,1);
        JsonObject op=add(out,"door","details",material,x,y,z,1,1,1);
        op.addProperty("facing",facing);
    }
    private static void chair(List<JsonObject> out,int x,int y,int z,String facing){
        JsonObject op=add(out,"chair","interior","dark_oak_stairs",x,y,z,1,1,1);
        op.addProperty("facing",facing);
    }
    private static JsonObject add(List<JsonObject> out,String type,String stage,String block,
                                  int x,int y,int z,int w,int h,int d){
        JsonObject op=new JsonObject();
        op.addProperty("type",type);op.addProperty("stage",stage);op.addProperty("block",block);
        op.addProperty("x",x);op.addProperty("y",y);op.addProperty("z",z);
        op.addProperty("w",w);op.addProperty("h",h);op.addProperty("d",d);
        out.add(op);
        return op;
    }
    private static String string(JsonObject op,String key,String fallback){
        return op.has(key) && !op.get(key).isJsonNull()?op.get(key).getAsString():fallback;
    }
    private static int integer(JsonObject op,String key,int fallback){
        return op.has(key) && !op.get(key).isJsonNull()?op.get(key).getAsInt():fallback;
    }
    private static boolean bool(JsonObject op,String key,boolean fallback){
        return op.has(key) && !op.get(key).isJsonNull()?op.get(key).getAsBoolean():fallback;
    }
}

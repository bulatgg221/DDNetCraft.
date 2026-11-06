package dev.aibuilder;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Deterministic one- or two-storey Minecraft house blueprint.
 * The AI chooses dimensions, facing and a palette; the engine calculates
 * exact walls, floors, door openings, glazing, stairwell and roof coordinates.
 *
 * All coordinates use the same player-relative convention as PlanCompiler.
 * This is deliberately a bounded primitive, not an unrestricted structure generator.
 */
public final class HouseLayouts {
    private HouseLayouts() {}
    private static final Set<String> FRONTS = Set.of("north","south","east","west");

    public static List<JsonObject> expand(JsonObject input) {
        int x = integer(input,"x",6), y = integer(input,"y",0), z = integer(input,"z",6);
        int w = integer(input,"w",15), d = integer(input,"d",13);
        int floors = integer(input,"floors",2);
        String front = string(input,"front","north");
        if (!FRONTS.contains(front)) throw new IllegalArgumentException(
            "Дом: front должен быть north, south, east или west");
        if (w < 11 || w > 25 || d < 9 || d > 23)
            throw new IllegalArgumentException("Дом: размеры w=11–25, d=9–23.");
        if (floors != 1 && floors != 2) throw new IllegalArgumentException("Дом: floors=1 или 2.");

        String foundation = string(input,"foundation","stone_bricks");
        String floor = string(input,"floor","oak_planks");
        String wall = string(input,"wall","birch_planks");
        String beam = string(input,"beam","stripped_oak_log");
        String roof = string(input,"roof","dark_oak_stairs");
        String door = string(input,"door","spruce_door");
        String glass = string(input,"glass","glass_pane");
        int cx=x+w/2,cz=z+d/2;
        int roofY=y+floors*5;
        List<JsonObject> out = new ArrayList<>();

        // Geometry and footings: y=0 is the walking floor, y=1 is the lower door half.
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

        // Solid corner posts are structural. Windows are never placed at these corners.
        for (int xx : new int[]{x,x+w-1})
            for (int zz : new int[]{z,z+d-1})
                add(out,"box","walls",beam,xx,y+1,zz,1,floors*5-1,1);

        // Seal each triangular gable end while retaining the roof-stair silhouette.
        add(out,"gable_fill","walls",wall,x,roofY,z,w,1,1);
        add(out,"gable_fill","walls",wall,x,roofY,z+d-1,w,1,1);
        add(out,"roof_stairs","roof",roof,x-1,roofY,z-1,w+2,1,d+2);

        // Carve and glaze symmetric 2x2 windows. All windows are on actual walls.
        for (int f=0;f<floors;f++) {
            int yy=y+2+f*5;
            for(int xx : new int[]{x+2,x+w-4}) {
                window(out,glass,xx,yy,z,2,1);
                window(out,glass,xx,yy,z+d-1,2,1);
            }
            for(int zz : new int[]{z+2,z+d-4}) {
                window(out,glass,x,yy,zz,1,2);
                window(out,glass,x+w-1,yy,zz,1,2);
            }
        }

        // The entrance door and (optional) rear door are always located on the shell.
        if(front.equals("north") || front.equals("south")) {
            int entranceZ=front.equals("north")?z:z+d-1;
            int rearZ=front.equals("north")?z+d-1:z;
            door(out,door,cx,y+1,entranceZ,front);
            door(out,door,cx,y+1,rearZ,front.equals("north")?"south":"north");
            if(front.equals("north")) add(out,"box","landscape","gravel",cx,y,z-5,1,1,5);
            else add(out,"box","landscape","gravel",cx,y,z+d,1,1,5);
        } else {
            int entranceX=front.equals("west")?x:x+w-1;
            int rearX=front.equals("west")?x+w-1:x;
            door(out,door,entranceX,y+1,cz,front);
            door(out,door,rearX,y+1,cz,front.equals("west")?"east":"west");
            if(front.equals("west")) add(out,"box","landscape","gravel",x-5,y,cz,5,1,1);
            else add(out,"box","landscape","gravel",x+w,y,cz,5,1,1);
        }

        // Entry-level furnishing with clear circulation across the building center.
        add(out,"table","interior","spruce_slab",x+3,y+1,z+3,3,1,2);
        chair(out,x+4,y+1,z+2,"south");
        chair(out,x+4,y+1,z+5,"north");
        add(out,"box","interior","bookshelf",x+1,y+1,z+d-3,1,2,2);
        // Inset lights into the side wall instead of leaving floating light blocks.
        add(out,"block","interior","glowstone",x,y+4,cz,1,1,1);
        if (floors == 1) {
            JsonObject singleBed=add(out,"bed","interior","red_bed",
                x+w-4,y+1,z+d-3,1,1,1);
            singleBed.addProperty("facing","north");
        }

        if(floors==2) {
            // A carved continuous vertical stairwell avoids collisions with the upper floor.
            int stairX=x+w-4, stairZ=z+d-3;
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
        return out;
    }

    private static void window(List<JsonObject> out,String glass,int x,int y,int z,int w,int d){
        add(out,"box","openings","air",x,y,z,w,2,d);
        add(out,"box","details",glass,x,y,z,w,2,d);
    }
    private static void door(List<JsonObject> out,String material,int x,int y,int z,String facing){
        add(out,"box","openings","air",x,y,z,1,2,1);
        JsonObject entry=add(out,"door","details",material,x,y,z,1,1,1);
        entry.addProperty("facing",facing);
    }
    private static void chair(List<JsonObject> out,int x,int y,int z,String facing){
        JsonObject chair=add(out,"chair","interior","dark_oak_stairs",x,y,z,1,1,1);
        chair.addProperty("facing",facing);
    }
    private static JsonObject add(List<JsonObject> out,String type,String stage,String block,
                                  int x,int y,int z,int w,int h,int d){
        JsonObject op=new JsonObject();
        op.addProperty("type",type);
        op.addProperty("stage",stage);
        op.addProperty("block",block);
        op.addProperty("x",x);op.addProperty("y",y);op.addProperty("z",z);
        op.addProperty("w",w);op.addProperty("h",h);op.addProperty("d",d);
        out.add(op);
        return op;
    }
    private static String string(JsonObject obj,String name,String fallback){
        return obj.has(name) && !obj.get(name).isJsonNull()?obj.get(name).getAsString():fallback;
    }
    private static int integer(JsonObject obj,String name,int fallback){
        return obj.has(name) && !obj.get(name).isJsonNull()?obj.get(name).getAsInt():fallback;
    }
}

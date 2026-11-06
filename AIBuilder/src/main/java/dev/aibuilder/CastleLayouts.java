package dev.aibuilder;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic compact castle. A single macro places a courtyard, square
 * fortifications, four hollow towers, crenellations, a gate and a central keep.
 * All positions are derived from one footprint, not invented independently by an LLM.
 */
public final class CastleLayouts {
    private CastleLayouts() {}

    public static List<JsonObject> expand(JsonObject in) {
        int x=number(in,"x",6),y=number(in,"y",0),z=number(in,"z",6);
        int w=Math.clamp(number(in,"w",35),25,39);
        int d=Math.clamp(number(in,"d",35),25,39);
        String stone=string(in,"wall","stone_bricks");
        String trim=string(in,"trim","cobblestone");
        String roof=string(in,"roof","dark_oak_stairs");
        String wood=string(in,"floor","dark_oak_planks");
        String door=string(in,"door","dark_oak_door");
        int cx=x+w/2,cz=z+d/2;
        List<JsonObject> out=new ArrayList<>();

        // Foundation and a protected, open-air courtyard.
        add(out,"box","foundation",stone,x,y-1,z,w,1,d);
        add(out,"box","foundation","grass_block",x+1,y,z+1,w-2,1,d-2);

        // Four linked fortification walls, 8 blocks high.
        add(out,"box","walls",stone,x,y+1,z,w,8,1);
        add(out,"box","walls",stone,x,y+1,z+d-1,w,8,1);
        add(out,"box","walls",stone,x,y+1,z+1,1,8,d-2);
        add(out,"box","walls",stone,x+w-1,y+1,z+1,1,8,d-2);

        // Four circular corner towers with upper walkable platforms.
        for(int tx: new int[]{x+3,x+w-4}) {
            for(int tz: new int[]{z+3,z+d-4}) {
                JsonObject tower=add(out,"hollow_cylinder","walls",stone,tx,y+1,tz,1,11,1);
                tower.addProperty("r",3);
                JsonObject deck=add(out,"cylinder","roof",wood,tx,y+11,tz,1,1,1);
                deck.addProperty("r",3);
                JsonObject parapet=add(out,"hollow_cylinder","details",stone,tx,y+12,tz,1,2,1);
                parapet.addProperty("r",3);
            }
        }

        // Battlements from the same footprint. Fewer blocks than continuous ledges.
        for(int dx=6;dx<w-6;dx+=4){
            add(out,"box","details",stone,x+dx,y+9,z,2,1,1);
            add(out,"box","details",stone,x+dx,y+9,z+d-1,2,1,1);
        }
        for(int dz=6;dz<d-6;dz+=4){
            add(out,"box","details",stone,x,y+9,z+dz,1,1,2);
            add(out,"box","details",stone,x+w-1,y+9,z+dz,1,1,2);
        }

        // The front gate is on north wall, 2 wide, with 4-block-high clear passage.
        add(out,"box","openings","air",cx-1,y+1,z,2,4,1);
        JsonObject gateA=add(out,"door","details",door,cx-1,y+1,z,1,1,1);
        gateA.addProperty("facing","north");
        JsonObject gateB=add(out,"door","details",door,cx,y+1,z,1,1,1);
        gateB.addProperty("facing","north");
        gateB.addProperty("hinge","right");

        // Gatehouse towers emphasize the central entrance.
        for(int gx:new int[]{cx-5,cx+4}) {
            add(out,"box","walls",trim,gx,y+1,z-1,2,11,3);
            add(out,"box","details",stone,gx,y+12,z-1,2,1,3);
        }
        add(out,"box","landscape","gravel",cx-1,y,z-6,2,1,6);
        add(out,"box","landscape","gravel",cx-1,y,z+1,2,1,d/2-6);

        // Central multi-storey keep: accessible via its north-facing door.
        int keepW=Math.min(13,w-12), keepD=Math.min(11,d-12);
        int kx=cx-keepW/2, kz=z+d-keepD-5;
        add(out,"hollow_box","walls",trim,kx,y+1,kz,keepW,10,keepD);
        add(out,"box","openings","air",cx,y+2,kz,1,2,1);
        JsonObject keepDoor=add(out,"door","details",door,cx,y+2,kz,1,1,1);
        keepDoor.addProperty("facing","north");
        add(out,"roof_stairs","roof",roof,kx-1,y+11,kz-1,keepW+2,1,keepD+2);
        add(out,"box","foundation",wood,kx+1,y+1,kz+1,keepW-2,1,keepD-2);
        add(out,"box","walls",wood,kx+1,y+6,kz+1,keepW-2,1,keepD-2);
        // Minimal interior, avoiding the entry and internal passages.
        add(out,"table","interior","oak_slab",kx+3,y+2,kz+4,3,1,2);
        add(out,"box","interior","bookshelf",kx+1,y+2,kz+keepD-3,1,3,2);
        JsonObject bed=add(out,"bed","interior","red_bed",kx+3,y+7,kz+keepD-3,1,1,1);
        bed.addProperty("facing","north");
        add(out,"block","interior","lantern",kx+2,y+3,kz+1,1,1,1);

        if(out.size()>100) throw new IllegalArgumentException(
            "Замок содержит слишком много операций: "+out.size());
        return out;
    }

    private static JsonObject add(List<JsonObject> out,String type,String stage,String block,
                                  int x,int y,int z,int w,int h,int d) {
        JsonObject o=new JsonObject();
        o.addProperty("type",type);o.addProperty("stage",stage);o.addProperty("block",block);
        o.addProperty("x",x);o.addProperty("y",y);o.addProperty("z",z);
        o.addProperty("w",w);o.addProperty("h",h);o.addProperty("d",d);
        out.add(o);
        return o;
    }
    private static int number(JsonObject o,String key,int fallback) {
        return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsInt():fallback;
    }
    private static String string(JsonObject o,String key,String fallback) {
        return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():fallback;
    }
}

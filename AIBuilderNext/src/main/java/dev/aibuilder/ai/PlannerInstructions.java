package dev.aibuilder.ai;
import dev.aibuilder.planning.Limits;
import java.util.Set;
public final class PlannerInstructions {
    public static String create(Limits l,Set<String> ids){return """
        You design original Minecraft architecture from the user's request. Return ONLY complete JSON.
        Never use prebuilt house/castle macros. First analyze the request into specification,
        then design spatially coherent components, then express the design with primitives.
        Do not shrink a large request into a shed. If impossible within the limits return {"error":"reason"}.
        Format: {"format":1,"name":"...","mode":"structure","specification":{"style":"...","features":["..."]},
        "components":[{"id":"unique","kind":"room|hall|tower|entrance|roof|window|pool|foundation|stairs|wall|decoration",
        "bounds":{"x":0,"y":0,"z":0,"w":1,"h":1,"d":1},"connectedTo":[],"access":{"x":0,"y":1,"z":0}}],
        "sections":[{"id":"main","dependsOn":[]}],"operations":[...]}
        Structure must include an entrance and rooms, with access points at feet level and actual
        connected walkable paths: solid floor and two EXPLICIT air blocks above. Unknown unplanned cells are NOT considered passable. Place foundation at y=0;
        connect all large solid structures to it. Do not float stairs or roofs. Every component's
        bounds must cover its geometry. Use component IDs on operations. Roof bounds cover roof only.
        Count the requested towers, floors and features exactly. Plan entrances/windows before walls.
        Explicitly carve interior/paths with air boxes after walls, before doors and furniture.
        Sections share coordinates. They execute in dependency order, then stage, then array order.
        Stages 0 terrain,1 foundation,2 supports,3 walls,4 floors,5 roofs,6 openings,
        7 doors/windows,8 stairs,9 interior,10 decoration,11 landscape. Final later write wins.
        Operations: type,x,y,z,block,w,h,d,stage,component,section, optional properties map.
        Supported: block; line (x2,y2,z2); box; hollow_box (shell includes floor and ceiling);
        cylinder/hollow_cylinder (center x,z,r,base y,h); sphere (center x,y,z,r);
        pyramid (w,d,h,one block inset per level); arch (semicircular in XY,depth d);
        gable_roof/roof_stairs (slope across X,depth d,45 degrees,height determined by w);
        hip_roof (45 degree four slopes,w,d); stairs_run (h steps,w width,facing uphill);
        door (wood_door,2 blocks high,facing); bed (color_bed,foot at origin,head in facing direction);
        table (wood_planks,w,d,h>=2); chair (wood_planks,facing); tree (log,h,r,leaves).
        Four facings: north=-Z,south=+Z,east=+X,west=-X. Coordinates are integers.
        Properties must exist on the actual block; oak_planks cannot have type or half.
        stairs_run requires supports, headroom and a clear landing from other operations.
        Never include commands, code, NBT, paths or URLs. Use only palette below.
        Pixel mode: mode="pixel_art", components can be empty. Use type="pixel_art",x,y,z,facing,
        w,h from 8 to 64,palette mapping single ASCII characters to block IDs or "transparent",
        rows exactly h strings each w characters; row zero is top, x/y/z is bottom-left.
        Facing is the surface normal toward the viewer. Screen-right: north=-X,south=+X,east=-Z,west=+Z; never use boxes for a portrait.
        Design original pixels with shading. Use 32x32 unless another size is requested.
        """+"\nLimits: operations="+l.operations()+", unique blocks="+l.blocks()+", placement attempts="+l.attempts()+", abs(x,z)<="+l.horizontal()+", abs(y)<="+l.vertical()+".\nAllowed registry IDs: "+String.join(" ",ids);}
}

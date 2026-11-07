package dev.aibuilder;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.Set;

/**
 * Fixes the LLM's frequent confusion between geometry "type" and "stage".
 *
 * Example: {"type":"details","block":"lantern","x":10,"y":2,"z":11}
 * becomes: {"type":"block","stage":"details", ...}.
 *
 * This is NOT a generic "guess whatever the model meant" implementation:
 * there must be explicit coordinates, a real allowed block, and either an
 * explicit recognized primitive hint or unambiguous low-level geometry.
 * All ambiguous/nested cases fail closed instead of generating random walls.
 */
public final class StageOperationRepair {
    private StageOperationRepair() {}

    private static final Set<String> MISPLACED_STAGES=Set.of(
        "details","detail","decor","decoration","decorations",
        "foundation","walls","openings","interior",
        "landscape","landscaping","roofing"
    );
    private static final String[] HINTS={
        "primitive","geometry","operation","shape","op"
    };

    public static boolean normalize(JsonObject operation,Set<String> allowed) {
        if(!operation.has("type")||!operation.get("type").isJsonPrimitive()||
            !operation.getAsJsonPrimitive("type").isString())
            return false;
        String raw=operation.get("type").getAsString();
        String type=raw.strip().toLowerCase(Locale.ROOT)
            .replace(' ','_').replace('-','_');
        if(!MISPLACED_STAGES.contains(type))return false;

        String stage=StageAliases.normalize(type);
        if(operation.has("operations")||operation.has("blocks"))
            throw failure(type,"вложенный список операций не поддерживается. "
                +"Каждый элемент должен быть отдельной операцией в operations.");
        if(!operation.has("block")||!operation.get("block").isJsonPrimitive()||
            !operation.getAsJsonPrimitive("block").isString())
            throw failure(type,"отсутствует настоящий block=<ID материала>. "
                +"Невозможно определить геометрию по одному названию этапа.");
        String block=operation.get("block").getAsString().strip();
        // The model is untrusted. Never infer a block outside the central
        // safe palette from a stage label.
        String material=MaterialAliases.resolve(block,allowed);
        if(!allowed.contains(material))
            throw failure(type,"неподдерживаемый материал "+block);
        if(material.equals("air") && !stage.equals("openings"))
            throw failure(type,"удаление блоков (air) разрешается только на этапе openings.");

        for(String coordinate:new String[]{"x","y","z"})
            if(!validInteger(operation,coordinate))
                throw failure(type,"нужны явные целые координаты x, y, z.");
        String primitive=null;
        for(String hint:HINTS) {
            if(!operation.has(hint))continue;
            JsonElement field=operation.get(hint);
            if(!field.isJsonPrimitive()||!field.getAsJsonPrimitive().isString())
                throw failure(type,"поле "+hint+" должно содержать имя геометрической операции.");
            String candidate=field.getAsString().strip();
            if(candidate.isEmpty())continue;
            // shape=straight/inner_left/etc describes the *block state* of
            // Minecraft stairs, not an operation type.
            if(hint.equals("shape") && Set.of("straight","inner_left",
                "inner_right","outer_left","outer_right")
                .contains(candidate.toLowerCase(Locale.ROOT)))continue;
            OperationTypes.Resolved resolved=OperationTypes.resolve(candidate,material,allowed);
            String inferred=resolved.type();
            if(GeneratedPlanGuard.usesPrebuilt(inferred))
                throw failure(type,"шаблонные постройки запрещены.");
            if(primitive!=null&&!primitive.equals(inferred))
                throw failure(type,"противоречивые подсказки геометрии.");
            primitive=inferred;
            // "shape" is also a Minecraft stair BlockState property. When
            // used here as an operation hint (shape=arch/box/etc), remove it
            // before applyProperties tries to apply "arch" to a stair state.
            if(hint.equals("shape"))operation.remove("shape");
        }
        if(primitive==null)
            primitive=inferSimpleShape(operation,type,material);
        else if(primitive.equals("pixel_art")&&!operation.has("rows"))
            throw failure(type,"для pixel_art нужны palette и rows.");

        if(!operation.has("stage")||operation.get("stage").isJsonNull())
            operation.addProperty("stage",stage);
        operation.addProperty("type",primitive);
        // Preserve all source coordinates/dimensions and material from the LLM.
        return true;
    }

    private static String inferSimpleShape(JsonObject op,String stage,String material) {
        if(op.has("rows")||op.has("palette"))
            throw failure(stage,"pixel_art требует явно указанное shape=pixel_art "
                +"или type=pixel_art.");
        if(op.has("x2")||op.has("y2")||op.has("z2")) {
            if(!validInteger(op,"x2")||!validInteger(op,"y2")||
                !validInteger(op,"z2"))
                throw failure(stage,"для линии нужны x2, y2, z2.");
            return "line";
        }
        if(op.has("r"))
            throw failure(stage,"радиус r не позволяет выбрать между сферой "
                +"и цилиндром. Укажи shape=sphere/cylinder.");
        boolean hasDimensions=op.has("w")||op.has("h")||op.has("d");
        if(hasDimensions){
            // Width/height/depth means an axis-aligned box ONLY when a
            // clearly thin 2D decorative plane / 1D beam is described.
            // A 15x15x15 mass might be a cylinder, tower, hollow box etc.
            int w=dimension(op,"w"),h=dimension(op,"h"),d=dimension(op,"d");
            if((material.endsWith("_door")||material.endsWith("_bed"))
                && (w>1||h>1||d>1))
                throw failure(stage,"многочастный блок "+material+
                    " нельзя разворачивать в прямоугольник; "
                    +"используй shape=door/bed и одну начальную координату.");
            if(w==1&&h==1&&d==1)
                return material.endsWith("_door")||material.endsWith("_bed")
                    ?OperationTypes.resolve("block",material,MaterialCatalog.allowedIds()).type()
                    :"block";
            if((w==1||h==1||d==1) && (long)w*h*d<=256L)
                return "box";
            throw failure(stage,"объёмная геометрия неоднозначна "
                +"("+w+"×"+h+"×"+d+"). Укажи shape=box/hollow_box/cylinder/... .");
        }
        if(material.equals("air"))
            throw failure(stage,"для удаления блоков укажи явную геометрию и размеры.");
        return OperationTypes.resolve("block",material,MaterialCatalog.allowedIds()).type();
    }

    private static int dimension(JsonObject op,String name){
        if(!op.has(name))return 1;
        if(!validInteger(op,name))
            throw failure(op.get("type").getAsString(),"размер "+name+" должен быть целым числом.");
        int n=op.get(name).getAsInt();
        if(n<1||n>256)
            throw failure(op.get("type").getAsString(),"размер "+name+" должен быть 1–256.");
        return n;
    }

    private static boolean validInteger(JsonObject o,String key){
        if(!o.has(key)||o.get(key).isJsonNull()||
            !o.get(key).isJsonPrimitive())return false;
        String value=o.get(key).getAsString().strip();
        if(!value.matches("-?[0-9]{1,10}"))return false;
        try { Integer.parseInt(value);return true; }
        catch(NumberFormatException ignore){return false;}
    }

    private static IllegalArgumentException failure(String stage,String reason){
        return new IllegalArgumentException(
            "Операция type="+stage+" перепутала этап и форму: "+reason);
    }
}

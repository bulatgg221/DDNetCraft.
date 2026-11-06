package dev.aibuilder;

import java.util.Locale;
import java.util.Set;

/** Canonicalizes optional LLM orientation hints for functional structures. */
public final class FacingHints {
    private FacingHints() {}

    public static String horizontal(String value){
        if(value==null)return "north";
        String name=value.trim().toLowerCase(Locale.ROOT).replace('-','_');
        return switch(name){
            case "north","n","negative_z","-z" -> "north";
            case "south","s","positive_z","+z" -> "south";
            case "east","e","positive_x","+x" -> "east";
            case "west","w","negative_x","-x" -> "west";
            default -> "north";
        };
    }

    public static String hinge(String value){
        return value!=null&&value.strip().equalsIgnoreCase("right")?"right":"left";
    }
}

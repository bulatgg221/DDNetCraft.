package dev.aibuilder.geometry;
import java.util.*;
public record Voxel(String block, Map<String,String> properties, int stage, String component) {
    public Voxel { properties=Map.copyOf(properties); }
    public Voxel with(String key,String value){var copy=new HashMap<>(properties);copy.put(key,value);return new Voxel(block,copy,stage,component);}
}

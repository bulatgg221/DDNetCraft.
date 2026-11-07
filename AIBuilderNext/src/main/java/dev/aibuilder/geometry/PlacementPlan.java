package dev.aibuilder.geometry;
import dev.aibuilder.planning.*;
import java.util.*;
public record PlacementPlan(Blueprint blueprint, Map<Pos,Voxel> voxels, long attempts, List<String> warnings) {
    public PlacementPlan { voxels=Collections.unmodifiableMap(new LinkedHashMap<>(voxels));warnings=List.copyOf(warnings); }
}

package dev.aibuilder;
import com.google.gson.*;
import dev.aibuilder.planning.*;
import dev.aibuilder.geometry.*;
import dev.aibuilder.validation.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SpatialTest {
    private PlacementPlan room(boolean blocked){List<Blueprint.Component> components=List.of(
        new Blueprint.Component("entry","entrance",new Blueprint.Bounds(0,0,0,5,3,5),List.of("room"),new Pos(1,1,1)),
        new Blueprint.Component("room","room",new Blueprint.Bounds(0,0,0,5,3,5),List.of("entry"),new Pos(3,1,3)));
        Blueprint b=new Blueprint("test","structure",new JsonObject(),components,List.of(),List.of(),"{}");Map<Pos,Voxel> v=new LinkedHashMap<>();
        for(int x=0;x<5;x++)for(int z=0;z<5;z++)for(int y=0;y<3;y++)v.put(new Pos(x,y,z),new Voxel(y==0||blocked&&x==2?"stone":"air",Map.of(),0,"room"));
        return new PlacementPlan(b,v,v.size(),List.of());}
    @Test void connectedRoomsPass(){var report=SpatialValidator.validate(room(false));assertEquals(1,report.rooms());assertTrue(report.reachable()>=9);}
    @Test void wallsBlockWalkability(){assertThrows(IllegalArgumentException.class,()->SpatialValidator.validate(room(true)));}
    @Test void unspecifiedAirCannotHideTerrain(){var plan=room(false);var v=new LinkedHashMap<>(plan.voxels());v.remove(new Pos(1,2,1));assertThrows(IllegalArgumentException.class,()->SpatialValidator.validate(new PlacementPlan(plan.blueprint(),v,0,List.of())));}
    @Test void missingDoorHalfFails(){var p=room(false);var v=new LinkedHashMap<>(p.voxels());v.put(new Pos(1,1,1),new Voxel("oak_door",Map.of("half","lower","facing","north"),0,"room"));assertThrows(IllegalArgumentException.class,()->SpatialValidator.validate(new PlacementPlan(p.blueprint(),v,0,List.of())));}
    @Test void requestedFeaturesAreChecked(){assertThrows(IllegalArgumentException.class,()->BuildQualityAnalyzer.analyze("Замок с пятью башнями",room(false)));assertThrows(IllegalArgumentException.class,()->BuildQualityAnalyzer.analyze("Вилла с бассейном",room(false)));}
    @Test void unsupportedLargeIslandFails(){var p=room(false);var v=new LinkedHashMap<>(p.voxels());for(int x=0;x<4;x++)for(int z=0;z<4;z++)v.put(new Pos(x,10,z),new Voxel("stone",Map.of(),0,""));assertThrows(IllegalArgumentException.class,()->SpatialValidator.validate(new PlacementPlan(p.blueprint(),v,0,List.of())));}
}

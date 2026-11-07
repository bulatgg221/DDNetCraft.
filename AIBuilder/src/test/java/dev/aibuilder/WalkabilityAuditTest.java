package dev.aibuilder;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class WalkabilityAuditTest {
    private static List<ArchitectureSpec.Region> twoConnectedRooms(){
        return List.of(
            new ArchitectureSpec.Region("entry","entrance",1,1,1,3,4,3,
                List.of("hall")),
            new ArchitectureSpec.Region("hall","hall",4,1,1,4,4,3,
                List.of("entry"))
        );
    }

    private static Map<WalkabilityAudit.Cell,String> floors(){
        Map<WalkabilityAudit.Cell,String> blocks=new HashMap<>();
        for(int x=1;x<8;x++)for(int z=1;z<4;z++)
            blocks.put(new WalkabilityAudit.Cell(x,0,z),"oak_planks");
        return blocks;
    }

    @Test void acceptsTrueTwoBlockHighConnectedRooms(){
        assertTrue(WalkabilityAudit.inspect(twoConnectedRooms(),floors()).isEmpty());
    }

    @Test void rejectsClosedDoorwayBlockedBySolidWalls(){
        Map<WalkabilityAudit.Cell,String> voxels=floors();
        for(int z=1;z<4;z++)for(int y=1;y<=2;y++)
            voxels.put(new WalkabilityAudit.Cell(4,y,z),"stone_bricks");
        List<String> issues=WalkabilityAudit.inspect(twoConnectedRooms(),voxels);
        assertTrue(issues.stream().anyMatch(s->s.contains("переход")),
            issues.toString());
    }

    @Test void identifiesRoomWithNoSupportingFloor(){
        List<String> issues=WalkabilityAudit.inspect(twoConnectedRooms(),Map.of());
        assertTrue(issues.stream().anyMatch(s->s.contains("полом")),
            issues.toString());
    }

    @Test void identifiesCeilingWithInsufficientHeadroom(){
        Map<WalkabilityAudit.Cell,String> voxels=floors();
        for(int x=1;x<8;x++)for(int z=1;z<4;z++){
            voxels.put(new WalkabilityAudit.Cell(x,2,z),"stone_bricks");
            // Otherwise the ceiling becomes a valid upper-level floor.
            voxels.put(new WalkabilityAudit.Cell(x,4,z),"stone_bricks");
        }
        var issues=WalkabilityAudit.inspect(twoConnectedRooms(),voxels);
        assertTrue(issues.stream().anyMatch(s->s.contains("высотой 2")),
            issues.toString());
    }

    @Test void realDoorsAreTreatedAsWalkableWhenThereIsHeadroom(){
        Map<WalkabilityAudit.Cell,String> voxels=floors();
        for(int z=1;z<4;z++)for(int y=1;y<=2;y++)
            voxels.put(new WalkabilityAudit.Cell(4,y,z),"stone_bricks");
        for(int y=1;y<=2;y++)
            voxels.put(new WalkabilityAudit.Cell(4,y,2),"oak_door");
        assertTrue(WalkabilityAudit.inspect(twoConnectedRooms(),voxels).isEmpty());
    }

    @Test void suspiciouslyHugeRegionIsSkippedRatherThanRunningAnUnboundedScan(){
        var giant=new ArchitectureSpec.Region("huge","hall",1,1,1,
            256,256,256,List.of());
        var issues=WalkabilityAudit.inspect(List.of(giant),Map.of());
        assertTrue(issues.stream().anyMatch(s->s.contains("слишком велика")),
            issues.toString());
    }
}

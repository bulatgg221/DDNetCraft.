package dev.aibuilder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ProjectStoreTest {
    @Test void rejectsPathTraversalAndUntrustedModelPaths() {
        for(String id:new String[]{"","../escape","/tmp/key","C:\\secret",
            ".","..","some name","коттедж","project.json",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}){
            assertThrows(IllegalArgumentException.class,
                ()->ProjectStore.safeId(id),id);
        }
    }

    @Test void allowsOnlyBoundedPortableProjectIds() {
        for(String name:new String[]{"castle_1","my-modern-villa","p01","B_2026"}){
            assertEquals(name,ProjectStore.safeId(name));
        }
    }
}

package dev.aibuilder;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ground the palette in the actual Minecraft 1.21.1 block registry.
 * A compile-time whitelist alone can contain plausible but nonexistent IDs.
 */
final class PaletteRegistryTest {
    @BeforeAll static void initializeMinecraftRegistries() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test void everyOfferedModelBlockExistsInMinecraft1211() {
        List<String> missing=new ArrayList<>();
        for(String name:MaterialCatalog.allowedIds()){
            Identifier id=Identifier.of("minecraft",name);
            if(Registries.BLOCK.getOrEmpty(id).isEmpty())missing.add(name);
        }
        Collections.sort(missing);
        assertTrue(missing.isEmpty(),
            "Never advertise invalid IDs to GPT: "+String.join(", ",missing));
    }

    @Test void noWhitelistedNameSilentlyResolvesToDefaultAir() {
        List<String> invalid=new ArrayList<>();
        for(String name:MaterialCatalog.allowedIds()) {
            if(name.equals("air"))continue;
            Block block=Registries.BLOCK.getOrEmpty(Identifier.of("minecraft",name))
                .orElse(Blocks.AIR);
            if(block==Blocks.AIR)invalid.add(name);
        }
        assertTrue(invalid.isEmpty(),"Unexpected AIR fallback for IDs: "+invalid);
    }

    @Test void purpleGlassPaneIsARealPlaceableMinecraftBlock(){
        var purple=Registries.BLOCK.getOrEmpty(
            Identifier.of("minecraft","purple_stained_glass_pane"));
        assertTrue(purple.isPresent());
        assertNotSame(Blocks.AIR,purple.orElseThrow());
    }
}

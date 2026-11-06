package dev.aibuilder;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks real Minecraft 1.21.1 registered block ID string literals from
 * net/minecraft/block/Blocks.class. It avoids starting the full Minecraft
 * bootstrap in plain JUnit (Fabric access wideners are not applied there).
 * Reads CONSTANT_String entries, not field names or arbitrary substrings.
 */
final class PaletteRegistryTest {
    private static Set<String> blockIdsFromMinecraftClassFile() throws IOException {
        try(InputStream stream=PaletteRegistryTest.class.getResourceAsStream(
                "/net/minecraft/block/Blocks.class")) {
            assertNotNull(stream,"Fabric Loom test classpath must contain Minecraft Blocks.class");
            DataInputStream in=new DataInputStream(stream);
            assertEquals(0xCAFEBABE,in.readInt(),"Not a JVM class file");
            in.readUnsignedShort(); // minor
            in.readUnsignedShort(); // major
            int count=in.readUnsignedShort();
            String[] utf8=new String[count];
            List<Integer> stringRefs=new ArrayList<>();
            for(int i=1;i<count;i++) {
                int tag=in.readUnsignedByte();
                switch(tag){
                    case 1 -> utf8[i]=in.readUTF();
                    case 3,4 -> in.skipNBytes(4);
                    case 5,6 -> {in.skipNBytes(8);i++;}
                    case 7,16,19,20 -> in.skipNBytes(2);
                    case 8 -> stringRefs.add(in.readUnsignedShort());
                    case 9,10,11,12,17,18 -> in.skipNBytes(4);
                    case 15 -> in.skipNBytes(3);
                    default -> throw new IOException("Unsupported JVM constant pool tag "+tag);
                }
            }
            Set<String> strings=new HashSet<>();
            for(int index:stringRefs) {
                if(index>0&&index<count&&utf8[index]!=null)strings.add(utf8[index]);
            }
            return strings;
        }
    }

    @Test void everyAllowedMaterialIsActuallyNamedInMinecraft1211BlocksRegistryCode()
            throws Exception {
        Set<String> minecraft=blockIdsFromMinecraftClassFile();
        assertTrue(minecraft.contains("stone_bricks"),"Block registry bytecode is incomplete");
        assertTrue(minecraft.contains("purple_stained_glass_pane"),
            "Minecraft 1.21.1 must contain the purple stained glass pane");
        List<String> invalid=new ArrayList<>();
        for(String material:MaterialCatalog.allowedIds())
            if(!minecraft.contains(material))invalid.add(material);
        Collections.sort(invalid);
        assertTrue(invalid.isEmpty(),
            "Palette has IDs missing from actual Minecraft Blocks.class registrations: "+invalid);
    }

    @Test void unsafeMinecraftBlocksAreDeliberatelyExcluded() throws Exception {
        Set<String> minecraft=blockIdsFromMinecraftClassFile();
        for(String dangerous:List.of("tnt","lava","command_block","bedrock","barrier")){
            assertTrue(minecraft.contains(dangerous),
                "Test must inspect real game registry strings, not an empty stub: "+dangerous);
            assertFalse(MaterialCatalog.allowedIds().contains(dangerous));
        }
    }
}

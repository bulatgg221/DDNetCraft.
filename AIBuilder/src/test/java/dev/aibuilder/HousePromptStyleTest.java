package dev.aibuilder;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class HousePromptStyleTest {
    private static final String BASE = """
        {"name":"Default","operations":[{"type":"house","x":6,"y":0,"z":6,
        "w":17,"d":13,"floors":2,"style":"medieval",
        "roof_style":"gable","wall":"birch_planks"}]}
        """;

    private static JsonObject transformed(String description){
        return JsonParser.parseString(HousePromptStyle.apply(BASE,description))
            .getAsJsonObject().getAsJsonArray("operations").get(0).getAsJsonObject();
    }

    @Test void russianJapaneseOverridesCopiedMedievalDefaults(){
        JsonObject j=transformed("Построй японский одноэтажный дом 19×13 с садом");
        assertEquals("japanese",j.get("style").getAsString());
        assertEquals(1,j.get("floors").getAsInt());
        assertEquals(19,j.get("w").getAsInt());
        assertEquals(13,j.get("d").getAsInt());
        assertTrue(j.get("garden").getAsBoolean());
    }

    @Test void modernFlatRoofBalconySelectedWithoutNewAPICall(){
        JsonObject o=transformed("Построй современную виллу 23x17 на два этажа, плоская крыша и балкон");
        assertEquals("modern",o.get("style").getAsString());
        assertEquals("flat",o.get("roof_style").getAsString());
        assertEquals(23,o.get("w").getAsInt());
        assertEquals(17,o.get("d").getAsInt());
        assertTrue(o.get("balcony").getAsBoolean());
        assertEquals(2,o.get("floors").getAsInt());
    }

    @Test void nordicAlpineAndRusticGetCorrectStyle(){
        assertEquals("nordic",transformed("Скандинавский дом викингов").get("style").getAsString());
        assertEquals("alpine",transformed("Горное шале в Альпах").get("style").getAsString());
        assertEquals("rustic",transformed("Уютный домик в лесу").get("style").getAsString());
        assertEquals("brick",transformed("Большой кирпичный особняк").get("style").getAsString());
    }

    @Test void noExplicitStylePreservesAiArchitectChoice(){
        JsonObject source=transformed("Построй красивый жилой дом");
        assertEquals("medieval",source.get("style").getAsString());
        assertEquals("gable",source.get("roof_style").getAsString());
    }

    @Test void manualNonHousePlanStaysUnchanged(){
        String shapes="{\"name\":\"Tower\",\"operations\":[{\"type\":\"cylinder\",\"block\":\"stone_bricks\",\"r\":3,\"h\":6}]}";
        assertEquals(shapes,HousePromptStyle.apply(shapes,"Построй японский дом"));
    }
}

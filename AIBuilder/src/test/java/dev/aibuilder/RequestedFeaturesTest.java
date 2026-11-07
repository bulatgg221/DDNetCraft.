package dev.aibuilder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class RequestedFeaturesTest {
    @Test void extractsNumericRequirementsFromRussianBrief() {
        assertEquals(5,RequestedFeatures.towers(
            "Построй готический замок с пять башен и тронным залом"));
        assertEquals(3,RequestedFeatures.towers("Построй 3 угловых башни"));
        assertEquals(4,RequestedFeatures.towers("Нужны четыре высоких башни"));
        assertEquals(2,RequestedFeatures.floors("Дом на два этажа"));
        assertEquals(3,RequestedFeatures.floors("Современный дом 3 этажа"));
    }

    @Test void extractsEnglishRequirementsWithoutGuessing(){
        assertEquals(5,RequestedFeatures.towers("a castle with five towers"));
        assertEquals(2,RequestedFeatures.floors("house with two floors"));
        assertEquals(0,RequestedFeatures.towers("build a nice castle"));
        assertEquals(0,RequestedFeatures.floors("building with many rooms"));
        assertEquals(0,RequestedFeatures.towers(null));
    }

    @Test void ignoresUnknownWordsInsteadOfInventingRequiredCount(){
        assertEquals(0,RequestedFeatures.towers("дом с необычными башнями"));
        assertEquals(0,RequestedFeatures.floors("большое многоэтажное здание"));
    }
}

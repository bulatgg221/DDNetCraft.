package dev.aibuilder;

import java.util.Locale;

/** Normalizes familiar stage labels without changing the geometry instructions. */
public final class StageAliases {
    private StageAliases() {}

    public static String normalize(String raw) {
        if(raw==null)throw new IllegalArgumentException("Не указан этап строительства.");
        String stage=raw.trim().toLowerCase(Locale.ROOT)
            .replace(' ','_').replace('-','_');
        return switch(stage){
            case "foundation","base","basement","floor","ground","footings","support" -> "foundation";
            case "walls","wall","shell","structure","structural","frame",
                "columns","pillars","facade" -> "walls";
            case "roof","roofing","roofs","eaves" -> "roof";
            case "openings","opening","cutouts","cutout","carving",
                "window_openings","door_openings","voids" -> "openings";
            case "details","detail","decor","decoration","decorations",
                "accents","trim","windows","doors","lighting" -> "details";
            case "interior","inside","furniture","furnishing","furnishings",
                "rooms","room","stairs","staircases" -> "interior";
            case "landscape","landscaping","outdoor","outside","terrain",
                "gardens","garden","paths","path","nature" -> "landscape";
            default -> throw new IllegalArgumentException(
                "Неизвестный этап строительства: "+raw);
        };
    }
}

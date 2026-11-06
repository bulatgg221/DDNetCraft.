package dev.aibuilder;

/**
 * Normalizes model-generated geometry dimensions without changing the world's
 * configured safety perimeter or allowing unbounded block placement.
 * Normalization only shrinks oversized objects and repairs zero dimensions.
 */
public final class OperationSizes {
    private OperationSizes() {}

    public record Fitted(int w, int h, int d, int r, boolean adjusted) {}

    public static Fitted fit(String type, int x, int y, int z,
                             int rawW, int rawH, int rawD, int rawR,
                             int horizontalLimit, int verticalLimit,
                             int maxBlocks, String facing) {
        if (x < -horizontalLimit || x > horizontalLimit
            || z < -horizontalLimit || z > horizontalLimit
            || y < -verticalLimit || y > verticalLimit) {
            throw new IllegalArgumentException(
                "Начало операции вне безопасных границ мира (x=" + x + ", y=" + y
                + ", z=" + z + ").");
        }

        boolean sized = switch (type) {
            case "box","hollow_box","pyramid","gable_roof","gable_fill",
                 "roof_stairs","hip_roof","table","arch","stairs_run" -> true;
            default -> false;
        };
        int w = sized ? Math.clamp(rawW <= 0 ? 1 : rawW,1,
            Math.max(1, Math.min(80,horizontalLimit-x+1))) : 1;
        int d = sized ? Math.clamp(rawD <= 0 ? 1 : rawD,1,
            Math.max(1, Math.min(80,horizontalLimit-z+1))) : 1;
        int h = Math.clamp(rawH <= 0 ? 1 : rawH,1,
            Math.max(1, Math.min(48,verticalLimit-y+1)));
        int r = Math.clamp(rawR <= 0 ? 2 : rawR,1,20);

        switch(type) {
            case "block","door","bed","chair","line" -> {h=1;r=1;}
            case "tree" -> {
                h=Math.clamp(rawH <= 0 ? 6 : rawH,3,14);
                r=Math.min(r,5);
                int horizontalRoom=Math.min(horizontalLimit-Math.abs(x),
                                            horizontalLimit-Math.abs(z));
                if(horizontalRoom < 1)
                    throw new IllegalArgumentException("Дерево слишком близко к границе мира.");
                r=Math.min(r,horizontalRoom);
                h=Math.min(h,verticalLimit-y-r+1);
                if(h<3)throw new IllegalArgumentException("Дереву не хватает места по высоте.");
            }
            case "sphere" -> {
                int room=Math.min(horizontalLimit-Math.abs(x),
                    Math.min(horizontalLimit-Math.abs(z),verticalLimit-Math.abs(y)));
                if(room<1)throw new IllegalArgumentException("Сфере не хватает места.");
                r=Math.min(r,room);
            }
            case "cylinder","hollow_cylinder" -> {
                int room=Math.min(horizontalLimit-Math.abs(x),
                                  horizontalLimit-Math.abs(z));
                if(room<1)throw new IllegalArgumentException("Башня слишком близко к границе.");
                r=Math.min(r,room);
            }
            case "table" -> {w=Math.min(w,8); d=Math.min(d,6);h=1;}
            case "stairs_run" -> {
                h=Math.min(h,20);
                String dir=facing==null?"north":facing;
                int space=switch(dir){
                    case "north" -> z+horizontalLimit+1;
                    case "south" -> horizontalLimit-z+1;
                    case "west" -> x+horizontalLimit+1;
                    case "east" -> horizontalLimit-x+1;
                    default -> h;
                };
                h=Math.min(h,Math.max(1,space));
                if(dir.equals("east")||dir.equals("west")){
                    w=Math.min(w,horizontalLimit-z+1);
                    d=1;
                }
                else w=Math.min(w,horizontalLimit-x+1);
            }
            case "arch" -> {
                if(w<3 || h<3) throw new IllegalArgumentException(
                    "Для арки нужны минимум 3 блока ширины и высоты.");
            }
            case "gable_fill" -> {
                if(w<5)throw new IllegalArgumentException(
                    "Фронтон должен быть шириной от 5 блоков.");
                d=Math.min(d,3);
                w=Math.min(w,Math.max(5,2*(verticalLimit-y)+1));
                h=1;
            }
            case "gable_roof","roof_stairs" -> {
                w=Math.min(w,Math.max(1,2*(verticalLimit-y)+1));
                h=1;
            }
            case "hip_roof" -> {
                int maxSpan=Math.max(1,2*(verticalLimit-y)+1);
                w=Math.min(w,maxSpan);d=Math.min(d,maxSpan);h=1;
            }
            default -> { }
        }

        // Respect the existing maximum number of blocks even if a model emits
        // an enormous filled volume. A final total-plan limit still applies.
        if (type.equals("box") || type.equals("pyramid")) {
            long volume=(long)w*h*d;
            if(volume>maxBlocks){
                double scale=Math.cbrt((double)maxBlocks/volume);
                w=Math.max(1,(int)Math.floor(w*scale));
                h=Math.max(1,(int)Math.floor(h*scale));
                d=Math.max(1,(int)Math.floor(d*scale));
                while((long)w*h*d>maxBlocks) {
                    if(w>=h && w>=d && w>1)w--;
                    else if(d>=h && d>1)d--;
                    else if(h>1)h--;
                    else break;
                }
            }
        }
        boolean adjusted = switch(type) {
            case "block","door","bed","chair","line" -> false;
            case "sphere" -> r!=rawR;
            case "cylinder","hollow_cylinder","tree" -> h!=rawH || r!=rawR;
            default -> w!=rawW || h!=rawH || d!=rawD;
        };
        return new Fitted(w,h,d,r,adjusted);
    }
}

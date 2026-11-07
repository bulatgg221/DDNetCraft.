package dev.aibuilder.planning;
import com.google.gson.JsonObject;
import java.util.*;

public record Blueprint(String name, String mode, JsonObject specification,
                        List<Component> components, List<Section> sections, List<JsonObject> operations, String json) {
    public record Bounds(int x,int y,int z,int w,int h,int d) {
        public boolean contains(Pos p) { return p.x()>=x && (long)p.x()< (long)x+w && p.y()>=y && (long)p.y()<(long)y+h && p.z()>=z && (long)p.z()<(long)z+d; }
    }
    public record Component(String id,String kind,Bounds bounds,List<String> connectedTo,Pos access) {}
    public record Section(String id,List<String> dependsOn) {}
}

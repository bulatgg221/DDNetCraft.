package dev.aibuilder.planning;
public record Pos(int x, int y, int z) {
    public Pos add(int dx,int dy,int dz) { return new Pos(Math.addExact(x,dx),Math.addExact(y,dy),Math.addExact(z,dz)); }
}

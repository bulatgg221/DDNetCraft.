package dev.aibuilder.planning;

public record Limits(int operations, int blocks, int horizontal, int vertical, int perTick) {
    public static final int HARD_OPERATIONS = 512;
    public static Limits defaults() { return new Limits(400, 30000, 48, 32, 300); }
    public Limits {
        if (operations < 1 || operations > 512 || blocks < 1 || blocks > 100000 ||
            horizontal < 8 || horizontal > 128 || vertical < 8 || vertical > 128 ||
            perTick < 1 || perTick > 1000) throw new IllegalArgumentException("Настройки за безопасными пределами.");
    }
    public long attempts() { return (long)blocks * 8; }
    public void position(Pos p) {
        if (Math.abs((long)p.x()) > horizontal || Math.abs((long)p.z()) > horizontal ||
            Math.abs((long)p.y()) > vertical) throw new IllegalArgumentException("Постройка превышает разрешённую область: " + p);
    }
}

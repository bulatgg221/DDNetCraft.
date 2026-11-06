package dev.ddnetcraft.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/** Outlined 3D representation of the 2D DDRace arena and client projectiles. */
public final class DDNetRender {
    private DDNetRender() {}

    public static void renderWorld(WorldRenderContext context) {
        DDNetClient game = DDNetClient.INSTANCE;
        if (game == null || !game.isEnabled() || game.map() == null ||
                context.matrixStack() == null || context.consumers() == null) return;
        MatrixStack matrices = context.matrixStack();
        Vec3d camera = context.camera().getPos();
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
        DDNetMap map = game.map();
        for (int y = 0; y < map.height; y++) {
            for (int x = 0; x < map.width; x++) {
                char tile = map.tile(x, y);
                if (tile == '.') continue;
                float[] rgb = color(tile);
                double px = game.worldX(x), py = game.worldY(y), z = game.worldZ();
                WorldRenderer.drawBox(matrices, lines, px, py, z - 0.45,
                    px + 1, py + 1, z + 0.45, rgb[0], rgb[1], rgb[2], 0.85f);
            }
        }
        DDNetClient.Hook hook = game.hook();
        if (hook != null) {
            // A line made of tiny boxes; no custom shaders or rendering entities.
            double fromX = game.localX(), fromY = game.localY() + 1.0;
            dottedLine(matrices, lines, game, fromX, fromY, hook.x, hook.y, 0.2f, 0.95f, 1f);
            box(matrices, lines, game.worldX(hook.x), game.worldY(hook.y), game.worldZ(), 0.12, 0.2f, 0.95f, 1f);
        }
        for (DDNetClient.Projectile p : game.projectiles()) {
            float red = p.weapon == DDNetClient.Weapon.GRENADE ? 1f : 1f;
            float green = p.weapon == DDNetClient.Weapon.GRENADE ? 0.55f : 0.95f;
            box(matrices, lines, game.worldX(p.x), game.worldY(p.y),
                game.worldZ(), 0.16, red, green, 0.15f);
        }
        for (DDNetClient.Beam beam : game.beams()) {
            dottedLine(matrices, lines, game,
                beam.x1(), beam.y1(), beam.x2(), beam.y2(), 1, 0.2f, 0.4f);
        }
        matrices.pop();
    }

    private static void dottedLine(MatrixStack matrices, VertexConsumer vertex, DDNetClient game,
                                   double x1, double y1, double x2, double y2,
                                   float r, float g, float b) {
        int steps = Math.max(1, Math.min(96, (int)(Math.hypot(x2 - x1, y2 - y1) * 5)));
        for (int i = 0; i <= steps; i++) {
            double t = (double)i / steps;
            box(matrices, vertex, game.worldX(x1 + (x2 - x1) * t),
                game.worldY(y1 + (y2 - y1) * t), game.worldZ(), 0.055, r, g, b);
        }
    }
    private static void box(MatrixStack matrix, VertexConsumer vertex,
                            double x, double y, double z, double radius,
                            float r, float g, float b) {
        WorldRenderer.drawBox(matrix, vertex, x - radius, y - radius, z - radius,
            x + radius, y + radius, z + radius, r, g, b, 1f);
    }
    private static float[] color(char tile) {
        return switch (tile) {
            case 'U' -> new float[]{1f, 0.4f, 0.1f};
            case 'H' -> new float[]{0.65f, 0.75f, 0.9f};
            case 'K' -> new float[]{1f, 0.08f, 0.13f};
            case 'F' -> new float[]{0.22f, 0.6f, 1f};
            case 'E' -> new float[]{0.2f, 1f, 0.15f};
            case 'C' -> new float[]{1f, 0.85f, 0.1f};
            case 'L' -> new float[]{0.8f, 0.3f, 1f};
            default -> new float[]{0.5f, 0.5f, 0.5f};
        };
    }

    public static void renderHud(DrawContext draw, MinecraftClient mc) {
        DDNetClient game = DDNetClient.INSTANCE;
        if (game == null || !game.isEnabled() || mc.player == null || game.map() == null) return;
        int width = mc.getWindow().getScaledWidth();
        draw.fill(5, 5, 260, 78, 0xA7000000);
        draw.drawText(mc.textRenderer, "DDNetCraft | " + game.map().name, 12, 12, 0xFF54DFF8, true);
        draw.drawText(mc.textRenderer,
            "Time " + game.timeLabel() + " | Deaths " + game.deaths(), 12, 25, 0xFFFFFFFF, true);
        draw.drawText(mc.textRenderer,
            "Weapon " + game.weapon() + " | Hook " +
                (game.hook() == null ? "READY" : game.hook().attached ? "ATTACHED" : "FLYING"),
            12, 38, 0xFFFFE08A, true);
        draw.drawText(mc.textRenderer, game.frozenTicks() > 0 ? "FREEZE " + game.frozenTicks() + " ticks"
            : game.finished() ? "FINISHED" : game.grounded() ? "GROUND" : "AIR", 12, 51,
            game.frozenTicks() > 0 ? 0xFF66BFFF : 0xFF8BFA99, true);
        draw.drawText(mc.textRenderer,
            "G toggle | Q hook | J shoot | 1-3 weapon | R restart", 12, 65, 0xFFCCCCCC, true);
        // Always-visible 2D map gives spatial awareness regardless of camera direction.
        renderMiniMap(draw, game, width);
        if (game.messageTicks() > 0) {
            String message = game.notice();
            int textWidth = mc.textRenderer.getWidth(message);
            draw.drawText(mc.textRenderer, Text.literal(message),
                Math.max(0, (width - textWidth) / 2), 92, 0xFFFFFF75, true);
        }
    }
    private static void renderMiniMap(DrawContext draw, DDNetClient game, int screenWidth) {
        DDNetMap map = game.map();
        if (map == null) return;
        int scale = Math.max(2, Math.min(5, (screenWidth - 40) / map.width));
        int left = Math.max(5, screenWidth - map.width * scale - 12);
        int top = 12;
        draw.fill(left - 3, top - 3, left + map.width * scale + 3,
            top + map.height * scale + 3, 0xCB000000);
        for (int y = 0; y < map.height; y++) {
            for (int x = 0; x < map.width; x++) {
                char tile = map.tile(x, y);
                if (tile == '.') continue;
                int rgb = switch (tile) {
                    case 'U' -> 0xFFEB8C3A;
                    case 'H' -> 0xFF9EB8CF;
                    case 'K' -> 0xFFEF3333;
                    case 'F' -> 0xFF358BEF;
                    case 'E' -> 0xFF4DDE4C;
                    case 'C' -> 0xFFFFCE45;
                    case 'L' -> 0xFFA45FE8;
                    default -> 0xFF777777;
                };
                int px = left + x * scale, py = top + (map.height - 1 - y) * scale;
                draw.fill(px, py, px + scale, py + scale, rgb);
            }
        }
        int px = left + (int)(game.localX() * scale);
        int py = top + (int)((map.height - game.localY()) * scale);
        draw.fill(px - 2, py - 4, px + 2, py + 1, 0xFFFFFFFF);
        if (game.hook() != null) {
            int hx = left + (int)(game.hook().x * scale);
            int hy = top + (int)((map.height - game.hook().y) * scale);
            draw.fill(hx - 1, hy - 1, hx + 2, hy + 2, 0xFF54F4FF);
        }
    }
}

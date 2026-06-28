package com.taskblocks.client;

import com.taskblocks.script.ScriptRunner;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class ScriptOverlay {

    private static boolean visible = true;

    public static boolean isVisible() { return visible; }
    public static void setVisible(boolean v) { visible = v; }
    public static void toggle() { visible = !visible; }

    public static void register() {
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            if (!visible) return;
            if (!ScriptRunner.isRunning()) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.options.hudHidden) return;

            render(drawContext, client);
        });
    }

    private static void render(DrawContext ctx, MinecraftClient client) {
        String scriptName = ScriptRunner.getRunningScriptName();
        if (scriptName == null) return;

        long elapsed   = ScriptRunner.getElapsedSeconds();
        long minutes   = elapsed / 60;
        long seconds   = elapsed % 60;
        String time    = String.format("%02d:%02d", minutes, seconds);
        String state   = "▶ Running";

        String nameLine  = "§b" + scriptName;
        String timeLine  = "§7" + time + "  §a" + state;

        int screenW      = client.getWindow().getScaledWidth();
        int nameW        = client.textRenderer.getWidth(nameLine);
        int timeW        = client.textRenderer.getWidth(timeLine);
        int boxW         = Math.max(nameW, timeW) + 12;
        int boxH         = 26;
        int margin       = 6;
        int x            = screenW - boxW - margin;
        int y            = margin;

        // Background
        ctx.fill(x, y, x + boxW, y + boxH, 0xAA0A0F1A);

        // Border left accent
        ctx.fill(x, y, x + 2, y + boxH, 0xFF3B82F6);

        // Border full
        ctx.fill(x,          y,          x + boxW, y + 1,      0xFF3B82F6);
        ctx.fill(x,          y + boxH-1, x + boxW, y + boxH,   0xFF3B82F6);
        ctx.fill(x + boxW-1, y,          x + boxW, y + boxH,   0xFF3B82F6);

        // Script name
        ctx.drawTextWithShadow(client.textRenderer,
            net.minecraft.text.Text.literal(nameLine),
            x + 6, y + 4, 0xFFFFFFFF);

        // Time + state
        ctx.drawTextWithShadow(client.textRenderer,
            net.minecraft.text.Text.literal(timeLine),
            x + 6, y + 14, 0xFFFFFFFF);
    }
}
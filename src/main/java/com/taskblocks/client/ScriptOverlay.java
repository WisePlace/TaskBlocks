package com.taskblocks.client;

import com.taskblocks.script.LookRecorder;
import com.taskblocks.script.MacroRecorder;
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
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.options.hudHidden) return;

            // Both recorders' countdown/status, and print(), render
            // regardless of the overlay visibility toggle — they're their
            // own distinct indicators, not the normal script-running
            // display below.
            if (MacroRecorder.isActive()) {
                renderMacroRecorder(drawContext, client);
            }

            if (LookRecorder.isActive()) {
                renderLookRecorder(drawContext, client);
            }

            String printedText = ScriptDisplay.getText();
            if (printedText != null) {
                int baseColor = ScriptDisplay.getColor();
                int alpha = ScriptDisplay.getAlpha();
                int fadedColor = (alpha << 24) | (baseColor & 0x00FFFFFF);
                drawBigCenteredText(drawContext, client, printedText, 6f, fadedColor);
            }

            if (!visible) return;
            if (!ScriptRunner.isRunning()) return;

            render(drawContext, client);
        });
    }

    private static void renderMacroRecorder(DrawContext ctx, MinecraftClient client) {
        int screenW = client.getWindow().getScaledWidth();

        if (MacroRecorder.isCountingDown()) {
            int seconds = MacroRecorder.getCountdownSecondsRemaining();
            String text = seconds > 0 ? String.valueOf(seconds) : "GO!";
            drawBigCenteredText(ctx, client, text, 6f, 0xFFFFD700);

        } else if (MacroRecorder.isRecording()) {
            String text = "\u25CF REC \u2014 press " + MacroRecorder.STOP_KEY + " to stop";
            int textWidth = client.textRenderer.getWidth(text);
            int x = (screenW - textWidth) / 2;
            int y = 20;

            ctx.fill(x - 6, y - 4, x + textWidth + 6, y + 12, 0xAA000000);
            ctx.drawTextWithShadow(client.textRenderer, Text.literal(text), x, y, 0xFFFF5555);
        }
    }

    private static void renderLookRecorder(DrawContext ctx, MinecraftClient client) {
        int screenW = client.getWindow().getScaledWidth();

        if (LookRecorder.isCountingDown()) {
            int seconds = LookRecorder.getCountdownSecondsRemaining();
            String text = seconds > 0 ? String.valueOf(seconds) : "GO!";
            drawBigCenteredText(ctx, client, text, 6f, 0xFF55FFFF);

        } else if (LookRecorder.isRecording()) {
            String text = "\u25CF REC (look only) \u2014 press " + LookRecorder.STOP_KEY + " to stop, copies to clipboard";
            int textWidth = client.textRenderer.getWidth(text);
            int x = (screenW - textWidth) / 2;
            int y = 20;

            ctx.fill(x - 6, y - 4, x + textWidth + 6, y + 12, 0xAA000000);
            ctx.drawTextWithShadow(client.textRenderer, Text.literal(text), x, y, 0xFF55FFFF);
        }
    }

    // Draws text scaled up and centered on screen — used by both
    // recorder countdowns and the print() action.
    private static void drawBigCenteredText(DrawContext ctx, MinecraftClient client,
            String text, float scale, int color) {
        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        float centerX = screenW / 2f;
        float centerY = screenH / 2f;

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(centerX, centerY);
        ctx.getMatrices().scale(scale, scale);
        ctx.getMatrices().translate(-centerX, -centerY);
        ctx.drawCenteredTextWithShadow(client.textRenderer, text,
            (int) centerX, (int) (centerY - client.textRenderer.fontHeight / 2f), color);
        ctx.getMatrices().popMatrix();
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
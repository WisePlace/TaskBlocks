package com.taskblocks.client.gui;

import com.taskblocks.client.ScriptOverlay;
import com.taskblocks.client.TaskBlocksClient;
import com.taskblocks.script.AntiAfk;
import com.taskblocks.script.LookRecorder;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

// Settings screen reached from the J menu's Settings button: overlay
// toggle, J menu keybind rebind, look recorder detail mode + trigger,
// and anti-AFK controls.
public class SettingsScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 210;
    private static final int PAD     = 14;
    private static final int HALF_W  = (PANEL_W - PAD * 2 - 10) / 2;

    private static final int COL_BG      = 0xFF111827;
    private static final int COL_HEADER  = 0xFF0F172A;
    private static final int COL_BORDER  = 0xFF3B82F6;
    private static final int COL_ACCENT  = 0xFF60A5FA;
    private static final int COL_RECORD  = 0xFFFF5555;
    private static final int COL_OVERLAY = 0xCC000000;

    private static final int AFK_INTERVAL_MIN = 5;
    private static final int AFK_INTERVAL_MAX = 300;
    private static final float AFK_NUDGE_MIN = 0.05f;
    private static final float AFK_NUDGE_MAX = 2.0f;

    private final Screen parent;
    private int px, py;

    private boolean listeningForKey = false;
    private ButtonWidget rebindButton;

    private int recordButtonX, recordButtonY, recordButtonW, recordButtonH;

    public SettingsScreen(Screen parent) {
        super(Text.literal("TaskBlocks Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        px = (this.width - PANEL_W) / 2;
        py = (this.height - PANEL_H) / 2;

        addDrawableChild(ButtonWidget.builder(
            Text.literal(ScriptOverlay.isVisible() ? "Overlay: ON" : "Overlay: OFF"),
            btn -> {
                ScriptOverlay.toggle();
                btn.setMessage(Text.literal(
                    ScriptOverlay.isVisible() ? "Overlay: ON" : "Overlay: OFF"));
            }
        ).dimensions(px + PAD, py + 40, HALF_W, 20).build());

        rebindButton = ButtonWidget.builder(
            Text.literal("Menu Key: " + keyDisplayName()),
            btn -> startListening()
        ).dimensions(px + PAD + HALF_W + 10, py + 40, HALF_W, 20).build();
        addDrawableChild(rebindButton);

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Look Detail: " + modeLabel(LookRecorder.getMode())),
            btn -> {
                LookRecorder.setMode(nextMode(LookRecorder.getMode()));
                btn.setMessage(Text.literal("Look Detail: " + modeLabel(LookRecorder.getMode())));
            }
        ).dimensions(px + PAD, py + 70, HALF_W, 20).build());

        recordButtonX = px + PAD + HALF_W + 10;
        recordButtonY = py + 70;
        recordButtonW = HALF_W;
        recordButtonH = 20;
        addDrawableChild(ButtonWidget.builder(
            Text.literal("\u25CF Record Look").styled(style -> style.withBold(true)),
            btn -> {
                LookRecorder.start();
                this.client.setScreen(null);
            }
        ).dimensions(recordButtonX, recordButtonY, recordButtonW, recordButtonH).build());

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Anti-AFK: " + (AntiAfk.isEnabled() ? "ON" : "OFF")),
            btn -> {
                AntiAfk.setEnabled(!AntiAfk.isEnabled());
                btn.setMessage(Text.literal("Anti-AFK: " + (AntiAfk.isEnabled() ? "ON" : "OFF")));
            }
        ).dimensions(px + PAD, py + 100, PANEL_W - PAD * 2, 20).build());

        double intervalRatio = (AntiAfk.getIntervalSeconds() - AFK_INTERVAL_MIN)
            / (double) (AFK_INTERVAL_MAX - AFK_INTERVAL_MIN);
        addDrawableChild(new SliderWidget(px + PAD, py + 130, HALF_W, 20,
                intervalLabel(AntiAfk.getIntervalSeconds()), intervalRatio) {
            @Override
            protected void updateMessage() {
                int seconds = AFK_INTERVAL_MIN
                    + (int) Math.round(this.value * (AFK_INTERVAL_MAX - AFK_INTERVAL_MIN));
                this.setMessage(intervalLabel(seconds));
            }

            @Override
            protected void applyValue() {
                int seconds = AFK_INTERVAL_MIN
                    + (int) Math.round(this.value * (AFK_INTERVAL_MAX - AFK_INTERVAL_MIN));
                AntiAfk.setIntervalSeconds(seconds);
            }
        });

        double nudgeRatio = (AntiAfk.getNudgeRangeDegrees() - AFK_NUDGE_MIN)
            / (double) (AFK_NUDGE_MAX - AFK_NUDGE_MIN);
        addDrawableChild(new SliderWidget(px + PAD + HALF_W + 10, py + 130, HALF_W, 20,
                nudgeLabel(AntiAfk.getNudgeRangeDegrees()), nudgeRatio) {
            @Override
            protected void updateMessage() {
                float degrees = (float) (AFK_NUDGE_MIN + this.value * (AFK_NUDGE_MAX - AFK_NUDGE_MIN));
                this.setMessage(nudgeLabel(degrees));
            }

            @Override
            protected void applyValue() {
                float degrees = (float) (AFK_NUDGE_MIN + this.value * (AFK_NUDGE_MAX - AFK_NUDGE_MIN));
                AntiAfk.setNudgeRangeDegrees(degrees);
            }
        });

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Back"),
            btn -> this.client.setScreen(parent)
        ).dimensions(px + PANEL_W / 2 - 35, py + PANEL_H - 30, 70, 18).build());
    }

    private static Text intervalLabel(int seconds) {
        return Text.literal("AFK Interval: " + seconds + "s");
    }

    private static Text nudgeLabel(float degrees) {
        return Text.literal("AFK Nudge: "
            + String.format(java.util.Locale.ROOT, "%.2f", degrees) + "\u00b0");
    }

    private String modeLabel(LookRecorder.DetailMode mode) {
        return switch (mode) {
            case DETAILED -> "Detailed";
            case MEDIUM -> "Medium";
            case LOW -> "Low";
        };
    }

    private LookRecorder.DetailMode nextMode(LookRecorder.DetailMode mode) {
        return switch (mode) {
            case DETAILED -> LookRecorder.DetailMode.MEDIUM;
            case MEDIUM -> LookRecorder.DetailMode.LOW;
            case LOW -> LookRecorder.DetailMode.DETAILED;
        };
    }

    private String keyDisplayName() {
        return TaskBlocksClient.openMenuKey.getBoundKeyLocalizedText().getString();
    }

    private void startListening() {
        listeningForKey = true;
        rebindButton.setMessage(Text.literal("Press a key... (Esc)"));
    }

    private void applyKey(InputUtil.Key key) {
        TaskBlocksClient.openMenuKey.setBoundKey(key);
        KeyBinding.updateKeysByCode();
        this.client.options.write();
        listeningForKey = false;
        rebindButton.setMessage(Text.literal("Menu Key: " + keyDisplayName()));
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (listeningForKey) {
            int code = input.key();
            if (code == 256) {
                listeningForKey = false;
                rebindButton.setMessage(Text.literal("Menu Key: " + keyDisplayName()));
                return true;
            }
            applyKey(InputUtil.Type.KEYSYM.createFromCode(code));
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, COL_OVERLAY);

        ctx.fill(px, py, px + PANEL_W, py + PANEL_H, COL_BG);
        ctx.fill(px, py, px + PANEL_W, py + 30, COL_HEADER);
        ctx.fill(px, py, px + PANEL_W, py + 1, COL_BORDER);
        ctx.fill(px, py + PANEL_H - 1, px + PANEL_W, py + PANEL_H, COL_BORDER);
        ctx.fill(px, py, px + 1, py + PANEL_H, COL_BORDER);
        ctx.fill(px + PANEL_W - 1, py, px + PANEL_W, py + PANEL_H, COL_BORDER);

        ctx.drawTextWithShadow(textRenderer, Text.literal("Settings"),
            px + PAD, py + 10, COL_ACCENT);

        super.render(ctx, mouseX, mouseY, delta);

        ctx.fill(recordButtonX - 1, recordButtonY - 1, recordButtonX + recordButtonW + 1, recordButtonY, COL_RECORD);
        ctx.fill(recordButtonX - 1, recordButtonY + recordButtonH, recordButtonX + recordButtonW + 1, recordButtonY + recordButtonH + 1, COL_RECORD);
        ctx.fill(recordButtonX - 1, recordButtonY - 1, recordButtonX, recordButtonY + recordButtonH + 1, COL_RECORD);
        ctx.fill(recordButtonX + recordButtonW, recordButtonY - 1, recordButtonX + recordButtonW + 1, recordButtonY + recordButtonH + 1, COL_RECORD);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
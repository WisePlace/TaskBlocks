package com.taskblocks.client.gui;

import com.taskblocks.client.ScriptOverlay;
import com.taskblocks.client.TaskBlocksClient;
import com.taskblocks.script.LookRecorder;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

// ============================================================
// Settings screen reached from the J menu's Settings button.
// Holds the overlay visibility toggle (moved here from the main
// menu), a rebind control for the J menu's own open keybind
// (keyboard only), using the same mechanism vanilla's Controls
// screen uses: KeyBinding.setBoundKey + updateKeysByCode + persist,
// and a trigger for the standalone look-only recorder (copies to
// clipboard rather than saving into a script), with a cycling
// button choosing how much its output gets path-simplified.
// ============================================================
public class SettingsScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 200;
    private static final int PAD     = 14;

    private static final int COL_BG      = 0xFF111827;
    private static final int COL_HEADER  = 0xFF0F172A;
    private static final int COL_BORDER  = 0xFF3B82F6;
    private static final int COL_ACCENT  = 0xFF60A5FA;
    private static final int COL_OVERLAY = 0xCC000000;

    private final Screen parent;
    private int px, py;

    private boolean listeningForKey = false;
    private ButtonWidget rebindButton;

    public SettingsScreen(Screen parent) {
        super(Text.literal("TaskBlocks Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        px = (this.width - PANEL_W) / 2;
        py = (this.height - PANEL_H) / 2;

        // Overlay toggle — moved here from the main menu footer
        addDrawableChild(ButtonWidget.builder(
            Text.literal(ScriptOverlay.isVisible() ? "Overlay: ON" : "Overlay: OFF"),
            btn -> {
                ScriptOverlay.toggle();
                btn.setMessage(Text.literal(
                    ScriptOverlay.isVisible() ? "Overlay: ON" : "Overlay: OFF"));
            }
        ).dimensions(px + PAD, py + 40, PANEL_W - PAD * 2, 20).build());

        // J menu keybind rebind
        rebindButton = ButtonWidget.builder(
            Text.literal("Open Menu Key: " + keyDisplayName()),
            btn -> startListening()
        ).dimensions(px + PAD, py + 70, PANEL_W - PAD * 2, 20).build();
        addDrawableChild(rebindButton);

        // Look detail mode — cycles Detailed -> Medium -> Low
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Look Detail: " + modeLabel(LookRecorder.getMode())),
            btn -> {
                LookRecorder.setMode(nextMode(LookRecorder.getMode()));
                btn.setMessage(Text.literal("Look Detail: " + modeLabel(LookRecorder.getMode())));
            }
        ).dimensions(px + PAD, py + 100, PANEL_W - PAD * 2, 20).build());

        // Look recorder — records camera movement only and copies
        // the result to the clipboard
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Record Look Movement"),
            btn -> {
                LookRecorder.start();
                this.client.setScreen(null);
            }
        ).dimensions(px + PAD, py + 130, PANEL_W - PAD * 2, 20).build());

        // Back
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Back"),
            btn -> this.client.setScreen(parent)
        ).dimensions(px + PANEL_W / 2 - 35, py + PANEL_H - 30, 70, 18).build());
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
        rebindButton.setMessage(Text.literal("Press a key... (Esc to cancel)"));
    }

    private void applyKey(InputUtil.Key key) {
        TaskBlocksClient.openMenuKey.setBoundKey(key);
        KeyBinding.updateKeysByCode();
        this.client.options.write();
        listeningForKey = false;
        rebindButton.setMessage(Text.literal("Open Menu Key: " + keyDisplayName()));
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (listeningForKey) {
            int code = input.key();
            if (code == 256) { // Escape cancels rebinding, doesn't close the screen
                listeningForKey = false;
                rebindButton.setMessage(Text.literal("Open Menu Key: " + keyDisplayName()));
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
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
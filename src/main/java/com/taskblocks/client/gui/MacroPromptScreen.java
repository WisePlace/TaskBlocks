package com.taskblocks.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

// ============================================================
// Shown right after a new script is created, offering to jump into
// the (future) Macro Recorder for it. Currently a placeholder — the
// "Yes" button doesn't record anything yet, both options just return
// to the script menu. Kept as its own screen so wiring in the real
// recorder later is a one-line change in the "Yes" button's handler.
// ============================================================
public class MacroPromptScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int PANEL_H = 140;
    private static final int PAD     = 14;

    private static final int COL_BG        = 0xFF111827;
    private static final int COL_HEADER    = 0xFF0F172A;
    private static final int COL_BORDER    = 0xFF3B82F6;
    private static final int COL_ACCENT    = 0xFF60A5FA;
    private static final int COL_DARK_GRAY = 0xFF64748B;
    private static final int COL_OVERLAY   = 0xCC000000;

    private final String scriptName;
    private int px, py;

    public MacroPromptScreen(String scriptName) {
        super(Text.literal("Record a Macro?"));
        this.scriptName = scriptName;
    }

    @Override
    protected void init() {
        px = (this.width - PANEL_W) / 2;
        py = (this.height - PANEL_H) / 2;

        int fieldW = PANEL_W - PAD * 2;
        int btnY = py + PANEL_H - 34;

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Yes"),
            btn -> this.client.setScreen(new ScriptMenuScreen())
        ).dimensions(px + PAD, btnY, (fieldW - 10) / 2, 20).build());

        addDrawableChild(ButtonWidget.builder(
            Text.literal("No"),
            btn -> this.client.setScreen(new ScriptMenuScreen())
        ).dimensions(px + PAD + (fieldW - 10) / 2 + 10, btnY, (fieldW - 10) / 2, 20).build());
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

        ctx.drawTextWithShadow(textRenderer, Text.literal("Script Created!"),
            px + PAD, py + 10, COL_ACCENT);

        ctx.drawTextWithShadow(textRenderer,
            Text.literal("Record a macro for \"" + scriptName + "\" now?"),
            px + PAD, py + 40, COL_DARK_GRAY);
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("Macro Recorder (BETA)"),
            px + PAD, py + 55, COL_DARK_GRAY);

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
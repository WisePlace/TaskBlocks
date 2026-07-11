package com.taskblocks.client.gui;

import com.taskblocks.client.TaskBlocksClient;
import com.taskblocks.script.ScriptLoader;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

// ============================================================
// "New Script" screen reached from the J menu. Collects the basic
// header fields (name, author, debug, start_stop_key), writes a new
// .tbs file via ScriptLoader, then reloads and reopens the menu so
// the new script shows up immediately.
// ============================================================
public class CreateScriptScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 240;
    private static final int PAD     = 14;

    private static final int COL_BG     = 0xFF111827;
    private static final int COL_HEADER = 0xFF0F172A;
    private static final int COL_BORDER = 0xFF3B82F6;
    private static final int COL_ACCENT = 0xFF60A5FA;
    private static final int COL_RED    = 0xFFFF6B6B;
    private static final int COL_DARK_GRAY = 0xFF64748B;
    private static final int COL_OVERLAY = 0xCC000000;

    private int px, py;

    private TextFieldWidget nameField;
    private TextFieldWidget authorField;
    private TextFieldWidget keyField;
    private ButtonWidget debugButton;
    private boolean debugEnabled = false;

    private int nameLabelY, authorLabelY, keyLabelY;

    private String errorMessage = null;

    public CreateScriptScreen() {
        super(Text.literal("New Script"));
    }

    @Override
    protected void init() {
        px = (this.width - PANEL_W) / 2;
        py = (this.height - PANEL_H) / 2;

        int fieldW = PANEL_W - PAD * 2;
        int y = py + 40;

        nameLabelY = y;
        y += 11;
        nameField = new TextFieldWidget(textRenderer, px + PAD, y, fieldW, 20, Text.literal(""));
        nameField.setMaxLength(64);
        addDrawableChild(nameField);
        setInitialFocus(nameField);
        y += 28;

        authorLabelY = y;
        y += 11;
        authorField = new TextFieldWidget(textRenderer, px + PAD, y, fieldW, 20, Text.literal(""));
        authorField.setMaxLength(64);
        authorField.setText("You");
        addDrawableChild(authorField);
        y += 28;

        debugButton = ButtonWidget.builder(
            Text.literal("Debug: OFF"),
            btn -> {
                debugEnabled = !debugEnabled;
                btn.setMessage(Text.literal("Debug: " + (debugEnabled ? "ON" : "OFF")));
            }
        ).dimensions(px + PAD, y, fieldW, 20).build();
        addDrawableChild(debugButton);
        y += 30;

        keyLabelY = y;
        y += 11;
        keyField = new TextFieldWidget(textRenderer, px + PAD, y, fieldW, 20, Text.literal(""));
        keyField.setMaxLength(32);
        addDrawableChild(keyField);
        y += 30;

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Create"),
            btn -> create()
        ).dimensions(px + PAD, y, (fieldW - 10) / 2, 20).build());

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Cancel"),
            btn -> this.client.setScreen(new ScriptMenuScreen())
        ).dimensions(px + PAD + (fieldW - 10) / 2 + 10, y, (fieldW - 10) / 2, 20).build());
    }

    private void create() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            errorMessage = "Script name cannot be empty";
            return;
        }

        String author = authorField.getText().trim();
        if (author.isEmpty()) author = "You";

        String key = keyField.getText().trim();

        String fileName = ScriptLoader.createScript(name, author, debugEnabled, key);
        if (fileName == null) {
            errorMessage = "Failed to create script - check the log";
            return;
        }

        TaskBlocksClient.reloadScripts();
        this.client.setScreen(new MacroPromptScreen(name, fileName));
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

        ctx.drawTextWithShadow(textRenderer, Text.literal("New Script"),
            px + PAD, py + 10, COL_ACCENT);

        ctx.drawTextWithShadow(textRenderer, Text.literal("Script Name"),
            px + PAD, nameLabelY, COL_DARK_GRAY);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Author"),
            px + PAD, authorLabelY, COL_DARK_GRAY);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Start/Stop Key (optional, e.g. O or CTRL+K)"),
            px + PAD, keyLabelY, COL_DARK_GRAY);

        if (errorMessage != null) {
            ctx.drawTextWithShadow(textRenderer, Text.literal(errorMessage),
                px + PAD, py + PANEL_H - 16, COL_RED);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
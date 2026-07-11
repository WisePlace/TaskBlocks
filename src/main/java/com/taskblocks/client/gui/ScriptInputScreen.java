package com.taskblocks.client.gui;

import java.util.function.Consumer;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

// ============================================================
// Minimal, semi-transparent text input prompt used by the input()
// action. Submitting (Enter or the Submit button) or cancelling
// (Escape, or the screen closing another way) both funnel through
// the same completion callback exactly once, so whatever script
// thread is polling on it always gets unblocked.
// ============================================================
public class ScriptInputScreen extends Screen {

    private static final int BOX_W = 280;
    private static final int BOX_H = 80;

    private static final int COL_BG     = 0xEE111827;
    private static final int COL_BORDER = 0xFF3B82F6;
    private static final int COL_ACCENT = 0xFF60A5FA;

    private final String prompt;
    private final String defaultValue;
    private final Consumer<String> onComplete;

    private TextFieldWidget textField;
    private boolean completed = false;

    public ScriptInputScreen(String prompt, String defaultValue, Consumer<String> onComplete) {
        super(Text.literal("TaskBlocks Input"));
        this.prompt = prompt;
        this.defaultValue = defaultValue;
        this.onComplete = onComplete;
    }

    @Override
    protected void init() {
        int px = (this.width - BOX_W) / 2;
        int py = (this.height - BOX_H) / 2;

        textField = new TextFieldWidget(textRenderer, px + 10, py + 30, BOX_W - 20, 20, Text.literal(""));
        textField.setMaxLength(256);
        textField.setText(defaultValue == null ? "" : defaultValue);
        addDrawableChild(textField);
        setInitialFocus(textField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Submit"), btn -> submit())
            .dimensions(px + BOX_W - 70, py + BOX_H - 26, 60, 18).build());
    }

    private void submit() {
        if (completed) return;
        completed = true;
        String text = textField.getText();
        onComplete.accept(text.isEmpty() ? defaultValue : text);
        if (this.client != null) this.client.setScreen(null);
    }

    // Cancel path — reached via Escape (Screen's default shouldCloseOnEsc()
    // routes here) or any other way this screen gets closed. No-ops if
    // submit() already fired, so completion only ever happens once.
    @Override
    public void close() {
        if (!completed) {
            completed = true;
            onComplete.accept(defaultValue);
        }
        if (this.client != null) this.client.setScreen(null);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (input.key() == 257 || input.key() == 335) { // Enter / numpad Enter
            submit();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Dim the background rather than blacking it out, so the world
        // stays visible behind the prompt.
        ctx.fill(0, 0, this.width, this.height, 0x55000000);

        int px = (this.width - BOX_W) / 2;
        int py = (this.height - BOX_H) / 2;

        ctx.fill(px, py, px + BOX_W, py + BOX_H, COL_BG);
        ctx.fill(px, py, px + BOX_W, py + 1, COL_BORDER);
        ctx.fill(px, py + BOX_H - 1, px + BOX_W, py + BOX_H, COL_BORDER);
        ctx.fill(px, py, px + 1, py + BOX_H, COL_BORDER);
        ctx.fill(px + BOX_W - 1, py, px + BOX_W, py + BOX_H, COL_BORDER);

        ctx.drawTextWithShadow(textRenderer, Text.literal(prompt), px + 10, py + 10, COL_ACCENT);

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
package com.taskblocks.client.gui;

import java.awt.Desktop;
import java.io.File;
import java.util.List;

import com.taskblocks.client.ScriptOverlay;
import com.taskblocks.client.TaskBlocksClient;
import com.taskblocks.script.ScriptData;
import com.taskblocks.script.ScriptLoader;
import com.taskblocks.script.ScriptRunner;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ScriptMenuScreen extends Screen {

    private List<ScriptData> scripts;

    private static final int PANEL_W  = 500;
    private static final int PANEL_H  = 320;
    private static final int HEADER_H = 36;
    private static final int FOOTER_H = 36;
    private static final int ROW_H    = 56;
    private static final int PAD      = 14;

    private static final int COL_BG        = 0xFF111827;
    private static final int COL_HEADER    = 0xFF0F172A;
    private static final int COL_FOOTER    = 0xFF0F172A;
    private static final int COL_ROW_ODD   = 0xFF1E293B;
    private static final int COL_ROW_EVEN  = 0xFF162032;
    private static final int COL_ROW_SEP   = 0xFF334155;
    private static final int COL_BORDER    = 0xFF3B82F6;
    private static final int COL_ACCENT    = 0xFF60A5FA;
    private static final int COL_WHITE     = 0xFFFFFFFF;
    private static final int COL_DARK_GRAY = 0xFF64748B;
    private static final int COL_GREEN     = 0xFF4ADE80;
    private static final int COL_RED       = 0xFFFF6B6B;
    private static final int COL_YELLOW    = 0xFFFFD700;
    private static final int COL_OVERLAY   = 0xCC000000;

    private int px, py, listY, listH;
    private int scrollOffset = 0;

    private String statusMessage = null;
    private int statusColor = COL_GREEN;

    public ScriptMenuScreen() {
        super(Text.literal("TaskBlocks"));
    }

    @Override
    protected void init() {
        scripts = ScriptLoader.loadScripts();
        px = (this.width - PANEL_W) / 2;
        py = (this.height - PANEL_H) / 2;
        listY = py + HEADER_H;
        listH = PANEL_H - HEADER_H - FOOTER_H;
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearChildren();

        int visibleRows = listH / ROW_H;

        for (int i = 0; i < visibleRows && (i + scrollOffset) < scripts.size(); i++) {
            int idx = i + scrollOffset;
            ScriptData script = scripts.get(idx);
            int rowY = listY + i * ROW_H;

            // Open File button
            addDrawableChild(ButtonWidget.builder(
                Text.literal("Open File"),
                btn -> openScriptFile(script)
            ).dimensions(px + PANEL_W - PAD - 80, rowY + 6, 80, 18).build());

            // Run / Stop button
            boolean isThisRunning = ScriptRunner.isRunning()
                && script.name.equals(ScriptRunner.getRunningScriptName());

            addDrawableChild(ButtonWidget.builder(
                Text.literal(isThisRunning ? "■ Stop" : "▶ Run"),
                btn -> {
                    if (ScriptRunner.isRunning()
                            && script.name.equals(ScriptRunner.getRunningScriptName())) {
                        ScriptRunner.stop();
                        showStatus("Stopped: " + script.name, COL_RED);
                        rebuildButtons();
                    } else {
                        showStatus("Running: " + script.name, COL_GREEN);
                        this.client.setScreen(null);
                        ScriptRunner.run(script, 1000);
                    }
                }
            ).dimensions(px + PANEL_W - PAD - 80, rowY + 28, 80, 18).build());
        }

        // Reload button
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Reload"),
            btn -> {
                scripts = ScriptLoader.loadScripts();
                TaskBlocksClient.reloadScripts();
                scrollOffset = 0;
                showStatus("Reloaded " + scripts.size() + " script"
                    + (scripts.size() != 1 ? "s" : "") + "!", COL_GREEN);
                rebuildButtons();
            }
        ).dimensions(px + PAD, py + PANEL_H - FOOTER_H + 9, 70, 18).build());

        // Close button
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Close"),
            btn -> this.close()
        ).dimensions(px + PANEL_W / 2 - 35, py + PANEL_H - FOOTER_H + 9, 70, 18).build());

        // Pin/unpin overlay button
        addDrawableChild(ButtonWidget.builder(
            Text.literal(ScriptOverlay.isVisible() ? "⊙ Overlay: ON" : "⊙ Overlay: OFF"),
            btn -> {
                ScriptOverlay.toggle();
                btn.setMessage(Text.literal(
                    ScriptOverlay.isVisible() ? "⊙ Overlay: ON" : "⊙ Overlay: OFF"));
            }
        ).dimensions(px + PANEL_W - 100 - PAD, py + PANEL_H - FOOTER_H + 9, 100, 18).build());
    }

    private void openScriptFile(ScriptData script) {
        try {
            File file = FabricLoader.getInstance()
                .getConfigDir()
                .resolve("TaskBlocks")
                .resolve(script.fileName)
                .toFile();
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file);
            } else {
                Runtime.getRuntime().exec(new String[]{"notepad.exe", file.getAbsolutePath()});
            }
            showStatus("Opened: " + script.fileName, COL_ACCENT);
        } catch (Exception e) {
            showStatus("Could not open file!", COL_RED);
        }
    }

    private void showStatus(String message, int color) {
        statusMessage = message;
        statusColor = color;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        int visibleRows = listH / ROW_H;
        int maxScroll = Math.max(0, scripts.size() - visibleRows);
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - vAmt));
        rebuildButtons();
        return true;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float deltaTicks) {
        ctx.fill(0, 0, this.width, this.height, COL_OVERLAY);

        // Panel
        ctx.fill(px, py, px + PANEL_W, py + PANEL_H, COL_BG);
        ctx.fill(px, py, px + PANEL_W, py + HEADER_H, COL_HEADER);
        ctx.fill(px, py + PANEL_H - FOOTER_H, px + PANEL_W, py + PANEL_H, COL_FOOTER);

        // Border
        ctx.fill(px,             py,               px + PANEL_W, py + 1,           COL_BORDER);
        ctx.fill(px,             py + PANEL_H - 1, px + PANEL_W, py + PANEL_H,     COL_BORDER);
        ctx.fill(px,             py,               px + 1,       py + PANEL_H,     COL_BORDER);
        ctx.fill(px + PANEL_W-1, py,               px + PANEL_W, py + PANEL_H,     COL_BORDER);
        ctx.fill(px, py + HEADER_H - 1, px + PANEL_W, py + HEADER_H, COL_BORDER);
        ctx.fill(px, py + PANEL_H - FOOTER_H,
                 px + PANEL_W, py + PANEL_H - FOOTER_H + 1, COL_ROW_SEP);

        // Title
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("TaskBlocks"),
            px + PAD, py + (HEADER_H - 8) / 2, COL_ACCENT);

        // Status message in header (right side, replaces count when active)
        if (statusMessage != null) {
            ctx.drawTextWithShadow(textRenderer,
                Text.literal(statusMessage),
                px + PANEL_W - PAD - textRenderer.getWidth(statusMessage),
                py + (HEADER_H - 8) / 2, statusColor);
        } else {
            // Running indicator
            if (ScriptRunner.isRunning()) {
                String running = "▶ " + ScriptRunner.getRunningScriptName();
                ctx.drawTextWithShadow(textRenderer,
                    Text.literal(running),
                    px + PANEL_W - PAD - textRenderer.getWidth(running),
                    py + (HEADER_H - 8) / 2, COL_GREEN);
            } else {
                String countStr = scripts.size() + " script"
                    + (scripts.size() != 1 ? "s" : "");
                ctx.drawTextWithShadow(textRenderer, Text.literal(countStr),
                    px + PANEL_W - PAD - textRenderer.getWidth(countStr),
                    py + (HEADER_H - 8) / 2, COL_DARK_GRAY);
            }
        }

        // Script rows
        if (scripts.isEmpty()) {
            String msg = "No scripts found in config/TaskBlocks/";
            ctx.drawTextWithShadow(textRenderer, Text.literal(msg),
                px + (PANEL_W - textRenderer.getWidth(msg)) / 2,
                listY + listH / 2 - 4, COL_DARK_GRAY);
        } else {
            int visibleRows = listH / ROW_H;

            for (int i = 0; i < visibleRows && (i + scrollOffset) < scripts.size(); i++) {
                ScriptData script = scripts.get(i + scrollOffset);
                int rowY = listY + i * ROW_H;
                boolean isRunning = ScriptRunner.isRunning()
                    && script.name.equals(ScriptRunner.getRunningScriptName());

                ctx.fill(px + 1, rowY, px + PANEL_W - 1, rowY + ROW_H,
                    i % 2 == 0 ? COL_ROW_ODD : COL_ROW_EVEN);
                ctx.fill(px + 1, rowY + ROW_H - 1,
                         px + PANEL_W - 1, rowY + ROW_H, COL_ROW_SEP);

                // Status dot — yellow if running, green if enabled, red if disabled
                int dotColor = isRunning ? COL_YELLOW
                    : script.enabled ? COL_GREEN : COL_RED;
                ctx.fill(px + PAD, rowY + 13, px + PAD + 7, rowY + 20, dotColor);

                // Script name
                ctx.drawTextWithShadow(textRenderer, Text.literal(script.name),
                    px + PAD + 14, rowY + 9, COL_WHITE);

                // Author + version
                ctx.drawTextWithShadow(textRenderer,
                    Text.literal("by " + script.author + "  •  v" + script.version),
                    px + PAD + 14, rowY + 21, COL_DARK_GRAY);

                // Keybind
                ctx.drawTextWithShadow(textRenderer,
                    Text.literal("▶ " + script.startStopKey),
                    px + PAD + 14, rowY + 33, COL_ACCENT);
            }

            // Scrollbar
            int visibleRows2 = listH / ROW_H;
            if (scripts.size() > visibleRows2) {
                int sbX = px + PANEL_W - 4;
                int thumbH = Math.max(20, listH * visibleRows2 / scripts.size());
                int maxScroll = scripts.size() - visibleRows2;
                int thumbY = listY + (listH - thumbH) * scrollOffset / maxScroll;
                ctx.fill(sbX, listY, sbX + 3, listY + listH, 0xFF1E293B);
                ctx.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, COL_BORDER);
            }
        }

        super.render(ctx, mouseX, mouseY, deltaTicks);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
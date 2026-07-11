package com.taskblocks.script.actions;

import java.util.List;

import com.taskblocks.TaskBlocks;
import com.taskblocks.client.ScriptDisplay;
import com.taskblocks.client.TaskBlocksNotifier;

// ============================================================
// print(text)
// print(text, durationMs)
// print(text, durationMs, color)
// print(text, durationMs, color, fadeMs)
//
// Shows text big and centered on screen (same rendering style as the
// macro recorder's countdown), blocking the script for the given
// duration (default 1000ms) before clearing it.
//
// color accepts a short list of named colors (white, red, green,
// blue, yellow, orange, pink, cyan, gray, black, gold) or a raw hex
// value (#RRGGBB or #AARRGGBB). fadeMs, if given, fades the text out
// over the last fadeMs of its display time instead of disappearing
// abruptly.
//
// Note: unlike say(), text here is the FIRST argument split on commas
// (not the "last comma" trick), so this simple parser doesn't support
// a literal comma inside the printed text once you're using the
// duration/color/fade arguments — keep messages short and comma-free.
// ============================================================
public class DisplayActions {

    private static final long DEFAULT_DURATION_MS = 1000;

    public static void register() {
        ActionRegistry.register(DisplayActions::handle);
    }

    private static ActionResult handle(String action, ActionContext ctx) throws InterruptedException {
        if (!action.startsWith("print(") || !action.endsWith(")")) return null;

        String inner = action.substring(6, action.length() - 1);
        List<String> parts = ArgSplitter.split(inner);

        if (parts.isEmpty() || parts.get(0).trim().isEmpty()) {
            TaskBlocks.LOGGER.error("[TaskBlocks] print() needs text to display");
            TaskBlocksNotifier.error("print() needs text to display");
            return ActionResult.normal();
        }

        String text = stripQuotes(parts.get(0).trim());
        long durationMs = DEFAULT_DURATION_MS;
        int color = 0xFFFFFFFF;
        long fadeMs = 0;

        if (parts.size() >= 2 && !parts.get(1).trim().isEmpty()) {
            try {
                durationMs = Long.parseLong(parts.get(1).trim());
            } catch (NumberFormatException e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Invalid print() duration: " + parts.get(1));
                TaskBlocksNotifier.error("Invalid print() duration: §f" + parts.get(1));
            }
        }

        if (parts.size() >= 3 && !parts.get(2).trim().isEmpty()) {
            color = parseColor(parts.get(2).trim());
        }

        if (parts.size() >= 4 && !parts.get(3).trim().isEmpty()) {
            try {
                fadeMs = Long.parseLong(parts.get(3).trim());
            } catch (NumberFormatException e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Invalid print() fade duration: " + parts.get(3));
                TaskBlocksNotifier.error("Invalid print() fade duration: §f" + parts.get(3));
            }
        }

        ScriptDisplay.show(text, color, durationMs, fadeMs);
        try {
            Thread.sleep(durationMs);
        } finally {
            // Guarantees the display clears even if the script gets
            // force-stopped mid-print(), so nothing stays stuck on screen.
            ScriptDisplay.clear();
        }

        return ActionResult.normal();
    }

    private static int parseColor(String value) {
        switch (value.toLowerCase()) {
            case "white":  return 0xFFFFFFFF;
            case "red":    return 0xFFFF5555;
            case "green":  return 0xFF55FF55;
            case "blue":   return 0xFF5555FF;
            case "yellow": return 0xFFFFFF55;
            case "orange": return 0xFFFFA500;
            case "pink":   return 0xFFFF69B4;
            case "cyan":   return 0xFF55FFFF;
            case "gray":
            case "grey":   return 0xFFAAAAAA;
            case "black":  return 0xFF000000;
            case "gold":   return 0xFFFFD700;
        }

        try {
            String hex = value.startsWith("#") ? value.substring(1)
                : value.toLowerCase().startsWith("0x") ? value.substring(2)
                : value;
            if (hex.length() == 6) {
                return (int) (0xFF000000L | Long.parseLong(hex, 16));
            } else if (hex.length() == 8) {
                return (int) Long.parseLong(hex, 16);
            }
        } catch (NumberFormatException ignored) {}

        TaskBlocks.LOGGER.warn("[TaskBlocks] print(): unknown color '" + value + "', using white");
        TaskBlocksNotifier.warn("print(): unknown color '" + value + "', using white");
        return 0xFFFFFFFF;
    }

    private static String stripQuotes(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2)
                || (value.startsWith("'") && value.endsWith("'") && value.length() >= 2)) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
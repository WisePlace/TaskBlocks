package com.taskblocks.client;

// ============================================================
// Holds the current big on-screen message set by the print() action,
// rendered by ScriptOverlay with an optional fade-out over the last
// portion of its display duration. Only one message shows at a time —
// a new print() call (or a script ending) replaces/clears it.
// ============================================================
public class ScriptDisplay {

    private static volatile String text = null;
    private static volatile int color = 0xFFFFFFFF;
    private static volatile long showTime = 0;
    private static volatile long durationMs = 0;
    private static volatile long fadeMs = 0;

    public static void show(String message, int argbColor, long totalDurationMs, long fadeOutMs) {
        text = message;
        color = argbColor;
        durationMs = totalDurationMs;
        fadeMs = Math.max(0, Math.min(fadeOutMs, totalDurationMs));
        showTime = System.currentTimeMillis();
    }

    public static void clear() {
        text = null;
    }

    public static String getText() {
        return text;
    }

    public static int getColor() {
        return color;
    }

    // 0-255 alpha for the current moment — 255 until the fade window
    // starts, then eases down to 0 by the time the display would clear.
    public static int getAlpha() {
        if (text == null || fadeMs <= 0) return 255;

        long elapsed = System.currentTimeMillis() - showTime;
        long fadeStart = durationMs - fadeMs;
        if (elapsed < fadeStart) return 255;

        long intoFade = elapsed - fadeStart;
        float t = 1f - Math.min(1f, (float) intoFade / fadeMs);
        return Math.max(0, Math.min(255, (int) (t * 255)));
    }
}
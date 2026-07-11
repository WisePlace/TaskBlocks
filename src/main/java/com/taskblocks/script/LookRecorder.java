package com.taskblocks.script;

import java.util.ArrayList;
import java.util.List;

import com.taskblocks.TaskBlocks;
import com.taskblocks.client.KeyComboUtil;
import com.taskblocks.client.TaskBlocksNotifier;

import net.minecraft.client.MinecraftClient;

// ============================================================
// Lightweight companion to MacroRecorder: records ONLY smooth camera
// movement (no keys, no clicks, no target script) and copies the
// resulting look() lines straight to the clipboard on stop, ready to
// paste into whatever script or function you're working on.
//
// Uses Minecraft's own clipboard access (client.keyboard) rather than
// java.awt.Toolkit — the game runs in an LWJGL/GLFW window, not a true
// AWT window, so AWT's clipboard can end up disconnected from what the
// OS actually treats as the clipboard in that context.
//
// Three detail modes:
//   DETAILED — emits a line on every meaningful angle change (same
//              behavior as before), most lines, most fidelity.
//   MEDIUM/LOW — path simplification: ticks are grouped into a single
//              line as long as movement keeps going roughly the same
//              direction; a line only closes when the direction turns
//              beyond the mode's angle threshold, OR the segment has
//              accumulated more than MAX_SEGMENT_YAW_ROTATION of net
//              yaw turn — without that cap, a continuous spin in one
//              direction (e.g. a full 360) never "changes direction"
//              and would stay open as a single segment ending back
//              near its own starting angle, which look() would then
//              read as barely any movement at all (it always
//              interpolates the SHORTEST path to an absolute target
//              angle, and can't represent more than 180 degrees of
//              turn in a single call regardless). Forcing a break
//              periodically during a long continuous turn keeps every
//              individual segment safely representable.
//              Real elapsed time is tracked continuously regardless
//              of how many lines that becomes, so total playback
//              duration and where you end up stay accurate — only
//              the in-between path is simplified.
// ============================================================
public class LookRecorder {

    public static final String STOP_KEY = "F10";

    public enum DetailMode { DETAILED, MEDIUM, LOW }

    private static final float LOOK_EPSILON_DEGREES = 1.5f;   // DETAILED mode
    private static final float NOISE_FLOOR_DEGREES = 0.3f;    // MEDIUM/LOW: ignore jitter below this
    private static final float MEDIUM_ANGLE_THRESHOLD = 30f;
    private static final float LOW_ANGLE_THRESHOLD = 70f;
    private static final float MAX_SEGMENT_YAW_ROTATION = 120f; // safely under look()'s 180 ambiguity point

    private enum State { IDLE, COUNTDOWN, RECORDING }

    private static State state = State.IDLE;
    private static long stateChangedTick = 0;
    private static long tickCounter = 0;

    private static DetailMode mode = DetailMode.DETAILED;

    private static final List<String> recordedLines = new ArrayList<>();

    // DETAILED mode state
    private static long lastEventTick = 0;
    private static float lastYaw = 0f;
    private static float lastPitch = 0f;

    // MEDIUM/LOW mode state
    private static long segmentStartTick = 0;
    private static boolean hasRefDirection = false;
    private static float refDirYaw = 0f;
    private static float refDirPitch = 0f;
    private static float prevYaw = 0f;
    private static float prevPitch = 0f;
    private static float segmentYawAccum = 0f;

    public static boolean isActive() {
        return state != State.IDLE;
    }

    public static boolean isCountingDown() {
        return state == State.COUNTDOWN;
    }

    public static boolean isRecording() {
        return state == State.RECORDING;
    }

    public static void setMode(DetailMode newMode) {
        mode = newMode;
    }

    public static DetailMode getMode() {
        return mode;
    }

    public static int getCountdownSecondsRemaining() {
        if (state != State.COUNTDOWN) return 0;
        long elapsedTicks = tickCounter - stateChangedTick;
        int remaining = 3 - (int) (elapsedTicks / 20);
        return Math.max(0, Math.min(3, remaining));
    }

    public static void start() {
        if (state != State.IDLE) return;
        if (MacroRecorder.isActive()) {
            TaskBlocksNotifier.warn("Can't record look movement while the macro recorder is active.");
            return;
        }

        recordedLines.clear();
        tickCounter = 0;
        stateChangedTick = 0;
        state = State.COUNTDOWN;

        TaskBlocks.LOGGER.info("[TaskBlocks] Look recorder: countdown started (mode: " + mode + ")");
    }

    public static void cancel() {
        state = State.IDLE;
        recordedLines.clear();
    }

    public static void tick(MinecraftClient client) {
        if (state == State.IDLE || client.player == null) return;

        tickCounter++;

        if (state == State.COUNTDOWN) {
            if (tickCounter - stateChangedTick >= 3 * 20) {
                beginRecording(client);
            }
            return;
        }

        // RECORDING
        if (KeyComboUtil.isComboDown(client, STOP_KEY)) {
            stopAndCopy(client);
            return;
        }

        if (mode == DetailMode.DETAILED) {
            checkLookDetailed(client);
        } else {
            checkLookGrouped(client);
        }
    }

    private static void beginRecording(MinecraftClient client) {
        state = State.RECORDING;
        stateChangedTick = tickCounter;

        float yaw = normalizeYaw(client.player.getYaw());
        float pitch = client.player.getPitch();

        lastEventTick = tickCounter;
        lastYaw = yaw;
        lastPitch = pitch;

        segmentStartTick = tickCounter;
        hasRefDirection = false;
        prevYaw = yaw;
        prevPitch = pitch;
        segmentYawAccum = 0f;

        TaskBlocksNotifier.notice("Recording look movement! Press " + STOP_KEY + " to stop.");
        TaskBlocks.LOGGER.info("[TaskBlocks] Look recorder: recording started");
    }

    // ============================================================
    // DETAILED — unchanged from the original behavior: emit a line
    // whenever the angle has drifted past the epsilon since the last
    // recorded line.
    // ============================================================
    private static void checkLookDetailed(MinecraftClient client) {
        float yaw = normalizeYaw(client.player.getYaw());
        float pitch = client.player.getPitch();

        float yawDelta = normalizeYaw(yaw - lastYaw);
        float pitchDelta = pitch - lastPitch;

        if (Math.abs(yawDelta) >= LOOK_EPSILON_DEGREES || Math.abs(pitchDelta) >= LOOK_EPSILON_DEGREES) {
            emitLine(yaw, pitch, lastEventTick, tickCounter);
            lastEventTick = tickCounter;
            lastYaw = yaw;
            lastPitch = pitch;
        }
    }

    // ============================================================
    // MEDIUM/LOW — path simplification by direction. Keeps extending
    // the current segment as long as each tick's movement direction
    // stays within the mode's angle threshold of the segment's
    // established direction; closes and emits when it turns beyond
    // that, or on stop.
    // ============================================================
    private static void checkLookGrouped(MinecraftClient client) {
        float threshold = mode == DetailMode.LOW ? LOW_ANGLE_THRESHOLD : MEDIUM_ANGLE_THRESHOLD;

        float yaw = normalizeYaw(client.player.getYaw());
        float pitch = client.player.getPitch();

        float stepYawDelta = normalizeYaw(yaw - prevYaw);
        float stepPitchDelta = pitch - prevPitch;
        float stepMag = (float) Math.sqrt(stepYawDelta * stepYawDelta + stepPitchDelta * stepPitchDelta);

        if (stepMag < NOISE_FLOOR_DEGREES) {
            prevYaw = yaw;
            prevPitch = pitch;
            return;
        }

        float stepDirYaw = stepYawDelta / stepMag;
        float stepDirPitch = stepPitchDelta / stepMag;

        if (!hasRefDirection) {
            refDirYaw = stepDirYaw;
            refDirPitch = stepDirPitch;
            hasRefDirection = true;
            segmentStartTick = tickCounter - 1;
            segmentYawAccum = stepYawDelta;
            prevYaw = yaw;
            prevPitch = pitch;
            return;
        }

        float dot = refDirYaw * stepDirYaw + refDirPitch * stepDirPitch;
        dot = Math.max(-1f, Math.min(1f, dot));
        float angleDiff = (float) Math.toDegrees(Math.acos(dot));

        boolean directionChanged = angleDiff > threshold;
        boolean rotationCapExceeded = Math.abs(segmentYawAccum + stepYawDelta) >= MAX_SEGMENT_YAW_ROTATION;

        if (directionChanged || rotationCapExceeded) {
            // Direction turned beyond the threshold, or this segment has
            // spun as far as a single look() call can safely represent —
            // close it at the previous tick's orientation, then start a
            // fresh segment that includes this tick's movement.
            emitLine(prevYaw, prevPitch, segmentStartTick, tickCounter - 1);
            segmentStartTick = tickCounter - 1;
            refDirYaw = stepDirYaw;
            refDirPitch = stepDirPitch;
            segmentYawAccum = stepYawDelta;
        } else {
            segmentYawAccum += stepYawDelta;
        }

        prevYaw = yaw;
        prevPitch = pitch;
    }

    private static void flushOpenSegment() {
        if (mode != DetailMode.DETAILED && hasRefDirection) {
            emitLine(prevYaw, prevPitch, segmentStartTick, tickCounter);
        }
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw % 360f;
        if (normalized > 180f) normalized -= 360f;
        if (normalized < -180f) normalized += 360f;
        return normalized;
    }

    // Roughly one interpolation step per 50ms of real time the line
    // covers, so a merged line spanning several seconds stays smooth
    // instead of being squeezed into a handful of steps. look() reads
    // the camera's actual current orientation as the interpolation
    // start at playback time, so only the target (yaw, pitch) is needed
    // here — consecutive lines chain together naturally.
    private static void emitLine(float yaw, float pitch, long startTick, long endTick) {
        long elapsedTicks = endTick - startTick;
        long elapsedMs = elapsedTicks * 50L;
        if (elapsedMs <= 0) return;

        int steps = (int) Math.max(2, Math.min(40, elapsedMs / 50));
        long delayPerStep = Math.max(1, elapsedMs / steps);

        double roundedYaw = Math.round(yaw * 10) / 10.0;
        double roundedPitch = Math.round(pitch * 10) / 10.0;

        recordedLines.add("look(" + roundedYaw + ", " + roundedPitch
            + ", " + steps + ", " + delayPerStep + ")");
    }

    private static void stopAndCopy(MinecraftClient client) {
        flushOpenSegment();

        List<String> lines = new ArrayList<>(recordedLines);
        state = State.IDLE;
        recordedLines.clear();

        if (lines.isEmpty()) {
            TaskBlocksNotifier.warn("No movement recorded - nothing copied.");
            return;
        }

        String joined = String.join("\n", lines);
        try {
            client.keyboard.setClipboard(joined);
            TaskBlocksNotifier.notice(lines.size() + " look() line"
                + (lines.size() != 1 ? "s" : "") + " copied to clipboard!");
            TaskBlocks.LOGGER.info("[TaskBlocks] Look recorder: copied " + lines.size() + " lines to clipboard");
        } catch (Exception e) {
            TaskBlocksNotifier.error("Failed to copy to clipboard - check the log");
            TaskBlocks.LOGGER.error("[TaskBlocks] Look recorder: clipboard copy failed", e);
        }
    }
}
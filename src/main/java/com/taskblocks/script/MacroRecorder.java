package com.taskblocks.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.taskblocks.TaskBlocks;
import com.taskblocks.client.KeyComboUtil;
import com.taskblocks.client.TaskBlocksNotifier;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

// ============================================================
// Basic macro recorder (BETA). Records movement key presses/releases,
// mouse clicks, camera orientation, and a short set of other keybinds,
// converting them directly into valid .tbs action lines using the SAME
// action names the script language already understands (forward_press,
// left_click_press, look(yaw, pitch), etc.) — no new action types
// needed for playback.
//
// Flow: start() -> 3-2-1 countdown (rendered via ScriptOverlay) ->
// recording begins (captures starting orientation as the first line)
// -> stopped by pressing the fixed STOP_KEY -> recorded lines replace
// the target script's [actions] body.
//
// Scope for now: movement, camera look direction, and mouse clicks
// only, as requested — no world interaction, no item use.
// ============================================================
public class MacroRecorder {

    public static final String STOP_KEY = "F10";

    // Minimum yaw/pitch change (degrees) before a new look() line is
    // recorded — keeps tiny mouse jitter from flooding the script with
    // near-identical lines every tick.
    private static final float LOOK_EPSILON_DEGREES = 1.5f;

    private enum State { IDLE, COUNTDOWN, RECORDING }

    private static State state = State.IDLE;
    private static long stateChangedTick = 0;
    private static long tickCounter = 0;

    private static String targetFileName = null;
    private static final List<String> recordedLines = new ArrayList<>();
    private static long lastEventTick = 0;

    // Tracks each watched KeyBinding's pressed state as of the last tick,
    // so an action only gets emitted on an actual press/release transition.
    private static final Map<String, Boolean> lastKeyState = new LinkedHashMap<>();

    private static float lastRecordedYaw = 0f;
    private static float lastRecordedPitch = 0f;

    public static boolean isActive() {
        return state != State.IDLE;
    }

    public static boolean isCountingDown() {
        return state == State.COUNTDOWN;
    }

    public static boolean isRecording() {
        return state == State.RECORDING;
    }

    // Seconds remaining in the countdown (3, 2, 1), or 0 once recording starts.
    public static int getCountdownSecondsRemaining() {
        if (state != State.COUNTDOWN) return 0;
        long elapsedTicks = tickCounter - stateChangedTick;
        int remaining = 3 - (int) (elapsedTicks / 20);
        return Math.max(0, Math.min(3, remaining));
    }

    public static void start(String fileName) {
        if (state != State.IDLE) return;

        targetFileName = fileName;
        recordedLines.clear();
        lastKeyState.clear();
        tickCounter = 0;
        stateChangedTick = 0;
        state = State.COUNTDOWN;

        TaskBlocks.LOGGER.info("[TaskBlocks] Macro recorder: countdown started for " + fileName);
    }

    public static void cancel() {
        state = State.IDLE;
        targetFileName = null;
        recordedLines.clear();
        lastKeyState.clear();
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
            stopAndSave();
            return;
        }

        checkKey(client, "forward", client.options.forwardKey);
        checkKey(client, "backward", client.options.backKey);
        checkKey(client, "left", client.options.leftKey);
        checkKey(client, "right", client.options.rightKey);
        checkKey(client, "jump", client.options.jumpKey);
        checkKey(client, "sneak", client.options.sneakKey);
        checkKey(client, "sprint", client.options.sprintKey);
        checkKey(client, "left_click", client.options.attackKey);
        checkKey(client, "right_click", client.options.useKey);
        checkLook(client);
    }

    private static void beginRecording(MinecraftClient client) {
        state = State.RECORDING;
        stateChangedTick = tickCounter;
        lastEventTick = tickCounter;

        // Capture starting orientation as the very first recorded line,
        // so playback snaps to the same direction the recording began in.
        lastRecordedYaw = normalizeYaw(client.player.getYaw());
        lastRecordedPitch = client.player.getPitch();
        emitEvent(formatLook(lastRecordedYaw, lastRecordedPitch));

        TaskBlocksNotifier.notice("Recording started! Press " + STOP_KEY + " to stop.");
        TaskBlocks.LOGGER.info("[TaskBlocks] Macro recorder: recording started");
    }

    private static void checkKey(MinecraftClient client, String name, KeyBinding binding) {
        boolean pressed = binding.isPressed();
        boolean wasPressed = lastKeyState.getOrDefault(name, false);

        if (pressed != wasPressed) {
            emitEvent(name + (pressed ? "_press" : "_release"));
            lastKeyState.put(name, pressed);
        }
    }

    private static void checkLook(MinecraftClient client) {
        float yaw = normalizeYaw(client.player.getYaw());
        float pitch = client.player.getPitch();

        float yawDelta = normalizeYaw(yaw - lastRecordedYaw);
        float pitchDelta = pitch - lastRecordedPitch;

        if (Math.abs(yawDelta) >= LOOK_EPSILON_DEGREES || Math.abs(pitchDelta) >= LOOK_EPSILON_DEGREES) {
            emitSmoothLook(yaw, pitch);
            lastRecordedYaw = yaw;
            lastRecordedPitch = pitch;
        }
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw % 360f;
        if (normalized > 180f) normalized -= 360f;
        if (normalized < -180f) normalized += 360f;
        return normalized;
    }

    private static String formatLook(float yaw, float pitch) {
        double roundedYaw = Math.round(yaw * 10) / 10.0;
        double roundedPitch = Math.round(pitch * 10) / 10.0;
        return "look(" + roundedYaw + ", " + roundedPitch + ")";
    }

    // Always uses the smooth multi-step form, even for very short gaps —
    // look()'s single-step "instant" form has a fixed ~50ms settle delay
    // baked into it (see MovementActions), which silently inflates total
    // playback time once you're chaining dozens of recorded samples —
    // exactly what made played-back movement overshoot the original.
    private static void emitSmoothLook(float yaw, float pitch) {
        long elapsedTicks = tickCounter - lastEventTick;
        long elapsedMs = elapsedTicks * 50L;

        int steps = (int) Math.max(2, Math.min(6, elapsedMs / 10));
        long delayPerStep = Math.max(1, elapsedMs / steps);

        double roundedYaw = Math.round(yaw * 10) / 10.0;
        double roundedPitch = Math.round(pitch * 10) / 10.0;

        recordedLines.add("look(" + roundedYaw + ", " + roundedPitch
            + ", " + steps + ", " + delayPerStep + ")");

        lastEventTick = tickCounter;
    }

    private static void emitEvent(String actionName) {
        long elapsedTicks = tickCounter - lastEventTick;
        long elapsedMs = elapsedTicks * 50L;
        if (elapsedMs > 0) {
            recordedLines.add("pause(" + elapsedMs + ", ms)");
        }
        recordedLines.add(actionName);
        lastEventTick = tickCounter;
    }

    private static void stopAndSave() {
        // Release any keys still held at the moment recording stops, so
        // the recorded macro doesn't leave something stuck "pressed".
        for (Map.Entry<String, Boolean> entry : lastKeyState.entrySet()) {
            if (entry.getValue()) {
                emitEvent(entry.getKey() + "_release");
            }
        }

        String fileName = targetFileName;
        List<String> lines = new ArrayList<>(recordedLines);

        state = State.IDLE;
        targetFileName = null;
        recordedLines.clear();
        lastKeyState.clear();

        boolean saved = ScriptLoader.appendRecordedActions(fileName, lines);
        if (saved) {
            TaskBlocksNotifier.notice("Macro recorded! " + lines.size() + " lines added to " + fileName);
            TaskBlocks.LOGGER.info("[TaskBlocks] Macro recorder: saved " + lines.size() + " lines to " + fileName);
        } else {
            TaskBlocksNotifier.error("Failed to save recorded macro - check the log");
            TaskBlocks.LOGGER.error("[TaskBlocks] Macro recorder: failed to save to " + fileName);
        }
    }
}
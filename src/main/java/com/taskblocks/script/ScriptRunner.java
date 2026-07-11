package com.taskblocks.script;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.taskblocks.TaskBlocks;
import com.taskblocks.client.TaskBlocksNotifier;
import com.taskblocks.script.actions.ActionContext;
import com.taskblocks.script.actions.ActionRegistry;
import com.taskblocks.script.actions.ActionResult;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public class ScriptRunner {

    private static Thread runnerThread = null;
    private static volatile boolean running = false;
    private static volatile String runningScriptName = null;
    private static volatile long scriptStartTime = 0;

    // ============================================================
    // Shared state exposed to EventListenerManager so a fired listener
    // can execute an action against the same running script (variables,
    // loop counters, cursor), and hand flow control (goto/end) back.
    // ============================================================
    private static volatile List<String> currentActions = null;
    private static volatile Map<Integer, Integer> currentLoopCounters = null;
    private static volatile Map<String, String> currentVariables = null;
    private static volatile Set<Integer> currentDispatchedBranches = null;
    private static volatile int currentCursor = 0;
    private static volatile long tickCounter = 0;
    private static volatile String pendingChainScript = null;
    private static volatile Map<String, String> pendingChainVariables = null;
    private static final java.util.concurrent.atomic.AtomicReference<ActionResult> pendingListenerControl =
        new java.util.concurrent.atomic.AtomicReference<>();

    public static List<String> getCurrentActions() { return currentActions; }
    public static Map<Integer, Integer> getCurrentLoopCounters() { return currentLoopCounters; }
    public static Map<String, String> getCurrentVariables() { return currentVariables; }
    public static Set<Integer> getCurrentDispatchedBranches() { return currentDispatchedBranches; }
    public static int getCurrentCursor() { return currentCursor; }

    public static void requestListenerControl(ActionResult result) {
        pendingListenerControl.set(result);
    }
    // Tracks keys and mouse buttons that should stay pressed
    private static final Set<String> heldKeys =
        Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> heldMouseButtons =
        Collections.synchronizedSet(new HashSet<>());
    // ============================================================
    // Public state accessors
    // ============================================================

    public static boolean isRunning() { return running; }
    public static String getRunningScriptName() { return runningScriptName; }

    public static long getElapsedSeconds() {
        if (!running || scriptStartTime == 0) return 0;
        return (System.currentTimeMillis() - scriptStartTime) / 1000;
    }

    private static volatile boolean mouseLocked = false;
    private static volatile float lockedYaw = 0f;
    private static volatile float lockedPitch = 0f;

    public static void lockMousePosition() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            lockedYaw   = client.player.getYaw();
            lockedPitch = client.player.getPitch();
        }
        mouseLocked = true;
    }

    public static void unlockMousePosition() {
        mouseLocked = false;
    }

    public static boolean isMousePositionLocked() { return mouseLocked; }
    // ============================================================
    // Hold state management
    // ============================================================

    public static void holdKey(String key)       { heldKeys.add(key); }
    public static void releaseKey(String key)    { heldKeys.remove(key); }
    public static void holdMouse(String button)  { heldMouseButtons.add(button); }
    public static void releaseMouse(String button) { heldMouseButtons.remove(button); }

    private static void releaseAll() {
        heldKeys.clear();
        heldMouseButtons.clear();
    }

    // ============================================================
    // Tick — called every game tick from TaskBlocksClient
    // Re-applies held keys/mouse buttons so they survive screen changes
    // ============================================================

    public static void reapplyHeldKeys() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        // If already on main thread, call directly
        if (client.isOnThread()) {
            tick(client);
        } else {
            client.execute(() -> tick(client));
        }
    }

    public static boolean isHoldingMouse(String button) {
        return heldMouseButtons.contains(button);
    }


    
    public static void tick(MinecraftClient client) {
        if (mouseLocked && client.player != null) {
            client.player.setYaw(lockedYaw);
            client.player.setPitch(lockedPitch);
        }

        if (!running || client.player == null) return;

        tickCounter++;
        EventListenerManager.tick(client, tickCounter);

        // Re-apply held mouse buttons
        if (heldMouseButtons.contains("left")) {
            client.options.attackKey.setPressed(true);
        }
        if (heldMouseButtons.contains("right")) {
            client.options.useKey.setPressed(true);
        }

        // Re-apply named movement keybinds
        if (heldKeys.contains("forward"))  client.options.forwardKey.setPressed(true);
        if (heldKeys.contains("backward")) client.options.backKey.setPressed(true);
        if (heldKeys.contains("left"))     client.options.leftKey.setPressed(true);
        if (heldKeys.contains("right"))    client.options.rightKey.setPressed(true);
        if (heldKeys.contains("jump"))     client.options.jumpKey.setPressed(true);
        if (heldKeys.contains("sneak"))    client.options.sneakKey.setPressed(true);
        if (heldKeys.contains("sprint"))   client.options.sprintKey.setPressed(true);

        // Re-apply raw key bindings
        for (String keyName : heldKeys) {
            if (keyName.equals("forward") || keyName.equals("backward")
                    || keyName.equals("left") || keyName.equals("right")
                    || keyName.equals("jump") || keyName.equals("sneak")
                    || keyName.equals("sprint")) continue;
            try {
                String normalized = keyName.toLowerCase().replace("_", ".");
                String translationKey = normalized.startsWith("key.keyboard.")
                    ? normalized : "key.keyboard." + normalized;
                InputUtil.Key key = InputUtil.fromTranslationKey(translationKey);
                KeyBinding.setKeyPressed(key, true);
            } catch (Exception ignored) {}
        }

        // Force block breaking every tick when left click is held
        // This covers BOTH when screen is open AND when it just closed
        if (heldMouseButtons.contains("left")
                && client.interactionManager != null
                && client.world != null) {
            client.attackCooldown = 0;
            if (client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit
                    && !client.world.getBlockState(hit.getBlockPos()).isAir()) {
                client.interactionManager.updateBlockBreakingProgress(
                    hit.getBlockPos(), hit.getSide());
            }
        }
    }

    
    // ============================================================
    // Stop
    // ============================================================

    public static void stop() {
        running = false;
        scriptStartTime = 0;
        ScriptRunner.unlockMousePosition();
        releaseAll();
        EventListenerManager.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (!mc.mouse.isCursorLocked() && mc.currentScreen == null) {
                mc.mouse.lockCursor();
            }
        });
        if (runnerThread != null) {
            runnerThread.interrupt();
            runnerThread = null;
        }
        runningScriptName = null;

        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            client.options.attackKey.setPressed(false);
            client.options.useKey.setPressed(false);
            client.options.forwardKey.setPressed(false);
            client.options.backKey.setPressed(false);
            client.options.leftKey.setPressed(false);
            client.options.rightKey.setPressed(false);
            client.options.jumpKey.setPressed(false);
            client.options.sneakKey.setPressed(false);
            client.options.sprintKey.setPressed(false);
        });

        TaskBlocks.LOGGER.info("[TaskBlocks] Script stopped.");
        TaskBlocksNotifier.info("Stopped.");
        TaskBlocksNotifier.setDebugMode(false);
    }

    // ============================================================
    // Run
    // ============================================================

    public static void run(ScriptData script, long initialDelayMs) {
        run(script, initialDelayMs, null);
    }

    public static void run(ScriptData script, long initialDelayMs, Map<String, String> initialVariables) {
        if (running) stop();

        running = true;
        scriptStartTime = System.currentTimeMillis();
        runningScriptName = script.name;

        TaskBlocksNotifier.setDebugMode(script.debug);
        TaskBlocksNotifier.notice("Script started: " + script.name);

        runnerThread = new Thread(() -> {
            try {
                Thread.sleep(initialDelayMs);
                TaskBlocks.LOGGER.info("[TaskBlocks] Running: " + script.name);
                executeActions(script.actions, initialVariables);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                running = false;
                runningScriptName = null;
                scriptStartTime = 0;
                ScriptRunner.unlockMousePosition();
                releaseAll();
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.execute(() -> {
                    if (!mc.mouse.isCursorLocked() && mc.currentScreen == null) {
                        mc.mouse.lockCursor();
                    }
                });

                MinecraftClient client = MinecraftClient.getInstance();
                client.execute(() -> {
                    client.options.attackKey.setPressed(false);
                    client.options.useKey.setPressed(false);
                    client.options.forwardKey.setPressed(false);
                    client.options.backKey.setPressed(false);
                    client.options.leftKey.setPressed(false);
                    client.options.rightKey.setPressed(false);
                    client.options.jumpKey.setPressed(false);
                    client.options.sneakKey.setPressed(false);
                    client.options.sprintKey.setPressed(false);
                });

                TaskBlocks.LOGGER.info("[TaskBlocks] Finished: " + script.name);
                TaskBlocksNotifier.info("Finished: " + script.name);
                TaskBlocksNotifier.setDebugMode(false);

                // Launch a chained script (run_script()) only now that this
                // thread's own cleanup (running=false, released keys, etc.)
                // has fully completed — starting it any earlier would race
                // on the shared 'running' flag with the new script's thread.
                String nextScript = pendingChainScript;
                Map<String, String> nextVariables = pendingChainVariables;
                pendingChainScript = null;
                pendingChainVariables = null;
                if (nextScript != null) {
                    ScriptData target = com.taskblocks.script.actions.ScriptActions.findScript(nextScript);
                    if (target != null && target.enabled) {
                        ScriptRunner.run(target, 0, nextVariables);
                    } else {
                        TaskBlocks.LOGGER.error("[TaskBlocks] run_script: target no longer available: " + nextScript);
                        TaskBlocksNotifier.error("run_script: target no longer available: " + nextScript);
                    }
                }
            }
        }, "TaskBlocks-Runner");

        runnerThread.setDaemon(true);
        runnerThread.start();
    }

    // ============================================================
    // Variable interpolation
    // ============================================================

    private static String interpolate(String action, Map<String, String> variables) {
        if (!action.contains("{") || variables.isEmpty()) return action;
        String result = action;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    // ============================================================
    // Execute actions
    // ============================================================

        private static void executeActions(List<String> actions, Map<String, String> initialVariables) throws InterruptedException {
        Map<Integer, Integer> loopCounters = new java.util.HashMap<>();
        Map<String, String> variables = initialVariables != null
            ? new java.util.HashMap<>(initialVariables) : new java.util.HashMap<>();
        java.util.Set<Integer> dispatchedBranches = new java.util.HashSet<>();
        int cursor = 0;

        currentActions = actions;
        currentLoopCounters = loopCounters;
        currentVariables = variables;
        currentDispatchedBranches = dispatchedBranches;
        pendingListenerControl.set(null);

        try {
            while (cursor < actions.size() && running) {
                currentCursor = cursor;

                // A listener fired goto()/end()/run_script() since the last line —
                // apply it before executing the next scripted line.
                ActionResult pending = pendingListenerControl.getAndSet(null);
                if (pending != null) {
                    if (pending.type == ActionResult.Type.END) {
                        TaskBlocks.LOGGER.info("[TaskBlocks] Script ended by a listener action.");
                        break;
                    } else if (pending.type == ActionResult.Type.JUMP) {
                        cursor = pending.targetLine;
                        continue;
                    } else if (pending.type == ActionResult.Type.CHAIN) {
                        TaskBlocks.LOGGER.info("[TaskBlocks] Script chaining (via listener) to: " + pending.targetScript);
                        pendingChainScript = pending.targetScript;
                        pendingChainVariables = pending.chainVariables;
                        break;
                    }
                }

                String raw = actions.get(cursor).trim();

                if (raw.isEmpty() || raw.startsWith("#")) {
                    cursor++;
                    continue;
                }

                String interpolated = interpolate(raw, variables);
                ActionContext ctx = new ActionContext(cursor, loopCounters, variables, actions, dispatchedBranches);
                ActionResult result = ActionRegistry.execute(interpolated, ctx);

                if (result.type == ActionResult.Type.END) {
                    TaskBlocks.LOGGER.info("[TaskBlocks] Script ended by 'end' action.");
                    break;
                } else if (result.type == ActionResult.Type.CHAIN) {
                    TaskBlocks.LOGGER.info("[TaskBlocks] Script chaining to: " + result.targetScript);
                    pendingChainScript = result.targetScript;
                    pendingChainVariables = result.chainVariables;
                    break;
                } else if (result.type == ActionResult.Type.JUMP) {
                    cursor = result.targetLine;
                } else {
                    cursor++;
                }
            }
        } finally {
            currentActions = null;
            currentLoopCounters = null;
            currentVariables = null;
            currentDispatchedBranches = null;
            EventListenerManager.clear();
        }
    }
}
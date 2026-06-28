package com.taskblocks.script;

import com.taskblocks.TaskBlocks;
import com.taskblocks.client.TaskBlocksNotifier;
import com.taskblocks.script.actions.ActionContext;
import com.taskblocks.script.actions.ActionRegistry;
import com.taskblocks.script.actions.ActionResult;
import net.minecraft.client.MinecraftClient;
import java.util.Map;

import java.util.List;

public class ScriptRunner {

    private static Thread runnerThread = null;
    private static volatile boolean running = false;
    private static volatile String runningScriptName = null;

    public static boolean isRunning() { return running; }
    public static String getRunningScriptName() { return runningScriptName; }

    public static void stop() {
        running = false;
        scriptStartTime = 0;
        if (runnerThread != null) {
            runnerThread.interrupt();
            runnerThread = null;
        }
        runningScriptName = null;
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            client.options.attackKey.setPressed(false);
            client.options.useKey.setPressed(false);
        });
        TaskBlocks.LOGGER.info("[TaskBlocks] Script stopped.");
        TaskBlocksNotifier.setDebugMode(false);
    }

    private static String interpolate(String action, Map<String, String> variables) {
        if (!action.contains("{") || variables.isEmpty()) return action;
        String result = action;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
    private static volatile long scriptStartTime = 0;

    public static long getElapsedSeconds() {
        if (!running || scriptStartTime == 0) return 0;
        return (System.currentTimeMillis() - scriptStartTime) / 1000;
    }
    public static void run(ScriptData script, long initialDelayMs) {
        if (running) stop();
        running = true;
        scriptStartTime = System.currentTimeMillis();
        runningScriptName = script.name;
        TaskBlocksNotifier.setDebugMode(script.debug);
        runnerThread = new Thread(() -> {
            try {
                Thread.sleep(initialDelayMs);
                TaskBlocks.LOGGER.info("[TaskBlocks] Running: " + script.name);
                executeActions(script.actions);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                running = false;
                runningScriptName = null;
                MinecraftClient client = MinecraftClient.getInstance();
                client.execute(() -> {
                    client.options.attackKey.setPressed(false);
                    client.options.useKey.setPressed(false);
                });
                TaskBlocks.LOGGER.info("[TaskBlocks] Finished: " + script.name);
            }
        }, "TaskBlocks-Runner");

        runnerThread.setDaemon(true);
        runnerThread.start();
    }

    private static void executeActions(List<String> actions) throws InterruptedException {
        int[] loopCounters = new int[actions.size()];
        Map<String, String> variables = new java.util.HashMap<>();
        int cursor = 0;

        while (cursor < actions.size() && running) {
            String raw = actions.get(cursor).trim();

            if (raw.isEmpty() || raw.startsWith("#")) {
                cursor++;
                continue;
            }
            String interpolated = interpolate(raw, variables);
            ActionContext ctx = new ActionContext(cursor, loopCounters, variables);
            ActionResult result = ActionRegistry.execute(interpolated, ctx);

            if (result.type == ActionResult.Type.END) {
                TaskBlocks.LOGGER.info("[TaskBlocks] Script ended by 'end' action.");
                break;
            } else if (result.type == ActionResult.Type.JUMP) {
                cursor = result.targetLine;
            } else {
                cursor++;
            }
        }
    }
}
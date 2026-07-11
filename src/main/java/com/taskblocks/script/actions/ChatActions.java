package com.taskblocks.script.actions;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.taskblocks.TaskBlocks;
import com.taskblocks.client.TaskBlocksNotifier;
import com.taskblocks.client.gui.ScriptInputScreen;
import com.taskblocks.script.ScriptRunner;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class ChatActions {

    private static final long INPUT_TIMEOUT_MS = 5 * 60 * 1000L; // 5 minutes

    public static void register() {
        ActionRegistry.register(ChatActions::handle);
    }

    private static ActionResult handle(String action, ActionContext ctx)
            throws InterruptedException {

        // ============================================================
        // say(message) / say(message, context)
        // ============================================================
        if (action.startsWith("say(") && action.endsWith(")")) {
            String inner = action.substring(4, action.length() - 1).trim();

            // Split on comma but only for 2 parts max
            // say(message) or say(message, context)
            String message;
            String context = null;

            int commaIdx = inner.lastIndexOf(",");
            if (commaIdx != -1) {
                message = inner.substring(0, commaIdx).trim();
                context = inner.substring(commaIdx + 1).trim().toLowerCase();
            } else {
                message = inner;
            }

            message = stripQuotes(message);

            final String finalMessage = message;
            final String finalContext = context;
            MinecraftClient client = MinecraftClient.getInstance();

            client.execute(() -> {
                if (client.player == null) return;

                if (finalContext == null) {
                    // say(message) — normal server chat or command
                    if (finalMessage.startsWith("/")) {
                        client.player.networkHandler.sendChatCommand(
                            finalMessage.substring(1));
                    } else {
                        client.player.networkHandler.sendChatMessage(finalMessage);
                    }

                } else if (finalContext.equals("local")) {
                    // say(message, local) — display only locally
                    client.player.sendMessage(
                        Text.literal("§7[TaskBlocks] §f" + finalMessage), false);

                } else {
                    // say(message, PlayerName) — send private message to player
                    client.player.networkHandler.sendChatCommand(
                        "msg " + finalContext + " " + finalMessage);
                }
            });

            Thread.sleep(50);
            return ActionResult.normal();
        }

        // ============================================================
        // input(prompt) / input(prompt, default)
        // varName=input(prompt) / varName=input(prompt, default)
        //
        // Opens a small in-game text prompt and blocks the calling
        // thread (polling, same pattern as block_break/item_use) until
        // the player submits, cancels, or a safety timeout elapses.
        // Uses the same 'last comma splits off the trailing argument'
        // convention as say(), so the prompt can itself contain commas
        // — only the FINAL comma introduces an optional default value.
        // ============================================================
        if (action.contains("=input(") || action.startsWith("input(")) {
            String varName = null;
            String inner;

            if (action.contains("=input(") && action.endsWith(")")) {
                int eqIdx = action.indexOf("=input(");
                varName = action.substring(0, eqIdx).trim();
                inner = action.substring(eqIdx + 7, action.length() - 1);

                if (varName.isEmpty()) {
                    TaskBlocks.LOGGER.error("[TaskBlocks] Variable name cannot be empty");
                    TaskBlocksNotifier.error("Variable name cannot be empty");
                    return ActionResult.normal();
                }
            } else if (action.startsWith("input(") && action.endsWith(")")) {
                inner = action.substring(6, action.length() - 1);
            } else {
                return null;
            }

            String prompt;
            String defaultValue = "";
            int commaIdx = inner.lastIndexOf(",");
            if (commaIdx != -1) {
                prompt = stripQuotes(inner.substring(0, commaIdx).trim());
                defaultValue = stripQuotes(inner.substring(commaIdx + 1).trim());
            } else {
                prompt = stripQuotes(inner.trim());
            }

            if (prompt.isEmpty()) {
                TaskBlocks.LOGGER.error("[TaskBlocks] input() needs a prompt");
                TaskBlocksNotifier.error("input() needs a prompt");
                return ActionResult.normal();
            }

            String result = awaitInput(prompt, defaultValue);

            if (varName != null) {
                ctx.variables.put(varName, result);
            }
            return ActionResult.normal();
        }

        return null;
    }

    private static String awaitInput(String prompt, String defaultValue) throws InterruptedException {
        MinecraftClient client = MinecraftClient.getInstance();
        AtomicReference<String> resultHolder = new AtomicReference<>();
        AtomicBoolean done = new AtomicBoolean(false);

        client.execute(() -> client.setScreen(new ScriptInputScreen(prompt, defaultValue, submitted -> {
            resultHolder.set(submitted);
            done.set(true);
        })));

        long startTime = System.currentTimeMillis();
        while (!done.get() && ScriptRunner.isRunning()) {
            if (System.currentTimeMillis() - startTime > INPUT_TIMEOUT_MS) {
                TaskBlocks.LOGGER.warn("[TaskBlocks] input(): timed out waiting for a response, using default");
                TaskBlocksNotifier.warn("input(): timed out, using default");
                closeIfOpen(client);
                return defaultValue;
            }
            Thread.sleep(50);
        }

        if (!ScriptRunner.isRunning()) {
            // Script was stopped externally while waiting on the prompt.
            closeIfOpen(client);
            return defaultValue;
        }

        String result = resultHolder.get();
        return result != null ? result : defaultValue;
    }

    private static void closeIfOpen(MinecraftClient client) {
        client.execute(() -> {
            if (client.currentScreen instanceof ScriptInputScreen) {
                client.currentScreen.close();
            }
        });
    }

    private static String stripQuotes(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2)
                || (value.startsWith("'") && value.endsWith("'") && value.length() >= 2)) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
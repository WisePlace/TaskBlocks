package com.taskblocks.script.actions;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class ChatActions {

    public static void register() {
        ActionRegistry.register(ChatActions::handle);
    }

    private static ActionResult handle(String action, ActionContext ctx)
            throws InterruptedException {
        if (!action.startsWith("say(") || !action.endsWith(")")) return null;

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

        // Strip surrounding quotes
        if ((message.startsWith("\"") && message.endsWith("\"")) ||
            (message.startsWith("'")  && message.endsWith("'"))) {
            message = message.substring(1, message.length() - 1);
        }

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
}
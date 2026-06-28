package com.taskblocks.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.concurrent.ConcurrentLinkedQueue;

public class TaskBlocksNotifier {

    private static boolean debugMode = false;
    private static final ConcurrentLinkedQueue<String> messageQueue = new ConcurrentLinkedQueue<>();

    public static void setDebugMode(boolean debug) {
        debugMode = debug;
    }

    public static boolean isDebugMode() {
        return debugMode;
    }

    public static void warn(String message) {
        if (!debugMode) return;
        queue("§e " + message);
    }

    public static void error(String message) {
        if (!debugMode) return;
        queue("§c " + message);
    }

    public static void info(String message) {
        if (!debugMode) return;
        queue("§b " + message);
    }

    public static void notice(String message) {
        queue("§b§l* §r" + message);
    }

    private static void queue(String message) {
        messageQueue.add("§8[§bTaskBlocks§8] §r" + message);
    }

    public static void flushMessages() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        String msg;
        while ((msg = messageQueue.poll()) != null) {
            client.inGameHud.getChatHud().addMessage(Text.literal(msg));
        }
    }
}
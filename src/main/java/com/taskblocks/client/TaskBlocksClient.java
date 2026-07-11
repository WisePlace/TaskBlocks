package com.taskblocks.client;

import com.taskblocks.TaskBlocks;
import com.taskblocks.client.gui.ScriptMenuScreen;
import com.taskblocks.script.ScriptData;
import com.taskblocks.script.ScriptLoader;
import com.taskblocks.script.ScriptRunner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskBlocksClient implements ClientModInitializer {

    public static KeyBinding openMenuKey;

    private static final KeyBinding.Category TASKBLOCKS_CATEGORY =
        KeyBinding.Category.create(Identifier.of("taskblocks", "general"));

    private static List<ScriptData> cachedScripts = null;

    private static final Map<String, Boolean> prevKeyState = new HashMap<>();

    public static void reloadScripts() {
        cachedScripts = ScriptLoader.loadScripts();
        prevKeyState.clear();
        TaskBlocks.LOGGER.info("[TaskBlocks] Scripts reloaded: " + cachedScripts.size());
    }

    public static List<ScriptData> getCachedScripts() {
        return cachedScripts;
    }

    @Override
    public void onInitializeClient() {
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.taskblocks.open_menu",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            TASKBLOCKS_CATEGORY
        ));

        reloadScripts();
        ScriptOverlay.register();

        // Main tick — handles menu, keybinds, notifier
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            TaskBlocksNotifier.flushMessages();
            ScriptRunner.tick(client);
            com.taskblocks.script.MacroRecorder.tick(client);
            if (client.player == null) return;

            // Open menu
            while (openMenuKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new ScriptMenuScreen());
                }
            }

            if (cachedScripts == null) return;

            for (ScriptData script : cachedScripts) {
                if (!script.enabled) continue;
                if (script.startStopKey == null
                        || script.startStopKey.equalsIgnoreCase("NONE")) continue;

                boolean pressed = KeyComboUtil.isComboDown(client, script.startStopKey);
                boolean wasPressed = prevKeyState.getOrDefault(script.startStopKey, false);

                if (pressed && !wasPressed) {
                    long conflictCount = cachedScripts.stream()
                        .filter(s -> s.enabled
                            && s.startStopKey != null
                            && s.startStopKey.equalsIgnoreCase(script.startStopKey))
                        .count();
                    if (conflictCount > 1) {
                        TaskBlocks.LOGGER.warn("[TaskBlocks] Keybind conflict on '"
                            + script.startStopKey
                            + "' — multiple scripts share this key, only '"
                            + script.name + "' will trigger.");
                        TaskBlocksNotifier.notice("Keybind conflict on '"
                            + script.startStopKey
                            + "' — only '" + script.name + "' will trigger.");
                    }

                    if (ScriptRunner.isRunning()
                            && script.name.equals(ScriptRunner.getRunningScriptName())) {
                        ScriptRunner.stop();
                        TaskBlocks.LOGGER.info("[TaskBlocks] Stopped: " + script.name);
                    } else if (!ScriptRunner.isRunning()) {
                        ScriptRunner.run(script, 0);
                        TaskBlocks.LOGGER.info("[TaskBlocks] Started: " + script.name);
                    }
                }

                prevKeyState.put(script.startStopKey, pressed);
            }
        });

        // Force block breaking even when a screen is open
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (!ScriptRunner.isRunning()) return;
            if (client.player == null || client.interactionManager == null) return;
            if (client.currentScreen == null) return;
            if (ScriptRunner.isHoldingMouse("left")) {
                client.options.attackKey.setPressed(true);
                client.attackCooldown = 0;
                // Call handleBlockBreaking logic directly without the accessor
                if (client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit) {
                    if (!client.world.getBlockState(hit.getBlockPos()).isAir()) {
                        client.interactionManager.updateBlockBreakingProgress(
                            hit.getBlockPos(), hit.getSide());
                        client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                    }
                }
            }
        });

        TaskBlocks.LOGGER.info("[TaskBlocks] Client initialized.");
    }
}
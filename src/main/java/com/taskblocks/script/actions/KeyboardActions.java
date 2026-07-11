package com.taskblocks.script.actions;

import com.taskblocks.TaskBlocks;
import com.taskblocks.client.TaskBlocksNotifier;
import com.taskblocks.script.ScriptRunner;

import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public class KeyboardActions {

    public static void register() {
        ActionRegistry.register(KeyboardActions::handle);
    }

    private static ActionResult handle(String action, ActionContext ctx)
            throws InterruptedException {
        MinecraftClient client = MinecraftClient.getInstance();

        // ============================================================
        // SHORTCUT ACTIONS (named actions for common keys)
        // ============================================================

        // jump — press and release jump key
        if (action.equalsIgnoreCase("jump")) {
            triggerKey(client, client.options.jumpKey, true);
            Thread.sleep(80);
            triggerKey(client, client.options.jumpKey, false);
            Thread.sleep(30);
            return ActionResult.normal();
        }

        // jump_press / jump_release
        if (action.equalsIgnoreCase("jump_press")) {
            triggerKey(client, client.options.jumpKey, true);
            ScriptRunner.holdKey("jump");
            Thread.sleep(30);
            return ActionResult.normal();
        }
        if (action.equalsIgnoreCase("jump_release")) {
            triggerKey(client, client.options.jumpKey, false);
            ScriptRunner.releaseKey("jump");
            Thread.sleep(30);
            return ActionResult.normal();
        }

        // sneak_press / sneak_release
        if (action.equalsIgnoreCase("sneak_press")) {
            triggerKey(client, client.options.sneakKey, true);
            ScriptRunner.holdKey("sneak");
            Thread.sleep(30);
            return ActionResult.normal();
        }
        if (action.equalsIgnoreCase("sneak_release")) {
            triggerKey(client, client.options.sneakKey, false);
            ScriptRunner.releaseKey("sneak");
            Thread.sleep(30);
            return ActionResult.normal();
        }

        // sprint_press / sprint_release
        if (action.equalsIgnoreCase("sprint_press")) {
            triggerKey(client, client.options.sprintKey, true);
            ScriptRunner.holdKey("sprint");
            Thread.sleep(30);
            return ActionResult.normal();
        }
        if (action.equalsIgnoreCase("sprint_release")) {
            triggerKey(client, client.options.sprintKey, false);
            ScriptRunner.releaseKey("sprint");
            Thread.sleep(30);
            return ActionResult.normal();
        }

        // forward_press / forward_release
        if (action.equalsIgnoreCase("forward_press")) {
            triggerKey(client, client.options.forwardKey, true);
            ScriptRunner.holdKey("forward");
            Thread.sleep(30);
            return ActionResult.normal();
        }
        if (action.equalsIgnoreCase("forward_release")) {
            triggerKey(client, client.options.forwardKey, false);
            ScriptRunner.releaseKey("forward");
            Thread.sleep(30);
            return ActionResult.normal();
        }

        // backward_press / backward_release
        if (action.equalsIgnoreCase("backward_press")) {
            triggerKey(client, client.options.backKey, true);
            ScriptRunner.holdKey("backward");
            Thread.sleep(30);
            return ActionResult.normal();
        }
        if (action.equalsIgnoreCase("backward_release")) {
            triggerKey(client, client.options.backKey, false);
            ScriptRunner.releaseKey("backward");
            Thread.sleep(30);
            return ActionResult.normal();
        }

        // left_press / left_release
        if (action.equalsIgnoreCase("left_press")) {
            triggerKey(client, client.options.leftKey, true);
            ScriptRunner.holdKey("left");
            Thread.sleep(30);
            return ActionResult.normal();
        }
        if (action.equalsIgnoreCase("left_release")) {
            triggerKey(client, client.options.leftKey, false);
            ScriptRunner.releaseKey("left");
            Thread.sleep(30);
            return ActionResult.normal();
        }

        // right_press / right_release
        if (action.equalsIgnoreCase("right_press")) {
            triggerKey(client, client.options.rightKey, true);
            ScriptRunner.holdKey("right");
            Thread.sleep(30);
            return ActionResult.normal();
        }
        if (action.equalsIgnoreCase("right_release")) {
            triggerKey(client, client.options.rightKey, false);
            ScriptRunner.releaseKey("right");
            Thread.sleep(30);
            return ActionResult.normal();
        }

        // drop — drop one item from held slot
        if (action.equalsIgnoreCase("drop")) {
            triggerKey(client, client.options.dropKey, true);
            Thread.sleep(80);
            triggerKey(client, client.options.dropKey, false);
            Thread.sleep(30);
            return ActionResult.normal();
        }

        // drop_stack — drop entire stack
        if (action.equalsIgnoreCase("drop_stack")) {
            client.execute(() -> {
                if (client.player != null) {
                    client.player.dropSelectedItem(true);
                }
            });
            Thread.sleep(50);
            return ActionResult.normal();
        }

        // open_inventory — opens player inventory
        if (action.equalsIgnoreCase("open_inventory")) {
            triggerKey(client, client.options.inventoryKey, true);
            Thread.sleep(80);
            triggerKey(client, client.options.inventoryKey, false);
            Thread.sleep(30);
            return ActionResult.normal();
        }
        // disconnect — disconnect from current server
        if (action.equalsIgnoreCase("disconnect")) {
            client.execute(() -> {
                if (client.getNetworkHandler() != null) {
                    client.getNetworkHandler()
                        .getConnection()
                        .disconnect(Text.literal("Disconnected by TaskBlocks"));
                }
            });

            Thread.sleep(100);
            return ActionResult.normal();
        }

        // ============================================================
        // DEFAULT KEY ALIASES
        // These tap the player's actual bound key — works regardless
        // of what key the user has configured in Minecraft settings
        // ============================================================

        if (action.equalsIgnoreCase("key_forward")) {
            return tapKey(client, client.options.forwardKey);
        }
        if (action.equalsIgnoreCase("key_backward")) {
            return tapKey(client, client.options.backKey);
        }
        if (action.equalsIgnoreCase("key_left")) {
            return tapKey(client, client.options.leftKey);
        }
        if (action.equalsIgnoreCase("key_right")) {
            return tapKey(client, client.options.rightKey);
        }
        if (action.equalsIgnoreCase("key_jump")) {
            return tapKey(client, client.options.jumpKey);
        }
        if (action.equalsIgnoreCase("key_sneak")) {
            return tapKey(client, client.options.sneakKey);
        }
        if (action.equalsIgnoreCase("key_sprint")) {
            return tapKey(client, client.options.sprintKey);
        }
        if (action.equalsIgnoreCase("key_drop")) {
            return tapKey(client, client.options.dropKey);
        }
        if (action.equalsIgnoreCase("key_inventory")) {
            return tapKey(client, client.options.inventoryKey);
        }
        if (action.equalsIgnoreCase("key_swap_hands")) {
            return tapKey(client, client.options.swapHandsKey);
        }
        if (action.equalsIgnoreCase("key_chat")) {
            return tapKey(client, client.options.chatKey);
        }

        // ============================================================
        // RAW KEY ACTIONS
        // key_tap(key), key_press(key), key_release(key)
        // ============================================================

        if (action.startsWith("key_tap(") && action.endsWith(")")) {
            String keyName = action.substring(8, action.length() - 1).trim().toLowerCase();
            InputUtil.Key key = resolveKey(keyName);
            if (key != null) {
                client.execute(() -> {
                    KeyBinding.setKeyPressed(key, true);
                    KeyBinding.onKeyPressed(key);
                });
                Thread.sleep(80);
                client.execute(() -> KeyBinding.setKeyPressed(key, false));
                Thread.sleep(30);
            }
            return ActionResult.normal();
        }

        if (action.startsWith("key_press(") && action.endsWith(")")) {
            String keyName = action.substring(10, action.length() - 1).trim().toLowerCase();
            InputUtil.Key key = resolveKey(keyName);
            if (key != null) {
                client.execute(() -> {
                    KeyBinding.setKeyPressed(key, true);
                    KeyBinding.onKeyPressed(key);
                });
                ScriptRunner.holdKey(keyName);
                Thread.sleep(30);
            }
            return ActionResult.normal();
        }

        if (action.startsWith("key_release(") && action.endsWith(")")) {
            String keyName = action.substring(12, action.length() - 1).trim().toLowerCase();
            InputUtil.Key key = resolveKey(keyName);
            if (key != null) {
                client.execute(() -> KeyBinding.setKeyPressed(key, false));
                ScriptRunner.releaseKey(keyName);
                Thread.sleep(30);
            }
            return ActionResult.normal();
        }

        return null;
    }

    // Tap a KeyBinding using its actual bound key (respects user's controls)
    private static ActionResult tapKey(MinecraftClient client, KeyBinding kb)
            throws InterruptedException {
        client.execute(() -> {
            kb.setPressed(true);
            KeyBinding.onKeyPressed(kb.getBoundKeyTranslationKey().equals("key.keyboard.unknown")
                ? InputUtil.UNKNOWN_KEY
                : InputUtil.fromTranslationKey(kb.getBoundKeyTranslationKey()));
        });
        Thread.sleep(80);
        client.execute(() -> kb.setPressed(false));
        Thread.sleep(30);
        return ActionResult.normal();
    }

    // Trigger a KeyBinding's pressed state directly
    private static void triggerKey(MinecraftClient client, KeyBinding kb, boolean pressed) {
        client.execute(() -> {
            kb.setPressed(pressed);
            if (pressed) {
                KeyBinding.onKeyPressed(
                    InputUtil.fromTranslationKey(kb.getBoundKeyTranslationKey()));
            }
        });
    }

    private static InputUtil.Key resolveKey(String keyName) {
        String normalized = keyName.replace("_", ".");
        String[] attempts = {
            "key.keyboard." + normalized,
            "key.keyboard." + keyName,
            normalized,
            keyName
        };
        for (String attempt : attempts) {
            try {
                return InputUtil.fromTranslationKey(attempt);
            } catch (IllegalArgumentException ignored) {}
        }
        TaskBlocks.LOGGER.error("[TaskBlocks] Unknown key: '" + keyName
            + "'. Examples: e, w, space, left.shift, left.control, f1...");
        TaskBlocksNotifier.error("Unknown key: §f" + keyName
            + " — Examples: e, w, space, left.shift, left.control, f1...");
        return null;
    }
}
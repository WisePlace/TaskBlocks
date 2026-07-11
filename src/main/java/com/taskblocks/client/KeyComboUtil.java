package com.taskblocks.client;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

// ============================================================
// Shared keybind parsing: supports single keys ("K") and modifier
// combos ("CTRL+K", "CTRL+ALT+K"). Used by both the start_stop_key
// script trigger and the listen() key() condition, so both understand
// the same syntax.
//
// A combo is "down" only while every key in it is held at once.
// CTRL/ALT/SHIFT match either their left or right physical key.
// ============================================================
public class KeyComboUtil {

    public static boolean isComboDown(MinecraftClient client, String combo) {
        if (combo == null || combo.isBlank() || combo.equalsIgnoreCase("NONE")) return false;

        String[] parts = combo.split("\\+");
        for (String part : parts) {
            if (!isSingleKeyDown(client, part.trim())) return false;
        }
        return true;
    }

    private static boolean isSingleKeyDown(MinecraftClient client, String keyName) {
        if (keyName.isEmpty()) return false;
        String normalized = keyName.toLowerCase();

        switch (normalized) {
            case "ctrl":
            case "control":
                return isRawKeyPressed(client, GLFW.GLFW_KEY_LEFT_CONTROL)
                    || isRawKeyPressed(client, GLFW.GLFW_KEY_RIGHT_CONTROL);
            case "alt":
                return isRawKeyPressed(client, GLFW.GLFW_KEY_LEFT_ALT)
                    || isRawKeyPressed(client, GLFW.GLFW_KEY_RIGHT_ALT);
            case "shift":
                return isRawKeyPressed(client, GLFW.GLFW_KEY_LEFT_SHIFT)
                    || isRawKeyPressed(client, GLFW.GLFW_KEY_RIGHT_SHIFT);
        }

        try {
            String translated = normalized.replace("_", ".");
            String translationKey = translated.startsWith("key.keyboard.")
                ? translated : "key.keyboard." + translated;
            InputUtil.Key key = InputUtil.fromTranslationKey(translationKey);
            return InputUtil.isKeyPressed(client.getWindow(), key.getCode());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isRawKeyPressed(MinecraftClient client, int glfwCode) {
        return InputUtil.isKeyPressed(client.getWindow(), glfwCode);
    }
}
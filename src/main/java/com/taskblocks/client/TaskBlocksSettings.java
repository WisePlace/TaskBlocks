package com.taskblocks.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import com.taskblocks.TaskBlocks;

import net.fabricmc.loader.api.FabricLoader;

// Persisted TaskBlocks settings, stored as a simple key=value file at
// config/TaskBlocks/settings.properties. Loaded lazily on first access,
// saved immediately whenever a setter is called.
public class TaskBlocksSettings {

    private static final Path SETTINGS_FILE = FabricLoader.getInstance()
        .getConfigDir().resolve("TaskBlocks").resolve("settings.properties");

    private static final Properties properties = new Properties();
    private static boolean loaded = false;

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (Files.exists(SETTINGS_FILE)) {
            try (var in = Files.newInputStream(SETTINGS_FILE)) {
                properties.load(in);
            } catch (IOException e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Failed to load settings.properties", e);
            }
        }
    }

    private static void save() {
        try {
            Files.createDirectories(SETTINGS_FILE.getParent());
            try (var out = Files.newOutputStream(SETTINGS_FILE)) {
                properties.store(out, "TaskBlocks settings");
            }
        } catch (IOException e) {
            TaskBlocks.LOGGER.error("[TaskBlocks] Failed to save settings.properties", e);
        }
    }

    public static boolean isShowDefaultScripts() {
        ensureLoaded();
        return Boolean.parseBoolean(properties.getProperty("show_default_scripts", "true"));
    }

    public static void setShowDefaultScripts(boolean value) {
        ensureLoaded();
        properties.setProperty("show_default_scripts", String.valueOf(value));
        save();
    }
}
package com.taskblocks.script;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.taskblocks.TaskBlocks;

import net.fabricmc.loader.api.FabricLoader;

public class ScriptLoader {

    private static final Path SCRIPTS_DIR = FabricLoader.getInstance()
        .getConfigDir().resolve("TaskBlocks");

    public static List<ScriptData> loadScripts() {
        List<ScriptData> scripts = new ArrayList<>();

        // Create the folder if it doesn't exist yet
        if (!Files.exists(SCRIPTS_DIR)) {
            try {
                Files.createDirectories(SCRIPTS_DIR);
            } catch (IOException e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Could not create scripts directory.", e);
                return scripts;
            }
        }

        // Find all .tbs files
        try (var stream = Files.list(SCRIPTS_DIR)) {
            stream.filter(p -> p.toString().endsWith(".tbs")).forEach(path -> {
                try {
                    ScriptData script = parseScript(path);
                    if (script != null) scripts.add(script);
                } catch (Exception e) {
                    TaskBlocks.LOGGER.error("[TaskBlocks] Failed to load script: " + path.getFileName(), e);
                }
            });
        } catch (IOException e) {
            TaskBlocks.LOGGER.error("[TaskBlocks] Failed to read scripts directory.", e);
        }

        return scripts;
    }

    private static ScriptData parseScript(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);

        String name = "Unnamed";
        String version = "1.0";
        String author = "Unknown";
        String startStopKey = "NONE";
        String pauseKey = "NONE";
        boolean enabled = false;
        boolean debug = false;
        List<String> actions = new ArrayList<>();
        boolean inActions = false;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            if (line.equalsIgnoreCase("[actions]")) {
                inActions = true;
                continue;
            }

            if (inActions) {
                actions.add(line);
            } else {
                if (line.startsWith("name=")) name = line.substring(5).trim();
                else if (line.startsWith("version=")) version = line.substring(8).trim();
                else if (line.startsWith("author=")) author = line.substring(7).trim();
                else if (line.startsWith("start_stop_key=")) startStopKey = line.substring(15).trim();
                else if (line.startsWith("pause_key=")) pauseKey = line.substring(10).trim();
                else if (line.startsWith("enabled=")) enabled = line.substring(8).trim().equalsIgnoreCase("true");
                else if (line.startsWith("debug=")) debug = line.substring(6).trim().equalsIgnoreCase("true");
            }
        }

        return new ScriptData(name, path.getFileName().toString(), version, author,
            startStopKey, pauseKey, enabled, debug, actions);
    }

    public static void saveScript(ScriptData script) {
    Path filePath = SCRIPTS_DIR.resolve(script.fileName);
    try {
        List<String> lines = Files.readAllLines(filePath);
        List<String> newLines = new ArrayList<>();
        for (String line : lines) {
            if (line.trim().startsWith("enabled=")) {
                newLines.add("enabled=" + script.enabled);
            } else {
                newLines.add(line);
            }
        }
        Files.write(filePath, newLines);
    } catch (IOException e) {
        TaskBlocks.LOGGER.error("[TaskBlocks] Failed to save script: " + script.fileName, e);
    }
    }
}
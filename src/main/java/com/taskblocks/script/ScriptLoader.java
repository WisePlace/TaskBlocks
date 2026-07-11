package com.taskblocks.script;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.taskblocks.TaskBlocks;

import net.fabricmc.loader.api.FabricLoader;

public class ScriptLoader {

    private static final Path SCRIPTS_DIR = FabricLoader.getInstance()
        .getConfigDir().resolve("TaskBlocks");

    private static final Pattern DEF_PATTERN =
        Pattern.compile("^def\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*:$", Pattern.CASE_INSENSITIVE);

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
        boolean enabled = false;
        boolean debug = false;
        List<String> actions = new ArrayList<>();
        Map<String, FunctionDef> functions = new LinkedHashMap<>();
        boolean inActions = false;
        boolean inFunctions = false;

        // State for the function currently being parsed, if any
        String currentFuncName = null;
        List<String> currentFuncParams = null;
        List<String> currentFuncBody = null;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            if (line.equalsIgnoreCase("[functions]")) {
                inFunctions = true;
                inActions = false;
                continue;
            }

            if (line.equalsIgnoreCase("[actions]")) {
                if (currentFuncName != null) {
                    TaskBlocks.LOGGER.warn("[TaskBlocks] " + path.getFileName()
                        + ": function '" + currentFuncName + "' is missing 'enddef'");
                }
                inActions = true;
                inFunctions = false;
                currentFuncName = null;
                currentFuncParams = null;
                currentFuncBody = null;
                continue;
            }

            if (inFunctions) {
                Matcher defMatch = DEF_PATTERN.matcher(line);
                if (defMatch.matches()) {
                    if (currentFuncName != null) {
                        TaskBlocks.LOGGER.warn("[TaskBlocks] " + path.getFileName()
                            + ": function '" + currentFuncName + "' is missing 'enddef'");
                    }
                    currentFuncName = defMatch.group(1);
                    String paramsRaw = defMatch.group(2).trim();
                    currentFuncParams = new ArrayList<>();
                    if (!paramsRaw.isEmpty()) {
                        for (String p : paramsRaw.split(",")) {
                            currentFuncParams.add(p.trim());
                        }
                    }
                    currentFuncBody = new ArrayList<>();
                } else if (line.equalsIgnoreCase("enddef")) {
                    if (currentFuncName == null) {
                        TaskBlocks.LOGGER.warn("[TaskBlocks] " + path.getFileName()
                            + ": stray 'enddef' with no matching def");
                    } else {
                        if (functions.containsKey(currentFuncName.toLowerCase())) {
                            TaskBlocks.LOGGER.warn("[TaskBlocks] " + path.getFileName()
                                + ": duplicate function '" + currentFuncName + "', keeping the last definition");
                        }
                        functions.put(currentFuncName.toLowerCase(),
                            new FunctionDef(currentFuncName, currentFuncParams, currentFuncBody));
                        currentFuncName = null;
                        currentFuncParams = null;
                        currentFuncBody = null;
                    }
                } else if (currentFuncName != null) {
                    currentFuncBody.add(line);
                } else {
                    TaskBlocks.LOGGER.warn("[TaskBlocks] " + path.getFileName()
                        + ": line inside [functions] outside any def block: " + line);
                }
                continue;
            }

            if (inActions) {
                actions.add(line);
            } else {
                if (line.startsWith("name=")) name = line.substring(5).trim();
                else if (line.startsWith("version=")) version = line.substring(8).trim();
                else if (line.startsWith("author=")) author = line.substring(7).trim();
                else if (line.startsWith("start_stop_key=")) startStopKey = line.substring(15).trim();
                else if (line.startsWith("enabled=")) enabled = line.substring(8).trim().equalsIgnoreCase("true");
                else if (line.startsWith("debug=")) debug = line.substring(6).trim().equalsIgnoreCase("true");
            }
        }

        if (currentFuncName != null) {
            TaskBlocks.LOGGER.warn("[TaskBlocks] " + path.getFileName()
                + ": function '" + currentFuncName + "' is missing 'enddef'");
        }

        return new ScriptData(name, path.getFileName().toString(), version, author,
            startStopKey, enabled, debug, actions, functions);
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
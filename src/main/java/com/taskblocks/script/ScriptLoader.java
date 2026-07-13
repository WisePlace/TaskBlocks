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
import com.taskblocks.client.TaskBlocksSettings;
import com.taskblocks.script.actions.ArgSplitter;

import net.fabricmc.loader.api.FabricLoader;

// Loads, parses, creates, and saves .tbs script files. Scans the whole
// config/TaskBlocks folder recursively, so scripts can be organized
// into subfolders. Handles the header, [functions] with def/enddef,
// [actions], import= for pulling in .tbsx function files from anywhere
// under config/TaskBlocks, and desugars inline
// listen(id, condition) { ... } blocks into an auto-generated hidden
// function plus a call() action, so the rest of the mod never needs
// to know the block syntax exists.
public class ScriptLoader {

    private static final Path SCRIPTS_DIR = FabricLoader.getInstance()
        .getConfigDir().resolve("TaskBlocks");

    private static final String DEFAULT_FOLDER_NAME = "default";

    private static final Pattern DEF_PATTERN =
        Pattern.compile("^def\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*:$", Pattern.CASE_INSENSITIVE);

    public static List<ScriptData> loadScripts() {
        List<ScriptData> scripts = new ArrayList<>();

        if (!Files.exists(SCRIPTS_DIR)) {
            try {
                Files.createDirectories(SCRIPTS_DIR);
            } catch (IOException e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Could not create scripts directory.", e);
                return scripts;
            }
        }

        boolean showDefault = TaskBlocksSettings.isShowDefaultScripts();

        try (var stream = Files.walk(SCRIPTS_DIR)) {
            stream.filter(p -> p.toString().endsWith(".tbs")).forEach(path -> {
                Path relative = SCRIPTS_DIR.relativize(path);
                boolean isDefault = relative.getNameCount() > 1
                    && relative.getName(0).toString().equalsIgnoreCase(DEFAULT_FOLDER_NAME);

                if (isDefault && !showDefault) return;

                try {
                    ScriptData script = parseScript(path, relative);
                    if (script != null) scripts.add(script);
                } catch (Exception e) {
                    TaskBlocks.LOGGER.error("[TaskBlocks] Failed to load script: " + path.getFileName(), e);
                }
            });
        } catch (IOException e) {
            TaskBlocks.LOGGER.error("[TaskBlocks] Failed to read scripts directory.", e);
        }

        scripts.sort((a, b) -> Boolean.compare(b.favorite, a.favorite));
        return scripts;
    }

    private static ScriptData parseScript(Path path, Path relativePath) throws IOException {
        List<String> rawLines = Files.readAllLines(path);

        String name = "Unnamed";
        String version = "1.0";
        String author = "Unknown";
        String startStopKey = "NONE";
        boolean enabled = false;
        boolean debug = false;
        boolean favorite = false;
        List<String> actions = new ArrayList<>();
        Map<String, FunctionDef> functions = new LinkedHashMap<>();
        List<String> importFiles = new ArrayList<>();
        boolean inActions = false;
        boolean inFunctions = false;

        String currentFuncName = null;
        List<String> currentFuncParams = null;
        List<String> currentFuncBody = null;

        int[] listenBlockCounter = {0};

        int i = 0;
        while (i < rawLines.size()) {
            String line = rawLines.get(i).trim();
            i++;

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
                    if (isListenBlockStart(line)) {
                        listenBlockCounter[0]++;
                        String blockName = "__listen_block_" + listenBlockCounter[0];
                        i = consumeListenBlock(rawLines, i, line, blockName, functions, currentFuncBody, path);
                    } else {
                        currentFuncBody.add(line);
                    }
                } else {
                    TaskBlocks.LOGGER.warn("[TaskBlocks] " + path.getFileName()
                        + ": line inside [functions] outside any def block: " + line);
                }
                continue;
            }

            if (inActions) {
                if (isListenBlockStart(line)) {
                    listenBlockCounter[0]++;
                    String blockName = "__listen_block_" + listenBlockCounter[0];
                    i = consumeListenBlock(rawLines, i, line, blockName, functions, actions, path);
                } else {
                    actions.add(line);
                }
            } else {
                if (line.startsWith("name=")) name = line.substring(5).trim();
                else if (line.startsWith("version=")) version = line.substring(8).trim();
                else if (line.startsWith("author=")) author = line.substring(7).trim();
                else if (line.startsWith("start_stop_key=")) startStopKey = line.substring(15).trim();
                else if (line.startsWith("enabled=")) enabled = line.substring(8).trim().equalsIgnoreCase("true");
                else if (line.startsWith("debug=")) debug = line.substring(6).trim().equalsIgnoreCase("true");
                else if (line.startsWith("favorite=")) favorite = line.substring(9).trim().equalsIgnoreCase("true");
                else if (line.startsWith("import=")) {
                    String importFile = line.substring(7).trim();
                    if (!importFile.isEmpty()) {
                        importFile = importFile.replace('\\', '/');
                        if (!importFile.toLowerCase().endsWith(".tbsx")) importFile += ".tbsx";
                        importFiles.add(importFile);
                    }
                }
            }
        }

        if (currentFuncName != null) {
            TaskBlocks.LOGGER.warn("[TaskBlocks] " + path.getFileName()
                + ": function '" + currentFuncName + "' is missing 'enddef'");
        }

        for (String importFile : importFiles) {
            Path importPath = SCRIPTS_DIR.resolve(importFile);
            if (!Files.exists(importPath)) {
                TaskBlocks.LOGGER.error("[TaskBlocks] " + path.getFileName()
                    + ": import file not found: " + importFile);
                continue;
            }
            try {
                Map<String, FunctionDef> imported = parseFunctionFile(importPath);
                for (Map.Entry<String, FunctionDef> entry : imported.entrySet()) {
                    functions.putIfAbsent(entry.getKey(), entry.getValue());
                }
            } catch (IOException e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] " + path.getFileName()
                    + ": failed to load import: " + importFile, e);
            }
        }

        String storedFileName = relativePath.toString().replace('\\', '/');

        return new ScriptData(name, storedFileName, version, author,
            startStopKey, enabled, debug, favorite, actions, functions);
    }

    private static Map<String, FunctionDef> parseFunctionFile(Path path) throws IOException {
        List<String> rawLines = Files.readAllLines(path);
        Map<String, FunctionDef> functions = new LinkedHashMap<>();

        String currentFuncName = null;
        List<String> currentFuncParams = null;
        List<String> currentFuncBody = null;
        int[] listenBlockCounter = {0};

        int i = 0;
        while (i < rawLines.size()) {
            String line = rawLines.get(i).trim();
            i++;

            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.equalsIgnoreCase("[functions]")) continue;
            if (line.toLowerCase().startsWith("version=")) continue;
            if (line.toLowerCase().startsWith("owner=")) continue;

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
                    functions.put(currentFuncName.toLowerCase(),
                        new FunctionDef(currentFuncName, currentFuncParams, currentFuncBody));
                    currentFuncName = null;
                    currentFuncParams = null;
                    currentFuncBody = null;
                }
            } else if (currentFuncName != null) {
                if (isListenBlockStart(line)) {
                    listenBlockCounter[0]++;
                    String blockName = "__listen_block_import_" + listenBlockCounter[0];
                    i = consumeListenBlock(rawLines, i, line, blockName, functions, currentFuncBody, path);
                } else {
                    currentFuncBody.add(line);
                }
            } else {
                TaskBlocks.LOGGER.warn("[TaskBlocks] " + path.getFileName()
                    + ": line outside any def block: " + line);
            }
        }

        if (currentFuncName != null) {
            TaskBlocks.LOGGER.warn("[TaskBlocks] " + path.getFileName()
                + ": function '" + currentFuncName + "' is missing 'enddef'");
        }

        return functions;
    }

    private static boolean isListenBlockStart(String line) {
        return line.toLowerCase().startsWith("listen(") && line.endsWith("{");
    }

    private static int consumeListenBlock(List<String> rawLines, int startIndex, String openingLine,
            String blockName, Map<String, FunctionDef> functions, List<String> targetList, Path path) {
        int openBraceIdx = openingLine.lastIndexOf('{');
        String beforeBrace = openingLine.substring(0, openBraceIdx).trim();

        if (!beforeBrace.endsWith(")")) {
            TaskBlocks.LOGGER.warn("[TaskBlocks] " + path.getFileName()
                + ": malformed listen() block header: " + openingLine);
            targetList.add(openingLine);
            return startIndex;
        }

        String innerArgs = beforeBrace.substring(beforeBrace.indexOf('(') + 1, beforeBrace.length() - 1);
        List<String> parts = ArgSplitter.split(innerArgs);

        if (parts.size() < 2 || parts.size() > 3) {
            TaskBlocks.LOGGER.error("[TaskBlocks] " + path.getFileName()
                + ": listen() block needs 2 or 3 args before '{': listen(id, condition, [intervalTicks]) { ... }");
            targetList.add(openingLine);
            return startIndex;
        }

        String id = parts.get(0).trim();
        String condition = parts.get(1).trim();
        String interval = parts.size() == 3 ? parts.get(2).trim() : null;

        List<String> blockBody = new ArrayList<>();
        int i = startIndex;
        boolean closed = false;
        while (i < rawLines.size()) {
            String bodyLine = rawLines.get(i).trim();
            i++;
            if (bodyLine.equals("}")) {
                closed = true;
                break;
            }
            if (bodyLine.isEmpty() || bodyLine.startsWith("#")) continue;
            if (bodyLine.equalsIgnoreCase("[actions]") || bodyLine.equalsIgnoreCase("[functions]")
                    || bodyLine.equalsIgnoreCase("enddef")) {
                i--;
                break;
            }
            blockBody.add(bodyLine);
        }

        if (!closed) {
            TaskBlocks.LOGGER.warn("[TaskBlocks] " + path.getFileName()
                + ": listen() block '" + id + "' is missing a closing '}'");
        }

        functions.put(blockName.toLowerCase(), new FunctionDef(blockName, new ArrayList<>(), blockBody));

        String rebuilt = "listen(" + id + "," + condition + ",call(" + blockName + ")"
            + (interval != null ? "," + interval : "") + ")";
        targetList.add(rebuilt);

        return i;
    }

    public static void saveScript(ScriptData script) {
        Path filePath = SCRIPTS_DIR.resolve(script.fileName);
        try {
            List<String> lines = Files.readAllLines(filePath);
            List<String> newLines = new ArrayList<>();
            boolean sawFavorite = false;

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("enabled=")) {
                    newLines.add("enabled=" + script.enabled);
                } else if (trimmed.startsWith("favorite=")) {
                    newLines.add("favorite=" + script.favorite);
                    sawFavorite = true;
                } else {
                    newLines.add(line);
                }
            }

            if (!sawFavorite) {
                int insertAt = 0;
                for (int i = 0; i < newLines.size(); i++) {
                    if (newLines.get(i).trim().startsWith("enabled=")) {
                        insertAt = i + 1;
                        break;
                    }
                }
                newLines.add(insertAt, "favorite=" + script.favorite);
            }

            Files.write(filePath, newLines);
        } catch (IOException e) {
            TaskBlocks.LOGGER.error("[TaskBlocks] Failed to save script: " + script.fileName, e);
        }
    }

    public static String createScript(String name, String author, boolean debug, String startStopKey) {
        if (!Files.exists(SCRIPTS_DIR)) {
            try {
                Files.createDirectories(SCRIPTS_DIR);
            } catch (IOException e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Could not create scripts directory.", e);
                return null;
            }
        }

        String baseName = sanitizeFileName(name);
        String fileName = baseName + ".tbs";
        Path filePath = SCRIPTS_DIR.resolve(fileName);

        int suffix = 1;
        while (Files.exists(filePath)) {
            fileName = baseName + "_" + suffix + ".tbs";
            filePath = SCRIPTS_DIR.resolve(fileName);
            suffix++;
        }

        String key = (startStopKey == null || startStopKey.isBlank()) ? "NONE" : startStopKey.trim();

        String content = "name=" + name + "\n"
            + "author=" + author + "\n"
            + "version=1.0\n"
            + "enabled=true\n"
            + "favorite=false\n"
            + "debug=" + debug + "\n"
            + "start_stop_key=" + key + "\n"
            + "\n"
            + "[actions]\n"
            + "end\n";

        try {
            Files.writeString(filePath, content);
            return fileName;
        } catch (IOException e) {
            TaskBlocks.LOGGER.error("[TaskBlocks] Failed to create script: " + fileName, e);
            return null;
        }
    }

    public static boolean appendRecordedActions(String fileName, List<String> lines) {
        Path filePath = SCRIPTS_DIR.resolve(fileName);
        try {
            List<String> existing = Files.readAllLines(filePath);
            List<String> newLines = new ArrayList<>();
            boolean foundMarker = false;

            for (String line : existing) {
                newLines.add(line);
                if (line.trim().equalsIgnoreCase("[actions]")) {
                    foundMarker = true;
                    break;
                }
            }

            if (!foundMarker) {
                TaskBlocks.LOGGER.error("[TaskBlocks] appendRecordedActions: no [actions] marker in " + fileName);
                return false;
            }

            newLines.addAll(lines);
            newLines.add("end");

            Files.write(filePath, newLines);
            return true;
        } catch (IOException e) {
            TaskBlocks.LOGGER.error("[TaskBlocks] Failed to save recorded macro to: " + fileName, e);
            return false;
        }
    }

    private static String sanitizeFileName(String name) {
        String sanitized = name.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.isEmpty() ? "script" : sanitized;
    }
}
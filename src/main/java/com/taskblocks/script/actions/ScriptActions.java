package com.taskblocks.script.actions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.taskblocks.TaskBlocks;
import com.taskblocks.client.TaskBlocksClient;
import com.taskblocks.client.TaskBlocksNotifier;
import com.taskblocks.script.ScriptData;

// ============================================================
// Cross-script control — run_script() ends the current script and
// immediately starts another one by name. This is a handoff, not a
// subroutine call: the calling script does not resume afterward.
//
// Optional key=value pairs after the name are seeded as variables in
// the target script, e.g. run_script(Test, var1=Caca, var2=5).
// Values can reference the CALLING script's own variables using the
// normal {varName} syntax — that's resolved before this action even
// runs, since ScriptRunner interpolates the whole line first.
// ============================================================
public class ScriptActions {

    public static void register() {
        ActionRegistry.register(ScriptActions::handle);
    }

    private static ActionResult handle(String action, ActionContext ctx) {
        if (!action.startsWith("run_script(") || !action.endsWith(")")) return null;

        String inner = action.substring(action.indexOf('(') + 1, action.length() - 1);
        List<String> parts = ArgSplitter.split(inner);

        if (parts.isEmpty() || parts.get(0).isEmpty()) {
            TaskBlocks.LOGGER.error("[TaskBlocks] run_script() needs a script name");
            TaskBlocksNotifier.error("run_script() needs a script name");
            return ActionResult.normal();
        }

        String targetName = parts.get(0).trim();
        Map<String, String> initialVariables = new HashMap<>();

        for (int i = 1; i < parts.size(); i++) {
            String pair = parts.get(i).trim();
            if (pair.isEmpty()) continue;

            int eq = pair.indexOf('=');
            if (eq == -1) {
                TaskBlocks.LOGGER.warn("[TaskBlocks] run_script: ignoring malformed argument '" + pair + "'");
                TaskBlocksNotifier.warn("run_script: ignoring malformed argument '" + pair + "'");
                continue;
            }

            String key = pair.substring(0, eq).trim();
            String value = pair.substring(eq + 1).trim();
            if (!key.isEmpty()) initialVariables.put(key, value);
        }

        ScriptData target = findScript(targetName);

        if (target == null) {
            TaskBlocks.LOGGER.error("[TaskBlocks] run_script: no script named '" + targetName + "'");
            TaskBlocksNotifier.error("run_script: no script named '" + targetName + "'");
            return ActionResult.normal();
        }

        if (!target.enabled) {
            TaskBlocks.LOGGER.error("[TaskBlocks] run_script: '" + targetName + "' is disabled");
            TaskBlocksNotifier.error("run_script: '" + targetName + "' is disabled");
            return ActionResult.normal();
        }

        TaskBlocksNotifier.info("Handing off to: §f" + target.name);
        return ActionResult.chain(target.name, initialVariables);
    }

    public static ScriptData findScript(String name) {
        List<ScriptData> scripts = TaskBlocksClient.getCachedScripts();
        if (scripts == null) return null;
        return scripts.stream()
            .filter(s -> s.name.equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }
}
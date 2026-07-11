package com.taskblocks.script.actions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.taskblocks.TaskBlocks;
import com.taskblocks.client.TaskBlocksNotifier;
import com.taskblocks.script.ExecOutcome;
import com.taskblocks.script.FunctionDef;
import com.taskblocks.script.ScriptRunner;

// ============================================================
// call() invokes a function defined in [functions], sharing the
// script's variable scope. return/return(value) exits the current
// function call — see ScriptRunner.runFunction() for how END and
// CHAIN still propagate all the way up through nested calls while
// RETURN only unwinds one level.
// ============================================================
public class FunctionActions {

    public static void register() {
        ActionRegistry.register(FunctionActions::handle);
    }

    private static ActionResult handle(String action, ActionContext ctx) throws InterruptedException {

        // --- varName=call(function, arg1, arg2) — capture the return value ---
        if (action.contains("=call(") && action.endsWith(")")) {
            int eqIdx = action.indexOf("=call(");
            String varName = action.substring(0, eqIdx).trim();
            String inner = action.substring(eqIdx + 6, action.length() - 1);

            if (varName.isEmpty()) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Variable name cannot be empty");
                TaskBlocksNotifier.error("Variable name cannot be empty");
                return ActionResult.normal();
            }

            return doCall(inner, ctx, varName);
        }

        // --- call(function, arg1, arg2) — no return value captured ---
        if (action.startsWith("call(") && action.endsWith(")")) {
            String inner = action.substring(5, action.length() - 1);
            return doCall(inner, ctx, null);
        }

        // --- return / return(value) ---
        if (action.equalsIgnoreCase("return")) {
            return ActionResult.functionReturn(null);
        }
        if (action.startsWith("return(") && action.endsWith(")")) {
            String value = action.substring(7, action.length() - 1).trim();
            if ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            return ActionResult.functionReturn(value);
        }

        return null;
    }

    private static ActionResult doCall(String inner, ActionContext ctx, String captureVar) throws InterruptedException {
        List<String> parts = ArgSplitter.split(inner);

        if (parts.isEmpty() || parts.get(0).isEmpty()) {
            TaskBlocks.LOGGER.error("[TaskBlocks] call() needs a function name");
            TaskBlocksNotifier.error("call() needs a function name");
            return ActionResult.normal();
        }

        String funcName = parts.get(0).trim();
        FunctionDef function = ScriptRunner.getFunction(funcName);

        if (function == null) {
            TaskBlocks.LOGGER.error("[TaskBlocks] call: no function named '" + funcName + "'");
            TaskBlocksNotifier.error("call: no function named '" + funcName + "'");
            return ActionResult.normal();
        }

        List<String> argValues = parts.subList(1, parts.size());
        if (argValues.size() != function.params.size()) {
            TaskBlocks.LOGGER.error("[TaskBlocks] call: '" + funcName + "' expects "
                + function.params.size() + " argument(s), got " + argValues.size());
            TaskBlocksNotifier.error("call: '" + funcName + "' expects "
                + function.params.size() + " argument(s), got " + argValues.size());
            return ActionResult.normal();
        }

        Map<String, String> variables = ctx.variables;

        // Save whatever the parameter names currently hold (if anything) so
        // they can be restored after the call — functions share the outer
        // scope, but a parameter name shouldn't permanently clobber a
        // same-named variable in the caller just by coincidence.
        Map<String, String> saved = new HashMap<>();
        Set<String> hadPrevious = new HashSet<>();
        for (int i = 0; i < function.params.size(); i++) {
            String paramName = function.params.get(i).trim();
            if (variables.containsKey(paramName)) {
                saved.put(paramName, variables.get(paramName));
                hadPrevious.add(paramName);
            }
            variables.put(paramName, stripQuotes(argValues.get(i).trim()));
        }

        ExecOutcome outcome;
        try {
            outcome = ScriptRunner.runFunction(function, variables);
        } finally {
            for (String paramName : function.params) {
                String trimmedName = paramName.trim();
                if (hadPrevious.contains(trimmedName)) {
                    variables.put(trimmedName, saved.get(trimmedName));
                } else {
                    variables.remove(trimmedName);
                }
            }
        }

        switch (outcome.type) {
            case ENDED:
                return ActionResult.end();
            case CHAINED:
                return ActionResult.chain(outcome.chainScript, outcome.chainVariables);
            case RETURNED:
            case NORMAL:
            default:
                if (captureVar != null) {
                    variables.put(captureVar, outcome.returnValue != null ? outcome.returnValue : "");
                }
                return ActionResult.normal();
        }
    }

    private static String stripQuotes(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2)
                || (value.startsWith("'") && value.endsWith("'") && value.length() >= 2)) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
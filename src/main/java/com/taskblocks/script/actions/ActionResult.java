package com.taskblocks.script.actions;

import java.util.Map;

public class ActionResult {

    public enum Type {
        NORMAL,   // advance to next line
        JUMP,     // jump to specific line
        END,      // stop script immediately
        CHAIN,    // stop this script, then start another one by name
        RETURN    // exit the current function call only
    }

    public final Type type;
    public final int targetLine;      // only used for JUMP
    public final String targetScript; // only used for CHAIN
    public final Map<String, String> chainVariables; // only used for CHAIN
    public final String returnValue;  // only used for RETURN

    private ActionResult(Type type, int targetLine, String targetScript,
                          Map<String, String> chainVariables, String returnValue) {
        this.type = type;
        this.targetLine = targetLine;
        this.targetScript = targetScript;
        this.chainVariables = chainVariables;
        this.returnValue = returnValue;
    }

    public static ActionResult normal() {
        return new ActionResult(Type.NORMAL, -1, null, null, null);
    }

    public static ActionResult jump(int line) {
        return new ActionResult(Type.JUMP, line, null, null, null);
    }

    public static ActionResult end() {
        return new ActionResult(Type.END, -1, null, null, null);
    }

    public static ActionResult chain(String scriptName, Map<String, String> variables) {
        return new ActionResult(Type.CHAIN, -1, scriptName, variables, null);
    }

    public static ActionResult functionReturn(String value) {
        return new ActionResult(Type.RETURN, -1, null, null, value);
    }
}
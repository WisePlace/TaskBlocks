package com.taskblocks.script.actions;

import java.util.HashMap;
import java.util.Map;

public class ActionContext {
    public final int currentLine;
    public final int[] loopCounters;
    public final Map<String, String> variables;

    public ActionContext(int currentLine, int[] loopCounters, Map<String, String> variables) {
        this.currentLine = currentLine;
        this.loopCounters = loopCounters;
        this.variables = variables;
    }
}
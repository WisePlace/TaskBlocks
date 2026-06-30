package com.taskblocks.script.actions;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ActionContext {
    public final int currentLine;
    public final Map<Integer, Integer> loopCounters;
    public final Map<String, String> variables;
    public final List<String> allLines;
    public final Set<Integer> dispatchedBranches;

    public ActionContext(int currentLine, Map<Integer, Integer> loopCounters,
                          Map<String, String> variables, List<String> allLines,
                          Set<Integer> dispatchedBranches) {
        this.currentLine        = currentLine;
        this.loopCounters       = loopCounters;
        this.variables          = variables;
        this.allLines           = allLines;
        this.dispatchedBranches = dispatchedBranches;
    }

    public int totalLines() {
        return allLines.size();
    }

    public String getLine(int index) {
        return allLines.get(index).trim();
    }
}
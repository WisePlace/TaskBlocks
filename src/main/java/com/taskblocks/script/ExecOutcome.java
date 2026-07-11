package com.taskblocks.script;

import java.util.Map;

// ============================================================
// How an instruction list (the top-level script, or a function body
// invoked by call()) finished running.
// NORMAL   — ran off the end of the list
// RETURNED — hit return/return(value); only unwinds to the caller of
//            runFunction(), doesn't affect anything further up
// ENDED    — hit 'end'; must propagate all the way up and stop the
//            whole script, regardless of how many functions deep
// CHAINED  — hit run_script(); same propagation as ENDED, then starts
//            the target script once everything has unwound
// ============================================================
public class ExecOutcome {

    public enum Type { NORMAL, RETURNED, ENDED, CHAINED }

    public final Type type;
    public final String returnValue;
    public final String chainScript;
    public final Map<String, String> chainVariables;

    private ExecOutcome(Type type, String returnValue, String chainScript, Map<String, String> chainVariables) {
        this.type = type;
        this.returnValue = returnValue;
        this.chainScript = chainScript;
        this.chainVariables = chainVariables;
    }

    public static ExecOutcome normal() {
        return new ExecOutcome(Type.NORMAL, null, null, null);
    }

    public static ExecOutcome returned(String value) {
        return new ExecOutcome(Type.RETURNED, value, null, null);
    }

    public static ExecOutcome ended() {
        return new ExecOutcome(Type.ENDED, null, null, null);
    }

    public static ExecOutcome chained(String scriptName, Map<String, String> variables) {
        return new ExecOutcome(Type.CHAINED, null, scriptName, variables);
    }
}
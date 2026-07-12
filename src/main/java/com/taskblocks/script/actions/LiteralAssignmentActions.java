package com.taskblocks.script.actions;

// ============================================================
// Generic variable assignment fallback: varName=literalValue
//
// This MUST be the last handler in the registry chain. Every other
// action using "varName=something(...)" syntax — get(), random(),
// call(), input() — already had a chance to claim the line before
// this ever runs (ActionRegistry stops at the first handler that
// returns non-null), so this only catches plain literal assignments
// that don't match any of those, e.g.:
//   trips=0
//   dir=north
//   phase=starting
// ============================================================
public class LiteralAssignmentActions {

    public static void register() {
        ActionRegistry.register(LiteralAssignmentActions::handle);
    }

    private static ActionResult handle(String action, ActionContext ctx) {
        int eqIdx = action.indexOf('=');
        if (eqIdx <= 0) return null;

        String varName = action.substring(0, eqIdx).trim();
        String value = action.substring(eqIdx + 1).trim();

        // Only a plain identifier on the left counts as an assignment —
        // this keeps if(x==5)-style comparisons and anything else with
        // an '=' elsewhere in the line from being misread as one.
        if (!varName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) return null;

        ctx.variables.put(varName, value);
        return ActionResult.normal();
    }
}
package com.taskblocks.script.actions;

import com.taskblocks.TaskBlocks;
import com.taskblocks.client.TaskBlocksNotifier;

import java.util.ArrayList;
import java.util.List;

public class ActionRegistry {

    // Each entry is a handler that inspects the action string itself
    // and returns null if it doesn't handle it.
    private static final List<ActionHandler> handlers = new ArrayList<>();

    static {
        MouseActions.register();
        KeyboardActions.register();
        ChatActions.register();
        FlowActions.register();
        MovementActions.register();
        WorldActions.register();
        VariableActions.register();
        ScriptActions.register();
        FunctionActions.register();
        DisplayActions.register();
    }

    public static void register(ActionHandler handler) {
        handlers.add(handler);
    }

    public static ActionResult execute(String action, ActionContext ctx)
            throws InterruptedException {
        for (ActionHandler handler : handlers) {
            ActionResult result = handler.execute(action, ctx);
            if (result != null) return result;
        }
        TaskBlocks.LOGGER.warn("[TaskBlocks] Unknown action: " + action);
        TaskBlocksNotifier.warn("Unknown action: §f" + action);
        return ActionResult.normal();
    }
}
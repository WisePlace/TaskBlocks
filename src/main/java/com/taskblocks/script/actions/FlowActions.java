package com.taskblocks.script.actions;

import com.taskblocks.TaskBlocks;
import com.taskblocks.client.TaskBlocksNotifier;

public class FlowActions {

    public static void register() {
        ActionRegistry.register(FlowActions::handle);
    }

    private static ActionResult handle(String action, ActionContext ctx)
            throws InterruptedException {

        // --- pause(amount) or pause(amount, unit) ---
        if (action.startsWith("pause(") && action.endsWith(")")) {
            String inner = action.substring(6, action.length() - 1).trim();
            String[] parts = inner.split(",");
            try {
                long amount = Long.parseLong(parts[0].trim());
                long ms = amount;
                if (parts.length >= 2) {
                    String unit = parts[1].trim().toLowerCase();
                    ms = switch (unit) {
                        case "ms", "milliseconds", "millisecond" -> amount;
                        case "s",  "seconds",      "second"      -> amount * 1000L;
                        case "m",  "minutes",      "minute"      -> amount * 60_000L;
                        case "h",  "hours",        "hour"        -> amount * 3_600_000L;
                        default -> {
                            TaskBlocks.LOGGER.warn("[TaskBlocks] Unknown time unit: "
                                + unit + ", defaulting to ms");
                            TaskBlocksNotifier.warn("Unknown time unit: §f" + unit + ", defaulting to ms");
                            yield amount;
                        }
                    };
                }
                Thread.sleep(ms);
            } catch (NumberFormatException e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Invalid pause value: " + inner);
                TaskBlocksNotifier.error("Invalid pause value: §f" + inner);
            }
            return ActionResult.normal();
        }

        if (action.startsWith("loop(") && action.endsWith(")")) {
            String inner = action.substring(5, action.length() - 1).trim();
            String[] parts = inner.split(",");
            if (parts.length == 2) {
                try {
                    int count      = Integer.parseInt(parts[0].trim());
                    int targetLine = Integer.parseInt(parts[1].trim()) - 1;

                    // Initialize counter on first visit
                    if (!ctx.loopCounters.containsKey(ctx.currentLine)) {
                        ctx.loopCounters.put(ctx.currentLine, count);
                    }

                    int remaining = ctx.loopCounters.get(ctx.currentLine);

                    if (remaining > 1) {
                        ctx.loopCounters.put(ctx.currentLine, remaining - 1);

                        // Reset all inner loop counters so they re-initialize fresh
                        ctx.loopCounters.entrySet().removeIf(e ->
                            e.getKey() >= targetLine && e.getKey() < ctx.currentLine);

                        return ActionResult.jump(targetLine);
                    } else {
                        // Loop done — remove so it re-initializes if reached again
                        ctx.loopCounters.remove(ctx.currentLine);
                        return ActionResult.normal();
                    }
                } catch (NumberFormatException e) {
                    TaskBlocks.LOGGER.error("[TaskBlocks] Invalid loop arguments: " + inner);
                    TaskBlocksNotifier.error("Invalid loop arguments: §f" + inner);
                }
            } else {
                TaskBlocks.LOGGER.error("[TaskBlocks] loop() needs 2 args: loop(count, line)");
                TaskBlocksNotifier.error("loop() needs 2 args: loop(count, line)");
            }
            return ActionResult.normal();
        }

        // --- goto(targetLine) ---
        if (action.startsWith("goto(") && action.endsWith(")")) {
            String inner = action.substring(5, action.length() - 1).trim();
            try {
                int targetLine = Integer.parseInt(inner.trim()) - 1;
                return ActionResult.jump(targetLine);
            } catch (NumberFormatException e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Invalid goto argument: " + inner);
                TaskBlocksNotifier.error("Invalid goto argument: §f" + inner);
            }
            return ActionResult.normal();
        }

        // --- end ---
        if (action.equalsIgnoreCase("end")) {
            return ActionResult.end();
        }

        return null;
    }
}
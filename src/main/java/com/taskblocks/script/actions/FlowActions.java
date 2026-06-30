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

        // --- loop(count, targetLine) ---
        if (action.startsWith("loop(") && action.endsWith(")")) {
            String inner = action.substring(5, action.length() - 1).trim();
            String[] parts = inner.split(",");
            if (parts.length == 2) {
                try {
                    int count      = Integer.parseInt(parts[0].trim());
                    int targetLine = Integer.parseInt(parts[1].trim()) - 1;

                    if (!ctx.loopCounters.containsKey(ctx.currentLine)) {
                        ctx.loopCounters.put(ctx.currentLine, count);
                    }

                    int remaining = ctx.loopCounters.get(ctx.currentLine);

                    if (remaining > 1) {
                        ctx.loopCounters.put(ctx.currentLine, remaining - 1);

                        ctx.loopCounters.entrySet().removeIf(e ->
                            e.getKey() >= targetLine && e.getKey() < ctx.currentLine);

                        return ActionResult.jump(targetLine);
                    } else {
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

                if (targetLine < ctx.currentLine) {
                    ctx.loopCounters.entrySet().removeIf(e ->
                        e.getKey() >= targetLine && e.getKey() < ctx.currentLine);
                }

                return ActionResult.jump(targetLine);
            } catch (NumberFormatException e) {
                TaskBlocks.LOGGER.error("[TaskBlocks] Invalid goto argument: " + inner);
                TaskBlocksNotifier.error("Invalid goto argument: §f" + inner);
            }
            return ActionResult.normal();
        }

        // ============================================================
        // if(condition) / else if(condition) / else / endif
        //
        // Model: each branch (if / else-if / else) is only EVALUATED or
        // ENTERED when execution reaches it via a "dispatch jump" from a
        // prior false condition in the same chain. If a branch line is
        // reached by normal straight-line execution (cursor++ from the
        // previous line, meaning a prior branch's body just finished),
        // that always means a chain member above already ran — so we
        // skip straight to endif.
        //
        // ctx.dispatchedBranches marks a line as "reached via dispatch
        // jump", consumed (removed) the moment it's checked.
        // ============================================================

        // --- if(condition) ---
        if (action.startsWith("if(") && action.endsWith(")")) {
            String condition = action.substring(3, action.length() - 1).trim();
            boolean result = evaluateCondition(condition, ctx.variables);
            TaskBlocks.LOGGER.info("[TaskBlocks][DEBUG] if(" + condition + ") -> " + result
                + " | variables=" + ctx.variables);

            if (result) {
                return ActionResult.normal(); // enter the block
            } else {
                int target = findNextBranchOrEndif(ctx, ctx.currentLine);
                TaskBlocks.LOGGER.info("[TaskBlocks][DEBUG] if false, jumping to line " + target);
                if (target == -1) {
                    TaskBlocks.LOGGER.error("[TaskBlocks] if() missing matching endif");
                    TaskBlocksNotifier.error("if() missing matching endif");
                    return ActionResult.end();
                }
                ctx.dispatchedBranches.add(target);
                return ActionResult.jump(target);
            }
        }

        // --- else if(condition) ---
        if (action.toLowerCase().startsWith("else if(") && action.endsWith(")")) {
            boolean wasDispatched = ctx.dispatchedBranches.remove(ctx.currentLine);
            TaskBlocks.LOGGER.info("[TaskBlocks][DEBUG] else-if at line " + ctx.currentLine
                + " wasDispatched=" + wasDispatched);

            if (!wasDispatched) {
                // Fell through from an executed branch above — skip the rest of the chain
                int endifLine = findMatchingEndif(ctx, ctx.currentLine);
                if (endifLine == -1) {
                    TaskBlocks.LOGGER.error("[TaskBlocks] else if missing matching endif");
                    TaskBlocksNotifier.error("else if missing matching endif");
                    return ActionResult.end();
                }
                return ActionResult.jump(endifLine);
            }

            String condition = action.substring(8, action.length() - 1).trim();
            boolean result = evaluateCondition(condition, ctx.variables);
            TaskBlocks.LOGGER.info("[TaskBlocks][DEBUG] else if(" + condition + ") -> " + result
                + " | variables=" + ctx.variables);

            if (result) {
                return ActionResult.normal();
            } else {
                int target = findNextBranchOrEndif(ctx, ctx.currentLine);
                TaskBlocks.LOGGER.info("[TaskBlocks][DEBUG] else-if false, jumping to line " + target);
                if (target == -1) {
                    TaskBlocks.LOGGER.error("[TaskBlocks] else if() missing matching endif");
                    TaskBlocksNotifier.error("else if() missing matching endif");
                    return ActionResult.end();
                }
                ctx.dispatchedBranches.add(target);
                return ActionResult.jump(target);
            }
        }

        // --- else ---
        if (action.equalsIgnoreCase("else")) {
            boolean wasDispatched = ctx.dispatchedBranches.remove(ctx.currentLine);

            if (!wasDispatched) {
                // Fell through from an executed branch above — skip to endif
                int endifLine = findMatchingEndif(ctx, ctx.currentLine);
                if (endifLine == -1) {
                    TaskBlocks.LOGGER.error("[TaskBlocks] else missing matching endif");
                    TaskBlocksNotifier.error("else missing matching endif");
                    return ActionResult.end();
                }
                return ActionResult.jump(endifLine);
            }

            // Dispatched here because nothing above matched — enter the block
            return ActionResult.normal();
        }

        // --- endif (no-op marker) ---
        if (action.equalsIgnoreCase("endif")) {
            boolean hasMatchingIf = false;
            int depth = 0;
            for (int line = ctx.currentLine - 1; line >= 0; line--) {
                String prev = ctx.getLine(line);
                if (prev.equalsIgnoreCase("endif")) {
                    depth++;
                } else if (isIfStart(prev)) {
                    if (depth == 0) {
                        hasMatchingIf = true;
                        break;
                    }
                    depth--;
                }
            }
            if (!hasMatchingIf) {
                TaskBlocks.LOGGER.warn("[TaskBlocks] Stray 'endif' with no matching if()");
                TaskBlocksNotifier.warn("Stray 'endif' with no matching if()");
            }
            return ActionResult.normal();
        }

        // --- end ---
        if (action.equalsIgnoreCase("end")) {
            return ActionResult.end();
        }

        return null;
    }

    // ============================================================
    // Helpers for if/else-if/else/endif navigation
    // ============================================================

    private static boolean isIfStart(String line) {
        return (line.startsWith("if(") && line.endsWith(")"));
    }

    private static boolean isElseIf(String line) {
        return line.toLowerCase().startsWith("else if(") && line.endsWith(")");
    }

    /**
     * From a false if/else-if at ctx.currentLine, find the next else-if,
     * else, or endif at the SAME nesting depth (depth 1 relative to here).
     * Returns the line index to jump to, or -1 if no endif found.
     */
    private static int findNextBranchOrEndif(ActionContext ctx, int fromLine) {
        int depth = 1;
        int line = fromLine + 1;
        while (line < ctx.totalLines()) {
            String next = ctx.getLine(line);
            if (isIfStart(next)) {
                depth++;
            } else if (next.equalsIgnoreCase("endif")) {
                depth--;
                if (depth == 0) return line;
            } else if (depth == 1 && (isElseIf(next) || next.equalsIgnoreCase("else"))) {
                return line;
            }
            line++;
        }
        return -1;
    }

    /**
     * From an else/else-if at ctx.currentLine, find the matching endif,
     * skipping over any nested if blocks and any other else-if/else at
     * the same depth (since only one branch in a chain ever executes).
     */
    private static int findMatchingEndif(ActionContext ctx, int fromLine) {
        int depth = 1;
        int line = fromLine + 1;
        while (line < ctx.totalLines()) {
            String next = ctx.getLine(line);
            if (isIfStart(next)) {
                depth++;
            } else if (next.equalsIgnoreCase("endif")) {
                depth--;
                if (depth == 0) return line;
            }
            line++;
        }
        return -1;
    }

    // ============================================================
    // Condition evaluation for if() / else if()
    // Supports: &&, || (left-to-right, no parentheses grouping)
    // Comparison operators: >=, <=, ==, !=, >, <
    // Numeric comparison when both sides parse as numbers, otherwise
    // falls back to string equality/inequality.
    // ============================================================
    private static boolean evaluateCondition(String condition, java.util.Map<String, String> variables) {
        java.util.List<String> clauses = new java.util.ArrayList<>();
        java.util.List<String> connectors = new java.util.ArrayList<>();

        String remaining = condition;
        while (true) {
            int andIdx = remaining.indexOf("&&");
            int orIdx  = remaining.indexOf("||");
            int splitIdx;
            String connector;

            if (andIdx == -1 && orIdx == -1) {
                clauses.add(remaining.trim());
                break;
            } else if (andIdx != -1 && (orIdx == -1 || andIdx < orIdx)) {
                splitIdx = andIdx;
                connector = "&&";
            } else {
                splitIdx = orIdx;
                connector = "||";
            }

            clauses.add(remaining.substring(0, splitIdx).trim());
            connectors.add(connector);
            remaining = remaining.substring(splitIdx + 2);
        }

        boolean result = evaluateSingleCondition(clauses.get(0), variables);
        for (int i = 0; i < connectors.size(); i++) {
            boolean next = evaluateSingleCondition(clauses.get(i + 1), variables);
            if (connectors.get(i).equals("&&")) {
                result = result && next;
            } else {
                result = result || next;
            }
        }
        return result;
    }

    private static boolean evaluateSingleCondition(String condition, java.util.Map<String, String> variables) {
        String[] ops = {">=", "<=", "==", "!=", ">", "<"};

        for (String op : ops) {
            int idx = condition.indexOf(op);
            if (idx == -1) continue;

            String left  = condition.substring(0, idx).trim();
            String right = condition.substring(idx + op.length()).trim();

            String leftVal  = variables.getOrDefault(left, left);
            String rightVal = variables.getOrDefault(right, right);

            try {
                double l = Double.parseDouble(leftVal);
                double r = Double.parseDouble(rightVal);
                return switch (op) {
                    case ">=" -> l >= r;
                    case "<=" -> l <= r;
                    case "==" -> l == r;
                    case "!=" -> l != r;
                    case ">"  -> l > r;
                    case "<"  -> l < r;
                    default   -> false;
                };
            } catch (NumberFormatException e) {
                return switch (op) {
                    case "==" -> leftVal.equals(rightVal);
                    case "!=" -> !leftVal.equals(rightVal);
                    default -> {
                        TaskBlocks.LOGGER.warn("[TaskBlocks] Cannot compare non-numeric values with "
                            + op + ": " + leftVal + " " + op + " " + rightVal);
                        TaskBlocksNotifier.warn("Cannot compare text with §f" + op);
                        yield false;
                    }
                };
            }
        }

        TaskBlocks.LOGGER.error("[TaskBlocks] Invalid condition: " + condition);
        TaskBlocksNotifier.error("Invalid condition: §f" + condition);
        return false;
    }
}
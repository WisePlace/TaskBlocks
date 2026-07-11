package com.taskblocks.script.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.taskblocks.TaskBlocks;
import com.taskblocks.client.TaskBlocksNotifier;

// ============================================================
// Shared condition evaluator for if()/else if() and listen().
// Supports &&, || (left-to-right, no parentheses grouping).
// Comparison operators: >=, <=, ==, !=, =, >, <
// Numeric comparison when both sides parse as numbers, otherwise
// falls back to string equality/inequality.
// ============================================================
public class ConditionEvaluator {

    public static boolean evaluate(String condition, Map<String, String> variables) {
        List<String> clauses = new ArrayList<>();
        List<String> connectors = new ArrayList<>();

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

        boolean result = evaluateSingle(clauses.get(0), variables);
        for (int i = 0; i < connectors.size(); i++) {
            boolean next = evaluateSingle(clauses.get(i + 1), variables);
            result = connectors.get(i).equals("&&") ? (result && next) : (result || next);
        }
        return result;
    }

    private static boolean evaluateSingle(String condition, Map<String, String> variables) {
        // "=" must be checked after "==" and "!=" so those match first
        String[] ops = {">=", "<=", "==", "!=", "=", ">", "<"};

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
                    case ">="       -> l >= r;
                    case "<="       -> l <= r;
                    case "==", "="  -> l == r;
                    case "!="       -> l != r;
                    case ">"        -> l > r;
                    case "<"        -> l < r;
                    default         -> false;
                };
            } catch (NumberFormatException e) {
                return switch (op) {
                    case "==", "=" -> leftVal.equals(rightVal);
                    case "!="      -> !leftVal.equals(rightVal);
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
package com.taskblocks.script.actions;

import com.taskblocks.TaskBlocks;
import com.taskblocks.client.TaskBlocksNotifier;

// ============================================================
// varName=calc(expression)
//
// Basic arithmetic: + - * / % with standard precedence and
// parentheses. Operands are typically {var} references, already
// resolved to their values by interpolation before this runs.
//
// Examples:
//   row=calc({row} + 1)
//   half=calc({size} / 2)
//   total=calc(({a} + {b}) * 2)
// ============================================================
public class MathActions {

    public static void register() {
        ActionRegistry.register(MathActions::handle);
    }

    private static ActionResult handle(String action, ActionContext ctx) {
        if (!action.contains("=calc(") || !action.endsWith(")")) return null;

        int eqIdx = action.indexOf("=calc(");
        String varName = action.substring(0, eqIdx).trim();
        String expr = action.substring(eqIdx + 6, action.length() - 1).trim();

        if (varName.isEmpty()) {
            TaskBlocks.LOGGER.error("[TaskBlocks] Variable name cannot be empty");
            TaskBlocksNotifier.error("Variable name cannot be empty");
            return ActionResult.normal();
        }

        try {
            double result = new Evaluator(expr).parse();
            ctx.variables.put(varName, formatNumber(result));
        } catch (Exception e) {
            TaskBlocks.LOGGER.error("[TaskBlocks] Invalid calc() expression: " + expr + " (" + e.getMessage() + ")");
            TaskBlocksNotifier.error("Invalid calc() expression: §f" + expr);
        }

        return ActionResult.normal();
    }

    // Whole numbers print without a trailing ".0", so counters read
    // naturally (e.g. "5" instead of "5.0").
    private static String formatNumber(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    // Small recursive-descent parser: + - * / % and parentheses,
    // standard precedence (* / % before + -).
    private static class Evaluator {
        private final String expr;
        private int pos = 0;

        Evaluator(String expr) {
            this.expr = expr;
        }

        double parse() {
            double result = parseExpression();
            skipWhitespace();
            if (pos < expr.length()) {
                throw new RuntimeException("unexpected character at position " + pos);
            }
            return result;
        }

        private double parseExpression() {
            double value = parseTerm();
            while (true) {
                skipWhitespace();
                char c = peekChar();
                if (c == '+') { pos++; value += parseTerm(); }
                else if (c == '-') { pos++; value -= parseTerm(); }
                else break;
            }
            return value;
        }

        private double parseTerm() {
            double value = parseFactor();
            while (true) {
                skipWhitespace();
                char c = peekChar();
                if (c == '*') { pos++; value *= parseFactor(); }
                else if (c == '/') {
                    pos++;
                    double divisor = parseFactor();
                    if (divisor == 0) throw new RuntimeException("division by zero");
                    value /= divisor;
                } else if (c == '%') {
                    pos++;
                    double divisor = parseFactor();
                    if (divisor == 0) throw new RuntimeException("division by zero");
                    value %= divisor;
                } else break;
            }
            return value;
        }

        private double parseFactor() {
            skipWhitespace();
            boolean negative = false;
            char sign = peekChar();
            if (sign == '-') { negative = true; pos++; skipWhitespace(); }
            else if (sign == '+') { pos++; skipWhitespace(); }

            double value;
            if (peekChar() == '(') {
                pos++;
                value = parseExpression();
                skipWhitespace();
                if (peekChar() != ')') throw new RuntimeException("missing closing parenthesis");
                pos++;
            } else {
                int start = pos;
                while (pos < expr.length()
                        && (Character.isDigit(expr.charAt(pos)) || expr.charAt(pos) == '.')) {
                    pos++;
                }
                if (start == pos) throw new RuntimeException("expected a number at position " + pos);
                value = Double.parseDouble(expr.substring(start, pos));
            }
            return negative ? -value : value;
        }

        private char peekChar() {
            skipWhitespace();
            return pos < expr.length() ? expr.charAt(pos) : '\0';
        }

        private void skipWhitespace() {
            while (pos < expr.length() && Character.isWhitespace(expr.charAt(pos))) pos++;
        }
    }
}
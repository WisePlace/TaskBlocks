package com.taskblocks.script.actions;

import java.util.ArrayList;
import java.util.List;

// ============================================================
// Splits a comma-separated argument list while respecting nested
// parentheses, so an argument that's itself a call — e.g. say(msg,
// local) as an argument inside listen(id, condition, action) — isn't
// split on its own internal comma.
// ============================================================
public class ArgSplitter {
    public static List<String> split(String input) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                parts.add(input.substring(start, i).trim());
                start = i + 1;
            }
        }
        parts.add(input.substring(start).trim());
        return parts;
    }
}
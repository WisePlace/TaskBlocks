package com.taskblocks.script.actions;

public class ActionResult {

    public enum Type {
        NORMAL,   // advance to next line
        JUMP,     // jump to specific line
        END       // stop script immediately
    }

    public final Type type;
    public final int targetLine; // only used for JUMP

    private ActionResult(Type type, int targetLine) {
        this.type = type;
        this.targetLine = targetLine;
    }

    public static ActionResult normal() {
        return new ActionResult(Type.NORMAL, -1);
    }

    public static ActionResult jump(int line) {
        return new ActionResult(Type.JUMP, line);
    }

    public static ActionResult end() {
        return new ActionResult(Type.END, -1);
    }
}
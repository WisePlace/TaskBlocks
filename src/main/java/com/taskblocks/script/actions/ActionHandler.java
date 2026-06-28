package com.taskblocks.script.actions;

@FunctionalInterface
public interface ActionHandler {
    ActionResult execute(String action, ActionContext ctx) throws InterruptedException;
}
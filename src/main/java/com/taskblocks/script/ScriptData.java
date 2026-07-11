package com.taskblocks.script;

import java.util.List;
import java.util.Map;

public class ScriptData {
    public String name;
    public String fileName;
    public String version;
    public String author;
    public String startStopKey;
    public boolean enabled;
    public boolean debug;
    public List<String> actions;
    public Map<String, FunctionDef> functions;

    public ScriptData(String name, String fileName, String version, String author,
                      String startStopKey, boolean enabled, boolean debug,
                      List<String> actions, Map<String, FunctionDef> functions) {
        this.name = name;
        this.fileName = fileName;
        this.version = version;
        this.author = author;
        this.startStopKey = startStopKey;
        this.enabled = enabled;
        this.debug = debug;
        this.actions = actions;
        this.functions = functions;
    }
}
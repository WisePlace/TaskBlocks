package com.taskblocks.script;

import java.util.List;

public class ScriptData {
    public String name;
    public String fileName;
    public String version;
    public String author;
    public String startStopKey;
    public String pauseKey;
    public boolean enabled;
    public boolean debug;
    public List<String> actions;

    public ScriptData(String name, String fileName, String version, String author,
                      String startStopKey, String pauseKey, boolean enabled, boolean debug,List<String> actions) {
        this.name = name;
        this.fileName = fileName;
        this.version = version;
        this.author = author;
        this.startStopKey = startStopKey;
        this.pauseKey = pauseKey;
        this.enabled = enabled;
        this.debug = debug;
        this.actions = actions;
    }
}
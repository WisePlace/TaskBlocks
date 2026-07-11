package com.taskblocks.script;

import java.util.List;

public class FunctionDef {
    public final String name;
    public final List<String> params;
    public final List<String> body;

    public FunctionDef(String name, List<String> params, List<String> body) {
        this.name = name;
        this.params = params;
        this.body = body;
    }
}
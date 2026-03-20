package com.hardrock.griefercheck.logging;

import com.google.gson.JsonObject;

public class GCEvent {

    public final JsonObject json = new JsonObject();

    public GCEvent(String action) {
        json.addProperty("ts", System.currentTimeMillis());
        json.addProperty("action", action);
    }

    public GCEvent add(String k, String v) {
        json.addProperty(k, v);
        return this;
    }

    public GCEvent add(String k, Number v) {
        json.addProperty(k, v);
        return this;
    }

    public GCEvent add(String k, boolean v) {
        json.addProperty(k, v);
        return this;
    }

    public JsonObject obj() {
        return json;
    }
}

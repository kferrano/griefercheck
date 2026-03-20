package com.hardrock.griefercheck.query;

import com.google.gson.JsonObject;

import java.util.*;

public class GCPagination {

    public static class PageState {
        public final List<JsonObject> results;
        public final int pageSize;
        public final boolean inspectOnly; // NEW

        public PageState(List<JsonObject> results, int pageSize) {
            this(results, pageSize, false);
        }

        public PageState(List<JsonObject> results, int pageSize, boolean inspectOnly) {
            this.results = results;
            this.pageSize = pageSize;
            this.inspectOnly = inspectOnly;
        }

        public int pageCount() {
            return (results.size() + pageSize - 1) / pageSize;
        }
    }


    private static final Map<UUID, PageState> STATES = new HashMap<>();

    public static void set(UUID admin, PageState state) {
        STATES.put(admin, state);
    }

    public static PageState get(UUID admin) {
        return STATES.get(admin);
    }
}

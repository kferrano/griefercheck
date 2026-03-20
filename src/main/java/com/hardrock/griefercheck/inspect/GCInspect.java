package com.hardrock.griefercheck.inspect;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GCInspect {

    private static final Set<UUID> ENABLED = ConcurrentHashMap.newKeySet();

    private GCInspect() {}

    public static boolean isEnabled(UUID id) {
        return ENABLED.contains(id);
    }

    public static void setEnabled(UUID id, boolean enabled) {
        if (enabled) ENABLED.add(id);
        else ENABLED.remove(id);
    }

    public static boolean toggle(UUID id) {
        if (ENABLED.contains(id)) {
            ENABLED.remove(id);
            return false;
        }
        ENABLED.add(id);
        return true;
    }
}

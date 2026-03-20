package com.hardrock.griefercheck.update;

import com.hardrock.griefercheck.Griefercheck;
import com.hardrock.griefercheck.config.GCConfig;
import net.minecraftforge.fml.ModList;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class GCUpdateChecker {

    private static volatile String latestVersion = null;
    private static volatile boolean updateAvailable = false;
    private static volatile boolean checkedOnce = false;

    private static final String UPDATE_CHECK_URL = "https://raw.githubusercontent.com/kferrano/Curseforge_Updates/refs/heads/main/griefercheck.txt";
    public static final String CURSEFORGE_URL = "https://www.curseforge.com/minecraft/mc-mods/griefercheck";


    private GCUpdateChecker() {}

    public static String getCurrentVersion() {
        try {
            return ModList.get().getModContainerById(Griefercheck.MODID)
                    .map(c -> c.getModInfo().getVersion().toString())
                    .orElse("unknown");
        } catch (Throwable t) {
            return "unknown";
        }
    }

    public static boolean hasCheckedOnce() {
        return checkedOnce;
    }

    public static boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public static String getLatestVersion() {
        return latestVersion;
    }

    public static boolean shouldNotify() {
        return updateAvailable;
    }

    public static void checkAsync() {
        if (!GCConfig.ENABLE_UPDATE_CHECK.get()) return;

        final int timeout = GCConfig.UPDATE_CHECK_TIMEOUT_MS.get();

        CompletableFuture.runAsync(() -> {
            try {

                String remote = fetchPlainText(UPDATE_CHECK_URL, timeout);checkedOnce = true;
                String currentRaw = getCurrentVersion();
                String current = normalizeCurrentVersion(currentRaw);
                String remoteNorm = normalizeRemoteVersion(remote);

                System.out.println("[GC][Update] current=" + remoteNorm);
                System.out.println("[GC][Update] fetched='" + remote + "' from " + UPDATE_CHECK_URL);

                checkedOnce = true;

                if (remote == null || remote.isBlank()) return;

                remote = remote.trim();
                latestVersion = remote;

                updateAvailable = isNewer(remoteNorm, current);

            } catch (Throwable ignored) {
                checkedOnce = true;
            }
        });
    }

    public static String normalizeCurrentVersion(String v) {
        if (v == null) return "unknown";
        v = v.trim();
        // Example: "1.18.2-0.5.0" -> "0.5.0"
        int idx = v.lastIndexOf('-');
        if (idx >= 0 && idx + 1 < v.length()) {
            return v.substring(idx + 1).trim();
        }
        return v;
    }

    public static String normalizeRemoteVersion(String v) {
        if (v == null) return null;
        return v.trim();
    }


    // Simple, robust comparison: splits by non-digits and compares numeric parts (e.g. 1.2.10 > 1.2.9)
    private static boolean isNewer(String remote, String current) {
        if (remote == null || current == null) return false;
        if (remote.equalsIgnoreCase(current)) return false;
        if ("unknown".equalsIgnoreCase(current)) return false;

        int[] r = parseVersion(remote);
        int[] c = parseVersion(current);

        int n = Math.max(r.length, c.length);
        for (int i = 0; i < n; i++) {
            int rv = i < r.length ? r[i] : 0;
            int cv = i < c.length ? c[i] : 0;
            if (rv > cv) return true;
            if (rv < cv) return false;
        }
        // same numeric parts but different suffix -> treat as not newer to avoid false positives
        return false;
    }

    private static int[] parseVersion(String v) {
        try {
            String[] parts = v.split("[^0-9]+");
            return java.util.Arrays.stream(parts)
                    .filter(s -> !s.isBlank())
                    .limit(6)
                    .mapToInt(s -> {
                        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
                    })
                    .toArray();
        } catch (Throwable t) {
            return new int[]{0};
        }
    }

    private static String fetchPlainText(String urlStr, int timeoutMs) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setRequestMethod("GET");
            conn.setUseCaches(false);
            conn.setRequestProperty("User-Agent", "Griefercheck-UpdateChecker");

            int code = conn.getResponseCode();
            System.out.println("[GC][Update] HTTP " + code + " " + urlStr);
            if (code < 200 || code >= 300) return null;

            if (code < 200 || code >= 300) return null;

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                // First line only
                return br.readLine();
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}

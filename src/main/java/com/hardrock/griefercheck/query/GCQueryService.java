package com.hardrock.griefercheck.query;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hardrock.griefercheck.config.GCConfig;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GCQueryService {

    private static final Gson GSON = new Gson();
    private static final Pattern FILE_PATTERN = Pattern.compile("^events-(\\d{6})\\.jsonl$");

    public static class Query {
        public final String dim;
        public final Integer xMin, xMax, yMin, yMax, zMin, zMax;
        public final int maxResults;

        public Query(String dim,
                     Integer xMin, Integer xMax,
                     Integer yMin, Integer yMax,
                     Integer zMin, Integer zMax,
                     int maxResults) {
            this.dim = dim;
            this.xMin = xMin; this.xMax = xMax;
            this.yMin = yMin; this.yMax = yMax;
            this.zMin = zMin; this.zMax = zMax;
            this.maxResults = maxResults;
        }
    }

    public static List<JsonObject> queryEvents(File dir, Query q) {
        if (!dir.exists() || !dir.isDirectory()) return List.of();

        List<File> files = listEventFilesNewestFirst(dir, GCConfig.MAX_FILES_TO_SCAN.get());
        int linesPerFile = GCConfig.SCAN_LINES_PER_FILE.get();

        List<JsonObject> out = new ArrayList<>();

        for (File f : files) {
            List<String> lines = tailLines(f, linesPerFile);

            // iterate newest-first inside file
            for (int i = lines.size() - 1; i >= 0; i--) {
                String line = lines.get(i);

                JsonObject o;
                try {
                    o = GSON.fromJson(line, JsonObject.class);
                    if (o == null) continue;
                } catch (Exception ex) {
                    continue;
                }

                if (!matchDim(o, q.dim)) continue;
                if (!matchPos(o, q.xMin, q.xMax, q.yMin, q.yMax, q.zMin, q.zMax)) continue;

                out.add(o);
                if (out.size() >= q.maxResults) {
                    out.sort((a, b) -> Long.compare(getLong(b, "ts"), getLong(a, "ts")));
                    return out;
                }
            }
        }

        out.sort((a, b) -> Long.compare(getLong(b, "ts"), getLong(a, "ts")));
        return out;
    }

    private static List<File> listEventFilesNewestFirst(File dir, int maxFiles) {
        File[] files = dir.listFiles();
        if (files == null) return List.of();

        List<File> eventFiles = new ArrayList<>();
        for (File f : files) {
            Matcher m = FILE_PATTERN.matcher(f.getName());
            if (m.matches()) eventFiles.add(f);
        }

        eventFiles.sort((a, b) -> Integer.compare(extractIndex(b.getName()), extractIndex(a.getName())));
        if (eventFiles.size() > maxFiles) {
            return eventFiles.subList(0, maxFiles);
        }
        return eventFiles;
    }

    private static int extractIndex(String name) {
        Matcher m = FILE_PATTERN.matcher(name);
        if (!m.matches()) return 0;
        try { return Integer.parseInt(m.group(1)); } catch (Exception e) { return 0; }
    }

    private static boolean matchDim(JsonObject o, String dim) {
        if (dim == null) return true;
        if (!o.has("dim")) return false;
        return dim.equals(o.get("dim").getAsString());
    }

    private static boolean matchPos(JsonObject o,
                                    Integer xMin, Integer xMax,
                                    Integer yMin, Integer yMax,
                                    Integer zMin, Integer zMax) {
        if (xMin == null && xMax == null && yMin == null && yMax == null && zMin == null && zMax == null) return true;
        if (!o.has("x") || !o.has("y") || !o.has("z")) return false;

        int x = o.get("x").getAsInt();
        int y = o.get("y").getAsInt();
        int z = o.get("z").getAsInt();

        if (xMin != null && x < xMin) return false;
        if (xMax != null && x > xMax) return false;
        if (yMin != null && y < yMin) return false;
        if (yMax != null && y > yMax) return false;
        if (zMin != null && z < zMin) return false;
        if (zMax != null && z > zMax) return false;

        return true;
    }

    private static long getLong(JsonObject o, String k) {
        try {
            return o.has(k) ? o.get(k).getAsLong() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private static List<String> tailLines(File file, int maxLines) {
        Deque<String> deque = new ArrayDeque<>(maxLines);
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long fileLen = raf.length();
            long pos = fileLen - 1;
            int lines = 0;
            StringBuilder sb = new StringBuilder();

            while (pos >= 0 && lines < maxLines) {
                raf.seek(pos);
                int b = raf.read();
                if (b == '\n') {
                    if (sb.length() > 0) {
                        deque.addFirst(sb.reverse().toString());
                        sb.setLength(0);
                        lines++;
                    }
                } else if (b != '\r') {
                    sb.append((char) b);
                }
                pos--;
            }
            if (sb.length() > 0 && lines < maxLines) {
                deque.addFirst(sb.reverse().toString());
            }
        } catch (Exception ignored) {}

        return new ArrayList<>(deque);
    }
}

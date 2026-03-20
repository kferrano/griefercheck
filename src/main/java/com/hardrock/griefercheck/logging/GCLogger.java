package com.hardrock.griefercheck.logging;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hardrock.griefercheck.config.GCConfig;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class GCLogger {

    private static final BlockingQueue<String> QUEUE = new LinkedBlockingQueue<>();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private static final File DIR = new File("griefercheck");
    private static final Pattern FILE_PATTERN = Pattern.compile("^events-(\\d{6})\\.jsonl$");

    private static BufferedWriter out;
    private static int currentIndex;
    private static int currentLineCount;

    public static void init() {
        if (!DIR.exists()) DIR.mkdirs();

        // Determine last file index
        currentIndex = findLastIndex();
        if (currentIndex <= 0) currentIndex = 1;

        // Open last file and count lines (only for the last file)
        File f = fileForIndex(currentIndex);
        currentLineCount = countLines(f);

        try {
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Failed to open Griefercheck log file: " + f.getAbsolutePath(), e);
        }

        Thread t = new Thread(() -> {
            while (true) {
                try {
                    String line = QUEUE.take();
                    writeLine(line);
                } catch (Throwable t1) {
                    t1.printStackTrace();
                }
            }
        }, "Griefercheck-Logger");
        t.setDaemon(true);
        t.start();
    }

    public static void log(GCEvent event) {
        QUEUE.offer(GSON.toJson(event.obj()));
    }

    private static synchronized void writeLine(String line) throws IOException {
        int maxLines = GCConfig.MAX_LINES_PER_FILE.get();

        if (currentLineCount >= maxLines) {
            rotate();
        }

        out.write(line);
        out.newLine();
        out.flush();
        currentLineCount++;
    }

    private static void rotate() throws IOException {
        out.flush();
        out.close();

        currentIndex++;
        currentLineCount = 0;

        File f = fileForIndex(currentIndex);
        out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8));
    }

    private static File fileForIndex(int idx) {
        return new File(DIR, String.format("events-%06d.jsonl", idx));
    }

    private static int findLastIndex() {
        int max = 0;
        File[] files = DIR.listFiles();
        if (files == null) return 0;

        for (File f : files) {
            Matcher m = FILE_PATTERN.matcher(f.getName());
            if (m.matches()) {
                try {
                    int idx = Integer.parseInt(m.group(1));
                    if (idx > max) max = idx;
                } catch (Exception ignored) {}
            }
        }
        return max;
    }

    private static int countLines(File f) {
        if (!f.exists()) return 0;
        int lines = 0;
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(f))) {
            byte[] buf = new byte[64 * 1024];
            int read;
            while ((read = in.read(buf)) != -1) {
                for (int i = 0; i < read; i++) {
                    if (buf[i] == '\n') lines++;
                }
            }
        } catch (Exception ignored) {}
        return lines;
    }
}

package com.hardrock.griefercheck.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public class GCConfig {

    public static final ForgeConfigSpec SPEC;

    public static ForgeConfigSpec.BooleanValue LOG_BLOCK_PLACE;
    public static ForgeConfigSpec.BooleanValue LOG_BLOCK_BREAK;
    public static ForgeConfigSpec.BooleanValue LOG_CONTAINER_OPEN;
    public static ForgeConfigSpec.BooleanValue LOG_CONTAINER_TRANSFER;
    public static ForgeConfigSpec.BooleanValue LOG_INTERACT_BLOCKS;

    public static ForgeConfigSpec.ConfigValue<List<? extends String>> INTERACT_BLOCK_BLACKLIST;
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> CONTAINER_OPEN_TRANSFER_BLACKLIST;

    // Performance / storage
    public static ForgeConfigSpec.IntValue MAX_LINES_PER_FILE;   // rotate after N lines
    public static ForgeConfigSpec.IntValue MAX_FILES_TO_SCAN;    // how many newest files to scan per query
    public static ForgeConfigSpec.IntValue SCAN_LINES_PER_FILE;  // how many lines per file to tail-scan

    // Update configuration options
    public static ForgeConfigSpec.BooleanValue ENABLE_UPDATE_CHECK;
    public static ForgeConfigSpec.IntValue UPDATE_CHECK_TIMEOUT_MS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("logging");

        LOG_BLOCK_PLACE = b
                .comment("Logs when a player places a block.")
                .define("blockPlace", true);
        LOG_BLOCK_BREAK = b
                .comment("Logs when a player breaks a block (including creative mode).")
                .define("blockBreak", true);
        LOG_CONTAINER_OPEN = b
                .comment("Logs when a player opens a container with an inventory.")
                .define("containerOpen", true);
        LOG_CONTAINER_TRANSFER = b
                .comment("Logs item transfers into and out of containers.")
                .define("containerTransfer", true);
        LOG_INTERACT_BLOCKS = b
                .comment("Logs block interactions such as levers or buttons.")
                .define("interactBlocks", false);
        INTERACT_BLOCK_BLACKLIST = b
                .comment("List of block IDs that are excluded from block interaction logging.")
                .defineList(
                        "interactBlockBlacklist",
                        List.of("minecraft:lever", "minecraft:stone_button"),
                        o -> o instanceof String
                );
        CONTAINER_OPEN_TRANSFER_BLACKLIST = b
                .comment("List of container block IDs that should be ignored for container open and transfer logging.")
                .defineList(
                        "containerOpenTransferBlacklist",
                        List.of("minecraft:ender_chest"),
                        o -> o instanceof String
                );

        b.pop();

        b.push("storage");
        MAX_LINES_PER_FILE = b
                .comment("Maximum number of log entries per file before a new file is created.")
                .defineInRange("maxLinesPerFile", 10000, 1000, 5_000_000);
        MAX_FILES_TO_SCAN = b
                .comment("Maximum number of log files scanned per query.")
                .defineInRange("maxFilesToScan", 20, 1, 500);
        SCAN_LINES_PER_FILE = b
                .comment("Maximum number of lines read per log file during a scan.")
                .defineInRange("scanLinesPerFile", 15000, 1000, 500_000);
        b.pop();

        b.push("update");

        ENABLE_UPDATE_CHECK = b
                .comment("If true, the server checks for new Griefercheck versions and notifies OPs ingame.")
                .define("enableUpdateCheck", true);

        UPDATE_CHECK_TIMEOUT_MS = b
                .comment("HTTP timeout in milliseconds for the update check.")
                .defineInRange("updateCheckTimeoutMs", 4000, 1000, 20000);

        b.pop();


        SPEC = b.build();
    }
}

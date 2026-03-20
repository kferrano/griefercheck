package com.hardrock.griefercheck.commands;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.hardrock.griefercheck.query.GCPagination;
import com.hardrock.griefercheck.query.GCQueryService;
import com.hardrock.griefercheck.inspect.GCInspect;
import com.hardrock.griefercheck.compat.FTBChunksCompat;

import net.minecraft.world.level.ChunkPos;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;


import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class GCCommands {

    private static final File LOG_DIR = new File("griefercheck");

    private static final int PAGE_SIZE = 10;
    private static final int MAX_RESULTS = 200;

    @SubscribeEvent
    public void onRegister(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();

        // Registers base `gc` command requiring permission level 2
        var gc = Commands.literal("gc")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("block").executes(ctx -> runBlock(ctx.getSource())))
                .then(Commands.literal("3x3").executes(ctx -> run3x3(ctx.getSource())))
                .then(Commands.literal("chunk").executes(ctx -> runChunk(ctx.getSource())))
                // Configures radius sub‑command with block limit
                .then(Commands.literal("radius").then(Commands.argument("blocks", IntegerArgumentType.integer(1, 100))
                // Configures page sub‑command with page number
                        .executes(ctx -> runRadius(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "blocks")))))
                // Registers page sub‑command with page number argument
                .then(Commands.literal("page")
                        .then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .executes(ctx -> runPage(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "n")))))
                .then(Commands.literal("inspect").executes(ctx -> toggleInspect(ctx.getSource()))
                );
        // Conditionally registers claim command if ftbchunks loaded
        if (ModList.get().isLoaded("ftbchunks")) {
            gc = gc.then(Commands.literal("claim").executes(ctx -> runClaim(ctx.getSource())));
        }
        d.register(gc);
    }

    /**
     * Queries and displays events for player's block
     */
    private int runBlock(CommandSourceStack src) {
        ServerPlayer p;
        try {
            p = src.getPlayerOrException();
        } catch (Exception ex) {
            src.sendFailure(new TextComponent("This command can only be used by a player."));
            return 0;
        }

        BlockPos pos = p.blockPosition();
        String dim = p.level.dimension().location().toString();

        GCQueryService.Query q = new GCQueryService.Query(
                dim,
                pos.getX(), pos.getX(),
                pos.getY(), pos.getY(),
                pos.getZ(), pos.getZ(),
                MAX_RESULTS
        );

        List<JsonObject> results = GCQueryService.queryEvents(LOG_DIR, q);
        setAndShow(src, p, results, 1, "Block @ " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        return 1;
    }

    /**
     * Queries and displays events for a 3x3 area
     */
    private int run3x3(CommandSourceStack src) {
        ServerPlayer p;
        try {
            p = src.getPlayerOrException();
        } catch (Exception ex) {
            src.sendFailure(new TextComponent("This command can only be used by a player."));
            return 0;
        }

        BlockPos pos = p.blockPosition();
        String dim = p.level.dimension().location().toString();

        int r = 3;
        GCQueryService.Query q = new GCQueryService.Query(
                dim,
                pos.getX() - r, pos.getX() + r,
                pos.getY() - r, pos.getY() + r,
                pos.getZ() - r, pos.getZ() + r,
                MAX_RESULTS
        );

        List<JsonObject> results = GCQueryService.queryEvents(LOG_DIR, q);
        setAndShow(src, p, results, 1, "Area 3x3 @ " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        return 1;
    }

    /**
     * Queries events within player's chunk and displays results
     */
    private int runChunk(CommandSourceStack src) {
        ServerPlayer p;
        try {
            p = src.getPlayerOrException();
        } catch (Exception ex) {
            src.sendFailure(new TextComponent("This command can only be used by a player."));
            return 0;
        }

        BlockPos pos = p.blockPosition();
        String dim = p.level.dimension().location().toString();

        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;

        int xMin = (cx << 4);
        int xMax = xMin + 15;
        int zMin = (cz << 4);
        int zMax = zMin + 15;

        GCQueryService.Query q = new GCQueryService.Query(
                dim,
                xMin, xMax,
                null, null,
                zMin, zMax,
                MAX_RESULTS
        );

        List<JsonObject> results = GCQueryService.queryEvents(LOG_DIR, q);
        setAndShow(src, p, results, 1, "Chunk [" + cx + "," + cz + "]");
        return 1;
    }

    /**
     * Handles pagination command for query results
     */
    private int runPage(CommandSourceStack src, int n) {
        ServerPlayer p;
        try {
            p = src.getPlayerOrException();
        } catch (Exception ex) {
            src.sendFailure(new TextComponent("This command can only be used by a player."));
            return 0;
        }

        GCPagination.PageState st = GCPagination.get(p.getUUID());
        if (st == null) {
            src.sendFailure(new TextComponent("No active query context. Use /gc block, /gc 3x3, or /gc chunk first."));
            return 0;
        }

        int pages = st.pageCount();
        if (n < 1 || n > pages) {
            src.sendFailure(new TextComponent("Invalid page. Range: 1-" + pages));
            return 0;
        }

        showPage(src, st, n, "Page");
    // Sets pagination state then shows first page
        return 1;
    }

    /**
     * Sets pagination state then shows first page
     */
    private void setAndShow(CommandSourceStack src, ServerPlayer admin, List<JsonObject> results, int page, String title) {
        GCPagination.set(admin.getUUID(), new GCPagination.PageState(results, PAGE_SIZE));
        GCPagination.PageState st = GCPagination.get(admin.getUUID());

        MutableComponent header = new TextComponent("[GC] ")
                .withStyle(ChatFormatting.GOLD)
                .append(new TextComponent(title).withStyle(ChatFormatting.YELLOW))
                .append(new TextComponent(" | Matches: " + results.size()).withStyle(ChatFormatting.GRAY));

        src.sendSuccess(header, false);
    // Sends formatted entries for current page
        showPage(src, st, page, title);
    }

    /**
     * Sends paginated entries; sends empty message if none
     */
    private void showPage(CommandSourceStack src, GCPagination.PageState st, int page, String title) {
        int pages = st.pageCount();
        int start = (page - 1) * st.pageSize;
        int end = Math.min(st.results.size(), start + st.pageSize);

        if (st.results.isEmpty()) {
            src.sendSuccess(new TextComponent("No entries found.").withStyle(ChatFormatting.GRAY), false);
            return;
        }

        for (int i = start; i < end; i++) {
            JsonObject o = st.results.get(i);
            sendFormattedEntry(src, o); // <- IMPORTANT
        }

        src.sendSuccess(pagerComponent(page, pages, title), false);
    }


    /**
     * Builds component with navigation controls
     */
    private Component pagerComponent(int page, int pages, String title) {
        MutableComponent left;
        if (page <= 1) {
            left = new TextComponent("<< Prev").withStyle(ChatFormatting.DARK_GRAY);
        } else {
            left = new TextComponent("<< Prev").withStyle(s -> s
                    .withColor(ChatFormatting.GRAY)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/gc page " + (page - 1)))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new TextComponent("Go to page " + (page - 1))))
            );
        }

        MutableComponent mid = new TextComponent("  [" + title + "] " + page + "/" + pages + "  ")
                .withStyle(ChatFormatting.DARK_GRAY);

        MutableComponent right;
            // Adds command to navigate to next page
        if (page >= pages) {
            right = new TextComponent("Next >>").withStyle(ChatFormatting.DARK_GRAY);
        } else {
            right = new TextComponent("Next >>").withStyle(s -> s
                    .withColor(ChatFormatting.GRAY)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/gc page " + (page + 1)))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            new TextComponent("Go to page " + (page + 1))))
            );
        }

        return new TextComponent("").append(left).append(mid).append(right);
    }

    private static String getStr(JsonObject o, String k) {
        try {
            JsonElement e = o.get(k);
            return e == null ? "" : e.getAsString();
        } catch (Exception ex) {
            return "";
    // Summarizes item deltas into a string
        }
    }

    /**
     * Summarizes item deltas into a string
     */
    private static String summarizeItems(JsonObject items, int maxEntries) {
        // Appends item deltas to summary string
        StringBuilder sb = new StringBuilder();
        int shown = 0;

        for (String key : items.keySet()) {
            if (shown >= maxEntries) break;
            int delta;
            try {
                delta = items.get(key).getAsInt();
            } catch (Exception ex) {
                continue;
            }

            if (shown > 0) sb.append(", ");
            sb.append(key).append(delta >= 0 ? " +" : " ").append(delta);
            shown++;
        }

        int total = items.keySet().size();
        if (total > shown) sb.append(", ...");

        return sb.toString();
    }
    /**
     * Formats elapsed time since timestamp as string
     */
    private static String ageAgo(long ts) {
        long diff = Math.max(0, System.currentTimeMillis() - ts);
        double min = diff / 60000.0;
        if (min < 60) return String.format("%.2f/m ago", min);
        double h = min / 60.0;
        if (h < 24) return String.format("%.2f/h ago", h);
        return String.format("%.2f/d ago", h / 24.0);
    }

    /**
     * Converts internal ID to human‑readable name
     */
    private static String prettyName(String id) {
        if (id == null) return "";
        String name = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            sb.append(Character.toUpperCase(p.charAt(0)))
                    .append(p.substring(1))
                    .append(" ");
        }
        return sb.toString().trim();
    }

    /**
     * Sends formatted chat messages for various actions
     */
    private void sendFormattedEntry(CommandSourceStack src, JsonObject o) {
        String action = getStr(o, "action");
        String player = getStr(o, "player");

        String pos = (o.has("x") && o.has("y") && o.has("z"))
                ? (o.get("x").getAsInt() + "/" + o.get("y").getAsInt() + "/" + o.get("z").getAsInt())
                : "?/?/?";

        String age = o.has("ts") ? ageAgo(o.get("ts").getAsLong()) : "";
        java.util.UUID viewerId = null;
        if (src.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
            viewerId = sp.getUUID();
        }

        GCPagination.PageState st = (viewerId != null) ? GCPagination.get(viewerId) : null;


        if ("container_transfer".equals(action)) {
            if (st == null || !st.inspectOnly) {
                return; // suppress global chat output
            }
        }

        if ("container_transfer".equals(action)
                && o.has("items")
                && o.get("items").isJsonObject()) {

            JsonObject items = o.getAsJsonObject("items");

            boolean isCorpse = o.has("source") && "corpse".equals(getStr(o, "source"));

            for (String key : items.keySet()) {
                int delta;
                try {
                    delta = items.get(key).getAsInt();
                } catch (Exception ex) {
                    continue;
                }
                if (delta == 0) continue;

                boolean added = delta > 0;
                int count = Math.abs(delta);

                // Prefix text depends on source
                String targetName = "Container";
                if (o.has("block")) {
                    targetName = prettyName(getStr(o, "block"));
                } else if (o.has("source") && "corpse".equals(getStr(o, "source"))) {
                    targetName = "Corpse";
                }


                src.sendSuccess(
                        new TextComponent(age + " ")
                                .withStyle(ChatFormatting.DARK_GRAY)
                                .append(new TextComponent(added ? "+ " : "- ")
                                        .withStyle(added ? ChatFormatting.GREEN : ChatFormatting.RED))
                                .append(new TextComponent(player).withStyle(ChatFormatting.YELLOW))
                                .append(new TextComponent(
                                        added ? " added x" : " removed x"
                                ).withStyle(ChatFormatting.GRAY))
                                .append(new TextComponent(count + " " + prettyName(key))
                                        .withStyle(ChatFormatting.WHITE)),
                        false
                );
            }
            return;
        }


        // One-liners for other actions
        String block = o.has("block") ? prettyName(getStr(o, "block")) : "";

        if ("container_open".equals(action)) {
            src.sendSuccess(
                    new TextComponent(age + " ")
                            .withStyle(ChatFormatting.DARK_GRAY)
                            .append(new TextComponent("+ ").withStyle(ChatFormatting.GREEN))
                            .append(new TextComponent(player).withStyle(ChatFormatting.YELLOW))
                            .append(new TextComponent(" clicked ").withStyle(ChatFormatting.GRAY))
                            .append(new TextComponent(block.isEmpty() ? "Container" : block).withStyle(ChatFormatting.WHITE))
                            .append(new TextComponent(" (" + pos + ")").withStyle(ChatFormatting.DARK_GRAY)),
                    false
            );
            return;
        }

        if ("block_place".equals(action)) {
            src.sendSuccess(
                    new TextComponent(age + " ")
                            .withStyle(ChatFormatting.DARK_GRAY)
                            .append(new TextComponent("+ ").withStyle(ChatFormatting.GREEN))
                            .append(new TextComponent(player).withStyle(ChatFormatting.YELLOW))
                            .append(new TextComponent(" placed ").withStyle(ChatFormatting.GRAY))
                            .append(new TextComponent(block.isEmpty() ? "Block" : block).withStyle(ChatFormatting.WHITE))
                            .append(new TextComponent(" (" + pos + ")").withStyle(ChatFormatting.DARK_GRAY)),
                    false
            );
            return;
        }

        if ("block_break".equals(action)) {
            src.sendSuccess(
                    new TextComponent(age + " ")
                            .withStyle(ChatFormatting.DARK_GRAY)
                            .append(new TextComponent("- ").withStyle(ChatFormatting.RED))
                            .append(new TextComponent(player).withStyle(ChatFormatting.YELLOW))
                            .append(new TextComponent(" broke ").withStyle(ChatFormatting.GRAY))
                            .append(new TextComponent(block.isEmpty() ? "Block" : block).withStyle(ChatFormatting.WHITE))
                            .append(new TextComponent(" (" + pos + ")").withStyle(ChatFormatting.DARK_GRAY)),
                    false
            );
            return;
        }

        if ("block_interact".equals(action)) {
            src.sendSuccess(
                    new TextComponent(age + " ")
                            .withStyle(ChatFormatting.DARK_GRAY)
                            .append(new TextComponent("+ ").withStyle(ChatFormatting.GREEN))
                            .append(new TextComponent(player).withStyle(ChatFormatting.YELLOW))
                            .append(new TextComponent(" used ").withStyle(ChatFormatting.GRAY))
                            .append(new TextComponent(block.isEmpty() ? "Block" : block).withStyle(ChatFormatting.WHITE))
                            .append(new TextComponent(" (" + pos + ")").withStyle(ChatFormatting.DARK_GRAY)),
                    false
            );
            return;
        }

        if ("corpse_open".equals(action)) {
            String corpse = o.has("corpse") ? prettyName(getStr(o, "corpse")) : "Corpse";

            src.sendSuccess(
                    new TextComponent(age + " ")
                            .withStyle(ChatFormatting.DARK_GRAY)
                            .append(new TextComponent("+ ").withStyle(ChatFormatting.GREEN))
                            .append(new TextComponent(player).withStyle(ChatFormatting.YELLOW))
                            .append(new TextComponent(" clicked ").withStyle(ChatFormatting.GRAY))
                            .append(new TextComponent(corpse).withStyle(ChatFormatting.WHITE))
                            .append(new TextComponent(" (" + pos + ")").withStyle(ChatFormatting.DARK_GRAY)),
                    false
            );
            return;
        }

        if ("corpse_emptied".equals(action)) {
            src.sendSuccess(
                    new TextComponent(age + " ")
                            .withStyle(ChatFormatting.DARK_GRAY)
                            .append(new TextComponent("! ").withStyle(ChatFormatting.GOLD))
                            .append(new TextComponent("Corpse was emptied").withStyle(ChatFormatting.YELLOW))
                            .append(new TextComponent(" (" + pos + ")").withStyle(ChatFormatting.DARK_GRAY)),
                    false
            );
            return;
        }

        if ("corpse_despawn".equals(action)) {
            src.sendSuccess(
                    new TextComponent(age + " ")
                            .withStyle(ChatFormatting.DARK_GRAY)
                            .append(new TextComponent("! ").withStyle(ChatFormatting.GOLD))
                            .append(new TextComponent("Corpse despawned").withStyle(ChatFormatting.YELLOW))
                            .append(new TextComponent(" (" + pos + ")").withStyle(ChatFormatting.DARK_GRAY)),
                    false
            );
            return;
        }




        // Fallback
        src.sendSuccess(
                new TextComponent(age + " ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                        .append(new TextComponent(player).withStyle(ChatFormatting.YELLOW))
                        .append(new TextComponent(" " + action).withStyle(ChatFormatting.GRAY))
                        .append(new TextComponent(" (" + pos + ")").withStyle(ChatFormatting.DARK_GRAY)),
                false
        );
    }
    private int runRadius(CommandSourceStack src, int r) {
        ServerPlayer p;
        try {
            p = src.getPlayerOrException();
        } catch (Exception ex) {
            src.sendFailure(new TextComponent("This command can only be used by a player."));
            return 0;
        }

        BlockPos pos = p.blockPosition();
        String dim = p.level.dimension().location().toString();

        GCQueryService.Query q = new GCQueryService.Query(
                dim,
                pos.getX() - r, pos.getX() + r,
                pos.getY() - r, pos.getY() + r,
                pos.getZ() - r, pos.getZ() + r,
                MAX_RESULTS
        );

        List<JsonObject> results = GCQueryService.queryEvents(LOG_DIR, q);
        setAndShow(src, p, results, 1, "Radius " + r + " @ " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        return 1;
    }

    private int toggleInspect(CommandSourceStack src) {
        ServerPlayer p;
        try { p = src.getPlayerOrException(); }
        catch (Exception ex) {
            src.sendFailure(new TextComponent("This command can only be used by a player."));
            return 0;
        }

        boolean now = GCInspect.toggle(p.getUUID());
        src.sendSuccess(new TextComponent("[GC] Inspect mode: " + (now ? "ON" : "OFF"))
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }
    private int runClaim(CommandSourceStack src) {
        ServerPlayer p;
        try {
            p = src.getPlayerOrException();
        } catch (Exception ex) {
            src.sendFailure(new TextComponent("This command can only be used by a player."));
            return 0;
        }

        if (!FTBChunksCompat.isLoaded()) {
            src.sendFailure(new TextComponent("FTB Chunks integration is not available.").withStyle(ChatFormatting.GRAY));
            return 0;
        }

        FTBChunksCompat.ClaimScan scan = FTBChunksCompat.getCurrentClaimTeamChunks(p);
        if (scan == null) {
            src.sendSuccess(new TextComponent("This chunk is not claimed (or claim data could not be read).")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        ChunkPos here = new ChunkPos(p.blockPosition());

        // Convert chunk bounds to block bounds (inclusive)
        int minX = scan.minChunkX * 16;
        int maxX = scan.maxChunkX * 16 + 15;
        int minZ = scan.minChunkZ * 16;
        int maxZ = scan.maxChunkZ * 16 + 15;

        String dim = p.level.dimension().location().toString();

        // Query a bounding box first (fast)...
        GCQueryService.Query q = new GCQueryService.Query(
                dim,
                minX, maxX,
                -64, 319,
                minZ, maxZ,
                MAX_RESULTS
        );

        List<JsonObject> raw = GCQueryService.queryEvents(LOG_DIR, q);

        // ...then filter to exact claimed chunks (correct)
        List<JsonObject> results = new ArrayList<>();
        for (JsonObject o : raw) {
            if (!o.has("x") || !o.has("z")) continue;
            int x = o.get("x").getAsInt();
            int z = o.get("z").getAsInt();
            long key = ChunkPos.asLong(x >> 4, z >> 4);
            if (scan.chunkKeys.contains(key)) {
                results.add(o);
            }
        }

        setAndShow(src, p, results, 1,
                "Claim scan @ chunk " + here.x + " " + here.z + " (" + scan.chunkKeys.size() + " chunks)");
        return 1;
    }



}

package com.hardrock.griefercheck.events;

import com.google.gson.JsonObject;

import com.hardrock.griefercheck.config.GCConfig;
import com.hardrock.griefercheck.logging.GCEvent;
import com.hardrock.griefercheck.logging.GCLogger;
import com.hardrock.griefercheck.logging.GCUtil;
import com.hardrock.griefercheck.tracking.ContainerTracker;
import com.hardrock.griefercheck.compat.CorpseCompat;
import com.hardrock.griefercheck.update.GCUpdateChecker;
import com.hardrock.griefercheck.inspect.GCInspect;
import com.hardrock.griefercheck.query.GCPagination;
import com.hardrock.griefercheck.query.GCQueryService;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.entity.EntityLeaveWorldEvent;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.fml.ModList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class GCEventHandlers {
    private static final File LOG_DIR = new File("griefercheck");
    private static final int INSPECT_MAX_RESULTS = 120;
    private static final int INSPECT_PAGE_SIZE = 12;


    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent e) {
        GCUpdateChecker.checkAsync();
    }

    /**
     * Notifies operators about available updates on login
     */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer sp)) return;
        if (!sp.hasPermissions(2)) return; // OP only

        // If not checked yet, start the async check now
        if (!GCUpdateChecker.hasCheckedOnce()) {
            GCUpdateChecker.checkAsync();
        }

        // Try immediate notify (in case check already finished)
        if (GCUpdateChecker.shouldNotify()) {
            sendUpdateMessage(sp);
            return;
        }

        // If check isn't finished yet, retry once after a short delay
        java.util.concurrent.CompletableFuture
                .delayedExecutor(3, java.util.concurrent.TimeUnit.SECONDS)
                .execute(() -> {
                    // must run message send on server thread
                    sp.getServer().execute(() -> {
                        if (!sp.isAlive()) return;
                        if (!sp.hasPermissions(2)) return;

                        if (GCUpdateChecker.shouldNotify()) {
                            sendUpdateMessage(sp);
                        }
                    });
                });
    }

    private void sendUpdateMessage(ServerPlayer sp) {
        String currentRaw = GCUpdateChecker.getCurrentVersion();
        String current = GCUpdateChecker.normalizeCurrentVersion(currentRaw);

        String latestRaw = GCUpdateChecker.getLatestVersion();
        String latest = GCUpdateChecker.normalizeRemoteVersion(latestRaw);

        sp.sendMessage(
                new TextComponent("[Griefercheck] ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(new TextComponent("A new version is available: ").withStyle(ChatFormatting.GRAY))
                        .append(new TextComponent(latest).withStyle(ChatFormatting.YELLOW))
                        .append(new TextComponent(" (current: ").withStyle(ChatFormatting.GRAY))
                        .append(new TextComponent(current).withStyle(ChatFormatting.YELLOW))
                        .append(new TextComponent(") ").withStyle(ChatFormatting.GRAY))
                        .append(new TextComponent("[Download]")
                                .withStyle(style -> style
                                        .withColor(ChatFormatting.AQUA)
                                        .withUnderlined(true)
                                        .withClickEvent(new ClickEvent(
                                                ClickEvent.Action.OPEN_URL,
                                                GCUpdateChecker.CURSEFORGE_URL
                                        ))
                                        .withHoverEvent(new HoverEvent(
                                                HoverEvent.Action.SHOW_TEXT,
                                                new TextComponent("Open Griefercheck on CurseForge")
                                        ))
                                )
                        ),
                sp.getUUID()
        );
    }



    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent e) {
        if (!GCConfig.LOG_BLOCK_PLACE.get()) return;
        if (!(e.getEntity() instanceof Player p)) return;

        GCEvent ev = new GCEvent("block_place");
        GCUtil.addPlayer(ev, p);
        GCUtil.addPos(ev, p.level, e.getPos());
        ev.add("block", e.getPlacedBlock().getBlock().getRegistryName().toString());

        GCLogger.log(ev);
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent e) {
        if (!GCConfig.LOG_BLOCK_BREAK.get()) return;

        GCEvent ev = new GCEvent("block_break");
        GCUtil.addPlayer(ev, e.getPlayer());
        GCUtil.addPos(ev, e.getPlayer().level, e.getPos());
        ev.add("block", e.getState().getBlock().getRegistryName().toString());

        GCLogger.log(ev);
    }

    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        Player p = e.getPlayer();
        if (p.level.isClientSide) return; // server only

        BlockPos pos = e.getPos();
        boolean isContainer = (p.level.getBlockEntity(pos) instanceof Container);

        // ---------------- INSPECT MODE ----------------
        if (isContainer && (p instanceof ServerPlayer sp) && GCInspect.isEnabled(sp.getUUID())) {

            String dim = sp.level.dimension().location().toString();

            // Query exact block position
            GCQueryService.Query q = new GCQueryService.Query(
                    dim,
                    pos.getX(), pos.getX(),
                    pos.getY(), pos.getY(),
                    pos.getZ(), pos.getZ(),
                    INSPECT_MAX_RESULTS
            );

            List<JsonObject> raw = GCQueryService.queryEvents(LOG_DIR, q);

            // Filter: ONLY container_transfer (added/removed)
            List<JsonObject> results = new ArrayList<>();
            for (JsonObject o : raw) {
                if (!o.has("action")) continue;
                String action = o.get("action").getAsString();
                if ("container_transfer".equals(action)) results.add(o);
            }

            // Save pagination context for /gc page
            GCPagination.set(
                    sp.getUUID(),
                    new GCPagination.PageState(results, INSPECT_PAGE_SIZE, true)
            );

            sp.sendMessage(new TextComponent("----- Griefercheck Lookup -----").withStyle(ChatFormatting.GOLD), sp.getUUID());
            sp.sendMessage(new TextComponent("Target: " + pos.getX() + " " + pos.getY() + " " + pos.getZ())
                    .withStyle(ChatFormatting.GRAY), sp.getUUID());
            sp.sendMessage(new TextComponent("Showing: container transfers (added/removed)").withStyle(ChatFormatting.GRAY), sp.getUUID());

            // Show first page via server dispatcher (NO client handler)
            sp.getServer().getCommands().performCommand(sp.createCommandSourceStack(), "gc page 1");

            // IMPORTANT: prevent opening the container
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        // ---------------- NORMAL LOGIC (non-inspect) ----------------
        if (!GCConfig.LOG_CONTAINER_OPEN.get() && !GCConfig.LOG_INTERACT_BLOCKS.get() && !GCConfig.LOG_CONTAINER_TRANSFER.get()) {
            return;
        }

        Block block = p.level.getBlockState(pos).getBlock();
        ResourceLocation id = block.getRegistryName();

        // optional block_interact
        if (GCConfig.LOG_INTERACT_BLOCKS.get()
                && id != null
                && !GCConfig.INTERACT_BLOCK_BLACKLIST.get().contains(id.toString())) {

            GCEvent ev = new GCEvent("block_interact");
            GCUtil.addPlayer(ev, p);
            GCUtil.addPos(ev, p.level, pos);
            ev.add("block", id.toString());
            GCLogger.log(ev);
        }

        // container context for transfer + optional open log
        if (isContainer
                && id != null
                && (GCConfig.LOG_CONTAINER_OPEN.get() || GCConfig.LOG_CONTAINER_TRANSFER.get())
                && GCConfig.CONTAINER_OPEN_TRANSFER_BLACKLIST.get().contains(id.toString())) {

            String bid = id.toString();

            ContainerTracker.setLastContainerContext(
                    p.getUUID(),
                    p.level.dimension().location().toString(),
                    pos.immutable(),
                    "block",
                    bid
            );
        }

        if (GCConfig.LOG_CONTAINER_OPEN.get() && isContainer) {
            GCEvent ev = new GCEvent("container_open");
            GCUtil.addPlayer(ev, p);
            GCUtil.addPos(ev, p.level, pos);
            if (id != null) ev.add("block", id.toString());
            GCLogger.log(ev);
        }
    }

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open e) {
        if (!GCConfig.LOG_CONTAINER_TRANSFER.get()) return;
        ContainerTracker.snapshot(e.getPlayer().getUUID(), e.getPlayer().getInventory(), e.getContainer());
    }

    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close e) {
        if (!GCConfig.LOG_CONTAINER_TRANSFER.get()) return;

        var res = ContainerTracker.diff(e.getPlayer().getUUID(), e.getPlayer().getInventory(), e.getContainer());
        if (res == null) return;

        var snap = res.diffData;

        // Log transfer if diff not empty
        if (!snap.items.isEmpty()) {
            com.google.gson.JsonObject items = new com.google.gson.JsonObject();
            snap.items.forEach(items::addProperty);

            GCEvent ev = new GCEvent("container_transfer");
            GCUtil.addPlayer(ev, e.getPlayer());

            if (snap.dim != null && !snap.dim.isEmpty()) ev.add("dim", snap.dim);
            if (snap.pos != null) {
                ev.add("x", snap.pos.getX());
                ev.add("y", snap.pos.getY());
                ev.add("z", snap.pos.getZ());
            }
            if (snap.blockId != null && !snap.blockId.isEmpty()) {
                ev.add("block", snap.blockId);
            }

            ev.add("source", snap.sourceType); // "block" or "corpse"
            ev.obj().add("items", items);

            GCLogger.log(ev);
        }

        // If it was a corpse and is empty after close -> log corpse_emptied
        if ("corpse".equals(snap.sourceType) && res.emptyAfter) {
            GCEvent ev = new GCEvent("corpse_emptied");
            GCUtil.addPlayer(ev, e.getPlayer());

            if (snap.dim != null && !snap.dim.isEmpty()) ev.add("dim", snap.dim);
            if (snap.pos != null) {
                ev.add("x", snap.pos.getX());
                ev.add("y", snap.pos.getY());
                ev.add("z", snap.pos.getZ());
            }

            GCLogger.log(ev);
        }
    }


    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract e) {
        if (!CorpseCompat.isLoaded()) return;

        Player p = e.getPlayer();
        if (p.level.isClientSide) return;

        Entity target = e.getTarget();
        if (!CorpseCompat.isCorpseEntity(target)) return;

        ContainerTracker.setLastContainerContext(
                p.getUUID(),
                p.level.dimension().location().toString(),
                target.blockPosition().immutable(),
                "corpse",
                "corpse:corpse"
        );

        if (ModList.get().isLoaded("corpse")) {
            GCEvent ev = new GCEvent("corpse_open");
            GCUtil.addPlayer(ev, p);
            GCUtil.addPos(ev, p.level, target.blockPosition());
            ev.add("block", "corpse:corpse"); // für prettyName => "Corpse"
            GCLogger.log(ev);
        }
    }

    @SubscribeEvent
    public void onEntityLeave(EntityLeaveWorldEvent e) {
        if (!CorpseCompat.isLoaded()) return;

        var ent = e.getEntity();
        if (!CorpseCompat.isCorpseEntity(ent)) return;

        // We only want server-side logging
        if (ent.level.isClientSide) return;

        GCEvent ev = new GCEvent("corpse_despawn");
        ev.add("dim", ent.level.dimension().location().toString());
        ev.add("x", ent.blockPosition().getX());
        ev.add("y", ent.blockPosition().getY());
        ev.add("z", ent.blockPosition().getZ());

        GCLogger.log(ev);
    }

}

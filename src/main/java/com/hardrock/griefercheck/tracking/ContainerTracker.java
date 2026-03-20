package com.hardrock.griefercheck.tracking;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ContainerTracker {

    public static class SnapshotData {
        public final Map<String, Integer> items; // either snapshot or diff (depending on method)
        public final String dim;
        public final BlockPos pos;
        public final String sourceType; // e.g. "block", "corpse"
        public final String blockId;

        public SnapshotData(Map<String, Integer> items, String dim, BlockPos pos, String sourceType, String blockId) {
            this.items = items;
            this.dim = dim;
            this.pos = pos;
            this.sourceType = sourceType;
            this.blockId = blockId;
        }
    }

    // containerId -> BEFORE snapshot + context
    private static final Map<Integer, SnapshotData> SNAPSHOTS = new HashMap<>();

    // player -> last container context (captured at RightClickBlock or EntityInteract)
    private static final Map<UUID, BlockPos> LAST_POS = new HashMap<>();
    private static final Map<UUID, String> LAST_DIM = new HashMap<>();
    private static final Map<UUID, String> LAST_TYPE = new HashMap<>();
    private static final Map<UUID, String> LAST_BLOCK_ID = new HashMap<>();


    public static void init() {}

    // Backwards-compatible: default type "block"
    public static void setLastContainerContext(UUID player, String dim, BlockPos pos, String type) {
        setLastContainerContext(player, dim, pos, type, null);
    }

    public static void setLastContainerContext(
            UUID player,
            String dim,
            BlockPos pos,
            String type,
            String blockId
    ) {
        LAST_DIM.put(player, dim);
        LAST_POS.put(player, pos);
        LAST_TYPE.put(player, type == null ? "block" : type);

        if (blockId != null && !blockId.isEmpty()) {
            LAST_BLOCK_ID.put(player, blockId);
        }
    }

    // Take BEFORE snapshot for "non-player inventory slots"
    public static void snapshot(UUID player, Inventory playerInv, AbstractContainerMenu menu) {
        String dim = LAST_DIM.getOrDefault(player, "");
        BlockPos pos = LAST_POS.get(player);
        String type = LAST_TYPE.getOrDefault(player, "block");
        String blockId = LAST_BLOCK_ID.getOrDefault(player, "");

        Map<String, Integer> before = snapshotNonPlayerSlots(menu, playerInv);
        SNAPSHOTS.put(menu.containerId, new SnapshotData(before, dim, pos, type, blockId));

    }

    // Compute DIFF (after-before) + also report whether container is empty after close
    public static DiffResult diff(UUID player, Inventory playerInv, AbstractContainerMenu menu) {
        SnapshotData beforeData = SNAPSHOTS.remove(menu.containerId);
        if (beforeData == null) return null;

        Map<String, Integer> after = snapshotNonPlayerSlots(menu, playerInv);

        Map<String, Integer> diff = new HashMap<>();
        for (String id : beforeData.items.keySet()) {
            int d = after.getOrDefault(id, 0) - beforeData.items.get(id);
            if (d != 0) diff.put(id, d);
        }
        for (String id : after.keySet()) {
            if (!beforeData.items.containsKey(id)) diff.put(id, after.get(id));
        }

        boolean emptyAfter = after.isEmpty();
        if (diff.isEmpty() && !emptyAfter) {
            // no changes and not empty; nothing to log
            return null;
        }

        return new DiffResult(
                new SnapshotData(diff, beforeData.dim, beforeData.pos, beforeData.sourceType, beforeData.blockId),
                emptyAfter
        );
    }

    public static class DiffResult {
        public final SnapshotData diffData;
        public final boolean emptyAfter;

        public DiffResult(SnapshotData diffData, boolean emptyAfter) {
            this.diffData = diffData;
            this.emptyAfter = emptyAfter;
        }
    }

    // Robust: exclude player inventory slots by checking slot.container instance
    private static Map<String, Integer> snapshotNonPlayerSlots(AbstractContainerMenu menu, Inventory playerInv) {
        Map<String, Integer> map = new HashMap<>();

        for (Slot slot : menu.slots) {
            // exclude player's inventory slots
            if (playerInv != null && slot.container == playerInv) continue;

            ItemStack st = slot.getItem();
            if (!st.isEmpty()) {
                String id = st.getItem().getRegistryName().toString();
                map.merge(id, st.getCount(), Integer::sum);
            }
        }

        return map;
    }
}

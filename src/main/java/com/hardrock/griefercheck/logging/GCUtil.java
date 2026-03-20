package com.hardrock.griefercheck.logging;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class GCUtil {

    public static void addPlayer(GCEvent e, Player p) {
        e.add("player", p.getGameProfile().getName());
        e.add("uuid", p.getUUID().toString());

        String gm = "unknown";
        if (p instanceof ServerPlayer sp && sp.gameMode != null && sp.gameMode.getGameModeForPlayer() != null) {
            gm = sp.gameMode.getGameModeForPlayer().getName();
        }
        e.add("gamemode", gm);
    }

    public static void addPos(GCEvent e, Level lvl, BlockPos pos) {
        e.add("dim", lvl.dimension().location().toString());
        e.add("x", pos.getX());
        e.add("y", pos.getY());
        e.add("z", pos.getZ());
    }
}

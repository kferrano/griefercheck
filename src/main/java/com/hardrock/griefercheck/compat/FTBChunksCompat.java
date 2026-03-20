package com.hardrock.griefercheck.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public final class FTBChunksCompat {

    public static final String MODID = "ftbchunks";

    // Classes present in FTB Chunks 1.18.2 (e.g. 1802.3.19 build 362)
    private static final String FTB_CHUNKS_API = "dev.ftb.mods.ftbchunks.data.FTBChunksAPI";
    private static final String CLAIMED_CHUNK_MANAGER = "dev.ftb.mods.ftbchunks.data.ClaimedChunkManager";
    private static final String CLAIMED_CHUNK = "dev.ftb.mods.ftbchunks.data.ClaimedChunk";
    private static final String TEAM_DATA = "dev.ftb.mods.ftbchunks.data.FTBChunksTeamData";
    private static final String CHUNK_DIM_POS = "dev.ftb.mods.ftblibrary.math.ChunkDimPos";

    private FTBChunksCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MODID);
    }

    public static class ClaimScan {
        public final Set<Long> chunkKeys; // ChunkPos.asLong(cx, cz)
        public final int minChunkX, maxChunkX, minChunkZ, maxChunkZ;

        public ClaimScan(Set<Long> chunkKeys, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
            this.chunkKeys = chunkKeys;
            this.minChunkX = minChunkX;
            this.maxChunkX = maxChunkX;
            this.minChunkZ = minChunkZ;
            this.maxChunkZ = maxChunkZ;
        }
    }

    /**
     * Scans all claimed chunks of the same TEAM as the claim at the player's current chunk,
     * in the player's current DIMENSION.
     * <p>
     * Returns null if the current chunk is not claimed or claim data cannot be read.
     */
    public static ClaimScan getCurrentClaimTeamChunks(ServerPlayer player) {
        if (!isLoaded()) return null;

        try {
            // Load classes
            Class<?> apiClz = Class.forName(FTB_CHUNKS_API);
            Class<?> mgrClz = Class.forName(CLAIMED_CHUNK_MANAGER);
            Class<?> claimedClz = Class.forName(CLAIMED_CHUNK);
            Class<?> teamDataClz = Class.forName(TEAM_DATA);
            Class<?> cdpClz = Class.forName(CHUNK_DIM_POS);

            // FTBChunksAPI.isManagerLoaded()
            Method isLoadedM = apiClz.getMethod("isManagerLoaded");
            boolean managerLoaded = (boolean) isLoadedM.invoke(null);
            if (!managerLoaded) return null;

            // FTBChunksAPI.getManager()
            Method getMgrM = apiClz.getMethod("getManager");
            Object manager = getMgrM.invoke(null);
            if (manager == null || !mgrClz.isInstance(manager)) return null;

            // Create ChunkDimPos for player's current chunk
            ChunkPos here = new ChunkPos(player.blockPosition());
            Object hereCDP = createChunkDimPos(cdpClz, player, here.x, here.z);
            if (hereCDP == null) return null;

            // manager.getChunk(ChunkDimPos)
            Method getChunkM = mgrClz.getMethod("getChunk", cdpClz);
            Object claimedHere = getChunkM.invoke(manager, hereCDP);
            if (claimedHere == null || !claimedClz.isInstance(claimedHere)) {
                return null; // truly not claimed
            }

            // claimedHere.getTeamData()
            Method getTeamDataM = claimedClz.getMethod("getTeamData");
            Object teamData = getTeamDataM.invoke(claimedHere);
            if (teamData == null || !teamDataClz.isInstance(teamData)) {
                // fallback: just current chunk
                Set<Long> only = new HashSet<>();
                only.add(ChunkPos.asLong(here.x, here.z));
                return new ClaimScan(only, here.x, here.x, here.z, here.z);
            }

            // Prefer manager.getClaimedChunksByTeam() to get ALL claims reliably
            Method getTeamIdM = teamDataClz.getMethod("getTeamId");
            Object teamIdObj = getTeamIdM.invoke(teamData); // UUID
            if (teamIdObj == null) {
                Set<Long> only = new HashSet<>();
                only.add(ChunkPos.asLong(here.x, here.z));
                return new ClaimScan(only, here.x, here.x, here.z, here.z);
            }

            Method getByTeamM = mgrClz.getMethod("getClaimedChunksByTeam");
            Object byTeamObj = getByTeamM.invoke(manager);

            Map<?, ?> byTeam = (byTeamObj instanceof Map<?, ?> m) ? m : null;
            if (byTeam == null) {
                Set<Long> only = new HashSet<>();
                only.add(ChunkPos.asLong(here.x, here.z));
                return new ClaimScan(only, here.x, here.x, here.z, here.z);
            }

            Object listObj = byTeam.get(teamIdObj);
            Collection<?> claimedChunks = toCollection(listObj);
            if (claimedChunks == null || claimedChunks.isEmpty()) {
                Set<Long> only = new HashSet<>();
                only.add(ChunkPos.asLong(here.x, here.z));
                return new ClaimScan(only, here.x, here.x, here.z, here.z);
            }


            String dimStr = player.level.dimension().location().toString();

            Set<Long> keys = new HashSet<>();
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

            // ClaimedChunk exposes getPos() -> ChunkDimPos
            Method getPosM = null;
            try {
                getPosM = claimedClz.getMethod("getPos");
            } catch (Throwable ignored) {
            }

            // Accept BOTH: ClaimedChunk and ChunkDimPos (and similar)
            for (Object entry : claimedChunks) {
                if (entry == null) continue;

                Object posObj;

                // If it's a ClaimedChunk -> use getPos()
                if (claimedClz.isInstance(entry) && getPosM != null) {
                    try {
                        posObj = getPosM.invoke(entry);
                    } catch (Throwable t) {
                        continue;
                    }
                } else {
                    // Otherwise assume the entry IS the position (ChunkDimPos) or something very similar
                    posObj = entry;
                }

                if (posObj == null) continue;

                // dimension filter (only current dimension)
                //if (!isSameDimension(posObj, player)) continue;


                Integer cx = tryIntGetter(posObj, "x");
                Integer cz = tryIntGetter(posObj, "z");
                if (cx == null || cz == null) continue;

                keys.add(ChunkPos.asLong(cx, cz));
                minX = Math.min(minX, cx);
                maxX = Math.max(maxX, cx);
                minZ = Math.min(minZ, cz);
                maxZ = Math.max(maxZ, cz);
            }
            System.out.println("[GC][FTB] Claimed chunks found for team in dim: " + keys.size());


            if (keys.isEmpty()) {
                // still claimed, but extraction failed -> current chunk only
                keys.add(ChunkPos.asLong(here.x, here.z));
                minX = maxX = here.x;
                minZ = maxZ = here.z;
            }

            return new ClaimScan(keys, minX, maxX, minZ, maxZ);

        } catch (Throwable t) {
            // optional integration: never crash
            return null;
        }


    }

    // ---------------- helpers ----------------

    /**
     * Create ChunkDimPos with reflection.
     * Common ctor in FTB Library for 1.18.2: (ResourceKey<Level>, int, int)
     * We also support other variants defensively.
     */
    private static Object createChunkDimPos(Class<?> chunkDimPosClz, ServerPlayer player, int cx, int cz) {
        try {
            for (Constructor<?> c : chunkDimPosClz.getConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length != 3) continue;
                if (p[1] != int.class || p[2] != int.class) continue;

                Object dimArg = null;

                // ResourceKey
                if (p[0].getName().equals("net.minecraft.resources.ResourceKey")) {
                    dimArg = player.level.dimension();
                }
                // ResourceLocation
                else if (p[0].getName().equals("net.minecraft.resources.ResourceLocation")) {
                    dimArg = player.level.dimension().location();
                }
                // String
                else if (p[0] == String.class) {
                    dimArg = player.level.dimension().location().toString();
                }
                // Level
                else if (p[0].getName().equals("net.minecraft.world.level.Level")) {
                    dimArg = player.level;
                }

                if (dimArg != null) {
                    return c.newInstance(dimArg, cx, cz);
                }
            }
        } catch (Throwable ignored) {
        }

        // Try static factory methods: of/create
        try {
            for (Method m : chunkDimPosClz.getMethods()) {
                if (!Modifier.isStatic(m.getModifiers())) continue;
                if (m.getParameterCount() != 3) continue;
                if (!(m.getName().equals("of") || m.getName().equals("create"))) continue;

                Class<?>[] p = m.getParameterTypes();
                if (p[1] != int.class || p[2] != int.class) continue;

                Object dimArg = null;

                if (p[0].getName().equals("net.minecraft.resources.ResourceKey")) {
                    dimArg = player.level.dimension();
                } else if (p[0].getName().equals("net.minecraft.resources.ResourceLocation")) {
                    dimArg = player.level.dimension().location();
                } else if (p[0] == String.class) {
                    dimArg = player.level.dimension().location().toString();
                } else if (p[0].getName().equals("net.minecraft.world.level.Level")) {
                    dimArg = player.level;
                }

                if (dimArg != null) {
                    return m.invoke(null, dimArg, cx, cz);
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static Collection<?> toCollection(Object obj) {
        if (obj == null) return null;

        if (obj instanceof Collection<?> c) return c;

        if (obj instanceof Map<?, ?> map) return map.values();

        if (obj instanceof Iterable<?> it) {
            List<Object> list = new ArrayList<>();
            for (Object o : it) list.add(o);
            return list;
        }

        return null;
    }

    /**
     * Try to extract dimension string from ChunkDimPos.
     * We attempt common getters/fields without hard dependency.
     */
    private static String extractDimString(Object chunkDimPos) {
        if (chunkDimPos == null) return null;

        // Common method names:
        for (String name : new String[]{"dimension", "getDimension", "dim", "getDim"}) {
            try {
                Method m = chunkDimPos.getClass().getMethod(name);
                Object r = m.invoke(chunkDimPos);
                if (r == null) continue;

                // ResourceKey -> location().toString()
                if (r.getClass().getName().equals("net.minecraft.resources.ResourceKey")) {
                    try {
                        Method loc = r.getClass().getMethod("location");
                        Object rl = loc.invoke(r);
                        return rl == null ? null : rl.toString();
                    } catch (Throwable ignored) {
                    }
                }

                return r.toString();
            } catch (Throwable ignored) {
            }
        }

        // Try public field "dimension"
        try {
            var f = chunkDimPos.getClass().getField("dimension");
            Object r = f.get(chunkDimPos);
            if (r != null) {
                // ResourceKey -> location().toString()
                if (r.getClass().getName().equals("net.minecraft.resources.ResourceKey")) {
                    try {
                        Method loc = r.getClass().getMethod("location");
                        Object rl = loc.invoke(r);
                        return rl == null ? null : rl.toString();
                    } catch (Throwable ignored) {
                    }
                }
                return r.toString();
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static Integer tryIntGetter(Object obj, String baseName) {
        if (obj == null) return null;

        // x(), z()
        try {
            Method m = obj.getClass().getMethod(baseName);
            if (m.getReturnType() == int.class || m.getReturnType() == Integer.class) {
                return (Integer) m.invoke(obj);
            }
        } catch (Throwable ignored) {
        }

        // getX(), getZ()
        try {
            Method m = obj.getClass().getMethod("get" + Character.toUpperCase(baseName.charAt(0)) + baseName.substring(1));
            if (m.getReturnType() == int.class || m.getReturnType() == Integer.class) {
                return (Integer) m.invoke(obj);
            }
        } catch (Throwable ignored) {
        }

        // public field x / z
        try {
            var f = obj.getClass().getField(baseName);
            if (f.getType() == int.class || f.getType() == Integer.class) {
                return (Integer) f.get(obj);
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static boolean isSameDimension(Object chunkDimPos, ServerPlayer player) {
        if (chunkDimPos == null || player == null) return true;

        Object dimVal = null;

        // Try common getter names first
        for (String name : new String[]{"dimension", "getDimension", "dim", "getDim"}) {
            try {
                Method m = chunkDimPos.getClass().getMethod(name);
                dimVal = m.invoke(chunkDimPos);
                if (dimVal != null) break;
            } catch (Throwable ignored) {}
        }

        // Try common field names
        if (dimVal == null) {
            for (String fname : new String[]{"dimension", "dim"}) {
                try {
                    var f = chunkDimPos.getClass().getField(fname);
                    dimVal = f.get(chunkDimPos);
                    if (dimVal != null) break;
                } catch (Throwable ignored) {}
            }
        }

        // If we can't read dimension -> don't filter
        if (dimVal == null) return true;

        // Compare by type
        try {
            // ResourceKey<Level>
            if (dimVal.getClass().getName().equals("net.minecraft.resources.ResourceKey")) {
                return dimVal.equals(player.level.dimension());
            }

            // ResourceLocation
            if (dimVal.getClass().getName().equals("net.minecraft.resources.ResourceLocation")) {
                return dimVal.equals(player.level.dimension().location());
            }

            // String
            if (dimVal instanceof String s) {
                String want = player.level.dimension().location().toString();
                // accept exact match OR contains (some implementations stringify like "ResourceKey[minecraft:overworld]")
                return s.equals(want) || s.contains(want);
            }

            // Fallback: compare string forms leniently
            String got = dimVal.toString();
            String want = player.level.dimension().location().toString();
            return got.equals(want) || got.contains(want);

        } catch (Throwable ignored) {
            return true;
        }
    }

}

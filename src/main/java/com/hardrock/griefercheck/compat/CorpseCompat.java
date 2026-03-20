package com.hardrock.griefercheck.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.ModList;

public final class CorpseCompat {

    public static final String MODID = "corpse";
    private static final ResourceLocation CORPSE_TYPE = ResourceLocation.tryParse("corpse:corpse");
    private static final String CORPSE_CLASS = "de.maxhenkel.corpse.entities.CorpseEntity";

    private CorpseCompat() {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MODID);
    }

    public static boolean isCorpseEntity(Entity e) {
        if (e == null) return false;

        // Registry-name check (preferred)
        try {
            if (CORPSE_TYPE != null) {
                ResourceLocation key = e.getType().getRegistryName();
                if (CORPSE_TYPE.equals(key)) return true;
            }
        } catch (Throwable ignored) {}

        // Fallback: class-name check (soft dependency)
        return e.getClass().getName().equals(CORPSE_CLASS);
    }
}

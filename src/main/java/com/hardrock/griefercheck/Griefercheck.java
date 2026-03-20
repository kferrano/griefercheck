package com.hardrock.griefercheck;

import com.hardrock.griefercheck.commands.GCCommands;
import com.hardrock.griefercheck.config.GCConfig;
import com.hardrock.griefercheck.events.GCEventHandlers;
import com.hardrock.griefercheck.logging.GCLogger;
import com.hardrock.griefercheck.tracking.ContainerTracker;
import com.mojang.logging.LogUtils;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;


@Mod("griefercheck")
public class Griefercheck {

    public static final String MODID = "griefercheck";
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Initializes mod; registers config, logger, tracker, handlers, commands
     */
    public Griefercheck() {
        LOGGER.info("[GrieferCheck] Initializing {}", MODID);
        LOGGER.info(
                "[GrieferCheck] Developed by Kindling Mod Developement / Rough Day Games (Ricardo Zimpel alias Klaus_Ferrano)"
        );
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, GCConfig.SPEC);

        GCLogger.init();
        ContainerTracker.init();

        MinecraftForge.EVENT_BUS.register(new GCEventHandlers());
        MinecraftForge.EVENT_BUS.register(new GCCommands());
    }
}

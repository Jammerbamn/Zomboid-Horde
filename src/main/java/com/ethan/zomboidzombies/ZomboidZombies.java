package com.ethan.zomboidzombies;

import com.ethan.zomboidzombies.config.ModConfig;
import com.ethan.zomboidzombies.event.ZombieBehaviorEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = ZomboidZombies.MOD_ID,
    name = ZomboidZombies.NAME,
    version = ZomboidZombies.VERSION,
    acceptedMinecraftVersions = "[1.12.2]",
    dependencies = "required-after:forge@[14.23.5.2859,)"
)
public final class ZomboidZombies {
    public static final String MOD_ID = "zomboidzombies";
    public static final String NAME = "Zomboid Zombies";
    public static final String VERSION = "0.1.0";

    public static Logger logger;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        ModConfig.load(event.getSuggestedConfigurationFile());
        MinecraftForge.EVENT_BUS.register(new ZombieBehaviorEvents());
        logger.info("Loaded PZ-inspired behavior for vanilla zombies.");
    }
}

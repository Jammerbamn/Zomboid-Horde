package com.jammerbam.zomboid;

import com.jammerbam.zomboid.command.ZomboidCommand;
import com.jammerbam.zomboid.config.ModConfig;
import com.jammerbam.zomboid.compat.AiImprovementsCompatibility;
import com.jammerbam.zomboid.event.PopulationEvents;
import com.jammerbam.zomboid.event.ZombieBehaviorEvents;
import com.jammerbam.zomboid.event.SoundSimulationEvents;
import com.jammerbam.zomboid.event.NavigationEvents;
import com.jammerbam.zomboid.population.HordeDefinitions;
import com.jammerbam.zomboid.variation.ZombieVariationDefinitions;
import com.jammerbam.zomboid.entity.ModEntities;
import com.jammerbam.zomboid.network.ZomboidNetwork;
import com.jammerbam.zomboid.performance.ServerTpsMonitor;
import com.jammerbam.zomboid.performance.VanillaEntityWorkSampler;
import com.jammerbam.zomboid.proxy.CommonProxy;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import org.apache.logging.log4j.Logger;

import java.io.File;

@Mod(
    modid = Zomboid.MOD_ID,
    name = Zomboid.NAME,
    version = Zomboid.VERSION,
    acceptedMinecraftVersions = "[1.12.2]",
    dependencies = "required-after:forge@[14.23.5.2847,)"
)
public final class Zomboid {
    public static final String MOD_ID = "zomboid";
    public static final String NAME = "Zomboid";
    public static final String VERSION = "0.1.0";

    @Mod.Instance(MOD_ID)
    public static Zomboid instance;

    @net.minecraftforge.fml.common.SidedProxy(
        clientSide = "com.jammerbam.zomboid.proxy.ClientProxy",
        serverSide = "com.jammerbam.zomboid.proxy.CommonProxy"
    )
    public static CommonProxy proxy;

    public static Logger logger;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        AiImprovementsCompatibility.detect();
        ZomboidNetwork.init();
        ModEntities.register();
        proxy.preInit();
        File modConfigDirectory =
            new File(event.getModConfigurationDirectory(), MOD_ID);
        ModConfig.load(new File(modConfigDirectory, MOD_ID + ".cfg"));
        HordeDefinitions.load(
            event.getModConfigurationDirectory(),
            ModConfig.hordeFrequencyPercentPerChunk,
            ModConfig.hordeDefinitionFiles,
            logger
        );
        ZombieVariationDefinitions.load(
            event.getModConfigurationDirectory(),
            logger
        );
        MinecraftForge.EVENT_BUS.register(new ZombieBehaviorEvents());
        MinecraftForge.EVENT_BUS.register(PopulationEvents.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new SoundSimulationEvents());
        MinecraftForge.EVENT_BUS.register(new NavigationEvents());
        MinecraftForge.EVENT_BUS.register(ServerTpsMonitor.INSTANCE);
        logger.info("Loaded PZ-inspired behavior for vanilla zombies.");
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        ZombieVariationDefinitions.validateRegistries();
        HordeDefinitions.validateRegistries(ZombieVariationDefinitions.get());
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        ServerTpsMonitor.INSTANCE.reset();
        event.registerServerCommand(new ZomboidCommand());
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        VanillaEntityWorkSampler.stopAndLog();
    }
}

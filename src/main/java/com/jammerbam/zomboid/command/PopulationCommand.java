package com.jammerbam.zomboid.command;

import com.jammerbam.zomboid.Zomboid;
import com.jammerbam.zomboid.config.ModConfig;
import com.jammerbam.zomboid.event.PopulationEvents;
import com.jammerbam.zomboid.population.HordeDefinitions;
import com.jammerbam.zomboid.population.HordeRecord;
import com.jammerbam.zomboid.population.PopulationSaveResetter;
import com.jammerbam.zomboid.population.PopulationTags;
import com.jammerbam.zomboid.population.ZombiePopulationData;
import com.jammerbam.zomboid.variation.ZombieVariationDefinitions;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.Loader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PopulationCommand extends CommandBase {
    @Override
    public String getName() {
        return "population";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/zomboid population <stats|regenerate>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args, BlockPos targetPos) {
        return args.length == 1
            ? getListOfStringsMatchingLastWord(args, "stats", "regenerate")
            : Collections.<String>emptyList();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
        throws CommandException {
        if (args.length == 1 && "regenerate".equalsIgnoreCase(args[0])) {
            regenerate(server, sender);
            return;
        }
        if (args.length > 1 || (args.length == 1 && !"stats".equalsIgnoreCase(args[0]))) {
            throw new CommandException(getUsage(sender));
        }
        if (!(sender.getEntityWorld() instanceof WorldServer)) {
            throw new CommandException("commands.generic.notFound");
        }

        WorldServer world = (WorldServer) sender.getEntityWorld();
        ZombiePopulationData data = ZombiePopulationData.get(world);
        int loadedManaged = 0;
        for (Entity entity : world.loadedEntityList) {
            if (PopulationTags.isManaged(entity)) {
                loadedManaged++;
            }
        }

        sender.sendMessage(new TextComponentString(
            "Zomboid population: dimension=" + world.provider.getDimension()
                + ", enabled=" + ModConfig.enableSeededPopulation
                + ", regionSize=" + data.getRegionSizeChunks() + " chunks"
        ));
        sender.sendMessage(new TextComponentString(
            "regions=" + data.getInitializedRegionCount()
                + ", hordes=" + data.getHordeCount()
                + ", materialized=" + data.getMaterializedCount()
                + ", dead=" + data.getDeadCount()
                + ", loadedManaged=" + loadedManaged
        ));

        BlockPos position = sender.getPosition();
        int chunkX = Math.floorDiv(position.getX(), 16);
        int chunkZ = Math.floorDiv(position.getZ(), 16);
        int regionX = Math.floorDiv(chunkX, data.getRegionSizeChunks());
        int regionZ = Math.floorDiv(chunkZ, data.getRegionSizeChunks());
        List<HordeRecord> hordes = data.getHordesInRegion(regionX, regionZ);
        if (!data.isRegionInitialized(regionX, regionZ)) {
            sender.sendMessage(new TextComponentString(
                "currentRegion=" + regionX + "," + regionZ + " is not initialized"
            ));
        } else if (hordes.isEmpty()) {
            sender.sendMessage(new TextComponentString(
                "currentRegion=" + regionX + "," + regionZ + " initialized without hordes"
            ));
        } else {
            HordeRecord closest = closestHorde(hordes, position);
            sender.sendMessage(new TextComponentString(
                "currentRegion=" + regionX + "," + regionZ
                    + ", hordes=" + hordes.size()
                    + ", closest=" + closest.getGroupId()
                    + ", type=" + closest.getDefinitionId()
                    + ", plannedSize=" + closest.getPlannedSize()
                    + ", center=" + closest.getCenterX() + "," + closest.getCenterZ()
            ));
        }
    }

    private static void regenerate(MinecraftServer server, ICommandSender sender)
        throws CommandException {
        File configDirectory = Loader.instance().getConfigDir();
        File modConfigDirectory = new File(configDirectory, Zomboid.MOD_ID);
        try {
            ModConfig.load(new File(modConfigDirectory, Zomboid.MOD_ID + ".cfg"));
            boolean variationsLoaded =
                ZombieVariationDefinitions.load(configDirectory, Zomboid.logger);
            boolean hordesLoaded = HordeDefinitions.load(
                configDirectory,
                ModConfig.hordeFrequencyPercentPerChunk,
                ModConfig.hordeDefinitionFiles,
                Zomboid.logger
            );
            if (!variationsLoaded || !hordesLoaded) {
                throw new CommandException(
                    "Could not reload one or more population XML files. Check latest.log; "
                        + "the population was not cleared."
                );
            }
            ZombieVariationDefinitions.validateRegistries();
            HordeDefinitions.validateRegistries(ZombieVariationDefinitions.get());
        } catch (CommandException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            Zomboid.logger.error("Could not reload population configuration.", exception);
            throw new CommandException(
                "Could not reload the Zomboid configuration. Check latest.log."
            );
        }

        List<WorldServer> loadedWorlds = new ArrayList<>();
        Set<Path> loadedDataDirectories = new HashSet<>();
        Path worldRoot = null;
        for (WorldServer world : DimensionManager.getWorlds()) {
            if (world == null) {
                continue;
            }
            loadedWorlds.add(world);
            loadedDataDirectories.add(
                world.getChunkSaveLocation().toPath().resolve("data")
                    .toAbsolutePath().normalize()
            );
            if (world.provider.getDimension() == 0) {
                worldRoot = world.getSaveHandler().getWorldDirectory().toPath();
            }
        }
        if (worldRoot == null) {
            WorldServer overworld = server.getWorld(0);
            if (overworld == null) {
                throw new CommandException("Could not resolve the active world save directory.");
            }
            worldRoot = overworld.getSaveHandler().getWorldDirectory().toPath();
        }

        PopulationSaveResetter.ResetPlan offlineReset;
        try {
            offlineReset = PopulationSaveResetter.prepare(worldRoot, loadedDataDirectories);
        } catch (IOException exception) {
            Zomboid.logger.error("Could not inspect saved Zomboid population ledgers.", exception);
            throw new CommandException(
                "Could not inspect every saved population ledger. Nothing was cleared; "
                    + "check latest.log."
            );
        }

        int dimensions = 0;
        int removedZombies = 0;
        int removedOtherManaged = 0;
        int clearedRegions = offlineReset.getClearedRegionCount();
        int clearedHordes = offlineReset.getClearedHordeCount();
        int queuedChunks = 0;
        for (WorldServer world : loadedWorlds) {
            dimensions++;
            ZombiePopulationData data = ZombiePopulationData.get(world);
            clearedRegions += data.getInitializedRegionCount();
            clearedHordes += data.getHordeCount();

            for (Entity entity : new ArrayList<>(world.loadedEntityList)) {
                boolean zombie = entity instanceof EntityZombie;
                boolean managed = PopulationTags.isManaged(entity);
                if (!zombie && !managed) {
                    continue;
                }
                entity.setDead();
                if (zombie) {
                    removedZombies++;
                } else {
                    removedOtherManaged++;
                }
            }

            data.resetForRegeneration();
            world.getPerWorldStorage().saveAllData();
            queuedChunks += PopulationEvents.INSTANCE.resetAndQueueLoadedChunks(world);
        }

        try {
            offlineReset.apply();
        } catch (IOException exception) {
            Zomboid.logger.error("Could not clear every saved Zomboid population ledger.", exception);
            throw new CommandException(
                "Loaded population ledgers were cleared, but one or more offline dimension "
                    + "ledgers could not be reset. Check latest.log."
            );
        }

        sender.sendMessage(new TextComponentString(
            "Zomboid population regenerated across " + dimensions + " loaded dimension(s) and "
                + offlineReset.getOfflineLedgerCount() + " offline ledger(s): "
                + removedZombies + " zombies and " + removedOtherManaged
                + " other managed mobs removed, " + clearedRegions + " regions and "
                + clearedHordes + " hordes cleared, " + queuedChunks
                + " loaded chunks queued for respawn."
        ));
        if (!ModConfig.enableSeededPopulation) {
            sender.sendMessage(new TextComponentString(
                "Seeded population is disabled; nodes were cleared but no chunks were queued."
            ));
        }
    }

    private static HordeRecord closestHorde(List<HordeRecord> hordes, BlockPos position) {
        HordeRecord closest = hordes.get(0);
        long closestDistance = Long.MAX_VALUE;
        for (HordeRecord horde : hordes) {
            long dx = (long) horde.getCenterX() - position.getX();
            long dz = (long) horde.getCenterZ() - position.getZ();
            long distance = dx * dx + dz * dz;
            if (distance < closestDistance) {
                closest = horde;
                closestDistance = distance;
            }
        }
        return closest;
    }
}

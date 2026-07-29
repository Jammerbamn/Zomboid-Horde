package com.ethan.zomboidzombies.command;

import com.ethan.zomboidzombies.config.ModConfig;
import com.ethan.zomboidzombies.population.HordeRecord;
import com.ethan.zomboidzombies.population.PopulationTags;
import com.ethan.zomboidzombies.population.ZombiePopulationData;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;

public final class PopulationCommand extends CommandBase {
    @Override
    public String getName() {
        return "zzpopulation";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/zzpopulation stats";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
        throws CommandException {
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
            if (entity instanceof EntityZombie && PopulationTags.isManaged(entity)) {
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
        HordeRecord horde = data.getHorde(regionX, regionZ);
        if (!data.isRegionInitialized(regionX, regionZ)) {
            sender.sendMessage(new TextComponentString(
                "currentRegion=" + regionX + "," + regionZ + " is not initialized"
            ));
        } else if (horde == null) {
            sender.sendMessage(new TextComponentString(
                "currentRegion=" + regionX + "," + regionZ + " initialized without a horde"
            ));
        } else {
            sender.sendMessage(new TextComponentString(
                "currentHorde=" + horde.getGroupId()
                    + ", plannedSize=" + horde.getPlannedSize()
                    + ", center=" + horde.getCenterX() + "," + horde.getCenterZ()
            ));
        }
    }
}

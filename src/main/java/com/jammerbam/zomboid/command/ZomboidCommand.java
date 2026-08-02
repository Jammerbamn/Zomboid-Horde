package com.jammerbam.zomboid.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Single registered root for every Zomboid operator command. */
public final class ZomboidCommand extends CommandBase {
    private static final SoundCommand SOUND = new SoundCommand();
    private static final PopulationCommand POPULATION = new PopulationCommand();

    @Override
    public String getName() {
        return "zomboid";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/zomboid <sound|population>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
        throws CommandException {
        if (args.length == 0) {
            throw new CommandException(getUsage(sender));
        }

        String[] childArguments = Arrays.copyOfRange(args, 1, args.length);
        if ("sound".equalsIgnoreCase(args[0])) {
            SOUND.execute(server, sender, childArguments);
            return;
        }
        if ("population".equalsIgnoreCase(args[0])) {
            POPULATION.execute(server, sender, childArguments);
            return;
        }
        throw new CommandException(getUsage(sender));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args, BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "sound", "population");
        }
        if (args.length < 2) {
            return Collections.emptyList();
        }

        String[] childArguments = Arrays.copyOfRange(args, 1, args.length);
        if ("sound".equalsIgnoreCase(args[0])) {
            return SOUND.getTabCompletions(server, sender, childArguments, targetPos);
        }
        if ("population".equalsIgnoreCase(args[0])) {
            return POPULATION.getTabCompletions(server, sender, childArguments, targetPos);
        }
        return Collections.emptyList();
    }
}

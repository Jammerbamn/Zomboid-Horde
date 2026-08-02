package com.jammerbam.zomboid.command;

import com.jammerbam.zomboid.behavior.NoiseManager;
import com.jammerbam.zomboid.config.ModConfig;
import com.jammerbam.zomboid.sound.SoundType;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import java.util.Collections;
import java.util.List;

public final class SoundCommand extends CommandBase {
    @Override
    public String getName() {
        return "sound";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/zomboid sound <realisticSimulation true|false|debug on|off|pulse|status|emit [strength]>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                          String[] args, BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(
                args, "status", "realisticSimulation", "debug", "pulse", "emit"
            );
        }
        if (args.length == 2 && "realisticSimulation".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "true", "false");
        }
        if (args.length == 2 && "debug".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "on", "off");
        }
        return Collections.emptyList();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
        throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            player.sendMessage(new TextComponentString(NoiseManager.status(player.world)));
            return;
        }
        if ("realisticSimulation".equalsIgnoreCase(args[0])) {
            if (args.length == 1) {
                player.sendMessage(new TextComponentString(
                    "realisticSimulation=" + ModConfig.realisticSimulation
                ));
                return;
            }
            if (args.length != 2
                || !("true".equalsIgnoreCase(args[1])
                    || "false".equalsIgnoreCase(args[1]))) {
                throw new CommandException(getUsage(sender));
            }
            boolean enabled = Boolean.parseBoolean(args[1]);
            ModConfig.setRealisticSimulation(enabled);
            NoiseManager.clearAll();
            player.sendMessage(new TextComponentString(
                "realisticSimulation=" + enabled
                    + ". Active sound fields were cleared and the Forge config was saved."
            ));
            return;
        }
        if ("debug".equalsIgnoreCase(args[0]) && args.length == 2) {
            boolean enabled;
            if ("on".equalsIgnoreCase(args[1])) {
                enabled = true;
            } else if ("off".equalsIgnoreCase(args[1])) {
                enabled = false;
            } else {
                throw new CommandException(getUsage(sender));
            }
            NoiseManager.setDebug(player, enabled);
            player.sendMessage(new TextComponentString(
                "Sound visualization " + (enabled ? "enabled" : "disabled") + "."
            ));
            return;
        }
        if ("pulse".equalsIgnoreCase(args[0]) && args.length == 1) {
            boolean rendered = NoiseManager.visualize(player);
            player.sendMessage(new TextComponentString(
                rendered ? NoiseManager.status(player.world) : "No active sound field to draw."
            ));
            return;
        }
        if ("emit".equalsIgnoreCase(args[0]) && args.length <= 2) {
            double strength = args.length == 2
                ? parseDouble(args[1], 1.0D, 128.0D)
                : ModConfig.blockBreakNoiseRadius;
            NoiseManager.recordNoise(
                player.world,
                player.getPosition(),
                strength,
                ModConfig.noiseLifetimeTicks,
                SoundType.DEBUG,
                player.getUniqueID()
            );
            player.sendMessage(new TextComponentString(
                "Emitted debug sound with strength " + strength + "."
            ));
            return;
        }
        throw new CommandException(getUsage(sender));
    }
}

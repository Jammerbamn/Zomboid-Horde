package com.jammerbam.zomboid.sound;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class BlockAcousticCosts {
    private BlockAcousticCosts() {
    }

    public static double cost(World world, BlockPos position) {
        return cost(world, position, AcousticProfile.NEUTRAL);
    }

    public static double cost(World world, BlockPos position,
                              AcousticProfile profile) {
        if (!world.isBlockLoaded(position)) {
            return Double.POSITIVE_INFINITY;
        }
        IBlockState state = world.getBlockState(position);
        Material material = state.getMaterial();
        Block block = state.getBlock();
        boolean passageBlock = block instanceof BlockDoor
            || block instanceof BlockTrapDoor
            || block instanceof BlockFenceGate;
        boolean openPassage = block instanceof BlockDoor
            && state.getValue(BlockDoor.OPEN)
            || block instanceof BlockTrapDoor
                && state.getValue(BlockTrapDoor.OPEN)
            || block instanceof BlockFenceGate
                && state.getValue(BlockFenceGate.OPEN);
        if (material == Material.AIR || material == Material.FIRE
            || material == Material.PORTAL || openPassage
            || !passageBlock && block.isPassable(world, position)) {
            return 1.0D;
        }
        double baseCost;
        if (material == Material.WATER) {
            baseCost = 2.0D;
        } else if (material == Material.LEAVES || material == Material.PLANTS
            || material == Material.VINE || material == Material.CARPET
            || material == Material.WEB) {
            baseCost = 2.5D;
        } else if (material == Material.GLASS || material == Material.ICE) {
            baseCost = 4.0D;
        } else if (material == Material.WOOD || material == Material.CLOTH
            || material == Material.GOURD) {
            baseCost = 5.0D;
        } else if (material == Material.GROUND || material == Material.GRASS
            || material == Material.SAND || material == Material.CLAY
            || material == Material.SNOW || material == Material.CRAFTED_SNOW) {
            baseCost = 7.0D;
        } else if (material == Material.ROCK || material == Material.PISTON
            || material == Material.PACKED_ICE) {
            baseCost = 9.0D;
        } else if (material == Material.IRON || material == Material.ANVIL
            || material == Material.BARRIER) {
            baseCost = 12.0D;
        } else {
            baseCost = 6.0D;
        }
        AcousticProfile selected = profile == null ? AcousticProfile.NEUTRAL : profile;
        return baseCost * selected.materialCostMultiplier(material);
    }

    public static double simpleOcclusionMultiplier(Material material) {
        return simpleOcclusionMultiplier(material, AcousticProfile.NEUTRAL);
    }

    public static double simpleOcclusionMultiplier(Material material,
                                                    AcousticProfile profile) {
        double baseTransmission;
        if (material == Material.AIR || material == Material.FIRE
            || material == Material.PORTAL) {
            baseTransmission = 1.0D;
        } else if (material == Material.LEAVES || material == Material.PLANTS
            || material == Material.VINE || material == Material.CARPET
            || material == Material.WEB) {
            baseTransmission = 0.8D;
        } else if (material == Material.GLASS || material == Material.ICE) {
            baseTransmission = 0.7D;
        } else if (material == Material.WATER) {
            baseTransmission = 0.65D;
        } else if (material == Material.WOOD || material == Material.CLOTH
            || material == Material.GOURD) {
            baseTransmission = 0.45D;
        } else if (material == Material.GROUND || material == Material.GRASS
            || material == Material.SAND || material == Material.CLAY
            || material == Material.SNOW || material == Material.CRAFTED_SNOW) {
            baseTransmission = 0.35D;
        } else if (material == Material.ROCK || material == Material.PISTON
            || material == Material.PACKED_ICE) {
            baseTransmission = 0.3D;
        } else if (material == Material.IRON || material == Material.ANVIL
            || material == Material.BARRIER) {
            baseTransmission = 0.2D;
        } else {
            baseTransmission = 0.4D;
        }
        AcousticProfile selected = profile == null ? AcousticProfile.NEUTRAL : profile;
        return selected.adjustTransmission(baseTransmission, material);
    }
}

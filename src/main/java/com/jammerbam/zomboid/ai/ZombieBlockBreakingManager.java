package com.jammerbam.zomboid.ai;

import com.jammerbam.zomboid.Zomboid;
import com.jammerbam.zomboid.ai.navigation.NavigationManager;
import com.jammerbam.zomboid.ai.navigation.SharedNavigationManager;
import com.jammerbam.zomboid.ai.navigation.VanillaNavigationOwnership;
import com.jammerbam.zomboid.config.ModConfig;
import com.jammerbam.zomboid.behavior.TargetMemory;
import com.jammerbam.zomboid.variation.VariationTags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Plans and executes direct or underground breaches during live player pursuit. */
public final class ZombieBlockBreakingManager {
    private static final int ATTEMPT_INTERVAL_TICKS = 10;
    private static final int PURSUIT_STALL_TICKS = 40;
    private static final int MAXIMUM_WALL_THICKNESS = 8;
    private static final double MAXIMUM_REACH_SQ = 6.25D;
    private static final Map<EntityZombie, BreakingState> STATES = new WeakHashMap<>();
    private static final Map<World, DiggingStats> STATS = new WeakHashMap<>();

    private ZombieBlockBreakingManager() {
    }

    public static void observeCapability(EntityZombie zombie) {
        DiggingStats stats = stats(zombie.world);
        stats.zombiesObserved++;
        if (VariationTags.getBlockBreakingLevel(zombie) > 0) {
            stats.capableZombiesObserved++;
        } else if (!VariationTags.getVariationId(zombie).isEmpty()) {
            stats.nonBreakingVariationsObserved++;
        }
    }

    public static void tick(EntityZombie zombie) {
        int level = VariationTags.getBlockBreakingLevel(zombie);
        if (level <= 0 || !zombie.isEntityAlive()) {
            forget(zombie);
            return;
        }
        DiggingObjective objective = findObjective(zombie);
        if (objective == null) {
            forget(zombie);
            return;
        }
        DiggingStats stats = stats(zombie.world);
        stats.objectiveTicks++;
        if (objective.remembered) {
            stats.rememberedObjectiveTicks++;
        } else {
            stats.liveObjectiveTicks++;
        }

        BreakingState state = STATES.computeIfAbsent(zombie, ignored -> new BreakingState());
        if (state.playerId != null && !state.playerId.equals(objective.playerId)) {
            cancel(zombie, state, false);
        }
        if (state.target != null) {
            updateBreaking(zombie, state, level);
            return;
        }
        if (state.hasPlan()) {
            startNextBlock(zombie, state, level);
            return;
        }

        long now = zombie.world.getTotalWorldTime();
        boolean stalled = state.observeProgress(zombie, objective.position, now);
        if ((!zombie.collidedHorizontally && !stalled) || now < state.nextAttemptAt) {
            return;
        }
        if (zombie.collidedHorizontally) {
            stats.collisionTriggers++;
        } else {
            stats.stallTriggers++;
        }
        stats.planAttempts++;
        state.nextAttemptAt = now + ATTEMPT_INTERVAL_TICKS;
        EnumFacing direction = directionToward(zombie, objective.position);
        BreachPlan plan = choosePlan(zombie, level, direction, stats);
        if (plan != null) {
            stats.plansAccepted++;
            state.playerId = objective.playerId;
            state.route.addAll(plan.blocks);
            state.underground = plan.underground;
            startNextBlock(zombie, state, level);
        }
    }

    /** Gives an active breach temporary ownership of pursuit movement. */
    public static boolean steer(EntityZombie zombie, EntityPlayer player, double speed) {
        BreakingState state = STATES.get(zombie);
        if (state == null || state.playerId == null
            || !state.playerId.equals(player.getUniqueID())
            || state.target == null && !state.hasPlan()) {
            return false;
        }
        return steerActivePlan(zombie, state, speed);
    }

    /** Continues a breach whose objective is a remembered player's last known position. */
    public static boolean steerRemembered(EntityZombie zombie, double speed) {
        BreakingState state = STATES.get(zombie);
        UUID rememberedPlayerId = TargetMemory.recallPlayerId(zombie);
        if (state == null || rememberedPlayerId == null
            || state.playerId == null || !state.playerId.equals(rememberedPlayerId)
            || state.target == null && !state.hasPlan()) {
            return false;
        }
        return steerActivePlan(zombie, state, speed);
    }

    private static boolean steerActivePlan(EntityZombie zombie, BreakingState state,
                                           double speed) {
        SharedNavigationManager.stopSteering(zombie);
        VanillaNavigationOwnership.release(zombie.getNavigator());
        if (state.target != null) {
            zombie.getMoveHelper().setMoveTo(
                zombie.posX, zombie.posY, zombie.posZ, 0.0D
            );
            return true;
        }
        BlockPos next = state.route.peekFirst();
        zombie.getMoveHelper().setMoveTo(
            next.getX() + 0.5D, next.getY(), next.getZ() + 0.5D, speed
        );
        return true;
    }

    @Nullable
    private static DiggingObjective findObjective(EntityZombie zombie) {
        EntityLivingBase target = zombie.getAttackTarget();
        if (target instanceof EntityPlayer && isAttackablePlayer((EntityPlayer) target)) {
            EntityPlayer player = (EntityPlayer) target;
            return new DiggingObjective(
                player.getUniqueID(), player.getPosition(), false
            );
        }
        BlockPos rememberedPosition = TargetMemory.recall(zombie);
        UUID rememberedPlayerId = TargetMemory.recallPlayerId(zombie);
        return rememberedPosition == null || rememberedPlayerId == null
            ? null : new DiggingObjective(
                rememberedPlayerId, rememberedPosition, true
            );
    }

    public static void forget(EntityZombie zombie) {
        BreakingState state = STATES.remove(zombie);
        if (state != null && state.target != null) {
            zombie.world.sendBlockBreakProgress(zombie.getEntityId(), state.target, -1);
        }
    }

    public static void clear(World world) {
        Iterator<Map.Entry<EntityZombie, BreakingState>> iterator =
            STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<EntityZombie, BreakingState> entry = iterator.next();
            EntityZombie zombie = entry.getKey();
            if (zombie == null || zombie.world == world) {
                if (zombie != null && entry.getValue().target != null) {
                    world.sendBlockBreakProgress(
                        zombie.getEntityId(), entry.getValue().target, -1
                    );
                }
                iterator.remove();
            }
        }
        DiggingStats stats = STATS.remove(world);
        if (stats != null) {
            Zomboid.logger.info(
                "Zombie digging closed for dimension {}: zombiesObserved={} "
                    + "[capable={}, nonBreakingVariations={}], objectiveTicks={} "
                    + "[live={}, remembered={}], triggers={} [collision={}, stall={}], "
                    + "planAttempts={}, noBlockingWall={}, noBreakablePlan={}, "
                    + "plansAccepted={}, reachWaitTicks={}, blocksStarted={}, "
                    + "blocksCompleted={}, startDenied={}",
                world.provider.getDimension(),
                stats.zombiesObserved,
                stats.capableZombiesObserved,
                stats.nonBreakingVariationsObserved,
                stats.objectiveTicks,
                stats.liveObjectiveTicks,
                stats.rememberedObjectiveTicks,
                stats.collisionTriggers + stats.stallTriggers,
                stats.collisionTriggers,
                stats.stallTriggers,
                stats.planAttempts,
                stats.noBlockingWall,
                stats.noBreakablePlan,
                stats.plansAccepted,
                stats.reachWaitTicks,
                stats.blocksStarted,
                stats.blocksCompleted,
                stats.startDenied
            );
        }
    }

    @Nullable
    private static BreachPlan choosePlan(EntityZombie zombie, int level,
                                         EnumFacing direction, DiggingStats stats) {
        BlockPos feet = new BlockPos(
            zombie.posX, zombie.getEntityBoundingBox().minY + 0.1D, zombie.posZ
        );
        int thickness = wallThickness(zombie.world, feet, direction);
        if (thickness <= 0) {
            stats.noBlockingWall++;
            return null;
        }

        BreachPlan direct = buildDirectPlan(zombie, level, feet, direction, thickness);
        BreachPlan underground = buildUndergroundPlan(
            zombie, level, feet, direction, thickness
        );
        long directTicks = direct == null ? -1L : direct.totalTicks;
        long undergroundTicks = underground == null ? -1L : underground.totalTicks;
        BreachPlan selected = BlockBreakingRules.shouldUseUnderground(
            directTicks, undergroundTicks
        ) ? underground : direct;
        if (selected == null) {
            stats.noBreakablePlan++;
        }
        return selected;
    }

    private static int wallThickness(World world, BlockPos feet, EnumFacing direction) {
        for (int distance = 1; distance <= MAXIMUM_WALL_THICKNESS + 1; distance++) {
            BlockPos column = feet.offset(direction, distance);
            if (!isCollisionBlock(world, column) && !isCollisionBlock(world, column.up())) {
                return distance - 1;
            }
        }
        return -1;
    }

    @Nullable
    private static BreachPlan buildDirectPlan(EntityZombie zombie, int level,
                                               BlockPos feet, EnumFacing direction,
                                               int thickness) {
        List<BlockPos> blocks = new ArrayList<>();
        for (int distance = 1; distance <= thickness; distance++) {
            BlockPos column = feet.offset(direction, distance);
            addIfSolid(blocks, zombie.world, column);
            addIfSolid(blocks, zombie.world, column.up());
        }
        return pricePlan(zombie, level, blocks, false, thickness + 1);
    }

    @Nullable
    private static BreachPlan buildUndergroundPlan(EntityZombie zombie, int level,
                                                    BlockPos feet, EnumFacing direction,
                                                    int thickness) {
        if (!hasSafeSupport(zombie.world, feet.down(3))) {
            return null;
        }
        List<BlockPos> blocks = new ArrayList<>();
        addIfSolid(blocks, zombie.world, feet.down());
        addIfSolid(blocks, zombie.world, feet.down(2));
        for (int distance = 1; distance <= thickness; distance++) {
            BlockPos column = feet.offset(direction, distance);
            if (!hasSafeSupport(zombie.world, column.down(3))) {
                return null;
            }
            addIfSolid(blocks, zombie.world, column.down(2));
            addIfSolid(blocks, zombie.world, column.down());
        }
        BlockPos exit = feet.offset(direction, thickness + 1);
        if (!hasSafeSupport(zombie.world, exit.down(2))) {
            return null;
        }
        addIfSolid(blocks, zombie.world, exit.down());
        return pricePlan(zombie, level, blocks, true, thickness + 4);
    }

    private static boolean hasSafeSupport(World world, BlockPos position) {
        if (!world.isBlockLoaded(position, false)) {
            return false;
        }
        IBlockState state = world.getBlockState(position);
        Material material = state.getMaterial();
        return isCollisionBlock(world, position)
            && material != Material.WATER && material != Material.LAVA;
    }

    @Nullable
    private static BreachPlan pricePlan(EntityZombie zombie, int level,
                                        List<BlockPos> rawBlocks,
                                        boolean underground, int traversalBlocks) {
        Set<BlockPos> unique = new LinkedHashSet<>(rawBlocks);
        List<BlockPos> blocks = new ArrayList<>(unique.size());
        long total = 0L;
        for (BlockPos position : unique) {
            IBlockState state = zombie.world.getBlockState(position);
            int duration = durationFor(zombie, position, state, level);
            if (duration < 0) {
                return null;
            }
            blocks.add(position.toImmutable());
            total += duration;
        }
        return blocks.isEmpty() ? null : new BreachPlan(
            blocks, BlockBreakingRules.totalPlanTicks(total, traversalBlocks), underground
        );
    }

    private static void addIfSolid(List<BlockPos> blocks, World world, BlockPos position) {
        if (isCollisionBlock(world, position)) {
            blocks.add(position);
        }
    }

    private static void startNextBlock(EntityZombie zombie, BreakingState breaking,
                                       int level) {
        while (breaking.hasPlan()) {
            BlockPos next = breaking.route.peekFirst();
            if (!isCollisionBlock(zombie.world, next)) {
                breaking.route.removeFirst();
                continue;
            }
            if (zombie.getDistanceSqToCenter(next) > MAXIMUM_REACH_SQ) {
                stats(zombie.world).reachWaitTicks++;
                return;
            }
            breaking.route.removeFirst();
            if (!beginBreaking(zombie, breaking, next, level)) {
                cancel(zombie, breaking, false);
            }
            return;
        }
        breaking.finishPlan(zombie.world.getTotalWorldTime() + ATTEMPT_INTERVAL_TICKS);
    }

    private static boolean beginBreaking(EntityZombie zombie, BreakingState breaking,
                                         BlockPos target, int level) {
        IBlockState state = zombie.world.getBlockState(target);
        int duration = durationFor(zombie, target, state, level);
        if (duration < 0
            || !ForgeEventFactory.getMobGriefingEvent(zombie.world, zombie)
            || !state.getBlock().canEntityDestroy(state, zombie.world, target, zombie)
            || !ForgeEventFactory.onEntityDestroyBlock(zombie, target, state)) {
            stats(zombie.world).startDenied++;
            return false;
        }
        breaking.target = target.toImmutable();
        breaking.originalState = state;
        breaking.durationTicks = duration;
        breaking.elapsedTicks = 0;
        breaking.previousProgress = -1;
        stats(zombie.world).blocksStarted++;
        return true;
    }

    private static void updateBreaking(EntityZombie zombie, BreakingState breaking,
                                       int level) {
        BlockPos target = breaking.target;
        IBlockState current = zombie.world.getBlockState(target);
        if (!current.equals(breaking.originalState)
            || zombie.getDistanceSqToCenter(target) > MAXIMUM_REACH_SQ
            || durationFor(zombie, target, current, level) < 0) {
            cancel(zombie, breaking, false);
            return;
        }

        breaking.elapsedTicks++;
        if (breaking.elapsedTicks == 1 || breaking.elapsedTicks % 10 == 0) {
            zombie.swingArm(EnumHand.MAIN_HAND);
        }
        if (breaking.elapsedTicks == 1 || breaking.elapsedTicks % 20 == 0) {
            SoundType sound = current.getBlock().getSoundType(
                current, zombie.world, target, zombie
            );
            zombie.world.playSound(
                null, target, sound.getHitSound(), SoundCategory.HOSTILE,
                (sound.getVolume() + 1.0F) / 8.0F, sound.getPitch() * 0.5F
            );
        }

        int progress = Math.min(
            9, (int) ((long) breaking.elapsedTicks * 10L / breaking.durationTicks)
        );
        if (progress != breaking.previousProgress) {
            zombie.world.sendBlockBreakProgress(zombie.getEntityId(), target, progress);
            breaking.previousProgress = progress;
        }
        if (breaking.elapsedTicks < breaking.durationTicks) {
            return;
        }

        zombie.world.sendBlockBreakProgress(zombie.getEntityId(), target, -1);
        if (zombie.world.destroyBlock(target, false)) {
            stats(zombie.world).blocksCompleted++;
            NavigationManager.invalidate(zombie.world, target);
        }
        breaking.clearBlock();
        startNextBlock(zombie, breaking, level);
    }

    private static void cancel(EntityZombie zombie, BreakingState breaking,
                               boolean retainPlan) {
        if (breaking.target != null) {
            zombie.world.sendBlockBreakProgress(zombie.getEntityId(), breaking.target, -1);
        }
        breaking.clearBlock();
        if (!retainPlan) {
            breaking.finishPlan(zombie.world.getTotalWorldTime() + ATTEMPT_INTERVAL_TICKS);
        }
    }

    private static int durationFor(EntityZombie zombie, BlockPos target,
                                   IBlockState state, int level) {
        Block block = state.getBlock();
        AxisAlignedBB collision = state.getCollisionBoundingBox(zombie.world, target);
        if (block.isAir(state, zombie.world, target)
            || (block instanceof BlockDoor && state.getMaterial() == Material.WOOD
                && !ModConfig.breakWoodenDoors)
            || collision == null || collision == Block.NULL_AABB) {
            return -1;
        }
        if (!BlockBreakingRules.canBreak(
            level, block.getHarvestTool(state), state.getMaterial().isToolNotRequired(),
            block.getHarvestLevel(state)
        )) {
            return -1;
        }
        return BlockBreakingRules.durationTicks(
            state.getBlockHardness(zombie.world, target)
        );
    }

    private static boolean isCollisionBlock(World world, BlockPos position) {
        if (!world.isBlockLoaded(position, false)
            || !world.getWorldBorder().contains(position)) {
            return false;
        }
        IBlockState state = world.getBlockState(position);
        AxisAlignedBB collision = state.getCollisionBoundingBox(world, position);
        return !state.getBlock().isAir(state, world, position)
            && collision != null && collision != Block.NULL_AABB;
    }

    private static boolean isAttackablePlayer(EntityPlayer player) {
        return player.isEntityAlive() && !player.isSpectator()
            && !player.capabilities.disableDamage;
    }

    private static EnumFacing directionToward(EntityZombie zombie, BlockPos target) {
        double dx = target.getX() + 0.5D - zombie.posX;
        double dz = target.getZ() + 0.5D - zombie.posZ;
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx >= 0.0D ? EnumFacing.EAST : EnumFacing.WEST;
        }
        return dz >= 0.0D ? EnumFacing.SOUTH : EnumFacing.NORTH;
    }

    private static final class DiggingObjective {
        private final UUID playerId;
        private final BlockPos position;
        private final boolean remembered;

        private DiggingObjective(UUID playerId, BlockPos position,
                                 boolean remembered) {
            this.playerId = playerId;
            this.position = position;
            this.remembered = remembered;
        }
    }

    private static DiggingStats stats(World world) {
        return STATS.computeIfAbsent(world, ignored -> new DiggingStats());
    }

    private static final class DiggingStats {
        private long zombiesObserved;
        private long capableZombiesObserved;
        private long nonBreakingVariationsObserved;
        private long objectiveTicks;
        private long liveObjectiveTicks;
        private long rememberedObjectiveTicks;
        private long collisionTriggers;
        private long stallTriggers;
        private long planAttempts;
        private long noBlockingWall;
        private long noBreakablePlan;
        private long plansAccepted;
        private long reachWaitTicks;
        private long blocksStarted;
        private long blocksCompleted;
        private long startDenied;
    }

    private static final class BreachPlan {
        private final List<BlockPos> blocks;
        private final long totalTicks;
        private final boolean underground;

        private BreachPlan(List<BlockPos> blocks, long totalTicks, boolean underground) {
            this.blocks = blocks;
            this.totalTicks = totalTicks;
            this.underground = underground;
        }
    }

    private static final class BreakingState {
        private final ArrayDeque<BlockPos> route = new ArrayDeque<>();
        private UUID playerId;
        private BlockPos target;
        private IBlockState originalState;
        private int durationTicks;
        private int elapsedTicks;
        private int previousProgress = -1;
        private long nextAttemptAt;
        private boolean underground;
        private double closestObjectiveDistance = Double.POSITIVE_INFINITY;
        private long lastProgressAt = Long.MIN_VALUE;

        private boolean hasPlan() {
            return !route.isEmpty();
        }

        private boolean observeProgress(EntityZombie zombie, BlockPos objective,
                                        long now) {
            double currentDistance = Math.sqrt(
                zombie.getDistanceSqToCenter(objective)
            );
            if (BlockBreakingRules.madeObjectiveProgress(
                closestObjectiveDistance, currentDistance
            )) {
                closestObjectiveDistance = currentDistance;
                lastProgressAt = now;
                return false;
            }
            return now - lastProgressAt >= PURSUIT_STALL_TICKS;
        }

        private void clearBlock() {
            target = null;
            originalState = null;
            durationTicks = 0;
            elapsedTicks = 0;
            previousProgress = -1;
        }

        private void finishPlan(long retryAt) {
            clearBlock();
            route.clear();
            playerId = null;
            underground = false;
            nextAttemptAt = retryAt;
            closestObjectiveDistance = Double.POSITIVE_INFINITY;
            lastProgressAt = Long.MIN_VALUE;
        }
    }
}

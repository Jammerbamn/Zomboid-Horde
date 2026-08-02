package com.jammerbam.zomboid.ai;

import com.jammerbam.zomboid.config.ModConfig;
import com.jammerbam.zomboid.behavior.GroupActivitySchedule;
import com.jammerbam.zomboid.population.PopulationTags;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class WanderCoordinator {
    private static final int START_STAGGER_MIN_TICKS = 10;
    private static final int START_STAGGER_MAX_TICKS = 30;
    private static final int SLOT_LEASE_TICKS = 20 * 60;
    private static final Map<World, Map<String, GroupState>> GROUPS =
        new WeakHashMap<>();

    private WanderCoordinator() {
    }

    public static boolean tryClaim(EntityZombie zombie) {
        World world = zombie.world;
        long now = world.getTotalWorldTime();
        String groupId = PopulationTags.getGroupId(zombie);
        if (groupId.isEmpty()) {
            groupId = zombie.getUniqueID().toString();
        }

        Map<String, GroupState> worldGroups =
            GROUPS.computeIfAbsent(world, ignored -> new HashMap<>());
        GroupState group = worldGroups.computeIfAbsent(groupId, ignored -> new GroupState());
        group.removeExpired(now);
        if (!GroupActivitySchedule.isDue(group.nextStartAt, now)) {
            return false;
        }

        if (group.pendingStarts <= 0) {
            group.selectedThisCycle.clear();
            int selectedActive = GroupActivitySchedule.randomCount(
                zombie.getRNG(),
                ModConfig.hordeWanderMaximumActive
            );
            group.pendingStarts = GroupActivitySchedule.remainingStarts(
                selectedActive,
                group.activeSlots.size()
            );
            if (group.pendingStarts == 0) {
                group.nextStartAt = nextSelectionAt(zombie, now);
                return false;
            }
        }

        UUID zombieId = zombie.getUniqueID();
        if (group.activeSlots.containsKey(zombieId)
            || group.selectedThisCycle.contains(zombieId)) {
            return false;
        }
        group.activeSlots.put(zombieId, now + SLOT_LEASE_TICKS);
        group.selectedThisCycle.add(zombieId);
        --group.pendingStarts;
        group.nextStartAt = group.pendingStarts > 0
            ? GroupActivitySchedule.nextAt(
                now,
                GroupActivitySchedule.randomInterval(
                    zombie.getRNG(), START_STAGGER_MIN_TICKS, START_STAGGER_MAX_TICKS
                )
            )
            : nextSelectionAt(zombie, now);
        return true;
    }

    public static void release(EntityZombie zombie) {
        Map<String, GroupState> worldGroups = GROUPS.get(zombie.world);
        if (worldGroups == null) {
            return;
        }
        String groupId = PopulationTags.getGroupId(zombie);
        if (groupId.isEmpty()) {
            groupId = zombie.getUniqueID().toString();
        }
        GroupState group = worldGroups.get(groupId);
        if (group != null) {
            group.activeSlots.remove(zombie.getUniqueID());
        }
    }

    public static void clear(World world) {
        GROUPS.remove(world);
    }

    private static long nextSelectionAt(EntityZombie zombie, long now) {
        int interval = GroupActivitySchedule.randomInterval(
            zombie.getRNG(),
            ModConfig.hordeWanderIntervalMinTicks,
            ModConfig.hordeWanderIntervalMaxTicks
        );
        return GroupActivitySchedule.nextAt(now, interval);
    }

    private static final class GroupState {
        private final Map<UUID, Long> activeSlots = new HashMap<>();
        private final Set<UUID> selectedThisCycle = new HashSet<>();
        private Long nextStartAt;
        private int pendingStarts;

        private void removeExpired(long now) {
            Iterator<Long> iterator = activeSlots.values().iterator();
            while (iterator.hasNext()) {
                if (iterator.next() <= now) {
                    iterator.remove();
                }
            }
        }
    }
}

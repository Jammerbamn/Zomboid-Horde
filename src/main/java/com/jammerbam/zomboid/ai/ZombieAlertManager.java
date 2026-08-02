package com.jammerbam.zomboid.ai;

import com.jammerbam.zomboid.config.ModConfig;
import com.jammerbam.zomboid.population.PopulationTags;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Staged, probabilistic local alert propagation without player-target sharing. */
public final class ZombieAlertManager {
    private static final Map<AlertKey, AlertEpisode> ACTIVE_EPISODES = new HashMap<>();
    private static final Map<EntityZombie, AlertEpisode> DIRECT_TARGETS = new WeakHashMap<>();
    private static final Map<EntityZombie, AlertEpisode> ORIGINS = new WeakHashMap<>();
    private static final Map<EntityZombie, AlertResponse> RESPONSES = new WeakHashMap<>();
    private static long nextEpisodeId = 1L;

    private ZombieAlertManager() {
    }

    /** Associates direct perception with one horde/player episode; only its first detector is origin. */
    public static void updateDirectTarget(EntityZombie zombie, EntityPlayer target) {
        AlertResponse inherited = RESPONSES.get(zombie);
        if (inherited != null && inherited.episode.targetId.equals(target.getUniqueID())
            && isEpisodeActive(inherited.episode)) {
            DIRECT_TARGETS.put(zombie, inherited.episode);
            return;
        }

        AlertEpisode previous = DIRECT_TARGETS.get(zombie);
        if (previous != null && previous.targetId.equals(target.getUniqueID())) {
            return;
        }
        if (previous != null) {
            DIRECT_TARGETS.remove(zombie);
            closeIfUnused(previous);
        }

        AlertKey key = new AlertKey(
            zombie.world, PopulationTags.getGroupId(zombie), target.getUniqueID()
        );
        AlertEpisode episode = ACTIVE_EPISODES.get(key);
        if (episode == null) {
            episode = new AlertEpisode(nextEpisodeId++, key, zombie);
            ACTIVE_EPISODES.put(key, episode);
            ORIGINS.put(zombie, episode);
        }
        DIRECT_TARGETS.put(zombie, episode);
    }

    public static void endDirectTarget(EntityZombie zombie) {
        AlertEpisode episode = DIRECT_TARGETS.remove(zombie);
        if (episode != null) {
            closeIfUnused(episode);
        }
    }

    /** Called from entity ticks; origins and active followers carry a local alertness radius. */
    public static void tryRecruitNearby(EntityZombie source) {
        AlertEpisode episode = carryingEpisode(source);
        if (episode == null || !isEpisodeActive(episode)) {
            return;
        }
        long now = source.world.getTotalWorldTime();
        Long nextScan = episode.nextScanByCarrier.get(source.getUniqueID());
        if (nextScan != null && now < nextScan) {
            return;
        }
        episode.nextScanByCarrier.put(
            source.getUniqueID(), now + ModConfig.hordeAlertPropagationIntervalTicks
        );

        double radius = ModConfig.hordeAlertnessRadius;
        AxisAlignedBB area = source.getEntityBoundingBox().grow(radius);
        List<EntityZombie> candidates = new ArrayList<>();
        for (EntityZombie candidate : source.world.getEntitiesWithinAABB(
            EntityZombie.class, area
        )) {
            if (candidate != source && candidate.isEntityAlive()
                && candidate.getAttackTarget() == null
                && !DIRECT_TARGETS.containsKey(candidate)
                && !ORIGINS.containsKey(candidate)
                && !RESPONSES.containsKey(candidate)) {
                candidates.add(candidate);
            }
        }
        Collections.shuffle(candidates, source.getRNG());

        for (EntityZombie candidate : candidates) {
            UUID candidateId = candidate.getUniqueID();
            if (!episode.considered.add(candidateId)) {
                continue;
            }
            if (candidate.getRNG().nextDouble() * 100.0D
                >= ModConfig.hordeAlertLookChancePercent) {
                continue;
            }

            int lookDelay = randomDelay(
                candidate,
                ModConfig.hordeAlertLookDelayMinTicks,
                ModConfig.hordeAlertLookDelayMaxTicks
            );
            int followDelay = randomDelay(
                candidate,
                ModConfig.hordeAlertFollowDelayMinTicks,
                ModConfig.hordeAlertFollowDelayMaxTicks
            );
            RESPONSES.put(candidate, new AlertResponse(
                episode, source, now + lookDelay, now + lookDelay + followDelay
            ));
        }
    }

    @Nullable
    public static EntityZombie getObservableLeader(EntityZombie follower) {
        AlertResponse response = validResponse(follower);
        if (response == null || follower.getAttackTarget() != null
            || follower.world.getTotalWorldTime() < response.lookAt) {
            return null;
        }
        boolean willFollow = resolveFollowDecision(follower, response);
        if (follower.world.getTotalWorldTime() >= response.followAt && !willFollow) {
            return null;
        }
        return response.leader;
    }

    public static boolean shouldFollow(EntityZombie follower) {
        AlertResponse response = validResponse(follower);
        return response != null
            && follower.getAttackTarget() == null
            && follower.world.getTotalWorldTime() >= response.followAt
            && Boolean.TRUE.equals(response.willFollow);
    }

    public static void forget(EntityZombie zombie) {
        AlertEpisode direct = DIRECT_TARGETS.remove(zombie);
        AlertEpisode origin = ORIGINS.remove(zombie);
        RESPONSES.remove(zombie);
        if (direct != null) {
            closeIfUnused(direct);
        }
        if (origin != null && origin != direct) {
            closeIfUnused(origin);
        }
    }

    public static void clear(World world) {
        clearKeysForWorld(DIRECT_TARGETS, world);
        clearKeysForWorld(ORIGINS, world);
        clearKeysForWorld(RESPONSES, world);
        Iterator<Map.Entry<AlertKey, AlertEpisode>> episodes =
            ACTIVE_EPISODES.entrySet().iterator();
        while (episodes.hasNext()) {
            if (episodes.next().getKey().world == world) {
                episodes.remove();
            }
        }
    }

    @Nullable
    private static AlertEpisode carryingEpisode(EntityZombie source) {
        AlertEpisode origin = ORIGINS.get(source);
        if (origin != null && DIRECT_TARGETS.get(source) == origin) {
            return origin;
        }
        AlertResponse response = validResponse(source);
        if (response != null && Boolean.TRUE.equals(response.willFollow)
            && source.world.getTotalWorldTime() >= response.followAt) {
            return response.episode;
        }
        return null;
    }

    @Nullable
    private static AlertResponse validResponse(EntityZombie follower) {
        AlertResponse response = RESPONSES.get(follower);
        if (response == null) {
            return null;
        }
        if (!isEpisodeActive(response.episode)
            || !response.leader.isEntityAlive()
            || response.leader.world != follower.world
            || !isLeaderActive(response.leader, response.episode)) {
            RESPONSES.remove(follower);
            return null;
        }
        return response;
    }

    private static boolean isLeaderActive(EntityZombie leader, AlertEpisode episode) {
        if (DIRECT_TARGETS.get(leader) == episode) {
            return true;
        }
        AlertResponse leaderResponse = RESPONSES.get(leader);
        return leaderResponse != null
            && leaderResponse.episode == episode
            && Boolean.TRUE.equals(leaderResponse.willFollow)
            && leader.world.getTotalWorldTime() >= leaderResponse.lookAt
            && leaderResponse.leader.isEntityAlive()
            && leaderResponse.leader.world == leader.world
            && isLeaderActive(leaderResponse.leader, episode);
    }

    private static boolean resolveFollowDecision(EntityZombie follower,
                                                 AlertResponse response) {
        if (response.willFollow != null) {
            return response.willFollow;
        }

        AlertEpisode episode = response.episode;
        UUID leaderId = response.leader.getUniqueID();
        int leaderRecruits = episode.recruitsByCarrier.containsKey(leaderId)
            ? episode.recruitsByCarrier.get(leaderId)
            : 0;
        boolean hasCarrierCapacity =
            leaderRecruits < ModConfig.hordeAlertMaximumRecruitsPerZombie;
        boolean hasEpisodeCapacity =
            episode.followerCount < ModConfig.hordeAlertMaximumFollowers;
        response.willFollow = hasCarrierCapacity && hasEpisodeCapacity
            && follower.getRNG().nextDouble() * 100.0D
                < ModConfig.hordeAlertFollowChancePercent;
        if (response.willFollow) {
            episode.followerCount++;
            episode.recruitsByCarrier.put(leaderId, leaderRecruits + 1);
        }
        return response.willFollow;
    }

    private static boolean isEpisodeActive(AlertEpisode episode) {
        return ACTIVE_EPISODES.get(episode.key) == episode;
    }

    private static void closeIfUnused(AlertEpisode episode) {
        for (AlertEpisode direct : DIRECT_TARGETS.values()) {
            if (direct == episode) {
                return;
            }
        }
        ACTIVE_EPISODES.remove(episode.key);
        removeEpisodeValues(ORIGINS, episode);
        removeEpisodeResponses(episode);
    }

    private static void removeEpisodeValues(Map<EntityZombie, AlertEpisode> map,
                                            AlertEpisode episode) {
        Iterator<Map.Entry<EntityZombie, AlertEpisode>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() == episode) {
                iterator.remove();
            }
        }
    }

    private static void removeEpisodeResponses(AlertEpisode episode) {
        Iterator<Map.Entry<EntityZombie, AlertResponse>> iterator =
            RESPONSES.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().episode == episode) {
                iterator.remove();
            }
        }
    }

    private static void clearKeysForWorld(Map<EntityZombie, ?> map, World world) {
        Iterator<EntityZombie> zombies = map.keySet().iterator();
        while (zombies.hasNext()) {
            EntityZombie zombie = zombies.next();
            if (zombie == null || zombie.world == world) {
                zombies.remove();
            }
        }
    }

    private static int randomDelay(EntityZombie source, int minimum, int maximum) {
        int range = maximum - minimum;
        return minimum + (range <= 0 ? 0 : source.getRNG().nextInt(range + 1));
    }

    private static final class AlertEpisode {
        private final long id;
        private final AlertKey key;
        private final UUID targetId;
        private final EntityZombie root;
        private final Set<UUID> considered = new HashSet<>();
        private final Map<UUID, Integer> recruitsByCarrier = new HashMap<>();
        private final Map<UUID, Long> nextScanByCarrier = new HashMap<>();
        private int followerCount;

        private AlertEpisode(long id, AlertKey key, EntityZombie root) {
            this.id = id;
            this.key = key;
            this.targetId = key.targetId;
            this.root = root;
        }
    }

    private static final class AlertResponse {
        private final AlertEpisode episode;
        private final EntityZombie leader;
        private final long lookAt;
        private final long followAt;
        private Boolean willFollow;

        private AlertResponse(AlertEpisode episode, EntityZombie leader,
                              long lookAt, long followAt) {
            this.episode = episode;
            this.leader = leader;
            this.lookAt = lookAt;
            this.followAt = followAt;
        }
    }

    private static final class AlertKey {
        private final World world;
        private final String groupId;
        private final UUID targetId;

        private AlertKey(World world, String groupId, UUID targetId) {
            this.world = world;
            this.groupId = groupId;
            this.targetId = targetId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof AlertKey)) {
                return false;
            }
            AlertKey key = (AlertKey) other;
            return world == key.world
                && groupId.equals(key.groupId)
                && targetId.equals(key.targetId);
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(world);
            result = 31 * result + groupId.hashCode();
            return 31 * result + targetId.hashCode();
        }
    }
}

# AI performance architecture

Status: Shared navigation, terrain cache, packed runtime, and correlated telemetry implemented

## Root cost

Minecraft 1.12.2 creates a `ChunkCache` and runs a bounded A* search for each
route request. Every entity with an active `PathNavigateGround` route also
performs its own block-aware path advancement and shortcut checks each tick.
Request throttling protects a server from bursts but does not make these
duplicated routes cheaper.

## Shared pursuit field

Zomboid owns a conservative ground-navigation surface and incremental reverse
breadth-first solver. One field is keyed to each pursued player in a world.
Covered zombies query a lower-cost adjacent cell and feed that waypoint to
`EntityMoveHelper`; they do not keep individual active `PathNavigateGround`
paths while the field can steer them.

This changes the common horde case from `N` A* searches and `N` active-path
scans into one field expansion plus cheap per-zombie map lookups. Equal-cost
choices use an entity-specific tie break so the entire group is not forced
through one identical branch.

Fields build incrementally under one per-world node budget. A completed field
remains usable while its replacement builds after the player moves. Block
placement and breaking invalidate intersecting fields; rebuilding waits until
the following tick so it reads committed block state. Fields unused for ten
seconds are released.

## Packed runtime representation

The field solver's hot path now uses Minecraft-compatible packed block
coordinates throughout. Distances live in a fixed-capacity primitive
`long -> int` hash table, the breadth-first frontier is a flat `long[]`, and
the walking surface writes neighbors into reusable arrays. Steering and cache
hits decode coordinates directly instead of allocating `BlockPos`, `Long`,
`Integer`, iterator, list, and map-entry objects for every expanded cell.

The reverse search also records a compact direction mask and one-block height
change for every reached cell. Equal-cost exits remain in the mask for
entity-specific tie breaking. Covered zombies therefore select their next cell
without re-querying neighboring terrain. Outside the eight-block close-response
radius, direction selection is staggered across entities every three ticks by
default. Minecraft 1.12.2's `EntityMoveHelper` consumes `MOVE_TO` in one tick and
then switches to `WAIT`, so Zomboid caches the selected waypoint and cheaply
reissues it on intervening ticks. Zombies move continuously without repeating
the field lookup, crowd scoring, or terrain query. A world-tick service continues
incremental field construction even when no zombie selects a new direction that tick.

This changes representation, not routing behavior. The object-based solver API
remains available for compatibility and is checked against the packed solver in
tests. The configured maximum-node limit sizes both the table and frontier, so
a field cannot grow or resize past its bound.

## Chunk-aligned terrain snapshots

The ground surface no longer asks the live world for the same block states and
collision boxes on every field rebuild or zombie steering query. Each world now
owns a lazy navigation cache keyed by Minecraft chunk. Within each chunk, exact
feet positions are stored in 16-block-high sections as either blocked or
standable, with the resolved support height retained for movement. Exact Y is
part of the key, so caves, bridges, and stacked floors remain separate layers.

Classification still happens synchronously on the server thread the first time
a cell is requested. Later fields and zombies reuse that immutable result.
Unloaded terrain is never cached as blocked. Placement, breaking, and neighbor
state notifications invalidate the edited block's head/feet/support dependencies
plus adjacent connected-block collision shapes. Chunk unload discards that
chunk's snapshot and invalidates intersecting fields; world unload releases the
entire cache and logs retained cells, classifications, hits, hit rate,
invalidations, field builds, expanded nodes, expansion time, and time per node.
The same shutdown record reports direction-steering queries, successful
queries, total steering time, and time per query separately from field builds.

This is deliberately an in-memory foundation, not a saved world navmesh yet.
It removes repeated terrain classification without introducing stale files,
world-generation stalls, or worker access to live `World` and chunk objects.

The first terrain profile supports ordinary ground movement, one-block steps,
partial-height supporting blocks, loaded chunks, and world borders. It rejects
liquids, fire, cactus, magma, fences, solid body/head cells, unloaded chunks,
and terrain outside its configured vertical range. Unsupported or not-yet-
reached terrain uses the existing budgeted vanilla navigator when fallback is
enabled.

Current defaults:

| Forge `general` setting | Default | Purpose |
| --- | ---: | --- |
| `enableSharedPursuitFlowFields` | `true` | Enables shared player-pursuit routing |
| `followRange` | `32.0` | Maximum range for custom head-directed player perception |
| `vanillaPathSearchRange` | `32.0` | Bounds vanilla `FOLLOW_RANGE` and its A* snapshot radius independently |
| `pursuitFlowFieldRadius` | `64` | Horizontal coverage in blocks |
| `pursuitFlowFieldVerticalRange` | `16` | Vertical coverage around the target |
| `pursuitFlowFieldNodesPerTick` | `4000` | Shared expansion budget per world tick |
| `pursuitFlowFieldMaximumNodes` | `24000` | Hard cells retained by one field |
| `pursuitFlowFieldRebuildDistance` | `2.0` | Target movement that requests replacement |
| `pursuitFlowFieldMinimumRebuildTicks` | `10` | Minimum rebuild interval |
| `pursuitFlowFieldSteeringIntervalTicks` | `3` | Far pursuit waypoint refresh interval |
| `pursuitFlowFieldFullRateRadius` | `8.0` | Radius where steering refreshes every tick |
| `pursuitFlowFieldVanillaFallback` | `true` | Uses budgeted vanilla routing when needed |
| `enableCohortCollisionSuppression` | `true` | Suppresses teammate pushing in directed cohorts |
| `enableCohortCollisionQueryOptimization` | `true` | Reuses the cohort entity index for movement collisions |
| `crowdCollisionMinimumCohortSize` | `4` | Pursuers required before collision optimizations engage |

## Fallback coordinator

Fallback route calculations retain a per-world TPS-aware budget. A zombie
consumes a slot only when its existing route is stale or missing, and denied
requests receive a short randomized retry. This is overload protection for
the compatibility path, not the primary optimization.

## Correlated runtime telemetry

The `telemetry` Forge-config section enables low-overhead timers around mutually exclusive
Zomboid server phases: custom zombie behavior, sound simulation, shared navigation, vanilla
fallback path builds, population materialization, chunk callbacks, and the population world-load
callback. The final registered
server-tick listener compares those phases with the complete Forge server tick. Their difference
is logged as `unattributed`; that intentionally includes vanilla entity work, chunk generation and
loading, world saves, networking, garbage collection pauses, and other mods.

Ticks at or above `stallThresholdMillis` immediately write a timestamped breakdown with phase
time and call counts. `stallLogCooldownTicks` prevents a struggling server from flooding its log,
while retaining a count of suppressed stall records. Every `summaryIntervalTicks`, the log reports
average, p95, p99, and maximum server-tick duration plus phase totals and maximum per-tick phase
cost. World unload writes a lifetime summary. Defaults are telemetry enabled, a 100 ms stall
threshold, a 20-tick stall-log cooldown, and a 1200-tick summary interval.

Chunk callback time covers only Zomboid's queue and navigation invalidation work. It does not
pretend to measure Minecraft's internal terrain generation; expensive generation instead appears
as high unattributed server time. Timing is server-thread-only and does not access world state from
a worker thread. Startup callbacks that occur outside a Forge server tick are retained in a
separate `outsideServerTick` total so they cannot distort the first gameplay tick.

The optional `vanillaEntityWorkSampler` complements those exact timers with a low-frequency
statistical sample of the server thread. It attributes otherwise-unmeasured vanilla work to AI
selectors, pre-search `ChunkCache` snapshot construction, navigator advancement, path search,
nearby-entity push scans, block collision queries, AABB resolution, entity movement, entity
travel, move-helper processing, general living-entity work,
entity tracking/networking, chunks/world work, or other server code. It also reports the five most
frequent leaf frames. The default five-millisecond interval avoids enabling Minecraft's much more
intrusive full profiler for every entity and tick. Sampling does not alter entity decisions, but it
is diagnostic work and can be disabled when collecting a clean production benchmark.

The residual `livingEntity` bucket is further reported as `livingEntityDetails`. Its samples are
split between despawning, loot/equipment handling, mob-specific ticks, look/jump/body controls,
leash handling, status effects, base living updates, and an explicit `otherLiving` remainder. These
subcounts partition only `livingEntity`; they do not double-count work already attributed to AI,
navigation, movement, collision, or networking. This makes the next optimization choice evidence
driven while keeping the sampler observational.

The `entityPushScan` bucket covers `EntityLivingBase.collideWithNearbyEntities`, its cramming and
push processing, and the broad-phase chunk entity lookup beneath it. Movement is split into
`blockCollisionQuery`, `aabbResolution`, `entityMove`, `entityTravel`, and `moveHelper`; the deepest
operation present in a sampled stack wins so nested calls are not counted twice. This shows whether
crowded-horde cost comes from looking for nearby entities, collecting block collision boxes,
resolving offsets, or higher-level movement processing.

Path-search samples that do not occur inside a labeled Zomboid path request also record the first
initiating frame outside Minecraft's `net.minecraft.pathfinding` package. Summaries expose the five
most frequent frames as `unwrappedPathCallers`, allowing leftover vanilla goals or other untracked
call sites to be identified without instrumenting every navigator method first.

Every synchronous vanilla route request is also timed exactly and labeled as personal wandering,
return-to-anchor recovery, sound investigation, last-known-position pursuit, alert-leader following,
or player-pursuit
fallback. World-unload summaries report calls, successful requests, total time, average time, and
maximum time for each source; the same breakdown is emitted periodically on the normal telemetry
summary cadence. While one of those calls is active, the statistical sampler carries
the same label and estimates how its server-thread time divides between eager chunk snapshot
construction, A* search, navigator overhead, and other work. The source totals are exact; the
internal snapshot/search division is statistical and should be interpreted across a sustained run.

Managed personal wandering and return-to-anchor recovery now use the local point navigator by
default. It reuses the shared chunk-layer terrain cache, incrementally expands short reverse fields
under one per-world node budget, and steers through `EntityMoveHelper`. The `localNavigation`
telemetry phase measures request setup, incremental builds, and steering independently. With
`general.enableLocalWanderNavigation=true`, the `personalWander` and `returnToAnchor` vanilla-path
source totals should remain at zero after startup. World-unload logs also report local requests,
expanded nodes, build cost per node, arrivals, stuck failures, and invalidations.

Direct player pursuit also builds one per-world occupancy and cohort snapshot at the start of each
tick. Zombies pursuing the same player choose among the shared field's already-precomputed
equal-cost exits using stationary occupancy, same-tick reservations, and heading continuity. This
adds no route search. A one-block ring of approach goals prevents the field from funneling every
zombie into the player's exact column.

When at least `general.crowdCollisionMinimumCohortSize` zombies directly pursue the same player,
unteamed members temporarily join a target-specific internal scoreboard team using 1.12.2's
`pushOtherTeams` collision rule. That filters teammates out of vanilla's entity-push result while
preserving block collision and contact with the player. Membership is removed as soon as pursuit
or the cohort ends; zombies already owned by another scoreboard team are never changed. The
`crowdCoordination` phase times the once-per-tick snapshot and team maintenance, while world-unload
telemetry reports cohort observations, alternate exits, assignments, releases, and team conflicts.
Persisted internal `zbc...` teams are removed once when a scoreboard is first observed after load,
before assignments are rebuilt. This repairs saves made while a prior session still had active
cohorts without touching user or mod teams.

For an assigned cohort zombie, the optional core transformer keeps vanilla's block-collision
collector and Forge `GetCollisionBoxesEvent`, but replaces the repeated world-wide entity query
with the crowd snapshot's X/Z cell index. It removes only same-team zombies from the result;
players, other cohorts, unassigned mobs, block geometry, mounts, and mod event changes remain in
the query. Every unassigned entity and every failure takes the original vanilla path. Packaged
jars declare the transformer in their manifest, and Gradle `runClient`/`runServer` pass the same
core-plugin property for development launches.

The collision index is built only on ticks with assigned qualifying cohorts. Ordinary entities
occupy one center-cell bucket, while players, unusually wide entities, and fast-moving entities
use a short direct-scan list so their live bounding boxes remain authoritative. Bucket lists are
pooled between ticks and queries do not allocate identity-deduplication sets. World-unload
telemetry reports index builds, ordinary/direct entity counts, peak buckets, and actual bucket
allocations so index construction cost can be correlated with `crowdCoordination`.

The shared navigator does not treat entities as terrain obstacles, so there was no zombie-induced
A* rerouting to disable. When shared steering succeeds it now clears the vanilla navigator's
current path, deferred block-change rebuild flag, and remembered target position. This prevents
`PathWorldListener` from silently rebuilding an obsolete personal route on a later entity tick.
The same complete ownership release now runs when each custom movement goal ends and whenever the
local point navigator takes control; plain `clearPath()` is insufficient because it preserves the
remembered target and deferred rebuild flag.
For a qualifying cohort, a new vanilla fallback request is deferred only while an existing
navigator path or move-helper waypoint is still active. Unsupported terrain can still request a
fallback once movement is genuinely exhausted.

Vanilla `FOLLOW_RANGE` is now separate from custom player perception. `followRange` remains the
32-block maximum for the custom head-facing LOS sensor, while `vanillaPathSearchRange` defaults to
32 blocks. This reduces the `ChunkCache` footprint used by unavoidable vanilla A* requests without
shortening custom player detection.

Automatic repaths are instrumented at their two vanilla call sites. Logs separate immediate
`PathWorldListener` block-change calls from deferred `PathNavigate#onUpdateNavigation` entity-tick
calls, then group them by managed status and current brain state. Each group reports invocations,
actual rebuild attempts, cooldown deferrals, total time, average time, and maximum time. These
counts are exact; the broader vanilla entity-work sampler remains statistical.

Block edits use dependency-aware navigation invalidation. The terrain cache still forgets the
changed block's support/feet/head classifications and adjacent connected collision shapes
immediately, but a field is considered affected only when its solver has already reached one of
those dependent feet cells. A change elsewhere inside the field's geometric footprint no longer
throws away unrelated search progress. An affected shared field generation is allowed to finish so
rapid building cannot starve horde steering; it is marked stale and followed by a clean replacement.
Short local routes fail only when a reached dependency changes. Forge place/break and neighbor
notifications for the same position in the same world tick are deduplicated before reaching the
navigation managers.

World-unload summaries report total invalidation callbacks, unique positions, suppressed
duplicates, ignored field/route checks, active fields dirtied, in-progress builds marked stale,
preserved builds, and chunk-unload cancellations. This makes repeated construction tests auditable instead of
inferring invalidation churn from the started-versus-completed field totals.

The shared-navigation shutdown summary also reports cached movement commands reissued and cache
misses. During sustained far pursuit, direction queries should remain near the configured staggered
cadence while reissues fill the intervening ticks. A miss forces a full steering refresh rather
than leaving the zombie idle.

## Next stages

1. Validate local wander/return route cost and behavior under dense hordes, then extend its
   terrain profiles or stuck recovery only where the trace identifies a concrete failure.
2. Add a chunk-region portal graph so long-distance route decisions traverse a
   small hierarchy while packed local fields handle the final approach.
3. Use vanilla-work samples to isolate the dominant per-entity subsystem before changing vanilla
   AI, navigation advancement, collision, or entity tracking behavior.
4. Extend the correlated telemetry with sound-delivery fanout and stuck-recovery counters if the
   phase summaries identify either as material.
5. Add distributed approach slots and local crowd separation.
6. Add explicit profiles for wooden doors, controlled falls, water, ladders,
   destructive obstacles, and special zombie movement capabilities.
7. Build shared spatial indexes for perception and alert recruitment.
8. If profiling shows value, serialize versioned chunk snapshots and optionally
   let workers solve exclusively against those immutable arrays. Worker threads
   must never access `World`, chunks, or entities directly.

## Compatibility boundary

AI Improvements remains optional. Zomboid detects `aiimprovements`, runs its
join initialization after that mod's normal-priority edits, and preserves its
look-helper replacement and configured task removals. It does not participate
in Zomboid's shared navigation fields.

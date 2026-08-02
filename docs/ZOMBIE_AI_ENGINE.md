# Zombie AI Engine

Status: Brain foundation

## Current structure

Every loaded vanilla zombie receives a `ZombieBrain`. The brain is the
authoritative high-level state source for future behavior and audio decisions.
Brains are held in a weak entity map, so unloading an entity does not leave a
permanent runtime entry.

The brain now owns player acquisition. Vanilla's nearest-player target task is
removed when a zombie joins the server, while retaliation and non-player target
selectors remain. Each brain performs a head-oriented vision scan on a
per-entity staggered schedule. A detected survival/adventure player always
overrides wandering, sound investigation, alert observation, and leader
following.

Current states:

- `IDLE`
- `WANDERING`
- `INVESTIGATING_SOUND`
- `OBSERVING_ALERT`
- `FOLLOWING_ALERT_LEADER`
- `PURSUING_TARGET`
- `PURSUING_LAST_KNOWN_POSITION`
- `SEARCHING`
- `RETURNING_HOME`

The existing noise-investigation, last-known-position, and managed-zombie
wander tasks publish their movement intents to the brain. A live attack
target produces `PURSUING_TARGET`. Alert responses distinguish a stationary
decision phase (`OBSERVING_ALERT`) from committed leader movement
(`FOLLOWING_ALERT_LEADER`).

Player alerts are staged local responses rather than target broadcasts. The
first direct detector establishes an alert episode and immediately begins its
normal pursuit. Every eligible nearby zombie has a 35 percent chance to notice
that alert. Successful observers wait 10–25 ticks, then turn toward their
immediate alert leader without moving and make a 75 percent follow decision.
Successful followers wait an additional randomized 40–80 ticks before walking;
unsuccessful observers return to normal idle behavior at the end of that window.
They never receive the leader's player target or alert coordinates.

Once actively following, a zombie carries the same short alertness radius and
may recruit a bounded number of zombies it passes. This permits a gradual
local chain through multiple immediate leaders without an instant horde-wide
cascade. Failed decisions are never rerolled during the episode. Per-carrier
and per-episode caps bound the chain, and a follower's independent target
acquisition always overrides observing or following.

Alert decisions do not modify player detection. Any zombie that
independently detects a valid player target through the brain-owned sensor
pursues it with 100 percent commitment and immediately leaves observation or
leader-following behavior.

Initial vision originates at the zombie's eye position and uses its head yaw
and pitch to form a three-dimensional cone. The default cone is 120 degrees
wide. A block ray from the zombie's eyes to the player's eyes must also be
clear. The ray passes through light-transmitting blocks such as glass, stained
glass, panes, regular ice, and leaves, including modded blocks that expose the
same server-side material and light-opacity semantics. Opaque walls, closed
doors, and opaque partial blocks still prevent detection.

Detection is guaranteed within eight blocks when the player is unobstructed and
inside the cone. Beyond that, chance per scan falls quadratically to two
percent at `general.followRange`, which defaults to 32 blocks. That range is a
bounded simulation radius rather than an all-or-nothing awareness threshold.
The scan runs every five ticks by default and is staggered by zombie UUID.

The cone and probability apply only to initial acquisition. Once a player has
been acquired, pursuit is deterministic and retention only needs the eye ray;
the player does not become invisible by stepping behind the zombie's head.
After LOS is lost, the brain allows a ten-tick grace period, clears the live
attack target, and lets last-known-position behavior take over. Creative and
spectator players remain invalid targets.

Minecraft's vanilla creature-home restriction is deliberately detached. Its
target selector treated that home radius as a hard perception gate. The
persistent `PopulationTags` anchor and `EntityAIPersonalWander` now enforce
idle territory without affecting combat perception.

Current alert defaults are a six-block alertness radius, 35 percent look chance,
75 percent follow chance, two successful recruits per carrier, and four followers
per episode. Look delay is 10–25 ticks and successful followers wait an additional
40–80 ticks before walking.

Managed idle zombies use a brain-owned personal-wander task. Each candidate
destination is checked against that entity's persistent personal anchor.
The anchor begins at materialization but migrates with meaningful activity:
sound investigation moves it to the investigated area, and ending direct
player pursuit moves it once to the zombie's current position. It is never
updated continuously during pursuit. Horde center and generation-anchor
coordinates are not inputs to idle wandering.

Managed hordes also share a wander-start budget. Each selection rolls an
active-wanderer target from zero through `horde.wanderMaximumActive`, which
defaults to three. If more walkers are needed, they begin one at a time with
a random 10-to-30-tick stagger rather than moving simultaneously. Once that
selection is filled, the next selection waits between `60` and `140` ticks by
default (three to seven seconds, averaging roughly five).
Zombies outside their personal wander radius bypass the shared budget so they
can return promptly. Wandering and return-to-home movement use a short-range,
incrementally budgeted ground field backed by the same chunk-layer terrain
cache as player pursuit. It feeds the next cell to Minecraft's move helper,
so collision and physical movement stay vanilla while these idle states avoid
creating a vanilla `PathNavigateGround` search and eager `ChunkCache` snapshot
for every moving zombie.

`general.enableLocalWanderNavigation` provides an A/B fallback to the previous
vanilla route requests. `localNavigationNodesPerTick` is a shared build budget,
`localNavigationMaximumNodes` caps one route, `localNavigationDetourRadius`
controls the short search margin, and `localNavigationStuckTicks` controls
stuck recovery. Failed or invalidated routes wait five to ten seconds before
retrying rather than repeatedly searching an unreachable destination.

Managed zombies no longer retain vanilla's independent wander or
move-through-village tasks. The custom movement task runs only while a zombie
has an actual destination. While it is stationary, Minecraft's existing
random idle-look and nearby-player watch tasks remain eligible, allowing the
rest of the horde to look around without calculating movement paths.

## Variation-driven block breaking

General block destruction is an opt-in variation capability rather than a
universal zombie behavior. A variation may set `blockBreakingLevel="1"` through
`"4"` on its `<variation>` tag. Those levels correspond to hand/no-tool, wooden,
stone, and iron harvest requirements. Hardness affects duration only, using a
forty-times-slower `ceil(hardness × 30 × 40)`-tick schedule. Dirt takes 30
seconds, while a hardness-2 block takes two minutes.

When a capable zombie directly sees a survival/adventure player, it records that
player and position as a persistent breach objective. Losing LOS stops position
updates but does not cancel excavation: the zombie continues navigating and
digging toward the last known position while that player remains alive and valid.
A newly detected player may replace the objective. Open shared-navigation routes
remain preferred until pursuit actually reaches a blocking wall. Forty ticks
without at least a quarter-block of progress toward the objective also permits
planning when Minecraft reports no route and never drives the zombie into the
obstacle. Lateral crowd pushes and wall jitter do not reset this timer. At that
boundary, a local cost planner compares a full-height direct
breach against a supported two-block-deep tunnel beneath up to eight columns of
wall. It sums hardness-based breaking time and estimated
movement time, rejects blocks above the zombie's tier, and chooses the faster
valid route with direct breaching winning ties. The chosen plan temporarily owns
pursuit steering until it opens a usable route, another visible player replaces
the objective, or the remembered player dies or otherwise becomes invalid.

Crack progress, arm movement, and hit sounds expose the action. The service
respects Forge griefing/protection hooks, does not produce block drops, and
invalidates shared navigation terrain after opening a route. Vanilla's global
hard-difficulty door task is disabled so a zombie without the variation
capability cannot bypass this rule.

On world unload, the log reports aggregate digging objectives, collision/stall
triggers, plan rejection categories, reach waits, started blocks, completed
blocks, and Forge-denied starts. This makes a failed runtime test distinguish a
missing capability or memory from wall geometry, harvest-tier, reach, and
mob-griefing/protection failures.

The brain owns whether a player is targeted. A custom zombie pursuit task owns
movement and melee attacks. Player pursuit now uses one block-aware reverse
flow field per target and world. Every covered zombie reads a lower-cost next
cell and submits it to Minecraft's `EntityMoveHelper`, avoiding an independent
A* route and active `PathNavigateGround` shortcut scan for each horde member.
The reverse search stores valid lower-cost direction choices with each packed
cell, so normal steering does not reconstruct terrain neighbors per zombie.
Far pursuit refreshes are staggered while close pursuit remains full-rate.

At world-tick start, pursuit coordination counts how many zombies are directly moving toward each
player and snapshots occupied cells once. Cohorts of two or more distribute equal-cost flow exits
with same-tick reservations and prefer continued headings, producing several lanes without another
path search. Eight adjacent approach cells serve as shared field goals; qualifying cohorts hold an
in-range approach position rather than all entering the player's exact block.

The buff zombie uses the same pursuit task but exposes a model-matched
`1.1 x 2.25`-block entity size and a `2.0`-block eye height. Melee reach is
derived from entity width, so its larger body produces a larger legal attack
radius, including corner approaches where its center cannot get as close as a
vanilla zombie. Its visual head and headwear rotate around the neck rather than
the torso origin. The custom model is adult-only, so vanilla child sizing is
disabled for this entity. Its standard profile has 40 maximum health, 4 attack
damage, and 0.21 movement speed. Unlike ordinary zombies, this custom base speed
is not replaced by the global shambler-speed setting; an explicit variation XML
speed still overrides it.

By default, four or more unteamed zombies pursuing the same player temporarily stop applying
vanilla push impulses to one another. Minecraft's team collision predicate filters only members of
that target cohort; terrain collision and collision with the player remain active. External
scoreboard teams are preserved. `general.enableCrowdAwarePursuit`,
`crowdSteeringMinimumCohortSize`, `enableCohortCollisionSuppression`, and
`crowdCollisionMinimumCohortSize` control this behavior.

The shared field is generated incrementally, and the previous complete field
remains active while a player-movement replacement builds. The first surface
is intentionally conservative. Zombies outside it or on unsupported terrain
use the earlier TPS-aware vanilla path scheduler as a compatibility fallback.

Pursuit look tracking is capped at 10 degrees of yaw and pitch per tick by
default instead of vanilla melee pursuit's 30-degree request. This smooths the
visible head turn while still allowing the head-facing perception cone to
catch up naturally. `general.pursuitHeadTurnDegreesPerTick` controls the cap.

When direct pursuit ends—including because a player changes to creative or
spectator—the zombie commits its current position as its personal wander
anchor immediately. Completing or abandoning last-known-position movement
commits the final search position again. The zombie therefore establishes new
idle territory around where pursuit ended instead of returning to its original
materialization point.

## Audio state

Brain states also expose whether a zombie is alerted for player-facing audio.
Idle, wandering, sound-investigation, and return-home states use the idle
ambient budget. Target pursuit, last-known-position pursuit, search, and alert
response use the faster alerted ambient budget.

Idle managed zombies share one ambient budget per horde. The default allows
one idle vocalization every `200` ticks (ten seconds) from the entire horde.
Alerted zombies use a separate faster group budget, defaulting to one ambient
voice every `10` ticks (twice per second). Unmanaged zombies share the same
policy through configurable horizontal cells.

Each zombie now receives ambient opportunities at Minecraft 1.12.2's vanilla
randomized per-entity cadence. The horde scheduler is an additional admission
ceiling: an individual opportunity plays only when that horde's idle or alerted
budget is also due. A blocked opportunity resets the individual timer just as
a played vanilla opportunity would, so one isolated zombie cannot consume a
large horde's full sound rate or retry every tick. Dense alerted hordes can
still produce frequent overlapping voices because many independent zombies
offer opportunities to the shared budget.

Vanilla Minecraft 1.12.2 requests 28 normal channels and four streaming
channels from its Paulscode backend. Zomboid now requests 128 normal channels
through Forge's client sound-setup event while leaving the four streaming
channels unchanged. OpenAL creates sources until it reaches either the request
or the audio device's actual limit, so a lower-capability device safely
allocates fewer. `audio.normalSoundChannels` can be set to 64 or 48 as
compatibility fallbacks, or 28 for vanilla behavior; changing it requires a
client restart.

Zombie ambience and most short world sounds use the normal pool. Vanilla
creates each as a non-priority source; once every available normal channel is
busy, the next source reuses a playing non-priority channel and cuts that sound
off.

The client uses a TPS-driven admission budget rather than an FPS-driven zombie
limit. The server records a rolling five-second window of tick work and sends
the current TPS to clients every two seconds. The physical OpenAL source pool
does not change after startup. Instead, the client limits which new
non-critical sounds may enter that pool.

At healthy TPS the effective budget is the full number of normal sources
OpenAL actually allocated. Below 19.5 TPS it becomes 75 percent, below 18 TPS
it becomes 50 percent, and below 15 TPS it becomes 25 percent. The default
minimum is 28 sources. Recovery uses wider thresholds and advances only one
tier per evaluation, preventing ordinary TPS jitter from making the limit
oscillate. Reductions never stop active audio; they only defer new sounds until
occupancy falls below the new budget.

`HOSTILE` and `NEUTRAL` mob sounds may occupy up to
`audio.mobSoundChannelPercent` of the current effective budget, defaulting to
30 percent. At 128 effective channels this permits 38 mob voices; at 64 it
permits 19. This is a cap, not a reservation: quiet mobs leave the entire pool
available to other categories. Player, UI/master, music, record, and voice
feedback may bypass the global admission ceiling so a horde cannot silence
critical feedback.

The zombie family includes ambience, steps, attacks, hurt, death, door
interaction, zombie villagers, husks, and zombie pigmen using the
`entity.zombie*` or `entity.husk*` event families. Every admitted zombie sound
is marked priority after source creation and before playback. If the current
mob or global admission budget is full, the next eligible sound is rejected
before Paulscode creates its source. Existing voices therefore finish
naturally instead of cutting one another off. This does not change the idle or
alerted emission schedules.

Physical OpenAL channel count remains a startup setting. Changing it live would
require a full sound reload that stops all active audio; only admission changes
dynamically. TPS is intentionally treated as a simulation-health signal rather
than a direct audio profiler. On a dedicated server it describes the server
machine, so throttling client audio protects the client's peak source work but
cannot repair server-side TPS by itself.

The controller suppresses Minecraft's unmanaged `livingSoundTime` path and
reproduces its `random.nextInt(1000) < livingSoundTime++` opportunity rule per
zombie before applying the group limiter. Forge
1.12.2's `PlaySoundAtEntityEvent` does not identify the speaking zombie for
this path because `Entity.playSound` delegates to `World.playSound` with a
null player argument. Direct timer control therefore avoids both that broken
filter path and a nearby-entity scan.

The horde-level emission scheduler still governs only ambient vocalizations.
The client admission layer separately counts every `HOSTILE` or `NEUTRAL`
sound, including steps, attacks, hurt, death, and modded mob events, against
the shared 30-percent mob ceiling.

## Next implementation slice

The next pursuit-control slice can build on the shared field with:

- Distributed approach positions around the target
- Local crowd separation
- Stuck detection and alternate approach selection
- Runtime timing diagnostics for field builds, coverage, fallbacks, and server tick cost

Vanilla `PathNavigate` remains only the fallback for terrain not covered by the
current shared ground profile.

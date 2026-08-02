# Zomboid - AI Design Directive

Status: Coarse design baseline

Target: Minecraft Forge 1.12.2

Last updated: 2026-07-28

## Purpose

This document records the intended player experience and architectural
direction for Zomboid. It is the authoritative starting point for
future AI design work. The requirements below are deliberate; exact
algorithms, tuning values, save formats, and performance budgets remain to be
specified in focused design documents.

The goal is not merely to give vanilla zombies a larger detection radius.
Zombies should appear to perceive and search the physical world, influence
nearby zombies without acting as a coordinated military pack, and form a
persistent population that the player can meaningfully clear from an area.

## Core design principles

1. **Information must be earned.** A zombie may react to something it heard,
   saw, or learned from another alerted zombie. It should not know the
   player's live position without a believable source.
2. **The world matters.** Walls, doors, openings, elevation, terrain, and
   changing blocks must affect perception and navigation.
3. **Memory is imperfect.** Zombies remember locations and stimuli, not an
   invisible permanent lock on a target.
4. **Groups emerge from local reactions.** Zombies may converge because they
   share or notice alerts, but they do not use formations, leaders, or a
   universal hive mind.
5. **Clearing an area must matter.** Seeded populations and hordes persist
   logically until killed or changed by an explicitly designed migration or
   repopulation rule.
6. **Persistence does not mean always loaded.** Unloaded zombies may be stored
   as world data and rematerialized later. The mod must not keep every zombie
   entity ticking across the entire world.
7. **Performance is part of the design.** Expensive perception, propagation,
   pathfinding, and persistence work must be budgeted and distributed over
   time without making the simulation feel arbitrary.
8. **The server is authoritative.** AI decisions, persistent population state,
   and world changes must remain correct in single-player and multiplayer.

## Required systems

### 1. Block-aware reactive sound

Sound must be represented as a world stimulus rather than a simple spherical
distance check.

Required behavior:

- Gameplay events create sound events with an origin, intensity, category,
  time, and other information needed by the propagation model.
- Sound strength changes as it travels through the block environment.
- Walls, solid materials, doors, windows, openings, and vertical routes can
  attenuate, redirect, contain, or expose a sound.
- A zombie reacts to the perceived result at its location, not merely the
  source's raw radius.
- Zombies investigate an estimated sound location and retain a limited sound
  memory. They do not receive the source entity's continuously updated
  position.
- Repeated, overlapping, and moving sounds can reinforce or confuse an
  investigation.
- Block changes can invalidate relevant acoustic information.
- The system must support both vanilla events and later integrations such as
  firearms, alarms, machines, explosions, and modded sound producers.

The detailed design must choose the propagation model, acoustic material
properties, caching strategy, update budget, event categories, uncertainty
rules, and conflict resolution between competing sounds.

The selected first implementation uses configurable simple distance attenuation or an
incrementally budgeted weighted voxel field. Its current architecture, diagnostics, and
remaining work are recorded in `SOUND_SIMULATION.md`.

### Player-facing horde audio

Gameplay sound perception and audible zombie vocalizations are separate
systems. Dense hordes should remain intimidating without exhausting the
client's simultaneous sound sources.

- Idle and wandering zombies share a configurable horde-level ambient
  vocalization budget.
- Zombies that acquire or pursue a target return to full ambient frequency.
- Alert transitions may later add controlled horde responses.
- Attack, hurt, death, and movement sounds are not removed by ambient
  throttling.
- A later audio pass may aggregate distant alerted zombies into positioned
  horde ambience while preserving individual nearby voices.

### 2. Loose alert propagation between zombies

Zombies should influence nearby zombies through observable local behavior.
This creates emergent hordes without conventional pack intelligence.

Required behavior:

- A zombie that detects a player or important stimulus enters an alert state.
- Nearby zombies can notice that alert through designed signals such as
  vocalization, movement, direct observation, or other local stimuli.
- Every eligible zombie in the local alert radius notices the carrier: it
  first waits, then looks at its immediate alert leader without receiving the
  leader's player target.
- The configured probability controls only the later decision to walk behind
  the leader. Independent player perception is unchanged, overrides the alert
  response at any stage, and always produces normal pursuit.
- Individual variation and path accessibility prevent every nearby zombie
  from moving as one synchronized unit.
- Zombies have no permanent leader, formation, squad target, or global hive
  mind.
- Active followers carry a short local alertness radius and can recruit
  zombies they pass. Each candidate decides only once per episode, and
  per-carrier and per-episode limits keep this gradual chain from becoming an
  instantaneous guaranteed cascade.

The detailed design must define alert states, signal types, transmission
conditions, propagation limits, uncertainty, cooldowns, and anti-feedback
rules.

### 3. Perception, pursuit, and smarter navigation

Players must be able to hide or break contact, while zombies must remain
dangerous after losing direct sight.

Required behavior:

- Initial vision originates at the zombie's head, respects its current facing
  direction, and uses a real eye-to-eye occlusion ray through the world.
  Light-transmitting blocks such as glass do not occlude that ray; opaque
  blocks and doors do.
- Detection probability decreases smoothly with distance inside a bounded
  simulation radius instead of treating every visible player within range as
  equally noticeable.
- Detection distinguishes direct sight from hearing and secondhand alerts.
- On seeing a player, a zombie records a last-known position, observation
  time, and any other information needed for believable pursuit.
- Losing line of sight does not immediately cancel pursuit.
- A zombie travels toward the last-known position, searches nearby, and can
  reacquire the target through new sight, sound, or alert evidence.
- A zombie with a variation-driven block-breaking capability retains the
  observed player's last-known position as a breach objective after LOS is
  lost. It may continue excavating toward that fixed observation until it
  detects another target or the remembered player dies or becomes invalid.
- Search confidence decays. If no evidence is found, the zombie eventually
  leaves the search state rather than tracking the hidden player forever.
- Navigation understands relevant obstacles and interactions, including
  doors, windows, barricades, hazards, elevation, and routes around blocked
  spaces as those features are implemented.
- The system detects failed paths and stuck zombies, then retries, changes
  tactics, or abandons unreachable goals according to explicit rules.
- Multiple zombies should navigate crowded spaces without requiring precise
  pack coordination.

The intended architecture is a custom perception, memory, decision, search,
and shared-navigation layer inside Minecraft's entity lifecycle. Direct player
pursuit uses reusable reverse flow fields so zombies following the same target
do not independently solve and maintain nearly identical routes.

The first shared-navigation profile covers conservative ground movement and
feeds adjacent waypoints to `EntityMoveHelper`. Vanilla pathfinding remains a
budgeted fallback for incomplete fields and terrain semantics not yet modeled.
Field expansion is incremental on the server thread, and completed fields stay
active while moving targets request replacements.
Each reached cell retains its lower-cost direction choices. Zombies reuse those
choices with staggered far-distance waypoint refreshes instead of querying the
walking surface every tick. The cached waypoint is reissued each tick because
Minecraft 1.12.2 consumes a move-helper command once; close pursuit continues to
select and issue a fresh direction every tick.

The first ownership slice removes vanilla nearest-player selection. A
staggered brain sensor now acquires attackable players through a configurable
head-facing vision cone, an eye-to-eye block ray, and a distance-based chance
that falls beyond a guaranteed near radius. The random roll applies only to
initial awareness: an acquired player receives full pursuit commitment. A
short sight-loss grace period precedes last-known-position behavior. The
custom pursuit task executes shared-field movement and melee attacks for the
selected target. The vanilla creature-home restriction is not used because it also
gates target suitability; personal idle territory is enforced by custom
wandering.

The detailed design must define vision geometry, update frequency, memory
decay, search patterns, obstacle costs, destructive interactions, crowd
handling, path caching, and stuck recovery.

### 4. Seed-driven hordes and persistent population

The world should contain a deterministic zombie population that conceptually
exists before the player discovers it.

Required behavior:

- The world seed contributes to deterministic population and horde placement.
- Region characteristics may influence population density and composition.
  Exact inputs will be chosen later, but structures, biomes, and configurable
  density maps are expected candidates.
- A horde or population record can exist before its chunks are first explored.
- Zombies materialize safely when the relevant area becomes active instead of
  requiring every entity to exist and tick from world creation.
- Zombies created by this system do not disappear because of vanilla hostile
  mob despawning.
- A persistent group remains represented until its members are killed or an
  explicit world rule moves, merges, splits, or repopulates it.
- Kills and cleared populations are saved. Leaving and returning must not
  silently restore the original horde.
- Clearing a building, town, or region should create a meaningful safer area.
- Any migration or repopulation system must be visible, configurable, and
  designed separately; it must not masquerade as ordinary despawning and
  respawning.
- The system must survive chunk unloads, server restarts, and multiplayer
  activity without duplicating or losing population records.
- Entity counts and ticking work must respect configurable performance limits.

A logical population record is distinct from a loaded Minecraft entity. The
record is the durable truth; loaded zombies are its active representation.

The first implementation slice derives all population randomness from the
current Minecraft world seed. It uses deterministic regional plans, saved
horde definitions, stable population slot IDs, materialized-ID tracking, and
death tombstones. See `SPAWNING_SYSTEM.md`.

The detailed design must define region partitioning, deterministic generation,
population records, identity granularity, materialization rules, unload
conversion, save migration, death accounting, chunk tickets, spawn safety,
dimension handling, and optional migration/repopulation.

## Preliminary system boundaries

The current leading architecture is hybrid:

- **Zombie brain:** individual state, perception results, memories, current
  intent, and local decision making.
- **Zombie audio controller:** converts brain state and horde membership into
  player-facing ambient sound policy.
- **Sound service:** accepts sound events and produces block-aware perceived
  stimuli under a controlled processing budget.
- **Alert service:** handles local zombie-to-zombie alert signals without
  distributing perfect target information.
- **Navigation adapter:** translates brain intents into Minecraft navigation
  requests and handles obstacle interaction and failure recovery.
- **Population director:** deterministically defines regional populations,
  materializes active groups, and manages optional migration.
- **Persistent world store:** saves population records, deaths, cleared state,
  and versioned simulation data.

Minecraft should continue to provide entity lifecycle, attributes, collision,
chunk/world access, and low-level pathfinding unless a later design review
identifies a concrete limitation that cannot be extended safely.

## Initial AI state vocabulary

These names are provisional but capture the needed distinctions:

- Dormant or idle
- Suspicious
- Investigating sound
- Responding to another zombie
- Pursuing visible target
- Pursuing last-known position
- Searching
- Interacting with obstacle
- Returning to idle or wandering

State transitions must cite their evidence: sight, perceived sound, received
alert, memory, path result, or timeout.

## Explicit non-goals for the first architecture

- Reimplementing Minecraft's entire pathfinding engine before proving it is
  necessary
- Giving zombies live shared knowledge of every player
- Traditional pack roles, formations, or tactical squad coordination
- Keeping all persistent zombies loaded as entities
- Exact replication of Project Zomboid's internal implementation
- Adding infection, combat wounds, loot, or player survival systems to the AI
  scope unless separately approved

## Coarse acceptance scenarios

These scenarios will later become detailed simulations or tests:

1. A player mines inside a sealed stone building. A zombie outside reacts
   differently from one beside an open doorway, despite similar straight-line
   distance.
2. A zombie sees a player, alerts some nearby zombies, and loses sight when the
   player hides. The group converges imperfectly and searches the last-known
   area without tracking the hidden player's live position.
3. A player uses doors and terrain to break pursuit. Zombies attempt plausible
   routes or obstacle interactions, recover from failed paths, and eventually
   abandon a search with no new evidence.
4. A seeded town contains a stable population on first discovery. After the
   player clears it, leaving the chunks and restarting the game does not
   recreate the killed zombies.
5. A distant persistent horde consumes no full entity-tick cost while unloaded
   and rematerializes without duplication when its area becomes active.
6. Two worlds with the same seed and compatible configuration produce the same
   initial regional population plan; different player actions produce
   different persisted outcomes.

## Detailed design backlog

Each item should receive its own design pass before major implementation:

1. Sound event model and block-aware propagation
2. Zombie perception and memory model
3. Alert transmission and emergent group behavior
4. Decision states and transition priorities
5. Navigation adapter, obstacle interaction, and stuck recovery
6. Seeded regional population generation
7. Persistent horde data model and lifecycle
8. Migration and repopulation policy
9. Server performance budgets and degradation behavior
10. Configuration, diagnostics, visualization, and test strategy

Changes to the core requirements should update this directive. Algorithm and
tuning decisions belong in the detailed design documents so this file remains
a stable statement of intent.

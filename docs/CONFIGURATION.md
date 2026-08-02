# Forge Configuration Layout

Zomboid writes its main Forge configuration to
`config/zomboid/zomboid.cfg`. The categories are numbered because Forge 1.12
always sorts top-level category names alphabetically. The numbers guarantee a
stable progression from common gameplay choices to technical controls.

Existing files using the older `general`, `horde`, `noise`, `audio`,
`population`, and `telemetry` categories are migrated automatically. Known
values are moved rather than reset.

## 01_gameplay

These settings change what an ordinary player sees and feels:

- `movementSpeed`: base zombie movement speed. Vanilla is roughly `0.23`.
- `allowDaylightZombies`: permits seeded daylight spawning and prevents the
  vanilla sunlight-ignition decision. It does not grant fire immunity; lava,
  burning blocks, weapons, and other ordinary fire sources still work.
- `breakWoodenDoors`: allows variations with block-breaking capability to destroy wooden
  doors. Zombies without `blockBreakingLevel` cannot break them.
- `followRange`: farthest potential player-detection distance; defaults to 32 blocks.
- `playerVisionFieldOfViewDegrees`: width of the head-facing vision cone.
- `playerGuaranteedDetectionRadius`: visible players inside this range are always noticed.
- `playerDetectionChanceAtMaximumRangePercent`: chance per scan at the outer range.
- `targetMemoryTicks`: time an ordinary zombie spends investigating a lost
  target's last known position. A block-breaking variation instead retains its
  player-linked breach objective while that player remains alive and valid.

Minecraft runs at 20 ticks per second.

## 02_hordes

- `frequencyPercentPerChunk`: chance that an eligible chunk requests a persistent horde.
- `wanderRadius`: maximum idle distance from each mob's personal anchor.
- `wanderMaximumActive`: maximum members of one idle horde wandering simultaneously.
- `alertnessRadius`: distance over which an alerted zombie influences nearby zombies.
- `alertLookChancePercent`: chance that a nearby zombie notices the carrier and enters the
  delayed look stage.
- `alertFollowChancePercent`: chance that an observer joins the staged follow behavior.
- `alertMaximumFollowers`: maximum followers in one propagated alert chain.
- `definitionFiles`: ordered horde XML paths, relative to the Forge `config` directory.

Horde size, radius, entity makeup, variation makeup, and biome weights remain
in the readable XML profiles under `config/zomboid/hordes/`. Zombie variations
are defined in the single catalog `config/zomboid/variations.xml`.

## 03_sound

This section contains the settings that noticeably change hearing and ambience:

- `realisticSimulation`: realistic block-aware wave propagation when true; cheaper cached
  occlusion rays when false.
- `soundDetectionChancePercent`: chance that each zombie notices a distinct sound event;
  defaults to `75`, and ignored events are not rerolled on later AI ticks.
- Movement, landing, block, and combat noise radii.
- `noiseLifetimeTicks`: how long a zombie remembers a heard location.
- `waveIntervalTicks`: speed at which the realistic acoustic wave advances.
- State-aware zombie ambience and idle/alerted horde vocalization intervals.

## 04_population

- `enableSeededPopulation`: enables persistent world-seeded hordes.
- `replaceNaturalZombieSpawns`: suppresses vanilla random zombie spawning.
- `disableVanillaReinforcements`: prevents untracked combat reinforcements.
- `dimensions`: numeric dimension IDs where the seeded system operates.

## 99_advanced

The advanced section retains engine controls for pack authors, compatibility,
profiling, and unusual hardware. Defaults are designed as a coordinated set.

- `ai`: perception scheduling, path budgets, flow fields, local navigation, crowd steering,
  and collision-query optimization.
- `hordes`: exact alert delays and wander scheduling. Successful look rolls wait 10–25 ticks;
  successful follow rolls then wait another randomized 40–80 ticks before walking.
- `sound`: OpenAL channel budgets, TPS throttling, simulation work limits, caches, and debug
  visualization limits.
- `population`: per-tick materialization and retry scheduling.
- `diagnostics`: performance summaries, stall logging, and the vanilla entity-work sampler.

Advanced settings are not hidden; they are placed last and grouped by subsystem
so troubleshooting remains possible without making the common configuration
feel like an engine-development checklist.

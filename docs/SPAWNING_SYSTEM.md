# Seeded Population System

Status: First vertical slice

## Implemented model

The population system uses the current Minecraft world seed directly. It does
not create or persist a separate mod seed.

The world is divided into square population regions. When a region first
becomes relevant, the system combines the world seed with the dimension and
region coordinates to deterministically decide whether the region has a horde
and where that horde begins.

The save data records:

- Which regions have been initialized
- The generated definition of each existing horde
- Population IDs that have materialized as Minecraft entities
- Population IDs that have been killed
- The population generator version and structural region size

Individual untouched zombie positions and types are regenerated from the
world seed and their stable numbered slots. No full zombie NBT collection is
stored inside a horde record.

## Stable identities

The first generator creates at most one horde per region. IDs use this form:

```text
Group:  d<dimension>:r<regionX>,<regionZ>:g0
Zombie: d<dimension>:r<regionX>,<regionZ>:z<slot>
```

The normal Minecraft entity UUID still exists, but the population ID is the
durable identity used for death accounting and duplication prevention.

## Materialization lifecycle

1. A server chunk loads.
2. Its population region is initialized if it has never been visited.
3. The world seed regenerates the horde's zombie slots.
4. Slots assigned to the loaded chunk are compared with saved deaths and
   materialized IDs.
5. Missing eligible slots are placed on valid surface blocks.
6. Spawned zombies receive their population and group tags.
7. Managed zombies enable Minecraft entity persistence.
8. A managed zombie death records a permanent tombstone and removes its
   materialized marker.
9. Chunk unload and reload use normal Minecraft entity NBT for living,
   materialized zombies.

Materialization is spread across server ticks. Failed positions are retried
after a configurable delay.

## Forge configuration

The default file is `config/zomboidzombies.cfg`.

The `population` category includes:

- `enableSeededPopulation`
- `replaceNaturalZombieSpawns`
- `disableVanillaReinforcements`
- `dimensions`
- `regionSizeChunks`
- `hordeFrequencyPercent`
- `hordeMinimumSize`
- `hordeMaximumSize`
- `hordeSpreadRadius`
- `normalZombieWeight`
- `huskWeight`
- `zombieVillagerWeight`
- `materializedChunksPerTick`
- `materializationRetryTicks`

The `general.allowDaylightZombies` setting defaults to `true`. When enabled,
managed zombies may materialize without a darkness requirement and all
zombies are protected from daytime burning.

`replaceNaturalZombieSpawns` defaults to `true`. It denies vanilla random
zombie spawning in configured population dimensions so cleared areas are not
silently repopulated. Mob spawners, commands, and spawn eggs remain available.

`disableVanillaReinforcements` also defaults to `true`. It prevents combat
from creating untracked reinforcement zombies outside the persistent
population ledger.

### Configuration stability

The region size is structural and is locked into a world's population data
when that data is first created.

Horde frequency, size, spread, and makeup are captured when each region is
initialized. Changing those settings affects newly initialized regions, not
hordes that already exist.

## Diagnostics

Server operators can run:

```text
/zzpopulation stats
```

The command reports:

- Current dimension and stored region size
- Initialized-region and horde counts
- Materialized and dead population counts
- Currently loaded managed zombies
- The current region's horde ID, planned size, and center

## Current limitations

This slice deliberately establishes persistence before adding population
simulation:

- At most one horde is generated per region.
- Initial placement is surface-oriented and not yet structure- or
  biome-aware.
- Unloaded groups do not migrate, split, merge, or repopulate.
- Living materialized zombies remain normal chunk-saved entities.
- A zombie removed by an administrative command without dying remains marked
  materialized; reconciliation and repair commands are a later task.
- Spawn-slot death records use string IDs rather than a compact regional
  bitset.
- No density map or building/city semantics exist yet.

## Manual validation target

The first milestone is successful when:

1. A new region consistently generates the same horde from the same world
   seed and configuration.
2. Seeded zombies remain present through chunk unloads and server restarts.
3. Killed seeded zombies stay dead after reload.
4. Revisiting chunks does not duplicate living zombies.
5. Daylight spawning and survival follow `allowDaylightZombies`.
6. Frequency, size, spread, and makeup settings affect newly initialized
   regions.

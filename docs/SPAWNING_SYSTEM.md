# Seeded Population System

Status: Native-region definition slice

## Population planning

The population system uses the Minecraft world seed directly. It does not
create or persist a separate mod seed.

Minecraft's native 32 by 32 chunk regions are the planning and persistence
units. When any chunk in an uninitialized region becomes relevant, the mod
plans all potential horde anchors in that region. Planning does not load or
generate all 1,024 terrain chunks; biomes are sampled through the world's
biome provider.

Every chunk receives a deterministic global frequency roll from
`frequencyPercentPerChunk`. A successful roll requests one horde. Relative
biome weights decide which horde is requested. The catalog requires
a positive `ALL`-only fallback definition, so biome eligibility never turns a
successful global roll into an empty result.

The default frequency is `7`, which averages about 72 requested hordes per
32 by 32 chunk region before overlap protection. Overlap rejection lowers the
final placed count according to the configured horde radii.

## Horde selection

Each definition resolves one relative biome weight. An exact biome registry
ID takes priority, followed by the highest matching Forge biome dictionary
type, followed by `ALL`. If none match, or the resolved weight is zero, that
definition is rejected at the location. Resolved weights compete across the
eligible definitions and are not percentages. The standard profile uses only
`ALL=1`, making it the universal fallback and the baseline share against which
specialized profiles compete.

After a definition is selected, its population size is rolled inclusively
between `minimum` and `maximum`. Each stable population slot then chooses an
entity from the definition's weighted `members` list.

## Overlap protection

Horde footprints are horizontal circles. Two hordes overlap when:

```text
centerDistance < firstRadius + secondRadius
```

Every requested horde receives a deterministic priority derived from the
world seed and anchor chunk. A candidate is accepted only when no
higher-priority requested footprint overlaps it. Nearby candidates outside
the current planning region are included in the comparison, so the result
does not depend on which side of a region boundary is explored first.

This protection can reduce the final number of placed hordes when frequency
or radii are high. The global percentage is therefore the requested
frequency, while the saved region records both the accepted result and
overlap-blocked count in its initialization log.

## Stable identities and saved snapshots

New IDs use this form:

```text
Group:  d<dimension>:c<anchorChunkX>,<anchorChunkZ>:g0
Entity: d<dimension>:c<anchorChunkX>,<anchorChunkZ>:g0:z<slot>
```

Each accepted horde saves:

- Planning region and anchor chunk
- Group ID and selected definition ID
- Center, radius, and selected population size
- Resolved entity registry IDs, member weights, and nested variation references

Definitions are loaded at startup. Editing horde XML affects only planning regions
that have not been initialized. Existing hordes use their saved snapshots.

Version-one world data is migrated into 32 by 32 planning-region indexes.
Legacy group IDs, population IDs, positions, and entity selection sequences
remain unchanged so existing deaths and materialized entities remain valid.

## Materialization lifecycle

1. A server chunk loads and is queued.
2. Its planning region is initialized if necessary.
3. Saved horde snapshots regenerate stable entity slots.
4. Slots assigned to the loaded chunk are compared with saved deaths and
   materialized IDs.
5. Missing eligible slots are placed using the entity's registered spawn
   placement type.
6. Spawned entities receive population and group tags and enable persistence.
7. Each managed creature saves its materialization position as its wander home.
8. A managed entity death records a permanent tombstone.
9. Chunk unload and reload use normal Minecraft entity NBT for living,
   materialized entities.

Managed `EntityCreature` instances retain Minecraft's native home restriction,
and managed zombies use a brain-owned idle-wander task. Every zombie stores
its own successful materialization position as its personal origin; the horde
center and anchor are never used as its wander destination. Idle candidates
must remain within `02_hordes.wanderRadius` blocks of that personal origin.
Managed zombies share a horde-level path-start budget controlled by
`horde.wanderIntervalMinTicks` and `horde.wanderIntervalMaxTicks`. A random
active target from zero through `horde.wanderMaximumActive` is selected for
each cycle, and its starts are staggered. Returning to a personal origin
bypasses the shared budget. Stationary zombies retain Minecraft's idle-look
behavior without requesting a movement path.
Attack, last-known-position, and sound-investigation tasks have higher
priority and may leave the area. The saved personal origin is reapplied after
an entity reload because Minecraft does not persist `EntityCreature` home
state.

The definition loader accepts vanilla and modded registry IDs. Managed
non-zombie entities retain their own AI. The advanced Zomboid behavior layer
continues to apply only to `EntityZombie` and its subclasses.

If an entity referenced by an existing saved horde is no longer registered,
the slot materializes as `minecraft:zombie` and emits a warning once for that
missing registry ID.

## Configuration files

The Forge file `config/zomboid/zomboid.cfg` is the top-level configuration. It
contains global controls and directly lists the horde XML profiles:

```cfg
04_population {
    B:enableSeededPopulation=true
}

02_hordes {
    D:frequencyPercentPerChunk=7.0
    I:wanderRadius=4
    I:wanderMaximumActive=3
    S:definitionFiles <
        zomboid/hordes/standard.xml
     >
}

99_advanced {
    hordes {
        I:wanderIntervalMinTicks=60
        I:wanderIntervalMaxTicks=140
    }
}
```

Per-horde content settings live in:

```text
config/zomboid/hordes/*.xml
```

The initial release creates only `zomboid/hordes/standard.xml`; additional
horde profiles are opt-in. The complete format and examples are documented in
`docs/HORDE_DEFINITIONS.md`.

Zomboid always loads the independent zombie-variation catalog from
`config/zomboid/variations.xml`. That XML file can register multiple named
profiles that match entity registry IDs and override selected base attributes or
equipment after normal spawn initialization. Horde member entries reference
those profiles and define their relative weights. Selection and gear rolls derive
from the world seed and persistent population ID. The format is documented in
`docs/ZOMBIE_VARIATIONS.md`.

## Diagnostics

`/zomboid population stats` reports:

- Current dimension and fixed planning-region size
- Initialized-region and horde counts
- Materialized and dead population counts
- Loaded managed entities
- The current region's horde count and nearest horde definition

`/zomboid population regenerate` provides a live-editing workflow. It reloads
the Forge config, horde XML files, and the variation catalog; removes all loaded
zombies plus managed non-zombie horde members without death drops; clears the
saved population ledger in every loaded or offline dimension found in the active
world save; and queues loaded chunks for normal budgeted materialization on the
following ticks. Dead-population records
are deliberately cleared, so previously killed slots are eligible again. If an
XML file cannot be loaded, regeneration stops before entities or saved nodes are
removed and the error is written to `latest.log`.

Managed entities saved in chunks that were unloaded during the command carry a
population-regeneration epoch. They are discarded when their chunks later load,
preventing old horde members from re-entering or blocking the regenerated
population.

Region initialization also logs requested, accepted, and overlap-blocked
horde counts.

## Current limitations

- Horde centers use one deterministic position inside each successful anchor
  chunk rather than searching alternate positions after overlap rejection.
- Unloaded groups do not migrate, split, merge, or repopulate.
- A managed entity removed administratively without dying remains marked
  materialized; reconciliation remains future work.
- Surface placement is the primary tested path. Aquatic and flying modded
  entities need dedicated placement validation.
- No structure, city, or building semantics exist yet.

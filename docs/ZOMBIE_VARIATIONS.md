# Zombie Variation Definitions

Managed horde entities can receive a deterministic variation after their normal
Minecraft or modded `onInitialSpawn` initialization. Zomboid always loads the
single catalog `config/zomboid/variations.xml`; there is no Forge setting for a
variation-file list. The file may contain multiple entity sections, and each
entity may contain multiple variations.

## Readable example

```xml
<?xml version="1.0" encoding="UTF-8"?>
<variations formatVersion="1">
    <entity id="minecraft:zombie">
        <variation id="soldier">
            <attributes health="30" speed="0.30" damage="5"
                        knockbackResistance="0.25" swimSpeed="1.20" />
            <items>
                <head item="minecraft:golden_helmet" />
                <chest item="minecraft:golden_chestplate" />
                <hand item="minecraft:iron_sword" />
            </items>
            <effects>
                <onHit potion="minecraft:poison" durationSeconds="5"
                       amplifier="0" chancePercent="25" />
            </effects>
        </variation>
    </entity>

    <entity id="minecraft:husk">
        <variation id="soldier">
            <attributes health="35" speed="0.25" damage="6"
                        knockbackResistance="0.10" swimSpeed="1.00" />
        </variation>
    </entity>
</variations>
```

Each `<entity>` accepts exactly one namespaced entity registry ID. Add another
`<entity>` section for another vanilla or modded entity. An entity section may
contain any number of `<variation>` children.

Unqualified variation IDs use the Zomboid namespace, so `soldier` registers as
`zomboid:soldier`. A fully qualified ID such as `mypack:soldier` is accepted. The
same variation ID may be defined under multiple entities, with different settings
if desired. Horde validation and spawning resolve the variation using both its ID
and the horde member's entity ID. A duplicate variation ID within the same entity
is rejected.

The namespace in an entity ID identifies the owning mod, so no required-mod field
is needed.

The shorthand item slots accept either an `item` attribute or text content:

```xml
<head item="minecraft:golden_helmet" />
<head>minecraft:golden_helmet</head>
```

`hand` is an alias for `mainhand`; `boots` aliases `feet`, and `leggings` aliases
`legs`. The canonical slot names are `mainhand`, `offhand`, `feet`, `legs`,
`chest`, and `head`.

## Selection and persistence

The variation catalog does not contain spawn-selection weights. Horde member XML
owns the relative weights. The reserved `zomboid:standard` choice means vanilla
configuration and is defined only in the horde definition; it must not appear in
the variation catalog:

```xml
<member entity="minecraft:zombie" weight="80">
    <variations>
        <variation id="zomboid:standard" weight="80" />
        <variation id="zomboid:soldier" weight="20" />
    </variations>
</member>
```

This lets different horde profiles assign different variation makeups. The outer
member weight selects the entity type; nested weights select a compatible
variation. Selecting `zomboid:standard` applies no attribute, equipment, or
variation-tag changes, leaving the entity's normal initialization intact. A member
without `variations` also spawns unchanged. Custom references are checked at
startup and must exist and support that member entity.

Variation and equipment rolls use the world seed, dimension, and persistent
population ID. Retrying a blocked spawn produces the same result. The chosen ID,
attributes, gear, NBT, and drop chances survive chunk reloads. Existing entities
are not rerolled after a configuration change.

## Attributes

All fields are optional absolute base values applied after `onInitialSpawn`:

- `health` (alias `maxHealth`): `1` through `2048`; the entity is refilled.
- `speed` (alias `movementSpeed`): `0.01` through `2.0`.
- `damage` (alias `attackDamage`): `0` through `2048`.
- `knockbackResistance` (alias `knockback`): `0` through `1`. A value of `1`
  provides full resistance to ordinary knockback checks.
- `swimSpeed` (alias `swim`): `0` through `1024`. Forge's normal living-entity
  base value is `1`; values act as multipliers for water movement.

Minecraft movement speed is not a percentage. A zombie near `0.23` is roughly in
the vanilla range; `speed="0.30"` is faster. Potion and equipment modifiers remain
active on top of these base values.

## Advanced equipment pools

A slot can roll among multiple vanilla or modded items:

```xml
<variation id="mypack:armored_worker" blockBreakingLevel="3">
    <attributes health="28" speed="0.18" damage="5"
                knockbackResistance="0.20" swimSpeed="1.10" />
    <items>
        <mainhand chancePercent="75">
            <item id="minecraft:iron_pickaxe" weight="3"
                  count="1" metadata="0" dropChancePercent="5" />
            <item id="examplemod:powered_wrench" weight="1"
                  dropChancePercent="1">
                <nbt>{"Energy":1000}</nbt>
            </item>
        </mainhand>
        <chest item="examplemod:worker_chestplate"
               dropChancePercent="2" />
    </items>
</variation>
```

Configuring a slot gives the variation ownership of it. Vanilla gear is cleared
first, and the slot stays empty if `chancePercent` fails. Omitted slots retain the
entity's normal equipment. Item `weight` is relative within that slot only; it is
unrelated to horde variation-selection weight.

Missing entities disable profiles that no longer match any available living
entity. Missing items are removed from their slot pools and reported at startup.

## Block breaking capability

`blockBreakingLevel` is an optional integer attribute on `<variation>` itself,
not a living attribute inside `<attributes>`. Omitting it means that the zombie
cannot break general terrain blocks:

```xml
<variation id="tunneler" blockBreakingLevel="3">
    <attributes health="24" speed="0.20" damage="5" />
</variation>
```

The four capability levels follow Minecraft and Forge harvest requirements:

- `1`: blocks requiring no tool, including ordinary hand-breakable terrain.
- `2`: level 1 plus blocks requiring wooden tools (harvest level 0).
- `3`: level 2 plus blocks requiring stone tools (harvest level 1).
- `4`: level 3 plus blocks requiring iron tools (harvest level 2).

Diamond-tier requirements (harvest level 3) and blocks with negative hardness
remain unbreakable. Eligibility comes only from the required tool tier; hardness
does not decide whether a zombie can break a block.

Hardness controls duration after eligibility succeeds. The duration is
`ceil(block hardness × 30 × 40)` ticks: forty times the standard harvestable-block
baseline. Dirt at hardness `0.5` takes 600 ticks (30 seconds), hardness `1.5`
takes 1,800 ticks (90 seconds), and hardness `2.0` takes 2,400 ticks (two minutes).

A capable zombie that directly sees a survival/adventure player records a
persistent last-known-position breach objective. Losing LOS prevents further
position updates but does not cancel digging while the remembered player remains
alive and valid. A newly detected player can replace the objective. Shared
navigation gets the first opportunity to use an existing open route. If pursuit
physically reaches a blocking wall, or fails to move at least a quarter block
closer to its objective for 40 ticks, the breach planner
measures up to eight blocks of local wall thickness and compares two complete plans:

- A direct two-block-high opening through every obstructed wall column.
- A supported two-block-deep tunnel beneath the wall with a climbable one-block
  step on the far side.

Each plan's cost includes the sum of its blocks' configured breaking durations
plus an estimated 20 ticks per traversed block. A route is rejected if any block
it needs exceeds the zombie's breaking level, is unbreakable, leaves the loaded
world, or lacks safe tunnel support. The lower predicted time wins; equal costs
prefer breaking directly through the wall. An underground plan can therefore
bypass an unbreakable wall when the supporting terrain remains breakable.

When an already materialized zombie is loaded, its stored variation ID is
resolved against the current variation catalog and its runtime capability tags
are refreshed. This allows changes such as adding or adjusting
`blockBreakingLevel` to take effect without rerolling equipment, health, or other
spawn-time variation choices.

The selected plan temporarily owns pursuit steering as it advances from block to
block. Losing or invalidating the player target immediately cancels the plan and
clears its crack overlay. Digging displays normal crack progress, arm swings, and
block-hit sounds. Destroyed blocks do not drop items. The system respects
`mobGriefing`, Forge's living block-destruction event, modded `canEntityDestroy`
rules, and the existing `breakWoodenDoors` setting. Direct terrain changes
invalidate Zomboid's shared navigation data so pursuing zombies can use the opening.

## On-hit effects and zombie aura

The optional `<effects>` section may contain multiple `<onHit>` entries. When
that variation directly hits a player in melee, each entry rolls independently
and applies its registered vanilla or modded potion effect on success.

- `potion`: required namespaced potion registry ID.
- `durationSeconds`: effect duration in seconds, default `5`. Decimal values
  such as `2.5` are supported and converted to Minecraft ticks internally.
- `amplifier`: zero-based effect level, default `0` (level I).
- `chancePercent`: independent application chance, default `100`.

Player effects use Minecraft's normal non-ambient rendering with visible
particles. Those rendering fields are intentionally not configurable.

No zombie particle is configured separately. A variation with on-hit effects
automatically emits a light `SPELL_MOB` aura using each potion's registered
liquid color. This keeps the visual warning synchronized with the effects the
zombie can inflict and also supports colors supplied by modded potions.

Limits:

- Equipment item weight: `1` through `1,000,000`
- Equipment and drop chance: `0` through `100` percent
- Stack count: `1` through `64`
- Metadata: `0` through `32767`
- On-hit duration: `0.05` through `50,000` seconds
- On-hit amplifier: `0` through `255` (zero is effect level I)
- On-hit chance: `0` through `100` percent

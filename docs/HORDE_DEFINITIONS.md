# Horde Definition Format

The Forge configuration controls global placement, definition files, and idle
behavior in the player-facing `02_hordes` section. Only detailed wander
scheduling lives under `99_advanced.hordes`:

```cfg
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

`frequencyPercentPerChunk` is the global chance that each chunk requests a
horde. It accepts decimal percentages. When this roll succeeds, the biome
weights decide which eligible horde definition is selected. The catalog must
include one positive `ALL`-only fallback definition, so the successful global
roll always has a definition to select.

`definitionFiles` paths are relative to the Forge `config` directory and may
not leave it.

`wanderRadius` is the maximum idle wander distance, in blocks, from each
managed mob's original spawn point. Set it to `0` to disable the restriction.

`wanderIntervalMinTicks` and `wanderIntervalMaxTicks` define the inclusive
random delay between new idle paths for members of the same managed horde.
The defaults select a fresh delay from three through seven seconds after each
wander, averaging roughly five seconds. A zombie returning to its personal
spawn point is not delayed by this setting.

`wanderMaximumActive` is the upper bound for each horde's randomly selected
active-wanderer target. The default rolls from zero through three. Additional
walkers start one at a time with a short random stagger; stationary members
remain eligible for vanilla idle-looking without calculating movement paths.

## Optional custom horde example

The initial release creates only `standard.xml`. Additional files such as this
specialized desert profile can be added manually and listed under
`definitionFiles`.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<horde formatVersion="2" id="zomboid:desert">
    <population minimum="18" maximum="36" radius="32" />

    <members>
        <member entity="minecraft:husk" weight="80">
            <variations>
                <variation id="zomboid:standard" weight="68" />
                <variation id="zomboid:sprinter" weight="15" />
                <variation id="zomboid:shambler" weight="10" />
                <variation id="zomboid:tunneler" weight="5" />
                <variation id="zomboid:experienced" weight="2" />
            </variations>
        </member>

        <member entity="minecraft:zombie" weight="20" />
        <member entity="anothermod:hostile_entity" weight="5" />
    </members>

    <biomeWeights>
        <biome selector="HOT" weight="3" />
        <biome selector="DRY" weight="3" />
        <biome selector="minecraft:desert" weight="6" />
    </biomeWeights>
</horde>
```

The namespace before the colon in an entity registry ID identifies its owning
mod. No separate required-mod declaration is needed. Missing entity entries
are ignored during startup validation. A definition with no available living
entities is disabled.

Biome weights are relative values, not percentages, and do not need to total
100. A selector containing a colon is an exact biome registry ID. A selector
without a colon is a Forge biome dictionary type. `ALL` is the fallback.

Within one definition, an exact biome match wins first. Otherwise the highest
matching biome-type weight wins. If neither matches, `ALL` is used when it is
present; otherwise the definition is rejected for that location. A resolved
weight of zero also rejects the definition. These matches do not add or
multiply together.

After resolving one weight per eligible definition, the values compete as
relative shares. For example, standard `ALL=1` and desert
`minecraft:desert=6` give the desert profile six shares and standard one share
in that biome. Keep one definition with only a positive `ALL` entry so every
successful global placement roll can select a horde.

Member `weight` values are relative shares and do not need to total 100.
Setting a member weight to zero is invalid; remove that `<member>` element
instead.

The optional member `<variations>` section combines the reserved vanilla choice
`zomboid:standard` with custom profiles registered from
`config/zomboid/variations.xml`. Its weights are relative and need not total
100. This nested selection occurs after the member entity is selected. Repeating
variation weights in different horde files allows each horde type to have a
different variation makeup. Omit `<variations>` to leave that member unmodified.

`zomboid:standard` is defined entirely by its horde weight and must not be added to
the variation XML. It applies no overrides, preserving vanilla entity attributes
and equipment. Every other referenced variation must exist under the matching XML
`<entity id="...">` section. Missing or incompatible references are reported during startup.
If a member explicitly configures variations but none are valid, that member is
disabled instead of silently spawning without the intended variation.

## Limits

- `formatVersion`: currently `2`
- `frequencyPercentPerChunk` in the Forge config: `0` through `100`
- `wanderRadius` in the Forge config: `0` through `128`; `0` disables it
- `wanderIntervalMinTicks` and `wanderIntervalMaxTicks` in the Forge config:
  `1` through `12000`
- `wanderMaximumActive` in the Forge config: `0` through `32`
- Population size: `1` through `1000`
- Radius: `1` through `256` blocks
- Member weight: `1` through `1,000,000`
- Nested variation-reference weight: `1` through `1,000,000`
- Biome weight: `0` through `1,000,000`

Definition paths are resolved relative to the Forge configuration directory.

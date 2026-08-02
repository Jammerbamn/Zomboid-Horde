# Zomboid - Project Context

This file is the compact handoff for continuing the project in a fresh Codex
task without loading the large Graphify generation history.

## Goal

Create a Minecraft Forge 1.12.2 mod that makes vanilla zombies behave more
like Project Zomboid zombies: deliberate shamblers with persistent pursuit,
sound investigation, group behavior, and world-scale horde pressure.

## Current workspace

- Forge: 1.12.2 / 14.23.5.2847
- Mod ID: `zomboid`
- Java: 8
- The starter implementation currently modifies vanilla `EntityZombie`
  behavior rather than registering a replacement zombie entity.
- Existing systems include configurable movement and detection, noise
  attraction, last-known-position pursuit, nearby horde alerting, wooden-door
  breaking, optional daylight immunity, a per-zombie brain-state foundation,
  state-aware horde-budgeted audio, and shared player-pursuit flow fields.
- The spawning system uses world-seeded 32 by 32 chunk planning regions,
  XML-defined horde types, biome selection modifiers, deterministic
  non-overlap protection, persistent population IDs, death tombstones, and
  chunk materialization. Its design is recorded in
  `docs/SPAWNING_SYSTEM.md` and `docs/HORDE_DEFINITIONS.md`.
- The Forge configuration presents common gameplay, horde, sound, and
  population choices first. Engine tuning and diagnostics are nested under
  the final `99_advanced` category; older category layouts migrate in place.
- The project built successfully before this handoff.

## AI architecture

The authoritative coarse requirements are recorded in
`docs/AI_DESIGN_DIRECTIVE.md`. Read that directive before making architecture
or implementation decisions.

The current design is a hybrid:

- Keep Minecraft's entity lifecycle, attributes, collision handling, and
  movement helpers.
- Use one reusable block-aware reverse flow field per pursued player for the
  common horde pursuit case. Covered zombies read adjacent waypoints instead
  of maintaining independent vanilla paths.
- Keep TPS-budgeted `PathNavigate` routing as a fallback for incomplete fields
  and terrain profiles the shared solver does not yet support.
- Add a custom decision layer for perception, memory, investigation, target
  persistence, horde communication, and state transitions.
- Use the brain state to control player-facing zombie ambience: dense idle
  groups are quieter, while alerted groups retain full ambient frequency.
- Add a world-level horde director for population pressure, migration,
  spawning policy, and performance budgets.
- Replace or suppress individual vanilla `EntityAIBase` tasks only where they
  conflict with the custom decision layer.

This preserves Forge/mod compatibility and avoids rebuilding navigation while
still allowing substantially more advanced behavior.

## Minecraft mob-system map

The reference map is stored in `minecraft-mob-system-map/`.

The mod's own Java architecture map is stored beside it in
`zomboid-code-map/`. Its interactive graph, raw graph, report, and health
diagnostic use the same `graphify-out/` layout as the vanilla reference map.

- Corpus: 478 decompiled Minecraft Java files
- Raw extraction: 9,254 nodes / 36,078 relationships
- Final graph: 9,214 nodes / 27,780 unique edges / 325 communities
- Human-readable report:
  `minecraft-mob-system-map/graphify-out/GRAPH_REPORT.md`
- Health report:
  `minecraft-mob-system-map/graphify-out/GRAPH_HEALTH.txt`
- Local interactive map:
  `minecraft-mob-system-map/graphify-out/graph.html`
- Local query data:
  `minecraft-mob-system-map/graphify-out/graph.json`

The large generated map, visualization, manifests, labels, caches, and
decompiled corpus remain on disk but are intentionally ignored by Git. This
prevents hundreds of thousands of generated lines from entering Codex review
history.

## Recommended opening prompt for a fresh task

> Read `PROJECT_CONTEXT.md` and `docs/AI_DESIGN_DIRECTIVE.md`, then help me
> detail one of the AI systems without weakening the directive's requirements.
> Use the existing Graphify map where useful. Do not rebuild the map.

## Remote-task note

The original task accumulated a roughly 42 MB local task record after large
Graphify JSON intermediates were created and cleaned up. That history cannot
be reduced retroactively. Continuing in a fresh task after this clean baseline
is the reliable way to avoid the phone Remote loader encountering the same
payload.

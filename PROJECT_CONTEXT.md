# Zomboid Zombies - Project Context

This file is the compact handoff for continuing the project in a fresh Codex
task without loading the large Graphify generation history.

## Goal

Create a Minecraft Forge 1.12.2 mod that makes vanilla zombies behave more
like Project Zomboid zombies: deliberate shamblers with persistent pursuit,
sound investigation, group behavior, and world-scale horde pressure.

## Current workspace

- Forge: 1.12.2 / 14.23.5.2859
- Mod ID: `zomboidzombies`
- Java: 8
- The starter implementation currently modifies vanilla `EntityZombie`
  behavior rather than registering a replacement zombie entity.
- Existing systems include configurable movement and detection, noise
  attraction, last-known-position pursuit, nearby horde alerting, wooden-door
  breaking, and optional daylight immunity.
- The project built successfully before this handoff.

## Architecture question to resolve next

Decide how much AI should be custom versus delegated to Minecraft.

The authoritative coarse requirements are recorded in
`docs/AI_DESIGN_DIRECTIVE.md`. Read that directive before making architecture
or implementation decisions.

The leading design direction is a hybrid:

- Keep Minecraft's entity lifecycle, attributes, collision handling,
  `PathNavigate`, and low-level pathfinding.
- Add a custom decision layer for perception, memory, investigation, target
  persistence, horde communication, and state transitions.
- Add a world-level horde director for population pressure, migration,
  spawning policy, and performance budgets.
- Replace or suppress individual vanilla `EntityAIBase` tasks only where they
  conflict with the custom decision layer.

This preserves Forge/mod compatibility and avoids rebuilding navigation while
still allowing substantially more advanced behavior.

## Minecraft mob-system map

The reference map is stored in `minecraft-mob-system-map/`.

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

# Sound Simulation

Status: Transient block-aware wavefront implementation

## Selected model

Zombie hearing uses one event-driven sound service per loaded world. The service supports
two Forge-configurable modes:

- `simple`: distance attenuation plus one material-aware occlusion ray shared by zombies in
  the same configurable listener cell. This is the lower-end block-aware option.
- `realistic`: a paced weighted voxel wavefront. Sound loses energy as it crosses blocks,
  and a lowest-loss search naturally sends it around walls through cheaper openings. Only the
  moving front can be heard; explored acoustic cells are solver bookkeeping, not persistent sound.

The realistic solver runs on the authoritative server thread. Minecraft 1.12 world and chunk
objects are not safe for concurrent reads, so no worker thread touches them. Work is instead
distributed across ticks using `noise.waveIntervalTicks` and
`noise.propagationNodesPerTick`. A future worker may process an immutable block snapshot, but it
must never access live world state.

## Propagation

Every event stores an ID, source position, type, initial strength, memory duration, and optional
source entity ID. In realistic mode it also owns a priority frontier and the cells reached by its
current wave step.

Air and open passages cost one strength unit per block. Water, foliage, glass, wood, earth,
stone, and metal progressively cost more. Costs accumulate through thick barriers. Because the
solver retains the strongest route to each cell, openings, halls, and routes around corners are
preferred over penetrating dense blocks. Each scheduled update advances only the next attenuation
layer—normally about one block in open air. Unloaded chunks are unavailable and are never loaded
for sound.

Each sound category now selects one scalar acoustic profile. Profiles approximate spectral
differences by changing material attenuation without creating additional frequency-band fields:

- Walking uses a light-footstep profile. It is easy for walls to absorb, but a strong nearby
  arrival can be localized precisely.
- Sprinting uses a heavier impact profile that carries through earth, stone, and metal better
  than ordinary footsteps.
- Jump takeoff and landing use the heavy-footstep profile. Landing strength grows with the
  tracked vertical drop, up to three times its configured base radius.
- Block breaking uses a structural-impact profile. Dense barriers transmit more of its low,
  heavy impulse, but reverberation gives zombies a less precise remembered origin.
- Block placement uses a more moderate construction-impact profile.
- Combat uses a broad impact profile between footsteps and structural breaking.
- Debug pulses use the neutral legacy profile, preserving the original material costs for
  controlled comparisons.

Simple mode applies the same profiles to its material transmission multiplier, so switching
simulation modes changes fidelity and cost rather than changing the identity of each stimulus.
The profile is shown alongside the event type in `/zomboid sound status`.

The current material costs are code defaults. They should become data-driven after gameplay
testing establishes useful values.

Realistic repeated sounds remain separate moving pulses, so a new footstep does not erase an older
wave that is still traveling. The configured active-event and per-event node limits remain hard
memory bounds. Simple-mode repeated sounds from the same source and category replace the prior
cached-ray event.

## Zombie queries

A realistic wave checks a zombie's feet and head only when its front reaches those cells. At that
instant the zombie stores an individual, deterministic but imprecise estimate of the source in its
brain. The wave then continues and disappears; investigation uses the remembered estimate rather
than querying a persistent world sound field. Weaker arrivals produce category-dependent
horizontal uncertainty. Light footsteps are relatively precise when clearly heard, while
structural impacts can be displaced by up to eight blocks to represent echoes and transmission
through the building fabric.

Simple mode first applies a distance
gate, then raycasts from the listener cell to the fixed event origin. Clear rays retain full
range. The first blocking material reduces effective range: foliage and glass remain relatively
audible, while wood, stone, and metal increasingly muffle the event. Results are cached per event
and listener cell so dense hordes share ray work.

After reaching an event, a zombie marks that event complete locally instead of repeatedly
restarting a path to the same source until the event expires.

Player movement is sampled explicitly on the server. Grounded horizontal movement emits periodic
footsteps: walking uses the configurable lower strength and sprinting uses the stronger sprint
setting. Forge's exact living-jump event emits one jump pulse. Air-to-ground transitions emit one
landing pulse, using the tracked airborne peak so vanilla's fall-distance
reset cannot erase the impact strength. Walking off an edge produces only the landing pulse.
Swimming, climbing, riding, and creative flight suppress these airborne transitions. Sneaking is
currently silent only for ordinary footsteps. The initial mining impact, completed block break,
block placement, and combat produce their configured stimuli. Same-player, same-category events
in one tick are deduplicated. Creative players emit movement, building, and combat stimuli so the
systems can be tested without changing game modes; spectators remain silent.

## Configuration

The player-facing `03_sound` section contains `realisticSimulation`, the sound
radii, memory duration, wave speed, and zombie ambience controls. Technical
work limits are grouped under `99_advanced.sound`:

- `realisticSimulation`: `true` enables realistic voxel propagation; `false` enables
  simple cached-ray simulation. It defaults to `true`.
- `soundDetectionChancePercent`: independent chance that each zombie notices a distinct sound
  event. It defaults to `75`; the stable decision is not rerolled while that event remains active.
- `waveIntervalTicks`: ticks between realistic front advances; defaults to two. Since Minecraft
  runs at 20 ticks per second, the open-air front advances at roughly ten blocks per second.
- `simpleOcclusionCellSize`: side length of cells sharing a simple-mode ray; defaults to four.
- `propagationNodesPerTick`: shared cell budget whenever active fronts advance.
- `maximumNodesPerEvent`: hard field size and work cap for one event.
- `maximumActiveEvents`: retained events per world.
- `debugParticleBudget`: maximum sampled visualization particles per viewer and pulse.

The existing source strength and lifetime settings continue to apply. Walking uses
`walkNoiseRadius`, while `footstepIntervalTicks` controls the shared walking and sprinting
emission interval. `jumpNoiseRadius` controls takeoff strength. `landingNoiseRadius` is the base
strength for an ordinary landing and scales with the square root of the tracked drop, capped at
three times the base.

## Visualization

Operators can use:

- `/zomboid sound realisticSimulation true`
- `/zomboid sound realisticSimulation false`
- `/zomboid sound status`
- `/zomboid sound emit [strength]`
- `/zomboid sound pulse`
- `/zomboid sound debug on`
- `/zomboid sound debug off`

Debug mode draws only the latest event's currently moving front. Green particles are strong,
spell particles are medium strength, and red particles are weak. Old field cells are not redrawn,
so the particles read as a traveling shell instead of a persistent filled cloud. Simple mode
draws cached source-to-listener rays and marks blocking cells in red. `status` reports the event
ID and category plus either cached occlusion cells or realistic wave/frontier progress. It also
reports lifetime producer counts by category and the last recorded event, allowing event capture
to be distinguished from propagation or visualization problems.

Particles are an initial diagnostic surface. A later client overlay can render selectable slices,
barrier costs, and lowest-loss paths without hiding samples inside solid blocks.

Changing `realisticSimulation` through the command takes effect immediately, clears active fields
created under the previous mode, and persists the boolean to `zomboid.cfg`. Existing string-based
`simulationMode` configuration is migrated once: `simple` becomes `false`, while `realistic`
becomes `true`.

## Next slices

1. Move the coded profile defaults into data-driven sound-category definitions after tuning.
2. Chunk-section acoustic caching and block-change invalidation.
3. Reinforcement and conflict handling for overlapping sounds.
4. Optional frequency bands only if scalar profiles cannot produce sufficiently distinct behavior.

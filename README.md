# Zomboid

A Minecraft Forge 1.12.2 mod that makes vanilla zombies behave more
like Project Zomboid's shamblers.

The long-term AI requirements and system boundaries are recorded in
[`docs/AI_DESIGN_DIRECTIVE.md`](docs/AI_DESIGN_DIRECTIVE.md).

## Included behavior

- Slower, configurable shambler movement
- Longer player detection range
- Attraction to footsteps, jumping, landing, block breaking/placing, and combat noise
- Pursuit of a player's last known position after line of sight is lost
- Horde alerting when one zombie acquires a player target
- World-seeded persistent horde generation
- Persistent death accounting for seeded zombies
- XML-defined horde types, biome weighting, size, radius, and entity makeup
- Deterministic non-overlapping horde footprints
- Persistent configurable wander homes for managed horde creatures
- Per-zombie brain-state foundation for staged custom AI development
- Density-aware idle ambience with full alerted-zombie vocal frequency
- Wooden-door breaking AI
- Daytime spawning and survival, enabled by default

The behavior applies to `EntityZombie`, its vanilla subclasses, and Zomboid's
custom buff-zombie subclass. Vanilla zombie entities are not replaced, so their
textures, drops, spawning, and mod compatibility are retained.

## Requirements

- Minecraft 1.12.2
- Forge 14.23.5.2847 (the final build with the legacy userdev artifact required by ForgeGradle 2.3)
- A Java 8 JDK for Gradle and the development client

Java 8 is already installed on this machine at:

`C:\Program Files\Eclipse Adoptium\jdk-8.0.462.8-hotspot`

The wrapper selects this JDK locally, so the machine-wide `JAVA_HOME` can
remain configured for newer projects:

```powershell
.\gradlew.bat build
```

## IntelliJ IDEA

Open the repository as a Gradle project and reload it after changes to
`build.gradle`. The project SDK and Gradle JVM should both be
`temurin-1.8 (2)`, the system Temurin installation above.

Use the shared `Zomboid Client (Forge 1.12.2)` or
`Zomboid Server (Forge 1.12.2)` run configuration. These configurations use
the ForgeGradle 2 launch classes and ForgeGradle's Minecraft 1.12 asset cache.
If they ever need to be regenerated, run
`.\gradlew.bat setupDecompWorkspace genIntellijRuns`.
The Gradle client task also repairs missing legacy assets over HTTPS because
ForgeGradle 2.3's original HTTP asset downloader is no longer reliable.

The built mod jar is written to `build/libs/`.

### Expected legacy-tooling messages

ForgeGradle 2.3 always prints an unsupported-version warning because that
toolchain is no longer maintained. It is nevertheless the appropriate
ForgeGradle generation for this Minecraft 1.12.2 workspace; upgrading this
project to ForgeGradle 5 is not a warning-only change and would not make the
legacy userdev environment compatible.

The development client may also report that Maven's library folder has an
unexpected layout and that FML has no signature data. Those messages come from
running Forge from Gradle's deobfuscated cache rather than from a packaged
Minecraft installation. They are expected in this development environment and
do not indicate a Zomboid failure.

## Configuration

Launch the game once, then edit:

`config/zomboid/zomboid.cfg`

The Forge config controls movement speed, follow range, memory duration,
noise ranges, horde alerting, global horde frequency, the XML profile list,
wander distance, zombie ambient audio, population operation, door breaking, and daylight
spawning/survival. Per-horde content is loaded from XML under
`config/zomboid/hordes/`.

First launch creates one horde profile, `standard.xml`, plus the shared
`variations.xml` catalog. Additional horde profiles are opt-in.

Common settings appear first as `01_gameplay`, `02_hordes`, `03_sound`, and
`04_population`. Engine budgets, compatibility switches, and diagnostics are
grouped at the bottom under `99_advanced`. The complete
layout is documented in [`docs/CONFIGURATION.md`](docs/CONFIGURATION.md).

## Optional performance companion

[AI Improvements](https://www.curseforge.com/minecraft/mc-mods/ai-improvements)
may be installed separately for its low-level `EntityLookHelper` optimization.
Zomboid does not bundle it, download it automatically, or require it, so users
should obtain it from its official CurseForge or Modrinth distribution.

For Zomboid's head-driven player detection, keep AI Improvements' task-removal
options disabled unless deliberately trading animation/perception behavior for
server performance. Its default look-helper replacement is the relevant
optimization. The Minecraft 1.12 release does not replace `PathNavigate` or the
A* route solver, so Zomboid owns a reusable block-aware pursuit flow field and
keeps budgeted vanilla navigation only as a fallback. See
[`docs/AI_IMPROVEMENTS_COMPATIBILITY.md`](docs/AI_IMPROVEMENTS_COMPATIBILITY.md).
Zomboid detects the optional mod at runtime and preserves its look-helper and
task choices; it is not a required dependency. The independent optimization
architecture is documented in
[`docs/AI_PERFORMANCE_ARCHITECTURE.md`](docs/AI_PERFORMANCE_ARCHITECTURE.md).

The seeded population design and current limitations are documented in
[`docs/SPAWNING_SYSTEM.md`](docs/SPAWNING_SYSTEM.md).
The Forge horde settings and XML profile schema are documented in
[`docs/HORDE_DEFINITIONS.md`](docs/HORDE_DEFINITIONS.md).
Vanilla 1.12.2 attribute, equipment, and spawn initialization behavior is
recorded in
[`docs/MOB_ATTRIBUTES_AND_EQUIPMENT_1.12.2.md`](docs/MOB_ATTRIBUTES_AND_EQUIPMENT_1.12.2.md).
Weighted zombie stat and equipment profiles are documented in
[`docs/ZOMBIE_VARIATIONS.md`](docs/ZOMBIE_VARIATIONS.md), including namespaced
modded items, metadata, stack NBT, and drop chances.

Server operators can inspect population state with:

```text
/zomboid population stats
```

After editing the Forge configuration, horde XML files, or
`config/zomboid/variations.xml`, an operator can rebuild the live population:

```text
/zomboid population regenerate
```

This reloads the definitions, removes loaded zombies and managed horde mobs,
clears saved population nodes across the entire active world save, and queues
loaded chunks for budgeted respawning with the new configuration.

## Suggested next steps

- Add loud firearms and alarm integrations through `NoiseManager.recordNoise`
- Add window/door thumping and barricade damage
- Add bite wounds and a configurable infection system
- Add migration and meta-event hordes
- Add GameStages or difficulty-based presets

## License

Zomboid is licensed under the GNU General Public License v3.0. See
[`LICENSE`](LICENSE) for the complete terms.

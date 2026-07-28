# Zomboid Zombies

A Minecraft Forge 1.12.2 starter mod that makes vanilla zombies behave more
like Project Zomboid's shamblers.

The long-term AI requirements and system boundaries are recorded in
[`docs/AI_DESIGN_DIRECTIVE.md`](docs/AI_DESIGN_DIRECTIVE.md).

## Included behavior

- Slower, configurable shambler movement
- Longer player detection range
- Attraction to sprinting, block breaking/placing, and combat noise
- Pursuit of a player's last known position after line of sight is lost
- Horde alerting when one zombie acquires a player target
- Wooden-door breaking AI
- Optional immunity to daylight burning

The behavior applies to `EntityZombie` and its vanilla subclasses. It does not
replace the zombie entity, so vanilla textures, drops, spawning, and mod
compatibility are retained.

## Requirements

- Minecraft 1.12.2
- Forge 14.23.5.2859 (the official recommended 1.12.2 build)
- A Java 8 JDK for Gradle and the development client

Java 8 is already installed on this machine at:

`C:\Program Files\Eclipse Adoptium\jdk-8.0.462.8-hotspot`

PowerShell setup for the current terminal:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-8.0.462.8-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat build
```

Generate IDE run configurations:

```powershell
.\gradlew.bat genIntellijRuns
# or
.\gradlew.bat genEclipseRuns
```

The built mod jar is written to `build/libs/`.

## Configuration

Launch the game once, then edit:

`config/zomboidzombies.cfg`

The config controls movement speed, follow range, memory duration, noise
ranges, horde alert range, door breaking, and daylight immunity.

## Suggested next steps

- Add loud firearms and alarm integrations through `NoiseManager.recordNoise`
- Add window/door thumping and barricade damage
- Add bite wounds and a configurable infection system
- Add migration and meta-event hordes
- Add GameStages or difficulty-based presets

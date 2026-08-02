# AI Improvements compatibility

Status: Optional companion; not bundled or required

Zomboid now has a runtime compatibility layer for this optional mod. It checks
the `aiimprovements` mod ID without a compile dependency and performs Zomboid's
zombie-join setup at Forge's lowest event priority. This lets AI Improvements'
normal-priority look-helper replacement and configured task removals happen
first. Zomboid observes and logs the replacement, but does not overwrite it or
restore tasks that the server owner chose to remove.

Official distribution:

- <https://www.curseforge.com/minecraft/mc-mods/ai-improvements>
- Project ID: `233019`
- Minecraft 1.12 file: `AIImprovements-1.12-0.0.1b3.jar`
- Runtime mod ID: `aiimprovements`

## What the 1.12 release changes

The public 1.12 branch replaces an entity's exact vanilla
`EntityLookHelper` with `FixedEntityLookHelper` when the entity joins a world.
The replacement uses a cached `atan2` table. Two optional settings remove
`EntityAIWatchClosest` and `EntityAILookIdle` tasks.

It does not replace `PathNavigate`, Minecraft's A* implementation, or
Zomboid's pursuit scheduler. Zomboid must therefore continue to own its
path-request budgets, crowd coordination, stuck detection, and any later route
solver changes.

## Recommended settings with Zomboid

- `ReplaceLookHelper=true`: compatible starting point; benchmark before and
  after because the project's 1.12 changelog describes only a small tangent
  cache gain on that Minecraft version.
- `RemoveEntityAIWatchClosest=false`: preserves nearby-player watch behavior.
- `RemoveEntityAILookIdle=false`: preserves stationary horde head movement.

Zomboid's initial player detection uses zombie head orientation. Removing the
look tasks changes visible idle awareness, so those settings should remain an
explicit server-owner tradeoff rather than a Zomboid default.

The compatibility layer deliberately does not change Zomboid's player sensor,
pursuit task, path scheduler, brain states, or horde logic. AI Improvements can
optimize its own supported low-level behavior while Zomboid remains the sole
owner of its gameplay decisions.

## Distribution and licensing boundary

The repository's current default branch displays an MIT license, but the
separate `1.12` branch at commit
`031ee3a3939bb7822422863148730ee38c3f9a88` contains no `LICENSE` file. The
CurseForge project currently labels the distributed project All Rights
Reserved and states that modpack distribution is allowed when downloads come
from CurseForge or its Modrinth mirror.

Because those signals do not establish an unambiguous MIT grant for the 1.12
artifact, Zomboid does not copy its source, compile against it, shade it, or
redistribute its JAR. Documentation links users to the official project, which
preserves the author's downloads and avoids making Zomboid dependent on the
uncertain licensing boundary.

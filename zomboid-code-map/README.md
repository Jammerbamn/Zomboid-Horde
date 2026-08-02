# Zomboid Code Map

This folder contains a Graphify knowledge map of the Zomboid mod's Java code.
It sits beside `minecraft-mob-system-map/` so mod and vanilla architecture can
be inspected independently.

## Open and query the map

Open `graphify-out/graph.html` in a browser. From this directory, queries use
the Zomboid graph by default:

```powershell
graphify query "How is a seeded horde planned and materialized?"
graphify path "PopulationCommand" "ZombiePopulationData"
graphify explain "PopulationManager"
```

The current map contains 1,823 nodes, 4,180 relationships, and 111 communities.

## Corpus scope

The local `corpus/` snapshot contains the Java sources under `src/main/java`
and `src/test/java`. Generated Forge sources, build output, logs, runtime
worlds, IntelliJ metadata, resources, and the vanilla Minecraft map are not
included.

The corpus and large generated graph artifacts are ignored by Git. The plain
language report and graph-health diagnostic remain tracked for project review.

To refresh the snapshot after code changes, recreate the local corpus from
the two Java source trees and run:

```powershell
graphify extract corpus --out . --code-only
```

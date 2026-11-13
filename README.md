# For Reals

## Main Focus

### 1. More real biomes

I want biomes to feel more real and less dead. I want better variety, and better biome sizes.

### 2. Weather you need to observe

Real weather cycles - winter, spring, summer, fall - and weather that you have to plan for.

### 3. Ecosystems

Animal herds and predators that keep each other in balance.

## Development

Fabric mod for Minecraft 26.3. All logic runs on the server, so it works on a dedicated server and in singleplayer (which runs an integrated server). Players joining a dedicated server do not need the mod installed.

Requires Java 25.

- `./gradlew runClient` launches the game with the mod for singleplayer testing.
- `./gradlew runServer` launches a dedicated server with the mod. Accept the EULA in `run/eula.txt` on first launch.
- `./gradlew build` writes the mod jar to `build/libs/` and runs the unit tests.
- `./gradlew runGameTest` runs the game tests in a headless server.

The design is in [docs/](docs): what vanilla gets wrong, the principles, and the plan.

## Installation

Put the mod jar and the Fabric API jar in the `mods/` folder of a Fabric 26.3 server or client instance.

Installing the mod changes nothing by itself. Each world chooses whether to use it, through a data pack named `forreals:core` that ships inside the jar and is off by default. A world with the pack off plays as vanilla.

- **New singleplayer world:** on the Create World screen, open Data Packs and move For Reals to the selected side.
- **New server world:** before the first start, add `forreals:core` to `initial-enabled-packs` in `server.properties`.
- **Existing world or server:** run `/datapack enable "forreals:core"`, then close the world or restart the server. The mod starts when the world next loads.

Choose once per world. A world that turns the pack off again keeps the new biomes in the chunks it already generated and loses its wild herd animals.

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

Installing the mod changes nothing by itself. Each world chooses whether to use it.

## Turning it on for a world

The switch is a data pack named `forreals:core` that ships inside the jar. It is off by default, and a world with it off plays as vanilla. Minecraft saves the choice in the world.

- **New singleplayer world:** on the Create World screen, open Data Packs and move For Reals to the selected side.
- **Existing singleplayer world:** run `/datapack enable "forreals:core"`, then save, quit, and reopen the world. The command needs cheats. In a world without them, use Open to LAN with cheats on first.
- **New server world:** before the first start, add `forreals:core` to `initial-enabled-packs` in `server.properties`.
- **Existing server:** run `datapack enable "forreals:core"` in the console, then restart.

The mod starts the next time the world loads, not when the pack is enabled. The log then says `For Reals is on for this world`.

Choose once per world. A world that turns the pack off again keeps the new biomes in the chunks it already generated and loses its wild herd animals.

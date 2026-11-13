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

Install the Fabric server launcher for 26.3, then put the mod jar and the Fabric API jar in the server's `mods/` folder.

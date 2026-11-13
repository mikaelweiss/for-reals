# For Reals

Fabric mod for Minecraft 26.3. All logic runs on the server, so it works on a dedicated server and in singleplayer (which runs an integrated server). Players joining a dedicated server do not need the mod installed.

## Develop

Requires Java 25.

- `./gradlew runClient` launches the game with the mod for singleplayer testing.
- `./gradlew runServer` launches a dedicated server with the mod. Accept the EULA in `run/eula.txt` on first launch.
- `./gradlew build` writes the mod jar to `build/libs/`.

## Install on a server

Install the Fabric server launcher for 26.3, then put the mod jar and the Fabric API jar in the server's `mods/` folder.

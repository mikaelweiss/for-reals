# Plan

Every behavior change, and how it is built. Problems are in [problems.md](problems.md). Principles are in [principles.md](principles.md).

## Foundations

Four pieces. Everything else reads from them.

- **Calendar.** Day of year, derived from the overworld clock. A year is 48 days, 12 per season.
- **Climate.** One function that returns the temperature at a place and time.
- **Weather field.** Storms and air masses computed from the world seed, place, and time. It holds no state, so it can be evaluated for any future moment.
- **Herd ledger.** Every wild herd on the map, saved with the world, simulated cheaply when nobody is near.

## Switch

**Each world chooses whether to use the mod.** The mod ships a data pack that is off by default. Minecraft saves the choice in the world. With the pack off, every rule below is off and the world plays as vanilla. The choice takes effect when the world loads.

## Time

1. **A year has four seasons.** The calendar counts days on the overworld clock.
2. **Summer days are long. Winter nights are long.** The clock runs faster or slower through the day and night halves, by season.
3. **Sleeping fast-forwards the night.** While enough players sleep, the clock runs fast and plant growth runs at the same multiple. Storms continue. Sleeping never clears weather.
4. **An empty server pauses everything.** With no player online, the clock stops and no simulation runs.

## Climate

5. **Every place has a temperature.** It starts from the biome's yearly average. Season, time of day, altitude, the current air mass, and shelter adjust it. A roof dampens swings. Fire, lava, and lit furnaces add heat.
6. **Snow settles anywhere below freezing.** Snowfall piles up in layers during storms and melts when it warms. A chunk that loads after a long absence gets its snow corrected before a player sees it.
7. **Water freezes in cold spells and thaws after.**
8. **Grass and leaves change color with the season.** Each biome has seasonal variants. Clients receive the variant in place of the real biome. The server keeps the real one. Local temperature picks the variant, so mountains turn first.
9. **Players have body temperature.** Wet, exposed, and night make you cold. Fire, shelter, and leather armor warm you. Shade and water cool you.
10. **Cold and heat warn, then slow, then hurt.** Stages show as frost on screen and a short note. Damage follows the starvation rule: none on Peaceful, stops at half health on Easy, at one heart on Normal, kills on Hard.
11. **Deep snow slows you.** Leather boots fix it.

## Weather

12. **Weather is regional.** Storm systems drift across the map with the wind. Each player sees the weather where they stand.
13. **Storms build.** The sky dims and rain thickens over minutes as a front arrives.
14. **Rain has strength**, from drizzle to downpour.
15. **Weather follows climate and season.** Deserts stay dry. Summer brings thunderstorms. Winter brings blizzards.
16. **Warm and cold spells last days.** Storms form where they meet, so a falling temperature means a storm is coming.
17. **Rain, snow, and lightning only happen where the storm is.** Farmland, fire, and lightning strikes read the local weather.

## Plants

18. **Crops grow by temperature.** Each crop has a range. Nothing grows below freezing.
19. **Hard frost kills exposed crops.** Roofed or heated crops survive.
20. **Crops and saplings keep growing while you are away.** Each chunk remembers when it was last active and catches up on load.
21. **Wild grass and flowers sprout in spring and die back in winter.** Sprouts come from the biome's own plant list.
22. **Trees drop saplings.** Logged forest regrows. Crowding and biome limit the spread.

## Animals

23. **Wild animals live in herds across the whole map.** The ledger holds each herd. Its animals appear when their area loads and fold back into the ledger when it unloads. Vanilla spawning is off for these species.
24. **Animals get hungry.** Grazers eat grass, plants, and unfenced crops. Starving animals die.
25. **Herds graze a patch and move on.** They migrate with the seasons toward food.
26. **Animals breed on their own in spring when well fed.** They age and die.
27. **Predators hunt only when hungry.** Wolves hunt as a pack.
28. **Herds flee predators and players.** Hunted herds grow warier.
29. **Starving predators hunt players.** Fire keeps them off. Howling warns you first.
30. **Overhunted land refills slowly from neighboring herds.** Unexplored land acts as a reservoir.
31. **Animals you feed, leash, name, or tame are yours.** They stay put and do not flee. They still need grass or hay bales. On first load in an existing world, animals near a fence count as yours.
32. **Herds bunch up shortly before a storm hits.**

Fish and water life stay vanilla.

## Monsters

33. **No hostile mobs spawn under open sky in the overworld.** Phantoms, patrols, zombie sieges, and lightning skeleton traps are off. Caves, the Nether, the End, pillager outposts, witch huts, and raids are unchanged.

## New terrain

These apply to chunks generated after install.

34. **Climate zones are bigger and vary in size.** Temperature and humidity change gradually, so hot and cold biomes never touch. Terrain shape is unchanged, so old and new chunks meet without cliffs.
35. **Coasts are wet, interiors are dry.**
36. **Vegetation is patchy.** Clearings, groves, and thicker growth near water.
37. **Biome borders blend into each other.**
38. **New in-between biomes:** steppe, scrubland, prairie, marsh, mixed forest, dry woodland, temperate rainforest, tundra.

## Limits

- Birch and spruce leaves have fixed colors in the client. Only oak-type leaves and grass change with the season.
- Bedrock players see the world's real biomes, with no seasonal colors. Where Java players see snow fall outside snowy biomes, Bedrock players see rain. Snow on the ground shows for everyone.
- The game has no wolf howl sound. A hungry pack is heard as a deep bark that carries.
- Only wild animals die of old age. Owned animals do not.
- Bees and other animals that react to rain do not react to local weather.
- A thunderstorm does not let a player sleep in the daytime.
- Marsh has no standing pools of water.

## Tests

- Unit tests cover the calendar, climate, weather field, and herd math.
- Fabric game tests cover behavior in a running world.

## Build order

Each step lands working, with tests: monsters, time, climate, weather, plants, animals, terrain.

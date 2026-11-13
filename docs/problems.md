# Why vanilla falls short

Vanilla builds the world once, then freezes it.

- **Static.** Nothing changes unless a player changes it.
- **Disconnected.** Weather, plants, and animals do not affect each other.
- **Ignorable.** Nothing costs you anything for not paying attention.

## Biomes

- **Uniform inside.** Same tree density and plants edge to edge. No clearings, groves, or gradients.
- **Hard borders.** Biomes switch on a line, not a transition zone.
- **Climate is noise, not geography.** No latitude, no rain shadow, no wetter coasts. Neighbors feel arbitrary.
- **Sizes are samey.** Most biomes are a few hundred blocks across. No vast plains, no tiny groves.
- **Plants never live.** Trees do not seed, grass and flowers do not regrow, nothing dies.
- **Empty of life.** Animals spawn at world generation and almost never again.

## Weather

- **No seasons.** A biome's temperature is fixed forever. There is no calendar.
- **Global.** It rains across the whole dimension at once. No fronts, no storms that move.
- **Random.** Timers roll dice, so there is nothing to observe or predict.
- **No stakes.** Rain barely matters. Crops grow the same always.
- **The player has no body.** Heat and cold cannot touch you.
- **Skippable.** Sleeping clears it.

## Ecosystems

- **No needs.** Animals never get hungry, so prey does not graze and predators do not need to hunt.
- **No births or deaths without you.** Animals breed only when a player feeds them. Populations only go down.
- **No herds.** Animals spawn in groups, then wander apart at random. No migration.
- **Predation is a one-way drain.** Wolves kill nearby sheep until none are left. Nothing balances it.
- **Fixed aggression.** Predators attack always, never, or when hit. Hunger and threat play no part.
- **Prey is oblivious.** Most animals only react after being hit.
- **No recovery.** A herd you thin never grows back.
- **Only exists near players.** Unloaded chunks freeze, so no population can live on its own.

## Food

Food is disconnected from the world.

- Crops grow at the same speed in any biome, season, or weather.
- Penned animals need no feed and no space.
- Once you have a farm, winter and herds stop mattering.

## Constraints

The mod ships in two layers.

- **Core** holds every rule and runs on the server alone. Vanilla Java clients and Bedrock clients (through Geyser) can join. It uses only vanilla blocks, items, and mobs.
- **Extras** is an optional add-on built on Core. It adds new animals, blocks, and HUD. Every player needs Java with the mod installed.

Bedrock clients do not show custom biome grass and leaf colors, and cap world height at Y -512 to 512.

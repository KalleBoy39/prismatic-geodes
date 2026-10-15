# How Prismatic Geodes works

Technical notes for developers and pack makers. Players don't need any of this.

**Mod ID:** `vplus_geodes` (Mini Blocks addon: `vplus_geodes_miniblocks`). The IDs come from the project's working
title and are kept so that worlds made with earlier builds keep their blocks and items. See
[CONTRIBUTING.md](../CONTRIBUTING.md#ids-that-must-not-change).

## Content
Purple **is** vanilla amethyst. The other 15 dye colours are complete amethyst families: block, budding block, three
bud sizes, cluster and shard (`vplus_geodes:<colour>_amethyst_block`, `<colour>_budding_amethyst`,
`small_<colour>_amethyst_bud`, `medium_…`, `large_…`, `<colour>_amethyst_cluster`, `<colour>_amethyst_shard`).

- **Blocks:** each one is registered with `BlockBehaviour.Properties.ofFullCopy(<vanilla amethyst block>)`.
  The only change is the map colour, which follows the dye colour. Buds and clusters are plain vanilla
  `AmethystClusterBlock`s with vanilla's sizes.
- **Growth:** `ColouredBuddingAmethystBlock` extends vanilla `BuddingAmethystBlock`. It overrides only
  `randomTick`, using vanilla's algorithm line for line with the family's own four growth stages.
  Vanilla hard-codes purple buds in that method.
- **Worldgen:** pure data, no mixins and no biome modifications. The mod overrides
  `data/minecraft/worldgen/feature/amethyst_geode.json` with a vanilla
  `minecraft:simple_random_selector` over 16 geode features
  (`vplus_geodes:amethyst_geode/<colour>`). Each one is vanilla's geode config with the amethyst
  blocks swapped, and `purple` is an exact copy of vanilla. The vanilla *placed* feature
  (rarity 1/24, y range, biome filter) is untouched, so every vanilla geode attempt still happens
  exactly once and picks one colour uniformly (1/16 each).
- **Recipes:** 4 shards → block per colour. The vanilla recipes for tinted glass, spyglass and calibrated sculk sensor
  are overridden to take any colour of shard (item tag).
- **Trims:** every shard is a trim material with its own palette. The vanilla armour item models and the items atlas
  are overridden so the 15 new materials render on armour items too.

## Files the mod overrides in the `minecraft` namespace
`worldgen/feature/amethyst_geode.json`, the recipes `tinted_glass`, `spyglass` and `calibrated_sculk_sensor`, the
trimmable armour item models, the items atlas and a few tags. Another mod, data pack or resource pack that replaces
the same files will conflict; only one of them can win.

## World safety
- The mod never reads, rewrites, scans or converts existing chunks. It only runs inside world
  generation, so only chunks generated after installing it can contain coloured geodes.
  Existing purple geodes stay vanilla blocks.
- **Once coloured geodes have generated, or coloured blocks or items exist in the world, the world
  depends on this mod.** If the mod is removed, those blocks become air and the items disappear
  when loaded. Vanilla purple geodes are unaffected either way.

## Tests
`gradlew build` runs 18 server game tests: block properties, shapes and waterlogging match vanilla, growth in the
own colour only, drops match vanilla, the worldgen selector, recipes (matching and mixed shards, any-colour vanilla
recipes, unlocking) and trims (each shard is its own material, all templates and armour, vanilla materials unchanged,
save/load). `gradlew runClientGameTest` starts a real client in a throwaway world and takes
screenshots of all families and trims. The Mini Blocks addon has its own 5 server game tests and a client test.

## Resources
Everything under `src/main/resources/{assets,data}` was generated from vanilla 26.3 files and the hand-approved
colour textures by a development script, and is committed as-is. `fabric.mod.json` is hand-written.

# Changelog

## Prismatic Geodes 1.0.0+26.3 (first public release)
- Amethyst geodes generate in all 16 dye colours, one colour per geode, each colour equally likely.
- Purple is vanilla amethyst; 15 new colour families: block, budding block, small/medium/large bud, cluster, shard.
- Vanilla geode rarity, height range and placement are unchanged; only newly generated chunks are affected.
- Coloured budding blocks grow buds and clusters of their own colour, at vanilla speed and with vanilla rules.
- Drops, Fortune and Silk Touch behave like vanilla amethyst.
- 4 shards of one colour craft into the block of that colour.
- Spyglass, tinted glass and calibrated sculk sensor accept amethyst shards of any colour.
- Every shard colour is an armour trim material, shown in its own colour on armour and armour items.
- Coloured blocks keep vanilla amethyst behaviour (sounds, chimes, vibrations, allay duplication) and use their dye
  colour on maps.

## Prismatic Geodes: Mini Blocks 1.0.0+26.3 (first public release)
- Mini blocks (decorative heads) for the 15 coloured amethyst blocks: 1 block → 8 mini blocks in a stonecutter.
- Matches the Vanilla Tweaks Mini Blocks data pack; textures recoloured from its Mini Amethyst Block.
- No purple mini block, because Vanilla Tweaks already has one.
- Includes the Vanilla Tweaks credit (`credits.txt`).

## Prismatic Geode Locator 1.0.0 (first public release)
- Windows program with a browser map: download, unzip, run. Java is included.
- Terrain map computed from the seed with Minecraft's own world generation: biomes, land, water, height shading.
- Crystal markers in the 16 geode colours, with exact coordinates, approximate centre, chunk, copy buttons and a
  ready `/tp` command.
- Colour filters, chunk grid, cursor read-out of coordinates, biome and height.
- **Verify visible area**: checks which geodes really formed by building the area in a hidden local test world;
  results are cached.
- First start downloads the official Minecraft 26.3 server (Mojang) and Fabric files, checked against fixed
  checksums, after the user accepts the Minecraft EULA.
- Predicts Prismatic Geodes 1.0.0+26.3 worlds of the default world type.

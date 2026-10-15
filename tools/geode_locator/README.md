# Prismatic Geode Locator

A local, Chunkbase-style map of amethyst geodes and their exact Prismatic Geodes colour for Minecraft 26.3, drawn on
a terrain map computed from the seed. Players download the ready-made Windows ZIP; this folder is its source.

## Build (Windows)
```
gradlew build                                   # in the repository root: builds the Prismatic Geodes jar
powershell -ExecutionPolicy Bypass -File tools\geode_locator\build-public.ps1 -Jdk <JDK 25> -Out <folder>
```
The JDK must be a full JDK 25 with `jlink` and `jpackage` (for example Eclipse Temurin 25). The script:
1. downloads its compile-time inputs once into `runtime/build-deps` and checks them against pinned hashes:
   the Minecraft 26.3 server (Mojang), Fabric Loader 0.19.5 and Fabric API 0.162.0+26.3 (FabricMC).
   They are never put into the output;
2. compiles `src/` (backend), `launcher/` (window, EULA consent, first-run downloads) and `probe-mod/`;
3. makes a reduced Java runtime with `jlink` and a native `Prismatic Geode Locator.exe` with `jpackage`;
4. copies the user docs next to the exe, runs a privacy audit and writes
   `Prismatic-Geode-Locator-<version>-windows-x64.zip`.

## What it shows

| Marker | Meaning |
|---|---|
| crystal icon | **Predicted**: geode attempt computed from the seed. Exact origin and colour; terrain not checked. |
| crystal + green tick | **Confirmed**: the geode was generated in the shadow world. |
| faded crystal + grey cross | **Stopped by terrain**: the attempt exists, but `GeodeFeature` found too much air or fluid and placed nothing. |
| small diamonds | zoomed out: same markers as dots; hidden when they would cover the map |
| light veil | area already verified in the shadow world (cached) |

**Terrain** (`Terrain.java`): Minecraft's own overworld noise from the vanilla data pack, no chunk generation. Per
tile: biome every 4 blocks (or 1 px), exact base height (`OCEAN_FLOOR_WG`) on a coarser grid, interpolated; land/water
from height + biome; hillshade, coastline darkening. 128 px tiles, 2^L blocks per pixel (L 0..5), cached in memory
and in `<data>/cache/tiles-v1`. Checked against a really generated world (devtools `TerrainCheck`, `WaterProbe`):
biome 100 %, land/water ~97 %, height within 3 blocks 86 %.

**Coordinates.** Markers sit on the **feature origin**, the `BlockPos` passed to `GeodeFeature.place`. It is exact
and comes straight from the RNG. The visible geode body is centred about **origin + 5** on X, Y and Z (measured
mean +4.4 / +4.6 / +4.4, range about 0 to +9). The popup shows the origin first and the approximate centre second.

## How the prediction works (Tier 1, instant)

Vanilla 26.3 `ChunkGenerator.applyBiomeDecoration`, per chunk:

1. `WorldgenRandom(Xoroshiro).setDecorationSeed(worldSeed, chunkMinX, chunkMinZ)`
2. `setFeatureSeed(decorationSeed, index, step)` for placed feature `minecraft:amethyst_geode`:
   step 2 (`LOCAL_MODIFICATIONS`), global index 2. The probe reads both live from `featuresPerStep`.
3. Placement chain (untouched by the mod): `rarity_filter` `nextFloat() < 1/24`, `in_square` `nextInt(16)` X then
   Z, `height_range` uniform Y in -58..30, then `biome` (no RNG, and every overworld biome has the feature).
4. Prismatic Geodes replaces the *configured* feature `minecraft:amethyst_geode` with a `simple_random_selector`.
   It draws `nextInt(16)` from the **same** RNG and picks
   `white, orange, magenta, light_blue, yellow, lime, pink, gray, light_gray, cyan, purple, blue, brown, green, red, black`
   (order read from the mod jar at start-up).

The backend calls Minecraft's own `WorldgenRandom`/`XoroshiroRandomSource` classes, so the prediction cannot drift
from the game's arithmetic. Only terrain decides whether an attempt survives: `GeodeFeature` samples 3–4 points
4–6 blocks from the origin and aborts if more than one is air, water, lava, ice or bedrock. About 9–10 % of
attempts abort.

## Verify visible area (Tier 2, on demand)

Nothing is generated unless you press the button. It then:

- starts a disposable Fabric server in `<data>/shadow-server` (same Minecraft, Fabric API and Prismatic Geodes jar,
  plus the read-only probe), bound to 127.0.0.1, with one `world_<seed>` folder per seed,
- force-generates the visible chunks plus a 1-chunk margin (at most 64 × 64 chunks per press),
- reads the saved blocks and marks each attempt **confirmed** (at least 20 blocks of its colour in the box
  origin −3..+13) or **aborted**,
- caches the result in `world_<seed>/locator_verified.json`, so verified chunks are never generated again.

The shadow server stops after 15 idle minutes and when the locator exits. It never opens the player's own worlds.

Caveat: vanilla feature placement depends a little on the order in which neighbouring chunks are generated. For
example, a neighbour's mineshaft or geode carved first can tip the abort check. A geode that is confirmed in the
shadow world is therefore very likely, but not guaranteed, to appear in a world that generates the same chunks in
a different order. Measured before release with independent generations of 3 seeds: 738/738 found geodes sat at a
predicted origin with the predicted colour, and 526/526 Tier 2 results matched.

## Files

- `src/dev/vplus/locator/`: backend (`Predictor` Tier 1, `Verifier` + `ShadowServer` Tier 2, `Terrain` +
  `BiomeColours` map tiles, `GeodeScan`/`Anvil`/`Nbt`/`Matcher` region readers, `Main` HTTP server on 127.0.0.1,
  `Extension` hook for optional extra layers)
- `launcher/`: public launcher (`Launcher` window + EULA consent, `Setup` first-run downloads with pinned hashes),
  `log4j2.xml`
- `probe-mod/`: shadow-server-only Fabric mod. It writes `locator_probe.json` with the live step, index,
  selector order and biome coverage
- `web/`: the map UI (`app.js` exposes `window.Locator` hooks for extensions)
- `devtools/`: development checks, never shipped: `TerrainCheck`, `SurfaceProbe`, `WaterProbe` (terrain
  accuracy/speed), `UiCheck.java` (headless Edge UI test), `MakeIcon.java` (draws `locator.ico` / `web/icon.png`)
- `build-public.ps1`: public build + privacy audit + ZIP

The Java packages are called `dev.vplus.*` after the project's working title. That name is internal only.

# Licences and third-party notices

The [LICENSE](LICENSE) file (MIT) covers **only the original code and project files written for Prismatic Geodes**.
It does **not** cover Minecraft-derived assets, Vanilla Tweaks-derived assets, screenshots/artwork or bundled
third-party software. Those keep their own terms, listed below. Nothing here claims ownership of Minecraft or
Vanilla Tweaks content.

## Overview

| | What | Licence / terms |
|---|---|---|
| **A** | Original source code and project files written for this project | **MIT** ([LICENSE](LICENSE)) |
| **B** | Textures and data derived from Minecraft | Mojang / Microsoft terms, **not MIT** |
| **C** | Mini Blocks head textures (and the matching recipe/advancement files) derived from Vanilla Tweaks Mini Blocks | Vanilla Tweaks terms, with required credit, **not MIT** |
| **D** | Bundled Java runtime and external dependencies | their own licences, **not MIT** |
| **E** | Screenshots, logo and cover artwork in `docs/images/` | **not MIT**, see below |

## A. Original code: MIT
Covered by the MIT licence in [LICENSE](LICENSE), Copyright (c) 2026 KalleBoy39:
- all Java source code (`src/main/java`, `src/gametest`, `compat/miniblocks/src/gametest`, and `src`, `launcher`,
  `probe-mod` and `devtools` under `tools/geode_locator/`),
- build files and scripts (Gradle build files, `tools/geode_locator/build-public.ps1`,
  `compat/miniblocks/tools/generate.py`),
- the locator's web interface (`tools/geode_locator/web/`) and its original crystal icon
  (`tools/geode_locator/locator.ico`),
- the `fabric.mod.json` files, language files, and the block-state, model, loot-table, tag, recipe and
  trim-material JSON files under `src/main/resources/assets/vplus_geodes/`, `src/main/resources/data/vplus_geodes/`
  and `src/main/resources/data/c/`, except the files listed under B,
- documentation and issue templates.

The Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) is by Gradle Inc. under the Apache License 2.0.

## B. Minecraft-derived content: Mojang / Microsoft terms, not MIT
Minecraft is © Mojang AB / Microsoft. These files are derived from Minecraft 26.3 assets or data. They remain subject
to the Minecraft EULA and Usage Guidelines (https://www.minecraft.net/eula,
https://www.minecraft.net/usage-guidelines), are distributed only as part of a free mod, and are **not** relicensed
under MIT:
- every texture in `src/main/resources/assets/vplus_geodes/textures/` (coloured amethyst blocks, budding blocks,
  buds, clusters, shards and trim palettes, recoloured from Minecraft's amethyst textures),
- the mod icons `src/main/resources/assets/vplus_geodes/icon.png` and
  `compat/miniblocks/src/main/resources/assets/vplus_geodes_miniblocks/icon.png`,
- files that override or copy vanilla files: everything under `src/main/resources/assets/minecraft/` (items atlas,
  armour item definitions) and `src/main/resources/data/minecraft/` (geode feature, three vanilla recipes and their
  advancements, tags),
- the per-colour geode feature settings in `src/main/resources/data/vplus_geodes/worldgen/`, which reuse vanilla's
  own geode configuration.

## C. Vanilla Tweaks-derived content: Vanilla Tweaks terms, not MIT
The **15 mini block head textures** of *Prismatic Geodes: Mini Blocks*
(`compat/miniblocks/src/main/resources/assets/vplus_geodes_miniblocks/textures/entity/mini_block/`) are recoloured
from the **Mini Amethyst Block** of the Vanilla Tweaks "Mini Blocks" data pack (v1.1.8). Their pixel pattern is
Vanilla Tweaks' work; the base colours come from Minecraft's amethyst block (see B). The addon's recipe and
advancement files (`compat/miniblocks/src/main/resources/data/vplus_geodes_miniblocks/`) follow the same pattern as
the Vanilla Tweaks Mini Blocks recipes, so that the two fit together. These files are used and redistributed under the
Vanilla Tweaks terms (https://vanillatweaks.net/terms/: modification and/or additions, free of charge, with credit)
and are **not** relicensed under MIT.

Vanilla Tweaks is credited specifically because these textures are based on, and recoloured from, its Mini Amethyst
Block. This project does not claim that the general idea of mini blocks comes from Vanilla Tweaks, or from itself.

Required credit (included as `credits.txt` in the Mini Blocks jar, and on every publishing page):
```
Credits:
Vanilla Tweaks: https://vanillatweaks.net/
```

The unmodified Vanilla Tweaks template image that the textures are generated from is **not** in this repository and
not in any download. Vanilla Tweaks is not affiliated with this project and does not support it.

## D. Bundled runtime and external dependencies: their own licences, not MIT

### Prismatic-Geode-Locator-1.0.0-windows-x64.zip
| Contents | Source | Terms |
|---|---|---|
| `runtime\` Java runtime | Eclipse Temurin 25.0.4.1+1 (Adoptium), reduced with `jlink` | GNU GPL v2 with the Classpath Exception, plus the third-party licences in `runtime\legal\`. Corresponding source: https://github.com/adoptium/temurin25-binaries/releases/tag/jdk-25.0.4.1%2B1 (`OpenJDK25U-jdk-sources_25.0.4.1_1.tar.gz`) |
| `Prismatic Geode Locator.exe` | native launcher generated by the JDK tool `jpackage` | part of the OpenJDK (GPL v2 with the Classpath Exception) |
| `app\lib\prismatic-geodes-1.0.0+26.3.jar` | this project | code: MIT (A); textures and vanilla-derived data: B |
| `app\locator.jar`, `app\lib\locator-core.jar`, `app\lib\locator-probe.jar`, `app\web\` | this project | MIT (A) |

**Downloaded by the locator on first start, not included in the ZIP**, each checked against a fixed checksum:
| File | From | Terms |
|---|---|---|
| Minecraft 26.3 server (`server.jar`) and the libraries inside it | Mojang, `piston-data.mojang.com` | Minecraft EULA, accepted by the user in the locator before download |
| Fabric server launcher (Fabric Loader 0.19.5, installer 1.1.2) | FabricMC, `meta.fabricmc.net` | Apache-2.0 |
| Fabric API 0.162.0+26.3 | FabricMC, `maven.fabricmc.net` | Apache-2.0 |

The locator uses the Minecraft server's own classes and libraries (for example Gson and fastutil) at runtime, loaded
from that download.

### The mod jars
`prismatic-geodes-1.0.0+26.3.jar` and `prismatic-geodes-miniblocks-1.0.0+26.3.jar` contain no third-party libraries.
They need Minecraft 26.3, Fabric Loader and Fabric API (Apache-2.0) at runtime, which are **not included**. Each jar
contains the MIT licence and this file; the MIT licence applies to their code (A), not to the content listed under
B and C.

## E. Screenshots and artwork
The images in `docs/images/` (screenshots and the Prismatic Geodes logo/cover artwork) are not covered by the MIT
licence. They show Minecraft game content (© Mojang / Microsoft); the project's own parts of them are © 2026
KalleBoy39, all rights reserved. They may be used when showing or writing about Prismatic Geodes.

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.

# Credits

## People
- **KalleBoy39** (project author): concept, gameplay direction, visual decisions (every colour palette was chosen
  and approved by hand) and testing.

## Implementation
Developed with extensive assistance from **Claude Code**, Anthropic's AI coding assistant. This covers Prismatic
Geodes, the Mini Blocks addon and the Prismatic Geode Locator.

I want to be open about how this was made. I did not hand-write all of the implementation myself: most of the Java
code, the generated data files and textures, the automated tests and the locator were written with Claude Code,
following my direction. I decided what the project should do and how it should look, chose and approved every
colour, judged the results and tested it in game.

Prismatic Geodes started as a personal Minecraft Vanilla+ survival project. It is released so that other players
can use the same idea without having to build it themselves.

## Minecraft (Mojang Studios)
- Minecraft is © Mojang AB / Microsoft. This project is not an official Minecraft product and is not approved by or
  associated with Mojang or Microsoft.
- The coloured amethyst textures (blocks, buds, clusters, shards) and trim palettes are **recoloured from Minecraft's own
  amethyst textures**. The geode generation reuses vanilla's own geode settings. All rights to the original game assets
  remain with Mojang, and these derived files are not covered by the project's MIT licence
  (see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md), section B).
- The locator downloads the official Minecraft 26.3 server from Mojang on first start, after the user accepts the
  Minecraft EULA (https://www.minecraft.net/eula). The server is not included in the download.

## Vanilla Tweaks (Mini Blocks addon)
The 15 head textures of *Prismatic Geodes: Mini Blocks* are **based on, and recoloured from, the Vanilla Tweaks Mini
Amethyst Block** (from the Vanilla Tweaks "Mini Blocks" data pack). That is why Vanilla Tweaks is credited here. The
textures are used under the Vanilla Tweaks terms (https://vanillatweaks.net/terms/) and are not covered by the
project's MIT licence (see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md), section C). The addon is made to sit
naturally next to the Vanilla Tweaks Mini Blocks data pack, and works with or without it.

```
Credits:
Vanilla Tweaks: https://vanillatweaks.net/
```

Vanilla Tweaks is not affiliated with this project and does not support it.

## Fabric
- **Fabric Loader** and **Fabric API** by the FabricMC project (https://fabricmc.net/), Apache License 2.0.
  The mods depend on them; they are not included in the mod jars. The locator downloads the Fabric server launcher and
  Fabric API from FabricMC on first start for its local verification server.

## Java runtime (locator only)
The locator includes a Java runtime built from **Eclipse Temurin 25** (https://adoptium.net/), licensed under the
GNU General Public License version 2 with the Classpath Exception. See [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

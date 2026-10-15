# Installation

## What to download

| File | What it is | Needed? |
|---|---|---|
| `prismatic-geodes-1.0.0+26.3.jar` | **Prismatic Geodes**, the mod | yes |
| Fabric API for 26.3 | library the mod needs | yes |
| `prismatic-geodes-miniblocks-1.0.0+26.3.jar` | **Mini Blocks** addon | optional |
| `Prismatic-Geode-Locator-1.0.0-windows-x64.zip` | the **map tool** (Windows) | optional |

## 1. Install Fabric
1. Download the Fabric installer from **https://fabricmc.net/use/installer/** and run it.
2. Choose Minecraft **26.3** and the latest Fabric Loader (0.19.5 or newer), then click *Install*.
3. Open the Minecraft Launcher once with the new **fabric-loader-26.3** profile, then close the game again.
   This creates the `mods` folder.

Using a launcher like Prism Launcher or the Modrinth App? Create a Fabric instance for Minecraft 26.3 there instead.

## 2. Add the mods
1. Open your `mods` folder. In the default launcher it is `%appdata%\.minecraft\mods`: press `Win + R`, type
   `%appdata%\.minecraft\mods` and press Enter.
2. Put these files in it:
   - `prismatic-geodes-1.0.0+26.3.jar`
   - Fabric API for 26.3 (from https://modrinth.com/mod/fabric-api or https://www.curseforge.com/minecraft/mc-mods/fabric-api)
   - optional: `prismatic-geodes-miniblocks-1.0.0+26.3.jar`
3. Start Minecraft with the Fabric profile. Coloured amethyst appears in the creative inventory next to the vanilla
   amethyst blocks.

## 3. Servers
Put the same jars (Prismatic Geodes, Fabric API and, if you use it, the Mini Blocks addon) in the server's `mods` folder.
**Every player needs the same mods on their own game too.**

## Adding it to an existing world
- **Back up your world first.**
- Only chunks that are generated *after* you install the mod get coloured geodes. Travel to new areas to find them.
- After coloured blocks exist in the world, keep the mod installed. If it is removed, the coloured blocks disappear.

---

## Prismatic Geode Locator (map tool)

### Requirements
- Windows 10 or 11, 64-bit
- About 400 MB of free disk space (more if you verify large areas, see below)
- 4 GB of RAM or more
- An internet connection the first time you start it

No Java, no Minecraft install and no other software is needed. Java is included.

### Start it
1. Unzip `Prismatic-Geode-Locator-1.0.0-windows-x64.zip` to a folder you can write to, for example
   your Desktop or Documents. (Not into `C:\Program Files`.)
2. Open the folder and double-click **Prismatic Geode Locator.exe**.
   - Windows may show *"Windows protected your PC"*, because the program is new and not signed.
     Click **More info**, then **Run anyway**.
3. **First start only:** the locator uses the official Minecraft 26.3 server to compute the map and check geodes.
   It asks you to accept the Minecraft EULA, then downloads the server from Mojang (about 60 MB) and Fabric Loader and
   Fabric API from FabricMC. Every file is checked against a known checksum.
4. The map opens in your browser. Keep the small *Prismatic Geode Locator* window open while you use the map.
   Closing that window stops the locator.

### Use it
1. In Minecraft, type `/seed` in chat and copy the number.
2. Paste it into **World seed** and click **Show**.
3. Drag to move, scroll to zoom. Use the colour buttons to show only the colours you want.
4. Click a crystal to see its coordinates and copy them, or copy a ready `/tp` command.
5. Optional: zoom in and click **Verify visible area**. The first verify takes a few minutes. Confirmed geodes get a
   green tick, spots where terrain stopped the geode get a grey cross.

The locator only works for worlds with the default world type (not Amplified, Large Biomes or Superflat), with
Prismatic Geodes 1.0.0. Areas that were explored before you installed the mod still have purple vanilla geodes.

### Where it keeps its files
Everything is in the `data` folder next to the program: the downloaded Minecraft files, the map cache and the hidden
test worlds used by *Verify*. If the program folder is not writable, it uses
`%LOCALAPPDATA%\Prismatic Geode Locator` instead. The *Open data folder* button in the locator window shows where.

Verified areas add up over time (about 60 MB for the largest verify, 64 × 64 chunks). To free the space, close the locator and
delete the `data\shadow-server\world_*` folders. Your real Minecraft worlds are never read or changed.

### Uninstall
Close the locator and delete its folder. It does not install anything anywhere else.

# Troubleshooting

## The mod

**The game crashes on start or says a dependency is missing**
- Check that you use Minecraft **26.3** with **Fabric Loader 0.19.5 or newer**.
- Prismatic Geodes needs **Fabric API** for 26.3 in the same `mods` folder.
- The Mini Blocks addon needs Prismatic Geodes itself.
- Remove other copies or older versions of the same jars from the `mods` folder.

**I can't find any coloured geodes**
- Only chunks generated *after* installing the mod have coloured geodes. Areas you explored before keep vanilla purple
  geodes. Travel to a part of the world you have never visited.
- Geodes are underground (between Y -58 and Y 30) and appear about once every 24 chunks, the same as vanilla.
- A purple geode is normal: purple is one of the 16 colours.
- The Prismatic Geode Locator shows where each geode is.

**All my geodes are still purple, even in new areas**
Another mod or data pack replaces the same geode feature (`minecraft:amethyst_geode`), and only one of them can win.
Try without your other worldgen mods or data packs to find which one.

**Coloured armour trims look wrong or purple**
A resource pack that changes the armour item models or the trim textures can hide the coloured trims. Try with that
resource pack turned off to check.

**I removed the mod and my coloured blocks are gone**
That is expected: the blocks belong to the mod. Put the mod back, or restore your backup.

**Server: players are kicked or see missing blocks**
Every player needs the same version of Prismatic Geodes (and the Mini Blocks addon, if the server uses it).

## Mini Blocks addon

**The mini blocks show a Steve or Alex skin**
The textures load from the addon itself. Make sure the addon is installed on your **own game**, not only on the server.

**There is no purple mini block**
On purpose: Vanilla Tweaks' Mini Blocks data pack already has the Mini Amethyst Block, so the addon doesn't add it again.

## Prismatic Geode Locator

**Windows says "Windows protected your PC"**
The program is new and not signed with a paid certificate. Click **More info**, then **Run anyway**.

**First start fails while downloading**
The first start needs internet access to `piston-data.mojang.com`, `meta.fabricmc.net` and `maven.fabricmc.net`.
Check your connection, firewall or antivirus, then click **Try again**.

**"the downloaded file does not match the expected checksum"**
The download was damaged or blocked. Click **Try again**. If it keeps happening, a proxy or antivirus may be changing
downloads.

**The map stays grey / "Preparing the terrain map"**
Preparing the map takes about 10 to 30 seconds after start. If it never finishes, close the locator, start it again and
check `data\logs\locator.log`.

**The geodes don't match my world**
- Check the seed: type `/seed` in Minecraft and copy it exactly.
- The world must use the default world type. Amplified, Large Biomes, Superflat and custom worlds are not supported.
- Areas explored *before* Prismatic Geodes was installed keep their purple vanilla geodes.
- About 1 in 10 predicted spots has no geode because the terrain stopped it. Use **Verify visible area** to check.

**Verify is very slow or fails**
- The first verify starts a hidden test world and can take a few minutes. Later ones are faster.
- Verify works on up to 64 × 64 chunks at a time. Zoom in if the button says so.
- It needs about 3 GB of free memory. Close other heavy programs and try again.

**The browser doesn't open**
Open the address shown in the locator window (usually `http://127.0.0.1:8765/`) yourself.

**Does the locator touch my worlds?**
No. It never opens your Minecraft saves. It only writes inside its own `data` folder. It runs only on your PC (on
`127.0.0.1`) and talks to the internet only to download the Minecraft server and Fabric files on the first start.

## Reporting a problem
Report it on GitHub: https://github.com/KalleBoy39/prismatic-geodes/issues Please include:
- Minecraft version, Fabric Loader version, Fabric API version and the Prismatic Geodes version,
- your other mods (or the list from the crash report),
- the crash report or `logs\latest.log`; for the locator, `data\logs\locator.log`.

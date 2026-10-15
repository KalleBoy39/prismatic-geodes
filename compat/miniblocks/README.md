# Prismatic Geodes: Mini Blocks

Separate, optional Fabric addon (mod id `vplus_geodes_miniblocks`) that adds **mini blocks** (decorative player
heads) for the 15 coloured Prismatic Geodes amethyst blocks, made to fit next to the Vanilla Tweaks Mini Blocks data
pack. The head textures are recoloured from the Vanilla Tweaks Mini Amethyst Block and keep the Vanilla Tweaks terms
(not MIT, see [THIRD-PARTY-NOTICES.md](../../THIRD-PARTY-NOTICES.md)). Purple is vanilla amethyst, and Vanilla Tweaks Mini
Blocks already has a mini block for it (`mini_blocks:amethyst_block`), so this addon does not duplicate it.

**How to get them:** put a coloured amethyst block in a **stonecutter** to get 8 *Mini <Colour> Amethyst Block*.
The recipe unlocks in the recipe book as soon as the block is in your inventory. Breaking a placed mini block
gives it back with its name.

## Same system as Vanilla Tweaks Mini Blocks v1.1.8 (MC 26.3)
| | Vanilla Tweaks | this addon |
|---|---|---|
| recipe | `stonecutting`, 1 block → 8 `minecraft:player_head` | identical |
| item components | `minecraft:profile`, `minecraft:item_name`, `minecraft:rarity` | identical |
| unlock | advancement per recipe (`inventory_changed` or `recipe_unlocked`) under a hidden `impossible` root | identical, own root `vplus_geodes_miniblocks:recipes/root` |
| drop when broken | `player_head` loot table copies block-entity components | uses that same table (untouched) |
| skin | `profile.properties` → skin on textures.minecraft.net | `profile.texture` → skin in this jar (26.3 skin patch) |
| face size | 8×8 per side | 8×8 per side |

Skins cannot be on textures.minecraft.net without uploading them through a Minecraft account, so the heads use the
26.3 profile `texture` field instead. It loads `assets/vplus_geodes_miniblocks/textures/entity/mini_block/<id>.png`
from the client's resources. Everything lives in its own namespace, so it cannot collide with Vanilla Tweaks, and
it works with or without Vanilla Tweaks installed. Requires Prismatic Geodes on client and server.

Credits:
Vanilla Tweaks: https://vanillatweaks.net/

(also shipped as `credits.txt` in the jar, as the Vanilla Tweaks terms require: https://vanillatweaks.net/terms/)

## Files
- `tools/generate.py`: generates **all** data and textures in `src/main/resources/{data,assets}` from the
  Prismatic Geodes jar and the Vanilla Tweaks template in `tools/templates/` (not in the public repository, see the
  README there). Head faces are 8×8, downscaled from the 16×16 block textures so highlights are kept.
  This matches the contrast of Vanilla Tweaks' own mini amethyst block.
- `src/main/resources/fabric.mod.json`: the only hand-written resource. There is no Java code.
- `src/gametest/`: server game tests and a client game test with screenshots.

## Adding more mini blocks
Add an entry to `VARIANTS` in `tools/generate.py`; entries for budding blocks and clusters are already written there
as comments. Then run:
```
python -I tools/generate.py   # needs the Prismatic Geodes jar (gradlew build in the repository root)
gradlew build                # compiles + server game tests
gradlew runClientGameTest    # screenshots in build/run/clientGameTest/screenshots
```
The jar is `build/libs/prismatic-geodes-miniblocks-<version>.jar`. Copy it into the profile's `mods` folder next to
Prismatic Geodes.

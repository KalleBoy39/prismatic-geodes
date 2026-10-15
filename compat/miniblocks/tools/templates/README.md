# Templates

`vt_mini_amethyst_block.png`: the six 8x8 head faces of Vanilla Tweaks' own **Mini Amethyst Block** (Mini Blocks
v1.1.8, MC 26.3). Copied pixel for pixel from its skin, with the rest of the skin left transparent:
http://textures.minecraft.net/texture/c13b77f5a746c1f00dd1ce7fe66976c75cecf7d89bdb02e8cf9436672af5896d
(the `minecraft:profile` of recipe `mini_blocks:amethyst_block`).

It uses exactly the 7 colours of vanilla `amethyst_block`. Every Prismatic Geodes `<colour>_amethyst_block` texture
is a 1:1 recolour of those 7 colours, so `tools/generate.py` recolours the template exactly. The result is
Vanilla Tweaks' pixel pattern in the approved Prismatic Geodes palettes.

The template itself is Vanilla Tweaks artwork. It is a build input only and is never put into the jar. The shipped
jar contains only the 15 recoloured variants (a modification), plus `credits.txt` with the credit text that the
Vanilla Tweaks terms require (https://vanillatweaks.net/terms/, checked 2026-10-13).

**Not in the public repository.** Because it is unmodified Vanilla Tweaks artwork, the PNG is excluded from the
repository (`.gitignore`). The generated, recoloured textures in `src/main/resources` are committed, so building the
addon does not need it. To regenerate them, download Vanilla Tweaks Mini Blocks yourself from
https://vanillatweaks.net/, copy the six head faces of the skin above into a 64x64 PNG at the standard head
positions, and save it here as `vt_mini_amethyst_block.png`.

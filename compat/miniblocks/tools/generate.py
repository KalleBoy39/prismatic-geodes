"""Generate every resource of the Prismatic Geodes Mini Blocks addon.

python -I tools/generate.py [PRISMATIC_GEODES_JAR] [MINECRAFT_CLIENT_JAR]

Mirrors Vanilla Tweaks Mini Blocks v1.1.8 (MC 26.3) exactly, per mini block:
  data/<ns>/recipe/<id>.json                 stonecutting: 1 block -> 8 minecraft:player_head
                                             (minecraft:profile + minecraft:item_name + minecraft:rarity)
  data/<ns>/advancement/recipes/<id>.json    unlocks the recipe once the block is in the inventory
  data/<ns>/advancement/recipes/root.json    hidden root (impossible trigger), like mini_blocks:recipes/root
The one difference: Vanilla Tweaks' heads point at skins on textures.minecraft.net, while these use the 26.3
profile "texture" field, which loads a skin from the client's own resources:
  assets/<ns>/textures/entity/mini_block/<id>.png   64x64 skin, head faces 8x8 like Vanilla Tweaks

Skins: a variant with a 'template' recolours a Vanilla Tweaks head (tools/templates/) pixel for pixel. The template
uses the 7 colours of a vanilla block texture ('palette_from'), and each Prismatic Geodes texture is a 1:1 recolour
of those colours, so every template pixel maps to the same shade in our palette. Variants without a template fall
back to downscaling the block texture to 8x8 per face.

Breaking a placed mini block already returns it with its name: Vanilla Tweaks' player_head loot table (and
vanilla's) copies the block entity's components. Nothing here touches that table.

To add more mini blocks later, add an entry to VARIANTS and run this again.
"""
import json, os, runpy, shutil, sys, zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(HERE)
REPO = os.path.abspath(os.path.join(PROJECT, '..', '..'))
RES = os.path.join(PROJECT, 'src', 'main', 'resources')
NS = 'vplus_geodes_miniblocks'
MOD = 'vplus_geodes'

VPLUS_JAR = sys.argv[1] if len(sys.argv) > 1 else next(
    (p for p in (os.path.join(REPO, d, 'prismatic-geodes-1.0.0+26.3.jar') for d in ('release', os.path.join('build', 'libs')))
     if os.path.exists(p)), os.path.join(REPO, 'build', 'libs', 'prismatic-geodes-1.0.0+26.3.jar'))
MC_JAR = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.expanduser('~'), '.gradle', 'caches', 'fabric-loom',
                                                              'minecraftMaven', 'net', 'minecraft', 'minecraft-merged-deobf',
                                                              '26.3', 'minecraft-merged-deobf-26.3.jar')

# Purple is vanilla amethyst: Vanilla Tweaks Mini Blocks already has mini_blocks:amethyst_block for it.
COLOURS = ['white', 'light_gray', 'gray', 'black', 'brown', 'red', 'orange', 'yellow',
           'lime', 'green', 'cyan', 'light_blue', 'blue', 'magenta', 'pink']

# One entry per kind of mini block. 'block' is the stonecutter input (Prismatic Geodes id without namespace),
# 'faces' maps head faces to block textures (textures/block/<name>.png in the Prismatic Geodes jar).
VARIANTS = [
    {'id': '{c}_amethyst_block', 'block': '{c}_amethyst_block', 'name': 'Mini {C} Amethyst Block',
     'template': 'vt_mini_amethyst_block', 'palette_from': ('amethyst_block', '{c}_amethyst_block'),
     'faces': {'all': '{c}_amethyst_block'}},
    # Ready to enable later (one line each). Budding blocks are full cubes like the block above:
    # {'id': '{c}_budding_amethyst', 'block': '{c}_budding_amethyst', 'name': 'Mini {C} Budding Amethyst',
    #  'faces': {'all': '{c}_budding_amethyst'}},
    # Buds/clusters are cross-shaped plants; as a head they need a decided look first, e.g. the cluster
    # texture on the four sides over the block texture top/bottom:
    # {'id': '{c}_amethyst_cluster', 'block': '{c}_amethyst_cluster', 'name': 'Mini {C} Amethyst Cluster',
    #  'faces': {'sides': '{c}_amethyst_cluster', 'top': '{c}_amethyst_block', 'bottom': '{c}_amethyst_block'}},
]

# Head layout of a 64x64 skin: face -> (u, v) of its 8x8 square, and a fixed per-face transform so the six
# sides are not identical copies (Vanilla Tweaks' own heads vary their sides too).
FACES = {'top': (8, 0), 'bottom': (16, 0), 'right': (0, 8), 'front': (8, 8), 'left': (16, 8), 'back': (24, 8)}
TRANSFORM = {'top': 'none', 'bottom': 'flip_v', 'right': 'rot90', 'front': 'none', 'left': 'flip_h', 'back': 'rot180'}

sheet = runpy.run_path(os.path.join(REPO, 'tools', 'sheet.py'))
read_png, write_png = sheet['read_png'], sheet['write_png']


def title(colour):
    return ' '.join(w.capitalize() for w in colour.split('_'))


def jar_texture(jar, path):
    """RGBA rows of a PNG inside a jar (first frame if it is an animation strip)."""
    tmp = os.path.join(PROJECT, 'build', 'gen_tmp.png')
    os.makedirs(os.path.dirname(tmp), exist_ok=True)
    with zipfile.ZipFile(jar) as z, open(tmp, 'wb') as f:
        f.write(z.read(path))
    w, h, rows = read_png(tmp)
    os.remove(tmp)
    return [r[:w] for r in rows[:w]]


def luma(p):
    return 0.3 * p[0] + 0.59 * p[1] + 0.11 * p[2]


def downscale(rows, n=8):
    """16x16 block texture -> 8x8 head face. From each 2x2 cell keep the pixel that differs most from the cell's
    mean brightness, so highlights survive. Matches the contrast of Vanilla Tweaks' own mini amethyst block
    (brightness std 36.9 vs 32.5, peak 221 vs 221); a plain box filter washes it out (std 21.4)."""
    f = len(rows) // n
    out = []
    for y in range(n):
        line = []
        for x in range(n):
            px = [rows[y * f + j][x * f + i] for j in range(f) for i in range(f)]
            mean = sum(map(luma, px)) / len(px)
            line.append(max(px, key=lambda p: abs(luma(p) - mean)))
        out.append(line)
    return out


def transform(face, how):
    n = len(face)
    if how == 'flip_h':
        return [list(reversed(r)) for r in face]
    if how == 'flip_v':
        return list(reversed(face))
    if how == 'rot90':
        return [[face[n - 1 - x][y] for x in range(n)] for y in range(n)]
    if how == 'rot180':
        return [list(reversed(r)) for r in reversed(face)]
    return face


def face_texture(spec, face):
    if 'all' in spec:
        return spec['all']
    if face in ('top', 'bottom'):
        return spec[face]
    return spec['sides']


def recoloured_template(spec, colour):
    """Vanilla Tweaks head template in this colour: vanilla shade -> the shade at the same pixels in our texture."""
    vanilla_name, ours_name = spec['palette_from']
    vanilla = jar_texture(MC_JAR, f'assets/minecraft/textures/block/{vanilla_name}.png')
    ours = jar_texture(VPLUS_JAR, f'assets/{MOD}/textures/block/{ours_name.format(c=colour)}.png')
    mapping = {}
    for y in range(len(vanilla)):
        for x in range(len(vanilla)):
            src, dst = vanilla[y][x][:3], ours[y][x]
            if mapping.setdefault(src, dst) != dst:
                raise SystemExit(f'{ours_name.format(c=colour)} is not a 1:1 recolour of {vanilla_name}')
    _, _, tpl = read_png(os.path.join(HERE, 'templates', spec['template'] + '.png'))
    out = []
    for row in tpl:
        line = []
        for p in row:
            if p[3] == 0:
                line.append((0, 0, 0, 0))
            elif p[:3] in mapping:
                line.append(mapping[p[:3]][:3] + (p[3],))
            else:
                raise SystemExit(f"template colour {p[:3]} is not in vanilla {vanilla_name}")
        out.append(line)
    return out


def skin(spec, colour, cache):
    if 'template' in spec:
        return recoloured_template(spec, colour)
    img = [[(0, 0, 0, 0)] * 64 for _ in range(64)]
    for face, (u, v) in FACES.items():
        name = face_texture(spec['faces'], face).format(c=colour)
        if name not in cache:
            cache[name] = downscale(jar_texture(VPLUS_JAR, f'assets/{MOD}/textures/block/{name}.png'))
        px = transform(cache[name], TRANSFORM[face])
        for y in range(8):
            for x in range(8):
                img[v + y][u + x] = px[y][x]
    return img


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write('\n')


def recipe(block, name, texture):
    return {
        'type': 'minecraft:stonecutting',
        'ingredient': f'{MOD}:{block}',
        'result': {
            'id': 'minecraft:player_head',
            'count': 8,
            'components': {
                'minecraft:profile': {'texture': texture},
                'minecraft:item_name': name,
                'minecraft:rarity': 'common',
            },
        },
    }


def advancement(rid, block):
    return {
        'parent': f'{NS}:recipes/root',
        'criteria': {
            'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipes': f'{NS}:{rid}'}},
            'has_item': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': f'{MOD}:{block}'}]}},
        },
        'requirements': [['has_the_recipe', 'has_item']],
        'rewards': {'recipes': [f'{NS}:{rid}']},
    }


def main():
    for sub in ('data', 'assets'):
        shutil.rmtree(os.path.join(RES, sub), ignore_errors=True)
    write_json(os.path.join(RES, 'data', NS, 'advancement', 'recipes', 'root.json'),
               {'criteria': {'impossible': {'trigger': 'minecraft:impossible'}}, 'requirements': [['impossible']]})
    cache, count, icon_faces = {}, 0, []
    for spec in VARIANTS:
        for colour in COLOURS:
            rid, block = spec['id'].format(c=colour), spec['block'].format(c=colour)
            name = spec['name'].format(C=title(colour))
            texture = f'{NS}:entity/mini_block/{rid}'
            img = skin(spec, colour, cache)
            out = os.path.join(RES, 'assets', NS, 'textures', 'entity', 'mini_block', rid + '.png')
            os.makedirs(os.path.dirname(out), exist_ok=True)
            write_png(out, 64, 64, img)
            write_json(os.path.join(RES, 'data', NS, 'recipe', rid + '.json'), recipe(block, name, texture))
            write_json(os.path.join(RES, 'data', NS, 'advancement', 'recipes', rid + '.json'), advancement(rid, block))
            if spec is VARIANTS[0]:
                icon_faces.append([r[8:16] for r in img[8:16]])
            count += 1
    # mod icon: the 15 front faces plus vanilla amethyst (purple) in DyeColor order, 4x4, scaled to 128x128
    purple = downscale(jar_texture(MC_JAR, 'assets/minecraft/textures/block/amethyst_block.png'))
    faces = icon_faces[:13] + [purple] + icon_faces[13:]
    icon = [[(0, 0, 0, 0)] * 128 for _ in range(128)]
    for i, face in enumerate(faces):
        ox, oy = (i % 4) * 32, (i // 4) * 32
        for y in range(32):
            for x in range(32):
                icon[oy + y][ox + x] = face[y // 4][x // 4] if 2 <= x % 32 < 30 and 2 <= y % 32 < 30 else (0, 0, 0, 0)
    out = os.path.join(RES, 'assets', NS, 'icon.png')
    write_png(out, 128, 128, icon)
    print(f'{count} mini blocks generated in {RES}')


if __name__ == '__main__':
    main()

package dev.vplus.geodes.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.vplus.geodes.GeodeFamily;
import dev.vplus.geodes.ModContent;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GeodeBlockSettings;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.GeodeFeature;
import net.minecraft.world.level.levelgen.feature.SimpleRandomSelectorFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.Fluids;

/**
 * Server-side checks of every colour family against the vanilla amethyst blocks it copies.
 * Each test compares with vanilla directly rather than with hard-coded numbers.
 */
public class GeodeGameTests {
	private static final BlockPos BASE = new BlockPos(3, 3, 3);

	/** vanilla equivalent of each block in a family, in the same order as {@link #blocks}. */
	private static final Block[] VANILLA = {Blocks.AMETHYST_BLOCK, Blocks.BUDDING_AMETHYST, Blocks.SMALL_AMETHYST_BUD,
			Blocks.MEDIUM_AMETHYST_BUD, Blocks.LARGE_AMETHYST_BUD, Blocks.AMETHYST_CLUSTER};

	private static Block[] blocks(GeodeFamily f) {
		return new Block[] {f.block(), f.budding(), f.smallBud(), f.mediumBud(), f.largeBud(), f.cluster()};
	}

	private static Block[] stages(GeodeFamily f) {
		return new Block[] {f.smallBud(), f.mediumBud(), f.largeBud(), f.cluster()};
	}

	/** Strength, sounds, light, pistons, tools, ticking, friction, instrument all equal vanilla; map colour = dye. */
	@GameTest
	public void propertiesMatchVanilla(GameTestHelper h) {
		h.assertValueEqual(ModContent.FAMILIES.size(), 15, "family count");
		ServerLevel level = h.getLevel();
		BlockPos pos = h.absolutePos(BASE);
		for (GeodeFamily f : ModContent.FAMILIES) {
			Block[] ours = blocks(f);
			for (int i = 0; i < ours.length; i++) {
				BlockState a = ours[i].defaultBlockState();
				BlockState v = VANILLA[i].defaultBlockState();
				String n = ours[i].toString();
				h.assertValueEqual(a.getDestroySpeed(level, pos), v.getDestroySpeed(level, pos), n + " hardness");
				h.assertValueEqual(ours[i].getExplosionResistance(), VANILLA[i].getExplosionResistance(), n + " blast");
				h.assertValueEqual(a.getSoundType(), v.getSoundType(), n + " sound");
				h.assertValueEqual(a.getLightEmission(), v.getLightEmission(), n + " light");
				h.assertValueEqual(a.getPistonPushReaction(), v.getPistonPushReaction(), n + " piston");
				h.assertValueEqual(a.requiresCorrectToolForDrops(), v.requiresCorrectToolForDrops(), n + " tool");
				h.assertValueEqual(a.isRandomlyTicking(), v.isRandomlyTicking(), n + " ticking");
				h.assertValueEqual(ours[i].getFriction(), VANILLA[i].getFriction(), n + " friction");
				h.assertValueEqual(a.instrument(), v.instrument(), n + " instrument");
				h.assertValueEqual(a.canOcclude(), v.canOcclude(), n + " occlusion");
				h.assertValueEqual(a.getMapColor(level, pos), f.colour().getMapColor(), n + " map colour");
				h.assertValueEqual(a.getProperties(), v.getProperties(), n + " state properties");
				h.assertValueEqual(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(a),
						new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(v), n + " pickaxe");
				h.assertTrue(a.is(BlockTags.MINEABLE_WITH_PICKAXE), n + " mineable/pickaxe");
				h.assertValueEqual(ours[i].asItem() != Items.AIR, true, n + " block item");
				h.assertValueEqual(ours[i].asItem().getDefaultMaxStackSize(),
						VANILLA[i].asItem().getDefaultMaxStackSize(), n + " stack size");
			}
		}
		h.succeed();
	}

	/** Every bud/cluster in every facing: same outline + collision shape as vanilla, survives only with support. */
	@GameTest
	public void orientationShapesAndSupport(GameTestHelper h) {
		for (GeodeFamily f : ModContent.FAMILIES) {
			Block[] st = stages(f);
			for (int s = 0; s < st.length; s++) {
				for (Direction d : Direction.values()) {
					BlockPos pos = BASE;
					BlockPos support = pos.relative(d.getOpposite());
					h.setBlock(support, f.block());
					BlockState ours = st[s].defaultBlockState().setValue(AmethystClusterBlock.FACING, d);
					BlockState van = VANILLA[s + 2].defaultBlockState().setValue(AmethystClusterBlock.FACING, d);
					BlockPos abs = h.absolutePos(pos);
					h.assertValueEqual(ours.getShape(h.getLevel(), abs).toAabbs(), van.getShape(h.getLevel(), abs).toAabbs(),
							st[s] + " outline " + d);
					h.assertValueEqual(ours.getCollisionShape(h.getLevel(), abs).toAabbs(),
							van.getCollisionShape(h.getLevel(), abs).toAabbs(), st[s] + " collision " + d);
					h.assertTrue(ours.canSurvive(h.getLevel(), abs), st[s] + " survives on support " + d);
					h.setBlock(pos, ours);
					h.assertValueEqual(h.getBlockState(pos), ours, st[s] + " placed " + d);
					// remove support: bud pops like vanilla
					h.setBlock(support, Blocks.AIR);
					h.assertFalse(ours.canSurvive(h.getLevel(), abs), st[s] + " no support " + d);
					h.setBlock(pos, Blocks.AIR);
				}
			}
		}
		h.succeed();
	}

	@GameTest
	public void waterlogging(GameTestHelper h) {
		for (GeodeFamily f : ModContent.FAMILIES) {
			for (Block b : stages(f)) {
				BlockState wet = b.defaultBlockState().setValue(AmethystClusterBlock.WATERLOGGED, true);
				h.assertTrue(wet.getFluidState().is(Fluids.WATER), b + " waterlogged fluid");
				h.assertTrue(wet.getFluidState().isSource(), b + " waterlogged source");
				h.assertTrue(b.defaultBlockState().getFluidState().isEmpty(), b + " dry by default");
			}
			// growth into a water source produces a waterlogged bud
			h.setBlock(BASE, f.budding());
			h.setBlock(BASE.above(), Blocks.WATER);
			growUntil(h, BASE, BASE.above(), f.smallBud(), 4000);
			BlockState grown = h.getBlockState(BASE.above());
			h.assertTrue(grown.is(f.smallBud()) && grown.getValue(AmethystClusterBlock.WATERLOGGED),
					f.name() + " bud grown in water is waterlogged");
			h.setBlock(BASE.above(), Blocks.AIR);
			h.setBlock(BASE, Blocks.AIR);
		}
		h.succeed();
	}

	private static void growUntil(GameTestHelper h, BlockPos budding, BlockPos target, Block wanted, int maxTicks) {
		RandomSource random = RandomSource.create(target.asLong());
		BlockPos abs = h.absolutePos(budding);
		for (int i = 0; i < maxTicks && !h.getBlockState(target).is(wanted); i++) {
			h.getBlockState(budding).randomTick(h.getLevel(), abs, random);
		}
	}

	/**
	 * Budding block surrounded by air: after enough random ticks all six sides end as own-colour clusters,
	 * every intermediate state is one of the family's stages facing outwards, and stages only advance one step.
	 */
	@GameTest(maxTicks = 400)
	public void growthOwnColourOnly(GameTestHelper h) {
		List<GeodeFamily> fams = new ArrayList<>(ModContent.FAMILIES);
		for (GeodeFamily f : fams) {
			checkGrowth(h, f.budding(), stages(f), f.name());
		}
		// vanilla budding amethyst still grows vanilla purple
		checkGrowth(h, Blocks.BUDDING_AMETHYST, new Block[] {Blocks.SMALL_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD,
				Blocks.LARGE_AMETHYST_BUD, Blocks.AMETHYST_CLUSTER}, "vanilla purple");
		h.succeed();
	}

	private static void checkGrowth(GameTestHelper h, Block budding, Block[] stages, String name) {
		for (Direction d : Direction.values()) {
			h.setBlock(BASE.relative(d), Blocks.AIR);
		}
		h.setBlock(BASE, budding);
		BlockPos abs = h.absolutePos(BASE);
		RandomSource random = RandomSource.create(name.hashCode());
		Map<Direction, Integer> stage = new HashMap<>();
		int ticks = 0;
		while (ticks < 20000 && !(stage.size() == 6 && stage.values().stream().allMatch(s -> s == 3))) {
			h.getBlockState(BASE).randomTick(h.getLevel(), abs, random);
			ticks++;
			for (Direction d : Direction.values()) {
				BlockState s = h.getBlockState(BASE.relative(d));
				if (s.isAir()) {
					h.assertFalse(stage.containsKey(d), name + " bud vanished at " + d);
					continue;
				}
				int idx = -1;
				for (int i = 0; i < stages.length; i++) {
					if (s.is(stages[i])) {
						idx = i;
					}
				}
				h.assertTrue(idx >= 0, name + " grew foreign block " + s + " at " + d);
				h.assertValueEqual(s.getValue(AmethystClusterBlock.FACING), d, name + " facing at " + d);
				int prev = stage.getOrDefault(d, -1);
				h.assertTrue(idx == prev || idx == prev + 1, name + " skipped stage " + prev + "->" + idx + " at " + d);
				stage.put(d, idx);
			}
		}
		h.assertTrue(stage.size() == 6 && stage.values().stream().allMatch(s -> s == 3),
				name + " did not reach clusters on all sides in " + ticks + " ticks");
		for (Direction d : Direction.values()) {
			h.setBlock(BASE.relative(d), Blocks.AIR);
		}
		h.setBlock(BASE, Blocks.AIR);
	}

	private static ItemStack tool(GameTestHelper h, Item item, ResourceKey<net.minecraft.world.item.enchantment.Enchantment> ench,
			int lvl) {
		ItemStack stack = new ItemStack(item);
		if (ench != null) {
			stack.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ench), lvl);
		}
		return stack;
	}

	private static Map<Item, Integer> drops(GameTestHelper h, BlockState state, ItemStack tool) {
		Map<Item, Integer> out = new HashMap<>();
		BlockPos abs = h.absolutePos(BASE);
		for (ItemStack s : Block.getDrops(state, h.getLevel(), abs, null, null, tool)) {
			out.merge(s.getItem(), s.getCount(), Integer::sum);
		}
		return out;
	}

	/** Drops for hand / pickaxe / silk touch / fortune III match vanilla, with the coloured item in place of purple. */
	@GameTest
	public void dropsMatchVanilla(GameTestHelper h) {
		List<ItemStack> tools = List.of(ItemStack.EMPTY, new ItemStack(Items.IRON_PICKAXE),
				tool(h, Items.DIAMOND_PICKAXE, Enchantments.SILK_TOUCH, 1), tool(h, Items.DIAMOND_PICKAXE, Enchantments.FORTUNE, 3));
		for (GeodeFamily f : ModContent.FAMILIES) {
			Map<Item, Item> toOurs = new HashMap<>();
			Block[] ours = blocks(f);
			for (int i = 0; i < ours.length; i++) {
				toOurs.put(VANILLA[i].asItem(), ours[i].asItem());
			}
			toOurs.put(Items.AMETHYST_SHARD, f.shard());
			for (int i = 0; i < ours.length; i++) {
				for (ItemStack t : tools) {
					for (int trial = 0; trial < 20; trial++) {
						String name = ours[i].toString();
						Map<Item, Integer> v = drops(h, VANILLA[i].defaultBlockState(), t);
						Map<Item, Integer> o = drops(h, ours[i].defaultBlockState(), t);
						Set<Item> expectedItems = new HashSet<>();
						v.keySet().forEach(it -> expectedItems.add(toOurs.getOrDefault(it, it)));
						h.assertValueEqual(o.keySet(), expectedItems, ours[i] + " drop items with " + t);
						boolean fortuneCluster = t == tools.get(3) && i == 5;
						if (!fortuneCluster) {
							// deterministic cases must match exactly (fortune on clusters is random, range-checked below)
							v.forEach((it, n) -> h.assertValueEqual(o.get(toOurs.getOrDefault(it, it)), n,
									name + " drop count with " + t));
						}
					}
				}
			}
			// fortune III on a cluster: ore_drops, 4..16 shards, coloured
			int min = Integer.MAX_VALUE, max = 0;
			for (int trial = 0; trial < 200; trial++) {
				int n = drops(h, f.cluster().defaultBlockState(), tools.get(3)).getOrDefault(f.shard(), 0);
				min = Math.min(min, n);
				max = Math.max(max, n);
			}
			h.assertTrue(min >= 4 && max <= 16 && max > 4, f.name() + " fortune range " + min + ".." + max);
		}
		h.succeed();
	}

	@GameTest
	public void recipes(GameTestHelper h) {
		ServerLevel level = h.getLevel();
		var rm = level.getServer().getRecipeManager();
		for (GeodeFamily f : ModContent.FAMILIES) {
			ItemStack s = new ItemStack(f.shard());
			var result = rm.getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(2, 2, List.of(s, s, s, s)), level)
					.map(r -> r.value().assemble(CraftingInput.of(2, 2, List.of(s, s, s, s))));
			h.assertTrue(result.isPresent() && result.get().is(f.block().asItem()), f.name() + " 4 shards -> block");
			ItemStack e = ItemStack.EMPTY;
			ItemStack spy = rm.getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3,
					List.of(e, s, e, e, new ItemStack(Items.COPPER_INGOT), e, e, new ItemStack(Items.COPPER_INGOT), e)), level)
					.map(r -> r.value().assemble(CraftingInput.of(3, 3,
							List.of(e, s, e, e, new ItemStack(Items.COPPER_INGOT), e, e, new ItemStack(Items.COPPER_INGOT), e))))
					.orElse(ItemStack.EMPTY);
			h.assertTrue(spy.is(Items.SPYGLASS), f.name() + " spyglass");
			h.assertTrue(s.is(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "gems/amethyst"))), f.name() + " c:gems/amethyst");
			h.assertTrue(s.is(ItemTags.DUPLICATES_ALLAYS), f.name() + " duplicates_allays");
			h.assertTrue(f.block().defaultBlockState().is(BlockTags.CRYSTAL_SOUND_BLOCKS), f.name() + " crystal sound");
			h.assertTrue(f.budding().defaultBlockState().is(BlockTags.CRYSTAL_SOUND_BLOCKS), f.name() + " crystal sound budding");
			h.assertTrue(f.block().defaultBlockState().is(BlockTags.VIBRATION_RESONATORS), f.name() + " resonator");
		}
		// mixed colours do not make a block; vanilla purple recipe unchanged
		ItemStack r = new ItemStack(ModContent.FAMILIES.get(0).shard());
		ItemStack b = new ItemStack(ModContent.FAMILIES.get(1).shard());
		h.assertTrue(rm.getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(2, 2, List.of(r, r, b, b)), level).isEmpty(),
				"mixed shards make nothing");
		ItemStack p = new ItemStack(Items.AMETHYST_SHARD);
		h.assertTrue(rm.getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(2, 2, List.of(p, p, p, p)), level)
				.map(x -> x.value().assemble(CraftingInput.of(2, 2, List.of(p, p, p, p))).is(Items.AMETHYST_BLOCK))
				.orElse(false), "vanilla amethyst block recipe");
		h.succeed();
	}

	/** The loaded worldgen: minecraft:amethyst_geode picks 1 of 16 single-colour geodes; placed feature untouched. */
	@GameTest
	public void worldgenSelector(GameTestHelper h) {
		var access = h.getLevel().registryAccess();
		Feature top = access.lookupOrThrow(Registries.FEATURE)
				.getOrThrow(ResourceKey.create(Registries.FEATURE, Identifier.withDefaultNamespace("amethyst_geode"))).value();
		h.assertTrue(top instanceof SimpleRandomSelectorFeature, "amethyst_geode is a selector: " + top);
		SimpleRandomSelectorFeature sel = (SimpleRandomSelectorFeature) top;
		h.assertValueEqual(sel.features().size(), 16, "16 options");
		Set<Block> seenBlocks = new HashSet<>();
		Map<Block, String> family = new HashMap<>();
		for (GeodeFamily f : ModContent.FAMILIES) {
			for (Block x : blocks(f)) {
				family.put(x, f.name());
			}
		}
		for (Block x : VANILLA) {
			family.put(x, DyeColor.PURPLE.getSerializedName());
		}
		Set<String> colours = new HashSet<>();
		for (Holder<PlacedFeature> pf : sel.features()) {
			h.assertTrue(pf.value().placement().isEmpty(), "inner placed feature has no extra placement");
			Feature inner = pf.value().feature().value();
			h.assertTrue(inner instanceof GeodeFeature, "inner is geode");
			GeodeFeature g = (GeodeFeature) inner;
			GeodeBlockSettings bs = g.blockSettings();
			RandomSource rnd = RandomSource.create(1);
			List<Block> used = new ArrayList<>();
			used.add(bs.innerLayerProvider().value().getState(h.getLevel(), rnd, BlockPos.ZERO).getBlock());
			used.add(bs.alternateInnerLayerProvider().value().getState(h.getLevel(), rnd, BlockPos.ZERO).getBlock());
			bs.innerPlacements().forEach(s -> used.add(s.getBlock()));
			Set<String> cs = new HashSet<>();
			used.forEach(x -> cs.add(family.get(x)));
			h.assertValueEqual(cs.size(), 1, "geode uses one colour: " + used);
			colours.addAll(cs);
			seenBlocks.addAll(used);
			h.assertValueEqual(bs.middleLayerProvider().value().getState(h.getLevel(), rnd, BlockPos.ZERO).getBlock(),
					Blocks.CALCITE, "calcite layer");
			h.assertValueEqual(bs.outerLayerProvider().value().getState(h.getLevel(), rnd, BlockPos.ZERO).getBlock(),
					Blocks.SMOOTH_BASALT, "basalt shell");
			h.assertValueEqual(g.useAlternateLayer0Chance(), 0.083, "budding frequency");
		}
		h.assertValueEqual(colours.size(), 16, "all 16 colours present once");
		PlacedFeature placed = access.lookupOrThrow(Registries.PLACED_FEATURE)
				.getOrThrow(ResourceKey.create(Registries.PLACED_FEATURE, Identifier.withDefaultNamespace("amethyst_geode"))).value();
		h.assertValueEqual(placed.feature().unwrapKey().map(k -> k.identifier().toString()).orElse("inline"),
				"minecraft:amethyst_geode", "vanilla placed feature still points at amethyst_geode");
		h.assertValueEqual(placed.placement().size(), 4, "vanilla placement modifiers intact");
		h.succeed();
	}
}

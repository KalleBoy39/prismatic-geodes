package dev.vplus.geodes.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.vplus.geodes.GeodeFamily;
import dev.vplus.geodes.ModContent;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

/** Crafting behaviour of coloured shards: matching blocks only, and any shard wherever vanilla takes one. */
public class RecipeGameTests {
	private static final ItemStack E = ItemStack.EMPTY;
	private static final TagKey<Item> ALL_SHARDS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("vplus_geodes", "amethyst_shards"));

	/** Every shard: the 15 coloured ones plus vanilla purple. */
	private static List<Item> allShards() {
		List<Item> out = new ArrayList<>();
		ModContent.FAMILIES.forEach(f -> out.add(f.shard()));
		out.add(Items.AMETHYST_SHARD);
		return out;
	}

	private static Item blockFor(Item shard) {
		if (shard == Items.AMETHYST_SHARD) {
			return Items.AMETHYST_BLOCK;
		}
		return ModContent.FAMILIES.stream().filter(f -> f.shard() == shard).findFirst().orElseThrow().block().asItem();
	}

	private static ItemStack craft(GameTestHelper h, int w, int ht, ItemStack... items) {
		ServerLevel level = h.getLevel();
		CraftingInput in = CraftingInput.of(w, ht, List.of(items));
		return level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, in, level)
				.map(r -> r.value().assemble(in)).orElse(ItemStack.EMPTY);
	}

	private static ItemStack s(Item i) {
		return new ItemStack(i);
	}

	private static boolean isAmethystBlock(ItemStack st) {
		if (st.is(Items.AMETHYST_BLOCK)) {
			return true;
		}
		for (GeodeFamily f : ModContent.FAMILIES) {
			if (st.is(f.block().asItem())) {
				return true;
			}
		}
		return false;
	}

	/** A: 4 matching shards make exactly one matching block (15 colours + vanilla purple). */
	@GameTest
	public void matchingShardsMakeMatchingBlock(GameTestHelper h) {
		for (Item shard : allShards()) {
			ItemStack out = craft(h, 2, 2, s(shard), s(shard), s(shard), s(shard));
			h.assertTrue(out.is(blockFor(shard)) && out.getCount() == 1, shard + " x4 -> " + out);
		}
		h.succeed();
	}

	/** B: any mix of two different shard colours (vanilla included) never makes an amethyst block. */
	@GameTest
	public void mixedShardsMakeNoBlock(GameTestHelper h) {
		List<Item> shards = allShards();
		int checked = 0;
		for (Item a : shards) {
			for (Item b : shards) {
				if (a == b) {
					continue;
				}
				for (ItemStack[] grid : List.of(new ItemStack[] {s(a), s(a), s(b), s(b)}, new ItemStack[] {s(a), s(b), s(b), s(a)},
						new ItemStack[] {s(a), s(a), s(a), s(b)})) {
					ItemStack out = craft(h, 2, 2, grid);
					h.assertFalse(isAmethystBlock(out), "mixed " + a + "/" + b + " crafted " + out);
					checked++;
				}
			}
		}
		h.assertValueEqual(checked, 16 * 15 * 3, "mixed combinations checked");
		h.succeed();
	}

	/** C/D/E/F: every shard, coloured or vanilla, works in spyglass, tinted glass and calibrated sculk sensor. */
	@GameTest
	public void anyShardWorksInVanillaShardRecipes(GameTestHelper h) {
		ItemStack cu = s(Items.COPPER_INGOT), glass = s(Items.GLASS), sensor = s(Items.SCULK_SENSOR);
		for (Item shard : allShards()) {
			ItemStack x = s(shard);
			ItemStack spy = craft(h, 3, 3, E, x, E, E, cu, E, E, cu, E);
			h.assertTrue(spy.is(Items.SPYGLASS) && spy.getCount() == 1, shard + " spyglass -> " + spy);
			ItemStack tint = craft(h, 3, 3, E, x, E, x, glass, x, E, x, E);
			h.assertTrue(tint.is(Items.TINTED_GLASS) && tint.getCount() == 2, shard + " tinted glass -> " + tint);
			ItemStack cal = craft(h, 3, 2, E, x, E, x, sensor, x);
			h.assertTrue(cal.is(Items.CALIBRATED_SCULK_SENSOR) && cal.getCount() == 1, shard + " calibrated sensor -> " + cal);
		}
		// "any amethyst shard" also means shards may be mixed inside those recipes
		List<Item> sh = allShards();
		ItemStack tintMixed = craft(h, 3, 3, E, s(sh.get(0)), E, s(sh.get(5)), glass, s(sh.get(9)), E, s(Items.AMETHYST_SHARD), E);
		h.assertTrue(tintMixed.is(Items.TINTED_GLASS), "mixed shards in tinted glass -> " + tintMixed);
		ItemStack calMixed = craft(h, 3, 2, E, s(sh.get(3)), E, s(Items.AMETHYST_SHARD), sensor, s(sh.get(12)));
		h.assertTrue(calMixed.is(Items.CALIBRATED_SCULK_SENSOR), "mixed shards in calibrated sensor -> " + calMixed);
		h.succeed();
	}

	private static Set<String> recipesAccepting(RecipeManager rm, ItemStack stack) {
		Set<String> ids = new HashSet<>();
		for (RecipeHolder<?> holder : rm.getRecipes()) {
			Recipe<?> r = holder.value();
			if (r instanceof CraftingRecipe cr) {
				var placement = cr.placementInfo();
				if (!placement.isImpossibleToPlace()) {
					for (Ingredient ing : placement.ingredients()) {
						if (ing.test(stack)) {
							ids.add(holder.id().identifier().toString());
							break;
						}
					}
				}
			}
		}
		return ids;
	}

	/** Recipe book: exactly one recipe per output, coloured shards only reach the intended recipes, vanilla shard unchanged. */
	@GameTest
	public void recipeSetIsExactNoDuplicates(GameTestHelper h) {
		RecipeManager rm = h.getLevel().getServer().getRecipeManager();
		Set<String> vanillaExpected = Set.of("minecraft:amethyst_block", "minecraft:spyglass", "minecraft:tinted_glass",
				"minecraft:calibrated_sculk_sensor");
		h.assertValueEqual(recipesAccepting(rm, s(Items.AMETHYST_SHARD)), vanillaExpected, "recipes taking the vanilla shard");
		for (GeodeFamily f : ModContent.FAMILIES) {
			Set<String> expected = Set.of("vplus_geodes:" + f.name() + "_amethyst_block", "minecraft:spyglass",
					"minecraft:tinted_glass", "minecraft:calibrated_sculk_sensor");
			h.assertValueEqual(recipesAccepting(rm, s(f.shard())), expected, "recipes taking " + f.name() + " shard");
			h.assertTrue(s(f.shard()).is(ALL_SHARDS), f.name() + " in vplus_geodes:amethyst_shards");
		}
		h.assertTrue(s(Items.AMETHYST_SHARD).is(ALL_SHARDS), "vanilla shard in vplus_geodes:amethyst_shards");
		// only our 15 block recipes live in our namespace (no leftover duplicate spyglass/tinted glass/sensor recipes)
		Set<String> ours = new HashSet<>();
		rm.getRecipes().forEach(r -> {
			if (r.id().identifier().getNamespace().equals("vplus_geodes")) {
				ours.add(r.id().identifier().getPath());
			}
		});
		Set<String> expectedOurs = new HashSet<>();
		ModContent.FAMILIES.forEach(f -> expectedOurs.add(f.name() + "_amethyst_block"));
		h.assertValueEqual(ours, expectedOurs, "vplus_geodes recipe ids");
		h.succeed();
	}

	private static ResourceKey<Recipe<?>> key(String id) {
		return ResourceKey.create(Registries.RECIPE, Identifier.parse(id));
	}

	/** Survival unlocks: picking up a coloured shard unlocks its own block recipe and the three shared recipes, nothing else. */
	@GameTest
	@SuppressWarnings("removal") // only vanilla helper that creates a player with advancements + recipe book
	public void recipeUnlocksInSurvival(GameTestHelper h) {
		for (Item shard : allShards()) {
			ServerPlayer p = h.makeMockServerPlayerInLevel();
			ItemStack st = s(shard);
			p.getInventory().add(st.copy());
			CriteriaTriggers.INVENTORY_CHANGED.trigger(p, p.getInventory(), st);
			String own = shard == Items.AMETHYST_SHARD ? "minecraft:amethyst_block"
					: "vplus_geodes:" + ModContent.FAMILIES.stream().filter(f -> f.shard() == shard).findFirst().orElseThrow().name()
							+ "_amethyst_block";
			for (String id : List.of(own, "minecraft:spyglass", "minecraft:tinted_glass", "minecraft:calibrated_sculk_sensor")) {
				h.assertTrue(p.getRecipeBook().contains(key(id)), shard + " should unlock " + id);
			}
			for (GeodeFamily f : ModContent.FAMILIES) {
				String other = "vplus_geodes:" + f.name() + "_amethyst_block";
				if (!other.equals(own)) {
					h.assertFalse(p.getRecipeBook().contains(key(other)), shard + " must not unlock " + other);
				}
			}
			if (shard != Items.AMETHYST_SHARD) {
				h.assertFalse(p.getRecipeBook().contains(key("minecraft:amethyst_block")), shard + " must not unlock vanilla block");
			}
			p.discard();
		}
		h.succeed();
	}
}

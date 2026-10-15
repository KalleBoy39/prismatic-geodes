package dev.vplus.geodes.miniblocks.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

/** The 15 mini blocks: stonecutter output, unlocking, and placing/breaking like Vanilla Tweaks mini blocks. */
public class MiniBlockGameTests {
	static final String NS = "vplus_geodes_miniblocks";
	static final List<String> COLOURS = List.of("white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow",
			"lime", "green", "cyan", "light_blue", "blue", "magenta", "pink");

	static String title(String colour) {
		StringBuilder b = new StringBuilder();
		for (String w : colour.split("_")) {
			b.append(b.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return b.toString();
	}

	static Item block(String colour) {
		return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("vplus_geodes", colour + "_amethyst_block"));
	}

	static List<RecipeHolder<StonecutterRecipe>> stonecutting(MinecraftServer server, ItemStack input) {
		List<RecipeHolder<StonecutterRecipe>> out = new ArrayList<>();
		SingleRecipeInput in = new SingleRecipeInput(input);
		for (RecipeHolder<?> r : server.getRecipeManager().getRecipes()) {
			if (r.value().getType() == RecipeType.STONECUTTING && ((StonecutterRecipe) r.value()).matches(in, null)) {
				@SuppressWarnings("unchecked")
				RecipeHolder<StonecutterRecipe> sr = (RecipeHolder<StonecutterRecipe>) r;
				out.add(sr);
			}
		}
		return out;
	}

	static Identifier texture(ItemStack stack) {
		ResolvableProfile p = stack.get(DataComponents.PROFILE);
		return p == null ? null : p.skinPatch().body().map(ClientAsset.ResourceTexture::id).orElse(null);
	}

	/** Each coloured block stonecuts into exactly one result: 8 named player heads with the addon's own skin. */
	@GameTest
	public void stonecutterGivesEightNamedHeads(GameTestHelper h) {
		MinecraftServer server = h.getLevel().getServer();
		for (String c : COLOURS) {
			Item block = block(c);
			h.assertTrue(block != Items.AIR, "vplus_geodes:" + c + "_amethyst_block exists");
			List<RecipeHolder<StonecutterRecipe>> recipes = stonecutting(server, new ItemStack(block));
			h.assertValueEqual(recipes.size(), 1, c + " stonecutter recipes");
			RecipeHolder<StonecutterRecipe> r = recipes.getFirst();
			h.assertValueEqual(r.id().identifier(), Identifier.fromNamespaceAndPath(NS, c + "_amethyst_block"), c + " recipe id");
			ItemStack out = r.value().assemble(new SingleRecipeInput(new ItemStack(block)));
			h.assertTrue(out.is(Items.PLAYER_HEAD), c + " gives a player head");
			h.assertValueEqual(out.getCount(), 8, c + " count");
			h.assertValueEqual(out.getHoverName().getString(), "Mini " + title(c) + " Amethyst Block", c + " name");
			h.assertValueEqual(out.get(DataComponents.RARITY), Rarity.COMMON, c + " rarity");
			h.assertValueEqual(texture(out), Identifier.fromNamespaceAndPath(NS, "entity/mini_block/" + c + "_amethyst_block"),
					c + " skin texture");
		}
		h.succeed();
	}

	/** Exactly the 15 recipes live in the addon namespace; vanilla amethyst (purple) is left to Vanilla Tweaks. */
	@GameTest
	public void exactRecipeSetAndPurpleUntouched(GameTestHelper h) {
		MinecraftServer server = h.getLevel().getServer();
		Set<String> ours = new HashSet<>();
		server.getRecipeManager().getRecipes().forEach(r -> {
			if (r.id().identifier().getNamespace().equals(NS)) ours.add(r.id().identifier().getPath());
		});
		Set<String> expected = new HashSet<>();
		COLOURS.forEach(c -> expected.add(c + "_amethyst_block"));
		h.assertValueEqual(ours, expected, "addon recipe ids");
		for (RecipeHolder<StonecutterRecipe> r : stonecutting(server, new ItemStack(Items.AMETHYST_BLOCK))) {
			h.assertFalse(r.id().identifier().getNamespace().equals(NS), "addon must not add a purple recipe: " + r.id());
		}
		h.succeed();
	}

	private static ResourceKey<Recipe<?>> key(String colour) {
		return ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(NS, colour + "_amethyst_block"));
	}

	/** Like Vanilla Tweaks: picking up a block unlocks its own mini block recipe and no other. */
	@GameTest
	@SuppressWarnings("removal") // only vanilla helper that creates a player with advancements + recipe book
	public void pickingUpBlockUnlocksItsRecipe(GameTestHelper h) {
		MinecraftServer server = h.getLevel().getServer();
		AdvancementHolder root = server.getAdvancements().get(Identifier.fromNamespaceAndPath(NS, "recipes/root"));
		h.assertTrue(root != null, "root advancement loaded");
		for (String c : COLOURS) {
			AdvancementHolder adv = server.getAdvancements().get(Identifier.fromNamespaceAndPath(NS, "recipes/" + c + "_amethyst_block"));
			h.assertTrue(adv != null && adv.value().parent().equals(Optional.of(root.id())), c + " unlock advancement under root");
			ServerPlayer p = h.makeMockServerPlayerInLevel();
			h.assertFalse(p.getRecipeBook().contains(key(c)), c + " locked before pickup");
			ItemStack st = new ItemStack(block(c));
			p.getInventory().add(st.copy());
			CriteriaTriggers.INVENTORY_CHANGED.trigger(p, p.getInventory(), st);
			for (String o : COLOURS) {
				h.assertValueEqual(p.getRecipeBook().contains(key(o)), o.equals(c), c + " pickup -> " + o + " unlocked");
			}
			p.discard();
		}
		h.succeed();
	}

	/** Placed as a block it keeps the skin; the head's block entity carries the components the loot table copies. */
	@GameTest
	public void placedHeadKeepsProfileAndName(GameTestHelper h) {
		MinecraftServer server = h.getLevel().getServer();
		BlockPos pos = new BlockPos(1, 1, 1);
		ItemStack head = stonecutting(server, new ItemStack(block("cyan"))).getFirst().value()
				.assemble(new SingleRecipeInput(new ItemStack(block("cyan"))));
		h.setBlock(pos, Blocks.PLAYER_HEAD);
		SkullBlockEntity be = h.getBlockEntity(pos, SkullBlockEntity.class);
		be.applyComponentsFromItemStack(head);
		h.assertValueEqual(be.getOwnerProfile().skinPatch().body().map(ClientAsset.ResourceTexture::id).orElse(null),
				Identifier.fromNamespaceAndPath(NS, "entity/mini_block/cyan_amethyst_block"), "placed skin");
		ItemStack drop = new ItemStack(Items.PLAYER_HEAD);
		drop.applyComponents(be.collectComponents());
		h.assertValueEqual(drop.getHoverName().getString(), "Mini Cyan Amethyst Block", "name after breaking");
		h.assertValueEqual(texture(drop), Identifier.fromNamespaceAndPath(NS, "entity/mini_block/cyan_amethyst_block"),
				"skin after breaking");
		h.succeed();
	}
}

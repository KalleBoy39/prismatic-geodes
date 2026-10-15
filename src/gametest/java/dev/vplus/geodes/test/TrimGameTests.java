package dev.vplus.geodes.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.vplus.geodes.GeodeFamily;
import dev.vplus.geodes.ModContent;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;

/** Coloured amethyst trim materials: one per shard, every template, every trimmable armor; vanilla untouched. */
public class TrimGameTests {
	private static String key(Holder<?> h) {
		return h.unwrapKey().map(k -> k.identifier().toString()).orElse("inline");
	}

	private static String expectedMaterial(Item shard) {
		if (shard == Items.AMETHYST_SHARD) {
			return "minecraft:amethyst";
		}
		GeodeFamily f = ModContent.FAMILIES.stream().filter(x -> x.shard() == shard).findFirst().orElseThrow();
		return "vplus_geodes:" + f.name() + "_amethyst";
	}

	private static List<Item> allShards() {
		List<Item> out = new ArrayList<>();
		ModContent.FAMILIES.forEach(f -> out.add(f.shard()));
		out.add(Items.AMETHYST_SHARD);
		return out;
	}

	private static List<Item> templates() {
		List<Item> out = new ArrayList<>();
		for (Item i : BuiltInRegistries.ITEM) {
			if (BuiltInRegistries.ITEM.getKey(i).getPath().endsWith("_armor_trim_smithing_template")) {
				out.add(i);
			}
		}
		return out;
	}

	private static List<Item> trimmableArmor() {
		List<Item> out = new ArrayList<>();
		BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.TRIMMABLE_ARMOR).forEach(h -> out.add(h.value()));
		return out;
	}

	private static ItemStack smith(GameTestHelper h, Item template, Item armor, Item addition) {
		ServerLevel level = h.getLevel();
		SmithingRecipeInput in = new SmithingRecipeInput(new ItemStack(template), new ItemStack(armor), new ItemStack(addition));
		return level.getServer().getRecipeManager().getRecipeFor(RecipeType.SMITHING, in, level)
				.map(r -> r.value().assemble(in)).orElse(ItemStack.EMPTY);
	}

	/** 1-3: each coloured shard is a trim material ingredient with its own material; vanilla shard stays amethyst. */
	@GameTest
	public void eachShardIsItsOwnTrimMaterial(GameTestHelper h) {
		var materials = h.getLevel().registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL);
		Set<String> seen = new HashSet<>();
		for (Item shard : allShards()) {
			ItemStack st = new ItemStack(shard);
			h.assertTrue(st.is(ItemTags.TRIM_MATERIALS), shard + " in #minecraft:trim_materials");
			Holder<TrimMaterial> mat = st.get(DataComponents.PROVIDES_TRIM_MATERIAL);
			h.assertTrue(mat != null, shard + " provides a trim material");
			String id = key(mat);
			h.assertValueEqual(id, expectedMaterial(shard), shard + " material id");
			h.assertTrue(seen.add(id), "material id is unique: " + id);
			TrimMaterial m = materials.getOrThrow(ResourceKey.create(Registries.TRIM_MATERIAL, Identifier.parse(id))).value();
			String expectedPalette = id.replace(":", ":trim/");
			h.assertValueEqual(m.paletteId().toString(), expectedPalette, id + " palette id");
		}
		h.assertValueEqual(seen.size(), 16, "16 distinct amethyst trim materials");
		h.succeed();
	}

	/** 4: every vanilla template x every shard on a diamond chestplate gives that pattern in that shard's material. */
	@GameTest
	public void allTemplatesWorkWithColouredShards(GameTestHelper h) {
		List<Item> tpl = templates();
		h.assertValueEqual(tpl.size(), 18, "vanilla 26.3 trim templates");
		for (Item t : tpl) {
			String pattern = "minecraft:" + BuiltInRegistries.ITEM.getKey(t).getPath().replace("_armor_trim_smithing_template", "");
			for (Item shard : allShards()) {
				ItemStack out = smith(h, t, Items.DIAMOND_CHESTPLATE, shard);
				ArmorTrim trim = out.get(DataComponents.TRIM);
				h.assertTrue(out.is(Items.DIAMOND_CHESTPLATE) && trim != null, t + " + " + shard + " -> " + out);
				h.assertValueEqual(key(trim.pattern()), pattern, t + " pattern");
				h.assertValueEqual(key(trim.material()), expectedMaterial(shard), t + " + " + shard + " material");
			}
		}
		h.succeed();
	}

	/** Every trimmable armor piece (leather, chainmail, iron, gold, diamond, netherite, copper, turtle...) x every shard. */
	@GameTest
	public void everyTrimmableArmorAcceptsColouredShards(GameTestHelper h) {
		List<Item> armor = trimmableArmor();
		h.assertTrue(armor.size() >= 29, "trimmable armor count " + armor.size());
		for (Item a : armor) {
			for (Item shard : allShards()) {
				ItemStack out = smith(h, Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, a, shard);
				ArmorTrim trim = out.get(DataComponents.TRIM);
				h.assertTrue(out.is(a) && trim != null, a + " + " + shard + " -> " + out);
				h.assertValueEqual(key(trim.material()), expectedMaterial(shard), a + " + " + shard);
				h.assertValueEqual(key(trim.pattern()), "minecraft:ward", a + " pattern");
			}
		}
		h.succeed();
	}

	/** 5: vanilla materials still map to themselves and keep their vanilla palettes. */
	@GameTest
	public void vanillaTrimMaterialsUnchanged(GameTestHelper h) {
		Map<Item, String> vanilla = Map.ofEntries(Map.entry(Items.AMETHYST_SHARD, "amethyst"), Map.entry(Items.COPPER_INGOT, "copper"),
				Map.entry(Items.DIAMOND, "diamond"), Map.entry(Items.EMERALD, "emerald"), Map.entry(Items.GOLD_INGOT, "gold"),
				Map.entry(Items.IRON_INGOT, "iron"), Map.entry(Items.LAPIS_LAZULI, "lapis"), Map.entry(Items.NETHERITE_INGOT, "netherite"),
				Map.entry(Items.QUARTZ, "quartz"), Map.entry(Items.REDSTONE, "redstone"), Map.entry(Items.RESIN_BRICK, "resin"));
		var materials = h.getLevel().registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL);
		for (var e : vanilla.entrySet()) {
			ItemStack out = smith(h, Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, Items.IRON_CHESTPLATE, e.getKey());
			ArmorTrim trim = out.get(DataComponents.TRIM);
			h.assertTrue(trim != null, e.getKey() + " still trims");
			h.assertValueEqual(key(trim.material()), "minecraft:" + e.getValue(), e.getKey() + " material");
			TrimMaterial m = materials.getOrThrow(ResourceKey.create(Registries.TRIM_MATERIAL, Identifier.withDefaultNamespace(e.getValue()))).value();
			h.assertValueEqual(m.paletteId().toString(), "minecraft:trim/" + e.getValue(), e.getValue() + " palette");
		}
		long ours = materials.listElementIds().filter(k -> k.identifier().getNamespace().equals("vplus_geodes")).count();
		long theirs = materials.listElementIds().filter(k -> k.identifier().getNamespace().equals("minecraft")).count();
		h.assertValueEqual(ours, 15L, "vplus_geodes trim materials");
		h.assertValueEqual(theirs, 11L, "vanilla trim materials still all present");
		h.succeed();
	}

	/** 6: a trimmed item survives a full save/load round trip (NBT with registry ops), for every shard. */
	@GameTest
	public void trimSurvivesSaveLoad(GameTestHelper h) {
		var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		for (Item shard : allShards()) {
			ItemStack trimmed = smith(h, Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, Items.NETHERITE_LEGGINGS, shard);
			Tag saved = ItemStack.CODEC.encodeStart(ops, trimmed).getOrThrow();
			ItemStack loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
			h.assertTrue(ItemStack.isSameItemSameComponents(trimmed, loaded), shard + " round trip");
			h.assertValueEqual(key(loaded.get(DataComponents.TRIM).material()), expectedMaterial(shard), shard + " after load");
		}
		h.succeed();
	}
}

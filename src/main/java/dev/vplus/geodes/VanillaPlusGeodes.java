package dev.vplus.geodes;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Prismatic Geodes (mod id {@code vplus_geodes}; the id and this class name predate the public name). Blocks/items are registered in code; geode generation is pure data
 * (see data/minecraft/worldgen/feature/amethyst_geode.json in this jar).
 */
public final class VanillaPlusGeodes implements ModInitializer {
	public static final String MOD_ID = "vplus_geodes";

	// vanilla keeps these keys private
	private static final ResourceKey<CreativeModeTab> NATURAL_BLOCKS =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("natural_blocks"));
	private static final ResourceKey<CreativeModeTab> INGREDIENTS =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("ingredients"));

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModContent.init();

		CreativeModeTabEvents.modifyOutputEvent(NATURAL_BLOCKS).register(output -> {
			List<ItemStack> stacks = new ArrayList<>();
			for (GeodeFamily f : ModContent.FAMILIES) {
				stacks.add(new ItemStack(f.block()));
				stacks.add(new ItemStack(f.budding()));
				stacks.add(new ItemStack(f.smallBud()));
				stacks.add(new ItemStack(f.mediumBud()));
				stacks.add(new ItemStack(f.largeBud()));
				stacks.add(new ItemStack(f.cluster()));
			}
			output.insertAfter(Items.AMETHYST_CLUSTER, stacks);
		});
		CreativeModeTabEvents.modifyOutputEvent(INGREDIENTS).register(output -> output.insertAfter(
				Items.AMETHYST_SHARD, ModContent.FAMILIES.stream().map(f -> new ItemStack(f.shard())).toList()));
	}
}

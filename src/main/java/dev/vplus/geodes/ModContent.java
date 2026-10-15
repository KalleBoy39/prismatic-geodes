package dev.vplus.geodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import dev.vplus.geodes.block.ColouredBuddingAmethystBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Registers the 15 non-purple colour families. Every block is a full copy of the matching vanilla
 * amethyst block's properties (strength, sounds, light, piston reaction, tool requirement, random
 * ticking...); only the map colour follows the dye colour.
 */
public final class ModContent {
	public static final List<GeodeFamily> FAMILIES;

	static {
		List<GeodeFamily> families = new ArrayList<>();
		for (DyeColor colour : DyeColor.values()) {
			if (colour != DyeColor.PURPLE) {
				families.add(registerFamily(colour));
			}
		}
		FAMILIES = Collections.unmodifiableList(families);
	}

	private ModContent() {
	}

	static void init() {
		// class loading runs the static registration above
	}

	private static GeodeFamily registerFamily(DyeColor colour) {
		String c = colour.getSerializedName();
		Block block = block(c + "_amethyst_block", Blocks.AMETHYST_BLOCK, colour, AmethystBlock::new);
		// vanilla shapes (height, width): cluster 7/10, large 5/10, medium 4/10, small 3/8
		Block cluster = block(c + "_amethyst_cluster", Blocks.AMETHYST_CLUSTER, colour,
				p -> new AmethystClusterBlock(7.0F, 10.0F, p));
		Block large = block("large_" + c + "_amethyst_bud", Blocks.LARGE_AMETHYST_BUD, colour,
				p -> new AmethystClusterBlock(5.0F, 10.0F, p));
		Block medium = block("medium_" + c + "_amethyst_bud", Blocks.MEDIUM_AMETHYST_BUD, colour,
				p -> new AmethystClusterBlock(4.0F, 10.0F, p));
		Block small = block("small_" + c + "_amethyst_bud", Blocks.SMALL_AMETHYST_BUD, colour,
				p -> new AmethystClusterBlock(3.0F, 8.0F, p));
		Block budding = block(c + "_budding_amethyst", Blocks.BUDDING_AMETHYST, colour,
				p -> new ColouredBuddingAmethystBlock(() -> small, () -> medium, () -> large, () -> cluster, p));
		// Each shard provides its own trim material (data/vplus_geodes/trim_material/<colour>_amethyst.json),
		// the same way vanilla's amethyst shard provides minecraft:amethyst. Item components can only be set here.
		Item shard = item(c + "_amethyst_shard", Item::new, new Item.Properties()
				.trimMaterial(ResourceKey.create(Registries.TRIM_MATERIAL, VanillaPlusGeodes.id(c + "_amethyst"))));
		return new GeodeFamily(colour, block, budding, small, medium, large, cluster, shard);
	}

	private static Block block(String name, Block vanilla, DyeColor colour,
			Function<BlockBehaviour.Properties, Block> factory) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, VanillaPlusGeodes.id(name));
		Block block = factory.apply(BlockBehaviour.Properties.ofFullCopy(vanilla)
				.mapColor(colour.getMapColor())
				.setId(key));
		Registry.register(BuiltInRegistries.BLOCK, key, block);
		item(name, p -> new BlockItem(block, p), new Item.Properties().useBlockDescriptionPrefix());
		return block;
	}

	private static Item item(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, VanillaPlusGeodes.id(name));
		Item item = factory.apply(properties.setId(key));
		// same as vanilla Items.registerItem: block items map their block back to the item
		if (item instanceof BlockItem blockItem) {
			blockItem.registerBlocks(Item.BY_BLOCK, item);
		}
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}
}

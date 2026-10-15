package dev.vplus.geodes;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** One registered colour family (purple is vanilla amethyst and has no family). */
public record GeodeFamily(DyeColor colour, Block block, Block budding, Block smallBud, Block mediumBud,
		Block largeBud, Block cluster, Item shard) {
	public String name() {
		return colour.getSerializedName();
	}
}

package dev.vplus.geodes.block;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * Budding amethyst for one colour family.
 *
 * <p>Vanilla {@link BuddingAmethystBlock#randomTick} hard-codes the purple bud blocks, so this
 * re-implements that method line for line (same 1-in-5 chance, same random direction, same
 * stage checks, same waterlogging) with the four growth stages of its own family.
 */
public class ColouredBuddingAmethystBlock extends BuddingAmethystBlock {
	private static final Direction[] DIRECTIONS = Direction.values();

	private final Supplier<Block> small;
	private final Supplier<Block> medium;
	private final Supplier<Block> large;
	private final Supplier<Block> cluster;

	public ColouredBuddingAmethystBlock(Supplier<Block> small, Supplier<Block> medium, Supplier<Block> large,
			Supplier<Block> cluster, Properties properties) {
		super(properties);
		this.small = small;
		this.medium = medium;
		this.large = large;
		this.cluster = cluster;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (random.nextInt(GROWTH_CHANCE) != 0) {
			return;
		}
		Direction direction = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
		BlockPos growPos = pos.relative(direction);
		BlockState growState = level.getBlockState(growPos);
		Block next = null;
		if (canClusterGrowAtState(growState)) {
			next = small.get();
		} else if (growState.is(small.get()) && growState.getValue(AmethystClusterBlock.FACING) == direction) {
			next = medium.get();
		} else if (growState.is(medium.get()) && growState.getValue(AmethystClusterBlock.FACING) == direction) {
			next = large.get();
		} else if (growState.is(large.get()) && growState.getValue(AmethystClusterBlock.FACING) == direction) {
			next = cluster.get();
		}
		if (next != null) {
			BlockState grown = next.defaultBlockState()
					.setValue(AmethystClusterBlock.FACING, direction)
					.setValue(AmethystClusterBlock.WATERLOGGED, growState.getFluidState().is(Fluids.WATER));
			level.setBlockAndUpdate(growPos, grown);
		}
	}
}

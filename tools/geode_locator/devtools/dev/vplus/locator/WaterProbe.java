package dev.vplus.locator;

import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Dev-only: how well do cheap land/water rules match the exact 4-block height grid?
 * Rules: biome only; coarse heights (every S blocks, bilinear); biome + coarse heights.
 */
public final class WaterProbe {
	public static void main(String[] a) throws Exception {
		long seed = Long.parseLong(a[0]);
		int ox = Integer.parseInt(a[1]), oz = Integer.parseInt(a[2]);
		Terrain t = new Terrain(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "sp-cache"));
		t.startLoading();
		while (!t.ready()) Thread.sleep(100);
		NoiseBasedChunkGenerator gen = (NoiseBasedChunkGenerator) SurfaceProbe.get(t, "generator");
		RandomState rs = (RandomState) SurfaceProbe.invoke(t, seed);
		var src = (net.minecraft.world.level.biome.MultiNoiseBiomeSource) SurfaceProbe.get(t, "biomeSource");
		var lh = net.minecraft.world.level.LevelHeightAccessor.create(-64, 384);
		int n = 128, step = 4, S = 16;
		int[][] exact = new int[n][n];
		long t0 = System.nanoTime();
		for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) exact[i][j] = gen.getBaseHeight(ox + i * step, oz + j * step, Heightmap.Types.OCEAN_FLOOR_WG, lh, rs);
		long t1 = System.nanoTime();
		BiomeResolver br = src.createUncachedResolver(rs);
		String[][] bio = new String[n][n];
		for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) bio[i][j] = br.getNoiseBiome((ox + i * step) >> 2, 63 >> 2, (oz + j * step) >> 2).unwrapKey().get().identifier().getPath();
		long t2 = System.nanoTime();
		int m = n * step / S + 2;
		int[][] coarse = new int[m][m];
		for (int i = 0; i < m; i++) for (int j = 0; j < m; j++) coarse[i][j] = gen.getBaseHeight(ox + i * S, oz + j * S, Heightmap.Types.OCEAN_FLOOR_WG, lh, rs);
		System.out.printf("exact %.0f us/col, biome %.1f us/col%n", (t1 - t0) / 1e3 / (n * n), (t2 - t1) / 1e3 / (n * n));
		int okB = 0, okC = 0, okBC = 0, okBC2 = 0;
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				boolean w = exact[i][j] < 63;
				String b = bio[i][j];
				boolean bw = b.contains("ocean") || b.contains("river");
				double fx = i * step / (double) S, fz = j * step / (double) S;
				int ix = (int) fx, iz = (int) fz;
				double u = fx - ix, v = fz - iz;
				double hc = coarse[ix][iz] * (1 - u) * (1 - v) + coarse[ix + 1][iz] * u * (1 - v) + coarse[ix][iz + 1] * (1 - u) * v + coarse[ix + 1][iz + 1] * u * v;
				boolean cw = hc < 63;
				if (bw == w) okB++;
				if (cw == w) okC++;
				if ((bw ? hc < 66 : hc < 60) == w) okBC++;
				if ((bw ? hc < 64.5 : hc < 61.5) == w) okBC2++;
			}
		}
		double N = n * n / 100.0;
		System.out.printf("biome-only %.1f%%  coarse(%d) %.1f%%  biome+coarse %.1f%%  biome+coarse2 %.1f%%%n", okB / N, S, okC / N, okBC / N, okBC2 / N);
		System.exit(0);
	}
}

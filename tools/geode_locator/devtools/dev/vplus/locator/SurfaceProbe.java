package dev.vplus.locator;

import java.lang.reflect.Field;

import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

/** Dev-only: speed/accuracy of the router's chunkSurfaceLevel and a finalDensity column search vs getBaseHeight. */
public final class SurfaceProbe {
	public static void main(String[] a) throws Exception {
		long seed = Long.parseLong(a[0]);
		Terrain t = new Terrain(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "sp-cache"));
		t.startLoading();
		while (!t.ready()) Thread.sleep(100);
		NoiseBasedChunkGenerator gen = (NoiseBasedChunkGenerator) get(t, "generator");
		NoiseGeneratorSettings s = (NoiseGeneratorSettings) get(t, "settings");
		RandomState rs = (RandomState) invoke(t, seed);
		var lh = net.minecraft.world.level.LevelHeightAccessor.create(-64, 384);
		var router = s.noiseRouter();
		int n = 3000;
		int[] xs = new int[n], zs = new int[n];
		java.util.Random r = new java.util.Random(1);
		for (int i = 0; i < n; i++) {
			xs[i] = r.nextInt(8000) - 4000;
			zs[i] = r.nextInt(8000) - 4000;
		}
		for (int round = 0; round < 3; round++) {
			int[] exact = new int[n];
			long t0 = System.nanoTime();
			for (int i = 0; i < n; i++) exact[i] = gen.getBaseHeight(xs[i], zs[i], Heightmap.Types.OCEAN_FLOOR_WG, lh, rs);
			long t1 = System.nanoTime();
			float[] est = new float[n];
			for (int i = 0; i < n; i++) est[i] = rs.sampleBlockValueUncached(router.chunkSurfaceLevel(), xs[i], 0, zs[i]);
			long t2 = System.nanoTime();
			int[] search = new int[n];
			for (int i = 0; i < n; i++) {
				int y = (int) est[i] + 24;
				while (y > -64 && rs.sampleBlockValueUncached(router.finalDensity(), xs[i], y, zs[i]) <= 0) y--;
				search[i] = y + 1;
			}
			long t3 = System.nanoTime();
			int w3e = 0, w3s = 0, wwE = 0, wwS = 0;
			for (int i = 0; i < n; i++) {
				if (Math.abs(exact[i] - Math.round(est[i])) <= 3) w3e++;
				if (Math.abs(exact[i] - search[i]) <= 3) w3s++;
				if ((exact[i] < 63) == (est[i] < 63)) wwE++;
				if ((exact[i] < 63) == (search[i] < 63)) wwS++;
			}
			System.out.printf("exact %.0f us | surfaceLevel %.0f us within3 %.1f%% water-agree %.1f%% | densitySearch %.0f us within3 %.1f%% water-agree %.1f%%%n",
					(t1 - t0) / 1e3 / n, (t2 - t1) / 1e3 / n, 100.0 * w3e / n, 100.0 * wwE / n, (t3 - t2) / 1e3 / n, 100.0 * w3s / n, 100.0 * wwS / n);
		}
		System.exit(0);
	}

	static Object get(Object o, String f) throws Exception {
		Field fl = o.getClass().getDeclaredField(f);
		fl.setAccessible(true);
		return fl.get(o);
	}

	static Object invoke(Terrain t, long seed) throws Exception {
		var m = Terrain.class.getDeclaredMethod("state", long.class);
		m.setAccessible(true);
		return m.invoke(t, seed);
	}
}

package dev.vplus.locator;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Dev-only check of the seed terrain map (not shipped). Renders a mosaic for visual review and compares
 * biome, base height and land/water with a world that was really generated (validation server).
 * Usage: TerrainCheck <seed> <world folder or -> <out dir>
 */
public final class TerrainCheck {
	public static void main(String[] a) throws Exception {
		long seed = Long.parseLong(a[0]);
		Path out = Path.of(a[2]);
		Files.createDirectories(out);
		Terrain t = new Terrain(out.resolve("cache"));
		t.startLoading();
		while (!t.ready()) {
			Thread.sleep(200);
			if (t.status().startsWith("terrain map unavailable")) throw new IllegalStateException(t.status());
		}
		System.out.println("unknown biomes: " + t.unknownBiomes());
		for (int level : new int[] {4, 2}) {
			int k = level == 2 ? 3 : 2; // tiles each side of the origin
			BufferedImage m = new BufferedImage(2 * k * Terrain.TILE, 2 * k * Terrain.TILE, BufferedImage.TYPE_INT_RGB);
			long t0 = System.nanoTime();
			List<java.util.concurrent.Future<?>> fs = new ArrayList<>();
			var ex = java.util.concurrent.Executors.newFixedThreadPool(12);
			for (int tx = -k; tx < k; tx++) {
				for (int tz = -k; tz < k; tz++) {
					int fx = tx, fz = tz;
					fs.add(ex.submit(() -> {
						byte[] png = t.tile(seed, level, fx, fz);
						BufferedImage im = ImageIO.read(new ByteArrayInputStream(png));
						synchronized (m) {
							m.getGraphics().drawImage(im, (fx + k) * Terrain.TILE, (fz + k) * Terrain.TILE, null);
						}
						return null;
					}));
				}
			}
			for (var f : fs) f.get();
			ex.shutdown();
			System.out.printf("level %d: %d tiles in %.1f s%n", level, fs.size(), (System.nanoTime() - t0) / 1e9);
			ImageIO.write(m, "png", out.resolve("mosaic_L" + level + ".png").toFile());
		}
		if (!a[1].equals("-")) compare(t, seed, Path.of(a[1]));
		System.exit(0);
	}

	@SuppressWarnings("unchecked")
	static void compare(Terrain t, long seed, Path world) throws Exception {
		Path rdir = Anvil.regionDir(world);
		int n = 0, biomeSame = 0, waterSame = 0, within3 = 0;
		List<Integer> diffs = new ArrayList<>();
		Map<String, Integer> confusions = new java.util.TreeMap<>();
		for (var f : Files.list(rdir).toList()) {
			String[] p = f.getFileName().toString().split("\\.");
			Anvil.Region r = new Anvil.Region(f, Integer.parseInt(p[1]), Integer.parseInt(p[2]));
			for (int i = 0; i < 1024; i += 7) {
				if (!r.present(i)) continue;
				Map<String, Object> c = r.chunk(i);
				if (c == null || !"minecraft:full".equals(c.get("Status"))) continue;
				int cx = r.rx * 32 + (i & 31), cz = r.rz * 32 + (i >> 5);
				Map<String, Object> hm = (Map<String, Object>) c.get("Heightmaps");
				long[] floor = (long[]) hm.get("OCEAN_FLOOR"), surf = (long[]) hm.get("WORLD_SURFACE");
				for (int s = 0; s < 4; s++) {
					int lx = s * 4 + 2, lz = (s * 7 + 3) & 15, idx = lz * 16 + lx;
					int realFloor = unpack(floor, idx) - 64, realSurf = unpack(surf, idx) - 64;
					String[] pr = t.probe(seed, cx * 16 + lx, cz * 16 + lz);
					int predH = Integer.parseInt(pr[1]);
					boolean predWater = predH < Terrain.SEA_LEVEL;
					boolean realWater = realSurf > realFloor && realFloor < Terrain.SEA_LEVEL;
					String realBiome = biomeAt(c, lx, Math.max(realFloor, Terrain.SEA_LEVEL), lz);
					n++;
					if (pr[0].equals(realBiome)) biomeSame++;
					else confusions.merge(pr[0] + " vs " + realBiome, 1, Integer::sum);
					if (predWater == realWater) waterSame++;
					int d = realFloor - predH;
					diffs.add(d);
					if (Math.abs(d) <= 3) within3++;
				}
			}
		}
		Collections.sort(diffs);
		System.out.printf("columns %d: biome same %.1f%%, land/water same %.1f%%, height within 3 blocks %.1f%%, median diff %d%n",
				n, 100.0 * biomeSame / n, 100.0 * waterSame / n, 100.0 * within3 / n, diffs.get(diffs.size() / 2));
		confusions.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(8).forEach(e -> System.out.println("  " + e));
	}

	static int unpack(long[] data, int idx) {
		int per = 64 / 9;
		return (int) ((data[idx / per] >>> ((idx % per) * 9)) & 511);
	}

	@SuppressWarnings("unchecked")
	static String biomeAt(Map<String, Object> chunk, int lx, int y, int lz) {
		for (Object so : (List<Object>) chunk.get("sections")) {
			Map<String, Object> s = (Map<String, Object>) so;
			if (((Number) s.get("Y")).intValue() != Math.floorDiv(y, 16)) continue;
			Map<String, Object> b = (Map<String, Object>) s.get("biomes");
			List<Object> pal = (List<Object>) b.get("palette");
			long[] data = (long[]) b.get("data");
			if (data == null) return GeodeScan.stateName(pal.get(0));
			int bits = 32 - Integer.numberOfLeadingZeros(pal.size() - 1);
			int per = 64 / bits, i = ((Math.floorMod(y, 16) >> 2) * 16) + ((lz >> 2) * 4) + (lx >> 2);
			return GeodeScan.stateName(pal.get((int) ((data[i / per] >>> ((i % per) * bits)) & ((1L << bits) - 1))));
		}
		return "?";
	}
}

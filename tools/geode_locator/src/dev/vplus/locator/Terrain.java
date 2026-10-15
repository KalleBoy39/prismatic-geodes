package dev.vplus.locator;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import net.minecraft.SharedConstants;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.util.Util;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * Terrain map straight from the seed, computed with Minecraft's own overworld noise (vanilla data pack, default
 * world type): the biome at the surface and the base terrain height ({@code OCEAN_FLOOR_WG}) per sample.
 * This is the terrain shape before caves, structures, trees and inland aquifer lakes, i.e. what Chunkbase-style
 * maps show. Prismatic Geodes does not change terrain, so no mod data is needed here.
 *
 * Tiles are 128 x 128 px; level L shows 2^L blocks per pixel. Rendered tiles are cached in memory and on disk.
 */
final class Terrain {
	static final int TILE = 128, MAX_LEVEL = 5, SEA_LEVEL = 63;
	/** Bumped whenever the rendering changes, so old disk caches are ignored. */
	static final String CACHE_VERSION = "tiles-v1";

	private final Path cacheRoot;
	private final ExecutorService pool;
	private final Map<String, CompletableFuture<byte[]>> inflight = new ConcurrentHashMap<>();
	private final Map<String, byte[]> memory = new LinkedHashMap<>(256, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, byte[]> e) {
			return size() > 600;
		}
	};
	private final Map<Long, RandomState> states = new LinkedHashMap<>(8, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, RandomState> e) {
			return size() > 4;
		}
	};
	private final Set<String> unknownBiomes = new TreeSet<>();
	private final AtomicInteger rendered = new AtomicInteger();

	private volatile String status = "not started";
	private volatile boolean ready;
	private HolderGetter<NormalNoise> noises;
	private NoiseGeneratorSettings settings;
	private MultiNoiseBiomeSource biomeSource;
	private NoiseBasedChunkGenerator generator;
	private final LevelHeightAccessor heights = LevelHeightAccessor.create(-64, 384);

	Terrain(Path cacheRoot) {
		this.cacheRoot = cacheRoot.resolve(CACHE_VERSION);
		int threads = Math.max(2, Runtime.getRuntime().availableProcessors() - 2);
		AtomicInteger n = new AtomicInteger();
		this.pool = Executors.newFixedThreadPool(threads, r -> {
			Thread t = new Thread(r, "terrain-" + n.incrementAndGet());
			t.setDaemon(true);
			t.setPriority(Thread.NORM_PRIORITY - 1);
			return t;
		});
	}

	boolean ready() {
		return ready;
	}

	String status() {
		return status;
	}

	synchronized Set<String> unknownBiomes() {
		return new TreeSet<>(unknownBiomes);
	}

	/** Loads the vanilla worldgen registries in the background (about 10 s). */
	void startLoading() {
		Thread t = new Thread(() -> {
			try {
				status = "loading Minecraft worldgen data";
				SharedConstants.tryDetectVersion();
				Bootstrap.bootStrap();
				var pack = new WorldLoader.PackConfig(ServerPacksSource.createVanillaTrustedRepository(), WorldDataConfiguration.DEFAULT, false, true);
				var init = new WorldLoader.InitConfig(pack, Commands.CommandSelection.DEDICATED, PermissionSet.NO_PERMISSIONS);
				RegistryAccess.Frozen reg = WorldLoader.<Void, RegistryAccess.Frozen>load(init,
						ctx -> new WorldLoader.DataLoadOutput<>(null, ctx.datapackDimensions()),
						(resources, reloadable, layers, unused) -> {
							resources.close();
							return layers.compositeAccess();
						}, Util.backgroundExecutor(), Runnable::run).get();
				Holder<NoiseGeneratorSettings> ns = reg.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
				settings = ns.value();
				noises = reg.lookupOrThrow(Registries.NOISE);
				biomeSource = MultiNoiseBiomeSource.createFromPreset(
						reg.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
				generator = new NoiseBasedChunkGenerator(biomeSource, ns);
				for (Holder<Biome> b : biomeSource.possibleBiomes()) {
					String id = b.unwrapKey().map(k -> k.identifier().toString()).orElse("?");
					if (BiomeColours.of(id) == null) {
						synchronized (this) {
							unknownBiomes.add(id);
						}
					}
				}
				ready = true;
				status = "ready";
			} catch (Exception | LinkageError e) {
				status = "terrain map unavailable: " + e;
				e.printStackTrace();
			}
		}, "terrain-init");
		t.setDaemon(true);
		t.start();
	}

	private synchronized RandomState state(long seed) {
		return states.computeIfAbsent(seed, s -> RandomState.create(noises, s, settings));
	}

	static String seedDir(long seed) {
		return seed < 0 ? "m" + Long.toString(seed).substring(1) : Long.toString(seed);
	}

	/** PNG of one tile; blocks until it is rendered (or loaded from the cache). */
	byte[] tile(long seed, int level, int tx, int tz) throws Exception {
		if (!ready) throw new IllegalStateException("terrain map is still loading");
		if (level < 0 || level > MAX_LEVEL) throw new IllegalArgumentException("bad zoom level");
		String key = seed + "/" + level + "/" + tx + "/" + tz;
		synchronized (memory) {
			byte[] b = memory.get(key);
			if (b != null) return b;
		}
		Path file = cacheRoot.resolve(seedDir(seed)).resolve(Integer.toString(level)).resolve(tx + "_" + tz + ".png");
		CompletableFuture<byte[]> f = inflight.computeIfAbsent(key, k -> CompletableFuture.supplyAsync(() -> {
			try {
				byte[] png;
				if (Files.isRegularFile(file)) {
					png = Files.readAllBytes(file);
				} else {
					png = render(seed, level, tx, tz);
					Files.createDirectories(file.getParent());
					Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
					Files.write(tmp, png);
					Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				}
				synchronized (memory) {
					memory.put(k, png);
				}
				return png;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}, pool));
		try {
			return f.get();
		} finally {
			inflight.remove(key, f);
		}
	}

	/** Biome cell size in blocks: vanilla's 4-block biome cells, or one pixel when zoomed further out. */
	static int biomeStep(int level) {
		return Math.max(4, 1 << level);
	}

	/**
	 * Height grid spacing in blocks. Exact heights cost ~0.2-0.4 ms per column, so they are sampled on a coarser grid
	 * and interpolated; with 16-block spacing land/water still agrees with the exact 4-block grid ~96% of the time
	 * (devtools WaterProbe), and the biome sharpens rivers and coasts.
	 */
	static int heightStep(int level) {
		return Math.max(8, 4 << level);
	}

	byte[] render(long seed, int level, int tx, int tz) throws IOException {
		RandomState rs = state(seed);
		BiomeResolver biomes = biomeSource.createUncachedResolver(rs);
		int bpp = 1 << level, span = TILE * bpp, bs = biomeStep(level), n = span / bs, px = bs / bpp, hs = heightStep(level);
		int x0 = tx * span, z0 = tz * span;
		// height grid: point k sits at x0 + (k - 1) * hs, so it covers one biome cell beyond each tile edge
		int m = span / hs + 3;
		int[][] grid = new int[m][m];
		for (int i = 0; i < m; i++) {
			for (int j = 0; j < m; j++) {
				grid[i][j] = generator.getBaseHeight(x0 + (i - 1) * hs, z0 + (j - 1) * hs, Heightmap.Types.OCEAN_FLOOR_WG, heights, rs);
			}
		}
		// per biome cell (with a one-cell border): interpolated height, biome, water
		double[][] h = new double[n + 2][n + 2];
		String[][] id = new String[n + 2][n + 2];
		boolean[][] water = new boolean[n + 2][n + 2];
		for (int i = 0; i < n + 2; i++) {
			for (int j = 0; j < n + 2; j++) {
				int x = x0 + (i - 1) * bs + bs / 2, z = z0 + (j - 1) * bs + bs / 2;
				double fx = (x - x0) / (double) hs + 1, fz = (z - z0) / (double) hs + 1;
				int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
				double u = fx - ix, v = fz - iz;
				double hh = grid[ix][iz] * (1 - u) * (1 - v) + grid[ix + 1][iz] * u * (1 - v) + grid[ix][iz + 1] * (1 - u) * v + grid[ix + 1][iz + 1] * u * v;
				h[i][j] = hh;
				int y = (int) Math.round(Math.max(hh, SEA_LEVEL));
				String b = biomes.getNoiseBiome(x >> 2, y >> 2, z >> 2).unwrapKey().map(k -> k.identifier().toString()).orElse("?");
				id[i][j] = b;
				boolean wetBiome = b.contains("ocean") || b.contains("river");
				water[i][j] = wetBiome ? hh < SEA_LEVEL + 1.5 : hh < SEA_LEVEL - 1.5;
			}
		}
		BufferedImage img = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_RGB);
		double exaggerate = 1.0 + bs / 16.0;
		for (int i = 1; i <= n; i++) {
			for (int j = 1; j <= n; j++) {
				double hh = h[i][j];
				int rgb;
				if (water[i][j]) {
					rgb = BiomeColours.water(id[i][j], (int) Math.round(Math.max(1, SEA_LEVEL - hh)));
				} else {
					int[] base = BiomeColours.of(id[i][j]);
					if (base == null) {
						synchronized (this) {
							unknownBiomes.add(id[i][j]);
						}
						base = BiomeColours.FALLBACK;
					}
					// hillshade, light from the north-west
					double dx = (h[i + 1][j] - h[i - 1][j]) / (2.0 * bs) * exaggerate;
					double dz = (h[i][j + 1] - h[i][j - 1]) / (2.0 * bs) * exaggerate;
					double len = Math.sqrt(dx * dx + 1 + dz * dz);
					double light = (dx + 1 + dz) / len;
					double shade = Math.max(0.62, Math.min(1.32, 0.35 + 0.65 * light));
					double lift = Math.max(0, Math.min(1, (hh - 90) / 130.0)) * 0.22;
					if (water[i - 1][j] || water[i + 1][j] || water[i][j - 1] || water[i][j + 1]) shade *= 0.8; // coastline
					rgb = BiomeColours.shade(base, shade, lift);
				}
				for (int a = 0; a < px; a++) {
					for (int c = 0; c < px; c++) img.setRGB((i - 1) * px + a, (j - 1) * px + c, rgb);
				}
			}
		}
		rendered.incrementAndGet();
		ByteArrayOutputStream out = new ByteArrayOutputStream(16384);
		ImageIO.write(img, "png", out);
		return out.toByteArray();
	}

	int renderedCount() {
		return rendered.get();
	}

	/** Surface biome and base height at one block column (for the cursor read-out). */
	String[] probe(long seed, int x, int z) {
		if (!ready) return null;
		RandomState rs = state(seed);
		int hh = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, rs);
		int y = hh < SEA_LEVEL ? SEA_LEVEL : hh;
		Holder<Biome> b = biomeSource.createUncachedResolver(rs).getNoiseBiome(x >> 2, y >> 2, z >> 2);
		return new String[] {b.unwrapKey().map(k -> k.identifier().toString()).orElse("?"), Integer.toString(hh)};
	}
}

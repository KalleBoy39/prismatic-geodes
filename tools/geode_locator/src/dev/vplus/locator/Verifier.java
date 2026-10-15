package dev.vplus.locator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Tier 2: generate the requested chunks (plus a 1-chunk margin) in the shadow world and classify every
 * predicted attempt as generated or aborted by terrain. Results are cached per seed in
 * runtime/shadow-server/world_<seed>/locator_verified.json, so verified chunks are never generated again.
 */
final class Verifier {
	static final int MAX_SIDE = 64;
	static final int CONFIRMED = 1, ABORTED = 2, NO_ATTEMPT = 0;

	private final ShadowServer shadow;
	private final Supplier<Predictor> predictor;
	private final Map<Long, Map<Long, int[]>> cache = new HashMap<>(); // seed -> chunk -> {status, blocks}
	private final Map<Long, List<int[]>> unexplained = new HashMap<>(); // seed -> {x,y,z,colour,blocks}

	Verifier(ShadowServer shadow, Supplier<Predictor> predictor) {
		this.shadow = shadow;
		this.predictor = predictor;
	}

	static long key(int cx, int cz) {
		return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
	}

	private Path cacheFile(long seed) {
		return shadow.world(seed).resolve("locator_verified.json");
	}

	synchronized Map<Long, int[]> results(long seed) throws IOException {
		Map<Long, int[]> m = cache.get(seed);
		if (m != null) return m;
		m = new HashMap<>();
		List<int[]> un = new ArrayList<>();
		Path f = cacheFile(seed);
		if (Files.exists(f)) {
			JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
			for (var e : o.getAsJsonArray("chunks")) {
				JsonArray a = e.getAsJsonArray();
				m.put(key(a.get(0).getAsInt(), a.get(1).getAsInt()), new int[] {a.get(2).getAsInt(), a.get(3).getAsInt()});
			}
			if (o.has("unexplained")) {
				for (var e : o.getAsJsonArray("unexplained")) un.add(new Gson().fromJson(e, int[].class));
			}
		}
		cache.put(seed, m);
		unexplained.put(seed, un);
		return m;
	}

	synchronized List<int[]> unexplained(long seed) throws IOException {
		results(seed);
		return unexplained.get(seed);
	}

	private synchronized void save(long seed) throws IOException {
		JsonObject o = new JsonObject();
		o.addProperty("note", "Prismatic Geode Locator: per-chunk geode attempt status from the shadow world. status 0=no attempt 1=generated 2=aborted by terrain");
		JsonArray arr = new JsonArray();
		for (var e : cache.get(seed).entrySet()) {
			JsonArray a = new JsonArray();
			a.add((int) (e.getKey() >> 32));
			a.add((int) (long) e.getKey());
			a.add(e.getValue()[0]);
			a.add(e.getValue()[1]);
			arr.add(a);
		}
		o.add("chunks", arr);
		o.add("unexplained", new Gson().toJsonTree(unexplained.get(seed)));
		Files.createDirectories(cacheFile(seed).getParent());
		Files.writeString(cacheFile(seed), o.toString(), StandardCharsets.UTF_8);
	}

	/** Verify every chunk of the inclusive rectangle that is not cached yet. */
	void verify(long seed, int x0, int z0, int x1, int z1, Consumer<String> progress) throws IOException, InterruptedException {
		if (x1 - x0 + 1 > MAX_SIDE || z1 - z0 + 1 > MAX_SIDE) {
			throw new IllegalArgumentException("area too large: zoom in to at most " + MAX_SIDE + " x " + MAX_SIDE + " chunks");
		}
		Map<Long, int[]> done = results(seed);
		int ux0 = Integer.MAX_VALUE, uz0 = Integer.MAX_VALUE, ux1 = Integer.MIN_VALUE, uz1 = Integer.MIN_VALUE, todo = 0;
		java.util.Set<Long> fresh = new java.util.HashSet<>();
		for (int cx = x0; cx <= x1; cx++) {
			for (int cz = z0; cz <= z1; cz++) {
				if (done.containsKey(key(cx, cz))) continue;
				todo++;
				fresh.add(key(cx, cz));
				ux0 = Math.min(ux0, cx);
				uz0 = Math.min(uz0, cz);
				ux1 = Math.max(ux1, cx);
				uz1 = Math.max(uz1, cz);
			}
		}
		if (todo == 0) {
			progress.accept("area already verified (cached)");
			return;
		}
		// geode blocks reach at most one chunk past the attempt's chunk, so a 1-chunk margin is generated too
		int gx0 = ux0 - 1, gz0 = uz0 - 1, gx1 = ux1 + 1, gz1 = uz1 + 1;
		shadow.ensure(seed, progress);
		shadow.generate(gx0, gz0, gx1, gz1, progress);
		progress.accept("scanning shadow world");
		Predictor p = predictor.get();

		GeodeScan.Blocks blocks = new GeodeScan.Blocks();
		int[] rect = {gx0, gz0, gx1, gz1};
		Path rdir = Anvil.regionDir(shadow.world(seed));
		int notFull = 0;
		for (int rx = gx0 >> 5; rx <= gx1 >> 5; rx++) {
			for (int rz = gz0 >> 5; rz <= gz1 >> 5; rz++) {
				GeodeScan.RegionResult r = GeodeScan.scanRegion(rdir.resolve("r." + rx + "." + rz + ".mca"), rx, rz, rect);
				blocks.addAll(r.blocks);
				for (int i = 0; i < 1024; i++) {
					int cx = rx * 32 + (i & 31), cz = rz * 32 + (i >> 5);
					if (cx >= gx0 && cx <= gx1 && cz >= gz0 && cz <= gz1 && !r.full[i]) notFull++;
				}
			}
		}
		if (notFull > 0) throw new IOException(notFull + " shadow chunks are not fully generated; try again");

		synchronized (this) {
			for (int cx = x0; cx <= x1; cx++) {
				for (int cz = z0; cz <= z1; cz++) {
					if (done.containsKey(key(cx, cz))) continue;
					int[] a = p.attempt(seed, cx, cz);
					if (a == null) {
						done.put(key(cx, cz), new int[] {NO_ATTEMPT, 0});
					} else {
						int n = GeodeScan.countInBox(blocks, a[0], a[1], a[2], a[3]);
						done.put(key(cx, cz), new int[] {n >= GeodeScan.GENERATED_MIN_BLOCKS ? CONFIRMED : ABORTED, n});
					}
				}
			}
			// sanity: every geode found in the new chunks must be explained by a predicted attempt of its colour
			List<int[]> un = unexplained.get(seed);
			for (GeodeScan.Component c : GeodeScan.components(blocks)) {
				for (Matcher.Found f : Matcher.explain(c, p, seed)) {
					if (fresh.contains(key(f.x() >> 4, f.z() >> 4)) && f.kind() != Matcher.Kind.MATCH) un.add(new int[] {f.x(), f.y(), f.z(), f.colour(), f.blocks()});
				}
			}
			save(seed);
		}
		progress.accept("verified " + todo + " chunks");
	}
}

package dev.vplus.locator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * Tier 1: exact geode attempts from the seed, using Minecraft's own WorldgenRandom.
 *
 * Mirrors ChunkGenerator.applyBiomeDecoration for minecraft:amethyst_geode:
 *   setDecorationSeed(seed, chunkMinX, chunkMinZ); setFeatureSeed(decorationSeed, index, step)
 *   rarity_filter   nextFloat() < 1/chance
 *   in_square       x += nextInt(16), z += nextInt(16)
 *   height_range    y = nextInt(max - min + 1) + min  (uniform)
 *   biome           no RNG; every overworld biome has the feature (checked live by the probe)
 *   selector        nextInt(16) -> Prismatic Geodes colour feature (simple_random_selector)
 * The result is the feature origin. GeodeFeature can still abort on terrain afterwards (Tier 2).
 */
final class Predictor {
	final int step, index, chance, minY, maxY;
	/** selector slot -> canonical colour index */
	final int[] slotColour;
	final String indexSource;
	final List<String> warnings = new ArrayList<>();

	private final ThreadLocal<WorldgenRandom> random = ThreadLocal.withInitial(() -> new WorldgenRandom(new XoroshiroRandomSource(0L)));

	Predictor(Path modJar, Path mcJar, Path probeJson) throws IOException {
		// colour order: the selector inside the Prismatic Geodes jar
		JsonObject selector = readJson(modJar, "data/minecraft/worldgen/feature/amethyst_geode.json");
		if (!"minecraft:simple_random_selector".equals(selector.get("type").getAsString())) {
			throw new IllegalStateException("mod's amethyst_geode is not a simple_random_selector");
		}
		JsonArray features = selector.getAsJsonArray("features");
		slotColour = new int[features.size()];
		for (int i = 0; i < features.size(); i++) {
			JsonObject f = features.get(i).getAsJsonObject();
			if (!f.getAsJsonArray("placement").isEmpty()) throw new IllegalStateException("selector entry has its own placement");
			slotColour[i] = Colours.ofFeatureId(f.get("feature").getAsString());
		}

		// placement: vanilla placed feature, which the mod leaves untouched
		JsonObject placed = readJson(mcJar, "data/minecraft/worldgen/placed_feature/amethyst_geode.json");
		JsonArray pl = placed.getAsJsonArray("placement");
		String[] expected = {"minecraft:rarity_filter", "minecraft:in_square", "minecraft:height_range", "minecraft:biome"};
		if (pl.size() != expected.length) throw new IllegalStateException("unexpected geode placement chain " + pl);
		for (int i = 0; i < expected.length; i++) {
			if (!expected[i].equals(pl.get(i).getAsJsonObject().get("type").getAsString())) {
				throw new IllegalStateException("unexpected geode placement chain " + pl);
			}
		}
		chance = pl.get(0).getAsJsonObject().get("chance").getAsInt();
		JsonObject height = pl.get(2).getAsJsonObject().getAsJsonObject("height");
		if (!"minecraft:uniform".equals(height.get("type").getAsString())) throw new IllegalStateException("height is not uniform");
		JsonObject dim = readJson(mcJar, "data/minecraft/dimension_type/overworld.json");
		int bottom = dim.get("min_y").getAsInt(), top = bottom + dim.get("height").getAsInt() - 1;
		minY = anchor(height.getAsJsonObject("min_inclusive"), bottom, top);
		maxY = anchor(height.getAsJsonObject("max_inclusive"), bottom, top);

		// step + global index: live registry (probe mod in the shadow server) when available
		int s = 2, ix = 2;
		String src = "calibrated (seed 21446193 test world: 330/330 geodes matched)";
		if (Files.exists(probeJson)) {
			JsonObject probe = JsonParser.parseString(Files.readString(probeJson, StandardCharsets.UTF_8)).getAsJsonObject();
			if (probe.has("error")) {
				warnings.add("probe error: " + probe.get("error").getAsString());
			} else {
				s = probe.get("step").getAsInt();
				ix = probe.get("index").getAsInt();
				src = "live registry (shadow server probe)";
				if (!probe.getAsJsonArray("biomes_without_geode").isEmpty()) {
					warnings.add("some overworld biomes lack amethyst_geode: " + probe.get("biomes_without_geode") + " - biome filter not modelled");
				}
				JsonArray live = probe.getAsJsonArray("selector_features");
				for (int i = 0; i < live.size(); i++) {
					if (Colours.ofFeatureId(live.get(i).getAsString()) != slotColour[i]) {
						warnings.add("live selector order differs from mod jar at slot " + i);
					}
				}
				if (!probe.get("selector_placements_empty").getAsBoolean()) warnings.add("live selector entries have placements");
			}
		}
		step = s;
		index = ix;
		indexSource = src;
	}

	private static int anchor(JsonObject a, int bottom, int top) {
		if (a.has("absolute")) return a.get("absolute").getAsInt();
		if (a.has("above_bottom")) return bottom + a.get("above_bottom").getAsInt();
		if (a.has("below_top")) return top - a.get("below_top").getAsInt();
		throw new IllegalStateException("unknown vertical anchor " + a);
	}

	static JsonObject readJson(Path jar, String entry) throws IOException {
		try (ZipFile z = new ZipFile(jar.toFile())) {
			ZipEntry e = z.getEntry(entry);
			if (e == null) throw new IOException(entry + " not found in " + jar);
			try (InputStream in = z.getInputStream(e)) {
				JsonElement el = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
				return el.getAsJsonObject();
			}
		}
	}

	/** Geode attempt of one chunk as {x, y, z, colour}, or null when the rarity roll fails. */
	int[] attempt(long seed, int cx, int cz) {
		WorldgenRandom r = random.get();
		long decorationSeed = r.setDecorationSeed(seed, cx * 16, cz * 16);
		r.setFeatureSeed(decorationSeed, index, step);
		if (!(r.nextFloat() < 1.0F / chance)) return null;
		int x = r.nextInt(16) + cx * 16;
		int z = r.nextInt(16) + cz * 16;
		int y = r.nextInt(maxY - minY + 1) + minY;
		int colour = slotColour[r.nextInt(slotColour.length)];
		return new int[] {x, y, z, colour};
	}
}

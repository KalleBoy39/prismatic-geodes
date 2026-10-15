package dev.vplus.locatorprobe;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.SimpleRandomSelectorFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Shadow-server-only probe for the geode locator. Never installed in a real profile.
 * On server start it reads the live overworld generator and writes locator_probe.json:
 * the decoration step and global feature index of minecraft:amethyst_geode (the values
 * ChunkGenerator.applyBiomeDecoration passes to setFeatureSeed), the colour order of the
 * selector that replaces it, and whether every overworld biome contains it (biome filter).
 * Read-only: it changes no worldgen state.
 */
public final class LocatorProbe implements ModInitializer {
	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(LocatorProbe::probe);
	}

	@SuppressWarnings("deprecation") // same lookup vanilla's BiomeFilter uses
	private static void probe(MinecraftServer server) {
		JsonObject out = new JsonObject();
		try {
			ServerLevel level = server.overworld();
			ChunkGenerator generator = level.getChunkSource().getGenerator();
			Registry<PlacedFeature> placed = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
			PlacedFeature geode = placed.getValue(Identifier.withDefaultNamespace("amethyst_geode"));

			Field f = ChunkGenerator.class.getDeclaredField("featuresPerStep");
			f.setAccessible(true);
			@SuppressWarnings("unchecked")
			List<FeatureSorter.StepFeatureData> steps = ((Supplier<List<FeatureSorter.StepFeatureData>>) f.get(generator)).get();
			int step = -1, index = -1;
			for (int s = 0; s < steps.size(); s++) {
				List<PlacedFeature> features = steps.get(s).features();
				for (int i = 0; i < features.size(); i++) {
					if (features.get(i) == geode) {
						step = s;
						index = steps.get(s).indexMapping().applyAsInt(geode);
					}
				}
			}
			out.addProperty("step", step);
			out.addProperty("index", index);

			Feature feature = geode.feature().value();
			out.addProperty("feature_class", feature.getClass().getName());
			JsonArray colours = new JsonArray();
			boolean placementsEmpty = true;
			if (feature instanceof SimpleRandomSelectorFeature selector) {
				for (Holder<PlacedFeature> h : selector.features()) {
					PlacedFeature sub = h.value();
					placementsEmpty &= sub.placement().isEmpty();
					colours.add(sub.feature().unwrapKey().map(k -> k.identifier().toString()).orElse("?"));
				}
			}
			out.add("selector_features", colours);
			out.addProperty("selector_placements_empty", placementsEmpty);
			JsonArray placement = new JsonArray();
			geode.placement().forEach(p -> placement.add(p.getClass().getSimpleName()));
			out.add("placement_modifiers", placement);

			List<String> missing = new ArrayList<>();
			int biomes = 0;
			for (Holder<Biome> b : generator.getBiomeSource().possibleBiomes()) {
				biomes++;
				if (!generator.getBiomeGenerationSettings(b).hasFeature(geode)) {
					missing.add(b.unwrapKey().map(k -> k.identifier().toString()).orElse("?"));
				}
			}
			out.addProperty("overworld_biomes", biomes);
			JsonArray miss = new JsonArray();
			missing.forEach(miss::add);
			out.add("biomes_without_geode", miss);
			out.addProperty("seed", level.getSeed());
			out.addProperty("min_y", level.getMinY());
		} catch (ReflectiveOperationException | RuntimeException e) {
			out.addProperty("error", e.toString());
		}
		String json = new GsonBuilder().setPrettyPrinting().create().toJson(out);
		try {
			Files.writeString(Path.of("locator_probe.json"), json, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		System.out.println("[locator-probe] " + json.replace('\n', ' '));
	}
}

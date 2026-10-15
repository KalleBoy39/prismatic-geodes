package dev.vplus.locator;

import java.util.HashMap;
import java.util.Map;

/** Map colours for overworld biomes: muted, map-mod style, so the crystal markers stay readable on top. */
final class BiomeColours {
	private BiomeColours() {
	}

	static final int[] FALLBACK = rgb(0x8a9a72);
	private static final Map<String, int[]> LAND = new HashMap<>();

	private static void land(int colour, String... ids) {
		for (String id : ids) LAND.put("minecraft:" + id, rgb(colour));
	}

	static {
		land(0x8db360, "plains");
		land(0x9cbd5c, "sunflower_plains");
		land(0x97c26c, "meadow");
		land(0x5a8a3c, "forest");
		land(0x7aa04e, "dappled_forest");
		land(0x6b9a42, "flower_forest");
		land(0x6f9e50, "birch_forest");
		land(0x63924a, "old_growth_birch_forest");
		land(0x3f6129, "dark_forest");
		land(0x8c9688, "pale_garden");
		land(0x557a4e, "taiga");
		land(0x4d6b45, "old_growth_pine_taiga");
		land(0x4a6643, "old_growth_spruce_taiga");
		land(0xb4c8bf, "snowy_taiga");
		land(0xe2eaec, "snowy_plains");
		land(0xc8e2ee, "ice_spikes");
		land(0xc9d8d0, "grove");
		land(0xdfe7ea, "snowy_slopes");
		land(0xeef4f7, "frozen_peaks");
		land(0xe4ebef, "jagged_peaks");
		land(0x9c9c94, "stony_peaks");
		land(0x7f8f75, "windswept_hills");
		land(0x8c8c88, "windswept_gravelly_hills");
		land(0x5a744c, "windswept_forest");
		land(0xa9a35c, "windswept_savanna");
		land(0xb7b061, "savanna");
		land(0xaaa45a, "savanna_plateau");
		land(0xe0d39a, "desert");
		land(0xc56c38, "badlands");
		land(0xd07a44, "eroded_badlands");
		land(0xa9783f, "wooded_badlands");
		land(0x3f8f22, "jungle");
		land(0x5e9b31, "sparse_jungle");
		land(0x5aa12d, "bamboo_jungle");
		land(0x5e6d3c, "swamp");
		land(0x52703a, "mangrove_swamp");
		land(0xa58ba8, "mushroom_fields");
		land(0xe3b4c8, "cherry_grove");
		land(0xe8dba6, "beach");
		land(0xe6e2d4, "snowy_beach");
		land(0x8f928e, "stony_shore");
		// rivers and oceans above sea level (rare) look like their banks
		land(0x8db360, "river");
		land(0xdfe7ea, "frozen_river");
		land(0xe8dba6, "ocean", "deep_ocean", "lukewarm_ocean", "deep_lukewarm_ocean", "warm_ocean", "cold_ocean", "deep_cold_ocean");
		land(0xdfe7ea, "frozen_ocean", "deep_frozen_ocean");
		// cave biomes only show up where the surface sample reaches them
		land(0x6f8f4a, "lush_caves");
		land(0x8a7f6a, "dripstone_caves");
		land(0xb3a64a, "sulfur_caves");
		land(0x3a4652, "deep_dark");
	}

	static int[] of(String id) {
		return LAND.get(id);
	}

	/** Water colour by biome temperature and depth below sea level. */
	static int water(String id, int depth) {
		int[] shallow, deep;
		String p = id.startsWith("minecraft:") ? id.substring(10) : id;
		if (p.contains("frozen")) {
			// frozen surface: icy, with a faint depth hint
			shallow = rgb(0xc4daf0);
			deep = rgb(0x9fbbe0);
		} else if (p.contains("warm_ocean") && !p.contains("lukewarm")) {
			shallow = rgb(0x52c4d4);
			deep = rgb(0x1f6f9f);
		} else if (p.contains("lukewarm")) {
			shallow = rgb(0x4aa4d2);
			deep = rgb(0x1f5a9a);
		} else if (p.contains("cold")) {
			shallow = rgb(0x4b6fc8);
			deep = rgb(0x1c3380);
		} else if (p.contains("swamp")) {
			shallow = rgb(0x5d8a7a);
			deep = rgb(0x2f514a);
		} else {
			shallow = rgb(0x4d86d6);
			deep = rgb(0x1d3f8e);
		}
		double t = Math.pow(Math.max(0, Math.min(1, (depth - 1) / 32.0)), 0.75);
		int r = (int) Math.round(shallow[0] + (deep[0] - shallow[0]) * t);
		int g = (int) Math.round(shallow[1] + (deep[1] - shallow[1]) * t);
		int b = (int) Math.round(shallow[2] + (deep[2] - shallow[2]) * t);
		return (r << 16) | (g << 8) | b;
	}

	/** Base colour times a hillshade factor, then lifted toward white for high ground. */
	static int shade(int[] c, double shade, double lift) {
		int[] o = new int[3];
		for (int k = 0; k < 3; k++) {
			double v = c[k] * shade;
			v = v + (255 - v) * lift;
			o[k] = (int) Math.max(0, Math.min(255, Math.round(v)));
		}
		return (o[0] << 16) | (o[1] << 8) | o[2];
	}

	private static int[] rgb(int c) {
		return new int[] {c >> 16 & 255, c >> 8 & 255, c & 255};
	}
}

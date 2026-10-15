package dev.vplus.locator;

import java.util.List;

/** The 16 geode families in UI order. Purple is vanilla minecraft:amethyst. */
final class Colours {
	private Colours() {
	}

	static final List<String> NAMES = List.of("white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow",
			"lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink");
	static final int PURPLE = NAMES.indexOf("purple");

	private static final String[] SUFFIXES = {"_budding_amethyst", "_amethyst_block", "_amethyst_bud", "_amethyst_cluster"};
	private static final String[] VANILLA = {"minecraft:amethyst_block", "minecraft:budding_amethyst",
			"minecraft:small_amethyst_bud", "minecraft:medium_amethyst_bud", "minecraft:large_amethyst_bud", "minecraft:amethyst_cluster"};

	/** Colour index of an amethyst-family block id, or -1. Bit 16 is set for budding blocks. */
	static int ofBlock(String id) {
		if (id.startsWith("minecraft:")) {
			for (String v : VANILLA) {
				if (v.equals(id)) return PURPLE | (id.endsWith("budding_amethyst") ? 16 : 0);
			}
			return -1;
		}
		if (!id.startsWith("vplus_geodes:")) return -1;
		String p = id.substring("vplus_geodes:".length());
		for (String pre : new String[] {"small_", "medium_", "large_"}) {
			if (p.startsWith(pre)) p = p.substring(pre.length());
		}
		for (String suf : SUFFIXES) {
			if (p.endsWith(suf)) {
				int c = NAMES.indexOf(p.substring(0, p.length() - suf.length()));
				return c < 0 ? -1 : c | (suf.equals("_budding_amethyst") ? 16 : 0);
			}
		}
		return -1;
	}

	/** "vplus_geodes:amethyst_geode/red" -> index of red. */
	static int ofFeatureId(String featureId) {
		String c = featureId.substring(featureId.lastIndexOf('/') + 1);
		int i = NAMES.indexOf(c);
		if (i < 0) throw new IllegalStateException("unknown geode colour feature " + featureId);
		return i;
	}
}

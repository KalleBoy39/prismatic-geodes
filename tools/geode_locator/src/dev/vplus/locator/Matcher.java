package dev.vplus.locator;

import java.util.ArrayList;
import java.util.List;

/** Explains found geodes by predicted attempts (shared by shadow verification and save scans). */
final class Matcher {
	private Matcher() {
	}

	enum Kind { MATCH, PREMOD_VANILLA, MISMATCH, UNEXPLAINED }

	/** One colour of one connected component. Overlapping geodes of two colours give two entries. */
	record Found(int colour, int x, int y, int z, int blocks, Kind kind, int[] attempt) {
	}

	/** Attempts whose body box could hold a geode whose centroid is at (x, z): chunks within +-2. */
	static List<int[]> attemptsNear(Predictor p, long seed, int x, int z) {
		List<int[]> out = new ArrayList<>();
		int cx = x >> 4, cz = z >> 4;
		for (int i = cx - 2; i <= cx + 2; i++) {
			for (int j = cz - 2; j <= cz + 2; j++) {
				int[] a = p.attempt(seed, i, j);
				if (a != null) out.add(a);
			}
		}
		return out;
	}

	static List<Found> explain(GeodeScan.Component c, Predictor p, long seed) {
		List<Found> out = new ArrayList<>();
		for (int k = 0; k < 16; k++) {
			if (c.count[k] < GeodeScan.MIN_GEODE_BLOCKS) continue;
			int x = (int) Math.floorDiv(c.sx[k], c.count[k]), y = (int) Math.floorDiv(c.sy[k], c.count[k]),
					z = (int) Math.floorDiv(c.sz[k], c.count[k]);
			List<int[]> near = attemptsNear(p, seed, x, z);
			// blocks covered by the union of same-colour attempt boxes (two same-colour geodes can merge)
			int sameCovered = 0, anyCovered = 0;
			int[] bestSame = null, bestAny = null;
			int[] perSame = new int[near.size()], perAny = new int[near.size()];
			for (long[] pc : c.positions) {
				if (pc[1] != k) continue;
				boolean s = false, any = false;
				for (int i = 0; i < near.size(); i++) {
					int[] a = near.get(i);
					if (in(GeodeScan.ux(pc[0]) - a[0]) && in(GeodeScan.uy(pc[0]) - a[1]) && in(GeodeScan.uz(pc[0]) - a[2])) {
						any = true;
						perAny[i]++;
						if (a[3] == k) {
							s = true;
							perSame[i]++;
						}
					}
				}
				if (s) sameCovered++;
				if (any) anyCovered++;
			}
			for (int i = 0; i < near.size(); i++) {
				if (perSame[i] > 0 && (bestSame == null || perSame[i] > perSame[near.indexOf(bestSame)])) bestSame = near.get(i);
				if (perAny[i] > 0 && (bestAny == null || perAny[i] > perAny[near.indexOf(bestAny)])) bestAny = near.get(i);
			}
			Kind kind;
			int[] att;
			if (sameCovered * 2 >= c.count[k]) {
				kind = Kind.MATCH;
				att = bestSame;
			} else if (anyCovered * 2 >= c.count[k]) {
				kind = k == Colours.PURPLE ? Kind.PREMOD_VANILLA : Kind.MISMATCH;
				att = bestAny;
			} else {
				kind = Kind.UNEXPLAINED;
				att = bestAny;
			}
			out.add(new Found(k, x, y, z, c.count[k], kind, att));
		}
		return out;
	}

	private static boolean in(int d) {
		return d >= GeodeScan.BOX_MIN && d <= GeodeScan.BOX_MAX;
	}
}

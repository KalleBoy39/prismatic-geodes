package dev.vplus.locator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/** Finds amethyst-family blocks in saved chunks (read-only) and groups them into geodes. */
final class GeodeScan {
	/** Bounding box, relative to the feature origin, that holds a geode's body (measured centre is origin + ~4.5). */
	static final int BOX_MIN = -3, BOX_MAX = 13;
	/** Blocks of the attempt's colour inside its box needed to count it as generated. Geodes average ~420. */
	static final int GENERATED_MIN_BLOCKS = 20;
	/** Connected groups smaller than this are stray buds, not geodes. */
	static final int MIN_GEODE_BLOCKS = 20;

	static long pack(int x, int y, int z) {
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
	}

	static int ux(long p) {
		return (int) (p >> 38);
	}

	static int uy(long p) {
		return (int) (p << 52 >> 52);
	}

	static int uz(long p) {
		return (int) (p << 26 >> 38);
	}

	/** Amethyst-family blocks of a set of chunks: pos -> colour | (budding ? 16 : 0). */
	static final class Blocks {
		final Long2ByteOpenHashMap map = new Long2ByteOpenHashMap();

		Blocks() {
			map.defaultReturnValue((byte) -1);
		}

		int get(int x, int y, int z) {
			return map.get(pack(x, y, z));
		}

		void addAll(Blocks o) {
			map.putAll(o.map);
		}
	}

	/** Per-chunk info gathered while scanning one region. */
	static final class RegionResult {
		final int rx, rz;
		final Blocks blocks = new Blocks();
		final boolean[] present = new boolean[1024];
		final boolean[] full = new boolean[1024];
		final long[] timestamp = new long[1024];
		int unreadable;

		RegionResult(int rx, int rz) {
			this.rx = rx;
			this.rz = rz;
		}
	}

	/** Scan one region file, optionally limited to an inclusive chunk rectangle. */
	static RegionResult scanRegion(Path regionFile, int rx, int rz, int[] rect) throws IOException {
		RegionResult res = new RegionResult(rx, rz);
		if (!Files.exists(regionFile)) return res;
		Anvil.Region region = new Anvil.Region(regionFile, rx, rz);
		for (int i = 0; i < 1024; i++) {
			int cx = rx * 32 + (i & 31), cz = rz * 32 + (i >> 5);
			if (rect != null && (cx < rect[0] || cz < rect[1] || cx > rect[2] || cz > rect[3])) continue;
			if (!region.present(i)) continue;
			res.present[i] = true;
			res.timestamp[i] = region.timestamp(i);
			try {
				Map<String, Object> chunk = region.chunk(i);
				if (chunk == null) continue;
				res.full[i] = "minecraft:full".equals(chunk.get("Status"));
				readChunk(chunk, cx, cz, res.blocks);
			} catch (IOException | RuntimeException e) {
				res.unreadable++;
			}
		}
		return res;
	}

	@SuppressWarnings("unchecked")
	private static void readChunk(Map<String, Object> chunk, int cx, int cz, Blocks out) {
		Object secs = chunk.get("sections");
		if (!(secs instanceof List<?> sections)) return;
		for (Object so : sections) {
			Map<String, Object> sec = (Map<String, Object>) so;
			Object bso = sec.get("block_states");
			if (!(bso instanceof Map<?, ?> bsm)) continue;
			Map<String, Object> bs = (Map<String, Object>) bsm;
			List<Object> palette = (List<Object>) bs.get("palette");
			if (palette == null) continue;
			int[] cols = new int[palette.size()];
			boolean any = false;
			for (int k = 0; k < cols.length; k++) {
				cols[k] = Colours.ofBlock(stateName(palette.get(k)));
				any |= cols[k] >= 0;
			}
			if (!any) continue;
			int sy = ((Number) sec.get("Y")).intValue();
			long[] data = (long[]) bs.get("data");
			if (data == null) { // whole section is one state
				for (int i = 0; i < 4096; i++) put(out, cx, cz, sy, i, cols[0]);
				continue;
			}
			int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
			int per = 64 / bits;
			long mask = (1L << bits) - 1;
			for (int i = 0; i < 4096; i++) {
				int idx = (int) ((data[i / per] >>> ((i % per) * bits)) & mask);
				if (idx < cols.length && cols[idx] >= 0) put(out, cx, cz, sy, i, cols[idx]);
			}
		}
	}

	private static void put(Blocks out, int cx, int cz, int sy, int i, int col) {
		int x = i & 15, z = (i >> 4) & 15, y = i >> 8;
		out.map.put(pack(cx * 16 + x, sy * 16 + y, cz * 16 + z), (byte) col);
	}

	@SuppressWarnings("unchecked")
	static String stateName(Object e) {
		if (e instanceof String s) return s; // 26.x shorthand for property-less states
		Map<String, Object> m = (Map<String, Object>) e;
		if (m.size() == 1 && m.containsKey("")) return stateName(m.get("")); // heterogeneous-list wrapper
		Object n = m.get("Name");
		if (n == null) n = m.get("id");
		return String.valueOf(n);
	}

	/** Blocks of one colour inside an attempt's body box. */
	static int countInBox(Blocks b, int ox, int oy, int oz, int colour) {
		int n = 0;
		for (int x = ox + BOX_MIN; x <= ox + BOX_MAX; x++) {
			for (int z = oz + BOX_MIN; z <= oz + BOX_MAX; z++) {
				for (int y = oy + BOX_MIN; y <= oy + BOX_MAX; y++) {
					int v = b.map.get(pack(x, y, z));
					if (v >= 0 && (v & 15) == colour) n++;
				}
			}
		}
		return n;
	}

	/** One 6-connected group of amethyst-family blocks, split per colour. */
	static final class Component {
		final int[] count = new int[16];
		final long[] sx = new long[16], sy = new long[16], sz = new long[16];
		final List<long[]> positions = new ArrayList<>(); // [packed, colour]
		int budding, size;
	}

	static List<Component> components(Blocks b) {
		List<Component> out = new ArrayList<>();
		LongOpenHashSet seen = new LongOpenHashSet();
		ArrayDeque<Long> stack = new ArrayDeque<>();
		for (long start : b.map.keySet()) {
			if (!seen.add(start)) continue;
			Component c = new Component();
			stack.push(start);
			while (!stack.isEmpty()) {
				long p = stack.pop();
				int v = b.map.get(p), col = v & 15;
				int x = ux(p), y = uy(p), z = uz(p);
				c.size++;
				c.count[col]++;
				c.sx[col] += x;
				c.sy[col] += y;
				c.sz[col] += z;
				if ((v & 16) != 0) c.budding++;
				c.positions.add(new long[] {p, col});
				long[] nb = {pack(x + 1, y, z), pack(x - 1, y, z), pack(x, y + 1, z), pack(x, y - 1, z), pack(x, y, z + 1), pack(x, y, z - 1)};
				for (long n : nb) {
					if (b.map.containsKey(n) && seen.add(n)) stack.push(n);
				}
			}
			if (c.size >= MIN_GEODE_BLOCKS && c.budding > 0) out.add(c);
		}
		return out;
	}
}

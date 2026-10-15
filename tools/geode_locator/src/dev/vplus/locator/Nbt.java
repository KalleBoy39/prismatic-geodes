package dev.vplus.locator;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Minimal read-only NBT decoder. Compounds become Maps, lists Lists, arrays primitive arrays. */
final class Nbt {
	private Nbt() {
	}

	static Object read(byte[] data) throws IOException {
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
		int type = in.readByte();
		in.skipNBytes(in.readUnsignedShort());
		return payload(in, type);
	}

	private static Object payload(DataInputStream in, int type) throws IOException {
		switch (type) {
			case 1: return in.readByte();
			case 2: return in.readShort();
			case 3: return in.readInt();
			case 4: return in.readLong();
			case 5: return in.readFloat();
			case 6: return in.readDouble();
			case 7: {
				byte[] b = new byte[in.readInt()];
				in.readFully(b);
				return b;
			}
			case 8: return string(in);
			case 9: {
				int et = in.readByte(), n = in.readInt();
				List<Object> l = new ArrayList<>(Math.max(0, n));
				for (int i = 0; i < n; i++) l.add(payload(in, et));
				return l;
			}
			case 10: {
				Map<String, Object> m = new HashMap<>();
				while (true) {
					int ct = in.readByte();
					if (ct == 0) return m;
					String name = string(in);
					m.put(name, payload(in, ct));
				}
			}
			case 11: {
				int[] a = new int[in.readInt()];
				for (int i = 0; i < a.length; i++) a[i] = in.readInt();
				return a;
			}
			case 12: {
				long[] a = new long[in.readInt()];
				for (int i = 0; i < a.length; i++) a[i] = in.readLong();
				return a;
			}
			default: throw new IOException("bad NBT tag " + type);
		}
	}

	private static String string(DataInputStream in) throws IOException {
		byte[] b = new byte[in.readUnsignedShort()];
		in.readFully(b);
		return new String(b, StandardCharsets.UTF_8); // modified UTF-8; block ids are plain ASCII
	}
}

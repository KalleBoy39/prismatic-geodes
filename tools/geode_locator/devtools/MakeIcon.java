import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Dev-only: draws the locator's own icon (same vector crystal cluster as the map markers, no game textures) and
 * writes web/icon.png plus a multi-size locator.ico. Usage: java MakeIcon.java <locator folder>
 */
public final class MakeIcon {
	// [topX, topY, baseX, baseY, halfWidth] in a unit box, same as SHARDS in web/app.js
	static final double[][] SHARDS = {{0.17, 0.36, 0.36, 0.97, 0.13}, {0.85, 0.42, 0.64, 0.97, 0.12}, {0.5, 0.03, 0.5, 0.98, 0.17}};
	// left shard blue, right magenta, centre purple (Prismatic Geodes palettes: fill, dark, light)
	static final String[][] PAL = {{"#4c63d3", "#021661", "#819bfe"}, {"#b34c9e", "#4d054a", "#e583ce"}, {"#9a5cc6", "#361c6a", "#c98ff3"}};

	public static void main(String[] a) throws Exception {
		Path root = Path.of(a[0]);
		int[] sizes = {16, 24, 32, 48, 64, 128, 256};
		List<byte[]> pngs = new ArrayList<>();
		for (int s : sizes) {
			BufferedImage img = draw(s);
			ByteArrayOutputStream o = new ByteArrayOutputStream();
			ImageIO.write(img, "png", o);
			pngs.add(o.toByteArray());
			if (s == 256) Files.write(root.resolve("web").resolve("icon.png"), o.toByteArray());
		}
		// ICO with embedded PNGs (supported since Windows Vista)
		ByteArrayOutputStream ico = new ByteArrayOutputStream();
		ByteBuffer h = ByteBuffer.allocate(6 + 16 * sizes.length).order(ByteOrder.LITTLE_ENDIAN);
		h.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
		int off = 6 + 16 * sizes.length;
		for (int i = 0; i < sizes.length; i++) {
			h.put((byte) (sizes[i] == 256 ? 0 : sizes[i])).put((byte) (sizes[i] == 256 ? 0 : sizes[i])).put((byte) 0).put((byte) 0);
			h.putShort((short) 1).putShort((short) 32).putInt(pngs.get(i).length).putInt(off);
			off += pngs.get(i).length;
		}
		ico.write(h.array());
		for (byte[] p : pngs) ico.write(p);
		Files.write(root.resolve("locator.ico"), ico.toByteArray());
		System.out.println("wrote web/icon.png and locator.ico");
	}

	static BufferedImage draw(int px) {
		BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		double pad = px * 0.08, size = px - 2 * pad;
		Path2D[][] polys = new Path2D[3][];
		for (int i = 0; i < 3; i++) polys[i] = shard(SHARDS[i], pad, pad, size);
		g.setStroke(new BasicStroke((float) Math.max(1.2, size * 0.09), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(new Color(10, 10, 14, 200));
		for (Path2D[] p : polys) g.draw(p[0]);
		for (int i = 0; i < 3; i++) {
			g.setColor(Color.decode(PAL[i][2]));
			g.fill(polys[i][1]);
			g.setColor(Color.decode(PAL[i][0]));
			g.fill(polys[i][2]);
			g.setColor(Color.decode(PAL[i][1]));
			g.setStroke(new BasicStroke((float) Math.max(0.8, size * 0.035), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.draw(polys[i][0]);
		}
		g.dispose();
		return img;
	}

	/** {outline, left facet, right facet} of one shard. */
	static Path2D[] shard(double[] s, double ox, double oy, double size) {
		double tx = s[0] * size, ty = s[1] * size, bx = s[2] * size, by = s[3] * size, hw = s[4] * size;
		double dx = bx - tx, dy = by - ty, len = Math.hypot(dx, dy), px = -dy / len, py = dx / len;
		double sx = tx + dx * 0.24, sy = ty + dy * 0.24;
		double[] T = {ox + tx, oy + ty}, S = {ox + sx, oy + sy}, B = {ox + bx, oy + by};
		double[] SL = {ox + sx + px * hw, oy + sy + py * hw}, SR = {ox + sx - px * hw, oy + sy - py * hw};
		double[] BL = {ox + bx + px * hw * 0.8, oy + by + py * hw * 0.8}, BR = {ox + bx - px * hw * 0.8, oy + by - py * hw * 0.8};
		return new Path2D[] {poly(T, SL, BL, BR, SR), poly(T, SL, BL, B, S), poly(T, S, B, BR, SR)};
	}

	static Path2D poly(double[]... pts) {
		Path2D p = new Path2D.Double();
		p.moveTo(pts[0][0], pts[0][1]);
		for (int i = 1; i < pts.length; i++) p.lineTo(pts[i][0], pts[i][1]);
		p.closePath();
		return p;
	}
}

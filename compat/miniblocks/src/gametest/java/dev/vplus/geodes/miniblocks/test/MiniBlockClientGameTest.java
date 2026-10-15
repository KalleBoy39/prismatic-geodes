package dev.vplus.geodes.miniblocks.test;

import java.util.List;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

/**
 * Real client in a throwaway world. Every shot puts Vanilla Tweaks' own Mini Amethyst Block (purple, skin from
 * textures.minecraft.net) next to the addon's mini blocks, on grass and from about the angle of a player looking
 * down at them in a survival world:
 *   miniblocks_vt_vs_<colour>   close pair, VT purple left, ours right (pink, red, cyan, green, white, black)
 *   miniblocks_family           all 16 in a 4x4 grid (VT purple first)
 *   miniblocks_hotbar_items     the mini blocks as items
 * Screenshots land in build/run/clientGameTest/screenshots.
 */
public class MiniBlockClientGameTest implements FabricClientGameTest {
	// the exact profile Vanilla Tweaks Mini Blocks v1.1.8 gives its Mini Amethyst Block
	private static final String VT_AMETHYST = "{properties:[{name:\"textures\",value:\"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzEzYjc3ZjVhNzQ2YzFmMDBkZDFjZTdmZTY2OTc2Yzc1Y2VjZjdkODliZGIwMmU4Y2Y5NDM2NjcyYWY1ODk2ZCJ9fX0=\"}]}";
	private static final List<String> PAIRS = List.of("pink", "red", "cyan", "green", "white", "black");

	private static String at(int x, int y, int z) {
		return x + " " + y + " " + z;
	}

	private static String ours(String colour) {
		return "minecraft:player_head[rotation=0]{profile:{texture:\"" + MiniBlockGameTests.NS + ":entity/mini_block/"
				+ colour + "_amethyst_block\"}}";
	}

	private static String vt() {
		return "minecraft:player_head[rotation=0]{profile:" + VT_AMETHYST + "}";
	}

	private static void shot(ClientGameTestContext context, TestSingleplayerContext sp, TestServerContext server, String tp, String name) {
		server.runCommand("tp @a " + tp);
		sp.getConnection().waitForChunksRender();
		context.waitTicks(60); // let skin textures (and the VT reference download) arrive
		context.takeScreenshot(name);
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			TestServerContext server = sp.getServer();
			context.runOnClient(mc -> {
				mc.options.pauseOnLostFocus = false;
				if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
			});
			server.runCommand("gamemode spectator @a");
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			BlockPos spawn = server.computeOnServer(s -> sp.getConnection().getServerPlayer().blockPosition());
			int x0 = spawn.getX() - 20, y = spawn.getY() + 12, z0 = spawn.getZ() + 4;
			server.runCommand("fill " + at(x0 - 4, y - 1, z0 - 4) + " " + at(x0 + 40, y - 1, z0 + 30) + " minecraft:grass_block");

			// close pairs; yaw 0 looks south (+z), so larger x is on the left of the screen: VT at x+2, ours at x
			for (int i = 0; i < PAIRS.size(); i++) {
				int x = x0 + i * 6, z = z0;
				server.runCommand("setblock " + at(x + 2, y, z) + " " + vt());
				server.runCommand("setblock " + at(x, y, z) + " " + ours(PAIRS.get(i)));
			}
			// the whole family as a 4x4 grid read left to right, top row first: VT purple, then ours
			List<String> row = MiniBlockGameTests.COLOURS;
			int xg = x0, zf = z0 + 12;
			for (int k = 0; k < 16; k++) {
				int x = xg + 3 - k % 4, z = zf + k / 4;
				server.runCommand("setblock " + at(x, y, z) + " " + (k == 0 ? vt() : ours(row.get(k - 1))));
			}

			for (int i = 0; i < PAIRS.size(); i++) {
				int x = x0 + i * 6;
				// tp sets the feet; the eye is 1.62 higher, so this puts the eye ~1.1 blocks above and ~1.1 in front of the heads
				shot(context, sp, server, (x + 1.5) + " " + (y - 0.5) + " " + (z0 - 0.85) + " 0 38", "miniblocks_vt_vs_" + PAIRS.get(i));
			}
			shot(context, sp, server, (xg + 2.0) + " " + (y + 0.6) + " " + (zf - 1.6) + " 0 50", "miniblocks_family");

			// items in the hotbar
			server.runCommand("gamemode creative @a");
			server.runCommand("clear @a");
			for (int i = 0; i < 9; i++) {
				String c = row.get(i);
				server.runCommand("item replace entity @a hotbar." + i + " with minecraft:player_head[profile={texture:\""
						+ MiniBlockGameTests.NS + ":entity/mini_block/" + c + "_amethyst_block\"},item_name=\"Mini "
						+ MiniBlockGameTests.title(c) + " Amethyst Block\"] 8");
			}
			context.runOnClient(mc -> {
				if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
			});
			server.runCommand("tp @a " + (xg + 2.0) + " " + y + " " + (zf - 2.5) + " 0 45");
			context.waitTicks(40);
			context.takeScreenshot("miniblocks_hotbar_items");
		}
	}
}

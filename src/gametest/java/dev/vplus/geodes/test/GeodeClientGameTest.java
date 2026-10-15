package dev.vplus.geodes.test;

import java.util.List;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

/**
 * Real client launch in a throwaway singleplayer world: builds a showcase of all 16 families
 * (vanilla purple included) next to spawn and screenshots it, so models, textures, rotations and
 * waterlogging can be checked by eye. Screenshots land in build/run/clientGameTest/screenshots.
 */
public class GeodeClientGameTest implements FabricClientGameTest {
	private static final List<String> COLOURS = List.of("white", "light_gray", "gray", "black", "brown", "red", "orange",
			"yellow", "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink");

	private static String id(String colour, String vanilla) {
		if (colour.equals("purple")) {
			return "minecraft:" + vanilla;
		}
		return switch (vanilla) {
			case "amethyst_block" -> "vplus_geodes:" + colour + "_amethyst_block";
			case "budding_amethyst" -> "vplus_geodes:" + colour + "_budding_amethyst";
			case "amethyst_cluster" -> "vplus_geodes:" + colour + "_amethyst_cluster";
			default -> "vplus_geodes:" + vanilla.replace("_amethyst_bud", "_" + colour + "_amethyst_bud");
		};
	}

	private static String at(int x, int y, int z) {
		return x + " " + y + " " + z;
	}

	private static void shot(ClientGameTestContext context, TestSingleplayerContext sp, TestServerContext server,
			String tp, String name) {
		server.runCommand("tp @a " + tp);
		sp.getConnection().waitForChunksRender();
		context.waitTicks(30);
		context.takeScreenshot(name);
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			TestServerContext server = sp.getServer();
			context.runOnClient(mc -> {
				mc.options.pauseOnLostFocus = false;
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			server.runCommand("gamemode spectator @a"); // no falling, no hand in view
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			BlockPos spawn = server.computeOnServer(s -> sp.getConnection().getServerPlayer().blockPosition());
			int x0 = spawn.getX() - 16, y = spawn.getY() + 12, z0 = spawn.getZ() + 4;

			server.runCommand("fill " + at(x0 - 2, y - 1, z0 - 2) + " " + at(x0 + 32, y - 1, z0 + 20) + " minecraft:calcite");
			// sealed water trough for the waterlogged row (z0+13, one block above the floor)
			server.runCommand("fill " + at(x0 - 2, y, z0 + 12) + " " + at(x0 + 32, y + 1, z0 + 14) + " minecraft:glass");
			server.runCommand("fill " + at(x0 - 1, y + 1, z0 + 13) + " " + at(x0 + 31, y + 1, z0 + 13) + " minecraft:water");
			server.runCommand("fill " + at(x0 - 1, y, z0 + 13) + " " + at(x0 + 31, y, z0 + 13) + " minecraft:calcite");
			for (int i = 0; i < COLOURS.size(); i++) {
				String c = COLOURS.get(i);
				int x = x0 + i * 2;
				server.runCommand("setblock " + at(x, y, z0) + " " + id(c, "amethyst_block"));
				server.runCommand("setblock " + at(x, y, z0 + 2) + " " + id(c, "budding_amethyst"));
				server.runCommand("setblock " + at(x, y, z0 + 4) + " " + id(c, "small_amethyst_bud") + "[facing=up]");
				server.runCommand("setblock " + at(x, y, z0 + 6) + " " + id(c, "medium_amethyst_bud") + "[facing=up]");
				server.runCommand("setblock " + at(x, y, z0 + 8) + " " + id(c, "large_amethyst_bud") + "[facing=up]");
				server.runCommand("setblock " + at(x, y, z0 + 10) + " " + id(c, "amethyst_cluster") + "[facing=up]");
				server.runCommand("setblock " + at(x, y, z0 + 13) + " " + id(c, "amethyst_block"));
				server.runCommand("setblock " + at(x, y + 1, z0 + 13) + " " + id(c, "amethyst_cluster")
						+ "[facing=up,waterlogged=true]");
				// sideways: cluster pointing south out of a block
				server.runCommand("setblock " + at(x, y, z0 + 17) + " " + id(c, "amethyst_block"));
				server.runCommand("setblock " + at(x, y, z0 + 18) + " " + id(c, "amethyst_cluster") + "[facing=south]");
			}
			// all six faces of one red block, floating
			int cx = x0 + 16, cy = y + 6, cz = z0 + 26;
			server.runCommand("setblock " + at(cx, cy, cz) + " vplus_geodes:red_amethyst_block");
			int[][] off = {{0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
			String[] dir = {"up", "down", "east", "west", "south", "north"};
			for (int k = 0; k < 6; k++) {
				server.runCommand("setblock " + at(cx + off[k][0], cy + off[k][1], cz + off[k][2])
						+ " vplus_geodes:red_amethyst_cluster[facing=" + dir[k] + "]");
			}

			// yaw 0 = looking south (+z), pitch > 0 = looking down
			shot(context, sp, server, (x0 + 15.5) + " " + (y + 11) + " " + (z0 - 7) + " 0 48", "vplus_geodes_all_families");
			shot(context, sp, server, (x0 + 4.5) + " " + (y + 2.5) + " " + (z0 + 7) + " -20 18", "vplus_geodes_rows_close");
			shot(context, sp, server, (x0 + 6.5) + " " + (y + 2.2) + " " + (z0 + 22) + " 160 22",
					"vplus_geodes_sideways_and_waterlogged");
			shot(context, sp, server, (cx + 3.5) + " " + (cy + 2.5) + " " + (cz - 3.5) + " 45 30", "vplus_geodes_six_faces_a");
			shot(context, sp, server, (cx - 2.5) + " " + (cy - 1.5) + " " + (cz + 3.5) + " 215 -25", "vplus_geodes_six_faces_b");
		}
	}
}

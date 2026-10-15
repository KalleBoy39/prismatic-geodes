package dev.vplus.geodes.test;

import java.util.List;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;

/**
 * Visual check of the coloured amethyst trims on armor stands in a throwaway world.
 * Row A: all 16 amethyst colours (purple = vanilla) with the same pattern on netherite; row B the same on iron;
 * then a contrast grid of the separation groups on iron / gold / diamond / netherite.
 */
public class GeodeTrimClientGameTest implements FabricClientGameTest {
	private static final List<String> ALL = List.of("white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow",
			"lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink");
	private static final List<String> GROUPS = List.of("white", "light_gray", "gray", "black", "yellow", "lime", "green",
			"light_blue", "cyan", "blue", "brown", "orange", "red");
	private static final String PATTERN = "minecraft:silence";

	private static String material(String c) {
		return c.equals("purple") ? "minecraft:amethyst" : "vplus_geodes:" + c + "_amethyst";
	}

	private static String piece(String armor, String part, String c) {
		return "{id:\"minecraft:" + armor + "_" + part + "\",components:{\"minecraft:trim\":{material:\"" + material(c)
				+ "\",pattern:\"" + PATTERN + "\"}}}";
	}

	private static void stand(TestServerContext server, double x, int y, double z, String armor, String c) {
		server.runCommand("summon minecraft:armor_stand " + x + " " + y + " " + z + " {Rotation:[180f,0f],NoGravity:1b,equipment:{"
				+ "head:" + piece(armor, "helmet", c) + ",chest:" + piece(armor, "chestplate", c) + ",legs:" + piece(armor, "leggings", c)
				+ ",feet:" + piece(armor, "boots", c) + "}}");
	}

	private static void shot(ClientGameTestContext context, TestSingleplayerContext sp, TestServerContext server, String tp, String name) {
		server.runCommand("tp @a " + tp);
		sp.getConnection().waitForChunksRender();
		context.waitTicks(40);
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
			server.runCommand("gamemode spectator @a");
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			BlockPos spawn = server.computeOnServer(s -> sp.getConnection().getServerPlayer().blockPosition());
			int x0 = spawn.getX() - 16, y = spawn.getY() + 12, z0 = spawn.getZ() + 6;
			server.runCommand("fill " + (x0 - 2) + " " + (y - 1) + " " + (z0 - 2) + " " + (x0 + 34) + " " + (y - 1) + " " + (z0 + 30)
					+ " minecraft:smooth_stone");
			for (int i = 0; i < ALL.size(); i++) {
				stand(server, x0 + i * 2 + 0.5, y, z0 + 0.5, "netherite", ALL.get(i));
				stand(server, x0 + i * 2 + 0.5, y, z0 + 4.5, "iron", ALL.get(i));
			}
			String[] armors = {"iron", "golden", "diamond", "netherite"};
			for (int a = 0; a < armors.length; a++) {
				for (int i = 0; i < GROUPS.size(); i++) {
					stand(server, x0 + 3 + i * 2 + 0.5, y, z0 + 12 + a * 4 + 0.5, armors[a], GROUPS.get(i));
				}
			}
			context.waitTicks(20);
			// stands face north (-z); camera stands north of each row looking south (yaw 0)
			shot(context, sp, server, (x0 + 8.5) + " " + (y + 1.6) + " " + (z0 - 7) + " 0 6", "trims_A_netherite_all16_left");
			shot(context, sp, server, (x0 + 24.5) + " " + (y + 1.6) + " " + (z0 - 7) + " 0 6", "trims_A_netherite_all16_right");
			shot(context, sp, server, (x0 + 15.5) + " " + (y + 2.5) + " " + (z0 - 13) + " 0 10", "trims_A_netherite_all16_full");
			for (int i = 0; i < ALL.size(); i++) {
				server.runCommand("kill @e[type=armor_stand,x=" + (x0 + i * 2 + 0.5) + ",y=" + y + ",z=" + (z0 + 0.5) + ",distance=..0.6]");
			}
			shot(context, sp, server, (x0 + 8.5) + " " + (y + 1.6) + " " + (z0 - 3) + " 0 6", "trims_B_iron_all16_left");
			shot(context, sp, server, (x0 + 24.5) + " " + (y + 1.6) + " " + (z0 - 3) + " 0 6", "trims_B_iron_all16_right");
			for (int i = 0; i < ALL.size(); i++) {
				server.runCommand("kill @e[type=armor_stand,x=" + (x0 + i * 2 + 0.5) + ",y=" + y + ",z=" + (z0 + 4.5) + ",distance=..0.6]");
			}
			shot(context, sp, server, (x0 + 16.5) + " " + (y + 9) + " " + (z0 + 2) + " 0 32", "trims_C_contrast_grid_iron_gold_diamond_netherite");
			shot(context, sp, server, (x0 + 10.5) + " " + (y + 1.8) + " " + (z0 + 8) + " 0 8", "trims_C_iron_row_close");
			shot(context, sp, server, (x0 + 22.5) + " " + (y + 1.8) + " " + (z0 + 8) + " 0 8", "trims_C_iron_row_close_right");
		}
	}
}

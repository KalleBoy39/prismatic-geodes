package dev.vplus.locator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Disposable Fabric server (Minecraft 26.3, Fabric API and the Prismatic Geodes jar, plus the read-only probe)
 * that generates chunks for "Verify visible area". It only ever writes inside its own folder
 * (one world_<seed> folder per seed) and listens on 127.0.0.1 only.
 */
final class ShadowServer {
	final Path dir;
	private final String java;
	private final int port;
	private final String memory;
	private Process process;
	private Writer stdin;
	private long seed;
	private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
	private volatile long lastUse;

	ShadowServer(Path dir, String java, int port, String memory) {
		this.dir = dir;
		this.java = java;
		this.port = port;
		this.memory = memory;
	}

	static String worldName(long seed) {
		return "world_" + (seed < 0 ? "m" + Long.toString(seed).substring(1) : Long.toString(seed));
	}

	Path world(long seed) {
		return dir.resolve(worldName(seed));
	}

	synchronized boolean runningFor(long s) {
		return process != null && process.isAlive() && seed == s;
	}

	long idleMillis() {
		return System.currentTimeMillis() - lastUse;
	}

	synchronized boolean alive() {
		return process != null && process.isAlive();
	}

	/** The configured port, or the next free one (another locator or a local Minecraft server may hold it). */
	private int freePort() {
		for (int p = port; p < port + 50; p++) {
			try (ServerSocket s = new ServerSocket(p, 1, InetAddress.getLoopbackAddress())) {
				return p;
			} catch (IOException e) {
				// taken
			}
		}
		return port;
	}

	/** Start (or restart for another seed) and wait until the server is ready. */
	synchronized void ensure(long s, Consumer<String> progress) throws IOException, InterruptedException {
		lastUse = System.currentTimeMillis();
		if (runningFor(s)) return;
		stop();
		Files.writeString(dir.resolve("eula.txt"), "eula=true\n"); // the user accepted the Minecraft EULA before setup
		List<String> props = List.of(
				"level-name=" + worldName(s), "level-seed=" + s, "level-type=minecraft\\:normal", "generate-structures=true",
				"online-mode=false", "server-ip=127.0.0.1", "server-port=" + freePort(), "enable-query=false", "enable-rcon=false",
				"spawn-protection=0", "view-distance=3", "simulation-distance=3", "max-tick-time=-1", "sync-chunk-writes=true",
				"pause-when-empty-seconds=-1", "max-players=1", "motd=Prismatic Geode Locator verification server");
		Files.write(dir.resolve("server.properties"), props, StandardCharsets.UTF_8);
		Files.deleteIfExists(dir.resolve("locator_probe.json"));
		progress.accept("starting shadow server for seed " + s);
		ProcessBuilder pb = new ProcessBuilder(java, "-Xmx" + memory, "-jar", "fabric-server-launch.jar", "nogui")
				.directory(dir.toFile()).redirectErrorStream(true);
		process = pb.start();
		seed = s;
		lines.clear();
		stdin = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
		Process p = process;
		Path log = dir.resolve("locator-shadow.log");
		Thread pump = new Thread(() -> {
			try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
					PrintWriter w = new PrintWriter(Files.newBufferedWriter(log, StandardCharsets.UTF_8))) {
				String l;
				while ((l = r.readLine()) != null) {
					w.println(l);
					w.flush();
					lines.add(l);
				}
			} catch (IOException ignored) {
				// process ended
			}
		}, "shadow-server-pump");
		pump.setDaemon(true);
		pump.start();
		waitFor("Done (", 600);
		progress.accept("shadow server ready");
	}

	private void send(String cmd) throws IOException {
		stdin.write(cmd + "\n");
		stdin.flush();
	}

	private List<String> waitFor(String text, int timeoutSeconds) throws IOException, InterruptedException {
		long end = System.currentTimeMillis() + timeoutSeconds * 1000L;
		List<String> got = new ArrayList<>();
		while (System.currentTimeMillis() < end) {
			String l = lines.poll(1, TimeUnit.SECONDS);
			if (l == null) {
				if (!process.isAlive()) throw new IOException("shadow server exited, see " + dir.resolve("locator-shadow.log"));
				continue;
			}
			got.add(l);
			if (l.contains(text)) return got;
		}
		throw new IOException("timeout waiting for '" + text + "' from shadow server");
	}

	private int pollId;

	/** Fully generate every chunk of the inclusive rectangle (16x16 forceload batches), then flush to disk. */
	synchronized void generate(int x0, int z0, int x1, int z1, Consumer<String> progress) throws IOException, InterruptedException {
		lastUse = System.currentTimeMillis();
		int total = (x1 - x0 + 1) * (z1 - z0 + 1), done = 0;
		for (int bx = x0; bx <= x1; bx += 16) {
			for (int bz = z0; bz <= z1; bz += 16) {
				int ex = Math.min(bx + 15, x1), ez = Math.min(bz + 15, z1);
				int n = (ex - bx + 1) * (ez - bz + 1);
				send("forceload add " + bx * 16 + " " + bz * 16 + " " + (ex * 16 + 15) + " " + (ez * 16 + 15));
				while (true) {
					Thread.sleep(400);
					pollId++;
					for (int cx = bx; cx <= ex; cx++) {
						for (int cz = bz; cz <= ez; cz++) send("execute if loaded " + cx * 16 + " 0 " + cz * 16);
					}
					send("say POLL_END_" + pollId);
					long ok = waitFor("POLL_END_" + pollId, 600).stream().filter(l -> l.contains("Test passed")).count();
					if (ok >= n) break;
				}
				send("forceload remove all");
				done += n;
				progress.accept("generated " + done + " / " + total + " chunks");
				lastUse = System.currentTimeMillis();
			}
		}
		send("save-all flush");
		waitFor("Saved the game", 600);
	}

	synchronized void stop() {
		if (process == null) return;
		try {
			if (process.isAlive()) {
				send("stop");
				if (!process.waitFor(120, TimeUnit.SECONDS)) process.destroy();
			}
		} catch (IOException | InterruptedException e) {
			process.destroy();
		}
		process = null;
	}
}

package dev.vplus.locator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import net.minecraft.world.level.levelgen.WorldOptions;

/**
 * Local web backend of the Prismatic Geode Locator. Binds to 127.0.0.1 only.
 *
 * The public build is started by the launcher ({@link #start(Settings)}) after first-run setup; the private dev build
 * runs {@link #main(String[])} from the source folder with its own locator.properties.
 */
public final class Main {
	static final long MAX_PREDICT_CHUNKS = 1_500_000;
	static final String VERSION = "1.0.0";

	/**
	 * Everything the backend needs to know about where things are. No user- or machine-specific defaults.
	 *
	 * @param web       folder with index.html, app.js, style.css
	 * @param data      writable folder for caches (terrain tiles)
	 * @param modJar    the Prismatic Geodes jar whose geode selector is predicted (also loaded by the shadow server)
	 * @param mcJar     the extracted Minecraft 26.3 server jar (vanilla placement data)
	 * @param shadowDir folder of the disposable verification server
	 */
	public record Settings(Path web, Path data, Path modJar, Path mcJar, Path shadowDir, int port, int shadowPort,
			int idleMinutes, String shadowMemory, String edition) {
	}

	/** What extensions may use. */
	record Context(Predictor predictor, Settings settings) {
	}

	private final Settings settings;
	private final AtomicReference<Predictor> predictor = new AtomicReference<>();
	private final ShadowServer shadow;
	private final Verifier verifier;
	private final Terrain terrain;
	private final List<Extension> extensions = new ArrayList<>();
	private final Path probeJson;

	private final Object jobLock = new Object();
	private volatile String jobMessage = "idle", jobError;
	private volatile boolean jobRunning;
	private volatile long jobSeed;
	private volatile int[] jobRect;
	private volatile int[] jobProgress;

	private Main(Settings s) throws IOException {
		this.settings = s;
		probeJson = s.shadowDir().resolve("locator_probe.json");
		predictor.set(new Predictor(s.modJar(), s.mcJar(), probeJson));
		String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
		shadow = new ShadowServer(s.shadowDir(), java, s.shadowPort(), s.shadowMemory());
		verifier = new Verifier(shadow, predictor::get);
		terrain = new Terrain(s.data().resolve("cache"));
	}

	/** Starts the backend and returns the port it listens on (the configured one or the next free one). */
	public static int start(Settings s) throws IOException {
		return start(s, List.of());
	}

	/**
	 * Public build, called by the launcher once setup is done. {@code app} is the program folder (web files, the
	 * Prismatic Geodes jar), {@code data} the writable data folder (downloaded Minecraft files, caches, test worlds).
	 */
	public static int startPublic(Path app, Path data) throws IOException {
		Path modJar;
		try (var files = Files.list(app.resolve("lib"))) {
			modJar = files.filter(p -> p.getFileName().toString().matches("prismatic-geodes-[0-9.]+\\+26\\.3\\.jar")).findFirst()
					.orElseThrow(() -> new IOException("Prismatic Geodes jar missing from " + app.resolve("lib")));
		}
		Settings s = new Settings(app.resolve("web"), data, modJar,
				data.resolve("minecraft").resolve("versions").resolve("26.3").resolve("server-26.3.jar"), data.resolve("shadow-server"),
				8765, 25590, 10, "3G", "public");
		return start(s);
	}

	static int start(Settings s, List<Extension> extensions) throws IOException {
		Main m = new Main(s);
		m.extensions.addAll(extensions);
		return m.serve();
	}

	/** Private dev build: {@code Main <locator folder>}, settings from locator.properties in that folder. */
	public static void main(String[] args) throws Exception {
		Path root = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
		Properties cfg = new Properties();
		try (InputStream in = Files.newInputStream(root.resolve("locator.properties"))) {
			cfg.load(in);
		}
		Path runtime = root.resolve("runtime");
		Settings s = new Settings(root.resolve("web"), runtime, root.resolve(cfg.getProperty("mod.jar")).normalize(),
				runtime.resolve("backend-lib").resolve("server-26.3.jar"), runtime.resolve("shadow-server"),
				Integer.parseInt(cfg.getProperty("port", "8765")), Integer.parseInt(cfg.getProperty("shadow.port", "25590")),
				Integer.parseInt(cfg.getProperty("shadow.idle.minutes", "15")), cfg.getProperty("shadow.memory", "4G"), "dev");
		List<Extension> ext = new ArrayList<>();
		for (String name : cfg.getProperty("extensions", "").split(",")) {
			if (name.isBlank()) continue;
			Class<?> c = Class.forName(name.trim());
			ext.add((Extension) c.getDeclaredConstructor(Path.class, Properties.class).newInstance(root, cfg));
		}
		start(s, ext);
	}

	private int serve() throws IOException {
		HttpServer http = null;
		int port = settings.port();
		for (int p = port; p < settings.port() + 20 && http == null; p++) {
			try {
				http = HttpServer.create(new InetSocketAddress("127.0.0.1", p), 0);
				port = p;
			} catch (BindException e) {
				// taken (another copy of the locator, or another program): try the next one
			}
		}
		if (http == null) throw new IOException("no free port in " + settings.port() + ".." + (settings.port() + 19));
		http.setExecutor(Executors.newFixedThreadPool(16, r -> {
			Thread t = new Thread(r, "http");
			t.setDaemon(true);
			return t;
		}));
		http.createContext("/", this::handle);
		http.start();
		terrain.startLoading();
		Runtime.getRuntime().addShutdownHook(new Thread(shadow::stop));
		int idleMinutes = settings.idleMinutes();
		Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "shadow-idle");
			t.setDaemon(true);
			return t;
		}).scheduleAtFixedRate(() -> {
			if (!jobRunning && shadow.alive() && shadow.idleMillis() > idleMinutes * 60_000L) shadow.stop();
		}, 1, 1, TimeUnit.MINUTES);
		System.out.println("Prismatic Geode Locator: http://127.0.0.1:" + port + "/");
		Predictor p = predictor.get();
		System.out.println("feature step " + p.step + " index " + p.index + " (" + p.indexSource + ")");
		return port;
	}

	private void handle(HttpExchange ex) throws IOException {
		try {
			String path = ex.getRequestURI().getPath();
			Map<String, String> q = query(ex.getRequestURI().getRawQuery());
			if (path.equals("/api/tile")) {
				tile(ex, q);
			} else if (path.startsWith("/api/")) {
				json(ex, 200, api(path, q, ex.getRequestMethod()));
			} else if (path.startsWith("/ext/")) {
				extFile(ex, path.substring(5));
			} else {
				file(ex, settings.web(), path.equals("/") ? "index.html" : path.substring(1));
			}
		} catch (IllegalArgumentException e) {
			JsonObject o = new JsonObject();
			o.addProperty("error", e.getMessage());
			json(ex, 400, o);
		} catch (Exception e) {
			e.printStackTrace();
			JsonObject o = new JsonObject();
			o.addProperty("error", e.getMessage() == null ? e.toString() : e.getMessage());
			json(ex, 500, o);
		} finally {
			ex.close();
		}
	}

	private JsonObject api(String path, Map<String, String> q, String method) throws Exception {
		switch (path) {
			case "/api/info": return info();
			case "/api/seed": return seed(q.getOrDefault("text", ""));
			case "/api/predict": return predict(q);
			case "/api/verified": return verified(q);
			case "/api/verify": {
				if (!"POST".equals(method)) throw new IllegalArgumentException("POST required");
				return startVerify(q);
			}
			case "/api/verify/status": return status();
			case "/api/terrain": return terrainStatus();
			case "/api/probe": return probe(q);
			default:
				for (Extension e : extensions) {
					JsonObject o = e.api(path, q, method, new Context(predictor.get(), settings));
					if (o != null) return o;
				}
				throw new IllegalArgumentException("unknown endpoint " + path);
		}
	}

	private JsonObject info() throws Exception {
		Predictor p = predictor.get();
		JsonObject o = new JsonObject();
		o.addProperty("version", VERSION);
		o.addProperty("edition", settings.edition());
		o.addProperty("minecraft", "26.3");
		o.addProperty("mod_jar", settings.modJar().getFileName().toString());
		o.addProperty("step", p.step);
		o.addProperty("index", p.index);
		o.addProperty("index_source", p.indexSource);
		o.addProperty("rarity_chance", p.chance);
		o.addProperty("min_y", p.minY);
		o.addProperty("max_y", p.maxY);
		JsonArray names = new JsonArray();
		Colours.NAMES.forEach(names::add);
		o.add("colours", names);
		JsonArray w = new JsonArray();
		p.warnings.forEach(w::add);
		o.add("warnings", w);
		o.addProperty("max_verify_side", Verifier.MAX_SIDE);
		o.addProperty("data_dir", settings.data().toString());
		o.add("terrain", terrainStatus());
		JsonArray ext = new JsonArray();
		for (Extension e : extensions) {
			ext.add(e.id());
			e.info(o);
		}
		o.add("extensions", ext);
		return o;
	}

	private JsonObject terrainStatus() {
		JsonObject o = new JsonObject();
		o.addProperty("ready", terrain.ready());
		o.addProperty("status", terrain.status());
		o.addProperty("tile", Terrain.TILE);
		o.addProperty("max_level", Terrain.MAX_LEVEL);
		JsonArray u = new JsonArray();
		terrain.unknownBiomes().forEach(u::add);
		o.add("unknown_biomes", u);
		return o;
	}

	private JsonObject probe(Map<String, String> q) {
		String[] r = terrain.probe(seedParam(q), Integer.parseInt(q.get("x")), Integer.parseInt(q.get("z")));
		JsonObject o = new JsonObject();
		if (r != null) {
			o.addProperty("biome", r[0]);
			o.addProperty("height", Integer.parseInt(r[1]));
		}
		return o;
	}

	private void tile(HttpExchange ex, Map<String, String> q) throws Exception {
		if (!terrain.ready()) {
			ex.getResponseHeaders().set("Retry-After", "2");
			ex.sendResponseHeaders(503, -1);
			return;
		}
		byte[] png = terrain.tile(seedParam(q), Integer.parseInt(q.get("l")), Integer.parseInt(q.get("x")), Integer.parseInt(q.get("z")));
		ex.getResponseHeaders().set("Content-Type", "image/png");
		ex.getResponseHeaders().set("Cache-Control", "max-age=86400");
		ex.sendResponseHeaders(200, png.length);
		try (OutputStream os = ex.getResponseBody()) {
			os.write(png);
		}
	}

	private static JsonObject seed(String text) {
		var parsed = WorldOptions.parseSeed(text);
		if (parsed.isEmpty()) throw new IllegalArgumentException("Enter a seed");
		JsonObject o = new JsonObject();
		o.addProperty("seed", Long.toString(parsed.getAsLong()));
		o.addProperty("text", text.trim());
		return o;
	}

	static long seedParam(Map<String, String> q) {
		String s = q.get("seed");
		if (s == null) throw new IllegalArgumentException("seed missing");
		return Long.parseLong(s);
	}

	private static int[] rect(Map<String, String> q) {
		int x0 = Integer.parseInt(q.get("x0")), z0 = Integer.parseInt(q.get("z0"));
		int x1 = Integer.parseInt(q.get("x1")), z1 = Integer.parseInt(q.get("z1"));
		if (x1 < x0 || z1 < z0) throw new IllegalArgumentException("empty rectangle");
		return new int[] {x0, z0, x1, z1};
	}

	private JsonObject predict(Map<String, String> q) {
		long seed = seedParam(q);
		int[] r = rect(q);
		long n = (long) (r[2] - r[0] + 1) * (r[3] - r[1] + 1);
		if (n > MAX_PREDICT_CHUNKS) throw new IllegalArgumentException("area too large");
		Predictor p = predictor.get();
		JsonArray out = new JsonArray();
		for (int cx = r[0]; cx <= r[2]; cx++) {
			for (int cz = r[1]; cz <= r[3]; cz++) {
				int[] a = p.attempt(seed, cx, cz);
				if (a == null) continue;
				JsonArray e = new JsonArray();
				for (int v : a) e.add(v);
				out.add(e);
			}
		}
		JsonObject o = new JsonObject();
		o.add("attempts", out);
		return o;
	}

	private JsonObject verified(Map<String, String> q) throws IOException {
		long seed = seedParam(q);
		int[] r = rect(q);
		JsonArray chunks = new JsonArray();
		for (var e : verifier.results(seed).entrySet()) {
			int cx = (int) (e.getKey() >> 32), cz = (int) (long) e.getKey();
			if (cx < r[0] || cz < r[1] || cx > r[2] || cz > r[3]) continue;
			JsonArray a = new JsonArray();
			a.add(cx);
			a.add(cz);
			a.add(e.getValue()[0]);
			a.add(e.getValue()[1]);
			chunks.add(a);
		}
		JsonObject o = new JsonObject();
		o.add("chunks", chunks);
		JsonArray un = new JsonArray();
		for (int[] u : verifier.unexplained(seed)) {
			JsonArray a = new JsonArray();
			for (int v : u) a.add(v);
			un.add(a);
		}
		o.add("unexplained", un);
		return o;
	}

	private JsonObject startVerify(Map<String, String> q) {
		long seed = seedParam(q);
		int[] r = rect(q);
		if (r[2] - r[0] + 1 > Verifier.MAX_SIDE || r[3] - r[1] + 1 > Verifier.MAX_SIDE) {
			throw new IllegalArgumentException("Zoom in: verification covers at most " + Verifier.MAX_SIDE + " x " + Verifier.MAX_SIDE + " chunks");
		}
		synchronized (jobLock) {
			if (jobRunning) throw new IllegalArgumentException("A verification is already running");
			jobRunning = true;
			jobError = null;
			jobMessage = "Starting";
			jobProgress = null;
			jobSeed = seed;
			jobRect = r;
			Thread job = new Thread(() -> {
				try {
					verifier.verify(seed, r[0], r[1], r[2], r[3], this::progress);
					// the probe has now reported the live feature index; reload if it differs
					Predictor fresh = new Predictor(settings.modJar(), settings.mcJar(), probeJson);
					Predictor old = predictor.get();
					if (fresh.step != old.step || fresh.index != old.index || !fresh.indexSource.equals(old.indexSource)) predictor.set(fresh);
				} catch (Exception e) {
					jobError = e.getMessage() == null ? e.toString() : e.getMessage();
					e.printStackTrace();
				} finally {
					jobRunning = false;
				}
			}, "verify");
			job.start();
		}
		return status();
	}

	private void progress(String msg) {
		jobMessage = msg;
		var m = java.util.regex.Pattern.compile("generated (\\d+) / (\\d+) chunks").matcher(msg);
		if (m.find()) jobProgress = new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
	}

	private JsonObject status() {
		JsonObject o = new JsonObject();
		o.addProperty("running", jobRunning);
		o.addProperty("message", jobMessage);
		if (jobError != null) o.addProperty("error", jobError);
		o.addProperty("seed", Long.toString(jobSeed));
		o.addProperty("shadow_running", shadow.alive());
		if (jobProgress != null) {
			o.addProperty("done", jobProgress[0]);
			o.addProperty("total", jobProgress[1]);
		}
		if (jobRect != null) {
			JsonArray a = new JsonArray();
			for (int v : jobRect) a.add(v);
			o.add("rect", a);
		}
		return o;
	}

	private static Map<String, String> query(String raw) {
		Map<String, String> m = new HashMap<>();
		if (raw == null) return m;
		for (String kv : raw.split("&")) {
			int i = kv.indexOf('=');
			if (i < 0) continue;
			m.put(URLDecoder.decode(kv.substring(0, i), StandardCharsets.UTF_8), URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8));
		}
		return m;
	}

	private static void json(HttpExchange ex, int code, JsonObject o) throws IOException {
		byte[] b = o.toString().getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		ex.getResponseHeaders().set("Cache-Control", "no-store");
		ex.sendResponseHeaders(code, b.length);
		try (OutputStream os = ex.getResponseBody()) {
			os.write(b);
		}
	}

	private void extFile(HttpExchange ex, String name) throws IOException {
		for (Extension e : extensions) {
			if (name.equals(e.id() + ".js")) {
				file(ex, e.webDir(), name);
				return;
			}
		}
		ex.sendResponseHeaders(404, -1);
	}

	private static void file(HttpExchange ex, Path dir, String name) throws IOException {
		Path f = dir.resolve(name).normalize();
		if (!f.startsWith(dir) || !Files.isRegularFile(f)) {
			ex.sendResponseHeaders(404, -1);
			return;
		}
		String type = name.endsWith(".js") ? "text/javascript" : name.endsWith(".css") ? "text/css"
				: name.endsWith(".png") ? "image/png" : name.endsWith(".ico") ? "image/x-icon" : "text/html";
		byte[] b = Files.readAllBytes(f);
		ex.getResponseHeaders().set("Content-Type", type + (type.startsWith("text") ? "; charset=utf-8" : ""));
		ex.getResponseHeaders().set("Cache-Control", "no-store");
		ex.sendResponseHeaders(200, b.length);
		try (OutputStream os = ex.getResponseBody()) {
			os.write(b);
		}
	}
}

package dev.vplus.locator.launch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * First-run setup of the public locator. Downloads only from the official sources and checks every file against a
 * pinned hash, so the locator always runs exactly the versions it was tested with:
 * <ul>
 * <li>Minecraft 26.3 server (Mojang), whose bundled libraries the map and the predictions use,</li>
 * <li>Fabric server launcher (Fabric Loader 0.19.5) and Fabric API 0.162.0+26.3 (FabricMC), for the verification server.</li>
 * </ul>
 * Nothing from Mojang is shipped in the locator download itself.
 */
final class Setup {
	static final String MC = "26.3";
	static final String SERVER_URL = "https://piston-data.mojang.com/v1/objects/33680f5f2ac32864d6d7cf5e56a705fdb3e05f4c/server.jar";
	static final String SERVER_SHA1 = "33680f5f2ac32864d6d7cf5e56a705fdb3e05f4c";
	static final String FABRIC_LAUNCHER_URL = "https://meta.fabricmc.net/v2/versions/loader/26.3/0.19.5/1.1.2/server/jar";
	static final String FABRIC_LAUNCHER_SHA256 = "0b56ad54d762172e8b8748e467f584f071e4dedecd93dc336cf2c68837e790be";
	static final String FABRIC_API = "fabric-api-0.162.0+26.3.jar";
	static final String FABRIC_API_URL = "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.162.0+26.3/fabric-api-0.162.0+26.3.jar";
	static final String FABRIC_API_SHA256 = "8626c189b702f466800b1c8ddac6203cf8a1e0bdaee75a5f9a33b18c2baa81b1";
	/** Written last; its presence means setup finished. Bump when the set of files changes. */
	static final String DONE = "setup-complete-v1";

	private final Path app, data;
	private final Consumer<String> status;
	private final Consumer<Double> progress;
	private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build();

	Setup(Path app, Path data, Consumer<String> status, Consumer<Double> progress) {
		this.app = app;
		this.data = data;
		this.status = status;
		this.progress = progress;
	}

	Path minecraft() {
		return data.resolve("minecraft");
	}

	Path shadow() {
		return data.resolve("shadow-server");
	}

	boolean done() {
		return Files.exists(data.resolve(DONE)) && Files.exists(minecraft().resolve("versions").resolve(MC).resolve("server-" + MC + ".jar"));
	}

	void run() throws IOException, InterruptedException {
		Files.createDirectories(minecraft());
		Path server = minecraft().resolve("server.jar");
		if (!matches(server, "SHA-1", SERVER_SHA1)) {
			download("Downloading Minecraft " + MC + " server from Mojang", SERVER_URL, server, "SHA-1", SERVER_SHA1);
		}
		status.accept("Unpacking Minecraft libraries");
		unbundle(server, minecraft());

		Path sh = shadow();
		Files.createDirectories(sh.resolve("mods"));
		Path launcher = sh.resolve("fabric-server-launch.jar");
		if (!matches(launcher, "SHA-256", FABRIC_LAUNCHER_SHA256)) {
			download("Downloading Fabric server launcher", FABRIC_LAUNCHER_URL, launcher, "SHA-256", FABRIC_LAUNCHER_SHA256);
		}
		Path api = sh.resolve("mods").resolve(FABRIC_API);
		if (!matches(api, "SHA-256", FABRIC_API_SHA256)) {
			download("Downloading Fabric API", FABRIC_API_URL, api, "SHA-256", FABRIC_API_SHA256);
		}
		// the Fabric launcher starts this vanilla server jar (it unpacks its own copy of the libraries)
		if (!matches(sh.resolve("server.jar"), "SHA-1", SERVER_SHA1)) Files.copy(server, sh.resolve("server.jar"), StandardCopyOption.REPLACE_EXISTING);
		syncMods();
		Files.writeString(data.resolve(DONE), "Prismatic Geode Locator setup finished\n");
		progress.accept(1.0);
	}

	/** Jars that must not be on the launcher's class path live in app/lib. Keeps the Prismatic Geodes jar and the locator probe in the verification server in sync with this download. */
	void syncMods() throws IOException {
		Path mods = shadow().resolve("mods");
		Files.createDirectories(mods);
		try (var s = Files.list(mods)) {
			for (Path p : s.toList()) {
				String n = p.getFileName().toString();
				if (n.startsWith("prismatic-geodes-") || n.startsWith("vanillaplus-geodes-") || n.startsWith("vplus-geode-locator-probe")) Files.delete(p);
			}
		}
		Files.copy(modJar(app), mods.resolve(modJar(app).getFileName()));
		Files.copy(app.resolve("lib").resolve("locator-probe.jar"), mods.resolve("vplus-geode-locator-probe.jar"));
	}

	static Path modJar(Path app) throws IOException {
		try (var s = Files.list(app.resolve("lib"))) {
			return s.filter(p -> p.getFileName().toString().matches("prismatic-geodes-[0-9.]+\\+26\\.3\\.jar")).findFirst()
					.orElseThrow(() -> new IOException("Prismatic Geodes jar missing from " + app));
		}
	}

	/** Extracts the libraries and the real server jar from Mojang's bundler jar, checking each SHA-256. */
	private void unbundle(Path bundler, Path out) throws IOException {
		try (ZipFile z = new ZipFile(bundler.toFile())) {
			for (String list : new String[] {"META-INF/libraries.list", "META-INF/versions.list"}) {
				String dir = list.contains("libraries") ? "libraries" : "versions";
				try (BufferedReader r = new BufferedReader(new InputStreamReader(z.getInputStream(z.getEntry(list)), StandardCharsets.UTF_8))) {
					String line;
					while ((line = r.readLine()) != null) {
						String[] f = line.split("\t");
						if (f.length < 3) continue;
						Path target = out.resolve(dir).resolve(f[2]).normalize();
						if (!target.startsWith(out)) throw new IOException("bad path in bundler: " + f[2]);
						if (matches(target, "SHA-256", f[0])) continue;
						ZipEntry e = z.getEntry("META-INF/" + dir + "/" + f[2]);
						Files.createDirectories(target.getParent());
						Path tmp = target.resolveSibling(target.getFileName() + ".part");
						try (InputStream in = z.getInputStream(e)) {
							Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
						}
						if (!matches(tmp, "SHA-256", f[0])) throw new IOException("checksum mismatch unpacking " + f[2]);
						Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
					}
				}
			}
		}
	}

	private void download(String what, String url, Path target, String algo, String hash) throws IOException, InterruptedException {
		status.accept(what);
		progress.accept(0.0);
		Path tmp = target.resolveSibling(target.getFileName() + ".part");
		HttpResponse<InputStream> r = http.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "PrismaticGeodeLocator/1.0").build(),
				HttpResponse.BodyHandlers.ofInputStream());
		if (r.statusCode() != 200) throw new IOException(what + ": HTTP " + r.statusCode());
		long total = r.headers().firstValueAsLong("Content-Length").orElse(-1);
		try (InputStream in = r.body(); OutputStream out = Files.newOutputStream(tmp)) {
			byte[] buf = new byte[65536];
			long done = 0;
			int n;
			while ((n = in.read(buf)) > 0) {
				out.write(buf, 0, n);
				done += n;
				if (total > 0) progress.accept((double) done / total);
			}
		}
		if (!matches(tmp, algo, hash)) {
			Files.deleteIfExists(tmp);
			throw new IOException(what + ": the downloaded file does not match the expected checksum");
		}
		Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
	}

	static boolean matches(Path p, String algo, String hex) throws IOException {
		if (!Files.isRegularFile(p)) return false;
		try (InputStream in = Files.newInputStream(p)) {
			MessageDigest md = MessageDigest.getInstance(algo);
			byte[] buf = new byte[65536];
			int n;
			while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
			return HexFormat.of().formatHex(md.digest()).equalsIgnoreCase(hex);
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}

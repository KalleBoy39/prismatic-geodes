import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dev-only UI check (not shipped): drives headless Edge over the DevTools protocol, hovers and clicks the first
 * geode marker and saves screenshots. Single-file program: java UiCheck.java <url> <out dir>
 */
public final class UiCheck {
	static WebSocket ws;
	static final AtomicInteger ids = new AtomicInteger();
	static final Map<Integer, CompletableFuture<String>> pending = new ConcurrentHashMap<>();

	public static void main(String[] a) throws Exception {
		String url = a[0];
		Path out = Path.of(a[1]);
		Files.createDirectories(out);
		Path profile = Files.createTempDirectory("uicheck");
		Process edge = new ProcessBuilder("C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe", "--headless=new", "--disable-gpu",
				"--remote-debugging-port=9333", "--user-data-dir=" + profile, "--window-size=1500,900", "--hide-scrollbars", url)
				.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
		try {
			HttpClient http = HttpClient.newHttpClient();
			String wsUrl = null;
			for (int i = 0; i < 50 && wsUrl == null; i++) {
				Thread.sleep(300);
				try {
					String list = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:9333/json")).build(), HttpResponse.BodyHandlers.ofString()).body();
					Matcher m = Pattern.compile("\"type\": \"page\"[^}]*?\"webSocketDebuggerUrl\": \"([^\"]+)\"", Pattern.DOTALL).matcher(list);
					if (m.find()) wsUrl = m.group(1);
				} catch (Exception e) {
					// not up yet
				}
			}
			ws = http.newWebSocketBuilder().buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
				StringBuilder buf = new StringBuilder();

				@Override
				public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
					buf.append(data);
					if (last) {
						String msg = buf.toString();
						buf = new StringBuilder();
						Matcher m = Pattern.compile("^\\{\"id\":(\\d+)").matcher(msg);
						if (m.find()) {
							CompletableFuture<String> f = pending.remove(Integer.parseInt(m.group(1)));
							if (f != null) f.complete(msg);
						}
					}
					w.request(1);
					return null;
				}
			}).get();
			// wait until markers are drawn and tiles have loaded
			String n = "0";
			for (int i = 0; i < 60; i++) {
				n = eval("String(Locator.state.drawn.length)");
				if (!n.equals("0")) break;
				Thread.sleep(500);
			}
			Thread.sleep(4000);
			System.out.println("markers drawn: " + n);
			shot(out.resolve("ui_map.png"));
			String xy = eval("(() => { const r = document.getElementById('map').getBoundingClientRect(); const d = Locator.state.drawn.find(d => d.sx > 300 && d.sy > 200 && d.sx < r.width - 400 && d.sy < r.height - 300) || Locator.state.drawn[0]; return (r.left + d.sx) + ',' + (r.top + d.sy); })()");
			String[] p = xy.split(",");
			double x = Double.parseDouble(p[0]), y = Double.parseDouble(p[1]);
			mouse("mouseMoved", x, y);
			Thread.sleep(500);
			shot(out.resolve("ui_hover.png"));
			System.out.println("tooltip: " + eval("document.getElementById('tip').textContent"));
			mouse("mousePressed", x, y);
			mouse("mouseReleased", x, y);
			Thread.sleep(600);
			shot(out.resolve("ui_popup.png"));
			System.out.println("popup: " + eval("document.getElementById('popup').innerText.replace(/\\n+/g, ' | ')"));
		} finally {
			edge.destroy();
			edge.waitFor(5, TimeUnit.SECONDS);
		}
		System.exit(0);
	}

	static String send(String method, String params) throws Exception {
		int id = ids.incrementAndGet();
		CompletableFuture<String> f = new CompletableFuture<>();
		pending.put(id, f);
		ws.sendText("{\"id\":" + id + ",\"method\":\"" + method + "\",\"params\":" + params + "}", true).get();
		return f.get(30, TimeUnit.SECONDS);
	}

	static String eval(String js) throws Exception {
		String r = send("Runtime.evaluate", "{\"expression\":" + quote(js) + ",\"returnByValue\":true}");
		Matcher m = Pattern.compile("\"value\":\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(r);
		return m.find() ? m.group(1).replace("\\\"", "\"").replace("\\n", "\n") : r;
	}

	static void mouse(String type, double x, double y) throws Exception {
		send("Input.dispatchMouseEvent", "{\"type\":\"" + type + "\",\"x\":" + x + ",\"y\":" + y + ",\"button\":\"left\",\"clickCount\":1}");
	}

	static void shot(Path f) throws Exception {
		String r = send("Page.captureScreenshot", "{\"format\":\"png\"}");
		Matcher m = Pattern.compile("\"data\":\"([^\"]+)\"").matcher(r);
		m.find();
		Files.write(f, Base64.getDecoder().decode(m.group(1)));
	}

	static String quote(String s) {
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}
}

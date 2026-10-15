package dev.vplus.locator.launch;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

/**
 * Entry point of the public Prismatic Geode Locator ("Prismatic Geode Locator.exe").
 *
 * Shows a small window, asks for the Minecraft EULA once, runs the first-time setup (official downloads), then loads
 * the backend together with the Minecraft classes in its own class loader and opens the map in the browser.
 * Closing the window stops the locator and its verification server.
 */
public final class Launcher {
	static final String TITLE = "Prismatic Geode Locator";
	static final String EULA_URL = "https://www.minecraft.net/eula";

	private Path app, data;
	private JFrame frame;
	private JLabel status;
	private JProgressBar bar;
	private JPanel actions;
	private volatile String url;

	public static void main(String[] args) throws Exception {
		new Launcher().start();
	}

	private void start() throws Exception {
		app = Path.of(Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
		data = dataDir(app);
		Files.createDirectories(data.resolve("logs"));
		log();
		System.out.println("=== " + TITLE + " " + LocalDateTime.now() + " app=" + app + " data=" + data + " java=" + System.getProperty("java.home"));

		// already running? just open the map again
		String running = runningUrl();
		if (running != null) {
			Desktop.getDesktop().browse(URI.create(running));
			System.exit(0);
		}
		SwingUtilities.invokeAndWait(this::window);
		if (!Files.exists(data.resolve("eula-accepted.txt"))) {
			SwingUtilities.invokeLater(this::askEula);
		} else {
			new Thread(this::boot, "boot").start();
		}
	}

	/** Next to the program when that folder is writable (so deleting the folder removes everything), else %LOCALAPPDATA%. */
	static Path dataDir(Path app) {
		Path beside = app.getParent().resolve("data");
		try {
			Files.createDirectories(beside);
			Path probe = Files.createTempFile(beside, "write", ".tmp");
			Files.delete(probe);
			return beside;
		} catch (IOException e) {
			String local = System.getenv("LOCALAPPDATA");
			return Path.of(local != null ? local : System.getProperty("user.home"), "Prismatic Geode Locator");
		}
	}

	private void log() throws IOException {
		Path f = data.resolve("logs").resolve("locator.log");
		if (Files.exists(f) && Files.size(f) > 5_000_000) Files.move(f, f.resolveSibling("locator.old.log"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		OutputStream file = new FileOutputStream(f.toFile(), true);
		PrintStream tee = new PrintStream(new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				file.write(b);
			}

			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				file.write(b, off, len);
				file.flush();
			}
		}, true, StandardCharsets.UTF_8);
		System.setOut(tee);
		System.setErr(tee);
	}

	private String runningUrl() {
		try {
			Path f = data.resolve("locator.port");
			if (!Files.exists(f)) return null;
			String u = "http://127.0.0.1:" + Files.readString(f).trim() + "/";
			HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
			HttpResponse<String> r = c.send(HttpRequest.newBuilder(URI.create(u + "api/info")).timeout(Duration.ofSeconds(3)).build(), HttpResponse.BodyHandlers.ofString());
			return r.statusCode() == 200 && r.body().contains("\"edition\"") ? u : null;
		} catch (Exception e) {
			return null;
		}
	}

	// ---------- window ----------
	private void window() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {
			// default look and feel is fine
		}
		frame = new JFrame(TITLE);
		frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				quit();
			}
		});
		try {
			frame.setIconImage(javax.imageio.ImageIO.read(app.resolve("web").resolve("icon.png").toFile()));
		} catch (Exception e) {
			// no icon
		}
		JPanel root = new JPanel(new BorderLayout(0, 10));
		root.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
		JLabel title = new JLabel(TITLE);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.add(title);
		top.add(new JLabel("Finds the coloured geodes of Prismatic Geodes in your world from its seed. Minecraft 26.3"));
		root.add(top, BorderLayout.NORTH);
		JPanel mid = new JPanel();
		mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
		status = new JLabel("Starting…");
		bar = new JProgressBar(0, 1000);
		bar.setIndeterminate(true);
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));
		mid.add(status);
		mid.add(Box.createVerticalStrut(8));
		mid.add(bar);
		root.add(mid, BorderLayout.CENTER);
		actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		root.add(actions, BorderLayout.SOUTH);
		frame.setContentPane(root);
		frame.setSize(520, 210);
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	private void setStatus(String s) {
		System.out.println("[status] " + s);
		SwingUtilities.invokeLater(() -> status.setText("<html>" + s + "</html>"));
	}

	private void setProgress(double p) {
		SwingUtilities.invokeLater(() -> {
			bar.setIndeterminate(false);
			bar.setValue((int) Math.round(p * 1000));
		});
	}

	private void buttons(JButton... bs) {
		SwingUtilities.invokeLater(() -> {
			actions.removeAll();
			for (JButton b : bs) actions.add(b);
			actions.revalidate();
			actions.repaint();
		});
	}

	private JButton button(String text, Runnable r) {
		JButton b = new JButton(text);
		b.addActionListener(e -> r.run());
		return b;
	}

	private void askEula() {
		bar.setVisible(false);
		status.setText("<html>The locator computes the map and checks geodes with the official <b>Minecraft 26.3 server</b>. "
				+ "On first start it downloads it from Mojang (about 60 MB), plus Fabric Loader and Fabric API.<br><br>"
				+ "To use the Minecraft server you must accept the Minecraft End User License Agreement.</html>");
		JButton read = button("Read the EULA", () -> browse(EULA_URL));
		JCheckBox accept = new JCheckBox("I accept the Minecraft EULA");
		JButton go = button("Download and start", () -> {
			try {
				Files.writeString(data.resolve("eula-accepted.txt"), "Minecraft EULA (" + EULA_URL + ") accepted in the Prismatic Geode Locator on " + LocalDateTime.now() + "\n");
			} catch (IOException e) {
				fail(e);
				return;
			}
			bar.setVisible(true);
			new Thread(this::boot, "boot").start();
		});
		go.setEnabled(false);
		accept.addActionListener(e -> go.setEnabled(accept.isSelected()));
		actions.removeAll();
		actions.add(read);
		actions.add(accept);
		actions.add(go);
		frame.setSize(560, 250);
		actions.revalidate();
	}

	private void boot() {
		buttons(button("Quit", this::quit));
		try {
			Setup setup = new Setup(app, data, this::setStatus, this::setProgress);
			if (!setup.done()) {
				setup.run();
			} else {
				setup.syncMods();
			}
			setStatus("Starting the locator…");
			SwingUtilities.invokeLater(() -> bar.setIndeterminate(true));
			int port = startBackend(setup);
			Files.writeString(data.resolve("locator.port"), Integer.toString(port));
			url = "http://127.0.0.1:" + port + "/";
			setStatus("Running. The map is open in your browser at <a href=''>" + url + "</a><br>"
					+ "Keep this window open while you use the map; closing it stops the locator.");
			SwingUtilities.invokeLater(() -> {
				bar.setIndeterminate(false);
				bar.setValue(1000);
				status.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				status.addMouseListener(new java.awt.event.MouseAdapter() {
					@Override
					public void mouseClicked(java.awt.event.MouseEvent e) {
						browse(url);
					}
				});
			});
			buttons(button("Open data folder", () -> open(data)), button("Open map", () -> browse(url)), button("Quit", this::quit));
			browse(url);
		} catch (Throwable t) {
			fail(t);
		}
	}

	/** Loads the backend and the Minecraft server classes (never shipped, downloaded in setup) in their own class loader. */
	private int startBackend(Setup setup) throws Exception {
		List<URL> urls = new ArrayList<>();
		urls.add(app.resolve("lib").resolve("locator-core.jar").toUri().toURL());
		Path mc = setup.minecraft();
		urls.add(mc.resolve("versions").resolve(Setup.MC).resolve("server-" + Setup.MC + ".jar").toUri().toURL());
		try (Stream<Path> s = Files.walk(mc.resolve("libraries"))) {
			for (Path p : s.filter(p -> p.toString().endsWith(".jar")).toList()) urls.add(p.toUri().toURL());
		}
		System.setProperty("log4j2.configurationFile", app.resolve("log4j2.xml").toString());
		URLClassLoader loader = new URLClassLoader(urls.toArray(URL[]::new), Launcher.class.getClassLoader());
		Thread.currentThread().setContextClassLoader(loader);
		Class<?> main = Class.forName("dev.vplus.locator.Main", true, loader);
		return (int) main.getMethod("startPublic", Path.class, Path.class).invoke(null, app, data);
	}

	private void fail(Throwable t) {
		t.printStackTrace();
		Throwable c = t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null ? t.getCause() : t;
		String msg = c.getMessage() == null ? c.toString() : c.getMessage();
		boolean net = c instanceof java.net.ConnectException || c instanceof java.net.http.HttpConnectTimeoutException
				|| c instanceof java.nio.channels.UnresolvedAddressException || msg.contains("HTTP");
		setStatus("<b>Something went wrong:</b> " + escape(msg) + "<br>"
				+ (net ? "Check your internet connection and try again. " : "")
				+ "Details are in data\\logs\\locator.log. TROUBLESHOOTING.txt explains common problems.");
		SwingUtilities.invokeLater(() -> {
			bar.setIndeterminate(false);
			bar.setValue(0);
		});
		buttons(button("Open log folder", () -> open(data.resolve("logs"))), button("Try again", () -> new Thread(this::boot, "boot").start()),
				button("Quit", this::quit));
	}

	private static String escape(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private void browse(String u) {
		try {
			Desktop.getDesktop().browse(URI.create(u));
		} catch (Exception e) {
			JOptionPane.showMessageDialog(frame, "Open this address in your browser:\n" + u, TITLE, JOptionPane.INFORMATION_MESSAGE);
		}
	}

	private void open(Path p) {
		try {
			Desktop.getDesktop().open(p.toFile());
		} catch (Exception e) {
			JOptionPane.showMessageDialog(frame, p.toString(), TITLE, JOptionPane.INFORMATION_MESSAGE);
		}
	}

	private void quit() {
		setStatus("Stopping…");
		new Thread(() -> {
			try {
				Files.deleteIfExists(data.resolve("locator.port"));
			} catch (IOException e) {
				// ignore
			}
			System.exit(0); // shutdown hooks stop the verification server
		}, "quit").start();
	}
}

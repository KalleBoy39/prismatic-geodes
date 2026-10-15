package dev.vplus.locator;

import java.nio.file.Path;
import java.util.Map;

import com.google.gson.JsonObject;

/**
 * Optional add-on for the locator backend. The public build has none; the private dev build adds its own
 * (compiled from a separate source folder) for features that only make sense on the developer's machine.
 */
interface Extension {
	/** Short id, also the name of the extension's web script ({@code /ext/<id>.js}). */
	String id();

	/** Folder holding {@code <id>.js}. */
	Path webDir();

	/** Adds the extension's fields to /api/info. */
	void info(JsonObject info) throws Exception;

	/** Handles an /api/ request, or returns null if the path is not the extension's. */
	JsonObject api(String path, Map<String, String> query, String method, Main.Context ctx) throws Exception;
}

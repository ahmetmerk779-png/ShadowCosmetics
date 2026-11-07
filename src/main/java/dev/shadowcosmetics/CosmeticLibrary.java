package dev.shadowcosmetics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.texture.NativeImage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * Online cosmetic discovery and local PNG import.
 * Uses HttpURLConnection for Android runtime compatibility.
 */
public final class CosmeticLibrary {

    private static final int MAX_FILE_SIZE = 8 * 1024 * 1024;
    private static final int MAX_SEARCH_RESULTS = 12;

    public record Result(String title, String imageUrl, String mime) {}

    private CosmeticLibrary() {
    }

    public static Path libraryDirectory() {
        return net.fabricmc.loader.api.FabricLoader.getInstance()
                .getConfigDir()
                .resolve("shadowcosmetics")
                .resolve("cosmetics");
    }

    /**
     * Searches the community cape catalogue and Wikimedia Commons.
     */
    public static List<Result> searchOnline(String query) throws IOException {
        if (query == null || query.isBlank()) {
            throw new IOException("Search query cannot be empty.");
        }

        List<Result> combined = new ArrayList<>();

        try {
            combined.addAll(searchCapeCatalogue(query));
        } catch (IOException ignored) {
            // Continue with Wikimedia Commons.
        }

        try {
            combined.addAll(searchCommons(query));
        } catch (IOException exception) {
            if (combined.isEmpty()) {
                throw exception;
            }
        }

        List<Result> unique = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();

        for (Result result : combined) {
            if (seen.add(result.imageUrl())) {
                unique.add(result);
            }

            if (unique.size() >= MAX_SEARCH_RESULTS) {
                break;
            }
        }

        return unique;
    }

    /**
     * Searches a public community cape catalogue.
     */
    private static List<Result> searchCapeCatalogue(String query)
            throws IOException {

        String encoded = URLEncoder.encode(
                query,
                StandardCharsets.UTF_8
        );

        String address =
                "https://mc-api.bisai.dev/v1/capes?search="
                        + encoded
                        + "&limit=12&sort=popular";

        String body = getText(address, 15000);

        JsonObject root;

        try {
            root = JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException("Invalid cape catalogue response.", exception);
        }

        List<Result> results = new ArrayList<>();

        if (!root.has("capes") || !root.get("capes").isJsonArray()) {
            return results;
        }

        for (JsonElement element : root.getAsJsonArray("capes")) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject cape = element.getAsJsonObject();

            String name = cape.has("name")
                    ? cape.get("name").getAsString()
                    : "Community Cape";

            String imageUrl = cape.has("url")
                    ? cape.get("url").getAsString()
                    : "";

            if (imageUrl.startsWith("/")) {
                imageUrl = "https://mc-api.bisai.dev" + imageUrl;
            }

            if (imageUrl.startsWith("https://")) {
                results.add(new Result(
                        "[Cape Gallery] " + name,
                        imageUrl,
                        "image/png"
                ));
            }
        }

        return results;
    }

    /**
     * Searches Wikimedia Commons for cape-compatible PNG files.
     */
    public static List<Result> searchCommons(String query)
            throws IOException {

        String encoded = URLEncoder.encode(
                query,
                StandardCharsets.UTF_8
        );

        String address =
                "https://commons.wikimedia.org/w/api.php"
                        + "?action=query"
                        + "&generator=search"
                        + "&gsrsearch=" + encoded
                        + "&gsrnamespace=6"
                        + "&gsrlimit=20"
                        + "&prop=imageinfo"
                        + "&iiprop=url%7Cmime%7Csize"
                        + "&format=json";

        String body = getText(address, 15000);

        JsonObject root;

        try {
            root = JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException("Invalid Wikimedia response.", exception);
        }

        JsonObject pages = new JsonObject();

        if (root.has("query")
                && root.getAsJsonObject("query").has("pages")) {

            pages = root.getAsJsonObject("query")
                    .getAsJsonObject("pages");
        }

        List<Result> results = new ArrayList<>();

        for (var entry : pages.entrySet()) {
            JsonObject page = entry.getValue().getAsJsonObject();

            if (!page.has("imageinfo")
                    || !page.getAsJsonArray("imageinfo").isJsonArray()
                    || page.getAsJsonArray("imageinfo").isEmpty()) {
                continue;
            }

            JsonObject info = page.getAsJsonArray("imageinfo")
                    .get(0)
                    .getAsJsonObject();

            String mime = info.has("mime")
                    ? info.get("mime").getAsString()
                    : "";

            String imageUrl = info.has("url")
                    ? info.get("url").getAsString()
                    : "";

            int width = info.has("width")
                    ? info.get("width").getAsInt()
                    : 0;

            int height = info.has("height")
                    ? info.get("height").getAsInt()
                    : 0;

            if ("image/png".equalsIgnoreCase(mime)
                    && imageUrl.startsWith("https://")
                    && width == 64
                    && (height == 32 || height == 64)) {

                String title = page.has("title")
                        ? page.get("title").getAsString()
                        : "Community Cape";

                results.add(new Result(
                        title,
                        imageUrl,
                        mime
                ));
            }
        }

        results.sort(
                Comparator.comparing(
                        Result::title,
                        String.CASE_INSENSITIVE_ORDER
                )
        );

        return results;
    }

    /**
     * Downloads a selected cape and saves it to the local library.
     */
    public static Path download(Result result) throws IOException {
        if (result == null) {
            throw new IOException("No cosmetic selected.");
        }

        if (!"image/png".equalsIgnoreCase(result.mime())) {
            throw new IOException("Only PNG files are supported.");
        }

        if (!result.imageUrl().startsWith("https://")) {
            throw new IOException("Only HTTPS downloads are supported.");
        }

        byte[] bytes = getBytes(
                result.imageUrl(),
                20000,
                MAX_FILE_SIZE
        );

        return savePng(
                bytes,
                safeName(result.title())
        );
    }

    /**
     * Imports a PNG from a local file path.
     */
    public static Path importPng(Path source) throws IOException {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IOException("File not found.");
        }

        long size = Files.size(source);

        if (size <= 0 || size > MAX_FILE_SIZE) {
            throw new IOException("PNG must be smaller than 8 MB.");
        }

        byte[] bytes = Files.readAllBytes(source);

        return savePng(
                bytes,
                safeName(source.getFileName().toString())
        );
    }

    /**
     * Lists PNG files in a local directory.
     */
    public static List<Path> listLocalPngs(Path directory)
            throws IOException {

        if (directory == null || !Files.isDirectory(directory)) {
            throw new IOException("Folder not found or inaccessible.");
        }

        try (var stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName()
                            .toString()
                            .toLowerCase()
                            .endsWith(".png"))
                    .sorted(
                            Comparator.comparing(
                                    path -> path.getFileName().toString(),
                                    String.CASE_INSENSITIVE_ORDER
                            )
                    )
                    .limit(40)
                    .toList();
        }
    }

    /**
     * Saves a validated cape PNG into the cosmetic library.
     */
    private static Path savePng(byte[] bytes, String fileName)
            throws IOException {

        if (bytes == null || bytes.length == 0) {
            throw new IOException("Downloaded file is empty.");
        }

        if (bytes.length > MAX_FILE_SIZE) {
            throw new IOException("Maximum file size is 8 MB.");
        }

        validateCapePng(bytes);

        Path directory = libraryDirectory();

        Files.createDirectories(directory);

        String safeFileName = safeName(fileName);

        if (!safeFileName.toLowerCase().endsWith(".png")) {
            safeFileName += ".png";
        }

        Path destination = directory.resolve(safeFileName)
                .normalize();

        if (!destination.getParent().equals(directory.normalize())) {
            throw new IOException("Invalid file name.");
        }

        Files.write(destination, bytes);

        return destination;
    }

    /**
     * Checks that the image is a supported Minecraft cape texture.
     */
    private static void validateCapePng(byte[] bytes)
            throws IOException {

        try (ByteArrayInputStream input =
                     new ByteArrayInputStream(bytes)) {

            NativeImage image = NativeImage.read(input);

            if (image == null) {
                throw new IOException("Invalid PNG image.");
            }

            try {
                int width = image.getWidth();
                int height = image.getHeight();

                if (width != 64 || (height != 32 && height != 64)) {
                    throw new IOException(
                            "Cape PNG must be 64x32 or 64x64."
                    );
                }

            } finally {
                image.close();
            }

        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("Could not read PNG image.", exception);
        }
    }

    /**
     * Android-compatible HTTP GET helper.
     */
    private static String getText(String address, int timeout)
            throws IOException {

        return new String(
                getBytes(address, timeout, 2 * 1024 * 1024),
                StandardCharsets.UTF_8
        );
    }

    /**
     * Downloads bytes with connection and file-size limits.
     */
    private static byte[] getBytes(
            String address,
            int timeout,
            int maxBytes
    ) throws IOException {

        HttpURLConnection connection =
                (HttpURLConnection) new URL(address).openConnection();

        connection.setConnectTimeout(10000);
        connection.setReadTimeout(timeout);
        connection.setRequestMethod("GET");
        connection.setInstanceFollowRedirects(true);

        connection.setRequestProperty(
                "User-Agent",
                "ShadowCosmetics/1.1"
        );

        connection.setRequestProperty(
                "Accept",
                "application/json,image/png,*/*"
        );

        try {
            int responseCode = connection.getResponseCode();

            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException(
                        "Server returned HTTP " + responseCode
                );
            }

            try (
                    InputStream input = connection.getInputStream();
                    ByteArrayOutputStream output =
                            new ByteArrayOutputStream()
            ) {

                byte[] buffer = new byte[8192];

                int read;

                while ((read = input.read(buffer)) != -1) {
                    if (output.size() + read > maxBytes) {
                        throw new IOException(
                                "Response exceeds size limit."
                        );
                    }

                    output.write(buffer, 0, read);
                }

                return output.toByteArray();
            }

        } finally {
            connection.disconnect();
        }
    }

    /**
     * Removes unsafe characters from downloaded file names.
     */
    private static String safeName(String value) {
        if (value == null || value.isBlank()) {
            return "custom_cosmetic.png";
        }

        String name = value
                .replaceFirst("(?i)^File:", "")
                .replaceAll("[^a-zA-Z0-9._-]", "_");

        if (name.length() > 64) {
            name = name.substring(0, 64);
        }

        if (name.isBlank()) {
            return "custom_cosmetic.png";
        }

        return name;
    }
}

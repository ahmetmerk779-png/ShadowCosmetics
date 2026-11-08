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
 * Online cape discovery, local PNG import and the saved-cape library.
 * Uses HttpURLConnection for Android runtime compatibility.
 */
public final class CosmeticLibrary {

    private static final int MAX_SEARCH_RESULTS = 16;
    private static final int MAX_LIBRARY_ENTRIES = 200;

    private static final String USER_AGENT =
            "ShadowCosmetics/0.2 (Minecraft Fabric mod; "
                    + "https://github.com/ahmetmerk779-png/ShadowCosmetics)";

    public record Result(String title, String imageUrl, String mime) {}

    private CosmeticLibrary() {
    }

    public static Path libraryDirectory() {
        return net.fabricmc.loader.api.FabricLoader.getInstance()
                .getConfigDir()
                .resolve("shadowcosmetics")
                .resolve("cosmetics");
    }

    // ------------------------------------------------------------------
    // Online search
    // ------------------------------------------------------------------

    /** Searches the community cape catalogue and Wikimedia Commons. */
    public static List<Result> searchOnline(String query) throws IOException {
        if (query == null || query.isBlank()) {
            throw new CosmeticException("shadowcosmetics.error.empty_query");
        }

        List<Result> combined = new ArrayList<>();
        IOException catalogueFailure = null;

        try {
            combined.addAll(searchCapeCatalogue(query.trim(), true));
        } catch (IOException exception) {
            catalogueFailure = exception;
        }

        try {
            combined.addAll(searchCommons(query.trim()));
        } catch (IOException exception) {
            // Only fail when neither source answered.
            if (combined.isEmpty()) {
                throw catalogueFailure != null ? catalogueFailure : exception;
            }
        }

        return unique(combined);
    }

    /** The most popular community capes (no search term). */
    public static List<Result> popular() throws IOException {
        return unique(searchCapeCatalogue("", false));
    }

    private static List<Result> unique(List<Result> combined) {
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

    /** Searches a public community cape catalogue. */
    private static List<Result> searchCapeCatalogue(
            String query,
            boolean useSearchTerm
    ) throws IOException {

        String address = "https://mc-api.bisai.dev/v1/capes?limit=12&sort=popular";

        if (useSearchTerm) {
            address += "&search=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        }

        String body = getText(address, 15000);

        JsonObject root;

        try {
            root = JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new CosmeticException("shadowcosmetics.error.bad_response");
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

            String name = stringOf(cape, "name", "Community Cape");
            String imageUrl = stringOf(cape, "url", "");

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

    /** Searches Wikimedia Commons for cape-compatible PNG files. */
    public static List<Result> searchCommons(String query) throws IOException {
        String encoded = URLEncoder.encode(
                query + " filetype:bitmap",
                StandardCharsets.UTF_8
        );

        String address = "https://commons.wikimedia.org/w/api.php"
                + "?action=query"
                + "&generator=search"
                + "&gsrsearch=" + encoded
                + "&gsrnamespace=6"
                + "&gsrlimit=30"
                + "&prop=imageinfo"
                + "&iiprop=url%7Cmime%7Csize"
                + "&format=json";

        String body = getText(address, 15000);

        JsonObject root;

        try {
            root = JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new CosmeticException("shadowcosmetics.error.bad_response");
        }

        JsonObject pages = new JsonObject();

        if (root.has("query") && root.getAsJsonObject("query").has("pages")) {
            pages = root.getAsJsonObject("query").getAsJsonObject("pages");
        }

        List<Result> results = new ArrayList<>();

        for (var entry : pages.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }

            JsonObject page = entry.getValue().getAsJsonObject();

            if (!page.has("imageinfo")
                    || !page.get("imageinfo").isJsonArray()
                    || page.getAsJsonArray("imageinfo").isEmpty()) {
                continue;
            }

            JsonObject info = page.getAsJsonArray("imageinfo")
                    .get(0)
                    .getAsJsonObject();

            String mime = stringOf(info, "mime", "");
            String imageUrl = stringOf(info, "url", "");
            int width = intOf(info, "width");
            int height = intOf(info, "height");

            if ("image/png".equalsIgnoreCase(mime)
                    && imageUrl.startsWith("https://")
                    && CapeFormat.isValidSize(width, height)) {

                results.add(new Result(
                        stringOf(page, "title", "Community Cape"),
                        imageUrl,
                        mime
                ));
            }
        }

        results.sort(Comparator.comparing(
                Result::title,
                String.CASE_INSENSITIVE_ORDER
        ));

        return results;
    }

    // ------------------------------------------------------------------
    // Download / import
    // ------------------------------------------------------------------

    /** Downloads a selected cape and saves it to the local library. */
    public static Path download(Result result) throws IOException {
        if (result == null) {
            throw new CosmeticException("shadowcosmetics.error.no_selection");
        }

        if (!"image/png".equalsIgnoreCase(result.mime())) {
            throw new CosmeticException("shadowcosmetics.error.png_only");
        }

        if (!result.imageUrl().startsWith("https://")) {
            throw new CosmeticException("shadowcosmetics.error.https_only");
        }

        byte[] bytes = getBytes(
                result.imageUrl(),
                20000,
                CapeFormat.MAX_FILE_SIZE
        );

        return savePng(bytes, result.title());
    }

    /**
     * Imports a PNG from a local file path into the library.
     * A file that already lives in the library is used as it is.
     */
    public static Path importPng(Path source) throws IOException {
        if (source == null || !Files.isRegularFile(source)) {
            throw new CosmeticException("shadowcosmetics.error.file_missing");
        }

        if (isInLibrary(source)) {
            validateCapePng(Files.readAllBytes(source));
            return source;
        }

        long size = Files.size(source);

        if (size <= 0) {
            throw new CosmeticException("shadowcosmetics.error.empty_file");
        }

        if (size > CapeFormat.MAX_FILE_SIZE) {
            throw new CosmeticException("shadowcosmetics.error.too_big");
        }

        return savePng(
                Files.readAllBytes(source),
                source.getFileName().toString()
        );
    }

    /** Saves a validated cape PNG into the cosmetic library. */
    private static Path savePng(byte[] bytes, String title) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new CosmeticException("shadowcosmetics.error.empty_file");
        }

        if (bytes.length > CapeFormat.MAX_FILE_SIZE) {
            throw new CosmeticException("shadowcosmetics.error.too_big");
        }

        validateCapePng(bytes);

        Path directory = libraryDirectory().toAbsolutePath().normalize();
        Files.createDirectories(directory);

        Path destination = directory
                .resolve(CapeFormat.libraryFileName(title, bytes))
                .normalize();

        if (!directory.equals(destination.getParent())) {
            throw new CosmeticException("shadowcosmetics.error.bad_name");
        }

        // Same content = same file name, so an identical cape is never stored twice.
        if (!Files.isRegularFile(destination)) {
            Files.write(destination, bytes);
        }

        return destination;
    }

    /** Checks that the data is a real PNG with a supported cape size. */
    private static void validateCapePng(byte[] bytes) throws IOException {
        if (!CapeFormat.isPng(bytes)) {
            throw new CosmeticException("shadowcosmetics.error.not_png");
        }

        // Cheap header check first, so we can name the size in the message.
        int[] headerSize = CapeFormat.readPngSize(bytes);

        if (headerSize != null
                && !CapeFormat.isValidSize(headerSize[0], headerSize[1])) {
            throw new CosmeticException(
                    "shadowcosmetics.error.bad_size",
                    CapeFormat.sizeLabel(headerSize)
            );
        }

        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes);
             NativeImage image = NativeImage.read(input)) {

            if (!CapeFormat.isValidSize(image.getWidth(), image.getHeight())) {
                throw new CosmeticException(
                        "shadowcosmetics.error.bad_size",
                        image.getWidth() + "x" + image.getHeight()
                );
            }

        } catch (CosmeticException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new CosmeticException("shadowcosmetics.error.bad_image");
        }
    }

    // ------------------------------------------------------------------
    // Saved capes (the library folder)
    // ------------------------------------------------------------------

    /** Saved capes, newest first. */
    public static List<Path> listLibrary() {
        Path directory = libraryDirectory();

        if (!Files.isDirectory(directory)) {
            return List.of();
        }

        try (var stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .toLowerCase().endsWith(".png"))
                    .sorted(Comparator.comparingLong(
                            CosmeticLibrary::lastModified
                    ).reversed())
                    .limit(MAX_LIBRARY_ENTRIES)
                    .toList();

        } catch (IOException | RuntimeException exception) {
            return List.of();
        }
    }

    /** True when the file is directly inside the cape library folder. */
    public static boolean isInLibrary(Path path) {
        if (path == null) {
            return false;
        }

        Path parent = path.toAbsolutePath().normalize().getParent();

        return parent != null
                && parent.equals(libraryDirectory().toAbsolutePath().normalize());
    }

    /** Deletes a saved cape. Only files inside the library can be deleted. */
    public static void deleteFromLibrary(Path path) throws IOException {
        if (!isInLibrary(path)) {
            throw new CosmeticException("shadowcosmetics.error.outside_library");
        }

        Files.deleteIfExists(path);
    }

    /** Reads {width, height} from a PNG header; null if unreadable. */
    public static int[] peekSize(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            return CapeFormat.readPngSize(input.readNBytes(33));
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return 0L;
        }
    }

    // ------------------------------------------------------------------
    // HTTP helpers
    // ------------------------------------------------------------------

    private static String getText(String address, int timeout)
            throws IOException {

        return new String(
                getBytes(address, timeout, 2 * 1024 * 1024),
                StandardCharsets.UTF_8
        );
    }

    /** GET with size limits. Retries once on rate limits and server errors. */
    private static byte[] getBytes(
            String address,
            int timeout,
            int maxBytes
    ) throws IOException {

        try {
            return fetchOnce(address, timeout, maxBytes);

        } catch (CosmeticException exception) {
            boolean retryable = "shadowcosmetics.error.http".equals(exception.key())
                    && exception.args().length > 0
                    && isRetryable(exception.args()[0]);

            if (!retryable) {
                throw exception;
            }

            pause(800);
            return fetchOnce(address, timeout, maxBytes);
        }
    }

    private static boolean isRetryable(Object code) {
        String value = String.valueOf(code);
        return value.equals("429") || value.startsWith("5");
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static byte[] fetchOnce(
            String address,
            int timeout,
            int maxBytes
    ) throws IOException {

        HttpURLConnection connection;

        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
        } catch (IOException | ClassCastException exception) {
            throw new CosmeticException(
                    "shadowcosmetics.error.network",
                    String.valueOf(exception.getMessage())
            );
        }

        connection.setConnectTimeout(10000);
        connection.setReadTimeout(timeout);
        connection.setRequestMethod("GET");
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/json,image/png,*/*");

        try {
            int responseCode = connection.getResponseCode();

            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new CosmeticException(
                        "shadowcosmetics.error.http",
                        String.valueOf(responseCode)
                );
            }

            long declared = connection.getContentLengthLong();

            if (declared > maxBytes) {
                throw new CosmeticException("shadowcosmetics.error.too_large_response");
            }

            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[8192];
                int read;

                while ((read = input.read(buffer)) != -1) {
                    if (output.size() + read > maxBytes) {
                        throw new CosmeticException(
                                "shadowcosmetics.error.too_large_response"
                        );
                    }

                    output.write(buffer, 0, read);
                }

                return output.toByteArray();
            }

        } catch (CosmeticException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new CosmeticException(
                    "shadowcosmetics.error.network",
                    String.valueOf(exception.getMessage())
            );

        } finally {
            connection.disconnect();
        }
    }

    private static String stringOf(JsonObject object, String key, String fallback) {
        try {
            if (object.has(key) && object.get(key).isJsonPrimitive()) {
                return object.get(key).getAsString();
            }
        } catch (RuntimeException ignored) {
            // fall through
        }

        return fallback;
    }

    private static int intOf(JsonObject object, String key) {
        try {
            if (object.has(key) && object.get(key).isJsonPrimitive()) {
                return object.get(key).getAsInt();
            }
        } catch (RuntimeException ignored) {
            // fall through
        }

        return 0;
    }
}

package dev.shadowcosmetics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Online discovery and local PNG import. Searches Wikimedia Commons' public API;
 * only PNG images are offered for download. No Minecraft server packets are used.
 */
public final class CosmeticLibrary {
    public record Result(String title, String imageUrl, String mime) {}

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private CosmeticLibrary() {}

    public static Path libraryDirectory() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve("shadowcosmetics").resolve("cosmetics");
    }


    /** Searches a public Minecraft cape catalogue and then Wikimedia Commons as a fallback. */
    public static List<Result> searchOnline(String query) throws IOException, InterruptedException {
        List<Result> combined = new ArrayList<>();
        try {
            combined.addAll(searchCapeCatalogue(query));
        } catch (IOException ignored) {
            // Continue with the independent Commons search if the catalogue is unavailable.
        }
        try {
            combined.addAll(searchCommons(query));
        } catch (IOException ignored) {
            if (combined.isEmpty()) throw ignored;
        }
        List<Result> unique = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (Result result : combined) {
            if (seen.add(result.imageUrl())) unique.add(result);
            if (unique.size() == 12) break;
        }
        return unique;
    }

    private static List<Result> searchCapeCatalogue(String query) throws IOException, InterruptedException {
        String encoded = URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        URI uri = URI.create("https://mc-api.bisai.dev/v1/capes?search=" + encoded + "&limit=12&sort=popular");
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                .header("User-Agent", "ShadowCosmetics/1.0")
                .GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("Cape catalogue HTTP " + response.statusCode());
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        List<Result> results = new ArrayList<>();
        if (!root.has("capes") || !root.get("capes").isJsonArray()) return results;
        for (JsonElement element : root.getAsJsonArray("capes")) {
            JsonObject cape = element.getAsJsonObject();
            String name = cape.has("name") ? cape.get("name").getAsString() : "Community Cape";
            String imageUrl = cape.has("url") ? cape.get("url").getAsString() : "";
            if (imageUrl.startsWith("/")) imageUrl = "https://mc-api.bisai.dev" + imageUrl;
            if (imageUrl.startsWith("https://")) results.add(new Result("[Cape Gallery] " + name, imageUrl, "image/png"));
        }
        return results;
    }

    public static List<Result> searchCommons(String query) throws IOException, InterruptedException {
        String encoded = URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        String url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch="
                + encoded + "&gsrnamespace=6&gsrlimit=20&prop=imageinfo&iiprop=url%7Cmime%7Csize&format=json";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "ShadowCosmetics/1.0 (Minecraft Fabric cosmetic browser)")
                .GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("Search service returned HTTP " + response.statusCode());

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonObject pages = root.has("query") && root.getAsJsonObject("query").has("pages")
                ? root.getAsJsonObject("query").getAsJsonObject("pages") : new JsonObject();
        List<Result> results = new ArrayList<>();
        for (var entry : pages.entrySet()) {
            JsonObject page = entry.getValue().getAsJsonObject();
            if (!page.has("imageinfo") || page.getAsJsonArray("imageinfo").isEmpty()) continue;
            JsonObject info = page.getAsJsonArray("imageinfo").get(0).getAsJsonObject();
            String mime = info.has("mime") ? info.get("mime").getAsString() : "";
            String imageUrl = info.has("url") ? info.get("url").getAsString() : "";
            int width = info.has("width") ? info.get("width").getAsInt() : 0;
            int height = info.has("height") ? info.get("height").getAsInt() : 0;
            if ("image/png".equalsIgnoreCase(mime) && imageUrl.startsWith("https://")
                    && width == 64 && (height == 32 || height == 64)) {
                results.add(new Result(page.get("title").getAsString(), imageUrl, mime));
            }
        }
        results.sort(Comparator.comparing(Result::title, String.CASE_INSENSITIVE_ORDER));
        return results;
    }

    public static Path download(Result result) throws IOException, InterruptedException {
        if (!"image/png".equalsIgnoreCase(result.mime()) || !result.imageUrl().startsWith("https://"))
            throw new IOException("Only HTTPS PNG images are supported.");
        HttpRequest request = HttpRequest.newBuilder(URI.create(result.imageUrl()))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "ShadowCosmetics/1.0")
                .GET().build();
        HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) throw new IOException("Download failed: HTTP " + response.statusCode());
        return savePng(response.body(), safeName(result.title()));
    }

    public static List<Path> listLocalPngs(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) throw new IOException("Folder not found or not accessible.");
        try (var stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".png"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .limit(40).toList();
        }
    }

    public static Path importPng(Path source) throws IOException {
        if (!Files.isRegularFile(source)) throw new IOException("File not found.");
        if (Files.size(source) > 8L * 1024 * 1024) throw new IOException("Maximum file size is 8 MB.");
        byte[] bytes = Files.readAllBytes(source);
        return savePng(bytes, safeName(source.getFileName().toString()));
    }

    private static Path savePng(byte[] bytes, String name) throws IOException {
        if (bytes.length < 24 || bytes.length > 8 * 1024 * 1024
                || bytes[0] != (byte) 0x89 || bytes[1] != 0x50 || bytes[2] != 0x4E
                || bytes[3] != 0x47 || bytes[4] != 0x0D || bytes[5] != 0x0A
                || bytes[6] != 0x1A || bytes[7] != 0x0A) {
            throw new IOException("Invalid PNG file (maximum 8 MB).");
        }
        int width = readInt(bytes, 16), height = readInt(bytes, 20);
        if (width != 64 || (height != 32 && height != 64))
            throw new IOException("Cape PNG must be 64x32 or 64x64 pixels.");
        Path dir = libraryDirectory();
        Files.createDirectories(dir);
        Path target = dir.resolve(name.endsWith(".png") ? name : name + ".png").normalize();
        if (!target.getParent().equals(dir.normalize())) throw new IOException("Invalid file name.");
        Files.write(target, bytes);
        return target;
    }

    private static int readInt(byte[] b, int i) {
        return ((b[i] & 255) << 24) | ((b[i + 1] & 255) << 16)
                | ((b[i + 2] & 255) << 8) | (b[i + 3] & 255);
    }

    private static String safeName(String value) {
        String name = value.replaceFirst("(?i)^File:", "").replaceAll("[^a-zA-Z0-9._-]", "_");
        if (name.length() > 64) name = name.substring(0, 64);
        return name.isBlank() ? "custom_cosmetic.png" : name;
    }
}

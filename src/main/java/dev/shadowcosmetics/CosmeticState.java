package dev.shadowcosmetics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Local-only cosmetic selections. No custom packets are sent to a server. */
public final class CosmeticState {
    public static boolean capeEnabled = true;
    public static boolean hatEnabled = true;
    public static boolean wingsEnabled = false;
    public static boolean haloEnabled = false;

    public static int capeIndex = 0;
    public static int hatIndex = 0;
    public static String customCapePath = loadCapePath();
    public static boolean customCapeEnabled = !customCapePath.isBlank();

    public static void setCustomCape(Path path) {
        customCapePath = path.toAbsolutePath().normalize().toString();
        customCapeEnabled = true;
        try {
            CosmeticTextureManager.invalidate();
            Path config = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                    .resolve("shadowcosmetics");
            Files.createDirectories(config);
            Files.writeString(config.resolve("active-cape.txt"), customCapePath);
        } catch (IOException ignored) { }
    }

    private static String loadCapePath() {
        try {
            Path config = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                    .resolve("shadowcosmetics").resolve("active-cape.txt");
            return Files.exists(config) ? Files.readString(config).trim() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    public static final String[] CAPES = {
            "Void Flame", "Nebula Drift", "Amethyst Pulse", "Nightfall", "Frostbyte", "Inferno"
    };
    public static final String[] HATS = {
            "Shadow Crown", "Purple Beanie", "Devil Horns", "Halo", "Top Hat"
    };

    private CosmeticState() {}
}

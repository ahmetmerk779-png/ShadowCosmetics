package dev.shadowcosmetics;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Local-only cosmetic selections. No custom packets are sent to a server.
 * Every setting is saved to {@code config/shadowcosmetics/settings.properties}
 * and restored on the next launch.
 */
public final class CosmeticState {

    public static final String[] CAPES = {
            "Void Flame", "Nebula Drift", "Amethyst Pulse",
            "Nightfall", "Frostbyte", "Inferno"
    };

    public static final String[] HATS = {
            "Shadow Crown", "Purple Beanie", "Devil Horns",
            "Wizard Hat", "Top Hat"
    };

    /** Accent colours (ARGB). Index 0 means "use each style's own colour". */
    public static final int[] ACCENTS = {
            0,
            0xFF8B5CF6, // violet
            0xFFDC2626, // crimson
            0xFF38BDF8, // azure
            0xFF22C55E, // emerald
            0xFFF59E0B, // gold
            0xFFF472B6, // rose
            0xFFF1F5F9  // snow
    };

    /** Master switch: when off, nothing is drawn. */
    public static volatile boolean masterEnabled = true;

    public static volatile boolean capeEnabled = true;
    public static volatile boolean hatEnabled = true;
    public static volatile boolean wingsEnabled = false;
    public static volatile boolean haloEnabled = false;
    public static volatile boolean animationsEnabled = true;

    public static volatile int capeIndex = 0;
    public static volatile int hatIndex = 0;
    public static volatile int accentIndex = 0;

    public static volatile String customCapePath = "";
    public static volatile boolean customCapeEnabled = false;

    static {
        load();
    }

    private CosmeticState() {
    }

    // ------------------------------------------------------------------
    // Custom cape
    // ------------------------------------------------------------------

    /** Equips a cape PNG and makes sure cosmetics and the cape are switched on. */
    public static void setCustomCape(Path path) {
        customCapePath = path.toAbsolutePath().normalize().toString();
        customCapeEnabled = true;
        capeEnabled = true;
        masterEnabled = true;

        CosmeticTextureManager.invalidate();
        save();
    }

    /** Removes the custom cape and returns to the built-in animated capes. */
    public static void clearCustomCape() {
        customCapePath = "";
        customCapeEnabled = false;

        CosmeticTextureManager.invalidate();
        save();
    }

    public static boolean hasCustomCape() {
        return customCapePath != null && !customCapePath.isBlank();
    }

    // ------------------------------------------------------------------
    // Helpers used by the UI and the renderer
    // ------------------------------------------------------------------

    public static int accentColor() {
        return ACCENTS[Math.floorMod(accentIndex, ACCENTS.length)];
    }

    public static void cycleCape() {
        capeIndex = (capeIndex + 1) % CAPES.length;
    }

    public static void cycleHat() {
        hatIndex = (hatIndex + 1) % HATS.length;
    }

    public static void cycleAccent() {
        accentIndex = (accentIndex + 1) % ACCENTS.length;
    }

    /** Restores every option to its default and unequips the custom cape. */
    public static void resetDefaults() {
        masterEnabled = true;
        capeEnabled = true;
        hatEnabled = true;
        wingsEnabled = false;
        haloEnabled = false;
        animationsEnabled = true;

        capeIndex = 0;
        hatIndex = 0;
        accentIndex = 0;

        customCapePath = "";
        customCapeEnabled = false;

        CosmeticTextureManager.invalidate();
        save();
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    private static Path configDirectory() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve("shadowcosmetics");
    }

    private static Path settingsFile() {
        return configDirectory().resolve("settings.properties");
    }

    /** Older versions only stored the cape path in this file. */
    private static Path legacyCapeFile() {
        return configDirectory().resolve("active-cape.txt");
    }

    public static synchronized void save() {
        Properties p = new Properties();

        p.setProperty("masterEnabled", String.valueOf(masterEnabled));
        p.setProperty("capeEnabled", String.valueOf(capeEnabled));
        p.setProperty("hatEnabled", String.valueOf(hatEnabled));
        p.setProperty("wingsEnabled", String.valueOf(wingsEnabled));
        p.setProperty("haloEnabled", String.valueOf(haloEnabled));
        p.setProperty("animationsEnabled", String.valueOf(animationsEnabled));

        p.setProperty("capeIndex", String.valueOf(capeIndex));
        p.setProperty("hatIndex", String.valueOf(hatIndex));
        p.setProperty("accentIndex", String.valueOf(accentIndex));

        p.setProperty("customCapePath", customCapePath);
        p.setProperty("customCapeEnabled", String.valueOf(customCapeEnabled));

        try {
            CosmeticSettings.write(settingsFile(), p);
        } catch (IOException | RuntimeException ignored) {
            // A read-only config folder must never crash the game.
        }
    }

    private static synchronized void load() {
        try {
            Path file = settingsFile();

            if (!Files.isRegularFile(file)) {
                migrateLegacy();
                return;
            }

            Properties p = CosmeticSettings.read(file);

            masterEnabled = CosmeticSettings.getBoolean(p, "masterEnabled", true);
            capeEnabled = CosmeticSettings.getBoolean(p, "capeEnabled", true);
            hatEnabled = CosmeticSettings.getBoolean(p, "hatEnabled", true);
            wingsEnabled = CosmeticSettings.getBoolean(p, "wingsEnabled", false);
            haloEnabled = CosmeticSettings.getBoolean(p, "haloEnabled", false);
            animationsEnabled = CosmeticSettings.getBoolean(p, "animationsEnabled", true);

            capeIndex = CosmeticSettings.getInt(p, "capeIndex", 0, 0, CAPES.length - 1);
            hatIndex = CosmeticSettings.getInt(p, "hatIndex", 0, 0, HATS.length - 1);
            accentIndex = CosmeticSettings.getInt(p, "accentIndex", 0, 0, ACCENTS.length - 1);

            customCapePath = CosmeticSettings.getString(p, "customCapePath", "");
            customCapeEnabled = CosmeticSettings.getBoolean(p, "customCapeEnabled", false)
                    && hasCustomCape();

        } catch (RuntimeException ignored) {
            // Keep defaults when anything is wrong with the saved file.
        }
    }

    private static void migrateLegacy() {
        try {
            Path legacy = legacyCapeFile();

            if (Files.isRegularFile(legacy)) {
                customCapePath = Files.readString(legacy).trim();
                customCapeEnabled = hasCustomCape();
            }
        } catch (IOException | RuntimeException ignored) {
            customCapePath = "";
            customCapeEnabled = false;
        }
    }
}

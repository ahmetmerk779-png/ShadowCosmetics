package dev.shadowcosmetics;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Reads and writes the small settings file. No Minecraft dependencies.
 * Writes are atomic so a crash can never leave a half written file.
 */
public final class CosmeticSettings {

    private CosmeticSettings() {
    }

    /** Loads a properties file; a missing or unreadable file gives empty properties. */
    public static Properties read(Path file) {
        Properties properties = new Properties();

        if (file == null || !Files.isRegularFile(file)) {
            return properties;
        }

        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException | IllegalArgumentException ignored) {
            return new Properties();
        }

        return properties;
    }

    /** Writes the properties atomically (temporary file, then move). */
    public static void write(Path file, Properties properties)
            throws IOException {

        Path parent = file.toAbsolutePath().getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");

        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, "Shadow Cosmetics settings");
        }

        try {
            Files.move(
                    temporary,
                    file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(
                    temporary,
                    file,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    public static boolean getBoolean(
            Properties properties,
            String key,
            boolean fallback
    ) {
        String value = properties.getProperty(key);

        if (value == null) {
            return fallback;
        }

        value = value.trim();

        if (value.equalsIgnoreCase("true")) {
            return true;
        }

        if (value.equalsIgnoreCase("false")) {
            return false;
        }

        return fallback;
    }

    /** Reads an int and clamps it to [min, max]; bad values give the fallback. */
    public static int getInt(
            Properties properties,
            String key,
            int fallback,
            int min,
            int max
    ) {
        String value = properties.getProperty(key);

        if (value == null) {
            return fallback;
        }

        try {
            int parsed = Integer.parseInt(value.trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public static String getString(
            Properties properties,
            String key,
            String fallback
    ) {
        String value = properties.getProperty(key);
        return value == null ? fallback : value.trim();
    }
}

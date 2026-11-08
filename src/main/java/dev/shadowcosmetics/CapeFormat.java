package dev.shadowcosmetics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;

/**
 * Pure helpers for cape PNG validation and file naming.
 * This class intentionally has no Minecraft dependencies so it can be unit tested.
 */
public final class CapeFormat {

    public static final int MAX_FILE_SIZE = 8 * 1024 * 1024;

    /** Largest accepted width (a 32x HD cape). Keeps GPU memory use reasonable. */
    public static final int MAX_WIDTH = 2048;

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };

    private CapeFormat() {
    }

    /** True when the bytes start with the 8-byte PNG signature. */
    public static boolean isPng(byte[] data) {
        if (data == null || data.length < PNG_SIGNATURE.length) {
            return false;
        }

        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (data[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }

        return true;
    }

    /**
     * Reads the image size from the PNG IHDR chunk without decoding the image.
     *
     * @return {width, height} or null when the header is not a valid PNG header
     */
    public static int[] readPngSize(byte[] data) {
        if (!isPng(data) || data.length < 24) {
            return null;
        }

        boolean ihdr = data[12] == 'I' && data[13] == 'H'
                && data[14] == 'D' && data[15] == 'R';

        if (!ihdr) {
            return null;
        }

        int width = readInt(data, 16);
        int height = readInt(data, 20);

        if (width <= 0 || height <= 0) {
            return null;
        }

        return new int[]{width, height};
    }

    /**
     * Accepts the classic 64x32 and 64x64 cape atlases plus HD versions of them
     * (128x64, 128x128, 256x128 ... up to {@link #MAX_WIDTH}).
     */
    public static boolean isValidSize(int width, int height) {
        if (width < 64 || width > MAX_WIDTH || width % 64 != 0) {
            return false;
        }

        return height == width / 2 || height == width;
    }

    /**
     * Height of the model's logical texture grid. The model always works in a
     * 64 pixel wide grid, so HD textures only change the scale factor.
     * Returns 32 for half-height atlases and 64 for square atlases.
     */
    public static int logicalHeight(int width, int height) {
        if (width <= 0) {
            return 32;
        }

        return Math.max(1, (int) ((long) height * 64L / width));
    }

    /** Human readable size such as "64x32", or "?" when unknown. */
    public static String sizeLabel(int[] size) {
        return size == null ? "?" : size[0] + "x" + size[1];
    }

    /** Turns any title or file name into a safe, short file name without extension. */
    public static String baseName(String value) {
        if (value == null || value.isBlank()) {
            return "cape";
        }

        String name = value.replaceFirst("(?i)^File:", "")
                .replaceAll("(?i)\\.png$", "")
                .replace('ı', 'i')
                .replace('İ', 'I');

        name = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        name = name.replaceAll("[^a-zA-Z0-9._-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[._-]+", "")
                .replaceAll("[._-]+$", "");

        if (name.length() > 48) {
            name = name.substring(0, 48);
        }

        return name.isEmpty() ? "cape" : name;
    }

    /**
     * Builds the library file name: a readable base plus a short content hash.
     * Identical files share one name, different files never overwrite each other.
     */
    public static String libraryFileName(String title, byte[] contents) {
        return baseName(title) + "_" + shortHash(contents) + ".png";
    }

    /** First 8 hex characters of the SHA-256 of the data. */
    public static String shortHash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    data == null ? new byte[0] : data
            );

            StringBuilder out = new StringBuilder();

            for (int i = 0; i < 4; i++) {
                out.append(String.format("%02x", hash[i]));
            }

            return out.toString();

        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 is guaranteed on every JVM.
            throw new IllegalStateException(exception);
        }
    }

    /** Shortens text to the given length, keeping the end ("...tail"). */
    public static String ellipsizeStart(String text, int max) {
        if (text == null || text.length() <= max || max < 4) {
            return text == null ? "" : text;
        }

        return "..." + text.substring(text.length() - (max - 3));
    }

    /** Shortens text to the given length, keeping the start ("head..."). */
    public static String ellipsizeEnd(String text, int max) {
        if (text == null || text.length() <= max || max < 2) {
            return text == null ? "" : text;
        }

        return text.substring(0, max - 1) + "…";
    }

    /** File size such as "812 B", "14 KB" or "1.2 MB". */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }

        if (bytes < 1024L * 1024L) {
            return (bytes / 1024) + " KB";
        }

        return String.format(
                java.util.Locale.ROOT,
                "%.1f MB",
                bytes / (1024.0 * 1024.0)
        );
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}

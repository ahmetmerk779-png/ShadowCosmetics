package dev.shadowcosmetics;

/** Small ARGB colour helpers (no Minecraft dependencies). */
public final class Colors {

    private Colors() {
    }

    /** Uses the accent colour when one is chosen (non-zero), otherwise the style default. */
    public static int resolve(int styleDefault, int accent) {
        return accent != 0 ? accent : styleDefault;
    }

    /** Blends {@code color} towards white. {@code amount} 0 = unchanged, 1 = white. */
    public static int lighten(int color, float amount) {
        return mix(color, 0xFFFFFFFF, amount);
    }

    /** Blends {@code color} towards black. {@code amount} 0 = unchanged, 1 = black. */
    public static int darken(int color, float amount) {
        return mix(color, 0xFF000000, amount);
    }

    /** Linear blend of two ARGB colours. The result is always fully opaque. */
    public static int mix(int from, int to, float amount) {
        float t = Math.max(0.0F, Math.min(1.0F, amount));

        int r = blend((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = blend((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = blend(from & 0xFF, to & 0xFF, t);

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int blend(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }
}

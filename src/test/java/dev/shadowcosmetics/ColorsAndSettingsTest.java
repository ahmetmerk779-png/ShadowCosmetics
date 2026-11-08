package dev.shadowcosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ColorsAndSettingsTest {

    @Test
    void accentOverridesOnlyWhenChosen() {
        assertEquals(0xFF112233, Colors.resolve(0xFF112233, 0));
        assertEquals(0xFFAABBCC, Colors.resolve(0xFF112233, 0xFFAABBCC));
    }

    @Test
    void mixingColours() {
        assertEquals(0xFF000000, Colors.mix(0xFF000000, 0xFFFFFFFF, 0.0F));
        assertEquals(0xFFFFFFFF, Colors.mix(0xFF000000, 0xFFFFFFFF, 1.0F));
        assertEquals(0xFF808080, Colors.mix(0xFF000000, 0xFFFFFFFF, 0.5F));
        // Out-of-range amounts are clamped.
        assertEquals(0xFFFFFFFF, Colors.mix(0xFF000000, 0xFFFFFFFF, 5.0F));
        assertEquals(0xFF000000, Colors.mix(0xFF000000, 0xFFFFFFFF, -5.0F));
    }

    @Test
    void lightenAndDarken() {
        assertEquals(0xFFFFFFFF, Colors.lighten(0xFF123456, 1.0F));
        assertEquals(0xFF000000, Colors.darken(0xFF123456, 1.0F));
        assertEquals(0xFF123456, Colors.lighten(0xFF123456, 0.0F));
    }

    @Test
    void settingsRoundTrip(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("nested").resolve("settings.properties");

        Properties out = new Properties();
        out.setProperty("capeEnabled", "false");
        out.setProperty("capeIndex", "3");
        out.setProperty("customCapePath", "C:\\capes\\my cape.png");

        CosmeticSettings.write(file, out);

        assertTrue(Files.isRegularFile(file));
        assertFalse(Files.exists(directory.resolve("nested").resolve("settings.properties.tmp")));

        Properties in = CosmeticSettings.read(file);

        assertFalse(CosmeticSettings.getBoolean(in, "capeEnabled", true));
        assertEquals(3, CosmeticSettings.getInt(in, "capeIndex", 0, 0, 5));
        assertEquals(
                "C:\\capes\\my cape.png",
                CosmeticSettings.getString(in, "customCapePath", "")
        );
    }

    @Test
    void missingOrBrokenValuesUseFallbacks(@TempDir Path directory) {
        Properties missing = CosmeticSettings.read(directory.resolve("nope.properties"));
        assertTrue(missing.isEmpty());

        Properties p = new Properties();
        p.setProperty("flag", "maybe");
        p.setProperty("number", "abc");
        p.setProperty("big", "999");
        p.setProperty("small", "-4");

        assertTrue(CosmeticSettings.getBoolean(p, "flag", true));
        assertFalse(CosmeticSettings.getBoolean(p, "absent", false));
        assertEquals(2, CosmeticSettings.getInt(p, "number", 2, 0, 5));
        assertEquals(5, CosmeticSettings.getInt(p, "big", 0, 0, 5));
        assertEquals(0, CosmeticSettings.getInt(p, "small", 3, 0, 5));
        assertEquals("fallback", CosmeticSettings.getString(p, "none", "fallback"));
    }
}

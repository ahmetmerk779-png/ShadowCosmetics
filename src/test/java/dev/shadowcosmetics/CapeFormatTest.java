package dev.shadowcosmetics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CapeFormatTest {

    /** Minimal valid PNG header: signature + IHDR chunk with the given size. */
    private static byte[] header(int width, int height) {
        byte[] data = new byte[33];

        byte[] signature = {
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
        };

        System.arraycopy(signature, 0, data, 0, 8);

        data[8] = 0;
        data[9] = 0;
        data[10] = 0;
        data[11] = 13;

        data[12] = 'I';
        data[13] = 'H';
        data[14] = 'D';
        data[15] = 'R';

        putInt(data, 16, width);
        putInt(data, 20, height);

        return data;
    }

    private static void putInt(byte[] data, int offset, int value) {
        data[offset] = (byte) (value >>> 24);
        data[offset + 1] = (byte) (value >>> 16);
        data[offset + 2] = (byte) (value >>> 8);
        data[offset + 3] = (byte) value;
    }

    @Test
    void acceptsClassicAndHdCapeSizes() {
        assertTrue(CapeFormat.isValidSize(64, 32));
        assertTrue(CapeFormat.isValidSize(64, 64));
        assertTrue(CapeFormat.isValidSize(128, 64));
        assertTrue(CapeFormat.isValidSize(128, 128));
        assertTrue(CapeFormat.isValidSize(512, 256));
        assertTrue(CapeFormat.isValidSize(2048, 1024));
    }

    @Test
    void rejectsOtherSizes() {
        assertFalse(CapeFormat.isValidSize(46, 22));
        assertFalse(CapeFormat.isValidSize(64, 48));
        assertFalse(CapeFormat.isValidSize(32, 16));
        assertFalse(CapeFormat.isValidSize(100, 50));
        assertFalse(CapeFormat.isValidSize(4096, 2048));
        assertFalse(CapeFormat.isValidSize(0, 0));
        assertFalse(CapeFormat.isValidSize(-64, -32));
        assertFalse(CapeFormat.isValidSize(64, 128));
    }

    @Test
    void logicalHeightKeepsA64WideGrid() {
        assertEquals(32, CapeFormat.logicalHeight(64, 32));
        assertEquals(64, CapeFormat.logicalHeight(64, 64));
        assertEquals(32, CapeFormat.logicalHeight(256, 128));
        assertEquals(64, CapeFormat.logicalHeight(256, 256));
    }

    @Test
    void detectsPngSignature() {
        assertTrue(CapeFormat.isPng(header(64, 32)));
        assertFalse(CapeFormat.isPng(null));
        assertFalse(CapeFormat.isPng(new byte[0]));
        assertFalse(CapeFormat.isPng(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}));
        assertFalse(CapeFormat.isPng("GIF89a-not-a-png".getBytes()));
    }

    @Test
    void readsSizeFromHeader() {
        assertArrayEquals(
                new int[]{64, 32},
                CapeFormat.readPngSize(header(64, 32))
        );

        assertArrayEquals(
                new int[]{1024, 512},
                CapeFormat.readPngSize(header(1024, 512))
        );
    }

    @Test
    void badHeadersGiveNoSize() {
        assertNull(CapeFormat.readPngSize(null));
        assertNull(CapeFormat.readPngSize(new byte[10]));
        assertNull(CapeFormat.readPngSize(header(0, 0)));

        byte[] wrongChunk = header(64, 32);
        wrongChunk[12] = 'X';
        assertNull(CapeFormat.readPngSize(wrongChunk));
    }

    @Test
    void baseNameIsSafe() {
        assertEquals("cape", CapeFormat.baseName(null));
        assertEquals("cape", CapeFormat.baseName("   "));
        assertEquals("cape", CapeFormat.baseName("###"));
        assertEquals("My_Cape", CapeFormat.baseName("File:My Cape.png"));
        assertEquals("Muge_cape", CapeFormat.baseName("Müge cape"));
        assertEquals("Isik_Sagi", CapeFormat.baseName("Işık Sağı"));
        // Path traversal can never survive: separators and leading dots are removed.
        assertEquals("etc_passwd", CapeFormat.baseName("../../etc/passwd"));
        assertFalse(CapeFormat.baseName("a/b\\c:d*e?f").matches(".*[/\\\\:*?].*"));
        assertEquals(48, CapeFormat.baseName("x".repeat(200)).length());
    }

    @Test
    void libraryNamesAreStableAndContentBased() {
        byte[] a = CapeFormat.utf8("first");
        byte[] b = CapeFormat.utf8("second");

        String nameA1 = CapeFormat.libraryFileName("Cool Cape", a);
        String nameA2 = CapeFormat.libraryFileName("Cool Cape", a);
        String nameB = CapeFormat.libraryFileName("Cool Cape", b);

        assertEquals(nameA1, nameA2);
        assertNotEquals(nameA1, nameB);
        assertTrue(nameA1.startsWith("Cool_Cape_"));
        assertTrue(nameA1.endsWith(".png"));
        assertEquals(8, CapeFormat.shortHash(a).length());
    }

    @Test
    void ellipsisHelpers() {
        assertEquals("short", CapeFormat.ellipsizeEnd("short", 10));
        assertEquals("abcd…", CapeFormat.ellipsizeEnd("abcdefghij", 5));
        assertEquals("...hij", CapeFormat.ellipsizeStart("abcdefghij", 6));
        assertEquals("", CapeFormat.ellipsizeEnd(null, 5));
    }

    @Test
    void formatsBytes() {
        assertEquals("512 B", CapeFormat.formatBytes(512));
        assertEquals("14 KB", CapeFormat.formatBytes(14 * 1024));
        assertEquals("1.5 MB", CapeFormat.formatBytes(1536 * 1024));
    }

    @Test
    void sizeLabel() {
        assertEquals("64x32", CapeFormat.sizeLabel(new int[]{64, 32}));
        assertEquals("?", CapeFormat.sizeLabel(null));
    }
}

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Dimension;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class TextureManagerTest {

    /**
     * Verifies the test resource exists and its pixels match the expected layout.
     * Resource path: src/test/resources/textures/test2.png
     */
    @Test
    void resourceImageHasExpectedPixels() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/textures/test2.png")) {
            assertNotNull(is, "Place test2.png in src/test/resources/textures/test2.png");
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(is);
            assertNotNull(img, "ImageIO failed to read test2.png");
            assertTrue(img.getWidth() >= 2 && img.getHeight() >= 2, "image must be at least 2x2");

            int rgb00 = img.getRGB(0, 0);
            int rgb10 = img.getRGB(1, 0);
            int rgb01 = img.getRGB(0, 1);
            int rgb11 = img.getRGB(1, 1);

            assertColorEquals(255, 0, 0, rgb00);   // top-left red
            assertColorEquals(0, 255, 0, rgb10);   // top-right green
            assertColorEquals(0, 0, 0, rgb01);     // bottom-left black
            assertColorEquals(0, 0, 250, rgb11);   // bottom-right blue-ish
        }
    }

    /**
     * Tests loadFromResource, hasTexture, getSize, getARGB, getAlpha/getRed/getGreen/getBlue,
     * getColor, getPixels, remove and clear.
     */
    @Test
    void textureManagerResourceLoadAndAccessors() throws Exception {
        TextureManager tm = new TextureManager();

        // initial state checks for a missing texture
        assertFalse(tm.hasTexture("test2"));
        assertNull(tm.getSize("test2"));
        assertNull(tm.getPixels("test2"));
        assertEquals(0, tm.getARGB("test2", 0, 0));

        // load from resource
        boolean loaded = tm.loadFromResource("test2", "/textures/test2.png");
        assertTrue(loaded, "loadFromResource should return true for valid resource");
        assertTrue(tm.hasTexture("test2"));

        // size
        Dimension size = tm.getSize("test2");
        assertNotNull(size);
        assertTrue(size.width >= 2 && size.height >= 2);

        // component getters for the four pixels
        assertEquals(255, tm.getRed("test2", 0, 0));
        assertEquals(0,   tm.getGreen("test2", 0, 0));
        assertEquals(0,   tm.getBlue("test2", 0, 0));
        assertEquals(255, tm.getAlpha("test2", 0, 0));

        assertEquals(0,   tm.getRed("test2", 1, 0));
        assertEquals(255, tm.getGreen("test2", 1, 0));
        assertEquals(0,   tm.getBlue("test2", 1, 0));

        assertEquals(0,   tm.getRed("test2", 0, 1));
        assertEquals(0,   tm.getGreen("test2", 0, 1));
        assertEquals(0,   tm.getBlue("test2", 0, 1));

        assertEquals(0,   tm.getRed("test2", 1, 1));
        assertEquals(0,   tm.getGreen("test2", 1, 1));
        assertEquals(250, tm.getBlue("test2", 1, 1));

        // getARGB returns packed int; verify components match when unpacked
        int argb00 = tm.getARGB("test2", 0, 0);
        assertNotEquals(0, argb00);
        int r = (argb00 >> 16) & 0xFF;
        int g = (argb00 >> 8) & 0xFF;
        int b = argb00 & 0xFF;
        assertEquals(255, r);
        assertEquals(0, g);
        assertEquals(0, b);

        // getColor returns a Color with same components (alpha included)
        Color c = tm.getColor("test2", 1, 1);
        assertNotNull(c);
        assertEquals(0, c.getRed());
        assertEquals(0, c.getGreen());
        assertEquals(250, c.getBlue());
        assertTrue(c.getAlpha() >= 0 && c.getAlpha() <= 255);

        // getPixels returns internal array; length should match width*height
        int[] pixels = tm.getPixels("test2");
        assertNotNull(pixels);
        assertEquals(size.width * size.height, pixels.length);

        // verify pixel array content matches getARGB for a sample pixel
        int sampleFromArray = pixels[0];
        assertEquals(tm.getARGB("test2", 0, 0), sampleFromArray);

        // out-of-bounds queries return 0
        assertEquals(0, tm.getARGB("test2", -1, 0));
        assertEquals(0, tm.getARGB("test2", 0, -1));
        assertEquals(0, tm.getARGB("test2", size.width, 0));
        assertEquals(0, tm.getARGB("test2", 0, size.height));

        // debug_print should not throw (prints to stderr)
        tm.debug_print("test2");

        // remove and clear
        tm.remove("test2");
        assertFalse(tm.hasTexture("test2"));
        assertNull(tm.getSize("test2"));
        assertNull(tm.getPixels("test2"));

        // reload and then clear
        assertTrue(tm.loadFromResource("test2", "/textures/test2.png"));
        assertTrue(tm.hasTexture("test2"));
        tm.clear();
        assertFalse(tm.hasTexture("test2"));
    }

    /**
     * Tests loadFromFile by copying the resource to a temporary file and calling loadFromFile.
     */
    @Test
    void loadFromFileWorksWithFilesystemPath() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/textures/test2.png")) {
            assertNotNull(is, "resource must exist");
            File tmp = Files.createTempFile("test2-copy", ".png").toFile();
            tmp.deleteOnExit();
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = is.read(buf)) != -1) fos.write(buf, 0, r);
            }

            TextureManager tm = new TextureManager();
            boolean ok = tm.loadFromFile("fromFile", tmp.getAbsolutePath());
            assertTrue(ok, "loadFromFile should return true for a valid file");
            assertTrue(tm.hasTexture("fromFile"));

            Dimension size = tm.getSize("fromFile");
            assertNotNull(size);
            assertTrue(size.width >= 2 && size.height >= 2);

            // verify a pixel
            assertEquals(255, tm.getRed("fromFile", 0, 0));
            assertEquals(0, tm.getGreen("fromFile", 0, 0));
            assertEquals(0, tm.getBlue("fromFile", 0, 0));

            // cleanup
            tm.remove("fromFile");
            assertFalse(tm.hasTexture("fromFile"));
        }
    }

    /**
     * When texture is not present, accessors should return null or 0 as documented.
     */
    @Test
    void missingTextureAccessorsReturnNullOrZero() {
        TextureManager tm = new TextureManager();
        assertFalse(tm.hasTexture("nope"));
        assertNull(tm.getSize("nope"));
        assertNull(tm.getPixels("nope"));
        assertEquals(0, tm.getARGB("nope", 0, 0));
        assertEquals(0, tm.getAlpha("nope", 0, 0));
        assertEquals(0, tm.getRed("nope", 0, 0));
        assertEquals(0, tm.getGreen("nope", 0, 0));
        assertEquals(0, tm.getBlue("nope", 0, 0));
    }


    // helper to compare RGB components (ignores alpha)
    private static void assertColorEquals(int expectedR, int expectedG, int expectedB, int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        assertEquals(expectedR, r, "Red component mismatch");
        assertEquals(expectedG, g, "Green component mismatch");
        assertEquals(expectedB, b, "Blue component mismatch");
    }
}

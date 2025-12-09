import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight texture container and loader.
 *
 * <p>The manager stores textures as packed ARGB {@code int[]} arrays for fast
 * pixel access during rendering. It supports loading from the filesystem and
 * from classpath resources. The API provides both per-pixel accessors and
 * direct access to the underlying pixel array for performance-critical code.</p>
 */
public class TextureManager {
    private static class Texture {
        final int[] pixels;
        final int w;
        final int h;

        Texture(int[] pixels, int w, int h) {
            this.pixels = pixels;
            this.w = w;
            this.h = h;
        }
    }

    private final Map<String, Texture> textures = new HashMap<>();

    /** Create an empty TextureManager. */
    public TextureManager() {}

    /**
     * Load a texture from a filesystem path.
     *
     * @param name logical name to store the texture under
     * @param path filesystem path to the image file
     * @return true on success, false on failure
     */
    public boolean loadFromFile(String name, String path) {
        try {
            BufferedImage img = ImageIO.read(new File(path));
            if (img == null) return false;
            storeTexture(name, img);
            return true;
        } catch (Exception e) {
            System.err.println("Texture load error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Load a texture from a classpath resource.
     *
     * @param name logical name to store the texture under
     * @param resourcePath classpath resource path (e.g. "/textures/wall.png")
     * @return true on success, false on failure
     */
    public boolean loadFromResource(String name, String resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) return false;
            BufferedImage img = ImageIO.read(is);
            if (img == null) return false;
            storeTexture(name, img);
            return true;
        } catch (Exception e) {
            System.err.println("Texture load error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Convert a BufferedImage into the internal packed ARGB pixel array and store it.
     *
     * @param name logical name for the texture
     * @param img image to store
     */
    private void storeTexture(String name, BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] pixels = new int[w * h];
        // bulk copy (fast)
        img.getRGB(0, 0, w, h, pixels, 0, w);
        textures.put(name, new Texture(pixels, w, h));
    }

    /**
     * Get packed ARGB pixel value at coordinates (x,y).
     *
     * @param name texture name
     * @param x x coordinate
     * @param y y coordinate
     * @return packed ARGB int or 0 if texture not found or coordinates out of range
     */
    public int getARGB(String name, int x, int y) {
        Texture t = textures.get(name);
        if (t == null) return 0;
        if (x < 0 || x >= t.w || y < 0 || y >= t.h) return 0;
        return t.pixels[y * t.w + x];
    }

    /**
     * Get alpha component (0..255) of the pixel at (x,y).
     *
     * @param name texture name
     * @param x x coordinate
     * @param y y coordinate
     * @return alpha component or 0 if not available
     */
    public int getAlpha(String name, int x, int y) {
        return (getARGB(name, x, y) >>> 24) & 0xFF;
    }

    /**
     * Get red component (0..255) of the pixel at (x,y).
     *
     * @param name texture name
     * @param x x coordinate
     * @param y y coordinate
     * @return red component or 0 if not available
     */
    public int getRed(String name, int x, int y) {
        return (getARGB(name, x, y) >> 16) & 0xFF;
    }

    /**
     * Get green component (0..255) of the pixel at (x,y).
     *
     * @param name texture name
     * @param x x coordinate
     * @param y y coordinate
     * @return green component or 0 if not available
     */
    public int getGreen(String name, int x, int y) {
        return (getARGB(name, x, y) >> 8) & 0xFF;
    }

    /**
     * Get blue component (0..255) of the pixel at (x,y).
     *
     * @param name texture name
     * @param x x coordinate
     * @param y y coordinate
     * @return blue component or 0 if not available
     */
    public int getBlue(String name, int x, int y) {
        return (getARGB(name, x, y)) & 0xFF;
    }

    /**
     * Convenience method that returns a {@link Color} instance for the pixel.
     *
     * <p>Allocates a new Color object and should be used sparingly in
     * performance-sensitive code.</p>
     *
     * @param name texture name
     * @param x x coordinate
     * @param y y coordinate
     * @return Color instance (alpha included) or a default color if not available
     */
    public Color getColor(String name, int x, int y) {
        int argb = getARGB(name, x, y);
        return new Color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24));
    }

    /**
     * Return the internal packed pixel array for direct access.
     *
     * <p>Do not modify the returned array; it is shared with the manager.</p>
     *
     * @param name texture name
     * @return pixel array or null if texture not found
     */
    public int[] getPixels(String name) {
        Texture t = textures.get(name);
        return t == null ? null : t.pixels;
    }

    /**
     * Get the stored texture size.
     *
     * @param name texture name
     * @return Dimension with width and height or null if texture not found
     */
    public Dimension getSize(String name) {
        Texture t = textures.get(name);
        return t == null ? null : new Dimension(t.w, t.h);
    }

    /**
     * Check whether a texture with the given name exists.
     *
     * @param name texture name
     * @return true if present
     */
    public boolean hasTexture(String name) {
        return textures.containsKey(name);
    }

    /**
     * Remove a texture from the manager.
     *
     * @param name texture name to remove
     */
    public void remove(String name) {
        textures.remove(name);
    }

    /** Remove all textures from the manager. */
    public void clear() {
        textures.clear();
    }

    /**
     * Debug helper that prints ARGB components for every pixel of a texture.
     *
     * <p>Useful during development to verify that images are loaded correctly.</p>
     *
     * @param name texture name
     */
    public void debug_print(String name) {
        Texture t = textures.get(name);
        if (t == null) {
            System.err.println("Texture not found: " + name);
            return;
        }
        for (int y = 0; y < t.h; y++) {
            for (int x = 0; x < t.w; x++) {
                int argb = t.pixels[y * t.w + x];
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                System.out.printf("(%d,%d) A:%d R:%d G:%d B:%d%n", x, y, a, r, g, b);
            }
        }
    }
}

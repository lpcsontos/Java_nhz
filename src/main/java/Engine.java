import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;

/**
 * Raycasting game engine panel.
 *
 * <p>This class implements a simple software raycaster that renders a 2D tile
 * map into a first-person view. It handles input, timing, saving and loading
 * via {@link SaveManager}, and basic overlay UI for saving the game.</p>
 *
 * <p>The panel is intended to be embedded inside a Swing container and does
 * not create its own top-level window.</p>
 */
public class Engine extends JPanel implements KeyListener {
    private int sW;
    private int sH;
    private int mW = 8;
    private int mH = 8;
    private int bS = 64;
    private double rDp = 200;
    private double pX = 100, pY = 100, pA = 0;
    private double timeLeft = 300;

    private final int skyColor = 0xFF87CEEB;
    private final int floorColor = 0xFF444444;
    private final int fogColor  = 0xFF000000;

    private final JButton saveOverlayButton = new JButton("Save Game");
    private boolean controlsEnabled = true;
    private final SaveManager saveManager;
    private Player player;
    private final Path timeFile;
    private boolean finished = false;

    private int[][] map = {
            {1,1,1,1,1,1,1,1},
            {1,0,0,0,0,0,0,1},
            {1,0,1,0,1,0,0,1},
            {1,0,1,0,1,1,0,1},
            {1,0,0,0,1,0,0,1},
            {1,0,1,1,1,1,0,1},
            {1,5,0,0,0,0,0,1},
            {1,1,1,1,1,1,1,1}
    };

    // rendering buffers & texture data
    private BufferedImage screenBuf = null;
    private int[] screenPixels = null;

    private final TextureManager texMan = new TextureManager();
    private int[] wallTexPixels = null;
    private int wallTexW = 0, wallTexH = 0;

    /**
     * Default constructor that delegates to the main constructor using the default save path.
     */
    public Engine() {
        this(null, Paths.get("savegame.txt"));
    }

    /**
     * Create an Engine instance optionally restoring from a {@link SaveState}.
     *
     * @param state optional save state to restore player position, time and map
     * @param saveFile path used for saving and the time file
     */
    public Engine(SaveState state, Path saveFile) {
        this.saveManager = new SaveManager(saveFile);
        this.player = new Player();

        this.timeFile = saveFile;

        if (state != null) {
            // restore simple state from save
            this.pX = state.player.getX();
            this.pY = state.player.getY();
            this.pA = state.player.getAngle();
            player.setX(state.player.getX());
            player.setY(state.player.getY());
            player.setAngle(state.player.getAngle());
            player.setHp(state.player.getHp());
            this.timeLeft = state.timeLeft;
            map = state.map;
        }

        // keep your existing initialization (size, textures, timers) but remove the frame creation
        sW = 640; sH = 480;
        setPreferredSize(new Dimension(sW, sH));
        setBackground(Color.BLACK);

        LoadTextures();

        setFocusable(true);
        addKeyListener(this);

        // overlay save button setup
        saveOverlayButton.setVisible(false);
        saveOverlayButton.setFocusable(false);
        saveOverlayButton.addActionListener(e -> doSaveInBackground());

        setLayout(null);
        add(saveOverlayButton);

        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                sW = getWidth(); sH = getHeight();
                positionOverlay();
            }
        });

        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        int refreshRate = gd.getDisplayMode().getRefreshRate();
        final int delay = 16;
        //if (refreshRate != DisplayMode.REFRESH_RATE_UNKNOWN) delay = Math.max(1, 1000 / refreshRate);

        Timer timer = new Timer(delay, e -> {
            // tick time (example)
            timeLeft = Math.max(0.0, timeLeft - delay / 1000.0);
            repaint();
        });
        timer.start();
    }

    /**
     * Load textures required by the renderer.
     *
     * <p>On failure the engine will fall back to a procedural/solid shading.</p>
     */
    private void LoadTextures(){
        boolean ok = texMan.loadFromFile("wall1", "textures/test.png");
        if (!ok) {
            System.err.println("Failed to load wall.png, using procedural texture");
        } else {
            Dimension d = texMan.getSize("wall1");
            if (d != null) {
                wallTexW = d.width;
                wallTexH = d.height;
                wallTexPixels = texMan.getPixels("wall1");
            }
        }
    }

    /** Get current screen height in pixels. */
    public int getScreenHeight() { return sH; }
    /** Get current screen width in pixels. */
    public int getScreenWidth() { return sW; }

    /**
     * Set the renderer's maximum render distance.
     *
     * @param rdp render distance in world units
     */
    public void setRenderdistance(int rdp){
        this.rDp = rdp;
    }

    /**
     * Set the map width (number of tiles).
     *
     * @param mw map width in tiles
     */
    public void setMapwidth(int mw){
        this.mW = mw;
    }

    /**
     * Set the map height (number of tiles).
     *
     * @param mh map height in tiles
     */
    public void setMapheight(int mh){
        this.mH = mh;
    }

    /** Get the current render distance. */
    public double getRenderdistance(){
        return rDp;
    }

    private static int clamp(int v) { if (v < 0) return 0; if (v > 255) return 255; return v; }

    /**
     * Main rendering routine. Paints sky, floor and walls using a simple raycasting algorithm.
     *
     * <p>The method writes directly into a backing {@link BufferedImage} pixel array
     * for performance and then draws that image to the component.</p>
     *
     * @param g Graphics context provided by Swing
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int sh = getScreenHeight();
        int sw = getScreenWidth();
        if (sw <= 0 || sh <= 0) return;

        //screen buffer and pixel array
        if (screenBuf == null || screenBuf.getWidth() != sw || screenBuf.getHeight() != sh) {
            screenBuf = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
            screenPixels = ((DataBufferInt) screenBuf.getRaster().getDataBuffer()).getData();
        }

        // clear background: simple sky and floor

        // texture references
        final int[] texPixels = wallTexPixels;
        final int texW = wallTexW;
        final int texH = wallTexH;
        final boolean haveTex = texPixels != null && texW > 0 && texH > 0;

        // projection and ray params
        final double fov = Math.toRadians(60.0);
        final double halfFov = fov * 0.5;
        final double pD = (sw * 0.5) / Math.tan(halfFov);
        final int maxSteps = Math.min((int)(rDp / bS) + 2, Math.max(mW, mH) * 8);
        final double fogStart = Math.max(1.0, rDp * 0.5);

        int mid = sh / 2;
        for (int py = 0; py < sh; py++) {
            double row = Math.abs(py - mid);
            if (row < 1.0) row = 1.0;

            double rowDist = (bS * pD) / row;
            if (rowDist > rDp) rowDist = rDp;

            // fog factor: 0 -> no fog, 1 -> full fog at rDp
            double pixelFogFactor;
            if (rowDist <= fogStart) pixelFogFactor = 0.0;
            else pixelFogFactor = (rowDist - fogStart) / (rDp - fogStart);
            pixelFogFactor = Math.max(0.0, Math.min(1.0, pixelFogFactor));

            // base color selection
            int base = (py < mid) ? skyColor : floorColor;

            int br = (base >> 16) & 0xFF;
            int bg = (base >> 8) & 0xFF;
            int bb = base & 0xFF;
            int fr = (fogColor >> 16) & 0xFF;
            int fg = (fogColor >> 8) & 0xFF;
            int fb = fogColor & 0xFF;

            int rr = (int) (br * (1.0 - pixelFogFactor) + fr * pixelFogFactor);
            int gg = (int) (bg * (1.0 - pixelFogFactor) + fg * pixelFogFactor);
            int bb2 = (int) (bb * (1.0 - pixelFogFactor) + fb * pixelFogFactor);

            int color = (0xFF << 24) | (clamp(rr) << 16) | (clamp(gg) << 8) | clamp(bb2);

            int baseIndex = py * sw;
            for (int x = 0; x < sw; x++) screenPixels[baseIndex + x] = color;
        }

        // ray loop
        for (int sx = 0; sx < sw; sx++) {
            double rA = pA - halfFov + ((double) sx / (double) sw) * fov;
            rA = (rA % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
            double rx = Math.cos(rA);
            double ry = Math.sin(rA);

            // Horizontal DDA
            double hHitX = 0, hHitY = 0;
            double distH = Double.POSITIVE_INFINITY;
            if (Math.abs(ry) > 1e-9) {
                boolean down = ry > 0;
                double stepY = down ? bS : -bS;
                double yIntercept = down ? (Math.floor(pY / bS) * bS + bS) : (Math.floor(pY / bS) * bS - 1e-6);
                double xIntercept = pX + (yIntercept - pY) / ry * rx;
                double deltaX = (stepY / ry) * rx;
                double curX = xIntercept, curY = yIntercept;
                int steps = 0;
                while (steps < maxSteps) {
                    int mapX = ((int) curX) >> 6;
                    int mapY = ((int) curY) >> 6;
                    if (mapX >= 0 && mapX < mW && mapY >= 0 && mapY < mH && map[mapY][mapX] == 1) {
                        hHitX = curX; hHitY = curY;
                        distH = Math.hypot(hHitX - pX, hHitY - pY);
                        break;
                    }
                    curX += deltaX; curY += stepY;
                    steps++;
                }
            }

            // Vertical DDA
            double vHitX = 0, vHitY = 0;
            double distV = Double.POSITIVE_INFINITY;
            if (Math.abs(rx) > 1e-9) {
                boolean right = rx > 0;
                double stepX = right ? bS : -bS;
                double xIntercept = right ? (Math.floor(pX / bS) * bS + bS) : (Math.floor(pX / bS) * bS - 1e-6);
                double yIntercept = pY + (xIntercept - pX) / rx * ry;
                double deltaY = (stepX / rx) * ry;
                double curX = xIntercept, curY = yIntercept;
                int steps = 0;
                while (steps < maxSteps) {
                    int mapX = ((int) curX) >> 6;
                    int mapY = ((int) curY) >> 6;
                    if (mapX >= 0 && mapX < mW && mapY >= 0 && mapY < mH && map[mapY][mapX] == 1) {
                        vHitX = curX; vHitY = curY;
                        distV = Math.hypot(vHitX - pX, vHitY - pY);
                        break;
                    }
                    curX += stepX; curY += deltaY;
                    steps++;
                }
            }

            boolean verticalHit = distV < distH;
            double hitX = verticalHit ? vHitX : hHitX;
            double hitY = verticalHit ? vHitY : hHitY;
            double dist = Math.min(distV, distH);

            if (Double.isInfinite(dist) || dist <= 0 || dist > rDp) continue;

            final double MIN_DIST = 1e-4;
            if (dist < MIN_DIST) dist = MIN_DIST;

            // fisheye correction
            dist *= Math.cos(rA - pA);

            // wall height
            double wallHf = (bS / dist) * pD;
            if (Double.isInfinite(wallHf) || Double.isNaN(wallHf)) wallHf = sh;
            int wallH = (int)Math.round(wallHf);
            if (wallH <= 0) continue;
            if (wallH > sh * 2) wallH = sh * 2;

            double idealTop = (sh / 2.0) - (wallH / 2.0);
            int yTop = (int) Math.max(0, Math.round(idealTop));
            int yBottom = Math.min(sh - 1, yTop + wallH - 1);

            double frac;
            if (verticalHit) {
                double mod = hitY - Math.floor(hitY / bS) * bS; // [0,bS)
                frac = mod / (double) bS;
            } else {
                double mod = hitX - Math.floor(hitX / bS) * bS;
                frac = mod / (double) bS;
            }
            if (frac < 0) frac += 1.0;
            int texX = haveTex ? (int)(frac * texW) : 0;
            if (haveTex) { if (texX < 0) texX = 0; else if (texX >= texW) texX = texW - 1; }

            double texStep = haveTex ? (double) texH / (double) wallH : 0.0;
            double texPos = 0.0;
            if (idealTop < 0) {
                double clipped = -idealTop;
                texPos = clipped * texStep;
            }


            double fogFactor = Math.max(0, Math.min(1, 1.0 - (dist / rDp)*1.5));


            final int col = sx;
            for (int py = yTop; py <= yBottom; py++) {
                int outColor;
                if (haveTex) {
                    int ty = (int) texPos;
                    if (ty < 0) ty = 0; else if (ty >= texH) ty = texH - 1;
                    int argb = texPixels[ty * texW + texX];

                    int a = (argb >>> 24) & 0xFF;
                    int rr = (argb >> 16) & 0xFF;
                    int gg = (argb >> 8) & 0xFF;
                    int bb = argb & 0xFF;

                    int rrr = (int) (rr * fogFactor);
                    int ggg = (int) (gg * fogFactor);
                    int bbb = (int) (bb * fogFactor);
                    if (rrr < 0) rrr = 0; else if (rrr > 255) rrr = 255;
                    if (ggg < 0) ggg = 0; else if (ggg > 255) ggg = 255;
                    if (bbb < 0) bbb = 0; else if (bbb > 255) bbb = 255;
                    outColor = (a << 24) | (rrr << 16) | (ggg << 8) | bbb;
                } else {
                    int gray = (int)(255 * fogFactor);
                    outColor = (0xFF << 24) | (gray << 16) | (gray << 8) | gray;
                }
                screenPixels[py * sw + col] = outColor;
                texPos += texStep;
            }
        }

        g.drawImage(screenBuf, 0, 0, null);
    }

    @Override public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            toggleOverlay();
            return;
        }
        if (!controlsEnabled) return;
        if (e.getKeyCode() == KeyEvent.VK_LEFT) pA -= 0.05;
        if (e.getKeyCode() == KeyEvent.VK_RIGHT) pA += 0.05;
        if (e.getKeyCode() == KeyEvent.VK_UP) {
            double nx = pX + Math.cos(pA) * 5;
            double ny = pY + Math.sin(pA) * 5;

            int mx = ((int)nx) >> 6, my = ((int)ny) >> 6;
            if (mx >= 0 && mx < mW && my >= 0 && my < mH && (map[my][mx] == 0 || map[my][mx] == 5)) { pX = nx; pY = ny; }
        }
        if (e.getKeyCode() == KeyEvent.VK_DOWN) {
            double nx = pX - Math.cos(pA) * 5;
            double ny = pY - Math.sin(pA) * 5;
            int mx = ((int)nx) >> 6, my = ((int)ny) >> 6;
            if (mx >= 0 && mx < mW && my >= 0 && my < mH && (map[my][mx] == 0 || map[my][mx] == 5)) { pX = nx; pY = ny; }
        }

        player.setY(pY);
        player.setX(pX);
        player.setAngle(pA);
        checkForExit();
    }
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}

    /**
     * Check whether the player stepped on an exit tile and trigger level completion.
     */
    private void checkForExit() {
        int mx = ((int)pX) >> 6;
        int my = ((int)pY) >> 6;
        if (mx >= 0 && mx < mW && my >= 0 && my < mH) {
            if (map[my][mx] == 5 && !finished) {
                finished = true;
                onLevelComplete();
            }
        }
    }

    /**
     * Called when the player reaches the exit tile.
     *
     * <p>Writes the remaining time to a map-specific time file and returns to the
     * start menu by creating a new {@link StartWindow} instance.</p>
     */
    private void onLevelComplete() {
        double savedTime = timeLeft;
        Path path = Paths.get("map1/time.txt");
        String content = "" +savedTime;
        try {
            Files.writeString(path, content);
        } catch (IOException e) {
            e.printStackTrace();
        }

        StartWindow.INSTANCE.showMenu();
        new StartWindow();
    }

    /**
     * Position the save overlay button in the center of the panel.
     */
    private void positionOverlay() {
        int bw = 160, bh = 40;
        int x = (getWidth() - bw) / 2;
        int y = (getHeight() - bh) / 2;
        saveOverlayButton.setBounds(x, y, bw, bh);
    }

    /**
     * Toggle visibility of the save overlay and enable/disable controls accordingly.
     */
    private void toggleOverlay() {
        boolean nowVisible = !saveOverlayButton.isVisible();
        saveOverlayButton.setVisible(nowVisible);
        setControlsEnabled(!nowVisible); // disable controls while overlay visible
        if (nowVisible) {
            saveOverlayButton.requestFocusInWindow();
        } else {
            requestFocusInWindow();
        }
        repaint();
    }

    /**
     * Enable or disable player controls.
     *
     * @param enabled true to enable controls, false to disable
     */
    private void setControlsEnabled(boolean enabled) {
        this.controlsEnabled = enabled;
    }

    /**
     * Create a {@link SaveState} and save it on a background thread to avoid blocking the UI.
     *
     * <p>Shows a confirmation dialog when the save completes or an error dialog on failure.</p>
     */
    private void doSaveInBackground() {
        SaveState state = new SaveState("defaultMap", timeLeft, player, skyColor, fogColor, rDp, map);

        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                try {
                    saveManager.save(state);
                    return true;
                } catch (Exception ex) {
                    ex.printStackTrace();
                    return false;
                }
            }

            @Override
            protected void done() {
                boolean ok = false;
                try { ok = get(); } catch (InterruptedException | ExecutionException ignored) {}
                if (ok) {
                    JOptionPane.showMessageDialog(Engine.this, "Game saved.");
                } else {
                    JOptionPane.showMessageDialog(Engine.this, "Save failed.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

}

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import static org.junit.jupiter.api.Assertions.*;

class EngineTest {

    /**
     * Helper: run a callable on the EDT and return its result.
     * Uses invokeAndWait semantics so tests proceed synchronously.
     */
    private static <T> T runOnEdt(Callable<T> callable) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            try {
                return callable.call();
            } catch (Exception e) {
                throw e;
            }
        } else {
            FutureTask<T> task = new FutureTask<>(callable);
            SwingUtilities.invokeAndWait(task);
            try {
                return task.get();
            } catch (ExecutionException ee) {
                Throwable cause = ee.getCause();
                if (cause instanceof Exception) throw (Exception) cause;
                throw ee;
            }
        }
    }

    @Test
    void constructorSetsDefaultScreenSizeAndRenderDistance() throws Exception {
        Engine engine = runOnEdt(() -> new Engine(null, Paths.get("target/test-save.txt")));
        assertEquals(640, engine.getScreenWidth(), "default screen width should be 640");
        assertEquals(480, engine.getScreenHeight(), "default screen height should be 480");

        // default rDp is 200 in source
        assertEquals(200.0, engine.getRenderdistance(), 1e-9);

        // change render distance and verify getter
        runOnEdt(() -> { engine.setRenderdistance(500); return null; });
        assertEquals(500.0, engine.getRenderdistance(), 1e-9);

        // map width/height setters
        runOnEdt(() -> { engine.setMapwidth(12); engine.setMapheight(9); return null; });

        Field mw = Engine.class.getDeclaredField("mW");
        Field mh = Engine.class.getDeclaredField("mH");
        mw.setAccessible(true); mh.setAccessible(true);
        assertEquals(12, mw.getInt(engine));
        assertEquals(9, mh.getInt(engine));
    }

    @Test
    void paintComponentDoesNotThrowAndCreatesBuffer() throws Exception {
        Engine engine = runOnEdt(() -> new Engine(null, Paths.get("target/test-save.txt")));

        runOnEdt(() -> {
            engine.setSize(200, 150);
            engine.setPreferredSize(new Dimension(200,150));
            return null;
        });

        runOnEdt(() -> {
            BufferedImage bi = new BufferedImage(200, 150, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = bi.createGraphics();
            try {
                engine.paintComponent(g);
            } finally {
                g.dispose();
            }
            return null;
        });
    }

    @Test
    void keyPressUpMovesPlayerAndUpdatesPlayerObject() throws Exception {
        Engine engine = runOnEdt(() -> new Engine(null, Paths.get("target/test-save.txt")));

        Player player = (Player) getPrivateField(engine, "player");
        assertNotNull(player);

        double beforeX = player.getX();
        double beforeY = player.getY();

        runOnEdt(() -> {
            KeyEvent up = new KeyEvent(engine, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED);
            engine.keyPressed(up);
            return null;
        });

        double afterX = player.getX();
        double afterY = player.getY();

        assertTrue(afterX != beforeX || afterY != beforeY, "UP key should move the player");
    }

    @Test
    void keyPressLeftRightAdjustsAngle() throws Exception {
        Engine engine = runOnEdt(() -> new Engine(null, Paths.get("target/test-save.txt")));
        Player player = (Player) getPrivateField(engine, "player");
        assertNotNull(player);

        runOnEdt(() -> { player.setAngle(0.0); return null; });

        runOnEdt(() -> {
            KeyEvent left = new KeyEvent(engine, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED);
            engine.keyPressed(left);
            return null;
        });
        double angleAfterLeft = player.getAngle();

        runOnEdt(() -> {
            KeyEvent right = new KeyEvent(engine, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_RIGHT, KeyEvent.CHAR_UNDEFINED);
            engine.keyPressed(right);
            engine.keyPressed(right);
            return null;
        });
        double angleAfterRights = player.getAngle();

        assertNotEquals(0.0, angleAfterLeft, 1e-9);
        assertNotEquals(angleAfterLeft, angleAfterRights, 1e-9);
    }

    @Test
    void escapeTogglesOverlayAndControls() throws Exception {
        Engine engine = runOnEdt(() -> new Engine(null, Paths.get("target/test-save.txt")));

        Object overlayButton = getPrivateField(engine, "saveOverlayButton");
        assertNotNull(overlayButton);
        java.lang.reflect.Method isVisibleMethod = overlayButton.getClass().getMethod("isVisible");

        boolean initiallyVisible = (boolean) isVisibleMethod.invoke(overlayButton);
        assertFalse(initiallyVisible, "overlay should be hidden initially");

        runOnEdt(() -> {
            KeyEvent esc = new KeyEvent(engine, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
            engine.keyPressed(esc);
            return null;
        });

        boolean afterEscVisible = (boolean) isVisibleMethod.invoke(overlayButton);
        assertTrue(afterEscVisible, "overlay should be visible after ESC");

        boolean controlsEnabled = getPrivateBooleanField(engine, "controlsEnabled");
        assertFalse(controlsEnabled, "controls should be disabled when overlay visible");

        runOnEdt(() -> {
            KeyEvent esc2 = new KeyEvent(engine, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
            engine.keyPressed(esc2);
            return null;
        });

        boolean afterEsc2Visible = (boolean) isVisibleMethod.invoke(overlayButton);
        assertFalse(afterEsc2Visible, "overlay should be hidden after second ESC");
        boolean controlsEnabled2 = getPrivateBooleanField(engine, "controlsEnabled");
        assertTrue(controlsEnabled2, "controls should be enabled after hiding overlay");
    }

    // Reflection helpers
    private static Object getPrivateField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static boolean getPrivateBooleanField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.getBoolean(target);
    }
}

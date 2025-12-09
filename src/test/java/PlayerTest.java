import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

class PlayerTest {

    @Test
    void defaultConstructorInitializesDefaults() {
        Player p = new Player();
        assertEquals(10, p.getHp(), "default HP should be 10");
        assertEquals(0.0, p.getX(), 1e-9);
        assertEquals(0.0, p.getY(), 1e-9);
        assertEquals(0.0, p.getAngle(), 1e-9);
    }

    @Test
    void paramConstructorSetsPosition() {
        Player p = new Player(10, 20);
        assertEquals(10.0, p.getX(), 1e-9);
        assertEquals(20.0, p.getY(), 1e-9);
    }

    @Test
    void hpSetterAndGetterWork() {
        Player p = new Player();
        p.setHp(42);
        assertEquals(42, p.getHp());
        p.setHp(0);
        assertEquals(0, p.getHp());
    }

    @Test
    void coordinateSettersAndGettersWork() {
        Player p = new Player();
        p.setX(3.1415);
        p.setY(-7.25);
        assertEquals(3.1415, p.getX(), 1e-9);
        assertEquals(-7.25, p.getY(), 1e-9);
    }

    @Test
    void angleSetterAndGetterWork() {
        Player p = new Player();
        p.setAngle(Math.PI / 2.0);
        assertEquals(Math.PI / 2.0, p.getAngle(), 1e-9);
    }

    @Test
    void playerIsSerializableAndRestoresState() throws Exception {
        Player p = new Player(5, 6);
        p.setHp(77);
        p.setAngle(1.234);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(p);
        }

        Player restored;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            Object obj = ois.readObject();
            assertTrue(obj instanceof Player, "deserialized object should be a Player");
            restored = (Player) obj;
        }

        assertEquals(77, restored.getHp());
        assertEquals(5.0, restored.getX(), 1e-9);
        assertEquals(6.0, restored.getY(), 1e-9);
        assertEquals(1.234, restored.getAngle(), 1e-9);
    }
}

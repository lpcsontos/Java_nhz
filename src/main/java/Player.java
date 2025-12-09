import java.io.Serializable;

/**
 * Represents the player state persisted in save files and used by the engine.
 *
 * <p>The class stores health (Hp), position (x,y) and facing angle. It is
 * intentionally lightweight and implements {@link Serializable} so it can be
 * embedded inside {@link SaveState}.</p>
 */
public class Player implements Serializable {
    private int Hp = 10;
    private double x;
    private double y;
    private double angle;

    /** Default constructor creating a player with default HP and zeroed position/angle. */
    public Player() {}

    /**
     * Convenience constructor that sets an initial integer position.
     *
     * @param X initial X coordinate (tile or world units depending on usage)
     * @param Y initial Y coordinate
     */
    public Player(int X, int Y){
        x = X;
        y = Y;
    }

    /**
     * Set the player's hit points.
     *
     * @param hp new HP value
     */
    public void setHp(int hp) {
        Hp = hp;
    }

    /**
     * Get the player's hit points.
     *
     * @return current HP
     */
    public int getHp() {
        return Hp;
    }

    /**
     * Get the player's X coordinate.
     *
     * @return X position
     */
    public double getX() {
        return x;
    }

    /**
     * Get the player's Y coordinate.
     *
     * @return Y position
     */
    public double getY() {
        return y;
    }

    /**
     * Set the player's X coordinate.
     *
     * @param x new X position
     */
    public void setX(double x) {
        this.x = x;
    }

    /**
     * Set the player's Y coordinate.
     *
     * @param y new Y position
     */
    public void setY(double y) {
        this.y = y;
    }

    /**
     * Set the player's facing angle in radians.
     *
     * @param angle new angle in radians
     */
    public void setAngle(double angle) {
        this.angle = angle;
    }

    /**
     * Get the player's facing angle in radians.
     *
     * @return angle in radians
     */
    public double getAngle() {
        return angle;
    }
}

import java.io.Serializable;

/**
 * Serializable container that represents a saved game state.
 *
 * <p>This class holds the minimal set of fields required to persist and restore
 * the engine state: map identifier, remaining time, player state, visual
 * parameters and the map layout. Instances are written and read using Java
 * serialization by {@link SaveManager}.</p>
 */
public class SaveState implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Name or identifier of the currently loaded map. */
    public String mapName;

    /** Remaining time in seconds when the save was created. */
    public double timeLeft;

    /** Player state (position, angle, HP). */
    public Player player;

    /** Packed ARGB color used for the sky in the saved state. */
    public int skyColor;

    /** Packed ARGB color used for fog in the saved state. */
    public int fogColor;

    /** Render distance used by the renderer at save time. */
    public double renderDistance;

    /** 2D integer array representing the tile map layout. */
    public int[][] map;

    /** Default no-arg constructor required for serialization frameworks. */
    public SaveState() {}

    /**
     * Create a new SaveState with the provided values.
     *
     * @param mapName name or id of the map
     * @param timeLeft remaining time in seconds
     * @param player player object containing position, angle and HP
     * @param skyColor packed ARGB sky color
     * @param fogColor packed ARGB fog color
     * @param renderDistance render distance used by the engine
     * @param map 2D integer array representing the map tiles
     */
    public SaveState(String mapName, double timeLeft, Player player, int skyColor, int fogColor, double renderDistance, int[][] map) {
        this.mapName = mapName;
        this.timeLeft = timeLeft;
        this.player = player;
        this.skyColor = skyColor;
        this.fogColor = fogColor;
        this.renderDistance = renderDistance;
        this.map = map;
    }
}

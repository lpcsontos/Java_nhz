import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Utility class responsible for saving and loading {@link SaveState} instances.
 *
 * <p>Saves are written atomically by first writing to a temporary sibling file
 * and then moving it into place. Loading validates that the deserialized
 * object is of type {@link SaveState}.</p>
 */
public class SaveManager {
    private final Path file;

    /**
     * Create a SaveManager that operates on the given file path.
     *
     * @param file path to the save file
     */
    public SaveManager(Path file) {
        this.file = file;
    }

    /**
     * Default constructor kept for compatibility; results in a manager with a null path.
     * Use {@link #SaveManager(Path)} for normal operation.
     */
    public SaveManager() {
        this.file = null;
    }

    /**
     * Return the configured save file path.
     *
     * @return Path used by this manager, or null if none configured
     */
    public Path getFilepath(){
        return this.file;
    }

    /**
     * Persist the provided {@link SaveState} to disk.
     *
     * <p>The method writes to a temporary sibling file first and then performs an
     * atomic move to replace the target file. This reduces the risk of producing
     * a corrupted save if the process is interrupted.</p>
     *
     * @param state the save state to write
     * @throws IOException if writing or moving the file fails
     */
    public void save(SaveState state) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".txt");
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)))) {
            oos.writeObject(state);
            oos.flush();
        }

        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Load a {@link SaveState} from disk.
     *
     * @return the deserialized SaveState, or null if the file does not exist
     * @throws IOException if reading fails or the file contains invalid data
     * @throws ClassNotFoundException if the serialized class cannot be resolved
     */
    public SaveState load() throws IOException, ClassNotFoundException {
        if (!Files.exists(file)) return null;
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(file, StandardOpenOption.READ)))) {
            Object o = ois.readObject();
            if (o instanceof SaveState) return (SaveState) o;
            else throw new IOException("Invalid save file");
        }
    }

    /**
     * Check whether the configured save file exists.
     *
     * @return true if the save file exists, false otherwise
     */
    public boolean exists() {
        return Files.exists(file);
    }
}

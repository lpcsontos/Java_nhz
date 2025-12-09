import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;

/**
 * Main application start window and menu UI for the labyrinth game.
 *
 * <p>This class builds a small Swing UI with a main menu, a map selection
 * card and a settings card. It manages card navigation, shows/hides the
 * "Load game" button depending on whether a save file exists and starts the
 * {@link Engine} panel when the player chooses to play or load a save.</p>
 *
 * <p>UI updates that affect Swing components are performed on the EDT where
 * appropriate.</p>
 */
public class StartWindow {
    private final JFrame frame = new JFrame("Escape from labyrinth");

    // Menu buttons
    private final JButton btnPlay = new JButton("Play");
    private final JButton btnSettings = new JButton("Settings");
    private final JButton btnHidden = new JButton("Load game");

    // Card layout container
    private final JPanel cards = new JPanel(new CardLayout());
    private static final String CARD_MENU = "menu";
    private static final String CARD_START = "start";
    private static final String CARD_SETTINGS = "settings";
    private static final String CARD_ENGINE = "engine";

    private Engine enginePanel;
    private final Path savePath = Paths.get("savegame.txt");
    private final DecimalFormat timeFmt = new DecimalFormat("#0.00");
    public static StartWindow INSTANCE;

    private final JLabel[] timeLabels = new JLabel[3];

    /**
     * Create and initialize the start window and show the main menu.
     */
    public StartWindow() {
        INSTANCE = this;
        init();
    }

    /**
     * Return to the menu by disposing the current frame.
     *
     * <p>Note: this method currently disposes the frame. The engine uses this
     * to return to the menu flow; the application creates a new StartWindow
     * instance when returning from a level.</p>
     */
    public void showMenu() {
        frame.dispose();
    }

    /**
     * Initialize UI components and layout.
     *
     * <p>Creates the menu, start and settings cards and registers listeners
     * for the buttons. The method also updates the visibility of the load
     * button based on whether a save file exists.</p>
     */
    private void init() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(8, 8));

        // --- Menu card ---
        JPanel menuPanel = new JPanel();
        menuPanel.setLayout(new BoxLayout(menuPanel, BoxLayout.Y_AXIS));
        menuPanel.setBorder(BorderFactory.createEmptyBorder(40, 80, 40, 80));
        btnHidden.setVisible(false);
        btnPlay.addActionListener(e -> showCard(CARD_START));
        btnSettings.addActionListener(e -> showCard(CARD_SETTINGS));
        btnHidden.addActionListener(e -> JOptionPane.showMessageDialog(frame, "Load game clicked"));

        btnPlay.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnSettings.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnHidden.setAlignmentX(Component.CENTER_ALIGNMENT);

        Dimension btnSize = new Dimension(200, 36);
        btnPlay.setMaximumSize(btnSize);
        btnSettings.setMaximumSize(btnSize);
        btnHidden.setMaximumSize(btnSize);

        menuPanel.add(Box.createVerticalGlue());
        menuPanel.add(btnHidden);
        menuPanel.add(Box.createRigidArea(new Dimension(0, 24)));
        menuPanel.add(btnPlay);
        menuPanel.add(Box.createRigidArea(new Dimension(0, 24)));
        menuPanel.add(btnSettings);
        menuPanel.add(Box.createVerticalGlue());

        updateLoadButtonVisibility();

        btnHidden.addActionListener(e -> {
            try {
                SaveManager sm = new SaveManager(savePath);
                SaveState s = sm.load();
                if (s != null) {
                    startEngine(s);
                } else {
                    JOptionPane.showMessageDialog(frame, "No save found.");
                    updateLoadButtonVisibility();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(frame, "Failed to load save.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        // --- Start card ---
        JPanel startPanel = createStartPanel();

        // --- Settings card ---
        JPanel settingsPanel = createSettingsPanel();

        // Add cards
        cards.add(menuPanel, CARD_MENU);
        cards.add(startPanel, CARD_START);
        cards.add(settingsPanel, CARD_SETTINGS);

        // --- Engine card ---
        JPanel engineHolder = new JPanel(new BorderLayout());
        cards.add(engineHolder, CARD_ENGINE);

        frame.add(cards, BorderLayout.CENTER);
        frame.setSize(320, 380);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    /**
     * Update the visibility of the hidden load button based on whether the save file exists.
     *
     * <p>This method schedules the visibility change on the EDT.</p>
     */
    private void updateLoadButtonVisibility() {
        boolean exists = Files.exists(savePath);
        SwingUtilities.invokeLater(() -> btnHidden.setVisible(exists));
    }

    /**
     * Set the hidden load button visibility from other threads.
     *
     * @param visible true to show the load button, false to hide it
     */
    public void setHiddenButtonVisible(boolean visible) {
        SwingUtilities.invokeLater(() -> btnHidden.setVisible(visible));
    }

    /**
     * Update one of the map time labels shown on the map selection card.
     *
     * @param index index of the label (0..2)
     * @param text  new text to display
     */
    public void updateTimeLabel(int index, String text) {
        if (index < 0 || index >= timeLabels.length) return;
        SwingUtilities.invokeLater(() -> timeLabels[index].setText(text));
    }

    /**
     * Show a specific card by name.
     *
     * @param name card identifier (one of CARD_MENU, CARD_START, CARD_SETTINGS, CARD_ENGINE)
     */
    private void showCard(String name) {
        CardLayout cl = (CardLayout) cards.getLayout();
        cl.show(cards, name);
    }

    /**
     * Create the map selection card UI.
     *
     * <p>Each column corresponds to a map slot. The method reads a time file
     * for each map (mapN/time.txt) and displays the recorded time if present.</p>
     *
     * @return a JPanel containing the start card UI
     */
    private JPanel createStartPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JLabel title = new JLabel("Choose a map", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(title, BorderLayout.NORTH);

        JPanel columns = new JPanel(new GridLayout(1, 3, 12, 0));
        for (int i = 0; i < 3; i++) {
            final int index = i +1;
            JPanel col = new JPanel();
            col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
            col.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

            String timeText = "No time";
            Path path = Path.of("map" + index + "/time.txt");

            if (Files.exists(path)) {
                try {
                    String content = Files.readString(path).trim();
                    double seconds = Double.parseDouble(content);
                    seconds = 300 - seconds;
                    timeText = timeFmt.format(seconds);
                } catch (IOException | NumberFormatException e) {
                    e.printStackTrace();
                }
            }

            JLabel timeLabel = new JLabel(timeText, SwingConstants.CENTER);
            timeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            timeLabels[i] = timeLabel; // store reference

            col.add(Box.createVerticalGlue());
            col.add(timeLabel);
            col.add(Box.createRigidArea(new Dimension(0, 8)));

            JButton playBtn = new JButton("Play");
            playBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

            playBtn.addActionListener(e -> {
                startEngine(index);
            });

            col.add(playBtn);
            col.add(Box.createVerticalGlue());
            columns.add(col);
        }
        panel.add(columns, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton back = new JButton("Back");
        back.addActionListener(e -> showCard(CARD_MENU));
        bottom.add(back);
        panel.add(bottom, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Create the settings card UI.
     *
     * <p>Provides simple controls for resolution and fullscreen toggling and
     * displays basic control instructions.</p>
     *
     * @return a JPanel containing the settings UI
     */
    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JLabel title = new JLabel("Settings", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel hamburger = new JLabel("\u2630"); // simple hamburger glyph
        hamburger.setFont(hamburger.getFont().deriveFont(20f));
        hamburger.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(hamburger);
        center.add(Box.createRigidArea(new Dimension(0, 8)));

        JLabel controls = new JLabel(
                "<html><div style='text-align:center;'>"
                        + "<b>Controls</b><br>"
                        + "Arrow Up will move the character up<br>"
                        + "Arrow Down will move back<br>"
                        + "Arrow Left and Right will look around in that direction"
                        + "</div></html>",
                SwingConstants.CENTER);
        controls.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(controls);
        center.add(Box.createRigidArea(new Dimension(0, 16)));

        String[] resOptions = {"640x480", "800x600", "1024x768", "1280x720", "1920x1080"};
        JComboBox<String> resSelect = new JComboBox<>(resOptions);
        resSelect.setMaximumSize(new Dimension(220, 28));
        resSelect.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel resLabel = new JLabel("Resolution:");
        resLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(resLabel);
        center.add(Box.createRigidArea(new Dimension(0,4)));
        center.add(resSelect);
        center.add(Box.createRigidArea(new Dimension(0,12)));

        JCheckBox fullscreenBox = new JCheckBox("Fullscreen");
        fullscreenBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(fullscreenBox);

        center.add(Box.createVerticalGlue());

        panel.add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton back = new JButton("Back");
        back.addActionListener(e -> showCard(CARD_MENU));
        bottom.add(back);
        panel.add(bottom, BorderLayout.SOUTH);

        resSelect.addActionListener(e -> {
            String sel = (String) resSelect.getSelectedItem();
            if (sel != null && !fullscreenBox.isSelected()) {
                String[] parts = sel.split("x");
                int w = Integer.parseInt(parts[0]);
                int h = Integer.parseInt(parts[1]);
                applyResolution(new Dimension(w, h));
            }
        });

        fullscreenBox.addActionListener(e -> {
            boolean fs = fullscreenBox.isSelected();
            setFullscreen(fs);
        });

        return panel;
    }

    /**
     * Start the engine for a selected map slot index.
     *
     * @param slotIndex map slot index (1-based)
     */
    private void startEngine(int slotIndex) {
        if (enginePanel == null) {
            enginePanel = new Engine();
        }

        JPanel engineCard = new JPanel(new BorderLayout());
        engineCard.add(enginePanel, BorderLayout.CENTER);

        cards.remove(cards.getComponentCount() - 1); // remove old placeholder (or keep reference)
        cards.add(engineCard, CARD_ENGINE);

        showCard(CARD_ENGINE);

        enginePanel.requestFocusInWindow();
    }

    /**
     * Start the engine using a previously saved {@link SaveState}.
     *
     * @param state save state to restore
     */
    private void startEngine(SaveState state) {
        frame.getContentPane().removeAll();

        enginePanel = new Engine(state, savePath);
        frame.add(enginePanel, BorderLayout.CENTER);
        frame.revalidate();
        frame.repaint();
        SwingUtilities.invokeLater(() -> enginePanel.requestFocusInWindow());
    }

    private final GraphicsDevice gd =
            GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

    /**
     * Apply a new window resolution (size) on the EDT.
     *
     * @param d new window size
     */
    private void applyResolution(Dimension d) {
        if (gd.getFullScreenWindow() == frame) return;
        SwingUtilities.invokeLater(() -> {
            frame.setSize(d);
            frame.validate();
            frame.repaint();
            frame.setLocationRelativeTo(null);
        });
    }

    /**
     * Toggle fullscreen mode for the main frame.
     *
     * @param fullscreen true to enter fullscreen, false to exit
     */
    private void setFullscreen(boolean fullscreen) {
        SwingUtilities.invokeLater(() -> {
            try {
                if (fullscreen) {
                    frame.dispose();
                    frame.setUndecorated(true);
                    frame.setVisible(true);
                    gd.setFullScreenWindow(frame);
                } else {
                    gd.setFullScreenWindow(null);
                    frame.dispose();
                    frame.setUndecorated(false);
                    frame.setVisible(true);
                    frame.setSize(1280, 720);
                    frame.setLocationRelativeTo(null);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            StartWindow w = new StartWindow();
        });
    }
}

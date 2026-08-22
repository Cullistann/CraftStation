package ui;

import core.Lang;
import core.UserPreferences;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.function.Consumer;

/**
 * Minecraft-themed server directory selection prompt.
 * Matches the visual design and layout of LanguagePrompt and LaunchPrompt.
 */
public final class ServerSelectPrompt extends JDialog {

    private final JTextField pathField;
    private final Consumer<String> onSelectedCallback;
    private boolean selectionConfirmed = false;
    private McLabel titleLabel;
    private McLabel descLabel;
    private StoneButton btnBrowse;
    private StoneButton btnSave;

    public ServerSelectPrompt(Frame owner, String initialPath, Consumer<String> onSelectedCallback) {
        super(owner, Lang.get("SELECT_SERVER_TITLE"), true);
        this.onSelectedCallback = onSelectedCallback;

        setSize(540, 310);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setResizable(false);
        setLayout(new BorderLayout());

        // Dirt texture background matching LanguagePrompt
        McPanel mainPanel = new McPanel(null, true);
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(22, 28, 22, 28));

        // Title panel with icon
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        titlePanel.setOpaque(false);

        titleLabel = new McLabel(Lang.get("SELECT_SERVER_TITLE"), SwingConstants.CENTER);
        titleLabel.setFont(McTheme.getMinecraftFont(20, Font.BOLD));
        titleLabel.setForeground(new Color(255, 255, 85)); // Minecraft Yellow
        titlePanel.add(titleLabel);

        descLabel = new McLabel(Lang.get("SELECT_SERVER_DESC"), SwingConstants.CENTER);
        descLabel.setFont(McTheme.getMinecraftFont(13, Font.PLAIN));
        descLabel.setForeground(new Color(170, 170, 170)); // Minecraft Grey
        descLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        mainPanel.add(titlePanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 14)));
        mainPanel.add(descLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 18)));

        // Path field and Browse button panel
        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        inputPanel.setOpaque(false);

        pathField = new JTextField(initialPath != null ? initialPath : "");
        pathField.setFont(McTheme.getMinecraftFont(13, Font.PLAIN));
        pathField.setPreferredSize(new Dimension(340, 38));
        pathField.setBackground(new Color(40, 40, 40));
        pathField.setForeground(Color.WHITE);
        pathField.setCaretColor(Color.WHITE);
        pathField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.BLACK, 2),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));

        btnBrowse = new StoneButton(Lang.get("BTN_BROWSE"));
        btnBrowse.setPreferredSize(new Dimension(110, 38));
        btnBrowse.addActionListener(e -> chooseFolder());

        inputPanel.add(pathField);
        inputPanel.add(btnBrowse);

        mainPanel.add(inputPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));

        // Action panel with Minecraft StoneButton
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        btnPanel.setOpaque(false);

        btnSave = new StoneButton(Lang.get("BTN_SAVE_DIR"));
        btnSave.setPreferredSize(new Dimension(280, 42));
        btnSave.addActionListener(e -> confirmSelection());

        btnPanel.add(btnSave);
        mainPanel.add(btnPanel);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });

        add(mainPanel, BorderLayout.CENTER);
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(Lang.get("SELECT_SERVER_TITLE"));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        String currentText = pathField.getText().trim();
        if (!currentText.isEmpty()) {
            File currentFile = new File(currentText);
            if (currentFile.exists()) {
                chooser.setCurrentDirectory(currentFile);
            }
        }

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedDir = chooser.getSelectedFile();
            if (selectedDir != null) {
                pathField.setText(selectedDir.getAbsolutePath());
            }
        }
    }

    private void confirmSelection() {
        String path = pathField.getText().trim();
        if (path.isEmpty()) {
            JOptionPane.showMessageDialog(this, Lang.get("SELECT_SERVER_DESC"), Lang.get("WARN_TITLE"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        File serverDir = new File(path);
        if (!serverDir.exists() || !serverDir.isDirectory()) {
            JOptionPane.showMessageDialog(this, Lang.get("ERR_BAT_NOT_FOUND"), Lang.get("MSG_ERROR"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!isValidServerFolder(serverDir)) {
            int choice = JOptionPane.showConfirmDialog(this,
                    Lang.get("WARN_INVALID_SERVER_DIR"),
                    Lang.get("WARN_TITLE"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }

        String absPath = serverDir.getAbsolutePath();
        UserPreferences.getInstance().setString(UserPreferences.KEY_LAST_SERVER_DIR, absPath);
        selectionConfirmed = true;
        dispose();

        if (onSelectedCallback != null) {
            onSelectedCallback.accept(absPath);
        }
    }

    public static boolean isValidServerFolder(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return false;
        }
        File props = new File(dir, "server.properties");
        File purpur = new File(dir, "purpur.jar");
        File paper = new File(dir, "paper.jar");
        File spigot = new File(dir, "spigot.jar");
        File bat = new File(dir, "sunucu_baslat.bat");
        File serverJar = new File(dir, "server.jar");

        return props.exists() || purpur.exists() || paper.exists() || spigot.exists() || bat.exists() || serverJar.exists();
    }

    public boolean isSelectionConfirmed() {
        return selectionConfirmed;
    }
}

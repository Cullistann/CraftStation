package ui;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import core.ConfigManager;
import core.Lang;
import core.ServerManager;

public final class ServerPanel extends McPanel {

    private final String serverDir;
    private final ServerManager server;
    private final ConfigManager configManager;
    private final JLabel globalStatusLabel;

    // Server Info Fields
    private JTextField nameField;
    private JTextArea motdField;
    private JLabel logoPreviewLabel;

    // Real-time Multiplayer List Preview Components
    private JLabel previewNameLabel;
    private JLabel previewMotdLabel;
    private ImageIcon logoIcon;

    private static final int LOGO_SIZE = 64;
    // BUG-S4 fix: field'da tutulup önceki durduruluyor; aksi halde hızlı kaydetmede
    // birden fazla timer aynı label'ı yarışıyor
    private Timer saveFeedbackTimer;
    private Timer statusClearTimer;

    public ServerPanel(String serverDir) {
        super(new BorderLayout(12, 12), false);
        this.serverDir = serverDir;
        this.server = ServerManager.getInstance(serverDir);
        this.configManager = ConfigManager.getInstance(serverDir);
        setBorder(new EmptyBorder(12, 14, 12, 14));

        globalStatusLabel = new JLabel("  ");
        globalStatusLabel.setFont(new Font("Inter", Font.ITALIC, 13));
        globalStatusLabel.setForeground(McTheme.TEXT_SECONDARY);

        JPanel mainContent = new JPanel();
        mainContent.setLayout(new BoxLayout(mainContent, BoxLayout.Y_AXIS));
        mainContent.setOpaque(false);
        mainContent.setBorder(new EmptyBorder(0, 5, 0, 5));

        // 1. MULTIPLAYER PREVIEW TITLE & CARD
        JLabel previewTitle = new JLabel(Lang.get("SET_SERVER_PREVIEW"));
        previewTitle.setFont(new Font("Inter", Font.BOLD, 17));
        previewTitle.setForeground(McTheme.TEXT_TITLE);
        previewTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainContent.add(previewTitle);
        mainContent.add(Box.createVerticalStrut(10));

        mainContent.add(createMultiplayerPreviewCard());
        mainContent.add(Box.createVerticalStrut(25));

        // 2. SERVER CONFIGURATION EDITORS
        JLabel settingsTitle = new JLabel(Lang.get("SET_SERVER_TITLE"));
        settingsTitle.setFont(new Font("Inter", Font.BOLD, 17));
        settingsTitle.setForeground(McTheme.TEXT_TITLE);
        settingsTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainContent.add(settingsTitle);
        mainContent.add(Box.createVerticalStrut(10));

        mainContent.add(createEditorsCard());
        mainContent.add(Box.createVerticalStrut(15));

        mainContent.add(Box.createVerticalGlue());

        JScrollPane mainScroll = new JScrollPane(mainContent);
        mainScroll.setBorder(null);
        mainScroll.setOpaque(false);
        mainScroll.getViewport().setOpaque(false);
        mainScroll.getVerticalScrollBar().setUnitIncrement(16);
        McTheme.styleScroll(mainScroll);

        add(mainScroll, BorderLayout.CENTER);

        // BOTTOM PANEL (SAVE & STATUS)
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

        McButton saveBtn = new McButton("  " + Lang.get("BTN_SAVE_ALL") + "  ");
        saveBtn.setPreferredSize(new Dimension(200, 36));
        saveBtn.setFont(new Font("Inter", Font.BOLD, 14));
        saveBtn.addActionListener(e -> saveSettingsAsync(saveBtn));

        bottomPanel.add(globalStatusLabel, BorderLayout.WEST);
        bottomPanel.add(saveBtn, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);

        // Async load initial settings
        loadSettingsAsync();
    }

    private JPanel createMultiplayerPreviewCard() {
        McCard card = new McCard(new BorderLayout(15, 0));
        card.setBorder(new EmptyBorder(12, 14, 12, 14));
        card.setMaximumSize(new Dimension(800, 106));
        card.setPreferredSize(new Dimension(800, 106));
        card.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Left Side: Server Icon Logo
        logoPreviewLabel = new JLabel();
        logoPreviewLabel.setPreferredSize(new Dimension(LOGO_SIZE, LOGO_SIZE));
        logoPreviewLabel.setMinimumSize(new Dimension(LOGO_SIZE, LOGO_SIZE));
        logoPreviewLabel.setMaximumSize(new Dimension(LOGO_SIZE, LOGO_SIZE));
        logoPreviewLabel.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 60), 2));
        logoPreviewLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logoPreviewLabel.setToolTipText(Lang.get("TOOLTIP_CLICK_LOGO"));
        
        logoPreviewLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                chooseAndCropLogo();
            }
        });

        // Center Area: Details (Name, MOTD)
        JPanel detailsPanel = new JPanel(new BorderLayout(0, 4));
        detailsPanel.setOpaque(false);

        previewNameLabel = new JLabel("Minecraft Server");
        previewNameLabel.setFont(new Font("Inter", Font.BOLD, 15));
        previewNameLabel.setForeground(Color.WHITE);
        detailsPanel.add(previewNameLabel, BorderLayout.NORTH);

        // Line 2 & 3: MOTD Description (HTML output for custom colors)
        previewMotdLabel = new JLabel("<html><body style='font-family:Consolas,Inter; font-size:12px; color:#AAAAAA;'>A Minecraft Server</body></html>");
        previewMotdLabel.setVerticalAlignment(SwingConstants.TOP);
        detailsPanel.add(previewMotdLabel, BorderLayout.CENTER);

        JPanel logoWrapper = new JPanel(new GridBagLayout());
        logoWrapper.setOpaque(false);
        logoWrapper.add(logoPreviewLabel);

        card.add(logoWrapper, BorderLayout.WEST);
        card.add(detailsPanel, BorderLayout.CENTER);

        return card;
    }

    private JPanel createEditorsCard() {
        McCard card = new McCard(new BorderLayout(20, 0));
        card.setBorder(McTheme.cardBorder());
        card.setMaximumSize(new Dimension(800, 310));
        card.setPreferredSize(new Dimension(800, 310));
        card.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Left: Inputs Panel (Name, MOTD)
        JPanel inputsPanel = new JPanel(new GridBagLayout());
        inputsPanel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new java.awt.Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Label for Name
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0;
        JLabel nameLabel = new JLabel(Lang.get("SET_SERVER_NAME") + ":");
        nameLabel.setFont(new Font("Inter", Font.BOLD, 13));
        nameLabel.setForeground(McTheme.TEXT_TITLE);
        inputsPanel.add(nameLabel, gbc);

        // TextField for Name
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0;
        nameField = new JTextField();
        nameField.setFont(new Font("Inter", Font.PLAIN, 14));
        nameField.setBackground(McTheme.INPUT_BG);
        nameField.setForeground(Color.WHITE);
        nameField.setCaretColor(Color.WHITE);
        nameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(McTheme.BORDER, 1),
                new EmptyBorder(8, 10, 8, 10)));
        inputsPanel.add(nameField, gbc);

        // Label for MOTD
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        JLabel motdLabel = new JLabel(Lang.get("SET_SERVER_MOTD") + ":");
        motdLabel.setFont(new Font("Inter", Font.BOLD, 13));
        motdLabel.setForeground(McTheme.TEXT_TITLE);
        inputsPanel.add(motdLabel, gbc);

        // TextArea for MOTD
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        motdField = new JTextArea();
        motdField.setFont(new Font("Consolas", Font.PLAIN, 13));
        motdField.setBackground(McTheme.INPUT_BG);
        motdField.setForeground(new Color(220, 220, 225));
        motdField.setCaretColor(Color.WHITE);
        motdField.setMargin(new Insets(8, 10, 8, 10));
        motdField.setLineWrap(true);
        motdField.setWrapStyleWord(true);
        
        JScrollPane motdScroll = new JScrollPane(motdField);
        motdScroll.setBorder(BorderFactory.createLineBorder(McTheme.BORDER, 1));
        McTheme.styleScroll(motdScroll);
        inputsPanel.add(motdScroll, gbc);

        // Right: Format Helper Panel (Color/Style grid)
        JPanel helperPanel = new JPanel(new BorderLayout(0, 10));
        helperPanel.setOpaque(false);
        helperPanel.setBorder(new EmptyBorder(8, 0, 8, 8));
        helperPanel.setPreferredSize(new Dimension(240, 0));

        JLabel helperTitle = new JLabel("Hızlı Format Kodları");
        helperTitle.setFont(new Font("Inter", Font.BOLD, 13));
        helperTitle.setForeground(McTheme.TEXT_TITLE);
        helperPanel.add(helperTitle, BorderLayout.NORTH);

        // Grid for Colors (4x4)
        JPanel colorsGrid = new JPanel(new GridLayout(4, 4, 6, 6));
        colorsGrid.setOpaque(false);
        
        colorsGrid.add(createColorButton("§0", new Color(0, 0, 0), "Black"));
        colorsGrid.add(createColorButton("§1", new Color(0, 0, 170), "Dark Blue"));
        colorsGrid.add(createColorButton("§2", new Color(0, 170, 0), "Dark Green"));
        colorsGrid.add(createColorButton("§3", new Color(0, 170, 170), "Dark Aqua"));
        colorsGrid.add(createColorButton("§4", new Color(170, 0, 0), "Dark Red"));
        colorsGrid.add(createColorButton("§5", new Color(170, 0, 170), "Dark Purple"));
        colorsGrid.add(createColorButton("§6", new Color(255, 170, 0), "Gold"));
        colorsGrid.add(createColorButton("§7", new Color(170, 170, 170), "Gray"));
        colorsGrid.add(createColorButton("§8", new Color(85, 85, 85), "Dark Gray"));
        colorsGrid.add(createColorButton("§9", new Color(85, 85, 255), "Blue"));
        colorsGrid.add(createColorButton("§a", new Color(85, 255, 85), "Green"));
        colorsGrid.add(createColorButton("§b", new Color(85, 255, 255), "Aqua"));
        colorsGrid.add(createColorButton("§c", new Color(255, 85, 85), "Red"));
        colorsGrid.add(createColorButton("§d", new Color(255, 85, 255), "Light Purple"));
        colorsGrid.add(createColorButton("§e", new Color(255, 255, 85), "Yellow"));
        colorsGrid.add(createColorButton("§f", new Color(255, 255, 255), "White"));
        helperPanel.add(colorsGrid, BorderLayout.CENTER);

        // Row for Styles
        JPanel stylesRow = new JPanel(new GridLayout(1, 5, 4, 0));
        stylesRow.setOpaque(false);
        stylesRow.setPreferredSize(new Dimension(0, 26));

        stylesRow.add(createStyleButton("§l", "B", "Bold (§l)"));
        stylesRow.add(createStyleButton("§o", "I", "Italic (§o)"));
        stylesRow.add(createStyleButton("§n", "U", "Underline (§n)"));
        stylesRow.add(createStyleButton("§m", "S", "Strikethrough (§m)"));
        stylesRow.add(createStyleButton("§r", "R", "Reset (§r)"));
        helperPanel.add(stylesRow, BorderLayout.SOUTH);

        card.add(inputsPanel, BorderLayout.CENTER);
        card.add(helperPanel, BorderLayout.EAST);

        // Document listeners to update preview in real-time
        DocumentListener previewUpdater = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { updatePreview(); }
            @Override
            public void removeUpdate(DocumentEvent e) { updatePreview(); }
            @Override
            public void changedUpdate(DocumentEvent e) { updatePreview(); }
        };
        nameField.getDocument().addDocumentListener(previewUpdater);
        motdField.getDocument().addDocumentListener(previewUpdater);

        return card;
    }

    private JButton createColorButton(String code, Color color, String name) {
        JButton btn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                g2.setColor(color);
                g2.fillRoundRect(1, 1, w - 2, h - 2, 4, 4);
                g2.setColor(new Color(255, 255, 255, 45));
                g2.drawRoundRect(1, 1, w - 2, h - 2, 4, 4);
                g2.setColor(new Color(0, 0, 0, 80));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 4, 4);
                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension(28, 28));
        btn.setToolTipText(name + " (" + code + ")");
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setFocusable(false);
        btn.setBorder(null);
        btn.setContentAreaFilled(false);
        btn.addActionListener(e -> {
            int pos = motdField.getCaretPosition();
            try {
                motdField.getDocument().insertString(pos, code, null);
                motdField.requestFocusInWindow();
                motdField.setCaretPosition(pos + code.length());
            } catch (Exception ignored) {}
        });
        return btn;
    }

    private JButton createStyleButton(String code, String label, String tooltip) {
        JButton btn = new JButton(label) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                g2.setColor(new Color(60, 65, 75));
                g2.fillRoundRect(1, 1, w - 2, h - 2, 4, 4);
                g2.setColor(new Color(255, 255, 255, 30));
                g2.drawRoundRect(1, 1, w - 2, h - 2, 4, 4);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Inter", Font.BOLD, 12));
        btn.setForeground(Color.WHITE);
        btn.setToolTipText(tooltip);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setFocusable(false);
        btn.setBorder(null);
        btn.setContentAreaFilled(false);
        btn.addActionListener(e -> {
            int pos = motdField.getCaretPosition();
            try {
                motdField.getDocument().insertString(pos, code, null);
                motdField.requestFocusInWindow();
                motdField.setCaretPosition(pos + code.length());
            } catch (Exception ignored) {}
        });
        return btn;
    }

    private void updatePreview() {
        String name = nameField.getText();
        if (name == null || name.isEmpty()) {
            name = "";
        }
        previewNameLabel.setText(name);

        String motd = motdField.getText();
        previewMotdLabel.setText(parseMotdToHtml(motd));
    }

    private void loadSettingsAsync() {
        globalStatusLabel.setText("  " + Lang.get("MSG_REFRESHING"));
        globalStatusLabel.setForeground(McTheme.TEXT_SECONDARY);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private String name;
            private String motd;
            private ImageIcon logo;

            @Override
            protected Void doInBackground() throws Exception {
                name = configManager.getProperty("server.properties", "server-name", "");
                motd = configManager.getProperty("server.properties", "motd", "");
                
                File logoFile = new File(serverDir, "server-icon.png");
                if (logoFile.exists()) {
                    BufferedImage bi = ImageIO.read(logoFile);
                    if (bi != null) {
                        logo = new ImageIcon(resizeImage(bi, LOGO_SIZE, LOGO_SIZE));
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    nameField.setText(name);
                    motdField.setText(motd);
                    
                    if (logo != null) {
                        logoIcon = logo;
                    } else {
                        logoIcon = new ImageIcon(new BufferedImage(LOGO_SIZE, LOGO_SIZE, BufferedImage.TYPE_INT_ARGB));
                    }
                    logoPreviewLabel.setIcon(logoIcon);
                    updatePreview();
                    
                    globalStatusLabel.setText("  ");
                } catch (Exception ex) {
                    globalStatusLabel.setText("  " + Lang.get("MSG_ERROR") + ": " + ex.getMessage());
                    globalStatusLabel.setForeground(McTheme.DANGER);
                }
            }
        };
        worker.execute();
    }

    private void saveSettingsAsync(McButton saveBtn) {
        String name = nameField.getText().trim();
        String motd = motdField.getText();

        globalStatusLabel.setText("  " + Lang.get("SET_SAVING"));
        globalStatusLabel.setForeground(McTheme.WARNING);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                Map<String, String> current = configManager.loadProperties("server.properties");
                current.put("server-name", name);
                current.put("motd", motd);
                configManager.saveProperties("server.properties", current);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    globalStatusLabel.setText("  " + Lang.get("SET_SAVED"));
                    globalStatusLabel.setForeground(McTheme.SUCCESS);

                    String oldText = saveBtn.getText();
                    saveBtn.setText("  \u2713 " + Lang.get("SET_SAVED") + "  ");
                    if (saveFeedbackTimer != null) saveFeedbackTimer.stop();
                    saveFeedbackTimer = new Timer(2000, e -> saveBtn.setText(oldText));
                    saveFeedbackTimer.setRepeats(false);
                    saveFeedbackTimer.start();
                } catch (Exception ex) {
                    globalStatusLabel.setText("  " + Lang.get("SET_SAVE_ERROR") + ": " + ex.getMessage());
                    globalStatusLabel.setForeground(McTheme.DANGER);
                }

                if (statusClearTimer != null) statusClearTimer.stop();
                statusClearTimer = new Timer(4000, e -> {
                    globalStatusLabel.setForeground(McTheme.TEXT_SECONDARY);
                    globalStatusLabel.setText("  ");
                });
                statusClearTimer.setRepeats(false);
                statusClearTimer.start();
            }
        };
        worker.execute();
    }

    private void chooseAndCropLogo() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("PNG Images (*.png)", "png"));
        
        int choice = chooser.showOpenDialog(this);
        if (choice == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            
            globalStatusLabel.setText("  " + Lang.get("MSG_REFRESHING"));
            globalStatusLabel.setForeground(McTheme.TEXT_SECONDARY);

            SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
                private ImageIcon newIcon;
                private Exception error;

                @Override
                protected Boolean doInBackground() {
                    try {
                        BufferedImage original = ImageIO.read(selectedFile);
                        if (original == null) {
                            throw new IOException("Invalid image file format");
                        }
                        
                        // Crop or scale to 64x64 square
                        BufferedImage resized = resizeImage(original, LOGO_SIZE, LOGO_SIZE);
                        File targetFile = new File(serverDir, "server-icon.png");
                        
                        // Write back directly as PNG
                        ImageIO.write(resized, "png", targetFile);
                        newIcon = new ImageIcon(resized);
                        return true;
                    } catch (Exception ex) {
                        error = ex;
                        return false;
                    }
                }

                @Override
                protected void done() {
                    try {
                        if (get()) {
                            logoIcon = newIcon;
                            logoPreviewLabel.setIcon(logoIcon);
                            globalStatusLabel.setText("  " + Lang.get("MSG_LOGO_UPDATED"));
                            globalStatusLabel.setForeground(McTheme.SUCCESS);
                        } else {
                            throw error;
                        }
                    } catch (Exception ex) {
                        // BUG-S2 fix: ExecutionException wrapper yerine root cause göster
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        globalStatusLabel.setText("  " + Lang.get("MSG_LOGO_ERROR") + cause.getMessage());
                        globalStatusLabel.setForeground(McTheme.DANGER);
                    }
                    
                    Timer t = new Timer(4000, e -> {
                        globalStatusLabel.setForeground(McTheme.TEXT_SECONDARY);
                        globalStatusLabel.setText("  ");
                    });
                    t.setRepeats(false);
                    t.start();
                }
            };
            worker.execute();
        }
    }

    private BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics2D = resizedImage.createGraphics();
        graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics2D.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        graphics2D.dispose();
        return resizedImage;
    }

    public static String parseMotdToHtml(String motd) {
        if (motd == null || motd.isEmpty()) {
            return "<html><body style='font-family:Consolas,Inter; font-size:12px; color:#AAAAAA;'></body></html>";
        }
        
        // Clean linebreaks typed in text areas
        String text = motd.replace("\\n", "\n");
        
        StringBuilder sb = new StringBuilder("<html><body style='font-family:Consolas,Inter; font-size:12px; color:#AAAAAA;'>");
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strikethrough = false;
        boolean hasFontTag = false;
        
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                i += 2;
                
                String colorHex = null;
                boolean isColor = false;
                switch (code) {
                    case '0' -> { colorHex = "#000000"; isColor = true; }
                    case '1' -> { colorHex = "#0000AA"; isColor = true; }
                    case '2' -> { colorHex = "#00AA00"; isColor = true; }
                    case '3' -> { colorHex = "#00AAAA"; isColor = true; }
                    case '4' -> { colorHex = "#AA0000"; isColor = true; }
                    case '5' -> { colorHex = "#AA00AA"; isColor = true; }
                    case '6' -> { colorHex = "#FFAA00"; isColor = true; }
                    case '7' -> { colorHex = "#AAAAAA"; isColor = true; }
                    case '8' -> { colorHex = "#555555"; isColor = true; }
                    case '9' -> { colorHex = "#5555FF"; isColor = true; }
                    case 'a' -> { colorHex = "#55FF55"; isColor = true; }
                    case 'b' -> { colorHex = "#55FFFF"; isColor = true; }
                    case 'c' -> { colorHex = "#FF5555"; isColor = true; }
                    case 'd' -> { colorHex = "#FF55FF"; isColor = true; }
                    case 'e' -> { colorHex = "#FFFF55"; isColor = true; }
                    case 'f' -> { colorHex = "#FFFFFF"; isColor = true; }
                    
                    case 'l' -> { if (!bold) { sb.append("<b>"); bold = true; } }
                    case 'o' -> { if (!italic) { sb.append("<i>"); italic = true; } }
                    case 'n' -> { if (!underline) { sb.append("<u>"); underline = true; } }
                    case 'm' -> { if (!strikethrough) { sb.append("<strike>"); strikethrough = true; } }
                    
                    case 'r' -> {
                        if (bold) { sb.append("</b>"); bold = false; }
                        if (italic) { sb.append("</i>"); italic = false; }
                        if (underline) { sb.append("</u>"); underline = false; }
                        if (strikethrough) { sb.append("</strike>"); strikethrough = false; }
                        if (hasFontTag) { sb.append("</font>"); hasFontTag = false; }
                    }
                }
                
                if (isColor) {
                    if (bold) { sb.append("</b>"); bold = false; }
                    if (italic) { sb.append("</i>"); italic = false; }
                    if (underline) { sb.append("</u>"); underline = false; }
                    if (strikethrough) { sb.append("</strike>"); strikethrough = false; }
                    if (hasFontTag) { sb.append("</font>"); }
                    
                    sb.append("<font color='").append(colorHex).append("'>");
                    hasFontTag = true;
                }
            } else {
                if (c == '\n') {
                    sb.append("<br>");
                } else if (c == ' ') {
                    sb.append("&nbsp;");
                } else if (c == '<') {
                    sb.append("&lt;");
                } else if (c == '>') {
                    sb.append("&gt;");
                } else if (c == '&') {
                    sb.append("&amp;");
                } else {
                    sb.append(c);
                }
                i++;
            }
        }
        
        if (bold) sb.append("</b>");
        if (italic) sb.append("</i>");
        if (underline) sb.append("</u>");
        if (strikethrough) sb.append("</strike>");
        if (hasFontTag) sb.append("</font>");
        
        sb.append("</body></html>");
        return sb.toString();
    }
}

package ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.function.Consumer;
import core.Lang;

public final class LaunchPrompt extends JFrame {
    private boolean usePanel = false;
    private Consumer<Boolean> callback;

    public LaunchPrompt(String serverDir) {
        super(Lang.get("APP_TITLE"));
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setLayout(new BorderLayout());

        try {
            java.awt.image.BufferedImage img = AssetManager.loadImage("CraftStation-logo.png");
            if (img == null) {
                File logoFile = new File("CraftStation-logo.png");
                if (logoFile.exists()) {
                    img = javax.imageio.ImageIO.read(logoFile);
                }
            }
            if (img == null) {
                File iconFile = new File(serverDir, "server-icon.png");
                if (iconFile.exists()) {
                    img = javax.imageio.ImageIO.read(iconFile);
                }
            }
            if (img != null) {
                java.util.List<Image> icons = new java.util.ArrayList<>();
                for (int size : new int[] { 16, 20, 24, 32, 40, 48, 64, 96, 128, 256 }) {
                    java.awt.image.BufferedImage scaled = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2 = scaled.createGraphics();
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.drawImage(img, 0, 0, size, size, null);
                    g2.dispose();
                    icons.add(scaled);
                }
                setIconImages(icons);
            }
        } catch (Exception ignored) {
        }

        // Main panel with MC dirt background
        McPanel mainPanel = new McPanel(null, true);
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(25, 30, 25, 30));

        // Title panel
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        titlePanel.setOpaque(false);

        // Özel Minecraft Label Sınıfımız (Gölgesi olan yazılar)
        McLabel titleLabel = new McLabel(Lang.get("LAUNCH_TITLE"), SwingConstants.CENTER);
        titleLabel.setFont(McTheme.getMinecraftFont(22, Font.BOLD));
        titleLabel.setForeground(new Color(255, 255, 85)); // MC yellow
        titlePanel.add(titleLabel);

        McLabel descLabel = new McLabel(Lang.get("LAUNCH_DESC"), SwingConstants.CENTER);
        descLabel.setFont(McTheme.getMinecraftFont(14, Font.PLAIN));
        descLabel.setForeground(new Color(170, 170, 170)); // MC grey
        descLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        mainPanel.add(titlePanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        mainPanel.add(descLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 30)));

        // Buttons panel
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        btnPanel.setOpaque(false);

        // Minecraft Taş Buton Kullanımı
        StoneButton panelBtn = new StoneButton(Lang.get("BTN_YES_PANEL"));
        panelBtn.setPreferredSize(new Dimension(260, 42));

        StoneButton terminalBtn = new StoneButton(Lang.get("BTN_NO_PANEL"));
        terminalBtn.setPreferredSize(new Dimension(260, 42));

        panelBtn.addActionListener(e -> {
            usePanel = true;
            dispose();
            if (callback != null) {
                callback.accept(true);
            }
        });

        terminalBtn.addActionListener(e -> {
            usePanel = false;
            dispose();
            if (callback != null) {
                callback.accept(false);
            }
        });

        btnPanel.add(panelBtn);
        btnPanel.add(terminalBtn);

        mainPanel.add(btnPanel);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
                if (callback != null) {
                    callback.accept(null);
                }
            }
        });

        add(mainPanel, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(null);
    }

    public void promptAsync(Consumer<Boolean> callback) {
        this.callback = callback;
        if (!isDisplayable()) {
            pack();
        }
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public boolean prompt() {
        throw new IllegalStateException(
                "LaunchPrompt.prompt() is not supported on EDT. Use promptAsync(Consumer<Boolean>) instead.");
    }

}

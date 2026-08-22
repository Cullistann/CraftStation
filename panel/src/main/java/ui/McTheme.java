package ui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;

public final class McTheme {
    public static final Color BG_TOP = new Color(48, 48, 48);
    public static final Color BG_BOTTOM = new Color(36, 36, 36);
    public static final Color SURFACE_1 = new Color(50, 50, 50, 245);
    public static final Color SURFACE_2 = new Color(38, 38, 38, 250);
    public static final Color BORDER = new Color(124, 130, 138, 150);
    public static final Color BORDER_SOFT = new Color(255, 255, 255, 32);
    public static final Color TEXT_PRIMARY = new Color(230, 232, 235);
    public static final Color TEXT_SECONDARY = new Color(165, 173, 181);
    public static final Color TEXT_TITLE = new Color(244, 247, 250);
    public static final Color ACCENT = new Color(56, 143, 215);
    public static final Color SUCCESS = new Color(94, 191, 107);
    public static final Color WARNING = new Color(225, 180, 77);
    public static final Color DANGER = new Color(218, 86, 86);
    public static final Color INPUT_BG = new Color(20, 23, 27);
    public static final Color STATUS_STOPPED = new Color(234, 74, 66);
    public static final Color STATUS_STOPPING = new Color(255, 170, 0);
    public static final Color TPS_GOOD = new Color(85, 255, 85);
    public static final Color TPS_WARN = new Color(255, 255, 85);
    public static final Color TPS_BAD = new Color(255, 85, 85);
    public static final Color RAM_LOW = new Color(94, 160, 107);
    public static final Color RAM_MED = new Color(200, 160, 60);
    public static final Color RAM_HIGH = new Color(195, 75, 75);
    public static final Color STRIP_TOP = new Color(53, 58, 64);
    public static final Color STRIP_BOTTOM = new Color(31, 35, 40);
    public static final Color STAT_HOVER_TOP = new Color(56, 56, 56);
    public static final Color STAT_HOVER_BOTTOM = new Color(40, 40, 40);
    public static final Color LIST_SELECTION_BG = new Color(74, 96, 118);
    public static final Color LIST_EVEN_BG = new Color(31, 36, 41);
    public static final Color LIST_ODD_BG = new Color(27, 31, 36);
    public static final Font FONT_UI = new Font("Inter", Font.PLAIN, 14);
    public static final Font FONT_TITLE = new Font("Inter", Font.BOLD, 18);
    public static final Font FONT_BIG = new Font("Inter", Font.BOLD, 24);

    private McTheme() {}

    public static Border cardBorder() {
        return new CompoundBorder(
            BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.LOWERED, 
                new Color(20, 20, 20, 150), new Color(10, 10, 10, 150), 
                new Color(75, 75, 75, 120), new Color(60, 60, 60, 120)
            ),
            new EmptyBorder(12, 12, 12, 12)
        );
    }

    public static Border sectionBorder() {
        return new CompoundBorder(
            BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.LOWERED, 
                new Color(25, 25, 25, 120), new Color(15, 15, 15, 150), 
                new Color(80, 80, 80, 80), new Color(65, 65, 65, 80)
            ),
            new EmptyBorder(8, 10, 8, 10)
        );
    }

    public static void styleScroll(JScrollPane pane) {
        pane.setBorder(BorderFactory.createLineBorder(BORDER, 1));
        pane.getViewport().setBackground(INPUT_BG);
        pane.setOpaque(false);
        pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        pane.getVerticalScrollBar().setUnitIncrement(16);
        pane.getVerticalScrollBar().setUI(new BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                thumbColor = new Color(108, 118, 130, 190);
                trackColor = new Color(28, 31, 36);
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return createZeroButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return createZeroButton();
            }

            @Override
            protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(118, 128, 140, 205));
                g2.fillRoundRect(thumbBounds.x + 2, thumbBounds.y + 2, thumbBounds.width - 4, thumbBounds.height - 4, 8, 8);
                g2.setColor(new Color(255, 255, 255, 35));
                g2.drawRoundRect(thumbBounds.x + 2, thumbBounds.y + 2, thumbBounds.width - 5, thumbBounds.height - 5, 8, 8);
                g2.dispose();
            }

            @Override
            protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(22, 24, 28));
                g2.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
                g2.dispose();
            }
        });
    }

    private static JButton createZeroButton() {
        JButton button = new JButton();
        button.setPreferredSize(new Dimension(0, 0));
        button.setMinimumSize(new Dimension(0, 0));
        button.setMaximumSize(new Dimension(0, 0));
        return button;
    }

    private static Font customMcFont = null;

    public static Font getMinecraftFont(int size, int style) {
        if (customMcFont == null) {
            try {
                java.io.File fontFile = new java.io.File("assets/minecraft.otf");
                if (fontFile.exists()) {
                    customMcFont = Font.createFont(Font.TRUETYPE_FONT, fontFile);
                }
            } catch (Exception ignored) {
            }
        }

        if (customMcFont != null) {
            return customMcFont.deriveFont(style, (float) size);
        }

        Font f = new Font("Minecraft", style, size);
        if (!"Minecraft".equalsIgnoreCase(f.getFamily())) {
            f = new Font("Minecraftia", style, size);
            if (!"Minecraftia".equalsIgnoreCase(f.getFamily())) {
                f = new Font("Monospaced", style, size);
            }
        }
        return f;
    }
}

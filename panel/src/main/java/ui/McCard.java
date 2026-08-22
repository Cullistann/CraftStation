package ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;

@SuppressWarnings("this-escape")
public class McCard extends JPanel {
    private TexturePaint customBg = null;
    private boolean useCustomBg = false;
    private int customOverlayAlpha = 95;

    private static BufferedImage scaleImage(BufferedImage img, int scale) {
        int w = img.getWidth() * scale;
        int h = img.getHeight() * scale;
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        return scaled;
    }

    public McCard(LayoutManager layout) {
        super(layout);
        setOpaque(false);
        setBorder(new EmptyBorder(10, 10, 10, 10));
    }

    public void setBlockBackground(String blockAssetPath) {
        setBlockBackground(blockAssetPath, 12, 120);
    }

    public void setBlockBackground(String blockAssetPath, int scale, int overlayAlpha) {
        try {
            BufferedImage blockImg = AssetManager.loadImage(blockAssetPath);
            if (blockImg != null) {
                int s = Math.max(1, scale);
                BufferedImage scaled = scaleImage(blockImg, s);
                customBg = new TexturePaint(scaled, new Rectangle(0, 0, scaled.getWidth(), scaled.getHeight()));
                customOverlayAlpha = Math.max(0, Math.min(overlayAlpha, 255));
                useCustomBg = true;
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        int w = getWidth();
        int h = getHeight();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        if (useCustomBg && customBg != null) {
            g2.setPaint(customBg);
            g2.fillRect(0, 0, w, h);
            g2.setColor(new Color(0, 0, 0, customOverlayAlpha));
            g2.fillRect(0, 0, w, h);
            super.paintComponent(g2);
            g2.dispose();
            return;
        }

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setPaint(new GradientPaint(0, 0, new Color(66, 71, 78), 0, h, new Color(45, 49, 55)));
        g2.fillRoundRect(0, 0, w - 1, h - 1, 8, 8);
        g2.setColor(new Color(255, 255, 255, 30));
        g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
        g2.setColor(new Color(0, 0, 0, 165));
        g2.drawRoundRect(1, 1, w - 3, h - 3, 8, 8);
        g2.setColor(new Color(0, 0, 0, 40));
        g2.fillRoundRect(2, h - 10, w - 4, 8, 0, 0);

        super.paintComponent(g2);
        g2.dispose();
    }
}

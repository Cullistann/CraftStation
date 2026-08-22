package ui;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

@SuppressWarnings("this-escape")
public class McPanel extends JPanel {
    private static TexturePaint dirtTexture;
    private static boolean loaded = false;
    
    // We only use dirt for now or a darker dirt for 'stone'.
    private final boolean isDirt;

    static {
        try {
            BufferedImage original = AssetManager.loadImage("options_background.png");
            if (original != null) {
                int scale = 4; // Options background is 16x16, scaling to 64x64 chunks
                BufferedImage scaled = new BufferedImage(original.getWidth()*scale, original.getHeight()*scale, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = scaled.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(original, 0, 0, scaled.getWidth(), scaled.getHeight(), null);
                
                // Add shadow layer so it acts like the real Minecraft options background GUI
                g.setColor(new Color(0, 0, 0, 120)); // Darker shadow over the dirt
                g.fillRect(0, 0, scaled.getWidth(), scaled.getHeight());
                g.dispose();

                dirtTexture = new TexturePaint(scaled, new Rectangle(0, 0, scaled.getWidth(), scaled.getHeight()));
                loaded = true;
            }
        } catch (Exception e) {
            core.LoggingUtil.getLogger(McPanel.class).warn("Failed to load options_background.png for McPanel: " + e.getMessage());
        }
    }

    public McPanel(LayoutManager layout, boolean isDirt) {
        super(layout);
        this.isDirt = isDirt;
        setOpaque(true);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setPaint(new GradientPaint(0, 0, new Color(48, 48, 48), 0, getHeight(), new Color(36, 36, 36)));
        g2.fillRect(0, 0, getWidth(), getHeight());

        if (isDirt && loaded && dirtTexture != null) {
            g2.setPaint(dirtTexture);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(new Color(0, 0, 0, 115));
            g2.fillRect(0, 0, getWidth(), getHeight());
        }

        Paint oldPaint = g2.getPaint();
        g2.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 48), 0, 40, new Color(0, 0, 0, 0)));
        g2.fillRect(0, 0, getWidth(), 40);
        g2.setPaint(new GradientPaint(0, getHeight() - 40, new Color(0, 0, 0, 0), 0, getHeight(), new Color(0, 0, 0, 58)));
        g2.fillRect(0, getHeight() - 40, getWidth(), 40);
        g2.setPaint(oldPaint);

        g2.setColor(new Color(255, 255, 255, 12));
        g2.drawLine(0, 0, getWidth(), 0);
        g2.setColor(new Color(0, 0, 0, 80));
        g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
        g2.dispose();
    }
}

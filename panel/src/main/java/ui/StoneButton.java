package ui;

import javax.swing.*;
import java.awt.*;

public class StoneButton extends JButton {
    public StoneButton(String text) {
        super(text);
        setFont(McTheme.getMinecraftFont(14, Font.BOLD)); // Minecraft fontu kullanıldı
        setFocusPainted(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        boolean pressed = getModel().isPressed();
        boolean hover = getModel().isRollover();

        // Minecraft Taş Rengi Arkaplanı
        g2.setColor(hover ? new Color(135, 135, 135) : new Color(110, 110, 110));
        g2.fillRect(0, 0, w, h);

        // Klasik Minecraft Outset/Inset 3D Gölgeleri (Köşeli)
        int borderSize = 2;
        g2.setColor(pressed ? new Color(50, 50, 50) : new Color(165, 165, 165)); // Sol & Üst
        g2.fillRect(0, 0, w, borderSize); // Top
        g2.fillRect(0, 0, borderSize, h); // Left

        g2.setColor(pressed ? new Color(165, 165, 165) : new Color(50, 50, 50)); // Sağ & Alt
        g2.fillRect(0, h - borderSize, w, borderSize); // Bottom
        g2.fillRect(w - borderSize, 0, borderSize, h); // Right

        // Siyah En Dış Çerçeve
        g2.setColor(new Color(0, 0, 0, 200));
        g2.drawRect(0, 0, w - 1, h - 1);

        // Metni Gölge ve Hover ile Klasik MC Gibi Çizdirme
        String text = getText();
        if (text != null) {
            FontMetrics fm = g2.getFontMetrics();
            int sw = fm.stringWidth(text);
            int sh = fm.getAscent();
            int sx = (w - sw) / 2;
            int sy = (h + sh) / 2 - 3;

            if (pressed) {
                sx += 1;
                sy += 1;
            }

            // Gölge Rengi
            g2.setColor(new Color(40, 40, 40));
            g2.drawString(text, sx + 2, sy + 2);
            // Ana Metin Rengi
            g2.setColor(hover ? new Color(255, 255, 160) : Color.WHITE);
            g2.drawString(text, sx, sy);
        }

        g2.dispose();
    }
}

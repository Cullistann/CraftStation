package ui;

import javax.swing.*;
import java.awt.*;

public class McLabel extends JLabel {
    private final Color SHADOW = new Color(40, 40, 40);

    public McLabel(String text, int alignment) {
        super(text, alignment);
    }

    @Override
    protected void paintComponent(Graphics g) {
        String text = getText();
        if (text == null) { super.paintComponent(g); return; }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        int sx = 0, sy = fm.getAscent();
        if (getHorizontalAlignment() == SwingConstants.CENTER) {
            sx = (getWidth() - fm.stringWidth(text)) / 2;
        }
        g2.setColor(SHADOW);
        g2.drawString(text, sx + 2, sy + 2);
        g2.setColor(getForeground());
        g2.drawString(text, sx, sy);
        g2.dispose();
    }
}

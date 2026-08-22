package ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class McButton extends JButton {
    private boolean isHovered = false;
    private boolean isPressed = false;
    private float hoverLevel = 0f;
    private float targetHover = 0f;
    private final Timer hoverTimer;
    private float rippleProgress = 1f;
    private Point rippleCenter = new Point(0, 0);
    private Timer rippleTimer;

    public McButton(String text) {
        this(text, null);
    }

    public McButton(String text, Icon icon) {
        super(text, icon);
        hoverTimer = new Timer(16, e -> {
            hoverLevel += (targetHover - hoverLevel) * 0.2f;
            if (Math.abs(targetHover - hoverLevel) < 0.01f) {
                hoverLevel = targetHover;
                ((Timer) e.getSource()).stop();
            }
            repaint();
        });
        rippleTimer = new Timer(16, e -> {
            rippleProgress += 0.08f;
            if (rippleProgress >= 1f) {
                rippleProgress = 1f;
                ((Timer) e.getSource()).stop();
            }
            repaint();
        });
        setFocusPainted(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setOpaque(false);
        setForeground(Color.WHITE);
        setFont(new Font("Inter", Font.BOLD, 14));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setMargin(new Insets(6, 10, 6, 10));
        setIconTextGap(8);

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { if (isEnabled()) { isHovered = true; targetHover = 1f; hoverTimer.start(); } repaint(); }
            @Override public void mouseExited(MouseEvent e) { isHovered = false; isPressed = false; targetHover = 0f; hoverTimer.start(); repaint(); }
            @Override public void mousePressed(MouseEvent e) {
                if (isEnabled()) {
                    isPressed = true;
                    rippleCenter = e.getPoint();
                    rippleProgress = 0f;
                    rippleTimer.restart();
                }
                repaint();
            }
            @Override public void mouseReleased(MouseEvent e) { if (isEnabled()) isPressed = false; repaint(); }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        int arc = 8;

        Color top;
        Color bottom;
        Color textColor;
        if (!isEnabled()) {
            top = new Color(86, 90, 96);
            bottom = new Color(64, 68, 73);
            textColor = new Color(160, 160, 160);
        } else if (isPressed) {
            top = new Color(50, 54, 60);
            bottom = new Color(74, 78, 86);
            textColor = new Color(236, 238, 240);
        } else if (isHovered || hoverLevel > 0.01f) {
            top = blend(new Color(84, 90, 98), new Color(100, 107, 116), hoverLevel);
            bottom = blend(new Color(60, 65, 71), new Color(72, 78, 85), hoverLevel);
            textColor = Color.WHITE;
        } else {
            top = new Color(78, 84, 92);
            bottom = new Color(57, 62, 68);
            textColor = Color.WHITE;
        }

        // Ana arkaplan
        g2.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
        g2.fillRoundRect(0, 0, w, h, arc, arc);

        // 3D Bevel/Derinlik Hissiyatı (Inset / Outset)
        if (!isEnabled() || !isPressed) {
            // Outset (Dışbükey) -> Üst ve Sol Aydınlık, Alt ve Sağ Koyu
            g2.setColor(new Color(255, 255, 255, 45 + (int)(30 * hoverLevel)));
            g2.drawRoundRect(1, 1, w - 2, h - 2, arc, arc); // Ana parlaklık
            g2.setColor(new Color(0, 0, 0, 180));
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc); // Dış kenar karanlık
            g2.setColor(new Color(0, 0, 0, 90));
            g2.drawLine(2, h - 2, w - 2, h - 2); // Alt ekstra koyuluk
            g2.drawLine(w - 2, 2, w - 2, h - 2); // Sağ ekstra koyuluk
        } else {
            // Inset (İçbükey - Basılmış) -> Üst ve Sol Koyu, Alt ve Sağ Aydınlık
            g2.setColor(new Color(0, 0, 0, 200));
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            g2.setColor(new Color(0, 0, 0, 120));
            g2.drawLine(1, 1, w - 2, 1); // Üst gölge
            g2.drawLine(1, 1, 1, h - 2); // Sol gölge
            g2.setColor(new Color(255, 255, 255, 30));
            g2.drawLine(2, h - 2, w - 2, h - 2); // Alt ışık
            g2.drawLine(w - 2, 2, w - 2, h - 2); // Sağ ışık
        }

        if (isEnabled() && rippleProgress < 1f) {
            Shape oldClip = g2.getClip();
            g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w, h, arc, arc));
            int maxRadius = (int) Math.hypot(w, h);
            int radius = (int) (maxRadius * rippleProgress);
            int alpha = (int) (60 * (1f - rippleProgress));
            g2.setColor(new Color(255, 255, 255, Math.max(0, alpha)));
            g2.fillOval(rippleCenter.x - radius, rippleCenter.y - radius, radius * 2, radius * 2);
            g2.setClip(oldClip);
        }

        int shift = isPressed ? 1 : 0;
        g2.translate(shift, shift);
        setForeground(textColor);
        super.paintComponent(g2);
        g2.dispose();
    }

    private Color blend(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        return new Color(r, g, bl);
    }
}

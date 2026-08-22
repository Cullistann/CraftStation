package ui;

import core.LoggingUtil;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Modern loading panel with animations and progress tracking.
 * Provides a professional user experience during application startup.
 */
public final class ModernLoadingPanel extends JPanel {

    private static final LoggingUtil logger = LoggingUtil.getLogger(ModernLoadingPanel.class);

    // Colors
    private static final Color BACKGROUND = new Color(30, 33, 40);
    private static final Color CARD_BG = new Color(40, 44, 52);
    private static final Color PROGRESS_BG = new Color(50, 55, 65);
    private static final Color PROGRESS_FILL = new Color(86, 156, 214);
    private static final Color TEXT_PRIMARY = new Color(230, 235, 240);
    private static final Color TEXT_SECONDARY = new Color(180, 185, 190);

    // Animation
    private float pulsePhase = 0f;
    private float progressWidth = 0f;
    private float targetProgress = 0f;
    private final AtomicInteger currentStep = new AtomicInteger(0);
    private final AtomicInteger totalSteps = new AtomicInteger(1);

    // Components
    private final JLabel titleLabel;
    private final JLabel detailLabel;
    private final JLabel stepLabel;
    private final Timer animationTimer;

    // Icons
    private final Icon loadingIcon;

    public ModernLoadingPanel() {
        setLayout(new BorderLayout());
        setBackground(BACKGROUND);
        setOpaque(true);

        // Create loading icon
        loadingIcon = createAnimatedIcon();

        // Create labels
        titleLabel = new JLabel("Minecraft Server Control Panel", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Inter", Font.BOLD, 28));
        titleLabel.setForeground(TEXT_PRIMARY);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(40, 0, 20, 0));

        detailLabel = new JLabel("Initializing system...", SwingConstants.CENTER);
        detailLabel.setFont(new Font("Inter", Font.PLAIN, 16));
        detailLabel.setForeground(TEXT_SECONDARY);
        detailLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 30, 0));

        stepLabel = new JLabel("Step 0 of 0", SwingConstants.CENTER);
        stepLabel.setFont(new Font("Inter", Font.PLAIN, 14));
        stepLabel.setForeground(new Color(150, 155, 160));

        // Create center panel
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 30, 0);
        centerPanel.add(titleLabel, gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, 20, 0);
        centerPanel.add(detailLabel, gbc);

        gbc.gridy = 2;
        gbc.insets = new Insets(0, 0, 40, 0);
        JLabel iconLabel = new JLabel(loadingIcon);
        centerPanel.add(iconLabel, gbc);

        gbc.gridy = 3;
        gbc.insets = new Insets(0, 0, 10, 0);
        centerPanel.add(createProgressBar(), gbc);

        gbc.gridy = 4;
        gbc.insets = new Insets(0, 0, 0, 0);
        centerPanel.add(stepLabel, gbc);

        add(centerPanel, BorderLayout.CENTER);

        // Create animation timer
        animationTimer = new Timer(16, e -> {
            pulsePhase += 0.05f;
            if (pulsePhase > Math.PI * 2f) {
                pulsePhase = 0f;
            }

            // Animate progress width
            progressWidth += (targetProgress - progressWidth) * 0.1f;
            if (Math.abs(targetProgress - progressWidth) < 0.5f) {
                progressWidth = targetProgress;
            }

            repaint();
        });
        animationTimer.start();

        logger.debug("ModernLoadingPanel initialized");
    }

    /**
     * Update loading progress.
     * 
     * @param step   Current step (1-based)
     * @param total  Total steps
     * @param detail Detail message
     */
    public void updateProgress(int step, int total, String detail) {
        SwingUtilities.invokeLater(() -> {
            currentStep.set(step);
            totalSteps.set(total);

            if (total > 0) {
                targetProgress = (float) step / total * 100f;
            } else {
                targetProgress = 0f;
            }

            detailLabel.setText(detail);
            stepLabel.setText(String.format("Step %d of %d", step, total));

            logger.debug("Loading progress: " + step + "/" + total + " - " + detail);
        });
    }

    /**
     * Set loading title.
     */
    public void setTitle(String title) {
        SwingUtilities.invokeLater(() -> {
            titleLabel.setText(title);
        });
    }

    /**
     * Stop animations and clean up.
     */
    public void stop() {
        if (animationTimer != null && animationTimer.isRunning()) {
            animationTimer.stop();
        }
        logger.debug("ModernLoadingPanel stopped");
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        stop();
    }

    /**
     * Create animated loading icon.
     */
    private Icon createAnimatedIcon() {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int size = 64;
                int centerX = x + size / 2;
                int centerY = y + size / 2;

                // Outer circle
                g2.setColor(new Color(60, 65, 75, 100));
                g2.setStroke(new BasicStroke(3f));
                g2.drawOval(x, y, size, size);

                // Animated arc
                float startAngle = pulsePhase * 57.2958f; // Convert to degrees
                float arcAngle = 120f;

                g2.setColor(PROGRESS_FILL);
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new Arc2D.Float(x, y, size, size, startAngle, arcAngle, Arc2D.OPEN));

                // Inner circle
                g2.setColor(new Color(80, 85, 95, 150));
                g2.fillOval(centerX - 12, centerY - 12, 24, 24);

                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return 64;
            }

            @Override
            public int getIconHeight() {
                return 64;
            }
        };
    }

    /**
     * Create custom progress bar component.
     */
    private JComponent createProgressBar() {
        return new JComponent() {
            {
                setPreferredSize(new Dimension(400, 24));
                setOpaque(false);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int width = getWidth();
                int height = getHeight();

                // Background
                g2.setColor(PROGRESS_BG);
                g2.fillRoundRect(0, 0, width, height, height, height);

                // Progress fill
                int fillWidth = (int) (width * progressWidth / 100f);
                if (fillWidth > 0) {
                    g2.setColor(PROGRESS_FILL);
                    g2.fillRoundRect(0, 0, fillWidth, height, height, height);

                    // Shine effect
                    GradientPaint shine = new GradientPaint(
                            0, 0, new Color(255, 255, 255, 80),
                            0, height / 2, new Color(255, 255, 255, 20));
                    g2.setPaint(shine);
                    g2.fillRoundRect(0, 0, fillWidth, height / 2, height, height);
                }

                // Border
                g2.setColor(new Color(0, 0, 0, 60));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, width - 1, height - 1, height, height);

                g2.dispose();
            }
        };
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Add subtle background pattern
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.03f));

        int gridSize = 40;
        for (int x = 0; x < getWidth(); x += gridSize) {
            for (int y = 0; y < getHeight(); y += gridSize) {
                g2.setColor(Color.WHITE);
                g2.fillOval(x, y, 2, 2);
            }
        }

        g2.dispose();
    }

    // Helper class for arc drawing
    private static class Arc2D extends java.awt.geom.Arc2D.Float {
        public Arc2D(float x, float y, float w, float h, float start, float extent, int type) {
            super(x, y, w, h, start, extent, type);
        }
    }
}
package ui;

import core.LoggingUtil;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advanced tooltip manager for providing contextual help and guidance.
 * Supports rich text, animations, and positioning control.
 */
public final class TooltipManager {

    private static final LoggingUtil logger = LoggingUtil.getLogger(TooltipManager.class);
    private static final TooltipManager instance = new TooltipManager();

    // Configuration
    private static final int DEFAULT_DELAY_MS = 800;
    private static final int DEFAULT_DURATION_MS = 5000;
    private static final Color BACKGROUND = new Color(30, 35, 42, 240);
    private static final Color BORDER = new Color(80, 90, 105);
    private static final Color TEXT_COLOR = new Color(230, 235, 240);
    private static final Color ACCENT = new Color(86, 156, 214);

    // State
    private final Map<Component, TooltipInfo> tooltips = new ConcurrentHashMap<>();
    private final Map<Component, Timer> showTimers = new ConcurrentHashMap<>();
    private final Map<Component, Timer> hideTimers = new ConcurrentHashMap<>();
    private JWindow currentTooltip;
    private Component currentComponent;

    // Singleton
    private TooltipManager() {
        logger.debug("TooltipManager initialized");
    }

    public static TooltipManager getInstance() {
        return instance;
    }

    /**
     * Register a tooltip for a component.
     * 
     * @param component The component to attach tooltip to
     * @param text      Tooltip text
     */
    public void register(Component component, String text) {
        register(component, text, DEFAULT_DELAY_MS, DEFAULT_DURATION_MS);
    }

    /**
     * Register a tooltip with custom delay and duration.
     * 
     * @param component  The component to attach tooltip to
     * @param text       Tooltip text
     * @param delayMs    Delay before showing (milliseconds)
     * @param durationMs Duration before auto-hiding (milliseconds)
     */
    public void register(Component component, String text, int delayMs, int durationMs) {
        TooltipInfo info = new TooltipInfo(text, delayMs, durationMs);
        boolean alreadyRegistered = tooltips.containsKey(component);
        tooltips.put(component, info);

        if (alreadyRegistered) {
            return;
        }

        // Add mouse listeners
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                // Cancel any pending hide
                Timer existingHide = hideTimers.remove(component);
                if (existingHide != null) {
                    existingHide.stop();
                }

                // Schedule show
                Timer showTimer = new Timer(delayMs, ev -> {
                    showTimers.remove(component);
                    showTooltip(component, e.getLocationOnScreen());
                });
                showTimer.setRepeats(false);
                showTimer.start();
                showTimers.put(component, showTimer);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                // Cancel show timer
                Timer existingShow = showTimers.remove(component);
                if (existingShow != null) {
                    existingShow.stop();
                }

                // Schedule hide
                Timer hideTimer = new Timer(300, ev -> {
                    hideTimers.remove(component);
                    hideTooltip();
                });
                hideTimer.setRepeats(false);
                hideTimer.start();
                hideTimers.put(component, hideTimer);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                // Hide immediately on click
                hideTooltip();
                Timer existingShow = showTimers.remove(component);
                if (existingShow != null) {
                    existingShow.stop();
                }
            }
        });

        logger.debug("Tooltip registered for component: " + component.getClass().getSimpleName());
    }

    /**
     * Unregister tooltip from component.
     */
    public void unregister(Component component) {
        tooltips.remove(component);
        hideTooltip();
        logger.debug("Tooltip unregistered for component: " + component.getClass().getSimpleName());
    }

    /**
     * Show tooltip immediately at specified screen location.
     */
    public void showTooltip(Component component, Point screenLocation) {
        SwingUtilities.invokeLater(() -> {
            TooltipInfo info = tooltips.get(component);
            if (info == null) {
                return;
            }

            // Hide previous tooltip
            hideTooltip();

            // Create tooltip window
            currentTooltip = new JWindow();
            currentComponent = component;

            // Create content panel
            JPanel content = new JPanel(new BorderLayout(10, 10)) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    // Background with rounded corners
                    g2.setColor(BACKGROUND);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);

                    // Border
                    g2.setColor(BORDER);
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);

                    // Accent line at top
                    g2.setColor(ACCENT);
                    g2.fillRoundRect(0, 0, getWidth(), 3, 12, 12);

                    g2.dispose();
                }
            };
            content.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
            content.setOpaque(false);

            // Create text label with HTML support
            JLabel textLabel = new JLabel("<html><div style='width:300px;'>" +
                    info.text + "</div></html>");
            textLabel.setForeground(TEXT_COLOR);
            textLabel.setFont(new Font("Inter", Font.PLAIN, 13));

            content.add(textLabel, BorderLayout.CENTER);

            // Add close button for persistent tooltips
            if (info.durationMs > 10000) {
                JButton closeBtn = new JButton("×");
                closeBtn.setFont(new Font("Arial", Font.BOLD, 16));
                closeBtn.setForeground(new Color(180, 185, 190));
                closeBtn.setContentAreaFilled(false);
                closeBtn.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
                closeBtn.setFocusPainted(false);
                closeBtn.addActionListener(e -> hideTooltip());

                JPanel topPanel = new JPanel(new BorderLayout());
                topPanel.setOpaque(false);
                topPanel.add(closeBtn, BorderLayout.EAST);
                content.add(topPanel, BorderLayout.NORTH);
            }

            currentTooltip.setContentPane(content);
            currentTooltip.pack();

            // Position tooltip (avoid covering the component)
            int x = screenLocation.x;
            int y = screenLocation.y + 30; // Below cursor

            // Ensure tooltip stays on screen
            Rectangle screenBounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration().getBounds();

            if (x + currentTooltip.getWidth() > screenBounds.width) {
                x = screenBounds.width - currentTooltip.getWidth() - 10;
            }
            if (y + currentTooltip.getHeight() > screenBounds.height) {
                y = screenLocation.y - currentTooltip.getHeight() - 10;
            }

            currentTooltip.setLocation(x, y);
            currentTooltip.setVisible(true);

            // Schedule auto-hide if duration is specified
            if (info.durationMs > 0) {
                Timer hideTimer = new Timer(info.durationMs, e -> hideTooltip());
                hideTimer.setRepeats(false);
                hideTimer.start();
                hideTimers.put(component, hideTimer);
            }

            logger.debug("Tooltip shown for: " + component.getClass().getSimpleName());
        });
    }

    /**
     * Hide current tooltip.
     */
    public void hideTooltip() {
        SwingUtilities.invokeLater(() -> {
            if (currentTooltip != null) {
                currentTooltip.dispose();
                currentTooltip = null;
            }

            if (currentComponent != null) {
                Timer hideTimer = hideTimers.remove(currentComponent);
                if (hideTimer != null) {
                    hideTimer.stop();
                }
                currentComponent = null;
            }
        });
    }

    /**
     * Show a temporary notification tooltip.
     * 
     * @param message    Notification message
     * @param durationMs Duration in milliseconds
     */
    public void showNotification(String message, int durationMs) {
        SwingUtilities.invokeLater(() -> {
            // Create notification window
            JWindow notification = new JWindow();

            JPanel content = new JPanel(new BorderLayout(10, 10)) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    // Gradient background
                    GradientPaint gradient = new GradientPaint(
                            0, 0, new Color(40, 100, 180, 240),
                            0, getHeight(), new Color(30, 70, 130, 240));
                    g2.setPaint(gradient);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);

                    // Border
                    g2.setColor(new Color(100, 150, 220));
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);

                    g2.dispose();
                }
            };
            content.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
            content.setOpaque(false);

            JLabel textLabel = new JLabel("<html><div style='width:250px;'>" +
                    message + "</div></html>");
            textLabel.setForeground(Color.WHITE);
            textLabel.setFont(new Font("Inter", Font.PLAIN, 13));

            content.add(textLabel, BorderLayout.CENTER);
            notification.setContentPane(content);
            notification.pack();

            // Position at bottom right
            Rectangle screenBounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration().getBounds();

            int x = screenBounds.width - notification.getWidth() - 20;
            int y = screenBounds.height - notification.getHeight() - 20;

            notification.setLocation(x, y);
            notification.setVisible(true);

            // Auto-hide
            Timer hideTimer = new Timer(durationMs, e -> notification.dispose());
            hideTimer.setRepeats(false);
            hideTimer.start();

            logger.debug("Notification shown: " + message.substring(0, Math.min(50, message.length())));
        });
    }

    /**
     * Enable tooltips for all components in a container.
     */
    public void enableForContainer(Container container) {
        enableForContainer(container, DEFAULT_DELAY_MS, DEFAULT_DURATION_MS);
    }

    /**
     * Enable tooltips with custom settings for all components in a container.
     */
    public void enableForContainer(Container container, int delayMs, int durationMs) {
        // Process all components recursively
        for (Component comp : container.getComponents()) {
            if (comp instanceof JComponent) {
                String tooltip = ((JComponent) comp).getToolTipText();
                if (tooltip != null && !tooltip.isBlank()) {
                    register(comp, tooltip, delayMs, durationMs);
                    ((JComponent) comp).setToolTipText(null); // Remove default tooltip
                }
            }

            if (comp instanceof Container) {
                enableForContainer((Container) comp, delayMs, durationMs);
            }
        }

        logger.debug("Tooltips enabled for container: " + container.getClass().getSimpleName());
    }

    /**
     * Get tooltip statistics.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("registered_tooltips", tooltips.size());
        stats.put("active_tooltip", currentTooltip != null);
        stats.put("pending_show_timers", showTimers.size());
        stats.put("pending_hide_timers", hideTimers.size());
        return stats;
    }

    /**
     * Clear all tooltips.
     */
    public void clear() {
        // M8 fix: timer'ları durdurmadan temizlemek orphaned timer'lara yol açıyordu
        for (javax.swing.Timer t : showTimers.values()) { t.stop(); }
        for (javax.swing.Timer t : hideTimers.values()) { t.stop(); }
        tooltips.clear();
        showTimers.clear();
        hideTimers.clear();
        hideTooltip();
        logger.info("All tooltips cleared");
    }

    // Internal data class
    private static class TooltipInfo {
        final String text;
        final int delayMs;
        final int durationMs;

        TooltipInfo(String text, int delayMs, int durationMs) {
            this.text = text;
            this.delayMs = delayMs;
            this.durationMs = durationMs;
        }
    }
}
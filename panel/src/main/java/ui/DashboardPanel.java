package ui;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import core.Lang;
import core.ServerManager;
import core.IServerManager.Status;

public final class DashboardPanel extends McPanel {

    private final ServerManager server;
    private final JLabel statusLabel;
    private final GlowDot statusDot;
    private final JLabel tpsLabel;
    private final JLabel playersLabel;
    private final JLabel uptimeLabel;
    private final JLabel ramLabel;
    private final McButton startBtn;
    private final McButton stopBtn;
    private final McButton restartBtn;
    private final DefaultListModel<String> errorListModel;
    private final JProgressBar ramBar;
    private final JLabel ramPercentLabel;
    private final Timer mainTimer;
    private final Timer tpsTimer;
    private final java.util.function.Consumer<Status> statusListener = status -> SwingUtilities.invokeLater(() -> updateStatus(status));

    public DashboardPanel(String serverDir) {
        super(new BorderLayout(12, 12), false);
        this.server = ServerManager.getInstance(serverDir);
        setBorder(new EmptyBorder(12, 14, 12, 14));

        JPanel topPanel = createModernStripPanel();
        topPanel.setLayout(new BorderLayout(15, 0));
        topPanel.setBorder(new EmptyBorder(10, 12, 10, 12));
        topPanel.setOpaque(false);

        // Status
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        statusPanel.setOpaque(false);
        statusDot = new GlowDot();

        statusLabel = new JLabel(Lang.get("STAT_OFFLINE"));
        statusLabel.setFont(new Font("Inter", Font.BOLD, 38));
        statusLabel.setForeground(McTheme.TEXT_SECONDARY);

        statusPanel.add(statusDot);
        statusPanel.add(statusLabel);
        topPanel.add(statusPanel, BorderLayout.WEST);

        // Control buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        btnPanel.setOpaque(false);

        startBtn = new McButton(Lang.get("BTN_START"), PixelIcons.createPlayIcon(12));
        stopBtn = new McButton(Lang.get("BTN_STOP"), PixelIcons.createStopIcon(12));
        restartBtn = new McButton(Lang.get("BTN_RESTART"), PixelIcons.createSyncIcon(14));

        startBtn.setPreferredSize(new Dimension(190, 54));
        stopBtn.setPreferredSize(new Dimension(190, 54));
        restartBtn.setPreferredSize(new Dimension(220, 54));

        stopBtn.setEnabled(false);
        restartBtn.setEnabled(false);

        startBtn.addActionListener(e -> server.start());
        stopBtn.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, Lang.get("STOP_WARN_MSG"),
                    Lang.get("CONFIRM_TITLE"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                server.stop();
            }
        });
        restartBtn.addActionListener(e -> server.restart());

        btnPanel.add(startBtn);
        btnPanel.add(stopBtn);
        btnPanel.add(restartBtn);
        topPanel.add(btnPanel, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);

        // === CENTER: Stats Cards ===
        JPanel statsGrid = new JPanel(new GridLayout(1, 4, 16, 0));
        statsGrid.setBorder(new EmptyBorder(2, 0, 8, 0));
        statsGrid.setOpaque(false);

        // 1. TPS Card
        tpsLabel = new JLabel("--", SwingConstants.CENTER);
        tpsLabel.setForeground(Color.WHITE);
        statsGrid.add(createStatCard(Lang.get("STAT_TPS"), PixelIcons.createGoldClockIcon(2), tpsLabel, null));

        // 2. Players Card
        playersLabel = new JLabel("--", SwingConstants.CENTER);
        playersLabel.setForeground(Color.WHITE);
        statsGrid.add(createStatCard(Lang.get("STAT_PLAYERS"), PixelIcons.createSteveIcon(2), playersLabel, null));

        // 3. Uptime
        uptimeLabel = new JLabel("--", SwingConstants.CENTER);
        uptimeLabel.setForeground(Color.WHITE);
        statsGrid.add(createStatCard(Lang.get("STAT_UPTIME"), PixelIcons.createSilverClockIcon(2), uptimeLabel, null));

        // 4. RAM
        ramLabel = new JLabel("--", SwingConstants.CENTER);
        ramLabel.setFont(new Font("Inter", Font.BOLD, 19));
        ramLabel.setForeground(Color.WHITE);
        ramBar = new JProgressBar(0, 100);
        ramBar.setPreferredSize(new Dimension(100, 14));
        ramBar.setForeground(new Color(138, 124, 101));
        ramBar.setBackground(new Color(22, 24, 28));
        ramBar.setBorder(BorderFactory.createLineBorder(new Color(180, 185, 190, 80), 1));
        ramPercentLabel = new JLabel("0%", SwingConstants.CENTER);
        ramPercentLabel.setForeground(McTheme.TEXT_SECONDARY);
        ramPercentLabel.setFont(new Font("Inter", Font.BOLD, 13));
        JPanel ramPanel = new JPanel(new BorderLayout(0, 6));
        ramPanel.setOpaque(false);
        ramPanel.add(ramLabel, BorderLayout.NORTH);
        ramPanel.add(ramBar, BorderLayout.CENTER);
        ramPanel.add(ramPercentLabel, BorderLayout.SOUTH);
        statsGrid.add(createStatCard(Lang.get("STAT_RAM"), PixelIcons.createRamIcon(2), null, ramPanel));

        add(statsGrid, BorderLayout.CENTER);

        JPanel errorSection = new JPanel(new BorderLayout(0, 6));
        errorSection.setOpaque(false);

        JLabel errorTitle = new JLabel(Lang.get("RECENT_ERRORS"));
        errorTitle.setFont(new Font("Inter", Font.BOLD, 34));
        errorTitle.setForeground(McTheme.TEXT_TITLE);
        errorTitle.setBorder(new EmptyBorder(0, 8, 0, 0));
        errorSection.add(errorTitle, BorderLayout.NORTH);

        McCard errorPanel = new McCard(new BorderLayout(0, 4));
        errorPanel.setBorder(new EmptyBorder(10, 12, 10, 12));

        errorListModel = new DefaultListModel<>();
        errorListModel.addElement(Lang.get("NO_ERRORS_YET"));
        JList<String> errorList = new JList<>(errorListModel);
        errorList.setOpaque(false);
        errorList.setLayoutOrientation(JList.VERTICAL);
        errorList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        errorList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() < 2)
                    return; // Çift tıklama gerektirir
                int index = errorList.locationToIndex(e.getPoint());
                if (index < 0)
                    return;
                var details = server.getRecentErrorDetails();
                if (details.isEmpty())
                    return; // "henüz hata yok" placeholder'ına tıklamayı yoksay
                if (index >= details.size())
                    return;
                var entry = details.get(index);
                String message = Lang.get("ERR_REASON_LABEL") + " " + entry.cause() + "\n\n"
                        + Lang.get("ERR_FIX_LABEL") + " " + entry.fix() + "\n\n"
                        + Lang.get("ERR_RAW_LABEL") + " " + entry.rawLine();
                JOptionPane.showMessageDialog(
                        DashboardPanel.this,
                        message,
                        entry.title(),
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });
        errorList.setCellRenderer(new DefaultListCellRenderer() {
            private final Icon checkIcon = PixelIcons.createCheckmarkIcon(2);

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                    boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                lbl.setOpaque(false);
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                String txt = (String) value;
                if (txt.equals(Lang.get("NO_ERRORS_YET"))) {
                    lbl.setIcon(null);
                    lbl.setOpaque(true);
                    lbl.setBackground(new Color(0, 0, 0, 95));
                    lbl.setFont(new Font("Inter", Font.BOLD, 28));
                    lbl.setForeground(McTheme.SUCCESS);
                } else {
                    lbl.setIcon(null);
                    lbl.setOpaque(true);
                    lbl.setBackground(new Color(0, 0, 0, 85));
                    lbl.setFont(new Font("Inter", Font.BOLD, 14));
                    lbl.setForeground(McTheme.DANGER);
                    lbl.setHorizontalAlignment(SwingConstants.LEFT);
                }
                lbl.setIconTextGap(8);
                lbl.setBorder(new EmptyBorder(4, 10, 4, 10));
                lbl.setToolTipText(txt);
                return lbl;
            }
        });

        JScrollPane errorScroll = new JScrollPane(errorList);
        errorScroll.setOpaque(false);
        errorScroll.getViewport().setOpaque(false);
        errorScroll.setBorder(null);
        errorScroll.setPreferredSize(new Dimension(0, 170));
        errorPanel.add(errorScroll, BorderLayout.CENTER);

        errorSection.add(errorPanel, BorderLayout.CENTER);
        add(errorSection, BorderLayout.SOUTH);

        // Listeners
        server.addStatusListener(statusListener);

        // Optimized timer system - single timer with multiple tasks
        mainTimer = new Timer(1000, e -> updateStats());
        mainTimer.start();

        // Separate TPS timer (every 15 seconds)
        tpsTimer = new Timer(15000, e -> {
            server.requestTps();
        });
        tpsTimer.setInitialDelay(5000); // Start after 5 seconds
        tpsTimer.start();

        updateStatus(server.getStatus());
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (mainTimer != null && !mainTimer.isRunning()) mainTimer.start();
        if (tpsTimer != null && !tpsTimer.isRunning()) tpsTimer.start();
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        if (mainTimer != null) mainTimer.stop();
        if (tpsTimer != null) tpsTimer.stop();
        server.removeStatusListener(statusListener);
    }

    private void updateStatus(Status status) {
        switch (status) {
            case STOPPED -> {
                statusDot.setColor(McTheme.STATUS_STOPPED);
                statusDot.setPulse(true);
                statusLabel.setText(Lang.get("STAT_OFFLINE"));
                statusLabel.setForeground(new Color(228, 231, 236));
                startBtn.setEnabled(true);
                stopBtn.setEnabled(false);
                restartBtn.setEnabled(false);
            }
            case STARTING -> {
                statusDot.setColor(McTheme.WARNING);
                statusDot.setPulse(false);
                statusLabel.setText(Lang.get("STAT_STARTING"));
                statusLabel.setForeground(McTheme.WARNING);
                startBtn.setEnabled(false);
                stopBtn.setEnabled(true);
                restartBtn.setEnabled(false);
            }
            case RUNNING -> {
                statusDot.setColor(McTheme.SUCCESS);
                statusDot.setPulse(false);
                statusLabel.setText(Lang.get("STAT_ONLINE"));
                statusLabel.setForeground(McTheme.SUCCESS);
                startBtn.setEnabled(false);
                stopBtn.setEnabled(true);
                restartBtn.setEnabled(true);
            }
            case STOPPING -> {
                statusDot.setColor(McTheme.STATUS_STOPPING);
                statusDot.setPulse(false);
                statusLabel.setText(Lang.get("STAT_STOPPING"));
                statusLabel.setForeground(McTheme.STATUS_STOPPING);
                startBtn.setEnabled(false);
                stopBtn.setEnabled(false);
                restartBtn.setEnabled(false);
            }
        }
    }

    private void updateStats() {
        if (server.getStatus() == Status.RUNNING) {
            double tps = server.getTps();
            if (tps > 0) {
                tpsLabel.setText(String.format("%.1f", tps));
                if (tps >= 19)
                    tpsLabel.setForeground(McTheme.TPS_GOOD);
                else if (tps >= 15)
                    tpsLabel.setForeground(McTheme.TPS_WARN);
                else
                    tpsLabel.setForeground(McTheme.TPS_BAD);
            } else {
                tpsLabel.setText("--");
                tpsLabel.setForeground(Color.WHITE);
            }
            java.util.List<String> onlinePlayers = server.getOnlinePlayers();
            int pCount = (onlinePlayers != null) ? onlinePlayers.size() : 0;
            playersLabel.setText(pCount + " / " + server.getMaxPlayers());
            uptimeLabel.setText(server.getUptime());

            String ram = server.getAllocatedRam();
            if (ram != null && !ram.equals("?")) {
                ramLabel.setText(ram);
                int percent = extractRamPercent(ram);
                if (percent >= 0) {
                    ramBar.setValue(percent);
                    ramPercentLabel.setText(percent + "%");
                    if (percent < 70)
                        ramBar.setForeground(McTheme.RAM_LOW);
                    else if (percent < 85)
                        ramBar.setForeground(McTheme.RAM_MED);
                    else
                        ramBar.setForeground(McTheme.RAM_HIGH);
                } else {
                    ramBar.setValue(0);
                    ramPercentLabel.setText("--");
                }
            } else {
                ramLabel.setText("--");
                ramBar.setValue(0);
                ramPercentLabel.setText("--");
            }

        } else {
            tpsLabel.setText("--");
            tpsLabel.setForeground(Color.WHITE);
            playersLabel.setText("--");
            uptimeLabel.setText("--");
            ramLabel.setText("--");
            ramBar.setValue(0);
            ramPercentLabel.setText("--");
        }

        var errors = server.getRecentErrorDetails();
        java.util.List<String> targetErrorRows = new java.util.ArrayList<>();
        if (errors.isEmpty()) {
            targetErrorRows.add(Lang.get("NO_ERRORS_YET"));
        } else {
            for (var err : errors)
                targetErrorRows.add(err.summary());
        }
        if (isDifferent(errorListModel, targetErrorRows)) {
            errorListModel.clear();
            for (String row : targetErrorRows)
                errorListModel.addElement(row);
        }
    }



    private boolean isDifferent(DefaultListModel<String> model, java.util.List<String> data) {
        if (model.getSize() != data.size())
            return true;
        for (int i = 0; i < data.size(); i++) {
            if (!data.get(i).equals(model.getElementAt(i)))
                return true;
        }
        return false;
    }

    private JPanel createStatCard(String title, Icon icon, JLabel valueLabel, Component bottomData) {
        JPanel card = createModernStatPanel();
        card.setLayout(new BorderLayout(0, 4));
        card.setBorder(new EmptyBorder(10, 12, 10, 12));

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleRow.setOpaque(false);
        JLabel titleLabel = new JLabel(title, icon, SwingConstants.CENTER);
        titleLabel.setFont(new Font("Inter", Font.BOLD, 15));
        titleLabel.setForeground(McTheme.TEXT_TITLE);
        titleRow.add(titleLabel);
        titlePanel.add(titleRow, BorderLayout.CENTER);
        JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
        separator.setForeground(new Color(0, 0, 0, 170));
        separator.setBackground(new Color(255, 255, 255, 30));
        titlePanel.add(separator, BorderLayout.SOUTH);
        card.add(titlePanel, BorderLayout.NORTH);

        if (valueLabel != null) {
            valueLabel.setFont(new Font("Inter", Font.BOLD, 54));
            valueLabel.setForeground(McTheme.TEXT_TITLE);
            card.add(valueLabel, BorderLayout.CENTER);
        }

        if (bottomData != null) {
            card.add(bottomData, BorderLayout.SOUTH);
        }

        return card;
    }

    private JPanel createModernStripPanel() {
        return new JPanel() {
            {
                setOpaque(false);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                g2.setPaint(new GradientPaint(0, 0, McTheme.STRIP_TOP, 0, h, McTheme.STRIP_BOTTOM));
                g2.fillRoundRect(0, 0, w - 1, h - 1, 6, 6);
                g2.setColor(new Color(255, 255, 255, 32));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 6, 6);
                g2.setColor(new Color(0, 0, 0, 160));
                g2.drawRoundRect(1, 1, w - 3, h - 3, 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
    }

    private JPanel createModernStatPanel() {
        return new HoverStatPanel();
    }

    private static class HoverStatPanel extends JPanel {
        private float hoverLevel = 0f;
        private float targetHover = 0f;
        private Timer hoverTimer;

        public HoverStatPanel() {
            hoverTimer = new Timer(16, e -> {
                hoverLevel += (targetHover - hoverLevel) * 0.18f;
                if (Math.abs(targetHover - hoverLevel) < 0.01f) {
                    hoverLevel = targetHover;
                    ((Timer) e.getSource()).stop();
                }
                repaint();
            });
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    targetHover = 1f;
                    hoverTimer.start();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    targetHover = 0f;
                    hoverTimer.start();
                }
            });
        }

        @Override
        public void removeNotify() {
            super.removeNotify();
            if (hoverTimer != null) {
                hoverTimer.stop();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth();
            int h = getHeight();
            Color topBase = McTheme.BG_TOP;
            Color bottomBase = McTheme.BG_BOTTOM;
            Color topHover = McTheme.STAT_HOVER_TOP;
            Color bottomHover = McTheme.STAT_HOVER_BOTTOM;
            Color top = blend(topBase, topHover, hoverLevel);
            Color bottom = blend(bottomBase, bottomHover, hoverLevel);

            g2.setPaint(new GradientPaint(0, 0, top, w, h, bottom));
            g2.fillRoundRect(0, 0, w - 1, h - 1, 8, 8);

            // Hover durumda sınır yansıması/glow
            g2.setColor(new Color(255, 255, 255, 26 + (int) (22 * hoverLevel)));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
            g2.setColor(new Color(0, 0, 0, 170));
            g2.drawRoundRect(1, 1, w - 3, h - 3, 8, 8);
            g2.dispose();
            super.paintComponent(g);
        }

        private Color blend(Color a, Color b, float t) {
            t = Math.max(0f, Math.min(1f, t));
            int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
            int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
            int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
            return new Color(r, g, bl);
        }
    }

    private int extractRamPercent(String ram) {
        try {
            String cleaned = ram.replace("GB", "").replace("gb", "").replace(",", ".").trim();
            String[] parts = cleaned.split("/");
            if (parts.length != 2)
                return -1;
            double used = Double.parseDouble(parts[0].trim());
            double max = Double.parseDouble(parts[1].trim());
            if (max <= 0)
                return -1;
            return (int) Math.round((used / max) * 100.0);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static class GlowDot extends JComponent {
        private Color color = new Color(170, 170, 170);
        private boolean pulse = true;
        private float pulsePhase = 0f;
        private final Timer pulseTimer;

        public GlowDot() {
            setPreferredSize(new Dimension(22, 22));
            pulseTimer = new Timer(70, e -> {
                pulsePhase += 0.24f;
                if (pulsePhase > Math.PI * 2f)
                    pulsePhase = 0f;
                repaint();
            });
            // Timer starts via setPulse(true) — default pulse=true so start it
            if (pulse)
                pulseTimer.start();
        }

        @Override
        public void addNotify() {
            super.addNotify();
            if (pulse && pulseTimer != null && !pulseTimer.isRunning()) pulseTimer.start();
        }

        @Override
        public void removeNotify() {
            super.removeNotify();
            if (pulseTimer != null) pulseTimer.stop();
        }

        public void setColor(Color c) {
            this.color = c;
            repaint();
        }

        public void setPulse(boolean pulse) {
            this.pulse = pulse;
            if (pulse) {
                pulseTimer.start();
            } else {
                pulseTimer.stop();
                pulsePhase = 0f;
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            int glowAlpha = pulse ? (int) (36 + 40 * (0.5f + 0.5f * Math.sin(pulsePhase))) : 26;
            for (int i = 0; i < 4; i++) {
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), glowAlpha));
                g2.fillOval(i, i, w - i * 2, h - i * 2);
            }
            g2.setColor(color);
            g2.fillOval(4, 4, w - 8, h - 8);
            g2.dispose();
        }
    }
}

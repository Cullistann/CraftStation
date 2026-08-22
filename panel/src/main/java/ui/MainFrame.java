package ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.*;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import core.ServerManager;
import core.ConfigManager;
import core.BackupManager;
import core.Lang;
import core.IServerManager;

public final class MainFrame extends JFrame {

    private static final String WINDOW_TITLE = "CraftStation";

    private final ServerManager serverManager;
    private final ConfigManager configManager;
    private final BackupManager backupManager;
    private final String serverDir;
    private final AtomicBoolean shutdownInProgress = new AtomicBoolean(false);
    // BUG-M2 fix: tutulmazsa her sonraki STOPPED event'te System.exit(0) tetikleniyordu
    private java.util.function.Consumer<IServerManager.Status> shutdownStatusListener;
    private JPanel viewport;

    public MainFrame(String serverDir) {
        this.serverDir = serverDir;
        this.serverManager = ServerManager.getInstance(serverDir);
        this.configManager = ConfigManager.getInstance(serverDir);
        this.backupManager = BackupManager.getInstance(serverDir);

        setTitle(WINDOW_TITLE);
        setName(WINDOW_TITLE);

        // Uygulama ikonunu CraftStation-logo.png olarak yüksek kalitede yükle
        new Thread(() -> {
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
                    SwingUtilities.invokeLater(() -> setIconImages(icons));
                }
            } catch (Exception ignored) {
            }
        }, "IconLoader").start();

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1200, 760);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);

        // Window close handler
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                IServerManager.Status status = serverManager.getStatus();
                if (status == IServerManager.Status.STOPPED) {
                    System.exit(0);
                    return;
                }

                if (!shutdownInProgress.compareAndSet(false, true)) {
                    return;
                }

                int choice = JOptionPane.showConfirmDialog(
                        MainFrame.this,
                        Lang.get("EXIT_WARN_MSG"),
                        Lang.get("EXIT_WARN_TITLE"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (choice != JOptionPane.YES_OPTION) {
                    shutdownInProgress.set(false);
                    return;
                }

                beginShutdownAndExit();
            }

        });

        JPanel root = new MetalFramePanel();
        root.setLayout(new BorderLayout(0, 0));
        setContentPane(root);

        viewport = new JPanel(new BorderLayout(0, 0));
        viewport.setOpaque(false);
        viewport.setBorder(new EmptyBorder(14, 14, 14, 14));
        root.add(viewport, BorderLayout.CENTER);

        JPanel header = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                int w = getWidth();
                int h = getHeight();
                g2.setPaint(new GradientPaint(0, 0, new Color(66, 70, 77), 0, h, new Color(46, 50, 56)));
                g2.fillRect(0, 0, w, h);
                g2.setColor(new Color(255, 255, 255, 28));
                g2.drawLine(0, 0, w, 0);
                g2.setColor(new Color(0, 0, 0, 150));
                g2.drawLine(0, h - 1, w, h - 1);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(6, 10, 7, 10));
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titlePanel.setOpaque(false);
        JLabel titleLabel = new JLabel(Lang.get("APP_TITLE"));
        titleLabel.setForeground(McTheme.TEXT_TITLE);
        titleLabel.setFont(new Font("Inter", Font.BOLD, 20));
        titlePanel.add(titleLabel);
        header.add(titlePanel, BorderLayout.WEST);
        viewport.add(header, BorderLayout.NORTH);

        JPanel loadingPanel = createLoadingPanel();
        viewport.add(loadingPanel, BorderLayout.CENTER);
        SwingUtilities.invokeLater(() -> startUiBoot(loadingPanel));

        // Initialize TooltipManager for the entire application
        initializeTooltips();
    }

    private void beginShutdownAndExit() {
        MainFrame.this.setEnabled(false);

        // BUG-M2 fix: listener referansını field'da tut; aksi halde bir sonraki
        // normal stop işleminde de System.exit(0) tetikleniyor.
        shutdownStatusListener = newStatus -> {
            if (newStatus == IServerManager.Status.STOPPED) {
                serverManager.removeStatusListener(shutdownStatusListener);
                System.exit(0);
            }
        };
        serverManager.addStatusListener(shutdownStatusListener);

        serverManager.stop();

        // 60-second watchdog safety net
        java.util.concurrent.CompletableFuture.delayedExecutor(60, java.util.concurrent.TimeUnit.SECONDS)
                .execute(() -> System.exit(1));
    }

    private void initializeTooltips() {
        TooltipManager tooltipManager = TooltipManager.getInstance();

        // Enable tooltips for all components in the main frame
        tooltipManager.enableForContainer(this);

        // Register custom tooltips for specific components
        registerCustomTooltips(tooltipManager);
    }

    private void registerCustomTooltips(TooltipManager tooltipManager) {
        // Example tooltips for common UI elements
        // These would be registered when components are created
        // For now, this is a placeholder for future tooltip registrations
    }

    private JPanel createLoadingPanel() {
        ModernLoadingPanel modernLoadingPanel = new ModernLoadingPanel();
        modernLoadingPanel.setTitle(Lang.get("APP_TITLE"));
        modernLoadingPanel.updateProgress(0, 7, Lang.get("LOADING_PANEL"));
        return modernLoadingPanel;
    }

    private void startUiBoot(JPanel loadingPanel) {
        ModernLoadingPanel modernLoadingPanel = (ModernLoadingPanel) loadingPanel;
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Inter", Font.BOLD, 14));
        tabs.setOpaque(false);
        tabs.setBorder(new EmptyBorder(4, 8, 8, 8));
        tabs.setUI(new MainTabsUI());

        TabIcons icons = new TabIcons();
        Runnable[] steps = new Runnable[] {
                () -> {
                    modernLoadingPanel.updateProgress(1, 7,
                            Lang.get("LOADING_ICONS") + " - " + Lang.get("TAB_DASHBOARD"));
                    icons.dashboard = PixelIcons.createGoldClockIcon(2);
                },
                () -> {
                    modernLoadingPanel.updateProgress(2, 7,
                            Lang.get("LOADING_ICONS") + " - " + Lang.get("TAB_CONSOLE"));
                    icons.console = PixelIcons.createRamIcon(2);
                },
                () -> {
                    modernLoadingPanel.updateProgress(3, 7,
                            Lang.get("LOADING_ICONS") + " - " + Lang.get("TAB_PLAYERS"));
                    icons.players = PixelIcons.createSteveIcon(2);
                },
                () -> {
                    modernLoadingPanel.updateProgress(4, 7,
                            Lang.get("LOADING_ICONS") + " - " + Lang.get("TAB_SERVER"));
                    icons.server = PixelIcons.createExperienceBottleIcon(2);
                },
                () -> {
                    modernLoadingPanel.updateProgress(5, 7,
                            Lang.get("LOADING_ICONS") + " - " + Lang.get("TAB_SETTINGS"));
                    icons.settings = PixelIcons.createSilverClockIcon(2);
                },
                () -> {
                    modernLoadingPanel.updateProgress(6, 7,
                            Lang.get("LOADING_ICONS") + " - " + Lang.get("TAB_BACKUP"));
                    icons.backup = PixelIcons.createPlayIcon(12);
                },
                () -> {
                    modernLoadingPanel.updateProgress(7, 7,
                            Lang.get("LOADING_PANELS") + " - " + Lang.get("TAB_DASHBOARD"));
                    tabs.addTab(Lang.get("TAB_DASHBOARD"), icons.dashboard,
                            new DashboardPanel(serverDir));
                    tabs.addTab(Lang.get("TAB_CONSOLE"), icons.console, new ConsolePanel(serverDir));
                    tabs.addTab(Lang.get("TAB_PLAYERS"), icons.players, new PlayersPanel(serverDir));
                    tabs.addTab(Lang.get("TAB_SERVER"), icons.server, new ServerPanel(serverDir));
                    tabs.addTab(Lang.get("TAB_SETTINGS"), icons.settings,
                            new SettingsPanel(serverDir));
                    tabs.addTab(Lang.get("TAB_BACKUP"), icons.backup,
                            new BackupPanel(serverDir));
                }
        };

        Timer bootTimer = new Timer(500, null); // 500ms delay between steps for better UX
        final int[] currentStep = { 0 };

        bootTimer.addActionListener(e -> {
            if (currentStep[0] >= steps.length) {
                modernLoadingPanel.stop();
                viewport.remove(loadingPanel);
                viewport.add(tabs, BorderLayout.CENTER);
                viewport.revalidate();
                viewport.repaint();
                ((Timer) e.getSource()).stop();
                return;
            }

            steps[currentStep[0]].run();
            currentStep[0]++;
        });

        bootTimer.setInitialDelay(100);
        bootTimer.start();
    }

    private static class TabIcons {
        private Icon dashboard;
        private Icon console;
        private Icon players;
        private Icon settings;
        private Icon backup;
        private Icon server;
    }



    private static class MainTabsUI extends BasicTabbedPaneUI {
        @Override
        protected void installDefaults() {
            super.installDefaults();
            tabAreaInsets = new Insets(0, 0, 0, 0);
            tabInsets = new Insets(10, 20, 8, 20);
            selectedTabPadInsets = new Insets(0, 0, 0, 0);
        }

        @Override
        protected void paintTabArea(Graphics g, int tabPlacement, int selectedIndex) {
            if (rects == null || rects.length == 0) {
                super.paintTabArea(g, tabPlacement, selectedIndex);
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            int top = rects[0].y;
            int areaH = calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight) + 4;
            g2.setPaint(new GradientPaint(0, top, new Color(59, 64, 71), 0, top + areaH, new Color(40, 44, 50)));
            g2.fillRect(0, top, tabPane.getWidth(), areaH);
            g2.setColor(new Color(255, 255, 255, 34));
            g2.drawLine(0, top, tabPane.getWidth(), top);
            g2.setColor(new Color(0, 0, 0, 165));
            g2.drawLine(0, top + areaH - 1, tabPane.getWidth(), top + areaH - 1);
            g2.setColor(new Color(18, 20, 23, 120));
            g2.fillRect(0, top + areaH, tabPane.getWidth(), 2);
            g2.dispose();
            super.paintTabArea(g, tabPlacement, selectedIndex);
        }

        @Override
        protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h,
                boolean isSelected) {
            Graphics2D g2 = (Graphics2D) g.create();
            if (isSelected) {
                g2.setColor(new Color(125, 180, 232, 55));
                g2.fillRoundRect(x - 1, y - 1, w + 2, h + 3, 8, 8);
                g2.setPaint(new GradientPaint(0, y, new Color(87, 154, 216), 0, y + h, new Color(53, 109, 164)));
            } else {
                g2.setPaint(new GradientPaint(0, y, new Color(70, 75, 83), 0, y + h, new Color(49, 53, 60)));
            }
            g2.fillRoundRect(x + 1, y + 1, w - 2, h - 2, 6, 6);
            if (isSelected) {
                g2.setColor(new Color(216, 233, 247, 120));
                g2.fillRoundRect(x + 2, y + 2, w - 4, (h / 2) - 1, 6, 6);
            }
            g2.dispose();
        }

        @Override
        protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics, int tabIndex,
                String title, Rectangle textRect, boolean isSelected) {
            g.setFont(new Font("Inter", Font.BOLD, 14));
            g.setColor(isSelected ? Color.WHITE : new Color(205, 210, 214));
            g.drawString(title, textRect.x + 1, textRect.y + metrics.getAscent());
        }

        @Override
        protected void paintIcon(Graphics g, int tabPlacement, int tabIndex, Icon icon, Rectangle iconRect,
                boolean isSelected) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, isSelected ? 1.0f : 0.72f));
            icon.paintIcon(tabPane, g2, iconRect.x, iconRect.y);
            g2.dispose();
        }

        @Override
        protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects, int tabIndex,
                Rectangle iconRect, Rectangle textRect, boolean isSelected) {
        }

        @Override
        protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h,
                boolean isSelected) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(new Color(255, 255, 255, 32));
            g2.drawRoundRect(x, y, w - 1, h - 1, 6, 6);
            g2.setColor(new Color(0, 0, 0, 150));
            g2.drawRoundRect(x + 1, y + 1, w - 3, h - 3, 6, 6);
            if (isSelected) {
                g2.setColor(new Color(182, 220, 255, 170));
                g2.fillRect(x + 4, y + h - 3, w - 8, 2);
            }
            g2.dispose();
        }
    }

    private static class MetalFramePanel extends McPanel {
        public MetalFramePanel() {
            super(null, false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            g2.setColor(new Color(112, 118, 126));
            g2.drawRect(1, 1, w - 3, h - 3);
            g2.setColor(new Color(52, 56, 61));
            g2.drawRect(3, 3, w - 7, h - 7);
            g2.setColor(new Color(138, 145, 152));
            g2.drawRect(5, 5, w - 11, h - 11);
            g2.setColor(new Color(28, 31, 35));
            g2.drawRect(7, 7, w - 15, h - 15);

            int corner = 16;
            g2.setColor(new Color(56, 60, 66));
            g2.fillRect(0, 0, corner, corner);
            g2.fillRect(w - corner, 0, corner, corner);
            g2.fillRect(0, h - corner, corner, corner);
            g2.fillRect(w - corner, h - corner, corner, corner);
            g2.setColor(new Color(144, 150, 160));
            g2.drawRect(0, 0, corner - 1, corner - 1);
            g2.drawRect(w - corner, 0, corner - 1, corner - 1);
            g2.drawRect(0, h - corner, corner - 1, corner - 1);
            g2.drawRect(w - corner, h - corner, corner - 1, corner - 1);

            // Perçin (Vida) Detayları
            for (Point p : new Point[] {
                    new Point(4, 4), new Point(w - corner + 4, 4),
                    new Point(4, h - corner + 4), new Point(w - corner + 4, h - corner + 4)
            }) {
                g2.setColor(new Color(0, 0, 0, 200));
                g2.fillOval(p.x, p.y, 8, 8); // Vida deliği / gölgesi
                g2.setColor(new Color(100, 105, 115));
                g2.fillOval(p.x + 1, p.y + 1, 6, 6); // Vida başı metal rengi
                g2.setColor(new Color(255, 255, 255, 90));
                g2.drawOval(p.x + 1, p.y + 1, 5, 5); // Metal yansıması
                g2.setColor(new Color(40, 44, 50));
                g2.drawLine(p.x + 2, p.y + 4, p.x + 6, p.y + 4); // Vida düz başlık çizgisi
            }

            // Kasa İç Gölgesi (Inner Shadow - Ekrana Derinlik Katar)
            int innerX = 8, innerY = 8;
            int innerW = w - 16, innerH = h - 16;
            int shadowDepth = 14;

            // Üst Gölge
            g2.setPaint(new GradientPaint(0, innerY, new Color(0, 0, 0, 210), 0, innerY + shadowDepth,
                    new Color(0, 0, 0, 0)));
            g2.fillRect(innerX, innerY, innerW, shadowDepth);
            // Alt Gölge
            g2.setPaint(new GradientPaint(0, innerY + innerH, new Color(0, 0, 0, 210), 0, innerY + innerH - shadowDepth,
                    new Color(0, 0, 0, 0)));
            g2.fillRect(innerX, innerY + innerH - shadowDepth, innerW, shadowDepth);
            // Sol Gölge
            g2.setPaint(new GradientPaint(innerX, 0, new Color(0, 0, 0, 210), innerX + shadowDepth, 0,
                    new Color(0, 0, 0, 0)));
            g2.fillRect(innerX, innerY, shadowDepth, innerH);
            // Sağ Gölge
            g2.setPaint(new GradientPaint(innerX + innerW, 0, new Color(0, 0, 0, 210), innerX + innerW - shadowDepth, 0,
                    new Color(0, 0, 0, 0)));
            g2.fillRect(innerX + innerW - shadowDepth, innerY, shadowDepth, innerH);

            g2.dispose();
        }
    }
}

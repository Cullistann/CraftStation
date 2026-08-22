package ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import core.ConfigManager;
import core.Lang;
import core.SpeedTest;

public final class SettingsPanel extends McPanel {

    private final ConfigManager configManager;
    private final JLabel globalStatusLabel;

    // RAM Manager
    private JSlider ramSlider;
    private JLabel ramLabel;

    // Config Tracking
    private Map<String, String> currentConfig;
    private Map<String, Component> propertyEditors = new HashMap<>();

    // YML Editor Areas
    private JTextArea spigotArea;
    private JTextArea bukkitArea;
    private JTextArea purpurArea;

    // Speed Test UI
    private JLabel speedLabel;
    private McButton testBtn;

    public SettingsPanel(String serverDir) {
        super(new BorderLayout(), false);
        this.configManager = ConfigManager.getInstance(serverDir);
        setBorder(new EmptyBorder(12, 14, 12, 14));

        globalStatusLabel = new JLabel("  ");
        globalStatusLabel.setFont(new Font("Inter", Font.ITALIC, 13));
        globalStatusLabel.setForeground(McTheme.TEXT_SECONDARY);

        currentConfig = new HashMap<>();

        JPanel mainContent = new JPanel();
        mainContent.setLayout(new BoxLayout(mainContent, BoxLayout.Y_AXIS));
        mainContent.setOpaque(false);
        mainContent.setBorder(new EmptyBorder(0, 5, 0, 5)); // Tam ekran ortalaması için sağ-sol padding eşitlendi

        // 1. DİL AYARLARI
        mainContent.add(createPanelSettingsCard());
        mainContent.add(Box.createVerticalStrut(15));

        // 2. RAM VE HIZ TESTİ KARTI
        mainContent.add(createHardwareCard());
        mainContent.add(Box.createVerticalStrut(20));

        // 3. SERVER.PROPERTIES MOZAİK ARAYÜZÜ
        JLabel propsTitle = new JLabel(Lang.get("SET_PROPS_TITLE"));
        propsTitle.setFont(new Font("Inter", Font.BOLD, 17));
        propsTitle.setForeground(McTheme.TEXT_TITLE);
        propsTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainContent.add(propsTitle);
        mainContent.add(Box.createVerticalStrut(10));

        JPanel propLayout = new JPanel(new WrapLayout(FlowLayout.CENTER, 15, 15));
        propLayout.setOpaque(false);
        propLayout.setAlignmentX(Component.CENTER_ALIGNMENT);

        // -- Harika Kutuların Eklenmesi --
        propLayout.add(
                createPropCard(Lang.get("SET_MAX_PLAYERS"), "max-players", createSpinner("max-players", 1, 10000, 20)));
        propLayout.add(createPropCard(Lang.get("SET_GAMEMODE"), "gamemode",
                createCombo("gamemode", new String[] { "survival", "creative", "adventure", "spectator" })));
        propLayout.add(createPropCard(Lang.get("SET_DIFFICULTY"), "difficulty",
                createCombo("difficulty", new String[] { "peaceful", "easy", "normal", "hard" })));
        propLayout.add(createPropCard(Lang.get("SET_ONLINE_MODE"), "online-mode", createCheckbox("online-mode", true)));
        propLayout.add(createPropCard(Lang.get("SET_PVP"), "pvp", createCheckbox("pvp", true)));
        propLayout.add(createPropCard(Lang.get("SET_CMD_BLOCKS"), "enable-command-blocks",
                createCheckbox("enable-command-blocks", false)));
        propLayout.add(createPropCard(Lang.get("SET_FLIGHT"), "allow-flight", createCheckbox("allow-flight", false)));
        propLayout.add(
                createPropCard(Lang.get("SET_VIEW_DIST"), "view-distance", createSpinner("view-distance", 2, 32, 10)));
        propLayout.add(createPropCard(Lang.get("SET_SIM_DIST"), "simulation-distance",
                createSpinner("simulation-distance", 2, 32, 10)));
        propLayout.add(
                createPropCard(Lang.get("SET_FORCE_GM"), "force-gamemode", createCheckbox("force-gamemode", false)));
        propLayout.add(createPropCard(Lang.get("SET_WHITELIST"), "white-list", createCheckbox("white-list", false)));

        mainContent.add(propLayout);
        mainContent.add(Box.createVerticalStrut(20));

        // 4. GELİŞMİŞ DOSYA DÜZENLEYİCİSİ (AKORDEON ÇEKMECELER)
        JLabel advTitle = new JLabel(Lang.get("SET_ADV_EDITOR"));
        advTitle.setFont(new Font("Inter", Font.BOLD, 17));
        advTitle.setForeground(McTheme.TEXT_TITLE);
        advTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainContent.add(advTitle);
        mainContent.add(Box.createVerticalStrut(10));

        spigotArea = createYmlArea();
        mainContent.add(new McAccordion("spigot.yml " + Lang.get("SET_YML_EDITOR"), wrapInScroll(spigotArea, 300)));
        mainContent.add(Box.createVerticalStrut(10));

        bukkitArea = createYmlArea();
        mainContent.add(new McAccordion("bukkit.yml " + Lang.get("SET_YML_EDITOR"), wrapInScroll(bukkitArea, 300)));
        mainContent.add(Box.createVerticalStrut(10));

        purpurArea = createYmlArea();
        mainContent.add(new McAccordion("purpur.yml " + Lang.get("SET_YML_EDITOR"), wrapInScroll(purpurArea, 300)));
        mainContent.add(Box.createVerticalStrut(15));

        mainContent.add(Box.createVerticalGlue());

        JScrollPane mainScroll = new JScrollPane(mainContent);
        mainScroll.setBorder(null);
        mainScroll.setOpaque(false);
        mainScroll.getViewport().setOpaque(false);
        mainScroll.getVerticalScrollBar().setUnitIncrement(16);
        McTheme.styleScroll(mainScroll);

        add(mainScroll, BorderLayout.CENTER);

        // ALT KISIM (KAYDET VE DURUM)
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

        McButton saveBtn = new McButton("  " + Lang.get("BTN_SAVE_ALL") + "  ");
        saveBtn.setPreferredSize(new Dimension(200, 36));
        saveBtn.setFont(new Font("Inter", Font.BOLD, 14));
        saveBtn.addActionListener(e -> saveAllConfigsAsync(saveBtn));

        bottomPanel.add(globalStatusLabel, BorderLayout.WEST);
        bottomPanel.add(saveBtn, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);

        // Arka planda asenkron yükleme
        loadConfigsAsync();
    }

    private JPanel createPanelSettingsCard() {
        McCard card = new McCard(new FlowLayout(FlowLayout.CENTER, 15, 10));
        card.setBorder(McTheme.cardBorder());
        card.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.setMaximumSize(new Dimension(695, 75));

        JLabel langTitle = new JLabel(Lang.get("SET_LANG") + ":");
        langTitle.setFont(new Font("Inter", Font.BOLD, 14));
        langTitle.setForeground(McTheme.TEXT_TITLE);

        JComboBox<Lang.Language> langCombo = new JComboBox<>(Lang.Language.values());
        langCombo.setSelectedItem(Lang.getInstance().getCurrentLanguage());
        langCombo.setFont(new Font("Inter", Font.BOLD, 13));
        langCombo.setBackground(new Color(45, 49, 54));
        langCombo.setForeground(Color.WHITE);
        langCombo.setFocusable(false);

        langCombo.addActionListener(e -> {
            Lang.Language selected = (Lang.Language) langCombo.getSelectedItem();
            if (Lang.getInstance().getCurrentLanguage() != selected) {
                Lang.getInstance().setLanguage(selected);
                try {
                    configManager.setPanelProperty("language", selected.getCode());
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                globalStatusLabel.setText(Lang.get("SET_LANG_UPDATED"));
                globalStatusLabel.setForeground(McTheme.WARNING);
            }
        });

        McButton changeDirBtn = new McButton(Lang.get("BTN_CHANGE_LOCATION"));
        changeDirBtn.setFont(new Font("Inter", Font.BOLD, 12));
        changeDirBtn.addActionListener(e -> {
            String currentPath = core.UserPreferences.getInstance().getString(core.UserPreferences.KEY_LAST_SERVER_DIR, "");
            Window parentWindow = SwingUtilities.getWindowAncestor(this);
            Frame owner = parentWindow instanceof Frame ? (Frame) parentWindow : null;
            ServerSelectPrompt prompt = new ServerSelectPrompt(owner, currentPath, newPath -> {
                globalStatusLabel.setText(Lang.get("SET_LANG_UPDATED"));
                globalStatusLabel.setForeground(McTheme.WARNING);
            });
            prompt.setVisible(true);
        });

        card.add(langTitle);
        card.add(langCombo);
        card.add(Box.createHorizontalStrut(15));
        card.add(changeDirBtn);
        return card;
    }

    private JPanel createHardwareCard() {
        JPanel container = new JPanel(new GridLayout(1, 2, 15, 0));
        container.setOpaque(false);
        container.setAlignmentX(Component.CENTER_ALIGNMENT);
        container.setMaximumSize(new Dimension(695, 110)); // İki kart genişliği (340x2 + 15), esnememesi için

        // RAM Yönetimi
        McCard ramCard = new McCard(new BorderLayout(10, 10));
        ramCard.setBorder(McTheme.cardBorder());

        JLabel ramTitle = new JLabel(Lang.get("SET_RAM_TITLE"));
        ramTitle.setFont(new Font("Inter", Font.BOLD, 14));
        ramTitle.setForeground(McTheme.TEXT_TITLE);

        ramLabel = new JLabel("...");
        ramLabel.setFont(new Font("Inter", Font.BOLD, 18));
        ramLabel.setForeground(new Color(134, 206, 255));

        ramSlider = new JSlider(JSlider.HORIZONTAL, 1, 32, 4); // Default 4, async yüklenecek
        ramSlider.setOpaque(false);
        ramSlider.setMajorTickSpacing(4);
        ramSlider.setPaintTicks(true);
        ramSlider.addChangeListener(e -> ramLabel.setText(ramSlider.getValue() + " GB"));
        ramLabel.setText(ramSlider.getValue() + " GB");

        JPanel ramTop = new JPanel(new BorderLayout());
        ramTop.setOpaque(false);
        ramTop.add(ramTitle, BorderLayout.WEST);
        ramTop.add(ramLabel, BorderLayout.EAST);

        ramCard.add(ramTop, BorderLayout.NORTH);
        ramCard.add(ramSlider, BorderLayout.CENTER);

        // Hız Testi
        McCard speedCard = new McCard(new BorderLayout(10, 10));
        speedCard.setBorder(McTheme.cardBorder());

        JLabel speedTitle = new JLabel(Lang.get("SET_SPEED_TITLE"));
        speedTitle.setFont(new Font("Inter", Font.BOLD, 14));
        speedTitle.setForeground(McTheme.TEXT_TITLE);

        speedLabel = new JLabel(Lang.get("SET_SPEED_IDLE"));
        speedLabel.setFont(new Font("Inter", Font.BOLD, 18));
        speedLabel.setForeground(McTheme.WARNING);

        testBtn = new McButton(Lang.get("SET_SPEED_START"));
        testBtn.setPreferredSize(new Dimension(120, 28));
        testBtn.setFont(new Font("Inter", Font.BOLD, 12));
        testBtn.addActionListener(e -> runSpeedTest());

        JPanel speedTop = new JPanel(new BorderLayout());
        speedTop.setOpaque(false);
        speedTop.add(speedTitle, BorderLayout.WEST);
        speedTop.add(testBtn, BorderLayout.EAST);

        speedCard.add(speedTop, BorderLayout.NORTH);
        speedCard.add(speedLabel, BorderLayout.CENTER);

        container.add(ramCard);
        container.add(speedCard);
        return container;
    }

    private McCard createPropCard(String titleTitle, String key, Component editor) {
        McCard card = new McCard(new BorderLayout(10, 0));
        card.setBorder(McTheme.cardBorder());
        card.setPreferredSize(new Dimension(340, 75));

        JLabel title = new JLabel(titleTitle);
        title.setFont(new Font("Inter", Font.BOLD, 14));
        title.setForeground(McTheme.TEXT_TITLE);

        JLabel subLine = new JLabel(key);
        subLine.setFont(new Font("Consolas", Font.PLAIN, 11));
        subLine.setForeground(McTheme.TEXT_SECONDARY);

        // Sol taraf: İçerik
        JPanel leftInner = new JPanel();
        leftInner.setLayout(new BoxLayout(leftInner, BoxLayout.Y_AXIS));
        leftInner.setOpaque(false);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        subLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        leftInner.add(title);
        leftInner.add(Box.createVerticalStrut(2));
        leftInner.add(subLine);

        // Sol taraf: Dikey ortalama ve tam sola yaslama
        JPanel left = new JPanel(new GridBagLayout());
        left.setOpaque(false);
        left.add(leftInner);

        card.add(left, BorderLayout.WEST);

        // Sağ taraf: Editor dikey ortalama ve tam sağa yaslama
        if (editor != null) {
            JPanel right = new JPanel(new GridBagLayout());
            right.setOpaque(false);
            right.add(editor);
            card.add(right, BorderLayout.EAST);
        }

        propertyEditors.put(key, editor);
        return card;
    }

    private JComboBox<String> createCombo(String key, String[] items) {
        JComboBox<String> combo = new JComboBox<>(items);
        combo.setPreferredSize(new Dimension(140, 28));
        combo.setBackground(McTheme.INPUT_BG);
        combo.setForeground(Color.WHITE);
        String val = currentConfig.getOrDefault(key, items[0]);
        combo.setSelectedItem(val);
        return combo;
    }

    private JCheckBox createCheckbox(String key, boolean defValue) {
        JCheckBox box = new JCheckBox();
        box.setOpaque(false);
        boolean val = Boolean.parseBoolean(currentConfig.getOrDefault(key, String.valueOf(defValue)));
        box.setSelected(val);
        return box;
    }

    private JSpinner createSpinner(String key, int min, int max, int defValue) {
        int val = defValue;
        try {
            val = Integer.parseInt(currentConfig.getOrDefault(key, String.valueOf(defValue)));
        } catch (Exception e) {
        }
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(val, min, max, 1));
        spinner.setPreferredSize(new Dimension(80, 28));
        spinner.getEditor().getComponent(0).setBackground(McTheme.INPUT_BG);
        spinner.getEditor().getComponent(0).setForeground(Color.WHITE);
        return spinner;
    }

    private JTextArea createYmlArea() {
        JTextArea area = new JTextArea();
        area.setFont(new Font("Consolas", Font.PLAIN, 13));
        area.setBackground(McTheme.INPUT_BG);
        area.setForeground(new Color(220, 220, 225));
        area.setCaretColor(Color.WHITE);
        area.setMargin(new Insets(8, 8, 8, 8));
        area.setTabSize(2);
        area.setText("Loading...");
        return area;
    }

    private JScrollPane wrapInScroll(Component comp, int height) {
        JScrollPane sp = new JScrollPane(comp);
        sp.setPreferredSize(new Dimension(800, height));
        McTheme.styleScroll(sp);
        return sp;
    }

    private void runSpeedTest() {
        testBtn.setEnabled(false);
        speedLabel.setText(Lang.get("SET_SPEED_CALC"));
        speedLabel.setForeground(McTheme.TEXT_SECONDARY);

        SpeedTest.runTest(new SpeedTest.SpeedTestListener() {
            @Override
            public void onProgress(int percent, double currentMbps) {
                SwingUtilities.invokeLater(() -> {
                    speedLabel.setText(String.format("%% %d | %.2f Mbps", percent, currentMbps));
                });
            }

            @Override
            public void onComplete(double finalMbps, long pingMs) {
                SwingUtilities.invokeLater(() -> {
                    speedLabel.setText(String.format("%.2f Mbps / %d ms Ping", finalMbps, pingMs));
                    speedLabel.setForeground(McTheme.SUCCESS);
                    testBtn.setEnabled(true);
                });
            }

            @Override
            public void onError(String err) {
                SwingUtilities.invokeLater(() -> {
                    speedLabel.setText(Lang.get("SET_SPEED_FAIL"));
                    speedLabel.setForeground(McTheme.DANGER);
                    testBtn.setEnabled(true);
                });
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void loadConfigsAsync() {
        globalStatusLabel.setText("  Yükleniyor...");
        globalStatusLabel.setForeground(McTheme.TEXT_SECONDARY);
        
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private Map<String, String> loadedConfig;
            private String loadedSpigot;
            private String loadedBukkit;
            private String loadedPurpur;
            private int loadedRam;

            @Override
            protected Void doInBackground() throws Exception {
                loadedConfig = configManager.loadProperties("server.properties");
                loadedSpigot = configManager.loadRawText("spigot.yml");
                loadedBukkit = configManager.loadRawText("bukkit.yml");
                loadedPurpur = configManager.loadRawText("purpur.yml");
                loadedRam = configManager.getServerRam();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    if (loadedConfig != null) {
                        currentConfig = loadedConfig;
                        for (Map.Entry<String, Component> entry : propertyEditors.entrySet()) {
                            String key = entry.getKey();
                            Component c = entry.getValue();
                            String val = currentConfig.get(key);
                            if (val != null) {
                                if (c instanceof JComboBox) {
                                    ((JComboBox<String>) c).setSelectedItem(val);
                                } else if (c instanceof JCheckBox) {
                                    ((JCheckBox) c).setSelected(Boolean.parseBoolean(val));
                                } else if (c instanceof JSpinner) {
                                    try {
                                        ((JSpinner) c).setValue(Integer.parseInt(val));
                                    } catch (Exception ignored) {}
                                }
                            }
                        }
                    }
                    if (loadedSpigot != null) spigotArea.setText(loadedSpigot);
                    if (loadedBukkit != null) bukkitArea.setText(loadedBukkit);
                    if (loadedPurpur != null) purpurArea.setText(loadedPurpur);
                    if (loadedRam > 0) {
                        ramSlider.setValue(loadedRam);
                        ramLabel.setText(loadedRam + " GB");
                    }
                    globalStatusLabel.setText("  ");
                } catch (Exception ex) {
                    globalStatusLabel.setText("  Yükleme Hatası: " + ex.getMessage());
                    globalStatusLabel.setForeground(McTheme.DANGER);
                }
            }
        };
        worker.execute();
    }

    private static final String LOADING_PLACEHOLDER = "Loading...";

    private void saveAllConfigsAsync(McButton saveBtn) {
        // H10 fix: concurrent save'i önle
        saveBtn.setEnabled(false);
        String originalBtnText = saveBtn.getText();

        // Collect property editors back to currentConfig
        for (Map.Entry<String, Component> entry : propertyEditors.entrySet()) {
            String key = entry.getKey();
            Component c = entry.getValue();
            if (c instanceof JComboBox) {
                Object selected = ((JComboBox<?>) c).getSelectedItem();
                if (selected != null)
                    currentConfig.put(key, selected.toString());
            } else if (c instanceof JCheckBox) {
                currentConfig.put(key, String.valueOf(((JCheckBox) c).isSelected()));
            } else if (c instanceof JSpinner) {
                currentConfig.put(key, String.valueOf(((JSpinner) c).getValue()));
            }
        }

        String spigotContent = spigotArea.getText();
        String bukkitContent = bukkitArea.getText();
        String purpurContent = purpurArea.getText();
        int selectedRam = ramSlider.getValue();

        globalStatusLabel.setText("  " + Lang.get("SET_SAVING"));
        globalStatusLabel.setForeground(McTheme.WARNING);

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                configManager.saveProperties("server.properties", currentConfig);

                // C2 fix: "Loading..." placeholder'ını kaydetmeyi önle
                if (!spigotContent.trim().isEmpty() && !spigotContent.trim().equals(LOADING_PLACEHOLDER))
                    configManager.saveRawText("spigot.yml", spigotContent);
                if (!bukkitContent.trim().isEmpty() && !bukkitContent.trim().equals(LOADING_PLACEHOLDER))
                    configManager.saveRawText("bukkit.yml", bukkitContent);
                if (!purpurContent.trim().isEmpty() && !purpurContent.trim().equals(LOADING_PLACEHOLDER))
                    configManager.saveRawText("purpur.yml", purpurContent);

                configManager.setServerRam(selectedRam);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get(); // Hata varsa burada fırlatılır
                    currentConfig = configManager.loadProperties("server.properties"); // Refresh
                    globalStatusLabel.setText("  " + Lang.get("SET_SAVED"));
                    globalStatusLabel.setForeground(McTheme.SUCCESS);
                    
                    saveBtn.setText("  ✓ " + Lang.get("SET_SAVED") + "  ");
                    Timer btnTimer = new Timer(2000, e -> saveBtn.setText(originalBtnText));
                    btnTimer.setRepeats(false);
                    btnTimer.start();
                } catch (Exception ex) {
                    globalStatusLabel.setText("  " + Lang.get("SET_SAVE_ERROR") + ": " + ex.getMessage());
                    globalStatusLabel.setForeground(McTheme.DANGER);
                }

                // H10 fix: save bitince butonu tekrar aktif et
                saveBtn.setEnabled(true);

                Timer t = new Timer(5000, e -> {
                    globalStatusLabel.setForeground(McTheme.TEXT_SECONDARY);
                    globalStatusLabel.setText("  ");
                });
                t.setRepeats(false);
                t.start();
            }
        };
        worker.execute();
    }
}

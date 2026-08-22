package ui;

import core.ConfigManager;
import core.Lang;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.File;

public final class LanguagePrompt extends JDialog {
    private boolean completed = false;
    private McLabel titleLabel;
    private McLabel descLabel;
    private StoneButton btnContinue;

    public LanguagePrompt(String serverDir, ConfigManager config) {
        super((Frame) null, "CraftStation", true);

        setSize(480, 280);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setResizable(false);
        setLayout(new BorderLayout());

        McPanel mainPanel = new McPanel(null, true);
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(25, 30, 25, 30));

        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        titlePanel.setOpaque(false);

        try {
            File f = new File(serverDir, "server-icon.png");
            if (f.exists()) {
                Image img = new ImageIcon(f.getAbsolutePath()).getImage().getScaledInstance(40, 40, Image.SCALE_SMOOTH);
                titlePanel.add(new JLabel(new ImageIcon(img)));
            }
        } catch (Exception ignored) {
        }

        titleLabel = new McLabel(Lang.get("LANG_TITLE"), SwingConstants.CENTER);
        titleLabel.setFont(McTheme.getMinecraftFont(22, Font.BOLD));
        titleLabel.setForeground(new Color(255, 255, 85));
        titlePanel.add(titleLabel);

        descLabel = new McLabel(Lang.get("LANG_DESC"), SwingConstants.CENTER);
        descLabel.setFont(McTheme.getMinecraftFont(14, Font.PLAIN));
        descLabel.setForeground(new Color(170, 170, 170));
        descLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        mainPanel.add(titlePanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        mainPanel.add(descLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));

        // Çekmece gibi açılan Minecraft stili JComboBox (Dikdörtgen Seçici)
        JComboBox<Lang.Language> langCombo = new JComboBox<>(Lang.Language.values());
        langCombo.setSelectedItem(Lang.ENGLISH); // Varsayılan İngilizce olsun diyorsa English kalır
        langCombo.setFont(McTheme.getMinecraftFont(15, Font.BOLD));
        langCombo.setPreferredSize(new Dimension(280, 40));
        langCombo.setMaximumSize(new Dimension(280, 40));
        langCombo.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Stil
        langCombo.setBackground(new Color(90, 90, 90));
        langCombo.setForeground(Color.WHITE);
        langCombo.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
        langCombo.setFocusable(false);

        // Dil değiştiğinde etiketleri canlı güncelle (hot-swap on selection)
        langCombo.addActionListener(e -> {
            Lang.Language selected = (Lang.Language) langCombo.getSelectedItem();
            Lang.getInstance().setLanguage(selected);
            updateUITexts();
        });

        mainPanel.add(langCombo);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 25)));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        btnPanel.setOpaque(false);

        btnContinue = new StoneButton(Lang.get("BTN_CONTINUE"));
        btnContinue.setPreferredSize(new Dimension(280, 42));
        btnContinue.addActionListener(e -> {
            Lang.Language selected = (Lang.Language) langCombo.getSelectedItem();
            Lang.getInstance().setLanguage(selected);
            try {
                config.setPanelProperty("language", selected.getCode());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            completed = true;
            dispose();
        });

        btnPanel.add(btnContinue);
        mainPanel.add(btnPanel);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });

        add(mainPanel, BorderLayout.CENTER);

        // M7 fix: mevcut dil ayarını koru, zorla English'e reset etme
        updateUITexts();
    }

    private void updateUITexts() {
        titleLabel.setText(Lang.get("LANG_TITLE"));
        descLabel.setText(Lang.get("LANG_DESC"));
        btnContinue.setText(Lang.get("BTN_CONTINUE"));
        repaint();
    }

    public boolean prompt() {
        setVisible(true);
        return completed;
    }

}

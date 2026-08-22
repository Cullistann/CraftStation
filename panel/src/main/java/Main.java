import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.util.function.Consumer;
import ui.LanguagePrompt;
import ui.MainFrame;
import ui.ServerSelectPrompt;
import core.ConfigManager;
import core.Lang;
import core.LoggingUtil;
import core.UserPreferences;

public class Main {

    private static final LoggingUtil logger = LoggingUtil.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            System.setProperty("flatlaf.useNativeLibrary", "false");
            System.setProperty("flatlaf.useWindowDecorations", "true");
            JFrame.setDefaultLookAndFeelDecorated(true);
            FlatDarkLaf.setup();
            UIManager.put("TitlePane.iconSize", new java.awt.Dimension(26, 26));
            UIManager.put("TitlePane.background", new Color(18, 20, 24));
            UIManager.put("TitlePane.inactiveBackground", new Color(18, 20, 24));
            UIManager.put("TitlePane.foreground", new Color(200, 200, 200));
            UIManager.put("TitlePane.inactiveForeground", new Color(150, 150, 150));
            UIManager.put("TabbedPane.selectedBackground", new Color(46, 134, 193));
            UIManager.put("TabbedPane.showTabSeparators", true);
            UIManager.put("Button.arc", 8);
            UIManager.put("Component.arc", 8);
            UIManager.put("TextComponent.arc", 8);
            UIManager.put("defaultFont", new Font("Inter", Font.PLAIN, 14));
        } catch (Exception e) {
            logger.error("FlatLaf başlatılamadı", e);
        }

        SwingUtilities.invokeLater(() -> {
            logger.debug("SwingUtilities.invokeLater başladı");
            resolveServerDirOrPrompt(serverDir -> {
                logger.info("serverDir = " + serverDir);

                ConfigManager config = ConfigManager.getInstance(serverDir);
                String savedLanguage = safeTrim(config.getPanelProperty("language", ""));
                logger.debug("savedLanguage = " + savedLanguage);

                if (savedLanguage.isEmpty()) {
                    logger.debug("LanguagePrompt açılıyor");
                    LanguagePrompt langPrompt = new LanguagePrompt(serverDir, config);
                    langPrompt.prompt();
                    logger.debug("LanguagePrompt kapandı");
                } else {
                    logger.debug("Dil ayarlanıyor: " + savedLanguage);
                    applySavedLanguage(savedLanguage);
                }

                logger.info("Panel modu başlatılıyor");
                try {
                    MainFrame frame = new MainFrame(serverDir);
                    logger.debug("MainFrame oluşturuldu");
                    frame.setVisible(true);
                    logger.info("MainFrame gösterildi");
                } catch (Exception ex) {
                    logger.error("MainFrame başlatılamadı", ex);
                    JOptionPane.showMessageDialog(null, Lang.get("MSG_ERROR") + ": " + ex.getMessage(), Lang.get("ERR_START_PANEL"),
                            JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                }
            });
        });
    }

    private static void resolveServerDirOrPrompt(Consumer<String> callback) {
        String savedDir = UserPreferences.getInstance().getString(UserPreferences.KEY_LAST_SERVER_DIR, "");
        if (!savedDir.isBlank()) {
            File file = new File(savedDir);
            if (file.exists() && file.isDirectory() && ServerSelectPrompt.isValidServerFolder(file)) {
                callback.accept(file.getAbsolutePath());
                return;
            } else {
                // Clear invalid or stale server directory
                UserPreferences.getInstance().setString(UserPreferences.KEY_LAST_SERVER_DIR, "");
            }
        }

        String panelDir = System.getProperty("user.dir", ".");
        File currentDir = new File(panelDir).getAbsoluteFile();
        File candidateDir = currentDir;
        if ("panel".equalsIgnoreCase(currentDir.getName()) && currentDir.getParentFile() != null) {
            candidateDir = currentDir.getParentFile();
        }

        if (ServerSelectPrompt.isValidServerFolder(candidateDir)) {
            String resolved = candidateDir.getAbsolutePath();
            UserPreferences.getInstance().setString(UserPreferences.KEY_LAST_SERVER_DIR, resolved);
            callback.accept(resolved);
            return;
        }

        ServerSelectPrompt prompt = new ServerSelectPrompt(null, candidateDir.getAbsolutePath(), callback);
        prompt.setVisible(true);
        if (!prompt.isSelectionConfirmed()) {
            System.exit(0);
        }
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static void applySavedLanguage(String savedLanguage) {
        Lang lang = Lang.getInstance();
        Lang.Language selectedLanguage = lang.fromCode(savedLanguage);
        if (selectedLanguage == null) {
            logger.warn("Geçersiz kayıtlı dil kodu bulundu, varsayılan dile dönülüyor: " + savedLanguage);
            return;
        }
        lang.setLanguage(selectedLanguage);
    }
}

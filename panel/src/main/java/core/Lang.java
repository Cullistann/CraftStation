package core;
 
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
 
/**
 * Modern internationalization service with Java 25 features.
 * Uses interfaces and pattern matching.
 */
public final class Lang {
 
    // Simple language representation
    public interface Language {
        String code();
 
        String displayName();
 
        Map<String, String> strings();
 
        // Backward compatible named constants removed to fix circular initialization nulls
 
        // Convenience method for getCode() compatibility
        default String getCode() {
            return code();
        }
 
        // Static method to get all language values
        static Language[] values() {
            return new Language[] { Lang.ENGLISH, Lang.TURKISH, Lang.GERMAN, Lang.FRENCH, Lang.SPANISH, Lang.RUSSIAN };
        }
 
        // Static method to get language from code
        static Language fromCode(String code) {
            return getInstance().fromCode(code);
        }
    }
 
    // Language implementations
    public static final Language ENGLISH = new Language() {
        @Override
        public String code() {
            return "en";
        }
 
        @Override
        public String displayName() {
            return "English";
        }
 
        @Override
        public Map<String, String> strings() {
            return ENGLISH_STRINGS;
        }
        @Override
        public String toString() {
            return displayName();
        }
    };
 
    public static final Language TURKISH = new Language() {
        @Override
        public String code() {
            return "tr";
        }
 
        @Override
        public String displayName() {
            return "Türkçe";
        }
 
        @Override
        public Map<String, String> strings() {
            return TURKISH_STRINGS;
        }
        @Override
        public String toString() {
            return displayName();
        }
    };
 
    public static final Language GERMAN = new Language() {
        @Override
        public String code() {
            return "de";
        }
 
        @Override
        public String displayName() {
            return "Deutsch";
        }
 
        @Override
        public Map<String, String> strings() {
            return GERMAN_STRINGS;
        }
        @Override
        public String toString() {
            return displayName();
        }
    };
 
    public static final Language FRENCH = new Language() {
        @Override
        public String code() {
            return "fr";
        }
 
        @Override
        public String displayName() {
            return "Français";
        }
 
        @Override
        public Map<String, String> strings() {
            return FRENCH_STRINGS;
        }
        @Override
        public String toString() {
            return displayName();
        }
    };
 
    public static final Language SPANISH = new Language() {
        @Override
        public String code() {
            return "es";
        }
 
        @Override
        public String displayName() {
            return "Español";
        }
 
        @Override
        public Map<String, String> strings() {
            return SPANISH_STRINGS;
        }
        @Override
        public String toString() {
            return displayName();
        }
    };
 
    public static final Language RUSSIAN = new Language() {
        @Override
        public String code() {
            return "ru";
        }
 
        @Override
        public String displayName() {
            return "Русский";
        }
 
        @Override
        public Map<String, String> strings() {
            return RUSSIAN_STRINGS;
        }
        @Override
        public String toString() {
            return displayName();
        }
    };
 
    // Singleton instance
    private static volatile Lang instance;
 
    // Current language
    private volatile Language currentLanguage;
 
    // All available languages
    private final Map<String, Language> languages = new ConcurrentHashMap<>();
 
    // String databases
    private static final Map<String, String> ENGLISH_STRINGS = new ConcurrentHashMap<>();
    private static final Map<String, String> TURKISH_STRINGS = new ConcurrentHashMap<>();
    private static final Map<String, String> GERMAN_STRINGS = new ConcurrentHashMap<>();
    private static final Map<String, String> FRENCH_STRINGS = new ConcurrentHashMap<>();
    private static final Map<String, String> SPANISH_STRINGS = new ConcurrentHashMap<>();
    private static final Map<String, String> RUSSIAN_STRINGS = new ConcurrentHashMap<>();
 
    static {
        initializeStrings();
    }
 
    /**
     * Private constructor for singleton pattern.
     */
    private Lang() {
        // Initialize languages
        languages.put("en", ENGLISH);
        languages.put("tr", TURKISH);
        languages.put("de", GERMAN);
        languages.put("fr", FRENCH);
        languages.put("es", SPANISH);
        languages.put("ru", RUSSIAN);
 
        // Default language
        currentLanguage = TURKISH;
    }
 
    /**
     * Singleton factory method.
     */
    public static Lang getInstance() {
        if (instance == null) {
            synchronized (Lang.class) {
                if (instance == null) {
                    instance = new Lang();
                }
            }
        }
        return instance;
    }
 
    /**
     * Resets the singleton instance (for testing).
     */
    static void resetInstance() {
        instance = null;
    }
 
    /**
     * Sets the current language by code.
     */
    public void setLanguage(String code) {
        Language lang = languages.get(code.toLowerCase());
        if (lang != null) {
            currentLanguage = lang;
        }
    }
 
    /**
     * Sets the current language by enum.
     */
    public void setLanguage(Language language) {
        if (language != null) {
            currentLanguage = language;
        }
    }
 
    /**
     * Gets the current language.
     */
    public Language getCurrentLanguage() {
        return currentLanguage;
    }
 
    /**
     * Gets a localized string by key.
     */
    public String getString(String key) {
        Objects.requireNonNull(key, "Key cannot be null");
 
        // Try current language first
        String value = currentLanguage.strings().get(key);
        if (value != null) {
            return value;
        }
 
        // Fallback to English
        value = ENGLISH_STRINGS.get(key);
        if (value != null) {
            return value;
        }
 
        // Return key if not found
        return key;
    }
 
    /**
     * Gets a formatted localized string.
     */
    public String getString(String key, Object... args) {
        String template = getString(key);
        return String.format(template, args);
    }
 
    /**
     * Gets all available languages.
     */
    public Collection<Language> getAvailableLanguages() {
        return List.copyOf(languages.values());
    }
 
    /**
     * Gets language by code.
     */
    public Language fromCode(String code) {
        if (code == null) return null;
        return languages.get(code.toLowerCase());
    }
 
    /**
     * Static convenience method for backward compatibility.
     * This provides the old Lang.get() API.
     */
    public static String get(String key) {
        return getInstance().getString(key);
    }
 
    /**
     * Static convenience method for backward compatibility.
     * This provides the old Lang.get() API with formatting.
     */
    public static String get(String key, Object... args) {
        return getInstance().getString(key, args);
    }
 
    /**
     * Static convenience method for backward compatibility.
     */
    public static void setLanguageStatic(Language language) {
        getInstance().setLanguage(language);
    }
 
    /**
     * Static convenience method for backward compatibility.
     */
    public static Language getCurrentLanguageStatic() {
        return getInstance().getCurrentLanguage();
    }
 
    /**
     * Static convenience method for backward compatibility.
     */
    public static Language fromCodeStatic(String code) {
        return getInstance().fromCode(code);
    }
 
    // String initialization
    private static void initializeStrings() {
        // Turkish strings
        TURKISH_STRINGS.putAll(Map.ofEntries(
                Map.entry("LANG_TITLE", "CraftStation"),
                Map.entry("LANG_DESC", "Hangi dili seçmek istersin?"),
                Map.entry("BTN_CONTINUE", "Devam Et"),
                Map.entry("LAUNCH_TITLE", "CraftStation Seçim Ekranı"),
                Map.entry("LAUNCH_DESC", "Sunucuyu Kontrol Paneli arayüzü ile başlatmak ister misiniz?"),
                Map.entry("BTN_YES_PANEL", "Evet, Paneli Başlat"),
                Map.entry("BTN_NO_PANEL", "Hayır, Paneli Başlatma"),
                Map.entry("APP_TITLE", "CraftStation"),
                Map.entry("EXIT_WARN_TITLE", "Çıkış Onayı"),
                Map.entry("EXIT_WARN_MSG",
                        "Sunucu hâlâ çalışıyor! Kapatırsanız sunucu da kapanacak.\nDevam etmek istiyor musunuz?"),
                Map.entry("TAB_DASHBOARD", "Gösterge"),
                Map.entry("TAB_CONSOLE", "Konsol"),
                Map.entry("TAB_PLAYERS", "Oyuncular"),
                Map.entry("TAB_SETTINGS", "Ayarlar"),
                Map.entry("TAB_BACKUP", "Yedekleme"),
                Map.entry("STAT_OFFLINE", "ÇEVRİMDIŞI"),
                Map.entry("STAT_ONLINE", "ÇEVRİMİÇİ"),
                Map.entry("STAT_STARTING", "BAŞLATILIYOR..."),
                Map.entry("STAT_STOPPING", "DURDURULUYOR..."),
                Map.entry("BTN_START", "Başlat"),
                Map.entry("BTN_STOP", "Durdur"),
                Map.entry("BTN_RESTART", "Yeniden Başlat"),
                Map.entry("CONFIRM_TITLE", "Onay"),
                Map.entry("STOP_WARN_MSG", "Sunucuyu durdurmak istediğinizden emin misiniz?"),
                Map.entry("LABEL_TPS", "TPS"),
                Map.entry("LABEL_PLAYERS", "Oyuncular"),
                Map.entry("LABEL_UPTIME", "Çalışma Süresi"),
                Map.entry("LABEL_RAM", "RAM"),
                Map.entry("LABEL_MAX_PLAYERS", "Maks. Oyuncu"),
                Map.entry("LABEL_RECENT_ERRORS", "Son Hatalar"),
                Map.entry("LABEL_ONLINE_PLAYERS", "Çevrimiçi Oyuncular"),
                Map.entry("ERR_BAT_NOT_FOUND", "Başlatma dosyası bulunamadı!"),
                Map.entry("ERR_START_FAIL", "Sunucu başlatılamadı"),
                Map.entry("ERR_READ", "Çıktı okunamadı"),
                Map.entry("ERR_CMD_SEND", "Komut gönderilemedi"),
                Map.entry("ERR_BAT_READ", "Başlatma dosyası okunamadı"),
                Map.entry("ERR_ALREADY_STARTING", "Sunucu zaten başlatılıyor"),
                Map.entry("LOG_SERVER_STOPPED", "Sunucu durduruldu"),
                Map.entry("LOG_SERVER_STARTED", "Sunucu başarıyla başlatıldı!"),
                Map.entry("LOG_SERVER_CRASH", "Sunucu beklenmedik şekilde kapandı"),
                Map.entry("LOG_FORCE_KILL", "Sunucu zorla kapatıldı"),
                Map.entry("LOG_RESTART_ABORTED", "Yeniden başlatma iptal edildi"),
                Map.entry("STAT_TPS", "TPS"),
                Map.entry("STAT_PLAYERS", "Oyuncular"),
                Map.entry("STAT_UPTIME", "Çalışma Süresi"),
                Map.entry("STAT_RAM", "RAM"),
                Map.entry("RECENT_ERRORS", "Son Hatalar"),
                Map.entry("NO_ERRORS_YET", "Hata Bulunamadı"),
                Map.entry("SEARCH_PLAYER", "Oyuncu ara..."),
                Map.entry("ERR_REASON_LABEL", "Sebep:"),
                Map.entry("ERR_FIX_LABEL", "Çözüm:"),
                Map.entry("ERR_RAW_LABEL", "Ham Satır:"),
                Map.entry("CHECK_AUTOSCROLL", "Otomatik Kaydırma"),
                Map.entry("BTN_CLEAR", "Temizle"),
                Map.entry("BTN_SEND", "Gönder"),
                Map.entry("LOG_PANEL_STARTED", "[Panel] CraftStation başlatıldı."),
                Map.entry("LOG_PANEL_HINT", "[Panel] Sunucuyu başlatmak için Gösterge sekmesini kullanın."),
                Map.entry("LOADING_PANEL", "Panel yükleniyor..."),
                Map.entry("LOADING_ICONS", "Simgeler yükleniyor"),
                Map.entry("LOADING_PANELS", "Paneller oluşturuluyor")));
 
        TURKISH_STRINGS.putAll(Map.ofEntries(
                Map.entry("PLAYERS_TITLE", "Oyuncular"),
                Map.entry("PLAYERS_COUNT", "Oyuncu"),
                Map.entry("BTN_KICK", "At"),
                Map.entry("BTN_BAN", "Yasakla"),
                Map.entry("BTN_OP", "OP Ver"),
                Map.entry("BTN_DEOP", "OP Al"),
                Map.entry("BTN_WHITELIST", "Whitelist"),
                Map.entry("BTN_REFRESH", "Yenile"),
                Map.entry("SET_LANG", "Dil"),
                Map.entry("SET_RAM_TITLE", "RAM"),
                Map.entry("SET_SPEED_TITLE", "Hız"),
                Map.entry("SET_SPEED_START", "Başlat"),
                Map.entry("SET_SPEED_CALC", "Hesaplanıyor..."),
                Map.entry("SET_SPEED_FAIL", "Başarısız!"),
                Map.entry("SET_LANG_UPDATED", "Dil değiştirildi. Değişikliklerin tam etki etmesi için paneli yeniden başlatın."),
                Map.entry("SET_SAVING", "Kaydediliyor..."),
                Map.entry("SET_SAVED", "Kaydedildi"),
                Map.entry("SET_SAVE_ERROR", "Kaydetme Hatası"),
                Map.entry("SET_SPEED_IDLE", "Boşta"),
                Map.entry("SET_PROPS_TITLE", "Sunucu Ayarları"),
                Map.entry("SET_MAX_PLAYERS", "Maks. Oyuncu"),
                Map.entry("SET_GAMEMODE", "Oyun Modu"),
                Map.entry("SET_DIFFICULTY", "Zorluk"),
                Map.entry("SET_ONLINE_MODE", "Online Mod"),
                Map.entry("SET_PVP", "PVP"),
                Map.entry("SET_CMD_BLOCKS", "Komut Blokları"),
                Map.entry("SET_FLIGHT", "Uçuş"),
                Map.entry("SET_VIEW_DIST", "Görüş Mesafesi"),
                Map.entry("SET_SIM_DIST", "Simülasyon Mesafesi"),
                Map.entry("BTN_SAVE_ALL", "Tümünü Kaydet"),
                Map.entry("BACKUP_TITLE", "Yedekleme"),
                Map.entry("BTN_BACKUP_NOW", "Şimdi Yedekle"),
                Map.entry("BACKUP_STATUS", "Yedekleme Durumu"),
                Map.entry("BACKUP_LAST", "Son Yedekleme"),
                Map.entry("BACKUP_NONE", "Henüz yedek yok"),
                Map.entry("CONSOLE_TITLE", "Konsol"),
                Map.entry("SET_FORCE_GM", "Oyun Modunu Zorla"),
                Map.entry("SET_WHITELIST", "Whitelist"),
                Map.entry("SET_ADV_EDITOR", "Gelişmiş Yapılandırma Editörü"),
                Map.entry("SET_YML_EDITOR", "YAML Editörü"),
                Map.entry("BTN_CREATE_BACKUP", "Yedek Oluştur"),
                Map.entry("NO_BACKUPS_YET", "Henüz yedek yok"),
                Map.entry("BTN_DELETE_BACKUP", "Sil"),
                Map.entry("BTN_RESTORE_BACKUP", "Geri Yükle"),
                Map.entry("LANG_SELECT", "Dil Seç"),
                Map.entry("SET_LANG_LABEL", "Dil:"),
                Map.entry("WARN_TITLE", "Uyarı"),
                Map.entry("WARN_SELECT_BACKUP", "Lütfen listeden bir yedek seçin."),
                Map.entry("WARN_DELETE_BACKUP", "Bu yedeği silmek istediğinizden emin misiniz?"),
                Map.entry("WARN_RESTORE_CONFIRM", "Bu yedeği geri yüklemek istediğinizden emin misiniz? Mevcut dünyalarınız ve ayarlarınız geri yüklenen yedekle değiştirilecek."),
                Map.entry("WARN_SELECT_PLAYER", "Lütfen listeden bir oyuncu seçin."),
                Map.entry("PROMPT_KICK", "Oyuncuyu atmak için bir sebep girin (isteğe bağlı):"),
                Map.entry("PROMPT_BAN", "Oyuncuyu yasaklamak için bir sebep girin (isteğe bağlı):"),
                Map.entry("MSG_BACKUP_CREATING", "Yedek oluşturuluyor..."),
                Map.entry("MSG_RESTORING", "Yedek geri yükleniyor..."),
                Map.entry("BTN_DELETE", "Sil"),
                Map.entry("DELETE_CONFIRM_TITLE", "Yedeği Sil"),
                Map.entry("MSG_BACKUP_DELETED", "Yedek başarıyla silindi."),
                Map.entry("MSG_BACKUP_ERROR", "Yedekleme hatası:"),
                Map.entry("MSG_RESTORE_SUCCESS", "Yedek başarıyla geri yüklendi."),
                Map.entry("MSG_RESTORE_ERROR", "Geri yükleme hatası:"),
                Map.entry("WARN_BACKUP_RUNNING", "Sunucu çalışırken yedekleme işlemi yapmak veri kaybına yol açabilir. Devam etmek istiyor musunuz?"),
                Map.entry("WARN_RESTORE_RUNNING", "Sunucu çalışırken geri yükleme yapılamaz. Lütfen önce sunucuyu durdurun."),
                Map.entry("MSG_BACKUP_SUCCESS", "Yedek başarıyla oluşturuldu:"),
                Map.entry("MSG_REFRESHING", "Yenileniyor..."),
                Map.entry("MSG_ERROR", "Hata"),
                Map.entry("ERR_START_PANEL", "Paneli başlatamıyorum"),
                Map.entry("TAB_SERVER", "Sunucu"),
                Map.entry("SET_SERVER_TITLE", "Sunucu Ayarları"),
                Map.entry("SET_SERVER_NAME", "Sunucu Adı"),
                Map.entry("SET_SERVER_MOTD", "Sunucu Açıklaması (MOTD)"),
                Map.entry("SET_SERVER_LOGO", "Sunucu Logosu"),
                Map.entry("BTN_CHANGE_LOGO", "Logo Seç"),
                Map.entry("MSG_LOGO_UPDATED", "Logo başarıyla güncellendi."),
                Map.entry("MSG_LOGO_ERROR", "Logo güncellenemedi: "),
                Map.entry("SET_SERVER_PREVIEW", "Çoklu Oyuncu Liste Önizlemesi"),
                Map.entry("TOOLTIP_CLICK_LOGO", "Logo değiştirmek için tıklayın"),
                Map.entry("SELECT_SERVER_TITLE", "Minecraft Sunucu Konumu Seçin"),
                Map.entry("SELECT_SERVER_DESC", "CraftStation paneli ile yönetmek istediğiniz Minecraft sunucusunun klasörünü seçin."),
                Map.entry("BTN_BROWSE", "Gözat..."),
                Map.entry("BTN_SAVE_DIR", "Kaydet ve Devam Et"),
                Map.entry("WARN_INVALID_SERVER_DIR", "Seçilen klasörde server.properties veya .jar dosyası bulunamadı. Yine de bu klasör kullanılsın mı?"),
                Map.entry("SET_SERVER_LOCATION", "Sunucu Klasörü Konumu"),
                Map.entry("BTN_CHANGE_LOCATION", "Konumu Değiştir...")));
 
        // English strings
        ENGLISH_STRINGS.putAll(Map.ofEntries(
                Map.entry("LANG_TITLE", "CraftStation"),
                Map.entry("LANG_DESC", "Which language would you like to choose?"),
                Map.entry("BTN_CONTINUE", "Continue"),
                Map.entry("LAUNCH_TITLE", "CraftStation Selection Screen"),
                Map.entry("LAUNCH_DESC", "Would you like to start the server with Control Panel interface?"),
                Map.entry("BTN_YES_PANEL", "Yes, Start Panel"),
                Map.entry("BTN_NO_PANEL", "No, Don't Start Panel"),
                Map.entry("APP_TITLE", "CraftStation"),
                Map.entry("EXIT_WARN_TITLE", "Exit Confirmation"),
                Map.entry("EXIT_WARN_MSG",
                        "Server is still running! If you close, the server will also shut down.\nDo you want to continue?"),
                Map.entry("TAB_DASHBOARD", "Dashboard"),
                Map.entry("TAB_CONSOLE", "Console"),
                Map.entry("TAB_PLAYERS", "Players"),
                Map.entry("TAB_SETTINGS", "Settings"),
                Map.entry("TAB_BACKUP", "Backup"),
                Map.entry("STAT_OFFLINE", "OFFLINE"),
                Map.entry("STAT_ONLINE", "ONLINE"),
                Map.entry("STAT_STARTING", "STARTING..."),
                Map.entry("STAT_STOPPING", "STOPPING..."),
                Map.entry("BTN_START", "Start"),
                Map.entry("BTN_STOP", "Stop"),
                Map.entry("BTN_RESTART", "Restart"),
                Map.entry("CONFIRM_TITLE", "Confirm"),
                Map.entry("STOP_WARN_MSG", "Are you sure you want to stop the server?"),
                Map.entry("LABEL_TPS", "TPS"),
                Map.entry("LABEL_PLAYERS", "Players"),
                Map.entry("LABEL_UPTIME", "Uptime"),
                Map.entry("LABEL_RAM", "RAM"),
                Map.entry("LABEL_MAX_PLAYERS", "Max Players"),
                Map.entry("LABEL_RECENT_ERRORS", "Recent Errors"),
                Map.entry("LABEL_ONLINE_PLAYERS", "Online Players"),
                Map.entry("ERR_BAT_NOT_FOUND", "Startup file not found!"),
                Map.entry("ERR_START_FAIL", "Failed to start server"),
                Map.entry("ERR_READ", "Failed to read output"),
                Map.entry("ERR_CMD_SEND", "Failed to send command"),
                Map.entry("ERR_BAT_READ", "Failed to read startup file"),
                Map.entry("ERR_ALREADY_STARTING", "Server is already starting"),
                Map.entry("LOG_SERVER_STOPPED", "Server stopped"),
                Map.entry("LOG_SERVER_STARTED", "Server started successfully!"),
                Map.entry("LOG_SERVER_CRASH", "Server crashed unexpectedly"),
                Map.entry("LOG_FORCE_KILL", "Server force killed"),
                Map.entry("LOG_RESTART_ABORTED", "Restart aborted"),
                Map.entry("STAT_TPS", "TPS"),
                Map.entry("STAT_PLAYERS", "Players"),
                Map.entry("STAT_UPTIME", "Uptime"),
                Map.entry("STAT_RAM", "RAM"),
                Map.entry("RECENT_ERRORS", "Recent Errors"),
                Map.entry("NO_ERRORS_YET", "No Errors Found"),
                Map.entry("SEARCH_PLAYER", "Search player..."),
                Map.entry("ERR_REASON_LABEL", "Cause:"),
                Map.entry("ERR_FIX_LABEL", "Fix:"),
                Map.entry("ERR_RAW_LABEL", "Raw Line:"),
                Map.entry("CHECK_AUTOSCROLL", "Auto Scroll"),
                Map.entry("BTN_CLEAR", "Clear"),
                Map.entry("BTN_SEND", "Send"),
                Map.entry("LOG_PANEL_STARTED", "[Panel] CraftStation started."),
                Map.entry("LOG_PANEL_HINT", "[Panel] Use the Dashboard tab to start the server."),
                Map.entry("LOADING_PANEL", "Loading panel..."),
                Map.entry("LOADING_ICONS", "Loading icons"),
                Map.entry("LOADING_PANELS", "Building panels")));
 
        ENGLISH_STRINGS.putAll(Map.ofEntries(
                Map.entry("PLAYERS_TITLE", "Players"),
                Map.entry("PLAYERS_COUNT", "Players"),
                Map.entry("BTN_KICK", "Kick"),
                Map.entry("BTN_BAN", "Ban"),
                Map.entry("BTN_OP", "Give OP"),
                Map.entry("BTN_DEOP", "Remove OP"),
                Map.entry("BTN_WHITELIST", "Whitelist"),
                Map.entry("BTN_REFRESH", "Refresh"),
                Map.entry("SET_LANG", "Language"),
                Map.entry("SET_RAM_TITLE", "RAM"),
                Map.entry("SET_SPEED_TITLE", "Speed"),
                Map.entry("SET_SPEED_START", "Start"),
                Map.entry("SET_SPEED_CALC", "Calculating..."),
                Map.entry("SET_SPEED_FAIL", "Failed!"),
                Map.entry("SET_LANG_UPDATED", "Language changed. Restart the panel to apply fully."),
                Map.entry("SET_SAVING", "Saving..."),
                Map.entry("SET_SAVED", "Saved"),
                Map.entry("SET_SAVE_ERROR", "Save Error"),
                Map.entry("SET_SPEED_IDLE", "Idle"),
                Map.entry("SET_PROPS_TITLE", "Server Settings"),
                Map.entry("SET_MAX_PLAYERS", "Max Players"),
                Map.entry("SET_GAMEMODE", "Game Mode"),
                Map.entry("SET_DIFFICULTY", "Difficulty"),
                Map.entry("SET_ONLINE_MODE", "Online Mode"),
                Map.entry("SET_PVP", "PVP"),
                Map.entry("SET_CMD_BLOCKS", "Command Blocks"),
                Map.entry("SET_FLIGHT", "Flight"),
                Map.entry("SET_VIEW_DIST", "View Distance"),
                Map.entry("SET_SIM_DIST", "Simulation Distance"),
                Map.entry("BTN_SAVE_ALL", "Save All"),
                Map.entry("BACKUP_TITLE", "Backup"),
                Map.entry("BTN_BACKUP_NOW", "Backup Now"),
                Map.entry("BACKUP_STATUS", "Backup Status"),
                Map.entry("BACKUP_LAST", "Last Backup"),
                Map.entry("BACKUP_NONE", "No backups yet"),
                Map.entry("CONSOLE_TITLE", "Console"),
                Map.entry("SET_FORCE_GM", "Force Gamemode"),
                Map.entry("SET_WHITELIST", "Whitelist"),
                Map.entry("SET_ADV_EDITOR", "Advanced Config Editor"),
                Map.entry("SET_YML_EDITOR", "YAML Editor"),
                Map.entry("BTN_CREATE_BACKUP", "Create Backup"),
                Map.entry("NO_BACKUPS_YET", "No backups yet"),
                Map.entry("BTN_DELETE_BACKUP", "Delete"),
                Map.entry("BTN_RESTORE_BACKUP", "Restore"),
                Map.entry("LANG_SELECT", "Select Language"),
                Map.entry("SET_LANG_LABEL", "Language:"),
                Map.entry("WARN_TITLE", "Warning"),
                Map.entry("WARN_SELECT_BACKUP", "Please select a backup from the list."),
                Map.entry("WARN_DELETE_BACKUP", "Are you sure you want to delete this backup?"),
                Map.entry("WARN_RESTORE_CONFIRM", "Are you sure you want to restore this backup? Your current worlds and settings will be overwritten by the restored backup."),
                Map.entry("WARN_SELECT_PLAYER", "Please select a player from the list."),
                Map.entry("PROMPT_KICK", "Enter a reason for kicking this player (optional):"),
                Map.entry("PROMPT_BAN", "Enter a reason for banning this player (optional):"),
                Map.entry("MSG_BACKUP_CREATING", "Creating backup..."),
                Map.entry("MSG_RESTORING", "Restoring..."),
                Map.entry("BTN_DELETE", "Delete"),
                Map.entry("DELETE_CONFIRM_TITLE", "Delete Backup"),
                Map.entry("MSG_BACKUP_DELETED", "Backup deleted successfully."),
                Map.entry("MSG_BACKUP_ERROR", "Backup error:"),
                Map.entry("MSG_RESTORE_SUCCESS", "Backup restored successfully."),
                Map.entry("MSG_RESTORE_ERROR", "Restore failed:"),
                Map.entry("WARN_BACKUP_RUNNING", "Creating a backup while the server is running may cause data loss. Do you want to continue?"),
                Map.entry("WARN_RESTORE_RUNNING", "Cannot restore backup while server is running. Please stop the server first."),
                Map.entry("MSG_BACKUP_SUCCESS", "Backup created successfully:"),
                Map.entry("MSG_REFRESHING", "Refreshing..."),
                Map.entry("MSG_ERROR", "Error"),
                Map.entry("ERR_START_PANEL", "Cannot start the panel"),
                Map.entry("TAB_SERVER", "Server"),
                Map.entry("SET_SERVER_TITLE", "Server Settings"),
                Map.entry("SET_SERVER_NAME", "Server Name"),
                Map.entry("SET_SERVER_MOTD", "Server Description (MOTD)"),
                Map.entry("SET_SERVER_LOGO", "Server Logo"),
                Map.entry("BTN_CHANGE_LOGO", "Choose Logo"),
                Map.entry("MSG_LOGO_UPDATED", "Logo updated successfully."),
                Map.entry("MSG_LOGO_ERROR", "Logo update failed: "),
                Map.entry("SET_SERVER_PREVIEW", "Multiplayer List Preview"),
                Map.entry("TOOLTIP_CLICK_LOGO", "Click to change logo"),
                Map.entry("SELECT_SERVER_TITLE", "Select Minecraft Server Location"),
                Map.entry("SELECT_SERVER_DESC", "Please select the folder of the Minecraft server you want to manage with CraftStation."),
                Map.entry("BTN_BROWSE", "Browse..."),
                Map.entry("BTN_SAVE_DIR", "Save & Continue"),
                Map.entry("WARN_INVALID_SERVER_DIR", "No server.properties or .jar file was found in the selected folder. Use this folder anyway?"),
                Map.entry("SET_SERVER_LOCATION", "Server Folder Location"),
                Map.entry("BTN_CHANGE_LOCATION", "Change Location...")));
 
        // Other languages fallback to English (PLACEHOLDERS until fully translated)
        GERMAN_STRINGS.putAll(ENGLISH_STRINGS);
        FRENCH_STRINGS.putAll(ENGLISH_STRINGS);
        SPANISH_STRINGS.putAll(ENGLISH_STRINGS);
        RUSSIAN_STRINGS.putAll(ENGLISH_STRINGS);
    }
 
}
 
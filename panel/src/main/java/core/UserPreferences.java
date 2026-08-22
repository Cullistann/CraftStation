package core;

import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Window;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.prefs.Preferences;

/**
 * User preferences manager for storing and retrieving user settings.
 * Provides a modern, type-safe API for preference management.
 */
public final class UserPreferences {
    
    private static final LoggingUtil logger = LoggingUtil.getLogger(UserPreferences.class);
    
    // Preference keys
    public static final String KEY_THEME = "theme";
    public static final String KEY_LANGUAGE = "language";
    public static final String KEY_AUTO_START = "auto_start";
    public static final String KEY_AUTO_BACKUP = "auto_backup";
    public static final String KEY_BACKUP_INTERVAL = "backup_interval";
    public static final String KEY_NOTIFICATIONS = "notifications";
    public static final String KEY_CONSOLE_FONT_SIZE = "console_font_size";
    public static final String KEY_SHOW_STATS = "show_stats";
    public static final String KEY_ANIMATIONS = "animations";
    public static final String KEY_LAST_SERVER_DIR = "last_server_dir";
    public static final String KEY_WINDOW_WIDTH = "window_width";
    public static final String KEY_WINDOW_HEIGHT = "window_height";
    public static final String KEY_WINDOW_X = "window_x";
    public static final String KEY_WINDOW_Y = "window_y";
    public static final String KEY_MAXIMIZED = "window_maximized";
    
    // Default values
    public static final String DEFAULT_THEME = "dark";
    public static final String DEFAULT_LANGUAGE = "tr";
    public static final boolean DEFAULT_AUTO_START = false;
    public static final boolean DEFAULT_AUTO_BACKUP = true;
    public static final int DEFAULT_BACKUP_INTERVAL = 60; // minutes
    public static final boolean DEFAULT_NOTIFICATIONS = true;
    public static final int DEFAULT_CONSOLE_FONT_SIZE = 12;
    public static final boolean DEFAULT_SHOW_STATS = true;
    public static final boolean DEFAULT_ANIMATIONS = true;
    
    // Instance
    private static final UserPreferences instance = new UserPreferences();
    private final Preferences prefs;
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private final List<PreferenceChangeListener> listeners = new CopyOnWriteArrayList<>();
    
    // Private constructor
    private UserPreferences() {
        this.prefs = Preferences.userNodeForPackage(UserPreferences.class);
        logger.info("UserPreferences initialized");
    }
    
    /**
     * Get the singleton instance.
     */
    public static UserPreferences getInstance() {
        return instance;
    }
    
    // String preferences
    
    public String getString(String key, String defaultValue) {
        // Check cache first
        if (cache.containsKey(key)) {
            Object value = cache.get(key);
            if (value instanceof String) {
                return (String) value;
            }
        }
        
        String value = prefs.get(key, defaultValue);
        if (value != null) {
            cache.put(key, value);
        }
        return value;
    }
    
    public void setString(String key, String value) {
        prefs.put(key, value);
        cache.put(key, value);
        notifyListeners(key, value);
        logger.debug("Preference set: " + key + " = " + value);
    }
    
    // Integer preferences
    
    public int getInt(String key, int defaultValue) {
        // Check cache first
        if (cache.containsKey(key)) {
            Object value = cache.get(key);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        }
        
        int value = prefs.getInt(key, defaultValue);
        cache.put(key, value);
        return value;
    }
    
    public void setInt(String key, int value) {
        prefs.putInt(key, value);
        cache.put(key, value);
        notifyListeners(key, value);
        logger.debug("Preference set: " + key + " = " + value);
    }
    
    // Boolean preferences
    
    public boolean getBoolean(String key, boolean defaultValue) {
        // Check cache first
        if (cache.containsKey(key)) {
            Object value = cache.get(key);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
        }
        
        boolean value = prefs.getBoolean(key, defaultValue);
        cache.put(key, value);
        return value;
    }
    
    public void setBoolean(String key, boolean value) {
        prefs.putBoolean(key, value);
        cache.put(key, value);
        notifyListeners(key, value);
        logger.debug("Preference set: " + key + " = " + value);
    }
    
    // Double preferences
    
    public double getDouble(String key, double defaultValue) {
        // Check cache first
        if (cache.containsKey(key)) {
            Object value = cache.get(key);
            if (value instanceof Double) {
                return (Double) value;
            }
        }
        
        double value = prefs.getDouble(key, defaultValue);
        cache.put(key, value);
        return value;
    }
    
    public void setDouble(String key, double value) {
        prefs.putDouble(key, value);
        cache.put(key, value);
        notifyListeners(key, value);
        logger.debug("Preference set: " + key + " = " + value);
    }
    
    // Convenience methods for common preferences
    
    public String getTheme() {
        return getString(KEY_THEME, DEFAULT_THEME);
    }
    
    public void setTheme(String theme) {
        setString(KEY_THEME, theme);
    }
    
    public String getLanguage() {
        return getString(KEY_LANGUAGE, DEFAULT_LANGUAGE);
    }
    
    public void setLanguage(String language) {
        setString(KEY_LANGUAGE, language);
    }
    
    public boolean isAutoStartEnabled() {
        return getBoolean(KEY_AUTO_START, DEFAULT_AUTO_START);
    }
    
    public void setAutoStartEnabled(boolean enabled) {
        setBoolean(KEY_AUTO_START, enabled);
    }
    
    public boolean isAutoBackupEnabled() {
        return getBoolean(KEY_AUTO_BACKUP, DEFAULT_AUTO_BACKUP);
    }
    
    public void setAutoBackupEnabled(boolean enabled) {
        setBoolean(KEY_AUTO_BACKUP, enabled);
    }
    
    public int getBackupInterval() {
        return getInt(KEY_BACKUP_INTERVAL, DEFAULT_BACKUP_INTERVAL);
    }
    
    public void setBackupInterval(int minutes) {
        setInt(KEY_BACKUP_INTERVAL, minutes);
    }
    
    public boolean areNotificationsEnabled() {
        return getBoolean(KEY_NOTIFICATIONS, DEFAULT_NOTIFICATIONS);
    }
    
    public void setNotificationsEnabled(boolean enabled) {
        setBoolean(KEY_NOTIFICATIONS, enabled);
    }
    
    public int getConsoleFontSize() {
        return getInt(KEY_CONSOLE_FONT_SIZE, DEFAULT_CONSOLE_FONT_SIZE);
    }
    
    public void setConsoleFontSize(int size) {
        setInt(KEY_CONSOLE_FONT_SIZE, size);
    }
    
    public boolean isShowStatsEnabled() {
        return getBoolean(KEY_SHOW_STATS, DEFAULT_SHOW_STATS);
    }
    
    public void setShowStatsEnabled(boolean enabled) {
        setBoolean(KEY_SHOW_STATS, enabled);
    }
    
    public boolean areAnimationsEnabled() {
        return getBoolean(KEY_ANIMATIONS, DEFAULT_ANIMATIONS);
    }
    
    public void setAnimationsEnabled(boolean enabled) {
        setBoolean(KEY_ANIMATIONS, enabled);
    }
    
    public String getLastServerDir() {
        return getString(KEY_LAST_SERVER_DIR, "");
    }
    
    public void setLastServerDir(String dir) {
        setString(KEY_LAST_SERVER_DIR, dir);
    }
    
    // Window state management
    
    public void saveWindowState(Window window) {
        boolean maximized = false;
        if (window instanceof Frame frame) {
            maximized = (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0;
            setBoolean(KEY_MAXIMIZED, maximized);
        }
        
        // Only save bounds when NOT maximized (otherwise we'd save the full-screen size)
        if (!maximized) {
            Rectangle bounds = window.getBounds();
            setInt(KEY_WINDOW_X, bounds.x);
            setInt(KEY_WINDOW_Y, bounds.y);
            setInt(KEY_WINDOW_WIDTH, bounds.width);
            setInt(KEY_WINDOW_HEIGHT, bounds.height);
        }
        
        logger.debug("Window state saved");
    }
    
    public void restoreWindowState(Window window) {
        // Restore size and position
        int x = getInt(KEY_WINDOW_X, -1);
        int y = getInt(KEY_WINDOW_Y, -1);
        int width = getInt(KEY_WINDOW_WIDTH, 1200);
        int height = getInt(KEY_WINDOW_HEIGHT, 760);
        
        if (x >= 0 && y >= 0) {
            window.setBounds(x, y, width, height);
        } else {
            window.setSize(width, height);
            window.setLocationRelativeTo(null);
        }
        
        // Restore maximized state (only applicable to Frame)
        if (window instanceof Frame frame) {
            if (getBoolean(KEY_MAXIMIZED, false)) {
                frame.setExtendedState(frame.getExtendedState() | Frame.MAXIMIZED_BOTH);
            }
        }
        
        logger.debug("Window state restored");
    }
    
    // Listener support
    
    public interface PreferenceChangeListener {
        void onPreferenceChanged(String key, Object value);
    }
    
    public void addChangeListener(PreferenceChangeListener listener) {
        listeners.add(listener);
    }
    
    public void removeChangeListener(PreferenceChangeListener listener) {
        listeners.remove(listener);
    }
    
    private void notifyListeners(String key, Object value) {
        for (PreferenceChangeListener listener : listeners) {
            try {
                listener.onPreferenceChanged(key, value);
            } catch (Exception e) {
                logger.error("Error in preference change listener", e);
            }
        }
    }
    
    // Utility methods
    
    public Map<String, Object> getAllPreferences() {
        Map<String, Object> allPrefs = new LinkedHashMap<>();
        
        try {
            String[] keys = prefs.keys();
            for (String key : keys) {
                String stringValue = prefs.get(key, null);
                if (stringValue != null) {
                    if (stringValue.equalsIgnoreCase("true") || stringValue.equalsIgnoreCase("false")) {
                        allPrefs.put(key, Boolean.parseBoolean(stringValue));
                    } else {
                        try {
                            allPrefs.put(key, Integer.parseInt(stringValue));
                        } catch (NumberFormatException e) {
                            try {
                                allPrefs.put(key, Double.parseDouble(stringValue));
                            } catch (NumberFormatException e2) {
                                allPrefs.put(key, stringValue);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error getting all preferences", e);
        }
        
        return allPrefs;
    }
    
    public void resetToDefaults() {
        try {
            prefs.clear();
            cache.clear();
            
            // Set default values
            setTheme(DEFAULT_THEME);
            setLanguage(DEFAULT_LANGUAGE);
            setAutoStartEnabled(DEFAULT_AUTO_START);
            setAutoBackupEnabled(DEFAULT_AUTO_BACKUP);
            setBackupInterval(DEFAULT_BACKUP_INTERVAL);
            setNotificationsEnabled(DEFAULT_NOTIFICATIONS);
            setConsoleFontSize(DEFAULT_CONSOLE_FONT_SIZE);
            setShowStatsEnabled(DEFAULT_SHOW_STATS);
            setAnimationsEnabled(DEFAULT_ANIMATIONS);
            
            logger.info("Preferences reset to defaults");
        } catch (Exception e) {
            logger.error("Error resetting preferences", e);
        }
    }
    
    public void exportPreferences(File file) throws IOException {
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), java.nio.charset.StandardCharsets.UTF_8))) {
            Map<String, Object> prefs = getAllPreferences();
            for (Map.Entry<String, Object> entry : prefs.entrySet()) {
                writer.println(entry.getKey() + "=" + entry.getValue());
            }
        }
        logger.info("Preferences exported to: " + file.getAbsolutePath());
    }
    
    public void importPreferences(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int equalsIndex = line.indexOf('=');
                if (equalsIndex > 0) {
                    String key = line.substring(0, equalsIndex).trim();
                    String value = line.substring(equalsIndex + 1).trim();
                    
                    // Try to determine type
                    if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                        setBoolean(key, Boolean.parseBoolean(value));
                    } else {
                        try {
                            int intValue = Integer.parseInt(value);
                            setInt(key, intValue);
                        } catch (NumberFormatException e) {
                            setString(key, value);
                        }
                    }
                }
            }
        }
        logger.info("Preferences imported from: " + file.getAbsolutePath());
    }
    
    /**
     * Get preference statistics.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        Map<String, Object> allPrefs = getAllPreferences();
        
        stats.put("total_preferences", allPrefs.size());
        stats.put("cache_size", cache.size());
        stats.put("listeners_count", listeners.size());
        
        // Count by type
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        for (Object value : allPrefs.values()) {
            String type = value.getClass().getSimpleName();
            typeCounts.put(type, typeCounts.getOrDefault(type, 0) + 1);
        }
        stats.put("preferences_by_type", typeCounts);
        
        return stats;
    }
}
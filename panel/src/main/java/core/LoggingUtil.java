package core;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Profesyonel logging utility sınıfı.
 * Thread-safe, performans odaklı, configurable logging çözümü.
 */
public class LoggingUtil {
    
    private static final String LOG_DIR = "logs";
    private static final String APP_NAME = "CraftStation";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final Object FILE_WRITER_LOCK = new Object();
    
    private static final ConcurrentMap<String, LoggingUtil> instances = new ConcurrentHashMap<>();
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);
    
    private final String componentName;
    private static volatile BufferedWriter fileWriter;
    private static volatile boolean fileLoggingEnabled = true;
    
    private LoggingUtil(String componentName) {
        this.componentName = componentName;
        initializeLogging();
    }
    
    public static LoggingUtil getLogger(Class<?> clazz) {
        return getLogger(clazz.getSimpleName());
    }
    
    public static LoggingUtil getLogger(String componentName) {
        return instances.computeIfAbsent(componentName, LoggingUtil::new);
    }
    
    private static synchronized void initializeLogging() {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        
        try {
            File logDir = new File(LOG_DIR);
            if (!logDir.exists() && !logDir.mkdirs()) {
                System.err.println("Log dizini oluşturulamadı: " + LOG_DIR);
                fileLoggingEnabled = false;
                return;
            }
            
            String dateStr = LocalDateTime.now().format(DATE_FORMATTER);
            String logFileName = String.format("%s/%s_%s.log", LOG_DIR, APP_NAME, dateStr);
            fileWriter = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(logFileName, true), StandardCharsets.UTF_8));
            
            if (shutdownHookRegistered.compareAndSet(false, true)) {
                Runtime.getRuntime().addShutdownHook(new Thread(LoggingUtil::cleanup, "CraftStation-LogShutdown"));
            }
            
        } catch (IOException e) {
            System.err.println("Log dosyası açılamadı: " + e.getMessage());
            fileLoggingEnabled = false;
        }
    }
    
    private static void cleanup() {
        synchronized (FILE_WRITER_LOCK) {
            BufferedWriter writer = fileWriter;
            fileWriter = null;
            if (writer == null) {
                return;
            }
            try {
                writer.flush();
                writer.close();
            } catch (IOException e) {
                System.err.println("Log dosyası kapatılırken hata: " + e.getMessage());
            }
        }
    }
    
    public void debug(String message) {
        log("DEBUG", message, null);
    }
    
    public void info(String message) {
        log("INFO", message, null);
    }
    
    public void warn(String message) {
        log("WARN", message, null);
    }
    
    public void warn(String message, Throwable throwable) {
        log("WARN", message, throwable);
    }
    
    public void error(String message) {
        log("ERROR", message, null);
    }
    
    public void error(String message, Throwable throwable) {
        log("ERROR", message, throwable);
    }
    
    private void log(String level, String message, Throwable throwable) {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        String logEntry = String.format("[%s] [%s] [%s] %s", 
            timestamp, level, componentName, message);
        
        // Console output
        System.out.println(logEntry);
        
        // File logging
        if (fileLoggingEnabled && fileWriter != null) {
            try {
                synchronized (FILE_WRITER_LOCK) {
                    BufferedWriter writer = fileWriter;
                    if (writer == null) {
                        return;
                    }
                    writer.write(logEntry);
                    writer.write(System.lineSeparator());
                    
                    if (throwable != null) {
                        StringWriter sw = new StringWriter();
                        try (PrintWriter pw = new PrintWriter(sw)) {
                            throwable.printStackTrace(pw);
                        }
                        writer.write(sw.toString());
                        writer.write(System.lineSeparator());
                    }
                    
                    writer.flush();
                }
            } catch (IOException e) {
                System.err.println("Log yazma hatası: " + e.getMessage());
            }
        }
        
        // Throwable stack trace to console
        if (throwable != null) {
            throwable.printStackTrace();
        }
    }
    
    public void disableFileLogging() {
        fileLoggingEnabled = false;
    }
    
    public void enableFileLogging() {
        fileLoggingEnabled = true;
    }
}

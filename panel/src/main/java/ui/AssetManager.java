package ui;

import core.LoggingUtil;
import core.CacheManager;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Profesyonel asset yönetim sistemi.
 * Thread-safe, performans odaklı, hata toleranslı asset loading çözümü.
 * Gelişmiş caching ve memory management ile optimize edilmiş.
 */
public class AssetManager {
    private static final LoggingUtil logger = LoggingUtil.getLogger(AssetManager.class);

    // Configuration
    private static final String DEFAULT_ASSET_VERSION = System.getProperty("craftstation.mc.assets.version", "26.1");
    private static final boolean PREFER_WEB_FIRST = Boolean
            .parseBoolean(System.getProperty("craftstation.mc.assets.preferWeb", "false"));
    private static final int WEB_CONNECT_TIMEOUT_MS = Integer
            .parseInt(System.getProperty("craftstation.mc.assets.connectTimeoutMs", "700"));
    private static final int WEB_READ_TIMEOUT_MS = Integer
            .parseInt(System.getProperty("craftstation.mc.assets.readTimeoutMs", "900"));
    private static final int MAX_CONCURRENT_WEB_LOADS = 3;
    private static final int FALLBACK_IMAGE_SIZE = 16;

    // Fallback versions for web loading
    private static final String[] FALLBACK_VERSIONS = {
            DEFAULT_ASSET_VERSION, "1.21.8", "1.21.7", "1.21.6", "1.21.5", "1.21.4", "1.21.1", "1.20.2"
    };

    // Local asset roots
    private static final List<File> LOCAL_ASSET_ROOTS = buildLocalRoots();

    // Performance tracking
    private static final AtomicInteger localLoadCount = new AtomicInteger(0);
    private static final AtomicInteger webLoadCount = new AtomicInteger(0);
    private static final AtomicInteger cacheHitCount = new AtomicInteger(0);
    private static final AtomicInteger totalLoadTimeMs = new AtomicInteger(0);

    // Semaphore for limiting concurrent web loads
    private static final java.util.concurrent.Semaphore webLoadSemaphore = new java.util.concurrent.Semaphore(
            MAX_CONCURRENT_WEB_LOADS, true);
    private static final Set<String> missingAssets = ConcurrentHashMap.newKeySet();
    // H7 fix: in-flight web loading deduplikasyonu
    private static final Set<String> pendingLoads = ConcurrentHashMap.newKeySet();

    // Common assets for preloading
    private static final String[] COMMON_ASSETS = {
            "gui/widgets.png",
            "gui/container/inventory.png",
            "gui/icons.png",
            "gui/background.png",
            "items/diamond.png",
            "items/iron_ingot.png",
            "items/gold_ingot.png"
    };

    /**
     * Asset yükleme için ana metod.
     * Önce cache kontrolü, sonra local, en son web loading.
     * Performans optimizasyonları ve memory-aware caching ile geliştirilmiş.
     * 
     * @param filename Yüklenecek asset dosya adı
     * @return BufferedImage veya null (yüklenemezse)
     */
    public static BufferedImage loadImage(String filename) {
        if (filename == null || filename.isBlank()) {
            logger.warn("Geçersiz filename parametresi: " + filename);
            return createFallbackImage("invalid");
        }

        long startTime = System.currentTimeMillis();

        try {
            // 1. Cache kontrolü (LRU cache with memory awareness)
            BufferedImage cached = CacheManager.get(filename);
            if (cached != null) {
                cacheHitCount.incrementAndGet();
                logger.debug("Cache hit: " + filename);
                return cached;
            }

            if (missingAssets.contains(filename)) {
                return createFallbackImage(filename);
            }

            // 2. Local loading denemesi (fast path)
            BufferedImage loaded = loadFromLocal(filename);
            if (loaded != null) {
                missingAssets.remove(filename);
                localLoadCount.incrementAndGet();
                CacheManager.put(filename, loaded);
                logger.debug("Local yükleme başarılı: " + filename);
                return loaded;
            }

            // 3. Web loading (async on EDT, sync on background threads)
            if (javax.swing.SwingUtilities.isEventDispatchThread()) {
                // H7 fix: zaten yükleme devam ediyorsa tekrar spawn etme
                if (!pendingLoads.add(filename)) {
                    return createFallbackImage(filename);
                }

                // Async web loading on EDT to prevent UI freeze
                logger.debug("EDT üzerinde, async web loading başlatılıyor: " + filename);

                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    boolean acquired = false;
                    try {
                        // Limit concurrent web loads
                        acquired = webLoadSemaphore.tryAcquire(5, java.util.concurrent.TimeUnit.SECONDS);
                        if (!acquired) {
                            logger.warn("Web loading timeout for: " + filename);
                            return;
                        }

                        BufferedImage webImg = loadFromWeb(filename);
                        if (webImg != null) {
                            missingAssets.remove(filename);
                            webLoadCount.incrementAndGet();
                            CacheManager.put(filename, webImg);
                            logger.info("Web yükleme başarılı: " + filename);

                            // UI refresh
                            javax.swing.SwingUtilities.invokeLater(() -> {
                                for (java.awt.Window w : java.awt.Window.getWindows()) {
                                    w.repaint();
                                }
                            });
                        } else {
                            missingAssets.add(filename);
                            logger.warn("Web yükleme başarısız: " + filename);
                        }
                    } catch (Exception e) {
                        missingAssets.add(filename);
                        logger.error("Async web loading hatası: " + filename, e);
                    } finally {
                        pendingLoads.remove(filename);
                        if (acquired) {
                            webLoadSemaphore.release();
                        }
                    }
                });

                return createFallbackImage(filename);
            } else {
                // Sync web loading on background threads
                boolean acquired = false;
                try {
                    // Limit concurrent web loads
                    acquired = webLoadSemaphore.tryAcquire(10, java.util.concurrent.TimeUnit.SECONDS);
                    if (!acquired) {
                        logger.warn("Web loading timeout for: " + filename);
                        missingAssets.add(filename);
                        return createFallbackImage(filename);
                    }

                    BufferedImage webImg = loadFromWeb(filename);
                    if (webImg != null) {
                        missingAssets.remove(filename);
                        webLoadCount.incrementAndGet();
                        CacheManager.put(filename, webImg);
                        logger.debug("Web yükleme başarılı (sync): " + filename);
                        return webImg;
                    }

                    missingAssets.add(filename);
                    logger.warn("Web yükleme başarısız: " + filename);
                    return createFallbackImage(filename);

                } catch (Exception e) {
                    missingAssets.add(filename);
                    logger.error("Web loading hatası: " + filename, e);
                    return createFallbackImage(filename);
                } finally {
                    if (acquired) {
                        webLoadSemaphore.release();
                    }
                }
            }

        } finally {
            // Performance tracking
            long loadTime = System.currentTimeMillis() - startTime;
            totalLoadTimeMs.addAndGet((int) loadTime);

            if (loadTime > 1000) {
                logger.warn("Slow asset load: " + filename + " took " + loadTime + "ms");
            }
        }
    }

    private static List<File> buildLocalRoots() {
        LinkedHashSet<File> roots = new LinkedHashSet<>();
        String userDir = System.getProperty("user.dir", ".");
        File current = new File(userDir);
        File parent = current.getParentFile();
        roots.add(new File("assets"));
        roots.add(new File("panel/assets"));
        roots.add(new File("../assets"));
        roots.add(new File("../../assets"));
        roots.add(new File(current, "assets"));
        roots.add(new File(current, "panel/assets"));
        if (parent != null) {
            roots.add(new File(parent, "panel/assets"));
            roots.add(new File(parent, "assets"));
        }
        return new ArrayList<>(roots);
    }

    /**
     * Local dosya sisteminden asset yükleme.
     * 
     * @param filename Yüklenecek asset dosya adı
     * @return BufferedImage veya null (yüklenemezse)
     */
    private static BufferedImage loadFromLocal(String filename) {
        for (File root : LOCAL_ASSET_ROOTS) {
            File f = new File(root, filename);
            if (!f.exists()) {
                logger.debug("Local dosya bulunamadı: " + f.getAbsolutePath());
                continue;
            }

            try {
                logger.debug("Local yükleme denemesi: " + f.getAbsolutePath());
                BufferedImage img = ImageIO.read(f);
                if (img != null) {
                    logger.info("Local yükleme başarılı: " + f.getAbsolutePath());
                    return img;
                } else {
                    logger.warn("ImageIO.read null döndü: " + f.getAbsolutePath());
                }
            } catch (Exception e) {
                logger.error("Local yükleme hatası: " + f.getAbsolutePath(), e);
                // Continue to next root
            }
        }

        logger.debug("Tüm local root'larda yükleme başarısız: " + filename);
        return null;
    }

    private static List<String> buildTextureCandidates(String filename) {
        List<String> candidates = new ArrayList<>();
        if (filename.startsWith("items/")) {
            String itemName = filename.substring(6);
            if ("clock.png".equals(itemName)) {
                candidates.add("item/clock_00.png");
                candidates.add("item/clock.png");
            } else if ("compass.png".equals(itemName)) {
                candidates.add("item/compass_00.png");
                candidates.add("item/compass.png");
            } else if ("steve.png".equals(itemName)) {
                candidates.add("entity/player/wide/steve.png");
                candidates.add("entity/steve.png");
            } else {
                candidates.add("item/" + itemName);
            }
            return candidates;
        }

        if (filename.startsWith("blocks/")) {
            String blockName = filename.substring(7);
            candidates.add("block/" + blockName);
            candidates.add("blocks/" + blockName);
            return candidates;
        }

        if ("options_background.png".equals(filename)) {
            candidates.add("gui/options_background.png");
            return candidates;
        }

        if ("inventory.png".equals(filename)) {
            candidates.add("gui/container/inventory.png");
            candidates.add("gui/inventory.png");
            return candidates;
        }

        if ("widgets.png".equals(filename)) {
            candidates.add("gui/widgets.png");
            return candidates;
        }

        candidates.add("gui/" + filename);
        return candidates;
    }

    /**
     * Web'den asset yükleme.
     * Fallback versiyonları ve candidate path'leri deneyerek asset bulmaya çalışır.
     * 
     * @param filename Yüklenecek asset dosya adı
     * @return BufferedImage veya null (yüklenemezse)
     */
    private static BufferedImage loadFromWeb(String filename) {
        logger.debug("Web yükleme başlatılıyor: " + filename);

        List<String> candidates = buildTextureCandidates(filename);
        logger.debug("Texture candidates: " + candidates);

        for (String version : FALLBACK_VERSIONS) {
            String baseUrl = "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/" + version
                    + "/assets/minecraft/textures/";
            logger.debug("Versiyon denemesi: " + version + ", baseUrl: " + baseUrl);

            for (String relPath : candidates) {
                String url = baseUrl + relPath;
                logger.debug("URL denemesi: " + url);

                HttpURLConnection httpConnection = null;
                try {
                    URLConnection connection = URI.create(url).toURL().openConnection();
                    connection.setConnectTimeout(WEB_CONNECT_TIMEOUT_MS);
                    connection.setReadTimeout(WEB_READ_TIMEOUT_MS);
                    
                    if (connection instanceof HttpURLConnection) {
                        httpConnection = (HttpURLConnection) connection;
                    }

                    BufferedImage img;
                    try (InputStream in = connection.getInputStream()) {
                        img = ImageIO.read(in);
                    }

                    if (img != null) {
                        logger.info("Web yükleme başarılı: " + url);
                        return img;
                    } else {
                        logger.warn("ImageIO.read null döndü: " + url);
                    }

                } catch (java.net.SocketTimeoutException e) {
                    logger.warn("Timeout hatası: " + url + " - " + e.getMessage());
                    // Continue to next candidate
                } catch (java.io.IOException e) {
                    logger.debug("IO hatası: " + url + " - " + e.getMessage());
                    // Continue to next candidate
                } catch (Exception e) {
                    logger.error("Web yükleme hatası: " + url, e);
                    // Continue to next candidate
                } finally {
                    if (httpConnection != null) {
                        httpConnection.disconnect();
                    }
                }
            }
        }

        logger.warn("Tüm web yükleme denemeleri başarısız: " + filename);
        return null;
    }

    /**
     * Cache temizleme metodu.
     * Bellek yönetimi için kullanılabilir.
     */
    public static void clearCache() {
        CacheManager.clear();
        missingAssets.clear();
        logger.info("Asset cache temizlendi");
    }

    /**
     * Cache istatistikleri.
     * 
     * @return Cache istatistikleri string
     */
    public static String getCacheStats() {
        return CacheManager.getStats();
    }

    /**
     * Get detailed performance statistics.
     * 
     * @return Detailed statistics map
     */
    public static Map<String, Object> getPerformanceStats() {
        Map<String, Object> stats = new java.util.LinkedHashMap<>();

        stats.put("cache_stats", CacheManager.getDetailedStats());
        stats.put("local_loads", localLoadCount.get());
        stats.put("web_loads", webLoadCount.get());
        stats.put("cache_hits", cacheHitCount.get());
        stats.put("total_load_time_ms", totalLoadTimeMs.get());

        int totalLoads = localLoadCount.get() + webLoadCount.get();
        if (totalLoads > 0) {
            double avgLoadTime = (double) totalLoadTimeMs.get() / totalLoads;
            stats.put("average_load_time_ms", String.format("%.2f", avgLoadTime));
        }

        return stats;
    }

    /**
     * Preload common assets for better performance.
     * Should be called during application startup.
     */
    public static void preloadCommonAssets() {
        logger.info("Preloading common assets...");

        for (String asset : COMMON_ASSETS) {
            if (!CacheManager.contains(asset)) {
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        BufferedImage img = loadImage(asset);
                        if (img != null) {
                            logger.debug("Preloaded: " + asset);
                        }
                    } catch (Exception e) {
                        logger.error("Preload failed for: " + asset, e);
                    }
                });
            }
        }
    }

    /**
     * Get memory warning if cache is under pressure.
     * 
     * @return Warning message or null if OK
     */
    public static String getMemoryWarning() {
        return CacheManager.getMemoryWarning();
    }

    private static BufferedImage createFallbackImage(String filename) {
        BufferedImage fallback = new BufferedImage(FALLBACK_IMAGE_SIZE, FALLBACK_IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = fallback.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        Color dark = new Color(45, 45, 45);
        Color light = new Color(200, 70, 170);
        int tileSize = 4;
        for (int y = 0; y < FALLBACK_IMAGE_SIZE; y += tileSize) {
            for (int x = 0; x < FALLBACK_IMAGE_SIZE; x += tileSize) {
                boolean even = ((x / tileSize) + (y / tileSize)) % 2 == 0;
                g2.setColor(even ? dark : light);
                g2.fillRect(x, y, tileSize, tileSize);
            }
        }

        g2.setColor(new Color(255, 255, 255, 180));
        g2.drawRect(0, 0, FALLBACK_IMAGE_SIZE - 1, FALLBACK_IMAGE_SIZE - 1);
        g2.dispose();

        logger.debug("Fallback asset kullanıldı: " + filename);
        return fallback;
    }
}

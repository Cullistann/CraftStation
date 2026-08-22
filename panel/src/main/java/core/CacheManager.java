package core;

import java.lang.ref.SoftReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Uygulama genelinde paylaşılan statik in-memory cache.
 *
 * <p><b>Scope kararı:</b> Bu cache kasıtlı olarak application-scoped (static) tasarlanmıştır.
 * Cache'lenen içerik (ikonlar, görseller, UI asset'leri) serverDir'e özgü değildir;
 * uygulama ömrü boyunca tüm bileşenler tarafından paylaşılabilir.
 * serverDir'e özel veri cache'lemek için kullanılmamalıdır.
 *
 * <p>LRU (Least Recently Used) eviction, SoftReference ile memory-aware caching
 * ve otomatik TTL tabanlı temizleme sağlar.
 */
public class CacheManager {
    private static final LoggingUtil logger = LoggingUtil.getLogger(CacheManager.class);
    
    // Cache configuration
    private static final int MAX_CACHE_SIZE = 1000;
    private static final long CACHE_TTL_MS = 30 * 60 * 1000; // 30 minutes
    private static final int CLEANUP_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes
    
    // Cache statistics
    private static final AtomicInteger hitCount = new AtomicInteger(0);
    private static final AtomicInteger missCount = new AtomicInteger(0);
    private static final AtomicInteger evictionCount = new AtomicInteger(0);
    
    // LRU Cache implementation
    private static class CacheEntry<V> {
        final String key;
        final SoftReference<V> valueRef;
        final long timestamp;
        volatile long lastAccess;
        
        CacheEntry(String key, V value) {
            this.key = key;
            this.valueRef = new SoftReference<>(value);
            this.timestamp = System.currentTimeMillis();
            this.lastAccess = this.timestamp;
        }
        
        V getValue() {
            lastAccess = System.currentTimeMillis();
            return valueRef.get();
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
        
        boolean isGarbageCollected() {
            return valueRef.get() == null;
        }
    }
    
    // Thread-safe cache
    private static final Map<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "CacheCleanup");
        t.setDaemon(true);
        return t;
    });
    
    static {
        // Start periodic cleanup
        cleanupExecutor.scheduleAtFixedRate(CacheManager::cleanupExpiredEntries, 
            CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS, TimeUnit.MILLISECONDS);
        
        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(CacheManager::shutdown, "CacheManagerShutdown"));
        
        logger.info("CacheManager initialized with max size: " + MAX_CACHE_SIZE + ", TTL: " + CACHE_TTL_MS + "ms");
    }
    
    /**
     * Put value in cache.
     */
    public static <V> void put(String key, V value) {
        if (key == null || value == null) {
            return;
        }
        
        CacheEntry<V> entry = new CacheEntry<>(key, value);
        cache.put(key, entry);
        logger.debug("Cache put: " + key);
        
        // Simple eviction check
        if (cache.size() > MAX_CACHE_SIZE) {
            cleanupExpiredEntries();
        }
    }
    
    /**
     * Get value from cache.
     */
    @SuppressWarnings("unchecked")
    public static <V> V get(String key) {
        if (key == null) {
            return null;
        }
        
        CacheEntry<V> entry = (CacheEntry<V>) cache.get(key);
        if (entry != null) {
            V value = entry.getValue();
            if (value != null) {
                if (entry.isExpired()) {
                    cache.remove(key);
                    logger.debug("Cache expired: " + key);
                    missCount.incrementAndGet();
                    return null;
                }
                hitCount.incrementAndGet();
                return value;
            } else {
                cache.remove(key);
                logger.debug("Cache GC'd: " + key);
            }
        }
        missCount.incrementAndGet();
        return null;
    }
    
    /**
     * Remove value from cache.
     */
    public static void remove(String key) {
        if (key == null) {
            return;
        }
        
        cache.remove(key);
        logger.debug("Cache removed: " + key);
    }
    
    /**
     * Check if key exists in cache.
     */
    public static boolean contains(String key) {
        if (key == null) {
            return false;
        }
        
        CacheEntry<?> entry = cache.get(key);
        if (entry != null) {
            if (entry.isExpired() || entry.isGarbageCollected()) {
                return false;
            }
            return true;
        }
        return false;
    }
    
    /**
     * Clear entire cache.
     */
    public static void clear() {
        int size = cache.size();
        cache.clear();
        logger.info("Cache cleared, removed " + size + " entries");
    }

    /**
     * Stop background cleanup and release cache resources.
     */
    public static void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("CacheManager shutdown completed");
    }
    
    /**
     * Cleanup expired entries.
     */
    private synchronized static void cleanupExpiredEntries() {
        int removed = 0;

        List<String> keysToRemove = new ArrayList<>();
        for (Map.Entry<String, CacheEntry<?>> entry : cache.entrySet()) {
            CacheEntry<?> cacheEntry = entry.getValue();
            if (cacheEntry.isExpired() || cacheEntry.isGarbageCollected()) {
                keysToRemove.add(entry.getKey());
            }
        }

        for (String key : keysToRemove) {
            if (cache.remove(key) != null) {
                removed++;
            }
        }

        // Evict oldest if still too large
        if (cache.size() > MAX_CACHE_SIZE) {
            List<Map.Entry<String, CacheEntry<?>>> entries = new ArrayList<>(cache.entrySet());
            entries.sort((a, b) -> Long.compare(a.getValue().lastAccess, b.getValue().lastAccess));
            int toRemove = cache.size() - MAX_CACHE_SIZE;
            for (int i = 0; i < toRemove && i < entries.size(); i++) {
                cache.remove(entries.get(i).getKey());
                removed++;
                evictionCount.incrementAndGet();
            }
        }
        
        if (removed > 0) {
            logger.debug("Cache cleanup: removed " + removed + " entries, " + 
                cache.size() + " remaining");
        }
    }
    
    /**
     * Get cache statistics.
     */
    public static String getStats() {
        int hits = hitCount.get();
        int misses = missCount.get();
        int total = hits + misses;
        double hitRate = total > 0 ? (double) hits / total * 100 : 0;
        
        return String.format(
            "Cache Stats: Size=%d, Hits=%d, Misses=%d, Hit Rate=%.1f%%, Evictions=%d",
            cache.size(), hits, misses, hitRate, evictionCount.get()
        );
    }
    
    /**
     * Get detailed cache statistics.
     */
    public static Map<String, Object> getDetailedStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        
        stats.put("size", cache.size());
        stats.put("max_size", MAX_CACHE_SIZE);
        stats.put("hits", hitCount.get());
        stats.put("misses", missCount.get());
        stats.put("evictions", evictionCount.get());
        stats.put("ttl_ms", CACHE_TTL_MS);
        
        // Get top 10 most accessed keys
        List<Map.Entry<String, CacheEntry<?>>> sortedEntries = new ArrayList<>(cache.entrySet());
        sortedEntries.sort((a, b) -> Long.compare(b.getValue().lastAccess, a.getValue().lastAccess));
        
        List<String> topKeys = new ArrayList<>();
        for (int i = 0; i < Math.min(10, sortedEntries.size()); i++) {
            topKeys.add(sortedEntries.get(i).getKey());
        }
        stats.put("top_accessed_keys", topKeys);
        
        return stats;
    }
    
    /**
     * Get cache memory usage warning if needed.
     */
    public static String getMemoryWarning() {
        if (cache.size() > MAX_CACHE_SIZE * 0.9) {
            return "⚠ Cache is near capacity (" + cache.size() + "/" + MAX_CACHE_SIZE + ")";
        }
        
        // Check for many garbage collected entries
        int gcCount = 0;
        for (CacheEntry<?> entry : cache.values()) {
            if (entry.isGarbageCollected()) {
                gcCount++;
            }
        }
        
        if (gcCount > cache.size() * 0.3) {
            return "⚠ High memory pressure (" + gcCount + " entries GC'd)";
        }
        
        return null;
    }
}

package core.strategy;

import java.util.Map;

/**
 * Strategy pattern interface for different server monitoring strategies.
 * Allows flexible implementation of monitoring algorithms.
 */
public interface MonitoringStrategy {
    
    /**
     * Initialize the monitoring strategy.
     * 
     * @param config Configuration parameters
     */
    void initialize(Map<String, Object> config);
    
    /**
     * Start monitoring.
     */
    void start();
    
    /**
     * Stop monitoring.
     */
    void stop();
    
    /**
     * Get monitoring metrics.
     * 
     * @return Map of metric names to values
     */
    Map<String, Object> getMetrics();
    
    /**
     * Check if monitoring is active.
     * 
     * @return true if monitoring is active
     */
    boolean isActive();
    
    /**
     * Get strategy name.
     * 
     * @return Strategy name
     */
    String getName();
    
    /**
     * Get strategy description.
     * 
     * @return Strategy description
     */
    String getDescription();
}
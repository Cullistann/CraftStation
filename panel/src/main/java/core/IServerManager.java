package core;

import java.util.List;
import java.util.function.Consumer;

/**
 * Interface for server management operations.
 * Follows Interface Segregation Principle.
 */
public interface IServerManager {
    
    enum Status { STOPPED, STARTING, RUNNING, STOPPING }
    
    // Core operations
    void start();
    void stop();
    void restart();
    void sendCommand(String command);
    
    // Status queries
    Status getStatus();
    List<String> getOnlinePlayers();
    List<String> getRecentErrors();
    double getTps();
    String getRamUsage();
    String getUptime();
    int getMaxPlayers();
    
    // Event listeners
    void addLogListener(Consumer<String> listener);
    void removeLogListener(Consumer<String> listener);
    void addStatusListener(Consumer<Status> listener);
    void removeStatusListener(Consumer<Status> listener);
    
    // Utility methods
    boolean isRunning();
    boolean isStopped();
    boolean isStarting();
    boolean isStopping();
}
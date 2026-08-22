package core;

import org.junit.jupiter.api.*;
import java.util.*;

/**
 * Strategy factory test - ayrı test sınıfı.
 */
public class StrategyFactoryTest {

    /**
     * Test strategy factory.
     */
    @Test
    @DisplayName("Test strategy factory")
    void testStrategyFactory() {
        System.out.println("\n=== Test: Strategy Factory ===");

        BackupManager.BackupStrategyFactory factory = new BackupManager.BackupStrategyFactory();

        // Test creating ZIP strategy
        System.out.println("Creating ZIP strategy...");
        IBackupStrategy zipStrategy = factory.createStrategy("ZIP");

        Assertions.assertNotNull(zipStrategy, "ZIP strategy should be created");
        Assertions.assertInstanceOf(ZipBackupStrategy.class, zipStrategy, "Strategy should be ZipBackupStrategy");
        System.out.println("ZIP strategy created successfully: YES");
        System.out.println("Strategy class: " + zipStrategy.getClass().getName());

        // Test available strategies
        List<String> availableStrategies = factory.getAvailableStrategies();
        System.out.println("Available strategies: " + availableStrategies);

        Assertions.assertTrue(availableStrategies.contains("ZIP"), "ZIP strategy should be in available strategies");
        System.out.println("ZIP strategy listed in available strategies: YES");
    }
}
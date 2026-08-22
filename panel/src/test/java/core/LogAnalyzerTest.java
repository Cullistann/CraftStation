package core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LogAnalyzerTest {

    @Test
    @DisplayName("Purpur TPS history satirindan ilk TPS degerini cikarir")
    void extractTpsFromPurpurHistoryLine() {
        String line = "[12:44:22] [Server thread/INFO]: TPS from last 5s, 1m, 5m, 15m: 20,0, 19,7, 19,9, 20,0";

        assertEquals(20.0, LogAnalyzer.extractTps(line));
    }

    @Test
    @DisplayName("Direkt TPS formatini destekler")
    void extractTpsFromDirectLine() {
        assertEquals(19.8, LogAnalyzer.extractTps("TPS: 19.8"));
    }

    @Test
    @DisplayName("TPS icermeyen satirlari yok sayar")
    void extractTpsReturnsNullForIrrelevantLine() {
        assertNull(LogAnalyzer.extractTps("[Server thread/INFO]: Starting minecraft server version 26.1.2"));
    }

    @Test
    @DisplayName("Tamsayı TPS degerlerini destekler")
    void extractIntegerTpsValues() {
        // Purpur history format with integer
        assertEquals(20.0, LogAnalyzer.extractTps("TPS from last 5s: 20"));
        
        // Direct format with integer
        assertEquals(20.0, LogAnalyzer.extractTps("TPS: 20"));
        
        // Standalone format with integer
        assertEquals(20.0, LogAnalyzer.extractTps("20 TPS"));
        
        // Colored format with integer (must contain 'TPS' for the method guard)
        assertEquals(20.0, LogAnalyzer.extractTps("TPS: §a*20"));
    }

    @Test
    @DisplayName("Kucuk harf tps ve saf renk kodu iceren satirlari destekler")
    void extractLowercaseAndColoredOnlyTps() {
        // Lowercase tps
        assertEquals(20.0, LogAnalyzer.extractTps("tps: 20"));
        assertEquals(19.9, LogAnalyzer.extractTps("tps from last 5s: 19.9"));
        
        // Colored only (no 'TPS' letters)
        assertEquals(20.0, LogAnalyzer.extractTps("§a*20"));
        assertEquals(18.5, LogAnalyzer.extractTps("§c18.5"));
    }
}

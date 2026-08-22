package core;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Lang class with Java 25 features.
 */
class LangTest {

    @BeforeEach
    void setUp() {
        Lang.resetInstance();
    }

    @AfterEach
    void tearDown() {
        Lang.resetInstance();
    }

    @Test
    @DisplayName("Test singleton instance creation")
    void testSingletonInstance() {
        Lang instance1 = Lang.getInstance();
        Lang instance2 = Lang.getInstance();

        assertNotNull(instance1);
        assertNotNull(instance2);
        assertSame(instance1, instance2, "Should return the same instance");
    }

    @Test
    @DisplayName("Test language switching")
    void testLanguageSwitching() {
        Lang lang = Lang.getInstance();

        // Default should be Turkish
        assertEquals("tr", lang.getCurrentLanguage().code());

        // Switch to English
        lang.setLanguage("en");
        assertEquals("en", lang.getCurrentLanguage().code());

        // Switch back to Turkish
        lang.setLanguage("tr");
        assertEquals("tr", lang.getCurrentLanguage().code());
    }

    @Test
    @DisplayName("Test string retrieval in Turkish")
    void testTurkishStrings() {
        Lang lang = Lang.getInstance();
        lang.setLanguage("tr");

        assertEquals("CraftStation", lang.get("LANG_TITLE"));
        assertEquals("Başlat", lang.get("BTN_START"));
        assertEquals("Durdur", lang.get("BTN_STOP"));
        assertEquals("Yeniden Başlat", lang.get("BTN_RESTART"));
    }

    @Test
    @DisplayName("Test string retrieval in English")
    void testEnglishStrings() {
        Lang lang = Lang.getInstance();
        lang.setLanguage("en");

        assertEquals("CraftStation", lang.get("LANG_TITLE"));
        assertEquals("Start", lang.get("BTN_START"));
        assertEquals("Stop", lang.get("BTN_STOP"));
        assertEquals("Restart", lang.get("BTN_RESTART"));
    }

    @Test
    @DisplayName("Test fallback to English for missing keys")
    void testFallbackToEnglish() {
        Lang lang = Lang.getInstance();
        lang.setLanguage("de"); // German, but strings are English fallback

        // Should fall back to English
        assertEquals("Start", lang.get("BTN_START"));
    }

    @Test
    @DisplayName("Test key return for non-existent strings")
    void testNonExistentKey() {
        Lang lang = Lang.getInstance();
        lang.setLanguage("en");

        String nonExistentKey = "NON_EXISTENT_KEY_12345";
        assertEquals(nonExistentKey, lang.get(nonExistentKey));
    }

    @Test
    @DisplayName("Test formatted strings")
    void testFormattedStrings() {
        Lang lang = Lang.getInstance();
        lang.setLanguage("en");

        // Test with a formatted string (if we had one)
        // For now, test basic formatting
        String result = lang.get("TEST_FORMAT", "value1", 123);
        assertEquals("TEST_FORMAT", result); // Fallback to key

        // Add a test string
        lang.fromCode("en").strings().put("WELCOME_MSG", "Welcome, %s! Your ID is %d");
        assertEquals("Welcome, John! Your ID is 123",
                lang.get("WELCOME_MSG", "John", 123));
    }

    @Test
    @DisplayName("Test available languages")
    void testAvailableLanguages() {
        Lang lang = Lang.getInstance();
        var languages = lang.getAvailableLanguages();

        assertNotNull(languages);
        assertFalse(languages.isEmpty());

        // Should have at least Turkish and English
        boolean hasTurkish = languages.stream()
                .anyMatch(l -> l.code().equals("tr"));
        boolean hasEnglish = languages.stream()
                .anyMatch(l -> l.code().equals("en"));

        assertTrue(hasTurkish, "Should have Turkish language");
        assertTrue(hasEnglish, "Should have English language");
    }

    @Test
    @DisplayName("Test language from code")
    void testLanguageFromCode() {
        Lang lang = Lang.getInstance();

        Lang.Language turkish = lang.fromCode("tr");
        assertNotNull(turkish);
        assertEquals("tr", turkish.code());
        assertEquals("Türkçe", turkish.displayName());

        Lang.Language english = lang.fromCode("en");
        assertNotNull(english);
        assertEquals("en", english.code());
        assertEquals("English", english.displayName());

        // Invalid code should return null
        assertNull(lang.fromCode("xx"));
    }

    @Test
    @DisplayName("Test static convenience methods")
    void testStaticMethods() {
        // Test getString
        Lang.setLanguageStatic(Lang.TURKISH);
        assertEquals("Başlat", Lang.get("BTN_START"));

        // Test fromCodeStatic
        Lang.Language lang = Lang.fromCodeStatic("en");
        assertNotNull(lang);
        assertEquals("en", lang.code());

        // Test getCurrentLanguageStatic
        Lang.setLanguageStatic(Lang.TURKISH);
        assertEquals("tr", Lang.getCurrentLanguageStatic().code());
    }



    @Test
    @DisplayName("Test null key handling")
    void testNullKey() {
        Lang lang = Lang.getInstance();

        assertThrows(NullPointerException.class, () -> {
            lang.get(null);
        });
    }
}
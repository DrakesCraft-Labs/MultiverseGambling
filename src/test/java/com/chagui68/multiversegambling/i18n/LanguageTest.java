package com.chagui68.multiversegambling.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class LanguageTest {

    @Test
    void acceptsCodesInAnyShape() {
        assertEquals(Language.ES, Language.match("es"));
        assertEquals(Language.ES, Language.match("ES"));
        assertEquals(Language.ES, Language.match(" es "));
        assertEquals(Language.ES, Language.match("es_es"));
        assertEquals(Language.ES, Language.match("es-AR"));
        assertEquals(Language.ES, Language.match("es_MX"));
    }

    @Test
    void acceptsNamesInBothLanguages() {
        assertEquals(Language.ES, Language.match("spanish"));
        assertEquals(Language.ES, Language.match("Spanish"));
        assertEquals(Language.ES, Language.match("español"));
        assertEquals(Language.ES, Language.match("Español"));
        assertEquals(Language.EN, Language.match("english"));
        assertEquals(Language.EN, Language.match("English"));
        assertEquals(Language.EN, Language.match("en_US"));
    }

    @Test
    void unknownInputFallsBackToEnglish() {
        assertNull(Language.match("de"));
        assertNull(Language.match("klingon"));
        assertNull(Language.match(""));
        assertNull(Language.match("   "));
        assertNull(Language.match(null));
        assertEquals(Language.EN, Language.of("de"));
        assertEquals(Language.EN, Language.of(null));
        assertEquals(Language.ES, Language.of("es"));
    }

    @Test
    void shippedCodesAreStable() {
        assertTrue(Language.isShipped("es"));
        assertTrue(Language.isShipped("EN"));
        assertFalse(Language.isShipped("fr"));
        assertEquals(java.util.List.of("en", "es"), Language.codes());
        assertEquals("es.yml", Language.ES.fileName());
        assertEquals("en.yml", Language.EN.fileName());
        assertEquals("Español", Language.ES.nativeName());
        assertEquals("Spanish", Language.ES.englishName());
        assertEquals("English", Language.EN.nativeName());
    }
}

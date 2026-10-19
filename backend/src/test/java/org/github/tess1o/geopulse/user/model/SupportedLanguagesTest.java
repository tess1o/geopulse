package org.github.tess1o.geopulse.user.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class SupportedLanguagesTest {

    @Test
    void isExactCodeAcceptsOnlyDeclaredCodesVerbatim() {
        // Backs @SupportedLanguage: the stored value is used as-is by the client, so unlike fromCode this
        // must not trim or lowercase.
        for (SupportedLanguages language : SupportedLanguages.values()) {
            assertTrue(SupportedLanguages.isExactCode(language.getCode()),
                    "isExactCode must accept declared code '" + language.getCode() + "'");
        }
        assertFalse(SupportedLanguages.isExactCode("xx"));
        assertFalse(SupportedLanguages.isExactCode(""));
        assertFalse(SupportedLanguages.isExactCode("EN"));
        assertFalse(SupportedLanguages.isExactCode("en "));
        assertFalse(SupportedLanguages.isExactCode(null));
    }

    @Test
    void resolvesKnownCodesCaseInsensitivelyAndTrimmed() {
        assertEquals(Optional.of(SupportedLanguages.EN), SupportedLanguages.fromCode("en"));
        assertEquals(Optional.of(SupportedLanguages.UK), SupportedLanguages.fromCode("uk"));
        assertEquals(Optional.of(SupportedLanguages.UK), SupportedLanguages.fromCode("UK"));
        assertEquals(Optional.of(SupportedLanguages.UK), SupportedLanguages.fromCode("  uk  "));
    }

    @Test
    void reportsUnknownCodesAsEmpty() {
        assertEquals(Optional.empty(), SupportedLanguages.fromCode("xx"));
        assertEquals(Optional.empty(), SupportedLanguages.fromCode(""));
        assertEquals(Optional.empty(), SupportedLanguages.fromCode("   "));
        assertEquals(Optional.empty(), SupportedLanguages.fromCode(null));
    }

    @Test
    void normalizeOrDefaultFallsBackRatherThanFailing() {
        // Used on read paths: an older row or a removed language must degrade to English, not throw.
        assertEquals("uk", SupportedLanguages.normalizeOrDefault("uk"));
        assertEquals("en", SupportedLanguages.normalizeOrDefault("xx"));
        assertEquals("en", SupportedLanguages.normalizeOrDefault(null));
        assertEquals("en", SupportedLanguages.DEFAULT.getCode());
    }

    @Test
    void allowedCodesListsEveryLanguageForErrorMessages() {
        String allowed = SupportedLanguages.allowedCodes();
        for (SupportedLanguages language : SupportedLanguages.values()) {
            assertTrue(allowed.contains(language.getCode()),
                    "allowedCodes() must mention '" + language.getCode() + "'");
        }
    }
}

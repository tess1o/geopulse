package org.github.tess1o.geopulse.user.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class SupportedLanguagesTest {

    @Test
    void patternAcceptsEveryDeclaredCode() {
        // The class-load guard exists because PATTERN is a literal (annotations need compile-time
        // constants) and so cannot be derived from the constants. Touching the class at all runs it,
        // but assert the contract explicitly so a divergence names the offending constant.
        for (SupportedLanguages language : SupportedLanguages.values()) {
            assertTrue(language.getCode().matches(SupportedLanguages.PATTERN),
                    "PATTERN must accept declared code '" + language.getCode() + "'");
        }
    }

    @Test
    void patternMatchesTheAllowedCodesExactly() {
        // Guards the other direction: PATTERN must not be so wide that it admits a language the enum
        // does not declare, which would let @Pattern wave through a value validation then rejects.
        assertTrue("en".matches(SupportedLanguages.PATTERN));
        assertTrue("uk".matches(SupportedLanguages.PATTERN));
        assertFalse("de".matches(SupportedLanguages.PATTERN));
        assertFalse("".matches(SupportedLanguages.PATTERN));
        assertFalse("EN".matches(SupportedLanguages.PATTERN));
        assertFalse("en ".matches(SupportedLanguages.PATTERN));
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
        assertEquals(Optional.empty(), SupportedLanguages.fromCode("de"));
        assertEquals(Optional.empty(), SupportedLanguages.fromCode(""));
        assertEquals(Optional.empty(), SupportedLanguages.fromCode("   "));
        assertEquals(Optional.empty(), SupportedLanguages.fromCode(null));
    }

    @Test
    void normalizeOrDefaultFallsBackRatherThanFailing() {
        // Used on read paths: an older row or a removed language must degrade to English, not throw.
        assertEquals("uk", SupportedLanguages.normalizeOrDefault("uk"));
        assertEquals("en", SupportedLanguages.normalizeOrDefault("de"));
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

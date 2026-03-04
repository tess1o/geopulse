package org.github.tess1o.geopulse.geocoding.service;

import org.github.tess1o.geopulse.geocoding.model.GeonamesCountryRecord;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class GeonamesCountryLineParserTest {
    private final GeonamesCountryLineParser parser = new GeonamesCountryLineParser();
    @Test
    void parseLine_ShouldSkipCommentAndBlankLines() {
        assertTrue(parser.parseLine("#ISO\tISO3\tISO-Numeric").isEmpty());
        assertTrue(parser.parseLine("   ").isEmpty());
    }
    @Test
    void parseLine_ShouldParseValidCountryInfoRow() {
        String line = "BE\tBEL\t056\tBE\tBelgium\tBrussels\t30510\t11555997\tEU\t.be\tEUR\tEuro\t32\t####\t^\\d{4}$\tnl-BE,fr-BE,de-BE\t2802361\tFR,DE,LU,NL\t";
        GeonamesCountryRecord result = parser.parseLine(line).orElseThrow();
        assertEquals("BE", result.isoAlpha2());
        assertEquals("BEL", result.isoAlpha3());
        assertEquals(56, result.isoNumeric());
        assertEquals("Belgium", result.countryName());
        assertEquals("Brussels", result.capital());
        assertEquals(30510.0, result.areaSqKm());
        assertEquals(11555997L, result.population());
        assertEquals("EU", result.continent());
        assertEquals("EUR", result.currencyCode());
        assertEquals(2802361L, result.geonameId());
        assertEquals("FR,DE,LU,NL", result.neighbors());
    }
    @Test
    void parseLine_ShouldReturnEmpty_WhenIsoOrCountryNameMissing() {
        String missingIso = "\tBEL\t056\tBE\tBelgium\tBrussels\t30510\t11555997\tEU\t.be\tEUR\tEuro\t32\t####\t^\\d{4}$\tnl-BE\t2802361\tFR,DE,LU,NL\t";
        String missingCountryName = "BE\tBEL\t056\tBE\t\tBrussels\t30510\t11555997\tEU\t.be\tEUR\tEuro\t32\t####\t^\\d{4}$\tnl-BE\t2802361\tFR,DE,LU,NL\t";
        assertTrue(parser.parseLine(missingIso).isEmpty());
        assertTrue(parser.parseLine(missingCountryName).isEmpty());
    }
    @Test
    void parseLine_ShouldKeepRawColumnValuesWithoutNormalization() {
        String line = "uaa\tukr\t804\tUP\tUkraine\tKyiv\t603700\t37000000\teu\t.ua\tuah\tHryvnia\t380\t####\t^\\d{5}$\t" +
                "uk,ru\t690791\tpl,sk,hu,ro,md,ru,by\tEQ";
        GeonamesCountryRecord result = parser.parseLine(line).orElseThrow();
        assertEquals("uaa", result.isoAlpha2());
        assertEquals("ukr", result.isoAlpha3());
        assertEquals("eu", result.continent());
        assertEquals("uah", result.currencyCode());
        assertEquals("pl,sk,hu,ro,md,ru,by", result.neighbors());
    }
}

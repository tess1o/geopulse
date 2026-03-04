package org.github.tess1o.geopulse.geocoding.service;

import org.github.tess1o.geopulse.geocoding.model.GeonamesCityRecord;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class GeonamesCityLineParserTest {
    private final GeonamesCityLineParser parser = new GeonamesCityLineParser();
    @Test
    void parseLine_ShouldParseValidGeonamesRow() {
        String line = "3039163\tSant Julià de Lòria\tSant Julia de Loria\tSan Julia,Sant Julia de Loria\t42.46372\t1.49129\tP\tPPLA\tAD\t\t06\t\t\t\t8022\t\t921\tEurope/Andorra\t2013-11-23";
        GeonamesCityRecord result = parser.parseLine(line).orElseThrow();
        assertEquals(3039163L, result.geonameId());
        assertEquals("Sant Julià de Lòria", result.name());
        assertEquals("Sant Julia de Loria", result.asciiName());
        assertEquals(42.46372, result.latitude());
        assertEquals(1.49129, result.longitude());
        assertEquals("P", result.featureClass());
        assertEquals("PPLA", result.featureCode());
        assertEquals("AD", result.countryCode());
        assertEquals("06", result.admin1Code());
        assertEquals(8022L, result.population());
        assertEquals(921, result.dem());
        assertEquals("Europe/Andorra", result.timezone());
        assertEquals(LocalDate.of(2013, 11, 23), result.modificationDate());
    }
    @Test
    void parseLine_ShouldReturnEmpty_WhenRequiredColumnsInvalid() {
        String missingId = "\tCity\tCity\t\t42.0\t1.0\tP\tPPL\tAD\t\t\t\t\t\t100\t\t\tEurope/Andorra\t2024-11-04";
        String missingName = "123\t\tCity\t\t42.0\t1.0\tP\tPPL\tAD\t\t\t\t\t\t100\t\t\tEurope/Andorra\t2024-11-04";
        String missingLat = "123\tCity\tCity\t\t\t1.0\tP\tPPL\tAD\t\t\t\t\t\t100\t\t\tEurope/Andorra\t2024-11-04";
        String missingLon = "123\tCity\tCity\t\t42.0\t\tP\tPPL\tAD\t\t\t\t\t\t100\t\t\tEurope/Andorra\t2024-11-04";
        assertTrue(parser.parseLine(missingId).isEmpty());
        assertTrue(parser.parseLine(missingName).isEmpty());
        assertTrue(parser.parseLine(missingLat).isEmpty());
        assertTrue(parser.parseLine(missingLon).isEmpty());
    }
    @Test
    void parseLine_ShouldKeepRawColumnValuesWithoutNormalization() {
        String line = "1\tCity Name\tCity Ascii\tAlt1,Alt2\t42.0\t1.0\tPP\tPPL\tbe\t" +
                "cc2\tadmin1\tadmin2\tadmin3\tadmin4\t10\t1\t2\tEurope/Kyiv\t2024-01-01";
        GeonamesCityRecord result = parser.parseLine(line).orElseThrow();
        assertEquals("City Name", result.name());
        assertEquals("City Ascii", result.asciiName());
        assertEquals("PP", result.featureClass());
        assertEquals("be", result.countryCode());
    }
}

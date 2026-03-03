package org.github.tess1o.geopulse.geocoding.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.geocoding.model.GeonamesCountryRecord;

import java.util.Optional;

import static org.github.tess1o.geopulse.geocoding.service.GeonamesParsingUtils.parseDouble;
import static org.github.tess1o.geopulse.geocoding.service.GeonamesParsingUtils.parseInteger;
import static org.github.tess1o.geopulse.geocoding.service.GeonamesParsingUtils.parseLong;

/**
 * Parser for tab-separated rows from GeoNames countryInfo file.
 */
@ApplicationScoped
public class GeonamesCountryLineParser {

    static final int COUNTRY_INFO_COLUMN_COUNT = 19;

    public Optional<GeonamesCountryRecord> parseLine(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }

        String trimmed = line.trim();
        if (trimmed.startsWith("#")) {
            return Optional.empty();
        }

        String[] columns = line.split("\t", -1);
        if (columns.length < COUNTRY_INFO_COLUMN_COUNT) {
            return Optional.empty();
        }

        String isoAlpha2 = columns[0];
        String countryName = columns[4];
        if (isoAlpha2 == null || isoAlpha2.isEmpty() || countryName == null || countryName.isEmpty()) {
            return Optional.empty();
        }

        GeonamesCountryRecord record = new GeonamesCountryRecord(
                isoAlpha2,
                columns[1],
                parseInteger(columns[2]),
                columns[3],
                countryName,
                columns[5],
                parseDouble(columns[6]),
                parseLong(columns[7]),
                columns[8],
                columns[9],
                columns[10],
                columns[11],
                columns[12],
                columns[13],
                columns[14],
                columns[15],
                parseLong(columns[16]),
                columns[17],
                columns[18]
        );

        return Optional.of(record);
    }
}

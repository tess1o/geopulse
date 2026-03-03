package org.github.tess1o.geopulse.geocoding.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.geocoding.model.GeonamesCityRecord;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import static org.github.tess1o.geopulse.geocoding.service.GeonamesParsingUtils.parseDouble;
import static org.github.tess1o.geopulse.geocoding.service.GeonamesParsingUtils.parseInteger;
import static org.github.tess1o.geopulse.geocoding.service.GeonamesParsingUtils.parseLong;

/**
 * Parser for tab-separated rows from GeoNames geoname dump.
 */
@ApplicationScoped
public class GeonamesCityLineParser {

    static final int GEONAMES_COLUMN_COUNT = 19;

    public Optional<GeonamesCityRecord> parseLine(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }

        String[] columns = line.split("\t", -1);
        if (columns.length < GEONAMES_COLUMN_COUNT) {
            return Optional.empty();
        }

        Long geonameId = parseLong(columns[0]);
        String name = columns[1];
        Double latitude = parseDouble(columns[4]);
        Double longitude = parseDouble(columns[5]);

        if (geonameId == null || name == null || name.isEmpty() || latitude == null || longitude == null) {
            return Optional.empty();
        }

        GeonamesCityRecord record = new GeonamesCityRecord(
                geonameId,
                name,
                columns[2],
                columns[3],
                latitude,
                longitude,
                columns[6],
                columns[7],
                columns[8],
                columns[9],
                columns[10],
                columns[11],
                columns[12],
                columns[13],
                parseLong(columns[14]),
                parseInteger(columns[15]),
                parseInteger(columns[16]),
                columns[17],
                parseDate(columns[18])
        );

        return Optional.of(record);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}

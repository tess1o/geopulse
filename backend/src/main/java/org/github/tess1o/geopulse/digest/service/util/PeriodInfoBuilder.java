package org.github.tess1o.geopulse.digest.service.util;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.digest.model.PeriodInfo;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for building period information metadata.
 */
@ApplicationScoped
public class PeriodInfoBuilder {

    private static final DateTimeFormatter MONTH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy");

    /**
     * Builds period information for display.
     *
     * @param year  The year
     * @param month The month (null for yearly digest)
     * @return PeriodInfo with display name and type
     */
    public PeriodInfo buildPeriodInfo(int year, Integer month) {
        if (month != null) {
            YearMonth yearMonth = YearMonth.of(year, month);
            String displayName = yearMonth.format(MONTH_YEAR_FORMATTER);
            return PeriodInfo.builder()
                    .year(year)
                    .month(month)
                    .displayName(displayName)
                    .type("monthly")
                    .build();
        } else {
            return PeriodInfo.builder()
                    .year(year)
                    .month(null)
                    .displayName(String.valueOf(year))
                    .type("yearly")
                    .build();
        }
    }
}

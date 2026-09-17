package org.github.tess1o.geopulse.digest.service.util;

import jakarta.enterprise.context.ApplicationScoped;
import org.github.tess1o.geopulse.digest.model.PeriodInfo;

/**
 * Utility class for building period information metadata.
 */
@ApplicationScoped
public class PeriodInfoBuilder {

    /**
     * Builds period information for display.
     *
     * @param year  The year
     * @param month The month (null for yearly digest)
     * @return semantic period information
     */
    public PeriodInfo buildPeriodInfo(int year, Integer month) {
        return PeriodInfo.builder().year(year).month(month).build();
    }
}

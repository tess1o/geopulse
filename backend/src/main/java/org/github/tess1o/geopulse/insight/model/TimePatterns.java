package org.github.tess1o.geopulse.insight.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.github.tess1o.geopulse.shared.api.MessageDescriptor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimePatterns {
    /**
     * Year-month of the most active month, ISO-8601 ({@code YYYY-MM}), or null if no activity is
     * recorded. Locale-neutral so the frontend formats the month name for the active locale.
     */
    private String mostActiveYearMonth;
    private MessageDescriptor monthlyComparison;
    /**
     * ISO-8601 day-of-week of the busiest day (1 = Monday .. 7 = Sunday), or null if no activity is
     * recorded. Locale-neutral so the frontend formats the weekday name for the active locale.
     */
    private Integer busiestDayOfWeek;
    private MessageDescriptor dayInsight;
    /**
     * Hour of day (0-23, local to the user's timezone data) of the most active time, or null if no
     * activity is recorded. Locale-neutral so the frontend formats it per the user's time preference.
     */
    private Integer mostActiveHour;
    private MessageDescriptor timeInsight;
}

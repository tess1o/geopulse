package org.github.tess1o.geopulse.insight.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimePatterns {
    private String mostActiveMonth;
    private String monthlyComparison;
    private String busiestDayOfWeek;
    private String dayInsight;
    private String mostActiveTime;
    private String timeInsight;
}
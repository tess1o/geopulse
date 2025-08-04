package org.github.tess1o.geopulse.insight.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Badge {
    private String id;
    private String title;
    private String description;
    private String icon;
    private boolean earned;
    private String earnedDate;
    private Integer progress;
    private Integer current;
    private Integer target;
}
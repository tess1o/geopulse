package org.github.tess1o.geopulse.digest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DigestReplay {
    private int year;
    /** The month of a monthly replay; null for a yearly one. */
    private Integer month;
    private List<DigestReplayTrip> trips;
}

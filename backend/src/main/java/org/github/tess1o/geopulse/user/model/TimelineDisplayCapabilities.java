package org.github.tess1o.geopulse.user.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Timeline display features an administrator has made available. Server-derived and read-only:
 * never stored per user and never accepted in an update.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimelineDisplayCapabilities {

    /** Whether map matching is enabled globally and configured by an administrator. */
    private boolean mapMatchingAvailable;

    /** Whether an administrator enabled a public Panoramax endpoint. */
    private boolean panoramaxAvailable;

    /** Read-only public STAC endpoint used by the browser layer and viewer. */
    private String panoramaxEndpoint;
}

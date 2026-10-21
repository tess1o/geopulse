package org.github.tess1o.geopulse.shared.map;

/**
 * How the timeline map colors the path. DAY gives each day of a multi-day range its own color;
 * the concrete colors are resolved in the frontend from the color scheme and day palette.
 */
public enum PathColorMode {
    SINGLE,
    DAY
}

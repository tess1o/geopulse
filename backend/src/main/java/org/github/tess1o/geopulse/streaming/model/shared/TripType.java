package org.github.tess1o.geopulse.streaming.model.shared;

/**
 * Enumeration of trip types that can be classified by analyzing GPS movement patterns.
 * Trip classification is based on speed analysis and movement characteristics.
 */
public enum TripType {
    /**
     * Walking trip - characterized by low speeds and human movement patterns.
     * Always enabled (mandatory type).
     */
    WALK,

    /**
     * Bicycle trip - characterized by medium speeds (8-25 km/h average).
     * Optional type, disabled by default.
     */
    BICYCLE,

    /**
     * Running trip - characterized by medium-low speeds (7-14 km/h average).
     * Optional type, disabled by default. Separates running from walking and cycling.
     * When disabled, running speeds are captured by BICYCLE (if enabled) or CAR.
     */
    RUNNING,

    /**
     * Car trip - characterized by higher speeds and vehicular movement patterns.
     * Always enabled (mandatory type).
     */
    CAR,

    /**
     * Train trip - characterized by high sustained speeds with low variance.
     * Optional type, disabled by default. Distinguished from cars by steady speed.
     */
    TRAIN,

    /**
     * Flight trip - characterized by very high speeds (400+ km/h average or 500+ km/h peak).
     * Optional type, disabled by default.
     */
    FLIGHT,

    /**
     * Unknown trip type - used when classification cannot be determined reliably
     * due to insufficient data or ambiguous speed patterns.
     */
    UNKNOWN;
}
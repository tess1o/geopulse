/**
 * Movement types, keyed by the backend\'s locale-neutral `TripType` enum value.
 *
 * Single source for the labels shared by the profile tab\'s MultiSelect and trip reconstruction.
 *
 * Note: Journey Insights labels the same enum value differently -- `UNKNOWN` reads 'Unclassified'
 * there while this table says 'Unknown'. They are kept separate deliberately, so translating does not
 * silently change either page\'s copy; unifying them is a copy decision, not a translation one.
 */
export default {
    WALK: 'Lopen',
    RUNNING: 'Hardlopen',
    BICYCLE: 'Fiets',
    CAR: 'Auto',
    MOTORCYCLE: 'Motorfiets',
    PUBLIC_TRANSPORT: 'Openbaar Vervoer',
    TRAIN: 'Trein',
    FLIGHT: 'Vlieg',
    BOAT: 'Boot',
    UNKNOWN: 'Onbekend'
}


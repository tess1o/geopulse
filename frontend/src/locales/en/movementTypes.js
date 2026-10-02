/**
 * Movement types, keyed by the backend's locale-neutral `TripType` enum value.
 *
 * Single source for the labels shared by the profile tab's MultiSelect and trip reconstruction.
 *
 * Note: Journey Insights labels the same enum value differently -- `UNKNOWN` reads 'Unclassified'
 * there while this table says 'Unknown'. They are kept separate deliberately, so translating does not
 * silently change either page's copy; unifying them is a copy decision, not a translation one.
 */
export default {
    WALK: 'Walk',
    RUNNING: 'Running',
    BICYCLE: 'Bicycle',
    CAR: 'Car',
    MOTORCYCLE: 'Motorcycle',
    PUBLIC_TRANSPORT: 'Public Transportation',
    TRAIN: 'Train',
    FLIGHT: 'Flight',
    BOAT: 'Boat',
    UNKNOWN: 'Unknown'
}

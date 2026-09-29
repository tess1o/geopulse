/**
 * Map controls, layers, and popups.
 *
 * EN values are copied verbatim from the literals they replace: the test suite is pinned to English
 * and asserts exact copy, so these files are a snapshot rather than a chance to reword.
 */
export default {
    controls: {
        hideFavorites: 'Hide Favorites',
        showFavorites: 'Show Favorites',
        hideTimeline: 'Hide Timeline',
        showTimeline: 'Show Timeline',
        hidePath: 'Hide Path',
        showPath: 'Show Path',
        heatmapTitle: 'Heatmap',
        stays: 'Stays',
        trips: 'Trips',
        turnOff: 'Turn off',
        hidePhotos: 'Hide Photos',
        showPhotos: 'Show Photos',
        hideNotes: 'Hide Notes',
        showNotes: 'Show Notes',
        hideWeather: 'Hide Weather',
        showWeather: 'Show Weather',
        hide3dBuildings: 'Hide 3D buildings',
        show3dBuildings: 'Show 3D buildings',
        moreControls: 'More map controls',
        zoomToData: 'Zoom to Data',
        heatmapSelectDateRange: 'Select a date range to enable heatmap',
        heatmapEnabledTitle: 'Heatmap enabled',
        heatmapShow: 'Show heatmap',
        routeDisplayRaw: 'Showing raw GPS route. Click to compare matched and raw GPS routes',
        routeDisplayComparison: 'Comparing matched route with raw GPS. Click to show matched route',
        routeDisplayMatched: 'Showing matched route. Click to show raw GPS route',
        rawGpsLoading: 'Loading raw GPS points',
        rawGpsHide: 'Hide raw GPS points',
        rawGpsShow: 'Show raw GPS points',
        panoramaxUnsupported: 'Panoramax coverage requires MapLibre vector maps',
        panoramaxHide: 'Hide Panoramax coverage',
        panoramaxShow: 'Show Panoramax coverage',
        heatmapStaysMenu: 'Heatmap: Stays',
        heatmapTripsMenu: 'Heatmap: Trips',
        heatmapTurnOffMenu: 'Turn off Heatmap'
    },
    tripReplay: {
        pause: 'Pause replay',
        play: 'Play replay',
        stop: 'Stop replay',
        setSpeed: 'Set speed {speed}x',
        followCamera: 'Follow camera',
        follow: 'Follow',
        enable3d: 'Enable 3D camera',
        hideControls: 'Hide replay controls',
        showControls: 'Show replay controls',
        replay: 'Replay'
    },
    messages: {
        engineFatal: 'Map engine failed to initialize. Falling back to raster mode.',
        genericWarning: 'Map warning'
    },
    timelineMap: {
        contextMenu: {
            wasIHere: 'Was I here?',
            planToVisitHere: 'Plan to visit here',
            addToFavorites: 'Add to Favorites',
            addAreaToFavorites: 'Add an area to Favorites',
            viewAllVisits: 'View all visits',
            edit: 'Edit',
            delete: 'Delete',
            editPlannedItem: 'Edit planned item',
            deletePlannedItem: 'Delete planned item'
        },
        addFavoriteDialog: {
            pointHeader: 'Add To Favorites',
            areaHeader: 'Add Area To Favorites'
        },
        clearTripSelection: 'Clear trip selection',
        tripSummaryHoverHint: 'Hover the route to see the time and speed at any point',
        zoomControl: {
            currentLocation: 'Zoom to Current Location',
            data: 'Zoom to Data'
        },
        immichError: {
            genericTitle: 'Immich Photos Error',
            genericDetail: 'Failed to load photos from Immich',
            fetchTitle: 'Failed to Load Photos',
            fetchDetailFallback: 'Unable to fetch photos from your Immich server. Please check your configuration.',
            refreshTitle: 'Refresh Failed',
            refreshDetailFallback: 'Unable to refresh photos from Immich. Please try again.',
            configTitle: 'Configuration Error',
            configDetailFallback: 'Immich configuration is invalid. Please check your server settings.'
        },
        notesError: {
            genericTitle: 'Notes Error',
            genericDetail: 'Failed to load notes',
            fetchTitle: 'Failed to Load Notes',
            fetchDetailFallback: 'Unable to fetch notes for this date range.',
            refreshTitle: 'Refresh Failed',
            refreshDetailFallback: 'Unable to refresh notes. Please try again.'
        },
        addFavoriteFailed: 'Could not add favorite. Location data was missing. Please try again.',
        rawGpsLimited: {
            summary: 'Raw GPS points limited',
            detail: 'Showing {returnedCount} of {totalCount} points. Narrow the date range for exact inspection.'
        },
        rawGpsLoadFailed: {
            summary: 'Raw GPS Points',
            detail: 'Failed to load raw GPS points for this date range.'
        }
    },
    popups: {
        common: {
            unknown: 'Unknown',
            unknownTime: 'Unknown time',
            unknownLocation: 'Unknown location',
            notAvailable: 'n/a',
            value: 'Value',
            telemetry: 'Telemetry',
            duration: 'Duration',
            distance: 'Distance',
            status: 'Status',
            battery: 'Battery',
            speed: 'Speed',
            accuracy: 'Accuracy'
        },
        favorite: {
            pendingName: 'Pending favorite',
            name: 'Favorite',
            pendingArea: 'Pending area',
            pendingPoint: 'Pending point',
            areaFavorite: 'Area favorite',
            favoritePoint: 'Favorite point',
            category: 'Category',
            description: 'Description',
            address: 'Address'
        },
        friend: {
            defaultName: 'Friend',
            lastSeen: 'Last seen',
            location: 'Location',
            activity: 'Activity',
            atCurrentPositionFor: 'At current position for {duration}',
            movingFor: 'Moving for {duration}',
            avatarAlt: '{name} avatar',
            openInGoogleMaps: 'Open in Google Maps'
        },
        location: {
            lastKnown: 'Your last known GeoPulse location',
            yourLocation: 'Your location',
            lastRecorded: 'Last recorded {time}',
            updated: 'Updated {time}',
            aboutMeters: 'About {value} m',
            sharedLocation: 'Shared location',
            visits: 'Visits',
            places: 'Places',
            lastVisit: 'Last visit',
            openCityDetails: 'Open city details',
            openPlaceDetails: 'Open place details'
        },
        timeline: {
            trip: 'Trip ({movementType})',
            dataGap: 'Data Gap',
            timelineItem: 'Timeline item',
            hoverHint: 'Hover the highlighted route to see when you were there and how fast you were moving.',
            start: 'Start',
            end: 'End',
            averageSpeed: 'Average speed',
            movementTrip: '{movementType} Trip',
            unknownMovement: 'Movement',
            tripStart: 'Trip Start',
            tripEnd: 'Trip End',
            time: 'Time',
            mode: 'Mode',
            panoramaxCoverage: 'Panoramax coverage',
            zoomingIn: 'Zooming in for details…',
            photos: 'Photos',
            panoramicPhotos: '360° photos',
            flatPhotos: 'Flat photos',
            user: 'User',
            stay: 'Stay',
            tripFallback: 'Trip',
            eventsAtLocation: '{count} events at this location',
            itemsAtLocation: '{count} item at this location | {count} items at this location',
            crossType: {
                note: 'Note',
                photoCount: '{count} photos',
                photo: 'Photo',
                sections: {
                    timeline: 'Stays and trips',
                    notes: 'Notes',
                    photos: 'Photos'
                }
            },
            hoverTooltip: {
                speedPrefix: 'Speed: {speed}',
                exactGpsPoint: 'Exact GPS point',
                estimatedBetweenPoints: 'Estimated between points',
                fromTripStart: 'From trip start: {duration}'
            }
        },
        tripPlan: {
            plannedStop: 'Planned stop',
            noDaySet: 'No day set',
            notVisitedManual: 'Not visited (manual)',
            visitedWithConfidence: 'Visited · {confidence}% confidence',
            visited: 'Visited',
            notVisitedYet: 'Not visited yet',
            priority: 'Priority',
            must: 'Must',
            optional: 'Optional'
        },
        weather: {
            observed: 'Observed',
            temperature: 'Temperature',
            precipitation: 'Precipitation',
            wind: 'Wind',
            samplesHere: 'Weather: {count} reading | Weather: {count} readings'
        },
        notes: {
            loadFailed: 'Failed to load notes'
        },
        placesMap: {
            defaultTitle: 'Place Location',
            defaultMarkerName: 'Selected Place'
        },
        viewerLocation: {
            findingLocation: 'Finding your location',
            centerOnLocation: 'Center on your location',
            showLocation: 'Show your location',
            hideLocation: 'Hide your location'
        },
        sharedLocation: {
            avatarAlt: 'Avatar'
        },
        rawGps: {
            coordinates: 'Coordinates',
            altitude: 'Altitude',
            locationUnavailable: 'Location unavailable',
            findingLocation: 'Finding location...',
            pointsHere: '{count} GPS points here',
            rawGpsPoint: 'Raw GPS point',
            favoriteSource: 'Favorite',
            geocodingSource: 'Geocoding',
            showingPoints: 'Showing first {shown} of {total} points',
            notAvailable: 'N/A'
        }
    }
}

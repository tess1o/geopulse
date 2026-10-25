/**
 * Map controls, layers, and popups (German).
 */
export default {
    controls: {
        hideFavorites: 'Favoriten ausblenden',
        showFavorites: 'Favoriten anzeigen',
        hideTimeline: 'Zeitleiste ausblenden',
        showTimeline: 'Zeitleiste anzeigen',
        hidePath: 'Strecke ausblenden',
        showPath: 'Strecke anzeigen',
        heatmapTitle: 'Heatmap',
        stays: 'Aufenthalte',
        trips: 'Fahrten',
        turnOff: 'Ausschalten',
        hidePhotos: 'Fotos ausblenden',
        showPhotos: 'Fotos anzeigen',
        hideNotes: 'Notizen ausblenden',
        showNotes: 'Notizen anzeigen',
        hideWeather: 'Wetter ausblenden',
        showWeather: 'Wetter anzeigen',
        hide3dBuildings: '3D-Gebäude ausblenden',
        show3dBuildings: '3D-Gebäude anzeigen',
        moreControls: 'Weitere Kartensteuerung',
        zoomToData: 'Auf Daten zoomen',
        heatmapSelectDateRange: 'Wähle einen Zeitraum, um die Heatmap zu aktivieren',
        heatmapEnabledTitle: 'Heatmap aktiv',
        heatmapShow: 'Heatmap anzeigen',
        routeDisplayRaw: 'Rohe GPS-Route wird angezeigt. Klicken, um angepasste und rohe Route zu vergleichen',
        routeDisplayComparison: 'Angepasste Route wird mit rohem GPS verglichen. Klicken, um die angepasste Route zu zeigen',
        routeDisplayMatched: 'Angepasste Route wird angezeigt. Klicken, um die rohe GPS-Route zu zeigen',
        rawGpsLoading: 'Rohe GPS-Punkte werden geladen',
        rawGpsHide: 'Rohe GPS-Punkte ausblenden',
        rawGpsShow: 'Rohe GPS-Punkte anzeigen',
        panoramaxUnsupported: 'Die Panoramax-Abdeckung braucht MapLibre-Vektorkarten',
        panoramaxHide: 'Panoramax-Abdeckung ausblenden',
        panoramaxShow: 'Panoramax-Abdeckung anzeigen',
        heatmapStaysMenu: 'Heatmap: Aufenthalte',
        heatmapTripsMenu: 'Heatmap: Fahrten',
        heatmapTurnOffMenu: 'Heatmap ausschalten',
        dayColorsShow: 'Strecke nach Tag einfärben',
        dayColorsHide: 'Strecke in einer Farbe zeigen'
    },
    tripReplay: {
        pause: 'Wiedergabe pausieren',
        play: 'Wiedergabe starten',
        stop: 'Wiedergabe stoppen',
        setSpeed: 'Geschwindigkeit {speed}x',
        followCamera: 'Kamera folgt',
        follow: 'Folgen',
        enable3d: '3D-Kamera aktivieren',
        hideControls: 'Wiedergabesteuerung ausblenden',
        showControls: 'Wiedergabesteuerung anzeigen',
        replay: 'Wiedergabe'
    },
    messages: {
        engineFatal: 'Die Karten-Engine konnte nicht gestartet werden. Es wird auf den Raster-Modus umgeschaltet.',
        genericWarning: 'Kartenwarnung'
    },
    appearanceMenu: {
        title: 'Kartenfarben',
        buttonLabel: 'Kartenfarben ändern',
        asSharedBy: 'Wie von {name} geteilt',
        asShared: 'Wie geteilt',
        mine: 'Meine Einstellungen',
        remembered: 'Deine Auswahl wird in diesem Browser gespeichert.'
    },
    legend: {
        speed: {
            title: 'Geschwindigkeit',
            ariaLabel: 'Farblegende Geschwindigkeit',
            slow: 'Unter {speed}',
            medium: '{from}–{to}',
            fast: 'Über {speed}',
            unknown: 'Unbekannt'
        },
        heatmap: {
            ariaLabel: 'Farblegende Heatmap',
            low: 'Weniger',
            high: 'Mehr'
        },
        days: {
            ariaLabel: 'Farblegende Tage',
            gradientRange: 'von {from} bis {to}'
        }
    },
    timelineMap: {
        contextMenu: {
            wasIHere: 'War ich hier?',
            planToVisitHere: 'Besuch hier planen',
            addToFavorites: 'Zu Favoriten hinzufügen',
            addAreaToFavorites: 'Bereich zu Favoriten hinzufügen',
            viewAllVisits: 'Alle Besuche ansehen',
            edit: 'Bearbeiten',
            delete: 'Löschen',
            editPlannedItem: 'Geplanten Stopp bearbeiten',
            deletePlannedItem: 'Geplanten Stopp löschen'
        },
        addFavoriteDialog: {
            pointHeader: 'Zu Favoriten hinzufügen',
            areaHeader: 'Bereich zu Favoriten hinzufügen'
        },
        clearTripSelection: 'Auswahl der Fahrt aufheben',
        tripSummaryHoverHint: 'Fahr mit der Maus über die Route, um Zeit und Geschwindigkeit an jedem Punkt zu sehen',
        zoomControl: {
            currentLocation: 'Auf aktuellen Standort zoomen',
            data: 'Auf Daten zoomen'
        },
        immichError: {
            genericTitle: 'Fehler bei Immich-Fotos',
            genericDetail: 'Fotos aus Immich konnten nicht geladen werden',
            fetchTitle: 'Fotos nicht geladen',
            fetchDetailFallback: 'Fotos von deinem Immich-Server konnten nicht abgerufen werden. Prüf deine Einstellungen.',
            refreshTitle: 'Aktualisieren fehlgeschlagen',
            refreshDetailFallback: 'Fotos aus Immich konnten nicht aktualisiert werden. Bitte versuch es noch einmal.',
            configTitle: 'Konfigurationsfehler',
            configDetailFallback: 'Die Immich-Konfiguration ist ungültig. Prüf deine Servereinstellungen.'
        },
        notesError: {
            genericTitle: 'Fehler bei Notizen',
            genericDetail: 'Notizen konnten nicht geladen werden',
            fetchTitle: 'Notizen nicht geladen',
            fetchDetailFallback: 'Für diesen Zeitraum konnten keine Notizen abgerufen werden.',
            refreshTitle: 'Aktualisieren fehlgeschlagen',
            refreshDetailFallback: 'Notizen konnten nicht aktualisiert werden. Bitte versuch es noch einmal.'
        },
        addFavoriteFailed: 'Favorit konnte nicht hinzugefügt werden, weil Standortdaten fehlten. Bitte versuch es noch einmal.',
        rawGpsLimited: {
            summary: 'Rohe GPS-Punkte begrenzt',
            detail: '{returnedCount} von {totalCount} Punkten werden angezeigt. Wähle einen kürzeren Zeitraum für eine genaue Prüfung.'
        },
        rawGpsLoadFailed: {
            summary: 'Rohe GPS-Punkte',
            detail: 'Rohe GPS-Punkte für diesen Zeitraum konnten nicht geladen werden.'
        }
    },
    popups: {
        common: {
            unknown: 'Unbekannt',
            unknownTime: 'Unbekannte Zeit',
            unknownLocation: 'Unbekannter Ort',
            notAvailable: 'k. A.',
            value: 'Wert',
            telemetry: 'Telemetrie',
            duration: 'Dauer',
            distance: 'Strecke',
            status: 'Status',
            battery: 'Akku',
            speed: 'Geschwindigkeit',
            accuracy: 'Genauigkeit'
        },
        favorite: {
            pendingName: 'Neuer Favorit',
            name: 'Favorit',
            pendingArea: 'Neuer Bereich',
            pendingPoint: 'Neuer Punkt',
            areaFavorite: 'Favorit (Bereich)',
            favoritePoint: 'Favorit (Punkt)',
            category: 'Kategorie',
            description: 'Beschreibung',
            address: 'Adresse'
        },
        friend: {
            defaultName: 'Freund',
            lastSeen: 'Zuletzt gesehen',
            location: 'Ort',
            activity: 'Aktivität',
            atCurrentPositionFor: 'Seit {duration} an diesem Ort',
            movingFor: 'Seit {duration} unterwegs',
            avatarAlt: 'Avatar von {name}',
            openInGoogleMaps: 'In Google Maps öffnen'
        },
        location: {
            lastKnown: 'Dein letzter bekannter GeoPulse-Standort',
            yourLocation: 'Dein Standort',
            lastRecorded: 'Zuletzt erfasst {time}',
            updated: 'Aktualisiert {time}',
            aboutMeters: 'Etwa {value} m',
            sharedLocation: 'Geteilter Standort',
            visits: 'Besuche',
            places: 'Orte',
            lastVisit: 'Letzter Besuch',
            openCityDetails: 'Stadtdetails öffnen',
            openPlaceDetails: 'Ortsdetails öffnen'
        },
        timeline: {
            trip: 'Fahrt ({movementType})',
            dataGap: 'Datenlücke',
            timelineItem: 'Zeitleisten-Eintrag',
            hoverHint: 'Fahr mit der Maus über die hervorgehobene Route, um zu sehen, wann du dort warst und wie schnell.',
            start: 'Start',
            end: 'Ende',
            averageSpeed: 'Durchschnittsgeschwindigkeit',
            movementTrip: 'Fahrt ({movementType})',
            unknownMovement: 'Bewegung',
            tripStart: 'Start der Fahrt',
            tripEnd: 'Ende der Fahrt',
            time: 'Zeit',
            mode: 'Art',
            panoramaxCoverage: 'Panoramax-Abdeckung',
            zoomingIn: 'Zoomt für Details hinein …',
            photos: 'Fotos',
            panoramicPhotos: '360°-Fotos',
            flatPhotos: 'Normale Fotos',
            user: 'Person',
            stay: 'Aufenthalt',
            tripFallback: 'Fahrt',
            eventsAtLocation: '{count} Ereignisse an diesem Ort',
            itemsAtLocation: '{count} Eintrag an diesem Ort | {count} Einträge an diesem Ort',
            crossType: {
                note: 'Notiz',
                photoCount: '{count} Fotos',
                photo: 'Foto',
                sections: {
                    timeline: 'Aufenthalte und Fahrten',
                    notes: 'Notizen',
                    photos: 'Fotos'
                }
            },
            hoverTooltip: {
                speedPrefix: 'Geschwindigkeit: {speed}',
                exactGpsPoint: 'Exakter GPS-Punkt',
                estimatedBetweenPoints: 'Zwischen Punkten geschätzt',
                fromTripStart: 'Seit Start der Fahrt: {duration}'
            }
        },
        tripPlan: {
            plannedStop: 'Geplanter Stopp',
            noDaySet: 'Kein Tag festgelegt',
            notVisitedManual: 'Nicht besucht (manuell)',
            visitedWithConfidence: 'Besucht · {confidence} % sicher',
            visited: 'Besucht',
            notVisitedYet: 'Noch nicht besucht',
            priority: 'Priorität',
            must: 'Pflicht',
            optional: 'Optional'
        },
        weather: {
            observed: 'Beobachtet',
            temperature: 'Temperatur',
            precipitation: 'Niederschlag',
            wind: 'Wind',
            samplesHere: 'Wetter: {count} Messwert | Wetter: {count} Messwerte'
        },
        notes: {
            loadFailed: 'Notizen konnten nicht geladen werden'
        },
        placesMap: {
            defaultTitle: 'Lage des Orts',
            defaultMarkerName: 'Ausgewählter Ort'
        },
        viewerLocation: {
            findingLocation: 'Dein Standort wird ermittelt',
            centerOnLocation: 'Auf deinen Standort zentrieren',
            showLocation: 'Deinen Standort anzeigen',
            hideLocation: 'Deinen Standort ausblenden'
        },
        sharedLocation: {
            avatarAlt: 'Avatar'
        },
        rawGps: {
            coordinates: 'Koordinaten',
            altitude: 'Höhe',
            locationUnavailable: 'Ort nicht verfügbar',
            findingLocation: 'Ort wird ermittelt …',
            pointsHere: '{count} GPS-Punkte hier',
            rawGpsPoint: 'Roher GPS-Punkt',
            favoriteSource: 'Favorit',
            geocodingSource: 'Geocoding',
            showingPoints: 'Die ersten {shown} von {total} Punkten',
            notAvailable: 'k. A.'
        }
    }
}

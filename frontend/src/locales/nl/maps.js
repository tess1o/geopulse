/**
 * Map controls, layers, and popups.
 *
 * EN values are copied verbatim from the literals they replace: the test suite is pinned to English
 * and asserts exact copy, so these files are a snapshot rather than a chance to reword.
 */
export default {
    controls: {
        hideFavorites: 'Favorieten verbergen',
        showFavorites: 'Favorieten tonen',
        hideTimeline: 'Tijdlijn verbergen',
        showTimeline: 'Tijdlijn tonen',
        hidePath: 'Pad verbergen',
        showPath: 'Pad tonen',
        heatmapTitle: 'Heatmap',
        stays: 'Verblijfplaatsen',
        trips: 'Reizen',
        turnOff: 'Uitschakelen',
        hidePhotos: 'Foto\'s verbergen',
        showPhotos: 'Foto\'s tonen',
        hideNotes: 'Notities verbergen',
        showNotes: 'Notities tonen',
        hideWeather: 'Weer verbergen',
        showWeather: 'Weer tonen',
        hide3dBuildings: '3D-gebouwen verbergen',
        show3dBuildings: '3D-gebouwen tonen',
        moreControls: 'Meer kaartbediening',
        zoomToData: 'Inzoomen op gegevens',
        heatmapSelectDateRange: 'Selecteer een datumbereik om heatmap in te schakelen',
        heatmapEnabledTitle: 'Heatmap ingeschakeld',
        heatmapShow: 'Heatmap tonen',
        routeDisplayRaw: 'Ruwe GPS-route tonen. Klik om afgestemde en ruwe GPS-routes te vergelijken',
        routeDisplayComparison: 'Afgestemde route vergelijken met ruwe GPS. Klik om afgestemde route te tonen',
        routeDisplayMatched: 'Afgestemde route tonen. Klik om ruwe GPS-route te tonen',
        rawGpsLoading: 'Ruwe GPS-punten laden',
        rawGpsHide: 'Ruwe GPS-punten verbergen',
        rawGpsShow: 'Ruwe GPS-punten tonen',
        panoramaxUnsupported: 'Panoramax-dekking vereist MapLibre-vectorkaarten',
        panoramaxHide: 'Panoramax-dekking verbergen',
        panoramaxShow: 'Panoramax-dekking tonen',
        heatmapStaysMenu: 'Heatmap: Verblijfplaatsen',
        heatmapTripsMenu: 'Heatmap: Reizen',
        heatmapTurnOffMenu: 'Heatmap uitschakelen',
        dayColorsShow: 'Pad kleuren per dag',
        dayColorsHide: 'Pad in één kleur tonen'
    },
    tripReplay: {
        pause: 'Afspelen pauzeren',
        play: 'Afspelen starten',
        stop: 'Afspelen stoppen',
        setSpeed: 'Snelheid instellen {speed}x',
        followCamera: 'Camera volgen',
        follow: 'Volgen',
        enable3d: '3D-camera inschakelen',
        hideControls: 'Afspeelbediening verbergen',
        showControls: 'Afspeelbediening tonen',
        replay: 'Afspelen'
    },
    messages: {
        engineFatal: 'Kaartengine kon niet worden geïnitialiseerd. Overstappen naar rastermodus.',
        genericWarning: 'Kaartwaarschuwing'
    },
    appearanceMenu: {
        title: 'Kaartkleuren',
        buttonLabel: 'Kaartkleuren wijzigen',
        asSharedBy: 'Zo gedeeld door {name}',
        asShared: 'Zo gedeeld',
        mine: 'Mijn instellingen',
        remembered: 'Uw keuze wordt onthouden in deze browser.'
    },
    legend: {
        speed: {
            title: 'Snelheid',
            ariaLabel: 'Snelheidskleurlegenda',
            slow: 'Onder {speed}',
            medium: '{from}–{to}',
            fast: 'Boven {speed}',
            unknown: 'Onbekend'
        },
        heatmap: {
            ariaLabel: 'Heatmapkleurlegenda',
            low: 'Minder',
            high: 'Meer'
        },
        days: {
            ariaLabel: 'Dagkleurlegenda',
            gradientRange: 'van {from} tot {to}'
        }
    },
    timelineMap: {
        contextMenu: {
            wasIHere: 'Was ik hier?',
            planToVisitHere: 'Hier heen reizen plannen',
            addToFavorites: 'Aan favorieten toevoegen',
            addAreaToFavorites: 'Gebied aan favorieten toevoegen',
            viewAllVisits: 'Alle bezoeken bekijken',
            edit: 'Bewerken',
            delete: 'Verwijderen',
            editPlannedItem: 'Gepland item bewerken',
            deletePlannedItem: 'Gepland item verwijderen'
        },
        addFavoriteDialog: {
            pointHeader: 'Aan Favorieten Toevoegen',
            areaHeader: 'Gebied Aan Favorieten Toevoegen'
        },
        clearTripSelection: 'Reisselectie wissen',
        tripSummaryHoverHint: 'Beweeg over de route om de tijd en snelheid op elk punt te zien',
        zoomControl: {
            currentLocation: 'Inzoomen op Huidige Locatie',
            data: 'Inzoomen op Gegevens'
        },
        immichError: {
            genericTitle: 'Immich-foto\'s Fout',
            genericDetail: 'Foto\'s laden van Immich is mislukt',
            fetchTitle: 'Foto\'s Laden Mislukt',
            fetchDetailFallback: 'Foto\'s ophalen van uw Immich-server is mislukt. Controleer uw configuratie.',
            refreshTitle: 'Vernieuwen Mislukt',
            refreshDetailFallback: 'Foto\'s vernieuwen vanuit Immich is mislukt. Probeer het opnieuw.',
            configTitle: 'Configuratiefout',
            configDetailFallback: 'Immich-configuratie is ongeldig. Controleer uw serverinstellingen.'
        },
        notesError: {
            genericTitle: 'Notities Fout',
            genericDetail: 'Notities laden is mislukt',
            fetchTitle: 'Notities Laden Mislukt',
            fetchDetailFallback: 'Notities ophalen voor dit datumbereik is mislukt.',
            refreshTitle: 'Vernieuwen Mislukt',
            refreshDetailFallback: 'Notities vernieuwen is mislukt. Probeer het opnieuw.'
        },
        addFavoriteFailed: 'Favoriet kon niet worden toegevoegd. Locatiegegevens ontbreken. Probeer het opnieuw.',
        rawGpsLimited: {
            summary: 'Ruwe GPS-punten beperkt',
            detail: '{returnedCount} van {totalCount} punten tonen. Beperk het datumbereik voor nauwkeurige inspectie.'
        },
        rawGpsLoadFailed: {
            summary: 'Ruwe GPS-punten',
            detail: 'Ruwe GPS-punten laden voor dit datumbereik is mislukt.'
        }
    },
    popups: {
        common: {
            unknown: 'Onbekend',
            unknownTime: 'Onbekende tijd',
            unknownLocation: 'Onbekende locatie',
            notAvailable: 'nvt',
            value: 'Waarde',
            telemetry: 'Telemetrie',
            duration: 'Duur',
            distance: 'Afstand',
            status: 'Status',
            battery: 'Batterij',
            speed: 'Snelheid',
            accuracy: 'Nauwkeurigheid'
        },
        favorite: {
            pendingName: 'Nieuwe favoriet',
            name: 'Favoriet',
            pendingArea: 'Nieuw gebied',
            pendingPoint: 'Nieuw punt',
            areaFavorite: 'Gebied favoriet',
            favoritePoint: 'Favoriet punt',
            category: 'Categorie',
            description: 'Beschrijving',
            address: 'Adres'
        },
        friend: {
            defaultName: 'Vriend',
            lastSeen: 'Laatst gezien',
            location: 'Locatie',
            activity: 'Activiteit',
            atCurrentPositionFor: 'Op huidige positie gedurende {duration}',
            movingFor: 'In beweging gedurende {duration}',
            avatarAlt: '{name} avatar',
            openInGoogleMaps: 'Open in Google Maps'
        },
        location: {
            lastKnown: 'Uw laatst bekende GeoPulse-locatie',
            yourLocation: 'Uw locatie',
            lastRecorded: 'Laatst geregistreerd {time}',
            updated: 'Bijgewerkt {time}',
            aboutMeters: 'Ongeveer {value} m',
            sharedLocation: 'Gedeelde locatie',
            visits: 'Bezoeken',
            places: 'Plaatsen',
            lastVisit: 'Laatste bezoek',
            openCityDetails: 'Staddetails openen',
            openPlaceDetails: 'Plaatsdetails openen'
        },
        timeline: {
            trip: 'Reis ({movementType})',
            dataGap: 'Datagap',
            timelineItem: 'Tijdlijnitem',
            hoverHint: 'Beweeg over de gemarkeerde route om te zien wanneer u er was en hoe snel u bewoog.',
            start: 'Start',
            end: 'Eind',
            averageSpeed: 'Gemiddelde snelheid',
            movementTrip: '{movementType} Reis',
            unknownMovement: 'Beweging',
            tripStart: 'Reisstart',
            tripEnd: 'Reiseind',
            time: 'Tijd',
            mode: 'Modus',
            panoramaxCoverage: 'Panoramax-dekking',
            zoomingIn: 'Inzoomen voor details…',
            photos: 'Foto\'s',
            panoramicPhotos: '360° foto\'s',
            flatPhotos: 'Platte foto\'s',
            user: 'Gebruiker',
            stay: 'Verblijfplaats',
            tripFallback: 'Reis',
            eventsAtLocation: '{count} gebeurtenissen op deze locatie',
            itemsAtLocation: '{count} item op deze locatie | {count} items op deze locatie',
            crossType: {
                note: 'Notitie',
                photoCount: '{count} foto\'s',
                photo: 'Foto',
                sections: {
                    timeline: 'Verblijfplaatsen en reizen',
                    notes: 'Notities',
                    photos: 'Foto\'s'
                }
            },
            hoverTooltip: {
                speedPrefix: 'Snelheid: {speed}',
                exactGpsPoint: 'Exact GPS-punt',
                estimatedBetweenPoints: 'Geschat tussen punten',
                fromTripStart: 'Vanaf reisstart: {duration}'
            }
        },
        tripPlan: {
            plannedStop: 'Geplande stop',
            noDaySet: 'Geen dag ingesteld',
            notVisitedManual: 'Niet bezocht (handmatig)',
            visitedWithConfidence: 'Bezocht · {confidence}% betrouwbaarheid',
            visited: 'Bezocht',
            notVisitedYet: 'Nog niet bezocht',
            priority: 'Prioriteit',
            must: 'Moet',
            optional: 'Optioneel'
        },
        weather: {
            observed: 'Gewaargenomen',
            temperature: 'Temperatuur',
            precipitation: 'Neerslag',
            wind: 'Wind',
            samplesHere: 'Weer: {count} meting | Weer: {count} metingen'
        },
        notes: {
            loadFailed: 'Notities laden is mislukt'
        },
        placesMap: {
            defaultTitle: 'Plaatslocatie',
            defaultMarkerName: 'Geselecteerde plaats'
        },
        viewerLocation: {
            findingLocation: 'Uw locatie zoeken',
            centerOnLocation: 'Centrer op uw locatie',
            showLocation: 'Uw locatie tonen',
            hideLocation: 'Uw locatie verbergen'
        },
        sharedLocation: {
            avatarAlt: 'Avatar'
        },
        rawGps: {
            pointDetails: 'Puntdetails',
            showMore: 'Meer tonen',
            coordinates: 'Coördinaten',
            altitude: 'Hoogte',
            locationUnavailable: 'Locatie niet beschikbaar',
            findingLocation: 'Locatie zoeken...',
            pointsHere: '{count} GPS-punten hier',
            rawGpsPoint: 'Ruwe GPS-punt',
            favoriteSource: 'Favoriet',
            geocodingSource: 'Geocodering',
            showingPoints: '{shown} van {total} punten tonen',
            notAvailable: 'Nvt'
        }
    }
}

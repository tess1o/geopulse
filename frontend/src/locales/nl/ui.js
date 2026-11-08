/**
 * Shared UI components, search, dashboard, and home content.
 *
 * EN values are copied verbatim from the literals they replace: the test suite is pinned to English
 * and asserts exact copy, so these files are a snapshot rather than a chance to reword.
 */
export default {
    mainApp: {
        shareLabel: 'Delen',
        shareAria: 'Tijdlijn delen',
        reconstructTooltip: 'Voeg een handmatige verblijfplaats of reis toe aan een lege periode van uw tijdlijn.',
        reconstructLabel: 'Ontbrekende activiteit toevoegen…',
        reconstructAria: 'Ontbrekende tijdlijndata toevoegen',
        timelineReportsTab: 'Tijdlijnrapporten'
    },
    dashboard: {
        page: {
            title: 'Dashboard',
            subtitle: 'Overzicht van uw locatiegegevens en analyses',
            selectedPeriodSummary: 'Samenvatting Geselecteerde Periode',
            sevenDaysOverview: 'Overzicht 7 Dagen',
            thirtyDaysOverview: 'Overzicht 30 Dagen',
            topPlacesSelected: 'Top Plaatsen voor geselecteerde periode',
            topPlacesLast7: 'Top Plaatsen voor laatste 7 dagen',
            topPlacesLast30: 'Top Plaatsen voor laatste 30 dagen',
            routeStatsSelected: 'Routestatistieken voor geselecteerde periode',
            routeStatsLast7: 'Routestatistieken voor laatste 7 dagen',
            routeStatsLast30: 'Routestatistieken voor laatste 30 dagen',
            noDataTitle: 'Geen Gegevens Beschikbaar',
            noDataMessage: 'Geen locatiegegevens gevonden voor de geselecteerde tijdsperiodes. Controleer uw GPS-bronnen of selecteer een ander datumbereik.',
            toasts: {
                selectedRangeFailed: 'Statistieken ophalen voor geselecteerde bereik is mislukt',
                weeklyFailed: 'Statistieken ophalen voor week is mislukt',
                monthlyFailed: 'Statistieken ophalen voor maand is mislukt'
            }
        },
        metrics: {
            totalDistance: 'Totale Afstand',
            timeMoving: 'Tijd in Beweging',
            dailyAverage: 'Dagelijks Gemiddelde',
            averageSpeed: 'Gemiddelde Snelheid',
            mostActiveDay: 'Actiefste Dag',
            uniqueLocations: 'Unieke Locaties',
            notAvailable: 'Nvt'
        },
        distanceActivityChart: 'Afstandsactiviteit',
        distanceAxisTitle: 'Afstand ({unit})',
        tooltip: {
            distance: 'Afstand: {value}',
            travelTime: 'Reistijd: {value}',
            locations: 'Locaties: {value}'
        },
        routeAnalysis: {
            noDataTitle: 'Geen Routedata',
            noDataMessage: 'Er zijn geen routes geanalyseerd voor deze periode.',
            uniqueRoutes: 'Unieke Routes',
            mostCommonRoute: 'Meest Voorkomende Route',
            tripsCount: '{count} reizen',
            avgTripDuration: 'Gemiddelde Reisduur',
            longestTripDuration: 'Langste Reis (duur)',
            longestTripDistance: 'Langste Reis (afstand)',
            notAvailable: 'Nvt'
        },
        topPlaces: {
            noDataTitle: 'Geen Plaatsgegevens',
            noDataMessage: 'Er zijn geen plaatsen bezocht gedurende deze periode.',
            visitsCount: '{count} bezoeken',
            totalDuration: '{duration} totaal',
            defaultLocationName: 'Geselecteerde Locatie'
        }
    },
    home: {
        loadContentFailed: 'Startpagina-inhoud laden is mislukt',
        nav: {
            resourcesAriaLabel: 'Projectbronnen',
            documentation: 'Documentatie',
            github: 'GitHub',
            whatsNewAriaLabel: "Wat is er nieuw",
            updateAvailable: 'Nieuw: {version} beschikbaar',
            updateAvailableShort: 'Nieuw: {version}',
            whatsNewTitle: "Wat is er Nieuw in {version}",
            whatsNewEmpty: 'Er zijn nog geen releasenotities beschikbaar voor deze versie.',
            releaseNotesLink: 'Volledige releasenotities op GitHub'
        },
        hero: {
            eyebrow: 'Zelf-gehost Google Timeline alternatief',
            titleWelcomeBack: 'Welkom terug',
            titleDefault: 'Eigen Locatietijdlijn',
            subtitle: 'GeoPulse zet ruwe GPS-punten om in een privélocatietijdlijn met verblijfplaatsen, reizen, kaarten en inzichten, allemaal onder uw controle.',
            accessDisabled: 'Inloggen en registratie zijn momenteel uitgeschakeld door de beheerder.',
            registrationDisabled: 'Registratie is uitgeschakeld. Bestaande gebruikers kunnen nog steeds inloggen.',
            tryDemo: 'Demo proberen',
            startJourney: 'Begin Uw Reis',
            goToTimeline: 'Naar Tijdlijn',
            loadingWorkspace: 'Werkruimte laden...',
            socialProofText: 'Bron beschikbaar op GitHub · Gratis voor persoonlijk gebruik',
            stars: 'Stars',
            forks: 'Forks'
        },
        features: {
            liveTracking: 'Live Tracking',
            smartImport: 'Slim Importeren',
            autoTimeline: 'Auto-Tijdlijn',
            deepInsights: 'Diepe Inzichten',
            immichIntegration: 'Immich-integratie',
            friends: 'Vrienden',
            geofences: 'Geofences',
            ai: 'AI'
        },
        panel: {
            exploreAriaLabel: 'GeoPulse en tips verkennen',
            tabsAriaLabel: 'Mobiele sectietabs',
            featuresTab: 'Functies',
            tipTab: 'Tip',
            tipOfDayTitle: 'Tip van de dag',
            tipOfDayAriaLabel: 'Tip van de dag'
        },
        footer: {
            copyright: '© GeoPulse. Alle rechten voorbehouden.'
        }
    },
    mobileAuth: {
        title: 'Mobiel',
        preparing: 'Mobiele authenticatie voorbereiden...',
        payloadMissing: 'Mobiele authenticatiepayload is niet geretourneerd.',
        opening: 'App openen...',
        timedOut: 'Het openen van de app is verlopen. Keer terug naar de app en probeer het opnieuw.',
        failed: 'Het voltooien van de mobiele authenticatieoverdracht is mislukt.'
    },
    notFound: {
        subtitle: 'Locatie Niet Gevonden',
        title: "Oeps! U bent van de kaart gewandeld",
        description: "De pagina die u zoekt bestaat niet of is verplaatst naar een andere locatie. Laten we u weer op het juiste spoor brengen.",
        optionsTitle: 'Waar wilt u naartoe?',
        options: {
            home: {
                title: 'Start',
                description: 'Begin uw reis vanaf het begin'
            },
            timeline: {
                title: 'Tijdlijn',
                description: 'Uw locatietijdlijn bekijken'
            },
            signIn: {
                title: 'Inloggen',
                description: 'Uw GeoPulse-account benaderen'
            },
            dashboard: {
                title: 'Dashboard',
                description: 'Uw locatie-inzichten controleren'
            },
            signUp: {
                title: 'Registreren',
                description: 'Maak een nieuw account aan'
            }
        },
        goBack: 'Teruggaan',
        helpSummary: "Hulp nodig bij het vinden van wat u zoekt?",
        helpIntro: 'Hier zijn enkele algemene pagina\'s waarnaar u mogelijk op zoek bent:',
        helpLinks: {
            timeline: 'Tijdlijn',
            timelineDesc: 'Uw locatiegeschiedenis bekijken',
            dashboard: 'Dashboard',
            dashboardDesc: 'Locatie-inzichten en analyses',
            journeyInsights: 'Reisinzichten',
            journeyInsightsDesc: 'Reispatronen ontdekken',
            friends: 'Vrienden',
            friendsDesc: 'Uw vriendenverbindingen beheren',
            signIn: 'Inloggen',
            signInDesc: 'Uw account benaderen'
        },
        helpNote: "Als u denkt dat dit een kapotte link is, neem dan contact op met de ondersteuning en we lossen het op."
    },
    darkModeSwitcher: {
        themeTooltip: 'Thema: {label}',
        themeAriaLabel: 'Themamodus: {label}',
        light: 'Licht',
        dark: 'Donker',
        system: 'Systeem',
        currentSuffix: '{label} (huidig)'
    },
    errorReferenceToast: {
        copyAriaLabel: 'Foutreferentie-id kopiëren'
    },
    providerIcon: {
        defaultAlt: 'Pictogram provider'
    },
    gpsFiltering: {
        title: 'GPS-gegevensfiltering',
        filterLabel: 'Onnauwkeurige datapunten filteren',
        filterHint: 'Schakel in om GPS-punten met nauwkeurigheid of snelheid buiten de gedefinieerde limieten eruit te filteren.',
        maxAccuracyLabel: 'Maximaal toegestane nauwkeurigheid (meter)',
        maxAccuracyPlaceholder: 'bijv., 100',
        maxAccuracyHint: 'Punten met nauwkeurigheid boven deze waarde worden afgewezen.',
        maxSpeedLabel: 'Maximaal toegestane snelheid (km/u)',
        maxSpeedPlaceholder: 'bijv., 250',
        maxSpeedHint: 'Punten met snelheid boven deze waarde worden afgewezen.',
        duplicateDetectionTitle: 'Dubbeldetectie',
        duplicateDetectionLabel: 'Dubbeldetectie inschakelen',
        duplicateDetectionHint: 'Sla GPS-punten over die dezelfde locatie hebben binnen een tijdvenster. Nuttig voor apparaten die herhaalde locaties versturen wanneer stationair.',
        thresholdLabel: 'Tijdsdrempel (minuten)',
        thresholdPlaceholder: 'bijv., 2',
        thresholdHint: 'Punten met dezelfde coördinaten (binnen ongeveer 11m) in dit tijdvenster worden overgeslagen. Laat leeg om de globale standaard te gebruiken.'
    },
    pullToRefresh: {
        pullText: 'Trekken om te vernieuwen',
        readyText: 'Loslaten om te vernieuwen',
        refreshingText: 'Vernieuwen...'
    },
    tipOfDayCard: {
        nextTip: 'Volgende tip',
        noTipTitle: 'Nog geen tip beschikbaar',
        noTipDescription: 'Tips verschijnen hier wanneer startpagina-inhoud beschikbaar is.'
    },
    dateRangePicker: {
        labelDefault: 'Datums selecteren',
        placeholderDefault: 'Datumbereik selecteren',
        presetPlaceholderDefault: 'Preset selecteren',
        presets: {
            today: 'Vandaag',
            yesterday: 'Gisteren',
            last7Days: 'Laatste 7 dagen',
            last30Days: 'Laatste 30 dagen'
        },
        maxRangeError: 'Maximaal bereik is {days} dagen',
        periodFallbackName: 'Periode',
        labelFallbackName: 'Label',
        timelineLabelsGroup: 'Tijdlijnlabels',
        presetsGroup: 'Presets'
    },
    transportTypeCard: {
        enableAriaLabel: '{title} detectie inschakelen',
        alwaysActive: 'Altijd actief',
        expandAriaLabel: '{title} uitvouwen',
        collapseAriaLabel: '{title} inklappen',
        disabledMessage: '{title} detectie is momenteel uitgeschakeld. Schakel in om drempels te configureren.'
    },
    appLayout: {
        viewTimelineJob: 'Tijdlijntaak bekijken',
        whatsNew: "Wat is er nieuw",
        readFullReleaseNotes: 'Volledige releasenotities lezen',
        gotIt: 'Begrepen',
        notificationDefaultSummary: 'Melding',
        viewAllNotifications: 'Alle meldingen bekijken',
        openNotification: 'Melding openen'
    },
    exploreFeatures: {
        ariaLabel: 'GeoPulse verkennen',
        kicker: 'GeoPulse verkennen',
        tabsAriaLabel: 'GeoPulse-functies',
        readDocs: 'Documentatie lezen',
        openInApp: 'Open in app',
        liveTracking: {
            tabLabel: 'Live Tracking',
            title: 'Realtime Bronintegraties',
            description: 'Verbind OwnTracks, Overland, Traccar, GPSLogger, Dawarich, Home Assistant en Colota in één tijdlijn.',
            highlights: [
                'Ondersteunt HTTP en MQTT-inname over ondersteunde trackers',
                'Per-bron GPS-filtering verbetert tijdlijnkwaliteit',
                'Meerdere apparaten/bronnen combineren in één geschiedenis'
            ]
        },
        imports: {
            tabLabel: 'Imports',
            title: 'Import & Migratie Geschiedenis',
            description: 'Importeer legacy en back-updata met achtergrondverwerking en tijdlijnregeneratie.',
            highlights: [
                'Ondersteunt GeoPulse, OwnTracks, Google Timeline, GPX, GeoJSON en CSV',
                'Datumrange-filtering voor gedeeltelijke imports',
                'Vervangen-of-samenvoegen workflow voor veilige reimports'
            ]
        },
        timeline: {
            tabLabel: 'Tijdlijn',
            title: 'Verblijfplaatsen, Reizen en Gaten',
            description: 'GeoPulse zet ruwe punten om in tijdlijngebeurtenissen, en laat u vervolgens routes verfijnen en gemiste stops corrigeren.',
            highlights: [
                'Automatische verblijfplaats/reis/gat detectie',
                'Configureerbare reisvormen van lopen en fietsen tot motorfiets, trein, vlieg en boot',
                'Een gemiste stop splitsen in Reis → Verblijfplaats → Reis; de correctie overleeft regeneratie',
                'Optionele Valhalla Map Matching volgt wegen terwijl ruwe GPS autoritair blijft'
            ]
        },
        insight: {
            tabLabel: 'Inzichten',
            title: 'Dashboard & Reisinzichten && Locatie-analyse ',
            description: 'Bewegingspatronen, plaatsen, routes en prestaties volgen over meerdere tijdvensters.',
            highlights: [
                'Geselecteerde periode + 7-dagen + 30-dagen overzichten',
                'Top plaatsen, routeanalyse en activiteitsonderdelen',
                'Reisinzichten voor landen/steden/reis mijlpalen'
            ]
        },
        friends: {
            tabLabel: 'Vrienden',
            title: 'Vrienden & Delingselementen',
            description: 'Maak verbinding met vrienden en controleer precies wat u deelt.',
            highlights: [
                'Vriendschapsverzoeken uitnodigen, accepteren, weigeren en annuleren',
                'Afzonderlijke machtigingen voor live locatie en tijdlijngeschiedenis',
                'Live kaart en gedeelde tijdlijnweergaven, inclusief inbedbare gedeelde locaties'
            ]
        },
        geofences: {
            tabLabel: 'Geofences',
            title: 'Geofenceregels & Gebeurtenissen',
            description: 'Binnenkom/verlaat-regels maken met in-app meldingen en optionele externe bezorging.',
            highlights: [
                'Geselecteerde onderwerpen volgen met configureerbare regelvoorwaarden',
                'Sjabloongebaseerde meldingen met macro-ondersteuning',
                'Gebeurtenissentab met ongelezen filteren en gelezen beheer'
            ]
        },
        immich: {
            tabLabel: 'Immich',
            title: 'Immich Foto-overlay',
            description: 'Immich-foto\'s direct op de tijdlijnkaart weergeven voor het geselecteerde datumbereik.',
            highlights: [
                'URL + API-sleutel configureren in Profiel',
                'Fotolayer op tijdlijnkaart inschakelen/uitschakelen',
                'Foto\'s in-app openen en originele bestanden downloaden'
            ]
        },
        weather: {
            tabLabel: 'Weer',
            title: 'Weer Langs Uw Tijdlijn',
            description: 'Lokale omstandigheden toevoegen aan de plaatsen en reizen in uw locatiegeschiedenis.',
            highlights: [
                'Temperatuur, neerslag, wind en omstandigheden zien op verblijfplaatsen, reizen en tijdlijnkaarten',
                'Weerpatronen vergelijken in Reisinzichten en weergerelateerde badges verdienen',
                'Doorgaande verzameling gebruikt standaard Open-Meteo; beheerders kunnen kiezen voor historische backfill'
            ]
        },
        ai: {
            tabLabel: 'AI',
            title: 'AI-assistent & MCP',
            description: 'Stel natuurlijke taalvragen of verbind een AI-client met uw persoonlijke bewegingsgeschiedenis.',
            highlights: [
                'Breng uw eigen OpenAI-compatibele API-sleutel/model met versleutelde per-gebruiker instellingen',
                'Verblijfplaatsen, reizen, plaatsen en reispatronen opvragen',
                'MCP is standaard ingeschakeld voor API-token-geauthenticeerde, alleen-lezen AI-tools'
            ]
        }
    },
    onboardingTour: {
        welcomeTitle: '🎉 Welkom bij GeoPulse!',
        welcomeDescription: 'Klaar om met het volgen van uw locatiereis te beginnen? Laten we uw eerste locatiebron instellen.',
        startTour: '🚀 Tour starten',
        exploreOnMyOwn: "Ik verken zelf",
        closeTour: 'Tour sluiten',
        stepCounter: '{current} van {total}',
        previous: 'Vorige',
        next: 'Volgende',
        getStarted: 'Begin!',
        steps: {
            welcome: {
                title: 'Welkom bij GeoPulse!',
                description: "Eerst stellen we uw locatiebronnen in zodat we uw reis kunnen gaan volgen."
            },
            addSource: {
                title: 'Voeg uw eerste locatiebron toe',
                description: 'Klik op de knop "Nieuwe bron toevoegen" om OwnTracks of Overland toe te voegen als uw locatiebron.'
            },
            followInstructions: {
                title: 'Volg installatie-instructies',
                description: "Zodra u een bron toevoegt, ziet u gedetailleerde installatie-instructies met uw unieke endpoint en token."
            },
            allSet: {
                title: "U bent helemaal klaar!",
                description: 'Zodra uw locatiebron is geconfigureerd, bezoek uw Tijdlijn om uw locatiegeschiedenis te zien en GeoPulse te verkennen!'
            }
        }
    },
    appNavbar: {
        demoBadge: 'DEMO',
        locationSharingEnabledTooltip: 'Locatiedeling ingeschakeld',
        locationSharingDisabledTooltip: 'Locatiedeling uitgeschakeld',
        shareLocation: 'Locatie delen',
        inviteFriend: 'Vriend uitnodigen',
        inviteFriendDisabledAriaLabel: 'Vriend uitnodigen uitgeschakeld in demo-modus',
        inviteFriendDisabledTooltip: 'Uitnodigingen zijn uitgeschakeld in demo-modus'
    },
    globalSearch: {
        bar: {
            placeholder: 'Zoeken naar plaatsen, pagina\'s, instellingen...',
            groupPages: 'Pagina\'s',
            groupSettings: 'Instellingen',
            groupLocations: 'Locaties',
            visitsCount: '{count} bezoek | {count} bezoeken',
            sinceDate: 'Sinds {date}'
        },
        settingsTrigger: {
            defaultPlaceholder: 'Zoeken in instellingen...',
            defaultButtonLabel: 'Instelling vinden'
        },
        registry: {
            tabAuthentication: 'Authenticatie',
            tabGeocoding: 'Geocodering',
            tabWeather: 'Weer',
            tabPlaceDiscovery: 'Plaatsontdekking',
            tabMapMatching: 'Map Matching',
            tabAiAssistant: 'AI-assistent',
            tabImport: 'Importeren',
            tabExport: 'Exporteren',
            tabBackup: 'Back-up & Herstellen',
            tabNotifications: 'Meldingen',
            tabSystem: 'Systeem',
            timelineTabStayPoints: 'Verblijfplaatsdetectie',
            timelineTabTrips: 'Reisclassificatie',
            timelineTabGpsGaps: 'Datagapdetectie',
            timelineTabMerging: 'Verblijfplaats samenvoeging',
            subtitleTimelinePreferences: 'Tijdlijnvoorkeuren / {tab}',
            subtitleProfile: 'Profiel / {subtitle}',
            subtitleSystemSettings: 'Systeeminstellingen / {tab}',
            subtitlePage: 'Pagina',
            subtitleAdministration: 'Beheer'
        }
    },
    charts: {
        barChart: {
            defaultTitle: 'Data',
            seriesFallback: 'Serie {number}',
            valueKm: '{value} km'
        }
    }
}


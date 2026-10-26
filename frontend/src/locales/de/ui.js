/**
 * Shared UI components, search, dashboard, and home content (German).
 */
export default {
    mainApp: {
        shareLabel: 'Teilen',
        shareAria: 'Zeitleiste teilen',
        reconstructTooltip: 'GPS-Punkte aus manuell angelegten Aufenthalten und Fahrten erzeugen. Vorhandene GPS-Punkte bleiben erhalten.',
        reconstructLabel: 'Fehlende Zeitleistendaten ergänzen',
        reconstructAria: 'Fehlende Zeitleistendaten ergänzen',
        timelineReportsTab: 'Zeitleisten-Berichte'
    },
    dashboard: {
        page: {
            title: 'Übersicht',
            subtitle: 'Überblick über deine Standortdaten und Auswertungen',
            selectedPeriodSummary: 'Zusammenfassung des Zeitraums',
            sevenDaysOverview: 'Letzte 7 Tage',
            thirtyDaysOverview: 'Letzte 30 Tage',
            topPlacesSelected: 'Top-Orte im gewählten Zeitraum',
            topPlacesLast7: 'Top-Orte der letzten 7 Tage',
            topPlacesLast30: 'Top-Orte der letzten 30 Tage',
            routeStatsSelected: 'Routenstatistik im gewählten Zeitraum',
            routeStatsLast7: 'Routenstatistik der letzten 7 Tage',
            routeStatsLast30: 'Routenstatistik der letzten 30 Tage',
            noDataTitle: 'Keine Daten vorhanden',
            noDataMessage: 'Für die gewählten Zeiträume wurden keine Standortdaten gefunden. Prüf deine GPS-Quellen oder wähle einen anderen Zeitraum.',
            toasts: {
                selectedRangeFailed: 'Statistik für den gewählten Zeitraum konnte nicht abgerufen werden',
                weeklyFailed: 'Wochenstatistik konnte nicht abgerufen werden',
                monthlyFailed: 'Monatsstatistik konnte nicht abgerufen werden'
            }
        },
        metrics: {
            totalDistance: 'Gesamtstrecke',
            timeMoving: 'Zeit in Bewegung',
            dailyAverage: 'Tagesdurchschnitt',
            averageSpeed: 'Durchschnittsgeschwindigkeit',
            mostActiveDay: 'Aktivster Tag',
            uniqueLocations: 'Verschiedene Orte',
            notAvailable: 'k. A.'
        },
        distanceActivityChart: 'Strecke pro Tag',
        distanceAxisTitle: 'Strecke ({unit})',
        tooltip: {
            distance: 'Strecke: {value}',
            travelTime: 'Unterwegs: {value}',
            locations: 'Orte: {value}'
        },
        routeAnalysis: {
            noDataTitle: 'Keine Routendaten',
            noDataMessage: 'Für diesen Zeitraum wurden keine Routen ausgewertet.',
            uniqueRoutes: 'Verschiedene Routen',
            mostCommonRoute: 'Häufigste Route',
            tripsCount: '{count} Fahrten',
            avgTripDuration: 'Ø Dauer einer Fahrt',
            longestTripDuration: 'Längste Fahrt (Dauer)',
            longestTripDistance: 'Längste Fahrt (Strecke)',
            notAvailable: 'k. A.'
        },
        topPlaces: {
            noDataTitle: 'Keine Ortsdaten',
            noDataMessage: 'In diesem Zeitraum wurden keine Orte besucht.',
            visitsCount: '{count} Besuche',
            totalDuration: '{duration} insgesamt',
            defaultLocationName: 'Ausgewählter Ort'
        }
    },
    home: {
        loadContentFailed: 'Inhalte der Startseite konnten nicht geladen werden',
        nav: {
            resourcesAriaLabel: 'Projektressourcen',
            documentation: 'Dokumentation',
            github: 'GitHub',
            whatsNewAriaLabel: 'Was ist neu',
            updateAvailable: 'Neu: {version} verfügbar',
            updateAvailableShort: 'Neu: {version}',
            whatsNewTitle: 'Neu in {version}',
            whatsNewEmpty: 'Für diese Version gibt es noch keine Versionshinweise.',
            releaseNotesLink: 'Vollständige Versionshinweise auf GitHub'
        },
        hero: {
            eyebrow: 'Selbst gehostete Alternative zu Google Timeline',
            titleWelcomeBack: 'Willkommen zurück',
            titleDefault: 'Deine Standort-Zeitleiste gehört dir',
            subtitle: 'GeoPulse macht aus GPS-Rohdaten eine private Zeitleiste mit Aufenthalten, Fahrten, Karten und Einblicken – alles unter deiner Kontrolle.',
            accessDisabled: 'Anmeldung und Registrierung sind derzeit von der Administration deaktiviert.',
            registrationDisabled: 'Die Registrierung ist deaktiviert. Bestehende Konten können sich weiterhin anmelden.',
            tryDemo: 'Demo ausprobieren',
            startJourney: 'Jetzt loslegen',
            goToTimeline: 'Zur Zeitleiste',
            loadingWorkspace: 'Arbeitsbereich wird geladen …',
            socialProofText: 'Quellcode auf GitHub · Kostenlos für den privaten Gebrauch',
            stars: 'Sterne',
            forks: 'Forks'
        },
        features: {
            liveTracking: 'Live-Tracking',
            smartImport: 'Smarter Import',
            autoTimeline: 'Automatische Zeitleiste',
            deepInsights: 'Tiefe Einblicke',
            immichIntegration: 'Immich-Anbindung',
            friends: 'Freunde',
            geofences: 'Geofences',
            ai: 'KI'
        },
        panel: {
            exploreAriaLabel: 'GeoPulse und Tipps entdecken',
            tabsAriaLabel: 'Bereiche (mobil)',
            featuresTab: 'Funktionen',
            tipTab: 'Tipp',
            tipOfDayTitle: 'Tipp des Tages',
            tipOfDayAriaLabel: 'Tipp des Tages'
        },
        footer: {
            copyright: '© GeoPulse. Alle Rechte vorbehalten.'
        }
    },
    mobileAuth: {
        title: 'Mobil',
        preparing: 'Mobile Anmeldung wird vorbereitet …',
        payloadMissing: 'Die Daten für die mobile Anmeldung wurden nicht zurückgegeben.',
        opening: 'App wird geöffnet …',
        timedOut: 'Das Öffnen der App hat zu lange gedauert. Wechsle zurück in die App und versuch es noch einmal.',
        failed: 'Die Übergabe der mobilen Anmeldung ist fehlgeschlagen.'
    },
    notFound: {
        subtitle: 'Ort nicht gefunden',
        title: 'Hoppla! Du bist vom Weg abgekommen',
        description: 'Die gesuchte Seite gibt es nicht oder sie wurde verschoben. Wir bringen dich zurück auf Kurs.',
        optionsTitle: 'Wohin möchtest du?',
        options: {
            home: {
                title: 'Startseite',
                description: 'Ganz von vorn beginnen'
            },
            timeline: {
                title: 'Zeitleiste',
                description: 'Deine Standort-Zeitleiste ansehen'
            },
            signIn: {
                title: 'Anmelden',
                description: 'Zu deinem GeoPulse-Konto'
            },
            dashboard: {
                title: 'Übersicht',
                description: 'Einblicke in deine Standortdaten'
            },
            signUp: {
                title: 'Registrieren',
                description: 'Ein neues Konto erstellen'
            }
        },
        goBack: 'Zurück',
        helpSummary: 'Brauchst du Hilfe beim Finden?',
        helpIntro: 'Diese Seiten suchst du vielleicht:',
        helpLinks: {
            timeline: 'Zeitleiste',
            timelineDesc: 'Deinen Standortverlauf ansehen',
            dashboard: 'Übersicht',
            dashboardDesc: 'Einblicke und Auswertungen zu deinen Standorten',
            journeyInsights: 'Reise-Einblicke',
            journeyInsightsDesc: 'Reisemuster entdecken',
            friends: 'Freunde',
            friendsDesc: 'Deine Freunde verwalten',
            signIn: 'Anmelden',
            signInDesc: 'Zu deinem Konto'
        },
        helpNote: 'Wenn du glaubst, dass der Link kaputt ist, melde dich beim Support, dann beheben wir das.'
    },
    darkModeSwitcher: {
        themeTooltip: 'Design: {label}',
        themeAriaLabel: 'Design-Modus: {label}',
        light: 'Hell',
        dark: 'Dunkel',
        system: 'System',
        currentSuffix: '{label} (aktuell)'
    },
    errorReferenceToast: {
        copyAriaLabel: 'Fehler-Referenz-ID kopieren'
    },
    providerIcon: {
        defaultAlt: 'Anbieter-Symbol'
    },
    gpsFiltering: {
        title: 'Filterung der GPS-Daten',
        filterLabel: 'Ungenaue Punkte herausfiltern',
        filterHint: 'Aktivieren, um GPS-Punkte mit Genauigkeit oder Geschwindigkeit außerhalb der Grenzen zu verwerfen.',
        maxAccuracyLabel: 'Maximal erlaubte Ungenauigkeit (Meter)',
        maxAccuracyPlaceholder: 'z. B. 100',
        maxAccuracyHint: 'Punkte mit einem höheren Genauigkeitswert werden verworfen.',
        maxSpeedLabel: 'Maximal erlaubte Geschwindigkeit (km/h)',
        maxSpeedPlaceholder: 'z. B. 250',
        maxSpeedHint: 'Punkte mit höherer Geschwindigkeit werden verworfen.',
        duplicateDetectionTitle: 'Duplikaterkennung',
        duplicateDetectionLabel: 'Duplikaterkennung aktivieren',
        duplicateDetectionHint: 'GPS-Punkte am selben Ort innerhalb eines Zeitfensters überspringen. Nützlich für Geräte, die im Stillstand wiederholt denselben Standort senden.',
        thresholdLabel: 'Zeitfenster (Minuten)',
        thresholdPlaceholder: 'z. B. 2',
        thresholdHint: 'Punkte mit denselben Koordinaten (innerhalb von ca. 11 m) in diesem Zeitfenster werden übersprungen. Leer lassen für den globalen Standard.'
    },
    pullToRefresh: {
        pullText: 'Zum Aktualisieren ziehen',
        readyText: 'Zum Aktualisieren loslassen',
        refreshingText: 'Wird aktualisiert …'
    },
    tipOfDayCard: {
        nextTip: 'Nächster Tipp',
        noTipTitle: 'Noch kein Tipp verfügbar',
        noTipDescription: 'Tipps erscheinen hier, sobald Inhalte für die Startseite verfügbar sind.'
    },
    dateRangePicker: {
        labelDefault: 'Datum wählen',
        placeholderDefault: 'Zeitraum wählen',
        presetPlaceholderDefault: 'Vorlage wählen',
        presets: {
            today: 'Heute',
            yesterday: 'Gestern',
            last7Days: 'Letzte 7 Tage',
            last30Days: 'Letzte 30 Tage'
        },
        maxRangeError: 'Der Zeitraum darf höchstens {days} Tage lang sein',
        periodFallbackName: 'Zeitraum',
        labelFallbackName: 'Label',
        timelineLabelsGroup: 'Zeitleisten-Labels',
        presetsGroup: 'Vorlagen'
    },
    transportTypeCard: {
        enableAriaLabel: '{title}: Erkennung aktivieren',
        alwaysActive: 'Immer aktiv',
        expandAriaLabel: '{title} aufklappen',
        collapseAriaLabel: '{title} einklappen',
        disabledMessage: '{title}: Die Erkennung ist derzeit deaktiviert. Aktiviere sie, um die Schwellen einzustellen.'
    },
    appLayout: {
        viewTimelineJob: 'Zeitleisten-Job ansehen',
        whatsNew: 'Was ist neu',
        readFullReleaseNotes: 'Vollständige Versionshinweise lesen',
        gotIt: 'Verstanden',
        notificationDefaultSummary: 'Benachrichtigung',
        viewAllNotifications: 'Alle Benachrichtigungen ansehen',
        openNotification: 'Benachrichtigung öffnen'
    },
    exploreFeatures: {
        ariaLabel: 'GeoPulse entdecken',
        kicker: 'GeoPulse entdecken',
        tabsAriaLabel: 'Funktionen von GeoPulse',
        readDocs: 'Dokumentation lesen',
        openInApp: 'In der App öffnen',
        liveTracking: {
            tabLabel: 'Live-Tracking',
            title: 'Quellen in Echtzeit',
            description: 'Führe OwnTracks, Overland, Traccar, GPSLogger, Dawarich, Home Assistant und Colota in einer Zeitleiste zusammen.',
            highlights: [
                'Empfang per HTTP und MQTT für die unterstützten Tracker',
                'GPS-Filter pro Quelle verbessern die Qualität der Zeitleiste',
                'Mehrere Geräte und Quellen in einem Verlauf kombinieren'
            ]
        },
        imports: {
            tabLabel: 'Importe',
            title: 'Verlauf importieren & umziehen',
            description: 'Importiere alte Daten und Backups mit Verarbeitung im Hintergrund und neu berechneter Zeitleiste.',
            highlights: [
                'Unterstützt GeoPulse, OwnTracks, Google Timeline, GPX, GeoJSON und CSV',
                'Filter nach Zeitraum für Teilimporte',
                'Ersetzen oder Zusammenführen für sichere erneute Importe'
            ]
        },
        timeline: {
            tabLabel: 'Zeitleiste',
            title: 'Aufenthalte, Fahrten und Lücken',
            description: 'GeoPulse macht aus Rohpunkten Ereignisse in der Zeitleiste. Routen kannst du verfeinern und verpasste Halte korrigieren.',
            highlights: [
                'Automatische Erkennung von Aufenthalten, Fahrten und Lücken',
                'Einstellbare Fortbewegungsarten von zu Fuß und Fahrrad bis Motorrad, Zug, Flug und Boot',
                'Einen verpassten Halt in Fahrt → Aufenthalt → Fahrt teilen; die Korrektur bleibt bei Neuberechnungen erhalten',
                'Optionales Map-Matching mit Valhalla folgt Straßen, die GPS-Rohdaten bleiben maßgeblich'
            ]
        },
        insight: {
            tabLabel: 'Einblicke',
            title: 'Übersicht, Reise-Einblicke & Ortsanalyse',
            description: 'Verfolge Bewegungsmuster, Orte, Routen und Erfolge über verschiedene Zeiträume.',
            highlights: [
                'Überblick über gewählten Zeitraum, 7 Tage und 30 Tage',
                'Top-Orte, Routenanalyse und Aufschlüsselung der Aktivitäten',
                'Reise-Einblicke zu Ländern, Städten und Meilensteinen'
            ]
        },
        friends: {
            tabLabel: 'Freunde',
            title: 'Freunde & Freigaben',
            description: 'Verbinde dich mit Freunden und lege genau fest, was du teilst.',
            highlights: [
                'Freundschaftsanfragen senden, annehmen, ablehnen und zurückziehen',
                'Getrennte Freigaben für Live-Standort und Zeitleistenverlauf',
                'Live-Karte und geteilte Zeitleisten, auch als einbettbare geteilte Standorte'
            ]
        },
        geofences: {
            tabLabel: 'Geofences',
            title: 'Geofence-Regeln & Ereignisse',
            description: 'Erstelle Regeln für Betreten und Verlassen mit Benachrichtigungen in der App und optionaler externer Zustellung.',
            highlights: [
                'Ausgewählte Personen mit einstellbaren Bedingungen verfolgen',
                'Benachrichtigungen aus Vorlagen mit Makros',
                'Ereignis-Tab mit Filter für Ungelesenes und Gelesen-Verwaltung'
            ]
        },
        immich: {
            tabLabel: 'Immich',
            title: 'Immich-Fotos auf der Karte',
            description: 'Zeige Immich-Fotos für den gewählten Zeitraum direkt auf der Zeitleistenkarte.',
            highlights: [
                'URL und API-Schlüssel im Profil einrichten',
                'Fotoebene auf der Zeitleistenkarte ein- und ausschalten',
                'Fotos in der App öffnen und Originale herunterladen'
            ]
        },
        weather: {
            tabLabel: 'Wetter',
            title: 'Wetter entlang deiner Zeitleiste',
            description: 'Ergänze die Orte und Reisen in deinem Standortverlauf um das Wetter vor Ort.',
            highlights: [
                'Temperatur, Niederschlag, Wind und Wetterlage bei Aufenthalten, Fahrten und auf der Zeitleistenkarte',
                'Wettermuster in den Reise-Einblicken vergleichen und Wetter-Abzeichen sammeln',
                'Die laufende Erfassung nutzt standardmäßig Open-Meteo; Admins können Verlaufsdaten nachladen'
            ]
        },
        ai: {
            tabLabel: 'KI',
            title: 'KI-Assistent & MCP',
            description: 'Stell Fragen in natürlicher Sprache oder verbinde einen KI-Client mit deinem Bewegungsverlauf.',
            highlights: [
                'Eigener OpenAI-kompatibler API-Schlüssel und eigenes Modell, verschlüsselt pro Nutzer gespeichert',
                'Aufenthalte, Fahrten, Orte und Reisemuster abfragen',
                'MCP ist standardmäßig aktiv – für KI-Werkzeuge mit Lesezugriff über API-Tokens'
            ]
        }
    },
    onboardingTour: {
        welcomeTitle: '🎉 Willkommen bei GeoPulse!',
        welcomeDescription: 'Bereit, deine Wege aufzuzeichnen? Richten wir deine erste Standortquelle ein.',
        startTour: '🚀 Tour starten',
        exploreOnMyOwn: 'Ich schau mich selbst um',
        closeTour: 'Tour schließen',
        stepCounter: '{current} von {total}',
        previous: 'Zurück',
        next: 'Weiter',
        getStarted: 'Los geht’s!',
        steps: {
            welcome: {
                title: 'Willkommen bei GeoPulse!',
                description: 'Richte zuerst deine Standortquellen ein, damit die Aufzeichnung starten kann.'
            },
            addSource: {
                title: 'Erste Standortquelle hinzufügen',
                description: 'Klicke auf „Neue Quelle hinzufügen“, um OwnTracks oder Overland als Standortquelle einzurichten.'
            },
            followInstructions: {
                title: 'Anleitung folgen',
                description: 'Nach dem Hinzufügen siehst du eine ausführliche Anleitung mit deinem persönlichen Endpunkt und Token.'
            },
            allSet: {
                title: 'Alles bereit!',
                description: 'Sobald deine Standortquelle eingerichtet ist, schau in deine Zeitleiste und entdecke GeoPulse!'
            }
        }
    },
    appNavbar: {
        demoBadge: 'DEMO',
        locationSharingEnabledTooltip: 'Standortfreigabe aktiv',
        locationSharingDisabledTooltip: 'Standortfreigabe aus',
        shareLocation: 'Standort teilen',
        inviteFriend: 'Freund einladen',
        inviteFriendDisabledAriaLabel: 'Freund einladen ist im Demo-Modus deaktiviert',
        inviteFriendDisabledTooltip: 'Einladungen sind im Demo-Modus deaktiviert'
    },
    globalSearch: {
        bar: {
            placeholder: 'Orte, Seiten, Einstellungen suchen …',
            groupPages: 'Seiten',
            groupSettings: 'Einstellungen',
            groupLocations: 'Orte',
            visitsCount: '{count} Besuch | {count} Besuche',
            sinceDate: 'Seit {date}'
        },
        settingsTrigger: {
            defaultPlaceholder: 'Einstellungen suchen …',
            defaultButtonLabel: 'Einstellung finden'
        },
        registry: {
            tabAuthentication: 'Anmeldung',
            tabGeocoding: 'Geocoding',
            tabWeather: 'Wetter',
            tabPlaceDiscovery: 'Ortssuche',
            tabMapMatching: 'Map-Matching',
            tabAiAssistant: 'KI-Assistent',
            tabImport: 'Import',
            tabExport: 'Export',
            tabBackup: 'Backup & Wiederherstellung',
            tabNotifications: 'Benachrichtigungen',
            tabSystem: 'System',
            timelineTabStayPoints: 'Erkennung von Aufenthalten',
            timelineTabTrips: 'Einstufung von Fahrten',
            timelineTabGpsGaps: 'Erkennung von GPS-Lücken',
            timelineTabMerging: 'Zusammenführen von Aufenthalten',
            subtitleTimelinePreferences: 'Zeitleisten-Einstellungen / {tab}',
            subtitleProfile: 'Profil / {subtitle}',
            subtitleSystemSettings: 'Systemeinstellungen / {tab}',
            subtitlePage: 'Seite',
            subtitleAdministration: 'Administration'
        }
    },
    charts: {
        barChart: {
            defaultTitle: 'Daten',
            seriesFallback: 'Reihe {number}',
            valueKm: '{value} km'
        }
    }
}

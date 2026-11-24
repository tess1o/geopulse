export default {
    // Live progress messages sent by the backend as a MessageDescriptor ({key, parameters, fallback}) via
    // `formatMessageDescriptor()`. Keys here are the `timelineJobs.progressMessages.*` suffixes the
    // backend appends (see TimelineJobProgressService.step()). Not every backend-recognized key has an
    // entry -- an unmapped key safely falls back to the backend\'s English `fallback` text.
    progressMessages: {
        initializing: 'Tijdlijngeneratie initialiseren',
        acquiringLock: 'Tijdlijnvergrendeling verkrijgen',
        cleaningUp: 'Oude tijdlijngegevens opschonen',
        preparingGpsProcessing: 'GPS-gegevensverwerking voorbereiden',
        noGpsData: 'Geen GPS-gegevens om te verwerken',
        readyToProcess: 'Klaar om {totalGpsPoints} GPS-punten te verwerken',
        processingStateMachine: 'GPS-punten verwerken via state machine',
        postProcessingTrips: 'Reizen nabewerken en detecties valideren',
        mergingTimeline: 'Tijdlijn samenvoegen',
        persistingTimeline: 'Tijdlijngebeurtenissen naar database schrijven',
        detectingDataGaps: 'Datagaps detecteren',
        finalizing: 'Tijdlijngeneratie afronden',
        autoMatchingVisits: 'Geplande bezoeken automatisch matchen',
        recalculatingBadges: 'Prestatiebadges opnieuw berekenen',
        completed: 'Tijdlijngeneratie voltooid',
        preparingBoatSetup: 'Boot-installatie voorbereiden',
        boatEvidenceReady: 'Boot waterbewijs gereed',
        regenerationQueued: 'Geforceerde tijdlijnregeneratie in de wachtrij geplaatst',
        startingLocationResolution: 'Locatie-resolutie starten',
        allResolvedFromFavorites: 'Alle locaties opgelost vanuit favorieten',
        resolvedFromFavorites: '{favoritesResolved} locaties opgelost vanuit favorieten',
        resolvedFromCache: '{cachedResolved} opgelost vanuit cache ({batchResolved} vanuit batch, {individualResolved} vanuit individuele opzoeking)',
        geocodingLocationProgress: 'Locatie geocoderen {current}/{total}',
        geocodingComplete: 'Geocodering voltooid: {favoritesResolved} favorieten, {cachedResolved} gecachet, {externalCompleted} externe API-oproepen',
        applyingCorrections: 'Correcties toepassen'
    },
    steps: {
        queued: 'Wachten op start',
        preparing: 'Voorbereiden',
        processingPoints: 'GPS-punten verwerken',
        correctionWindow: 'Gecorrigeerde periode opnieuw opbouwen',
        geocoding: 'Verblijven benoemen',
        postProcessingTrips: 'Ritten nabewerken',
        merging: 'Verblijven samenvoegen',
        corrections: 'Correcties toepassen',
        persisting: 'Tijdlijn opslaan',
        finalizing: 'Afronden',
        badges: 'Badges herberekenen'
    },
    types: {
        FULL_REBUILD: 'Volledige hergeneratie',
        PARTIAL_REBUILD: 'Gedeeltelijke hergeneratie',
        CORRECTION: 'Correctie',
        IMPORT: 'Import',
        CAMPAIGN: 'Hergeneratiecampagne',
        OTHER: 'Tijdlijntaak'
    },
    listPage: {
        title: 'Tijdlijngeneratietaken',
        description: 'Bekijk tijdlijngeneratiewerk dat nu wordt uitgevoerd of recent is voltooid.',
        loading: 'Zoeken naar actieve taken...',
        noJob: {
            title: 'Er zijn momenteel geen tijdlijntaken actief',
            message: 'Als u deze vanuit een melding hebt geopend, kan de geplande vernieuwing al voltooid zijn. Recent voltooide of mislukte taken worden hieronder getoond indien beschikbaar.',
            hint: 'Tijdlijntaken kunnen worden gestart door tijdlijnvoorkeurwijzigingen, favorietupdates, handmatige regeneratie of geplande GeoPulse-onderhoud.',
            viewTimelineButton: 'Tijdlijn bekijken'
        },
        error: {
            title: 'Controleren op actieve taken is mislukt'
        },
        history: {
            title: 'Taakgeschiedenis',
            description: 'Recente tijdlijngeneratietaken',
            loading: 'Taakgeschiedenis laden...',
            empty: 'Geen recente tijdlijntaken gevonden.',
            duration: 'Duur: {duration}',
            gpsPoints: '{count} GPS-punten',
            viewDetails: 'Details bekijken'
        },
        notAvailable: 'Nvt',
        durationSeconds: '{seconds}s',
        durationMinutesSeconds: '{minutes}m {seconds}s'
    },
    detailsPage: {
        title: 'Tijdlijngeneratievoortgang',
        description: 'Realtime voortgangstracking voor tijdlijnregeneratietaak.',
        backButton: 'Terug naar Tijdlijn',
        loading: 'Taakdetails laden...',
        error: {
            title: 'Taakdetails laden is mislukt'
        },
        jobIdLabel: 'Taak-ID: {jobId}',
        status: {
            loading: 'Laden...',
            queued: 'In de wachtrij geplaatst voor verwerking',
            running: 'Tijdlijn verwerken',
            completed: 'Succesvol voltooid',
            failed: 'Mislukt'
        },
        details: {
            started: 'Gestart:',
            completed: 'Voltooid:',
            duration: 'Duur:',
            type: 'Soort:',
            gpsPoints: 'GPS-punten:',
            produced: 'Gemaakt:',
            producedValue: '{stays} verblijven, {trips} ritten, {gaps} datagaten',
            corrections: 'Correcties:',
            correctionsValue: '{applied} toegepast, {notApplied} niet toegepast',
            memoryLimit: 'Geheugenlimiet:',
            noMemoryLimit: 'Geen (heap tot {heap})',
            cpus: 'CPU\'s:'
        },
        stepsTitle: 'Verwerkingsstappen',
        stepBadges: {
            slowest: 'Traagst',
            memoryPeak: 'Geheugenpiek',
            failed: 'Hier mislukt',
            pending: 'Nog te doen'
        },
        stepMemory: {
            label: 'Hoogste geheugen:',
            heap: 'heap {value}',
            process: 'proces {value}',
            container: 'container {value}'
        },
        stepShare: '{percent}% van de taak',
        stepShareTiny: '<1% van de taak',
        durationUnderSeconds: '<{seconds}s',
        detail: {
            gpsPoints: 'GPS-punten: {loaded} / {total}',
            processingHeader: 'GPS-punten verwerken: {processed} / {total}',
            remaining: 'Over: {count} punten',
            geocodingHeader: 'Omgekeerde geocodering: {resolved} / {total} locaties',
            favoritesInstant: 'Favorieten (direct): {count}',
            cachedInDatabase: 'Gecachet in database: {count}',
            externalApiCalls: 'Externe API-oproepen: {count}',
            pending: 'In afwachting: {count}'
        },
        diagnostics: {
            button: 'Diagnose kopiëren',
            title: 'Taakdiagnose',
            description: 'Stappen, tijden, geheugen, versie en instellingen van deze taak, voor een supportvraag of een issue. Bevat geen coördinaten, plaatsnamen, notities of tijden van gebeurtenissen.',
            copy: 'Kopiëren',
            copied: 'Diagnose naar het klembord gekopieerd',
            copyFailed: 'Kopiëren lukte niet. Selecteer de tekst en kopieer die zelf.',
            loadFailed: 'De diagnose kon niet worden geladen'
        },
        completion: {
            title: 'Tijdlijngeneratie voltooid!',
            message: 'Uw tijdlijn is succesvol geregenereerd met alle nieuwste GPS-gegevens.'
        },
        durationZero: '0s',
        durationSeconds: '{seconds}s',
        durationMinutesSeconds: '{minutes}m {seconds}s',
        durationHoursMinutesSeconds: '{hours}u {minutes}m {seconds}s'
    },
    regeneration: {
        alreadyRunning: {
            header: 'Tijdlijntaak bezig',
            message: 'Een tijdlijngeneratietaak is al bezig ({progress}% voltooid). Wacht tot deze is afgerond.',
            viewProgress: 'Voortgang bekijken'
        },
        savedSummary: 'Opgeslagen',
        queuedDetail: 'Wijzigingen opgeslagen. Uw tijdlijn wordt op de achtergrond bijgewerkt.'
    }
}


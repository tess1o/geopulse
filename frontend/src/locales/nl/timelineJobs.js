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
        geocodingComplete: 'Geocodering voltooid: {favoritesResolved} favorieten, {cachedResolved} gecachet, {externalCompleted} externe API-oproepen'
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
            currentStep: 'Huidige stap:',
            stepOf: '{current} van {total}'
        },
        stepsTitle: 'Verwerkingsstappen',
        steps: {
            acquiringLock: {
                title: 'Vergrendeling verkrijgen',
                description: 'Exclusieve toegang tot tijdlijngegevens garanderen'
            },
            cleaningUp: {
                title: 'Opruimen',
                description: 'Oude tijdlijngebeurtenissen verwijderen'
            },
            preparingGpsProcessing: {
                title: 'GPS-verwerking voorbereiden',
                description: 'GPS-punten tellen en streaming iterator voorbereiden'
            },
            processingGeocoding: {
                title: 'Verwerken & Geocoderen',
                description: 'GPS-punten streamen en verwerken via state machine, vervolgens locatienamen oplossen'
            },
            postProcessingTrips: {
                title: 'Reizen nabewerken',
                description: 'Reisdetectie valideren en verfijnen'
            },
            mergingSimplifying: {
                title: 'Samenvoegen & Vereenvoudigen',
                description: 'Tijdlijnoptimalisaties toepassen'
            },
            persistingTimeline: {
                title: 'Tijdlijn opslaan',
                description: 'Tijdlijngebeurtenissen naar database opslaan'
            },
            dataGapDetection: {
                title: 'Datagapdetectie',
                description: 'Gaten in GPS-dekking identificeren'
            },
            finalizing: {
                title: 'Afronden',
                description: 'Mijlpalen berekenen en generatie voltooien'
            }
        },
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


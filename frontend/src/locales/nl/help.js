export default {
    page: {
        title: 'Help & Ondersteuning',
        description: 'Krijg hulp, toegang tot documentatie en meld problemen',
        faq: {
            title: 'Veelgestelde vragen',
            description: 'Korte antwoorden op de meest gestelde vragen. Elke link opent het antwoord in de documentatie.',
            browseAll: 'Alle veelgestelde vragen',
            adminTitle: 'Voor beheerders',
            questions: {
                timelineNotGenerating: 'Waarom wordt mijn tijdlijn niet gegenereerd?',
                timelineGaps: 'Waarom zitten er gaten in mijn tijdlijn?',
                tripMisclassified: 'Waarom wordt mijn autorit als wandeling herkend (of andersom)?',
                jobsFailing: 'Tijdlijntaken blijven mislukken. Wat moet ik doen?',
                importGoogle: 'Kan ik mijn Google Tijdlijn-geschiedenis importeren?',
                backup: 'Hoe maak ik een back-up van mijn gegevens?',
                reduceMemory: 'Hoe verlaag ik het geheugengebruik van GeoPulse?',
                backendRestarts: 'De backend herstart of heeft te weinig geheugen. Wat moet ik doen?'
            }
        },
        issues: {
            title: 'Problemen?',
            description: "Als u problemen ondervindt met tijdlijngeneratie, reistypeclassificatie of andere functies, kunt u geanonimiseerde debuggegevens exporteren om ons te helpen oplossen.",
            debugExport: {
                title: 'Debuggegevens exporteren',
                description: 'Exporteer uw GPS-gegevens met privacy-bevattende coördinaatverschuivingen. Alle coördinaten worden geanonimiseerd door ze met een willekeurige offset te verschuiven, waardoor het veilig is om te delen voor probleemoplossing.',
                bullets: [
                    'Coördinaten worden verschoven om uw privacy te beschermen',
                    'Locatienamen worden geanonimiseerd',
                    'Omvat tijdlijnconfiguratie voor analyse',
                    'Hiermee kunt u beschrijven wat er mis is en wanneer het gebeurde'
                ],
                demoDisabledText: 'Debuggegevens export is uitgeschakeld in demo-modus om het gedeelde demo-dataset te beschermen.',
                demoDisabledTooltip: 'Debuggegevens export is uitgeschakeld in demo-modus',
                button: 'Debuggegevens exporteren'
            },
            reportIssue: {
                title: 'Een probleem melden',
                description: 'Een bug gevonden of een functieverzoek? Meld het op GitHub.',
                stepsTitle: 'Hoe effectief een probleem melden:',
                steps: [
                    'Debuggegevens exporteren met de knop hierboven',
                    'Maak een nieuw issue op GitHub',
                    'Voeg de debug export ZIP-bestand toe',
                    'Beschrijf het probleem en het verwachte gedrag'
                ],
                button: 'GitHub Issue maken'
            },
            demoModeToast: 'Debuggegevens export is uitgeschakeld in demo-modus.'
        },
        docs: {
            title: 'Documentatie',
            description: 'Uitgebreide gidsen en documentatie voor alle GeoPulse functies',
            gettingStarted: {
                title: 'Aan de Slag',
                description: 'Snelle startgids en initiële installatie'
            },
            timelineFeatures: {
                title: 'Tijdlijn Functies',
                description: 'Hoe tijdlijngeneratie en -verwerking werkt'
            },
            tripClassification: {
                title: 'Reistypeclassificatie',
                description: 'Begrijpen hoe reizen worden geclassificeerd'
            },
            locationSources: {
                title: 'Locatiebronnen',
                description: 'Ondersteunde GPS-tracking bronnen en installatie'
            },
            faq: {
                title: 'Veelgestelde vragen',
                description: 'Veelgestelde vragen'
            },
            fullDocumentation: {
                title: 'Volledige Documentatie',
                description: 'Complete documentatie met zoeken'
            }
        },
        about: {
            title: 'Over GeoPulse',
            description: 'Toepassingsinformatie en credits',
            version: 'Versie',
            loading: 'Laden...',
            license: 'Licentie',
            licenseLinkText: 'BSL 1.1 Licentie',
            repository: 'Repository',
            repositoryLinkText: 'GitHub',
            author: 'Auteur',
            authorLinkText: 'tess1o',
            builtWith: 'Gebouwd Met'
        }
    }
}

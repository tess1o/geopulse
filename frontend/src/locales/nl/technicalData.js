export default {
    page: {
        title: 'GPS-gegevens',
        subtitle: 'Technische informatie over uw GPS-trackinggegevens',
        actions: {
            selected: '{count} geselecteerd',
            deleteSelected: 'Geselecteerde verwijderen',
            deleteAllData: 'Alle gegevens verwijderen',
            exportCsv: 'CSV exporteren',
            exportCsvSelected: 'CSV exporteren ({count} geselecteerd)',
            exportCsvZero: 'CSV exporteren (0 pt)',
            exportCsvFiltered: 'CSV exporteren ({count} pt)'
        },
        stats: {
            totalPoints: 'Totaal aantal GPS-punten',
            pointsToday: 'Punten vandaag',
            firstPoint: 'Eerste GPS-punt',
            lastPoint: 'Laatste GPS-punt'
        },
        filters: {
            title: 'Filters',
            activeCount: '{count} actief',
            clearAll: 'Alles wissen',
            hideAdvanced: 'Geavanceerd verbergen',
            showAdvanced: 'Geavanceerd tonen',
            today: 'Vandaag',
            yesterday: 'Gisteren',
            last7Days: 'Laatste 7 dagen',
            last30Days: 'Laatste 30 dagen',
            from: 'Vanaf',
            to: 'Tot',
            startPlaceholder: 'Startdatum en -tijd',
            endPlaceholder: 'Einddatum en -tijd',
            apply: 'Toepassen',
            clear: 'Wissen',
            fromBeforeTo: 'Van moet voor Tot liggen.',
            sourceTypesLabel: 'Bronnen:',
            allSources: 'Alle bronnen',
            accuracyLabel: 'Nauwkeurigheid (meter):',
            speedLabel: 'Snelheid (km/u):',
            min: 'Min',
            max: 'Max',
            rangeTo: 'tot',
            dateChip: 'Datum: {range}',
            accuracyChip: 'Nauwkeurigheid: {min} - {max} m',
            speedChip: 'Snelheid: {min} - {max} km/u',
            sourcesChip: 'Bronnen: {count} geselecteerd',
            sourceOptions: {
                manualReconstruction: 'Handmatige reconstructie'
            },
            noPointsForPeriod: 'Geen punten beschikbaar voor het geselecteerde periode',
            showingOfTotal: '{filtered} van {total} punten tonen',
            showingCount: '{count} punten tonen'
        },
        telemetry: {
            title: 'Telemetrie Mapping (Optioneel)',
            hint: 'Globaal per bron type voor GPS-gegevens en tijdlijnpop-ups.',
            hideMapping: 'Mapping verbergen',
            showMapping: 'Mapping tonen',
            sourceTypeLabel: 'Bron type',
            resetToDefaults: 'Reset naar standaard',
            addRow: 'Rij toevoegen',
            saveMapping: 'Mapping opslaan',
            loading: 'Telemetrie mapping laden...',
            empty: 'Geen telemetrie mappings geconfigureerd.',
            keyPlaceholder: 'ext sleutel',
            labelPlaceholder: 'Label',
            unitPlaceholder: 'Eenheid',
            trueValuesPlaceholder: 'Waarde voor waar (bijv. 1,true,on)',
            falseValuesPlaceholder: 'Waarde voor onwaar (bijv. 0,false,off)',
            booleanDefaultLabel: 'Booleaans {index}',
            columns: {
                key: 'Sleutel',
                label: 'Label',
                type: 'Type',
                unit: 'Eenheid',
                gpsData: 'GPS-gegevens',
                currentPopup: 'Huidig Pop-up',
                order: 'Volgorde'
            },
            typeOptions: {
                boolean: 'Booleaans',
                number: 'Getal',
                string: 'Tekst'
            }
        },
        table: {
            title: 'GPS-punten',
            showing: '{start}-{end} van {total} punten tonen',
            filteredFrom: 'gefilterd van {total}',
            noPointsFound: 'Geen punten gevonden',
            rowsPerPage: 'Rijen per pagina:',
            rowsShort: 'Rijen:',
            pageOf: 'Pagina {current} van {total}',
            totalCount: '{count} totaal',
            emptyTitle: 'Geen GPS-punten gevonden',
            emptyDescription: 'Geen GPS-trackinggegevens beschikbaar voor de geselecteerde criteria.',
            editTooltip: 'GPS-punt bewerken',
            deleteTooltip: 'GPS-punt verwijderen',
            columns: {
                date: 'Datum',
                delta: 'Delta',
                location: 'Locatie',
                speed: 'Snelheid',
                accuracy: 'Nauwkeurigheid',
                altitude: 'Hoogte',
                battery: 'Batterij',
                telemetry: 'Telemetrie',
                source: 'Bron',
                actions: 'Acties'
            }
        },
        mobile: {
            selectPage: 'Pagina selecteren',
            clearPage: 'Pagina wissen',
            selectAllAriaLabel: 'Alle GPS-punten op deze pagina selecteren',
            sort: 'Sorteren',
            ascending: 'Oplopend',
            descending: 'Aflopend',
            sortAscendingAriaLabel: 'Oplopend sorteren',
            sortDescendingAriaLabel: 'Aflopend sorteren',
            loading: 'GPS-punten laden...',
            actionsTooltip: 'GPS-punt acties',
            selectPointAriaLabel: 'GPS-punt selecteren van {date} {time}',
            actionsAriaLabel: 'Acties voor GPS-punt van {date} {time}'
        },
        pendingEdits: {
            message: '{count} GPS-puntwijziging is nog niet toegepast. Pas deze toe om het punt bij te werken en de tijdlijn opnieuw te genereren. | {count} GPS-puntwijzigingen zijn nog niet toegepast. Pas ze samen toe om de punten bij te werken en de tijdlijn eenmaal opnieuw te genereren.',
            revertAll: 'Alles terugdraaien',
            apply: 'Wijzigingen toepassen',
            revertTooltip: 'Wijziging terugdraaien',
            leaveConfirm: {
                header: 'Niet toegepaste wijzigingen',
                message: 'U hebt {count} GPS-puntwijziging die nog niet is toegepast. Als u deze pagina verlaat, gaat deze verloren. | U hebt {count} GPS-puntwijzigingen die nog niet zijn toegepast. Als u deze pagina verlaat, gaan deze verloren.',
                accept: 'Verlaten zonder toe te passen',
                reject: 'Blijven'
            }
        },
        dialogs: {
            delete: {
                header: 'GPS-punt verwijderen',
                message: 'Weet u zeker dat u dit GPS-punt wilt verwijderen? Deze actie kan niet ongedaan worden gemaakt.',
                confirm: 'Verwijderen'
            },
            bulkDelete: {
                header: 'Meerdere GPS-punten verwijderen',
                message: 'Weet u zeker dat u {count} GPS-punt wilt verwijderen? Deze actie kan niet ongedaan worden gemaakt. | Weet u zeker dat u {count} GPS-punten wilt verwijderen? Deze actie kan niet ongedaan worden gemaakt.',
                confirm: 'Alles verwijderen'
            },
            deleteAll: {
                header: 'Alle GPS-gegevens verwijderen',
                warningTitle: 'Dit verwijdert permanent:',
                allPoints: 'Alle {count} GPS-punten',
                timelineData: 'Alle tijdlijn verblijfplaatsen, reizen en datagaps',
                note: 'Uw instellingen en favoriete locaties worden {not} beïnvloed. Deze actie kan niet ongedaan worden gemaakt.',
                notWord: 'niet',
                confirm: 'Alles verwijderen'
            }
        },
        toasts: {
            telemetryLoadFailedSummary: 'Telemetrie laden mislukt',
            telemetryLoadFailedFallback: 'Telemetrie mapping laden is mislukt',
            telemetrySavedSummary: 'Telemetrie opgeslagen',
            telemetrySavedDetail: 'Telemetrie mapping succesvol bijgewerkt',
            saveFailedSummary: 'Opslaan mislukt',
            saveTelemetryFailedFallback: 'Telemetrie mapping opslaan is mislukt',
            telemetryResetSummary: 'Telemetrie gereset',
            telemetryResetDetail: 'Telemetrie mapping gereset naar standaard',
            resetFailedSummary: 'Reset mislukt',
            resetTelemetryFailedFallback: 'Telemetrie mapping resetten is mislukt',
            summaryLoadFailedDetail: 'GPS-gegevens samenvatting laden is mislukt',
            pointsLoadFailedDetail: 'GPS-punten laden is mislukt',
            exportStartedSummary: 'Export gestart',
            exportSelectedDetail: '{count} geselecteerde GPS-punten exporteren. Download start binnenkort.',
            exportAllDetail: '{count} GPS-punten exporteren. Download start binnenkort.',
            exportFailedSummary: 'Export mislukt',
            exportFailedDetail: 'GPS-gegevens exporteren is mislukt',
            pointDeletedSummary: 'GPS-punt verwijderd',
            pointDeletedDetail: 'GPS-punt is succesvol verwijderd',
            timelineRegenStarted: 'Tijdlijnregeneratie gestart.',
            timelineRegenScheduled: 'Tijdlijnregeneratie wordt uitgevoerd nadat de huidige tijdlijntaak is voltooid.',
            coverageRebuildScheduled: 'Dekking heropbouw gepland.',
            deleteFailedSummary: 'Verwijderen mislukt',
            deleteFailedFallback: 'GPS-punt verwijderen is mislukt',
            pointsUpdatedSummary: 'GPS-punten bijgewerkt',
            pointsUpdatedDetail: '{count} GPS-punt bijgewerkt. | {count} GPS-punten bijgewerkt.',
            updateFailedSummary: 'Bijwerken mislukt',
            updateFailedFallback: 'GPS-punt bijwerken is mislukt',
            pointsDeletedSummary: 'GPS-punten verwijderd',
            bulkDeleteSuccessDetail: '{count} GPS-punt succesvol verwijderd | {count} GPS-punten succesvol verwijderd',
            bulkDeleteFailedSummary: 'Bulk Verwijderen Mislukt',
            bulkDeleteFailedFallback: 'Geselecteerde GPS-punten verwijderen is mislukt',
            allDataDeletedSummary: 'Alle Gegevens Verwijderd',
            allDataDeletedDetail: 'Alle GPS-punten en tijdlijngegevens zijn succesvol verwijderd',
            deleteAllFailedFallback: 'Alle GPS-gegevens verwijderen is mislukt'
        }
    }
}

/**
 * Reverse geocoding management page (German).
 */
export default {
    page: {
        title: 'Reverse Geocoding verwalten',
        subtitle: 'Verwalte und aktualisiere deine Geocoding-Ergebnisse',
        reconcileAllButton: 'Alle abgleichen ({count})',
        bulkEditButton: 'Gemeinsam bearbeiten ({count})',
        reconcileSelectedButton: 'Auswahl abgleichen ({count})',
        providerSwitchInfo: 'Ein Wechsel des Anbieters gilt nur für neue Abfragen. Mit „Auswahl abgleichen“ bzw. „Alle abgleichen“ aktualisierst du bestehende Einträge im Cache.',
        normalization: {
            title: 'Normalisierungsregeln',
            subtitle: 'Eigene Zuordnungsregeln für Länder- und Städtenamen. Neue Geocoding-Ergebnisse nutzen sie automatisch; mit „Regeln jetzt anwenden“ aktualisierst du vorhandene Geocoding-Einträge und Favoriten.',
            collapse: 'Einklappen',
            expand: 'Aufklappen',
            addRule: 'Regel hinzufügen',
            applyRulesNow: 'Regeln jetzt anwenden',
            collapsedHint: 'Bereich eingeklappt. Aufklappen, um Regeln zu verwalten.',
            empty: 'Noch keine Normalisierungsregeln.',
            typeColumn: 'Typ',
            typeCountry: 'Land',
            typeCity: 'Stadt',
            fromColumn: 'Von',
            toColumn: 'Zu',
            actionsColumn: 'Aktionen',
            valueDash: '-'
        },
        filters: {
            providerLabel: 'Anbieter:',
            allProviders: 'Alle Anbieter',
            searchLabel: 'Suche:',
            searchPlaceholder: 'Nach Ort, Stadt oder Land suchen',
            clearFilters: 'Filter zurücksetzen'
        },
        table: {
            title: 'Geocoding-Ergebnisse',
            emptyTitle: 'Keine Geocoding-Ergebnisse gefunden',
            emptyDescription: 'Für die gewählten Kriterien gibt es keine Geocoding-Daten.',
            displayNameColumn: 'Anzeigename',
            cityColumn: 'Stadt',
            countryColumn: 'Land',
            providerColumn: 'Anbieter',
            coordinatesColumn: 'Koordinaten',
            lastUsedColumn: 'Zuletzt verwendet',
            actionsColumn: 'Aktionen',
            nullValue: '-',
            viewDetailsTooltip: 'Details ansehen',
            editTooltip: 'Bearbeiten',
            reconcileTooltip: 'Abgleichen'
        },
        bulkEdit: {
            itemTypeName: 'Geocoding-Ergebnisse'
        },
        ruleDialog: {
            editHeader: 'Normalisierungsregel bearbeiten',
            addHeader: 'Normalisierungsregel hinzufügen',
            ruleTypeLabel: 'Art der Regel',
            fromCountryLabel: 'Von Land',
            toCountryLabel: 'Zu Land',
            fromCityLabel: 'Von Stadt',
            toCityLabel: 'Zu Stadt',
            countryPlaceholderFrom: 'z. B. Bulgaria',
            countryPlaceholderTo: 'z. B. Bulgarien',
            cityPlaceholderFrom: 'z. B. Munich',
            cityPlaceholderTo: 'z. B. München',
            cancel: 'Abbrechen',
            save: 'Speichern'
        },
        applyRulesDialog: {
            applySelectedHeader: 'Ausgewählte Regel anwenden',
            applyAllHeader: 'Normalisierungsregeln anwenden',
            applying: 'Wird angewendet: {source} → {target}',
            applyToGeocoding: 'Auf Reverse-Geocoding-Einträge anwenden',
            applyToFavorites: 'Auf Favoriten anwenden',
            cancel: 'Abbrechen',
            apply: 'Anwenden'
        },
        ruleTypeOptions: {
            country: 'Land',
            city: 'Stadt'
        },
        deleteRuleConfirm: 'Zuordnung „{source} → {target}“ löschen?',
        toasts: {
            loadResultsFailed: 'Geocoding-Ergebnisse konnten nicht geladen werden',
            loadRulesFailed: 'Normalisierungsregeln konnten nicht geladen werden',
            ruleUpdatedSummary: 'Regel aktualisiert',
            ruleUpdatedDetail: 'Normalisierungsregel aktualisiert',
            ruleAddedSummary: 'Regel hinzugefügt',
            ruleAddedDetail: 'Normalisierungsregel erstellt',
            saveFailedSummary: 'Speichern fehlgeschlagen',
            saveRuleFailedFallback: 'Normalisierungsregel konnte nicht gespeichert werden',
            ruleDeletedSummary: 'Regel gelöscht',
            ruleDeletedDetail: 'Normalisierungsregel gelöscht',
            deleteFailedSummary: 'Löschen fehlgeschlagen',
            deleteRuleFailedFallback: 'Normalisierungsregel konnte nicht gelöscht werden',
            normalizationStartedSummary: 'Normalisierung gestartet',
            normalizationStartedSingleDetail: 'Ausgewählte Regel wird im Hintergrund angewendet …',
            normalizationStartedAllDetail: 'Regeln werden im Hintergrund angewendet …',
            applyFailedSummary: 'Anwenden fehlgeschlagen',
            applyFailedFallback: 'Normalisierung konnte nicht gestartet werden',
            updateSuccessDetail: 'Geocoding-Ergebnis aktualisiert',
            updateFailedSummary: 'Aktualisieren fehlgeschlagen',
            updateFailedFallback: 'Geocoding-Ergebnis konnte nicht aktualisiert werden',
            reconciliationFailedSummary: 'Abgleich fehlgeschlagen',
            reconciliationFailedFallback: 'Abgleich konnte nicht gestartet werden',
            reconciliationCompleteSummary: 'Abgleich abgeschlossen',
            reconciliationCompleteDetail: '{success} von {total} Ergebnissen abgeglichen',
            normalizationCompleteSummary: 'Normalisierung abgeschlossen',
            normalizationCompleteDetail: '{geocodingUpdated} Geocoding-Einträge und {favoritesUpdated} Favoriten aktualisiert',
            normalizationCompleteFailedSuffix: ' ({count} fehlgeschlagen)'
        }
    }
}

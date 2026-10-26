/**
 * Timeline & Map tab (German).
 */
export default {
    title: 'Zeitleiste & Karte',
    description: 'Passe die Darstellung von Zeitleiste und Karte an, ohne die Zeitleistendaten neu zu berechnen.',
    behavior: {
        heading: 'Verhalten der Zeitleiste',
        description: 'Lege fest, was du beim Öffnen und Bedienen der Zeitleiste siehst.',
        defaultDateRange: {
            title: 'Standardzeitraum',
            description: 'Der anfängliche Zeitraum für Zeitleiste, Übersicht und Zeitleisten-Berichte.',
            details: 'Leere die Auswahl, um den App-Standard zu nutzen: Heute.',
            placeholder: 'App-Standard verwenden (Heute)'
        },
        telemetry: {
            title: 'Telemetrie beim aktuellen Standort',
            description: 'Telemetriewerte im Karten-Popup des aktuellen Standorts anzeigen.',
            details: 'Betrifft nur die Anzeige im Popup. Speicherung der Telemetrie und GPS-Datentabelle bleiben unverändert.'
        }
    },
    sources: {
        heading: 'Kartenanzeige & Quellen',
        description: 'Lege fest, wie Karten dargestellt werden und woher ihre Daten kommen.',
        renderMode: {
            title: 'Darstellungsmodus der Karte',
            description: 'Der Renderer für alle Kartenansichten.',
            details: 'Beim Wechsel bleiben beide eigenen Quell-URLs erhalten.'
        },
        buildings3d: {
            title: '3D-Gebäude',
            description: 'Gebäudeformen auf kompatiblen Vektorkarten anzeigen.',
            details: 'Nur für MapTiler-Stile mit Gebäudehöhen verfügbar. Gebäude erscheinen, wenn du bis auf Straßenebene heranzoomst.'
        },
        rasterTiles: {
            title: 'Eigene Raster-Kacheln',
            description: 'Optionale Quelle für den Raster-Modus.',
            details: "Die URL-Vorlage muss HTTP oder HTTPS nutzen und {'{z}'}, {'{x}'} und {'{y}'} enthalten. Leer lassen für OpenStreetMap.",
            ariaLabel: 'URL für eigene Raster-Kacheln'
        },
        vectorStyle: {
            title: 'Eigener Vektorstil',
            description: 'Optionaler Stil für den Vektor-Modus.',
            details: 'Gib eine HTTP- oder HTTPS-URL zu einer Stil-JSON ein. Leer lassen für OpenFreeMap.',
            ariaLabel: 'URL für eigenen Vektorstil'
        }
    },
    processing: {
        heading: 'Kartenverarbeitung',
        description: 'Optionales Map-Matching und Darstellungsleistung steuern.',
        mapMatching: {
            title: 'Map-Matching',
            descriptionAvailable: 'Zwischengespeicherte, an Straßen angepasste Strecken anzeigen, sofern vorhanden',
            descriptionUnavailable: 'Nicht verfügbar, bis die Administration einen Valhalla-Dienst einrichtet.',
            detailsAvailable: 'Erfordert eine eingerichtete Valhalla-Instanz. GPS-Rohdaten, Exporte und die Erkennung der Zeitleiste bleiben unverändert.',
            detailsUnavailable: 'Die Administration muss Map-Matching aktivieren und Valhalla einrichten, bevor du das einschalten kannst.'
        },
        rawGps: {
            title: 'Rohes GPS zeigen für',
            description: 'Die ursprüngliche GPS-Strecke für ausgewählte Fortbewegungsarten behalten.',
            details: 'Das Matching kann im Hintergrund trotzdem laufen, angepasste Strecken, Fortschritt, Vergleich und Details bleiben aber ausgeblendet.',
            placeholder: 'Angepasste Routen für alle unterstützten Arten verwenden',
            ariaLabel: 'Fortbewegungsarten mit rohem GPS'
        },
        simplification: {
            title: 'Streckenvereinfachung',
            description: 'Weniger Punkte pro Fahrt zeichnen.',
            details: 'Vereinfacht Strecken mit dem Douglas-Peucker-Algorithmus, ohne deine Zeitleistendaten zu ändern.'
        },
        tolerance: {
            title: 'Toleranz der Vereinfachung',
            description: 'Abstandsschwelle für die Vereinfachung von Strecken.',
            detailLowerLabel: 'Niedrigere Werte (1–10 m)',
            detailLowerValue: 'Mehr Details, mehr Punkte',
            detailHigherLabel: 'Höhere Werte (20–100 m)',
            detailHigherValue: 'Stärkere Komprimierung, weniger Punkte'
        },
        maxPoints: {
            title: 'Maximale Punktzahl',
            description: 'Wie viele GPS-Punkte eine Strecke höchstens zeigt.',
            details: 'Liegt eine Strecke über diesem Limit, wird die Toleranz automatisch erhöht. 0 bedeutet kein Limit.'
        },
        adaptive: {
            title: 'Adaptive Vereinfachung',
            description: 'Vereinfachung automatisch an die Länge der Fahrt anpassen.',
            details: 'Längere Fahrten nutzen eine höhere Toleranz für bessere Leistung, kürzere behalten mehr Details.'
        }
    },
    toggleAria: {
        mapMatching: 'Map-Matching aktivieren'
    },
    // Trailing units appended to slider values by SliderControl.
    pointsSuffix: ' Punkte',
    toleranceLabels: {
        low: '1 m (viele Details)',
        mid: '15 m (ausgewogen)',
        high: '50 m (starke Komprimierung)'
    },
    maxPointsLabels: {
        none: '0 (kein Limit)',
        balanced: '100 (ausgewogen)',
        high: '500 (hohes Limit)'
    },
    dateRangeOptions: {
        today: 'Heute',
        yesterday: 'Gestern',
        lastWeek: 'Letzte 7 Tage',
        lastMonth: 'Letzte 30 Tage'
    },
    renderModeOptions: {
        vector: 'Vektor (MapLibre)',
        raster: 'Raster (Leaflet)'
    },
    resetToDefaults: 'Auf Standard zurücksetzen',
    saveChanges: 'Änderungen speichern',
    validation: {
        tilePlaceholders: "Die URL muss die Platzhalter {'{z}'}, {'{x}'} und {'{y}'} enthalten",
        protocol: 'Die URL muss HTTP oder HTTPS nutzen',
        invalidProtocol: 'Ungültiges URL-Protokoll',
        invalidFormat: 'Ungültiges URL-Format',
        styleEndpoint: 'Die URL sollte auf eine Stil-JSON zeigen'
    }
}

/**
 * Help & Support page (German).
 */
export default {
    page: {
        title: 'Hilfe & Support',
        description: 'Hilfe bekommen, Dokumentation lesen und Probleme melden',
        issues: {
            title: 'Probleme?',
            description: 'Wenn es Probleme mit der Erstellung der Zeitleiste, der Einstufung von Fahrten oder anderen Funktionen gibt, kannst du anonymisierte Debug-Daten exportieren, die bei der Fehlersuche helfen.',
            debugExport: {
                title: 'Debug-Daten exportieren',
                description: 'Exportiere deine GPS-Daten mit verschobenen Koordinaten. Alle Koordinaten werden um einen zufälligen Wert verschoben und so anonymisiert, sodass du sie gefahrlos zur Fehlersuche weitergeben kannst.',
                bullets: [
                    'Koordinaten werden zum Schutz deiner Privatsphäre verschoben',
                    'Ortsnamen werden anonymisiert',
                    'Enthält die Zeitleisten-Konfiguration für die Analyse',
                    'Du kannst beschreiben, was nicht stimmt und wann es passiert ist'
                ],
                demoDisabledText: 'Der Debug-Datenexport ist im Demo-Modus deaktiviert, um den gemeinsamen Demo-Datensatz zu schützen.',
                demoDisabledTooltip: 'Der Debug-Datenexport ist im Demo-Modus deaktiviert',
                button: 'Debug-Daten exportieren'
            },
            reportIssue: {
                title: 'Problem melden',
                description: 'Einen Fehler gefunden oder einen Funktionswunsch? Melde ihn auf GitHub.',
                stepsTitle: 'So meldest du am besten:',
                steps: [
                    'Exportiere Debug-Daten über den Button oben',
                    'Erstelle ein neues Issue auf GitHub',
                    'Hänge die ZIP-Datei des Debug-Exports an',
                    'Beschreib das Problem und das erwartete Verhalten'
                ],
                button: 'GitHub-Issue erstellen'
            },
            demoModeToast: 'Der Debug-Datenexport ist im Demo-Modus deaktiviert.'
        },
        docs: {
            title: 'Dokumentation',
            description: 'Ausführliche Anleitungen und Dokumentation zu allen Funktionen von GeoPulse',
            gettingStarted: {
                title: 'Erste Schritte',
                description: 'Schnellstart und Ersteinrichtung'
            },
            timelineFeatures: {
                title: 'Funktionen der Zeitleiste',
                description: 'Wie die Zeitleiste erstellt und berechnet wird'
            },
            tripClassification: {
                title: 'Einstufung von Fahrten',
                description: 'Wie Fahrten eingestuft werden'
            },
            locationSources: {
                title: 'Standortquellen',
                description: 'Unterstützte GPS-Quellen und ihre Einrichtung'
            },
            faq: {
                title: 'FAQ',
                description: 'Häufige Fragen'
            },
            fullDocumentation: {
                title: 'Gesamte Dokumentation',
                description: 'Vollständige Dokumentation mit Suche'
            }
        },
        about: {
            title: 'Über GeoPulse',
            description: 'Informationen zur Anwendung und Mitwirkende',
            version: 'Version',
            loading: 'Wird geladen …',
            license: 'Lizenz',
            licenseLinkText: 'BSL-1.1-Lizenz',
            repository: 'Repository',
            repositoryLinkText: 'GitHub',
            author: 'Autor',
            authorLinkText: 'tess1o',
            builtWith: 'Erstellt mit'
        }
    }
}

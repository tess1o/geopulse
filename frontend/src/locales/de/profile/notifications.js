/**
 * Notifications tab, plus the shared channel settings block it renders per event group (German).
 */
export default {
    title: 'Benachrichtigungen',
    description: 'Lege fest, welche Ereignisse Hinweise auslösen und wie sie zugestellt werden.',
    gpsHealth: {
        heading: 'GPS-Status',
        description: 'Werde benachrichtigt, wenn alle aktiven Live-GPS-Quellen keine Daten mehr senden.',
        monitor: {
            title: 'GPS-Eingang überwachen',
            description: 'Längere Lücken bei allen aktiven Live-Quellen erkennen.'
        },
        silence: {
            title: 'Schwelle für Funkstille',
            description: 'So viele Minuten warten, bevor der erste Hinweis erstellt wird.',
            details: 'Wähle einen Wert zwischen 1 Minute und 7 Tagen.',
            ariaLabel: 'Schwelle für GPS-Funkstille in Minuten'
        },
        channelLabel: 'Zustellung GPS-Status'
    },
    rewind: {
        heading: 'Monatlicher Rückblick',
        description: 'Erhalte eine Erinnerung, wenn der Vormonat zum Ansehen bereit ist.',
        notify: {
            title: 'Benachrichtigen, wenn der Rückblick bereit ist',
            description: 'Am ersten Tag jedes Monats einen Hinweis erstellen.'
        },
        channelLabel: 'Zustellung Rückblick'
    },
    productUpdates: {
        heading: 'Produkt-Neuigkeiten',
        description: 'Hinweise in der App zu neuen GeoPulse-Funktionen steuern.',
        whatsNew: {
            title: 'Highlights neuer Versionen anzeigen',
            description: '„Was ist neu“ einmal nach einem Update anzeigen.',
            details: 'Die Highlights erscheinen nur in GeoPulse und werden nie extern versendet.'
        }
    },
    // Trailing unit for the silence-threshold number input.
    silenceSuffix: ' Min.',
    saveChanges: 'Änderungen speichern',
    channels: {
        inApp: {
            title: 'Im Posteingang anzeigen',
            description: 'Einen Hinweis im Benachrichtigungs-Posteingang von GeoPulse erstellen.'
        },
        apprise: {
            title: 'Über Apprise senden',
            description: 'Diesen Hinweis an eingerichtete externe Ziele weiterleiten.'
        },
        routing: {
            title: 'Apprise-Zustellung',
            description: 'Lege fest, wie GeoPulse das Ziel adressiert.',
            urls: 'Ziel-URL(s)',
            keyTag: 'Apprise-Konfigurationsschlüssel und -Tag'
        },
        destinationUrls: {
            title: 'Ziel-URLs',
            description: 'Gib eine oder mehrere Apprise-Ziel-URLs ein.',
            ariaLabel: 'Apprise-Ziel-URLs'
        },
        configKey: {
            title: 'Konfigurationsschlüssel',
            description: 'Name der gespeicherten Apprise-Konfiguration.',
            placeholder: 'Apprise-Konfigurationsschlüssel',
            ariaLabel: 'Apprise-Konfigurationsschlüssel'
        },
        configTag: {
            title: 'Konfigurations-Tag',
            description: 'Optionales Tag zur Auswahl eingerichteter Ziele.',
            placeholder: 'Optionales Apprise-Tag',
            ariaLabel: 'Apprise-Konfigurations-Tag'
        }
    }
}

/**
 * Notifications tab, plus the shared channel settings block it renders per event group.
 *
 * 'GPS health', 'Monthly Rewind', 'Product updates', 'Destination URLs', 'Configuration key' and
 * 'Configuration tag' are asserted verbatim by NotificationsPreferencesTab.test.js.
 */
export default {
    title: 'Meldingen',
    description: 'Kies welke gebeurtenissen waarschuwingen genereren en hoe ze worden bezorgd.',
    gpsHealth: {
        heading: 'GPS-status',
        description: 'Krijg een waarschuwing wanneer elke actieve live GPS-bron stopt met het versturen van gegevens.',
        monitor: {
            title: 'GPS-aankomsten bewaken',
            description: 'Detecteer uitgebreide gaten over alle actieve live bronnen.'
        },
        silence: {
            title: 'Stiltedrempel',
            description: 'Wacht dit aantal minuten voordat de eerste waarschuwing wordt gemaakt.',
            details: 'Kies een waarde van 1 minuut tot 7 dagen.',
            ariaLabel: 'GPS-stiltedrempel in minuten'
        },
        channelLabel: 'GPS-status bezorging'
    },
    rewind: {
        heading: 'Maandelijkse Terugspoeling',
        description: 'Krijg een herinnering wanneer de vorige maand klaar is om te verkennen.',
        notify: {
            title: 'Meld wanneer Terugspoeling klaar is',
            description: 'Maak een waarschuwing op de eerste dag van elke maand.'
        },
        channelLabel: 'Terugspoeling bezorging'
    },
    productUpdates: {
        heading: 'Productupdates',
        description: 'Controleer in-app aankondigingen over nieuwe GeoPulse-functies.',
        whatsNew: {
            title: 'Release-accenten tonen',
            description: 'Toon Wat is er Nieuw eenmaal na een upgrade.',
            details: 'Release-accenten verschijnen alleen binnen GeoPulse en worden nooit extern verzonden.'
        }
    },
    // Trailing unit for the silence-threshold number input.
    silenceSuffix: ' min',
    saveChanges: 'Wijzigingen opslaan',
    channels: {
        inApp: {
            title: 'Toneren in inbox',
            description: 'Maak een waarschuwing in de GeoPulse-meldingeninbox.'
        },
        apprise: {
            title: 'Versturen via Apprise',
            description: 'Stuur deze waarschuwing door naar geconfigureerde externe bestemmingen.'
        },
        routing: {
            title: 'Apprise-routering',
            description: 'Kies hoe GeoPulse de bestemming adresseert.',
            urls: 'Bestemming-URL(s)',
            keyTag: 'Apprise-configsleutel en -tag'
        },
        destinationUrls: {
            title: 'Bestemming-URLs',
            description: 'Voer een of meer Apprise-bestemming-URL\'s in.',
            ariaLabel: 'Apprise-bestemming-URLs'
        },
        configKey: {
            title: 'Configsleutel',
            description: 'Naam van de opgeslagen Apprise-configuratie.',
            placeholder: 'Apprise-configsleutel',
            ariaLabel: 'Apprise-configsleutel'
        },
        configTag: {
            title: 'Configtag',
            description: 'Optionele tag die wordt gebruikt om geconfigureerde bestemmingen te selecteren.',
            placeholder: 'Optionele Apprise-tag',
            ariaLabel: 'Apprise-configtag'
        }
    }
}


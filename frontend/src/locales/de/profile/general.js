/**
 * General tab (German): identity, regional preferences, and the default landing page.
 *
 * The destination picker reuses the `nav.items.*` keys; timezone city names are IANA identifiers
 * and deliberately not translated (see en/profile/general.js).
 */
export default {
    title: 'Allgemein',
    description: 'Verwalte dein Profil und deine wichtigsten Anzeigeeinstellungen.',
    profile: {
        heading: 'Profil',
        description: 'Wähle den Namen und das Bild, die in GeoPulse angezeigt werden.',
        fullName: {
            title: 'Vollständiger Name',
            description: 'Der Name, der zu deinem Konto angezeigt wird.',
            placeholder: 'Dein vollständiger Name'
        },
        image: {
            heading: 'Profilbild',
            optional: 'Optional',
            description: 'Passe das Bild für deine Kartenmarker an.',
            upload: 'Eigenen Avatar hochladen',
            formats: 'PNG, JPEG oder WEBP; wird vor dem Hochladen automatisch verkleinert.',
            selected: 'Ausgewählt: {name}',
            chooseAria: 'Profilbild {index} wählen'
        }
    },
    regional: {
        heading: 'Regionale Einstellungen',
        description: 'Zeitzone, Formate und Maßeinheiten festlegen.',
        timezone: {
            title: 'Zeitzone',
            description: 'Wird für Datum, Uhrzeit und Statistiken verwendet.',
            placeholder: 'Zeitzone wählen'
        },
        dateFormat: {
            title: 'Datumsformat',
            description: 'Lege fest, wie Datumsangaben in Ziffern erscheinen.',
            details: 'Datumsparameter in URLs nutzen weiterhin ein festes ISO-Format.',
            placeholder: 'Datumsformat wählen'
        },
        timeFormat: {
            title: 'Uhrzeitformat',
            description: 'Wähle zwischen 12- und 24-Stunden-Anzeige.',
            placeholder: 'Uhrzeitformat wählen'
        },
        timeDisplayMode: {
            title: 'Zeitzone der Zeitleiste',
            description: 'Zeige Zeiten in Zeitleiste und GPS-Daten in der Zeitzone deines Profils oder in der Ortszeit des jeweiligen Orts.',
            details: 'Die Tage werden weiterhin nach der Zeitzone deines Profils gruppiert. Die Ortszeitzone stammt von der nächstgelegenen Stadt, nahe an Zeitzonengrenzen können Zeiten also ungenau sein. Fahr mit der Maus über eine Uhrzeit, um zu sehen, woher ihre Zeitzone stammt.',
            unavailable: 'Die Ortszeit ist nicht verfügbar, weil die GeoNames-Städtedaten auf diesem Server nicht geladen sind. Zeiten werden in der Zeitzone deines Profils angezeigt.'
        },
        distanceUnit: {
            title: 'Entfernungseinheit',
            description: 'Gilt für angezeigte Entfernungen und Geschwindigkeiten.',
            placeholder: 'Entfernungseinheit wählen'
        },
        temperatureUnit: {
            title: 'Temperatureinheit',
            description: 'Gilt für Temperaturen in den Wetteranzeigen.',
            placeholder: 'Temperatureinheit wählen'
        }
    },
    navigation: {
        heading: 'Navigation',
        description: 'Lege fest, wo GeoPulse nach der Anmeldung startet.',
        homePage: {
            title: 'Startseite',
            description: 'Die erste Seite nach der Anmeldung.',
            details: 'Leere die Auswahl, um das Standardverhalten von GeoPulse zu nutzen. Eigene Ziele müssen interne Pfade sein, die mit / beginnen.',
            placeholder: 'Startseite wählen',
            customOption: 'Eigene URL …',
            customLabel: 'Eigener interner Pfad',
            customPlaceholder: '/app/deine-eigene-seite'
        }
    },
    dateFormatOptions: {
        dmy: 'TT/MM/JJJJ (europäisch)',
        mdy: 'MM/TT/JJJJ (US)',
        ymd: 'JJJJ-MM-TT (ISO)'
    },
    timeFormatOptions: {
        h24: '24 Stunden (13:45)',
        h12: '12 Stunden (1:45 PM)'
    },
    timeDisplayModeOptions: {
        profile: 'Zeitzone des Profils',
        location: 'Ortszeit am jeweiligen Ort'
    },
    distanceUnitOptions: {
        kilometers: 'Kilometer (km, m)',
        miles: 'Meilen (mi, ft)'
    },
    temperatureUnitOptions: {
        celsius: 'Celsius (°C)',
        fahrenheit: 'Fahrenheit (°F)'
    },
    actions: {
        reset: 'Zurücksetzen',
        save: 'Änderungen speichern'
    },
    errors: {
        fullNameRequired: 'Name ist erforderlich',
        fullNameTooShort: 'Der Name muss mindestens 2 Zeichen lang sein',
        customUrlRequired: 'Eigene URL ist erforderlich',
        customUrlInternal: 'Die URL muss ein interner Pfad sein, der mit / beginnt',
        customUrlInvalid: 'Ungültiges URL-Format',
        customUrlTooLong: 'Die URL ist zu lang (höchstens 1000 Zeichen)',
        imageLoad: 'Bild konnte nicht geladen werden',
        imageTooLarge: 'Das Bild ist auch nach der Komprimierung zu groß. Versuch ein anderes Bild.',
        imageFormat: 'Nicht unterstütztes Bildformat. Verwende PNG, JPEG oder WEBP.',
        imageUnsupported: 'Bildverarbeitung wird in diesem Browser nicht unterstützt',
        imageProcess: 'Das ausgewählte Bild konnte nicht verarbeitet werden',
        avatarTooLarge: 'Der Avatar ist auch nach der Komprimierung zu groß'
    }
}

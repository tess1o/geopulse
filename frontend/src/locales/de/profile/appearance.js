/**
 * Appearance & accessibility tab (German): map colors, path width/outline, speed bands and heatmap gradient.
 */
export default {
    title: 'Darstellung & Barrierefreiheit',
    description: 'Wähle Kartenfarben und Linienstile, die für dich gut erkennbar sind, auch Vorlagen für Farbsehschwächen.',
    colorVision: {
        heading: 'Farbsehen',
        description: 'Wähle eine Vorlage, die Strecken-, Geschwindigkeits- und Heatmap-Farben sowie die Farben der Fortbewegungsarten in Diagrammen in einem Schritt festlegt. Einzelne Farben kannst du unten trotzdem anpassen.',
        customized: 'Angepasst',
        schemes: {
            DEFAULT: {
                name: 'Standard',
                description: 'Die ursprünglichen GeoPulse-Farben.'
            },
            RED_GREEN_SAFE: {
                name: 'Rot-Grün-sicher',
                description: 'Bei Deuteranopie und Protanopie. Nutzt Blau und Gelb statt Rot und Grün.'
            },
            BLUE_YELLOW_SAFE: {
                name: 'Blau-Gelb-sicher',
                description: 'Bei Tritanopie. Nutzt Rot und Petrol statt Blau und Gelb.'
            },
            HIGH_CONTRAST: {
                name: 'Hoher Kontrast',
                description: 'Kräftige, klar unterscheidbare Farben, die sich auch in der Helligkeit unterscheiden. Für alle Arten des Farbsehens geeignet.'
            }
        }
    },
    preview: {
        heading: 'Vorschau',
        description: 'So sehen Strecken mit deiner aktuellen (noch nicht gespeicherten) Auswahl auf heller, dunkler und Satellitenkarte aus.',
        ariaLabel: 'Vorschau der Strecken auf heller, dunkler und Satellitenkarte',
        backgrounds: {
            light: 'Helle Karte',
            dark: 'Dunkle Karte',
            satellite: 'Satellit'
        },
        simulate: {
            label: 'Simulieren',
            none: 'Normales Sehen',
            deuteranopia: 'Deuteranopie',
            protanopia: 'Protanopie',
            tritanopia: 'Tritanopie'
        }
    },
    paths: {
        heading: 'Strecken',
        description: 'Farben und Linienstil für Fahrten auf der Karte.',
        followScheme: 'Aus dem Farbschema',
        resetColor: 'Farbe des Schemas verwenden',
        defaultPathColor: {
            title: 'Streckenfarbe',
            description: 'Farbe für nicht ausgewählte Fahrten.',
            details: 'Nicht festlegen, um dem Farbschema zu folgen. Wird ein mehrtägiger Zeitraum nach Tagen eingefärbt, gelten stattdessen die Tagesfarben.'
        },
        activePathColor: {
            title: 'Farbe der ausgewählten Fahrt',
            description: 'Farbe der hervorgehobenen Linie, wenn du eine Fahrt auswählst.',
            details: 'Nicht festlegen, um dem Farbschema zu folgen. Autofahrten nutzen die Geschwindigkeitsfarben, sofern diese nicht ausgeschaltet sind.'
        },
        pathWidth: {
            title: 'Linienbreite',
            description: 'Dicke der Fahrtlinien. Die ausgewählte Fahrt wird etwas dicker gezeichnet.',
            details: 'Dickere Linien sind auf unruhigen Karten oder Satellitenbildern leichter zu erkennen.'
        },
        outline: {
            title: 'Linienkontur',
            description: 'Einen kontrastierenden Rand um Fahrtlinien zeichnen.',
            details: 'Hält Linien auf jedem Hintergrund sichtbar, egal in welcher Farbe.'
        },
        widthLabels: {
            thin: 'Dünn',
            default: 'Standard',
            thick: 'Dick'
        }
    },
    speedBands: {
        heading: 'Geschwindigkeitsfarben für Autofahrten',
        description: 'Eine ausgewählte Autofahrt wird nach Geschwindigkeit eingefärbt, damit du langsame und schnelle Abschnitte erkennst.',
        palette: {
            title: 'Geschwindigkeitsfarben',
            description: 'Farben für langsame, mittlere und schnelle Abschnitte einer Autofahrt.',
            details: 'Langsam ist unter {slow} {unit}, mittel {slow}–{medium} {unit}, schnell über {medium} {unit}. Ausschalten, um Autofahrten in der Farbe der ausgewählten Fahrt zu zeichnen.',
            followScheme: 'Farbschema folgen ({name})'
        },
        options: {
            DEFAULT: 'Rot / Bernstein / Grün',
            RED_GREEN_SAFE: 'Violett / Himmelblau / Gelb',
            BLUE_YELLOW_SAFE: 'Rot / Lachs / Petrol',
            HIGH_CONTRAST: 'Blau / Rosé / Gold',
            OFF: 'Aus (eine Farbe)'
        }
    },
    days: {
        heading: 'Mehrtägige Zeiträume',
        description: 'Wenn du mehr als einen Tag ansiehst, kann jeder Tag eine eigene Streckenfarbe bekommen.',
        pathColorMode: {
            title: 'Strecken nach Tag einfärben',
            description: 'Jeden Tag eines mehrtägigen Zeitraums in einer eigenen Farbe zeichnen, passend zu den Punkten in den Tagesköpfen der Zeitleiste.',
            details: 'Du kannst das auch auf der Karte mit dem Kalender-Button umschalten. Kurze Zeiträume bekommen eine eigene Farbe pro Tag, längere laufen vom ältesten zum neuesten Tag entlang der Heatmap-Farben. Farben, die der Farbe der ausgewählten Fahrt zu ähnlich sind, werden übersprungen.'
        },
        dayPalette: {
            title: 'Tagesfarben',
            description: 'Farben für die Tage eines mehrtägigen Zeitraums.',
            details: 'Okabe-Ito, Gedämpft und Hell und dunkel bleiben bei den häufigen Farbsehschwächen unterscheidbar. Hell und dunkel wechselt zusätzlich die Helligkeit, sodass sich benachbarte Tage sogar in Graustufen unterscheiden.',
            followScheme: 'Farbschema folgen ({name})'
        },
        options: {
            CLASSIC: 'Klassisch (7 Farben)',
            OKABE_ITO: 'Okabe-Ito (7 Farben)',
            TOL_MUTED: 'Gedämpft (7 Farben)',
            CONTRAST: 'Hell und dunkel (7 Farben)'
        }
    },
    heatmap: {
        heading: 'Heatmap',
        description: 'Farben der Heatmap auf der Zeitleistenkarte und in Zusammenfassungen.',
        gradient: {
            title: 'Heatmap-Farben',
            description: 'Verlauf von wenigen zu vielen Besuchen.',
            details: 'Viridis und Cividis bleiben für alle Arten des Farbsehens und in Graustufen lesbar.',
            followScheme: 'Farbschema folgen ({name})'
        },
        options: {
            CLASSIC: 'Klassisch (Blau bis Rot)',
            VIRIDIS: 'Viridis (Lila bis Gelb)',
            CIVIDIS: 'Cividis (Marineblau bis Gelb)'
        }
    },
    map: {
        heading: 'Karte',
        description: 'Zusätzliche Informationen auf jeder Karte.',
        scaleBar: {
            title: 'Maßstabsleiste',
            description: 'Eine Maßstabsleiste unten links auf Karten anzeigen.',
            details: 'Nutzt Kilometer oder Meilen, je nach deiner Entfernungseinheit.'
        }
    },
    resetToDefaults: 'Auf Standard zurücksetzen',
    saveChanges: 'Änderungen speichern'
}

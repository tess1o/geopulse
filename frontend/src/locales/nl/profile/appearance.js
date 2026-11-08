/**
 * Appearance & accessibility tab: map colors, path width/outline, speed bands and heatmap gradient.
 */
export default {
    title: 'Uiterlijk & Toegankelijkheid',
    description: 'Kies kaartkleuren en lijnstijlen die voor u goed zichtbaar zijn, inclusief kleurenzien-vriendelijke vooraf ingestelde instellingen.',
    colorVision: {
        heading: 'Kleurenzien',
        description: 'Kies een vooraf ingestelde instelling die pad-, snelheid- en heatmapkleuren in één stap instelt, samen met de reistypekleuren in grafieken. U kunt nog steeds individuele kleuren hieronder fijn afstemmen.',
        customized: 'Aangepast',
        schemes: {
            DEFAULT: {
                name: 'Standaard',
                description: 'De originele GeoPulse kleuren.'
            },
            RED_GREEN_SAFE: {
                name: 'Rood-groen veilig',
                description: 'Voor deuteranopie en protanopie. Gebruikt blauw en geel in plaats van rood en groen.'
            },
            BLUE_YELLOW_SAFE: {
                name: 'Blauw-geel veilig',
                description: 'Voor tritanopie. Gebruikt rood en tealkleur in plaats van blauw en geel.'
            },
            HIGH_CONTRAST: {
                name: 'Hoge contrast',
                description: 'Sterke, duidelijke kleuren die ook in helderheid verschillen. Werkt voor alle kleurenzientypen.'
            }
        }
    },
    preview: {
        heading: 'Voorbeeld',
        description: 'Hoe paden eruitzien op lichte, donkere en satellietkaarten met uw huidige (niet opgeslagen) keuzes.',
        ariaLabel: 'Kaartpadvoorbeeld op lichte, donkere en satellietachtergronden',
        backgrounds: {
            light: 'Lichte kaart',
            dark: 'Donkere kaart',
            satellite: 'Satelliet'
        },
        simulate: {
            label: 'Simuleren',
            none: 'Normaal zicht',
            deuteranopia: 'Deuteranopie',
            protanopia: 'Protanopie',
            tritanopia: 'Tritanopie'
        }
    },
    paths: {
        heading: 'Paden',
        description: 'Kleuren en lijnstijl die worden gebruikt om reizen op de kaart te tekenen.',
        followScheme: 'Uit kleurenschema',
        resetColor: 'Gebruik schemakleur',
        defaultPathColor: {
            title: 'Padkleur',
            description: 'Kleur die wordt gebruikt voor reislijnen die niet geselecteerd zijn.',
            details: 'Laat ongewijzigd om het kleurenschema te volgen. Wanneer een meerdaags bereik per dag is gekleurd, worden de dagkleuren in plaats daarvan gebruikt.'
        },
        activePathColor: {
            title: 'Geselecteerde reiskleur',
            description: 'Kleur die wordt gebruikt voor de gemarkeerde lijn wanneer u een reis selecteert.',
            details: 'Laat ongewijzigd om het kleurenschema te volgen. Rijreizen gebruiken snelheidskleuren tenzij snelheidskleuren zijn uitgeschakeld.'
        },
        pathWidth: {
            title: 'Lijnbreedte',
            description: 'Dikte van reislijnen. De geselecteerde reis wordt iets dikker getekend.',
            details: 'Dikkere lijnen zijn makkelijker te zien op drukke of satellietkaarten.'
        },
        outline: {
            title: 'Lijncontour',
            description: 'Teken een contrasterende rand rond reislijnen.',
            details: 'Houdt lijnen zichtbaar op elke achtergrond, ongeacht hun kleur.'
        },
        widthLabels: {
            thin: 'Dun',
            default: 'Standaard',
            thick: 'Dik'
        }
    },
    speedBands: {
        heading: 'Rijsnelheidkleuren',
        description: 'Een geselecteerde rijreis wordt per snelheid gekleurd zodat u langzame en snelle secties kunt herkennen.',
        palette: {
            title: 'Snelheidskleuren',
            description: 'Kleuren voor langzame, gemiddelde en snelle secties van een rijreis.',
            details: 'Langzaam is onder {slow} {unit}, gemiddeld is {slow}–{medium} {unit}, snel is boven {medium} {unit}. Schakel uit om rijreizen in de geselecteerde reiskleur te tekenen.',
            followScheme: 'Volg kleurenschema ({name})'
        },
        options: {
            DEFAULT: 'Rood / amber / groen',
            RED_GREEN_SAFE: 'Violet / lichtblauw / geel',
            BLUE_YELLOW_SAFE: 'Rood / zalm / tealkleur',
            HIGH_CONTRAST: 'Blauw / roze / goud',
            OFF: 'Uit (één kleur)'
        }
    },
    days: {
        heading: 'Meerdaagse reeksen',
        description: 'Wanneer u meer dan één dag bekijkt, kan elke dag een eigen padkleur krijgen.',
        pathColorMode: {
            title: 'Paden per dag kleuren',
            description: 'Teken elke dag van een meerdaags bereik in zijn eigen kleur, overeenkomend met de stippen op de tijdlijndagkoppen.',
            details: 'U kunt dit ook op de kaart inschakelen met de kalenderknop. Korte reeksen krijgen een duidelijke kleur per dag; langere reeksen lopen van oudste naar nieuwste langs de heatmapkleuren. Kleuren die te dicht bij de geselecteerde reiskleur liggen, worden overgeslagen.'
        },
        dayPalette: {
            title: 'Dagkleuren',
            description: 'Kleuren die worden gebruikt voor de dagen van een meerdaags bereik.',
            details: 'Okabe-Ito, Muted en Light en dark blijven herkenbaar voor veelvoorkomende kleurenzientypen. Light en dark wisselt ook helderheid af, zodat opeenvolgende dagen ook in grijswaarden verschillen.',
            followScheme: 'Volg kleurenschema ({name})'
        },
        options: {
            CLASSIC: 'Klassiek (7 kleuren)',
            OKABE_ITO: 'Okabe-Ito (7 kleuren)',
            TOL_MUTED: 'Gedempt (7 kleuren)',
            CONTRAST: 'Licht en donker (7 kleuren)'
        }
    },
    heatmap: {
        heading: 'Heatmap',
        description: 'Kleuren die worden gebruikt voor de heatmap op de tijdlijnkaart en in samenvattingen.',
        gradient: {
            title: 'Heatmapkleuren',
            description: 'Verloop van minder naar meer bezoeken.',
            details: 'Viridis en Cividis blijven leesbaar voor alle kleurenzientypen en in grijswaarden.',
            followScheme: 'Volg kleurenschema ({name})'
        },
        options: {
            CLASSIC: 'Klassiek (blauw naar rood)',
            VIRIDIS: 'Viridis (paars naar geel)',
            CIVIDIS: 'Cividis (donkerblauw naar geel)'
        }
    },
    map: {
        heading: 'Kaart',
        description: 'Extra informatie die op elke kaart wordt getoond.',
        scaleBar: {
            title: 'Schaalbalk',
            description: 'Toon een afstandsschaal in de linkerbovenhoek van kaarten.',
            details: 'Gebruikt kilometers of mijlen, afhankelijk van uw afstandseenheidinstelling.'
        }
    },
    resetToDefaults: 'Reset naar standaard',
    saveChanges: 'Wijzigingen opslaan'
}

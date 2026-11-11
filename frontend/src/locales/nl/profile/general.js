/**
 * General tab (ProfileTab.vue): identity, regional preferences, and the default landing page.
 *
 * EN values are verbatim from the previous literals -- profileTabsDirtyState.test.js asserts
 * 'General', 'Profile', 'Regional preferences' and 'Navigation' exactly.
 *
 * The destination picker reuses the `nav.items.*` keys rather than restating the names: the entries
 * are the same destinations the navigation shows, so a shared key keeps the two in step. Only
 * 'Custom URL...' is specific to this picker.
 *
 * Timezone city names are deliberately NOT in the catalog -- they are IANA identifiers shown in Latin
 * script by convention, and translating them would make the stored value harder to match.
 */
export default {
    title: 'Algemeen',
    description: 'Beheer uw identiteit en alledaagse weergavevoorkeuren.',
    profile: {
        heading: 'Profiel',
        description: 'Kies de naam en afbeelding die in GeoPulse worden getoond.',
        fullName: {
            title: 'Volledige naam',
            description: 'De naam die op uw account wordt getoond.',
            placeholder: 'Voer uw volledige naam in'
        },
        image: {
            heading: 'Profielafbeelding',
            optional: 'Optioneel',
            description: 'Pas de afbeelding aan die voor kaartmarkeringen wordt gebruikt.',
            upload: 'Aangepaste avatar uploaden',
            formats: 'PNG, JPEG of WEBP; automatisch aangepast vóór upload.',
            selected: 'Geselecteerd: {name}',
            chooseAria: 'Profielafbeelding {index} kiezen'
        }
    },
    regional: {
        heading: 'Regionale voorkeuren',
        description: 'Controleer tijdzone, indelingen en meeteenheden.',
        timezone: {
            title: 'Tijdzone',
            description: 'Wordt gebruikt voor datums, tijden en statistieken.',
            placeholder: 'Selecteer uw tijdzone'
        },
        dateFormat: {
            title: 'Datumindeling',
            description: 'Kies hoe numerieke datums verschijnen.',
            details: 'URL-datumparameters blijven een stabiele ISO-indeling gebruiken.',
            placeholder: 'Selecteer uw voorkeursdatumindeling'
        },
        timeFormat: {
            title: 'Tijdindeling',
            description: 'Kies of tijden een 12- of 24-uursklok gebruiken.',
            placeholder: 'Selecteer uw voorkeurstijdindeling'
        },
        timeDisplayMode: {
            title: 'Tijdlijn-tijdzone',
            description: 'Tijdlijn- en GPS-data tijden tonen in uw profieltijdzone, of in de lokale tijd waar elke plaats werd bezocht.',
            details: 'Dagen worden nog steeds gegroepeerd per uw profieltijdzone. De lokale tijdzone komt van de dichtstbijzijnde stad, dus tijden nabij tijdzonegrenzen kunnen benaderend zijn. Beweeg over een tijd om te zien waar de tijdzone vandaan komt.',
            unavailable: 'Lokale tijd is niet beschikbaar omdat GeoNames-stadgegevens niet op deze server zijn geladen. Tijden worden getoond in uw profieltijdzone.'
        },
        distanceUnit: {
            title: 'Afstandseenheid',
            description: 'Bepaalt weergegeven afstanden en snelheden.',
            placeholder: 'Selecteer uw afstandseenheid'
        },
        temperatureUnit: {
            title: 'Temperatureenheid',
            description: 'Bepaalt temperaturen die in weergaven worden getoond.',
            placeholder: 'Selecteer uw temperatuureenheid'
        }
    },
    navigation: {
        heading: 'Navigatie',
        description: 'Kies waar GeoPulse na aanmelding naartoe opent.',
        homePage: {
            title: 'Standaard startpagina',
            description: 'Selecteer de eerste pagina die na aanmelding wordt getoond.',
            details: "Wis de selectie om het standaardgedrag van GeoPulse te gebruiken. Aangepaste bestemmingen moeten interne paden zijn die beginnen met /.",
            placeholder: 'Selecteer uw standaardpagina',
            customOption: 'Aangepaste URL...',
            customLabel: 'Aangepast intern pad',
            customPlaceholder: '/app/uw-aangepaste-pagina'
        }
    },
    dateFormatOptions: {
        dmy: 'DD/MM/JJJJ (Europese)',
        dmyDot: 'DD.MM.JJJJ (Duitse)',
        mdy: 'MM/DD/JJJJ (VS)',
        ymd: 'JJJJ-MM-DD (ISO)'
    },
    timeFormatOptions: {
        h24: '24-uurs (13:45)',
        h12: '12-uurs (1:45 PM)'
    },
    timeDisplayModeOptions: {
        profile: 'Profieltijdzone',
        location: 'Lokale tijd op elke plaats'
    },
    distanceUnitOptions: {
        kilometers: 'Kilometers (km, m)',
        miles: 'Mijl (mi, ft)'
    },
    temperatureUnitOptions: {
        celsius: 'Celsius (°C)',
        fahrenheit: 'Fahrenheit (°F)'
    },
    actions: {
        reset: 'Resetten',
        save: 'Wijzigingen opslaan'
    },
    errors: {
        fullNameRequired: 'Volledige naam is verplicht',
        fullNameTooShort: 'Volledige naam moet minstens 2 tekens bevatten',
        customUrlRequired: 'Aangepaste URL is verplicht',
        customUrlInternal: 'URL moet een intern pad zijn dat begint met /',
        customUrlInvalid: 'Ongeldig URL-formaat',
        customUrlTooLong: 'URL is te lang (max 1000 tekens)',
        imageLoad: 'Afbeelding laden is mislukt',
        imageTooLarge: 'Afbeelding is te groot na compressie. Probeer een andere afbeelding.',
        imageFormat: 'Niet-ondersteund afbeeldingsformaat. Gebruik PNG, JPEG of WEBP.',
        imageUnsupported: 'Afbeeldingsverwerking wordt niet ondersteund in deze browser',
        imageProcess: 'Geselecteerde afbeelding kan niet worden verwerkt',
        avatarTooLarge: 'Avatar is te groot na compressie'
    }
}

/**
 * Timeline & Map tab.
 *
 * EN values verbatim from the previous literals. Note `mapMatching.descriptionUnavailable` starts with
 * 'Unavailable', which profileTabsDirtyState.test.js asserts on.
 */
export default {
    title: 'Tijdlijn & Kaart',
    description: 'Pas tijdlijn- en kaartweergave aan zonder tijdlijngegevens te herbouwen.',
    behavior: {
        heading: 'Tijdlijn gedrag',
        description: 'Kies wat verschijnt wanneer u de tijdlijn opent en ermee werkt.',
        defaultDateRange: {
            title: 'Standaard datumbereik',
            description: 'Kies het initiële bereik voor Tijdlijn, Dashboard en Tijdlijnrapporten.',
            details: 'Wis de selectie om de app-standaard te gebruiken: Vandaag.',
            placeholder: 'App-standaard gebruiken (Vandaag)'
        },
        unknownActivity: {
            title: 'Onbekende activiteit tonen',
            description: 'Toon datagaps in de hoofd Tijdlijn.',
            details: 'Schakel uit om gapkaarten en datums zonder opgenomen verblijfplaatsen of reizen te verbergen. Tijdlijngegevens en -verwerking blijven ongewijzigd.'
        },
        telemetry: {
            title: 'Huidige locatie telemetrie',
            description: 'Toon telemetriewaarden in het huidige locatiekaartpop-up.',
            details: 'Dit heeft alleen invloed op pop-upzichtbaarheid. Telemetrieopslag en GPS-data tabel blijven ongewijzigd.'
        }
    },
    sources: {
        heading: 'Kaartweergave & bronnen',
        description: 'Kies hoe kaarten worden gerenderd en waar hun visuele gegevens vandaan komen.',
        renderMode: {
            title: 'Kaartrendermodus',
            description: 'Kies de renderer die in de kaartweergaven wordt gebruikt.',
            details: 'Het wisselen van modi behoudt beide aangepaste bron-URL\'s.'
        },
        buildings3d: {
            title: '3D-gebouwen',
            description: 'Toon gebouwvormen op compatibele vectorkaarten.',
            details: 'Alleen beschikbaar voor MapTiler-stijlen met gebouwhoogtegegevens. Gebouwen verschijnen wanneer u inzoomt op straatniveau.'
        },
        rasterTiles: {
            title: 'Aangepaste rastertegels',
            description: 'Optionele bron die wordt gebruikt wanneer Raster-modus is geselecteerd.',
            details: "De URL-sjabloon moet HTTP of HTTPS gebruiken en {'{z}'}, {'{x}'} en {'{y}'} bevatten. Laat leeg om OpenStreetMap te gebruiken.",
            ariaLabel: 'Aangepaste rastertegel-URL'
        },
        vectorStyle: {
            title: 'Aangepaste vectorstijl',
            description: 'Optionele stijl die wordt gebruikt wanneer Vector-modus is geselecteerd.',
            details: 'Voer een HTTP- of HTTPS-stijl JSON-URL in. Laat leeg om OpenFreeMap te gebruiken.',
            ariaLabel: 'Aangepaste vectorstijl-URL'
        }
    },
    processing: {
        heading: 'Kaartverwerking',
        description: 'Controleer optionele routematching en weergaveprestaties.',
        mapMatching: {
            title: 'Map matching',
            descriptionAvailable: 'Toon gecachete afgestemde reisgeometrie wanneer beschikbaar',
            descriptionUnavailable: 'Niet beschikbaar totdat een beheerder een Valhalla-service configureert.',
            detailsAvailable: 'Vereist een geconfigureerde Valhalla-instantie. Ruwe GPS-data, exporten en tijdlijndetectie blijven ongewijzigd.',
            detailsUnavailable: 'Een beheerder moet Map Matching inschakelen en Valhalla configureren voordat u dit kunt inschakelen.'
        },
        rawGps: {
            title: 'Ruwe GPS tonen voor',
            description: 'Behoud het originele GPS-pad voor geselecteerde bewegingstypen.',
            details: 'Matching kan nog steeds op de achtergrond worden uitgevoerd, maar afgestemde geometrie, voortgang, vergelijkingsbediening en details blijven verborgen.',
            placeholder: 'Gebruik afgestemde routes voor alle ondersteunde typen',
            ariaLabel: 'Bewegingstypen die ruwe GPS tonen'
        },
        simplification: {
            title: 'Padversimpeling',
            description: 'Verklein het aantal punten dat voor een reis wordt getekend.',
            details: 'Gebruikt het Douglas-Peucker-algoritme om paden te versimpelen zonder uw tijdlijngegevens te beïnvloeden.'
        },
        tolerance: {
            title: 'Versimpelingstolerantie',
            description: 'Stel de afstandsdrempel in die wordt gebruikt om paden te versimpelen.',
            detailLowerLabel: 'Lagere waarden (1-10m)',
            detailLowerValue: 'Meer detail behouden, meer punten tonen',
            detailHigherLabel: 'Hogere waarden (20-100m)',
            detailHigherValue: 'Meer compressie, minder punten tonen'
        },
        maxPoints: {
            title: 'Maximale punten',
            description: 'Limiteer hoeveel GPS-punten er in een pad worden weergegeven.',
            details: 'Als een pad deze limiet overschrijdt, wordt de tolerantie automatisch verhoogd. Stel 0 in voor geen limiet.'
        },
        adaptive: {
            title: 'Adaptieve versimpeling',
            description: 'Pas versimpeling automatisch aan op basis van reislengte.',
            details: 'Langere reizen gebruiken hogere tolerantie voor betere prestaties; kortere reizen behouden meer detail.'
        }
    },
    toggleAria: {
        mapMatching: 'Map matching inschakelen'
    },
    // Trailing units appended to slider values by SliderControl.
    pointsSuffix: ' punten',
    toleranceLabels: {
        low: '1m (Hoge detail)',
        mid: '15m (Gebalanceerd)',
        high: '50m (Hoge compressie)'
    },
    maxPointsLabels: {
        none: '0 (Geen limiet)',
        balanced: '100 (Gebalanceerd)',
        high: '500 (Hoge limiet)'
    },
    dateRangeOptions: {
        today: 'Vandaag',
        yesterday: 'Gisteren',
        lastWeek: 'Laatste 7 dagen',
        lastMonth: 'Laatste 30 dagen'
    },
    renderModeOptions: {
        vector: 'Vector (MapLibre)',
        raster: 'Raster (Leaflet)'
    },
    resetToDefaults: 'Reset naar standaard',
    saveChanges: 'Wijzigingen opslaan',
    validation: {
        tilePlaceholders: "URL moet {'{z}'}, {'{x}'} en {'{y}'} plaatsaanduidingen bevatten",
        protocol: 'URL moet HTTP- of HTTPS-protocol gebruiken',
        invalidProtocol: 'Ongeldig URL-protocol',
        invalidFormat: 'Ongeldig URL-formaat',
        styleEndpoint: 'URL moet naar een stijl JSON-eindpunt wijzen'
    }
}

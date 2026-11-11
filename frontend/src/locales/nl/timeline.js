/**
 * Timeline page, its preferences, labels, reports, and the timeline components.
 *
 * EN values are copied verbatim from the literals they replace: the test suite is pinned to English
 * and asserts exact copy, so these files are a snapshot rather than a chance to reword.
 */
export default {
    edit: {
        groupStructureHint: 'Om tijden te wijzigen, splits, samenvoegen of deze activiteiten te verwijderen, maak de correctiegroep in Uw wijzigingen ongedaan en voeg deze opnieuw toe.',
        reviewChanges: 'Wijzigingen bekijken',
        reviewFirst: 'Stap 1 van 2 · Maak uw wijzigingen, ga dan verder naar controle.',
        reviewReady: 'Stap 2 van 2 · Controleer het resultaat hieronder, sla dan uw wijzigingen op.',
        uncertainTime: 'Deze tijd wordt overgeslagen of herhaald bij klokwijzigingen. Kies een eenduidige tijd.',
        validTimesRequired: 'Voer een volledige, geldige datum en tijd in.',
        savedValue: 'Opgeslagen',
        placeRequired: 'Kies een locatie voordat u beoordeelt.',
        unchanged: 'Wijzig een waarde om op te slaan.',
        allVisits: 'Wijzigt alle bezoeken aan deze locatie',
        changeCount: '1 wijziging | {count} wijzigingen',
        yourChanges: 'Uw wijzigingen',
        previousChanges: 'Vorige wijzigingen',
        retry: 'Opnieuw proberen',
        previousUnavailable: 'Vorige waarde niet beschikbaar',
        noLabel: 'Geen bezoeklabel',
        relatedChange: 'Gerelateerde wijziging die deze periode beïnvloedt',
        undoHint: 'Ongedaan maken verwijdert deze wijziging en bouwt de tijdlijn opnieuw op met de overige wijzigingen. Vervangen wijzigingen worden niet automatisch hersteld.',
        previousEvent: 'Vorige gebeurtenis',
        nextEvent: 'Volgende gebeurtenis',
        removeLabel: 'Label verwijderen',
        tripStart: 'Waar begon de reis?',
        tripEnd: 'Waar eindigde de reis?',
        before: 'Voor',
        after: 'Na — bekijk voordat u opslaat',
        viewChange: 'Wijziging bekijken',
        saveChanges: 'Wijzigingen opslaan',
        discardChanges: 'Uw niet-opgeslagen wijzigingen weggooien?',
        labelRequired: 'Voer een bezoeklabel in, of gebruik Label verwijderen.',
        invalidTimes: 'Het eind moet na de start liggen.',
        chooseResolution: 'Kies wat de verwijderde tijd moet invullen.',
        endpointsRequired: 'Kies de ontbrekende start- en eindlocaties voordat u beoordeelt.',
        undone: 'Wijziging ongedaan gemaakt',
        redone: 'Wijziging hersteld',
        updating: 'Tijdlijn bijwerken…',
        updateFailed: 'Uw wijziging is opgeslagen, maar de tijdlijn kon niet worden bijgewerkt. Open de tijdlijntaak om het probleem te inspecteren.',
        resultHint: 'De getroffen periode is bijgewerkt. Verwijderde of samengevoegde gebeurtenissen blijven toegankelijk in de wijzigingsgeschiedenis.',
        visit: 'Bezoek bewerken…',
        trip: 'Reis bewerken…',
        gap: 'Leemte oplossen…',
        history: 'Tijdlijnwijzigingen',
        editedCount: '{count} tijdlijnwijzigingen',
        details: 'Details',
        moreChanges: 'Meer wijzigingen',
        visitLabel: 'Bezoeklabel',
        visitLabelPlaceholder: 'Bijv. Diner met Alex',
        visitLabelHint: 'Alleen voor dit bezoek getoond. De locatie behoudt de naam "{location}" elders.',
        locationHint: 'Waar dit bezoek plaatsvond. Bezoeklocaties worden gegroepeerd en geteld per locatie.',
        gapStayQuestion: 'Waar was u tijdens deze leemte?',
        splitStayQuestion: 'Waar was u na dit tijdstip?',
        splitStayHint: 'Het bezoek wordt twee bezoeklocaties. Het eerste deel blijft bij {location}.',
        place: {
            current: 'Huidige locatie',
            selected: 'Geselecteerde locatie',
            unnamed: 'Onbenoemde plaats',
            suggestions: 'Suggesties',
            beforeGap: 'Waar u was vóór de leemte',
            afterGap: 'Waar u was na de leemte',
            search: 'Zoek uw plaatsen',
            searchPlaceholder: 'Een favoriet of een plaats die u hebt bezocht',
            pickOnMap: 'Kies op de kaart',
            hideMap: 'Kaart verbergen',
            mapHint: 'Klik op de kaart of sleep de marker naar waar u was.',
            newPlace: 'Nieuwe plaats',
            newPlaceName: 'Naam van de nieuwe plaats',
            newPlacePlaceholder: 'Bijv. Sportschool',
            useNewPlace: 'Gebruik deze nieuwe plaats',
            usingNewPlace: 'Deze nieuwe plaats is geselecteerd',
            referenceCaption: 'Huidige locatie',
            splitReferenceCaption: 'Waar het bezoek nu is',
            resolving: 'Plaatsen hier opzoeken…',
            lookupFailed: 'Kon plaatsen hier niet opzoeken.',
            searchFailed: 'Kon uw plaatsen niet doorzoeken.',
            source: { favorite: 'Favoriet', known: 'Bezochte plaats', custom: 'Nieuwe plaats' }
        },
        eventType: { STAY: 'Verblijfplaats', TRIP: 'Reis', DATA_GAP: 'Datagap' },
        movementType: 'Bewegingstype',
        start: 'Start',
        end: 'Eind',
        splitAt: 'Splitsen op',
        splitAtRange: 'Kies een tijdstip na {start} en voor {end}, terwijl het bezoek duurt.',
        sharedBoundaryHint: 'Het wijzigen van een grens wijzigt ook het aangrenzende evenement, zodat de tijdlijn continu blijft.',
        mergeDirection: 'Samenvoegen met',
        removeResolution: 'De verwijderde tijd invullen door',
        preview: 'Voorbeeld',
        apply: 'Toepassen',
        merge: {
            trip: {
                explanation: 'Voegt deze reis samen met een andere reis van hetzelfde type, voor wanneer een korte stop één reis in twee splitste.',
                previous: 'Samenvoegen met vorige reis', next: 'Samenvoegen met volgende reis',
                removes: 'Verwijderd de {duration} stop bij {name} ertussen.'
            },
            stay: {
                explanation: 'Voegt dit bezoek samen met een ander bezoek aan dezelfde plaats, voor wanneer een korte reis één bezoek in twee splitste.',
                previous: 'Samenvoegen met vorig bezoek', next: 'Samenvoegen met volgend bezoek',
                removes: 'Verwijderd de {duration} {name} reis ertussen.'
            },
            adjacent: 'Ze liggen naast elkaar; er wordt verder niets verwijderd.'
        },
        loadingHistory: 'Tijdlijnwijzigingen laden…',
        noHistory: 'Nog geen tijdlijnwijzigingen.',
        historyFailed: 'Kon tijdlijnwijzigingen niet laden.',
        unapplied: 'Niet momenteel toegepast; wordt opnieuw geprobeerd na regeneratie.',
        revert: 'Ongedaan',
        restore: 'Herstellen',
        undo: 'Ongedaan',
        undoFailed: 'Kon deze tijdlijnwijziging niet ongedaan maken.',
        previewFailed: 'Kon deze correctie niet in voorbeeld tonen.',
        saveFailed: 'Kon deze correctie niet opslaan.',
        saved: 'Wijziging opgeslagen',
        savedDetail: 'De tijdlijn is bijgewerkt.',
        header: { stay: 'Verblijfplaats bewerken', trip: 'Reis bewerken', dataGap: 'Datagap oplossen' },
        action: {
            location: 'Locatie', visitLabel: 'Bezoeklabel', renameLocation: 'Overal locatie hernoemen', times: 'Tijd',
            movement: 'Bewegingstype', splitStay: 'Verblijf splitsen', splitTrip: 'Stop toevoegen',
            merge: 'Samenvoegen', remove: 'Verwijderen', gapToStay: 'Converteren naar verblijfplaats', gapToTrip: 'Converteren naar reis'
        },
        actionHint: {
            location: 'Waar dit bezoek plaatsvond',
            visitLabel: 'Een titel voor dit bezoek alleen, bijvoorbeeld "Diner met Alex"',
            renameLocation: 'Wijzigt de naam van deze locatie voor al haar bezoeklocaties',
            splitTrip: 'Kies de stop op de routekaart',
            mergeTrip: 'Samenvoegen met een reis die een korte stop heeft afgesplitst',
            mergeStay: 'Samenvoegen met een bezoek dat een korte reis heeft afgesplitst',
            times: 'Wanneer het begon en eindigde',
            movement: 'Hoe u reisde',
            splitStay: 'U bent onderweg ergens anders heen geweest',
            remove: 'Het is niet gebeurd; kies wat de tijd invult',
            gapToStay: 'U bleef op één plaats gedurende de leemte',
            gapToTrip: 'U reisde gedurende de leemte'
        },
        resolution: { merge: 'Compatibele buren samenvoegen', previous: 'Het vorige evenement verlengen', next: 'Het volgende evenement verlengen', gap: 'Een datagap achterlaten' },
        kind: {
            SET_MOVEMENT_TYPE: 'Bewegingstype', SET_PLACE: 'Locatie', SET_VISIT_NAME: 'Bezoeklabel',
            FORCE_STAY: 'Gedwongen verblijfplaats', FORCE_TRIP: 'Gedwongen reis', SET_TIMES: 'Tijd', SPLIT_STAY: 'Verblijf splitsen',
            MERGE: 'Samenvoegen', REMOVE_EVENT: 'Verwijderde gebeurtenis'
        },
        status: { ACTIVE: 'Actief', SUPERSEDED: 'Vervangen', REVERTED: 'Teruggedraaid' }
    },
    locationEdit: {
        editFavoriteHeader: 'Favoriete locatie bewerken',
        renameStayHeader: 'Verblijfplaatslocatie hernoemen',
        // The rename updates the place itself, so every stay there changes, not only stays sharing its name.
        renameStayMessage: 'Hernoemen van "{name}" werkt elk verblijf bij op deze plaats bijwerken. Doorgaan?'
    },
    notes: {
        openSingle: 'Notitie openen',
        openMultiple: '{count} notities openen'
    },
    photos: {
        openSingle: 'Foto openen',
        openMultiple: '{count} foto\'s openen',
        fallbackAlt: 'Foto'
    },
    dataGap: {
        subtitle: 'Datagap - Onbekende activiteit',
        endTimeLabel: 'Eindtijd:',
        durationLabel: 'Duur:',
        totalDuration: 'Totale duur:',
        onThisDay: 'Op deze dag:',
        convertTooltip: 'Deze datagap converteren naar een verblijfplaats',
        convertButton: 'Converteren naar verblijfplaats'
    },
    overnight: {
        continuedFrom: 'Voortgezet vanaf {time}'
    },
    localTime: {
        resolved: 'Lokale tijd in {zone}, vanaf de dichtstbijzijnde stad {city} ({country}, {distance} km afstand)',
        beyondMaxDistance: 'Lokale tijdzone onbekend: de dichtstbijzijnde stad {city} ({country}) is {distance} km verwijderd. Uw profieltijdzone wordt getoond.',
        noGeonamesData: 'Lokale tijdzone onbekend: GeoNames-stadgegevens zijn niet geladen op deze server. Uw profieltijdzone wordt getoond.',
        invalidTimezone: 'Lokale tijdzone onbekend: de dichtstbijzijnde stad {city} ({country}) heeft een onherkenbare tijdzone. Uw profieltijdzone wordt getoond.',
        notProvided: 'Lokale tijdzone niet beschikbaar voor dit item. Uw profieltijdzone wordt getoond.'
    },
    card: {
        showGpsPoints: 'GPS-punten tonen',
        viewAllVisits: 'Alle bezoeklocaties aan deze plaats bekijken',
        renamePlace: 'Plaats hernoemen...',
        manageNoteSingle: 'Notitie beheren...',
        manageNotesMultiple: 'Notities ({count}) beheren...',
        viewNoteSingle: 'Notitie bekijken...',
        viewNotesMultiple: 'Notities ({count}) bekijken...',
        addNote: 'Notitie toevoegen...',
        viewCityDetails: '{city} Details bekijken',
        viewCountryDetails: '{country} Details bekijken',
        exportGpx: 'Exporteren als GPX',
        renameStayAria: 'Verblijfplaats hernoemen',
        renameDisabledDemo: 'Hernoemen is uitgeschakeld in demo-modus',
        resetDisabledDemo: 'Resetten is uitgeschakeld in demo-modus'
    },
    stay: {
        stayedAt: 'Verbleven bij',
        manualIndicator: '(Handmatig)',
        forDuration: 'Gedurende'
    },
    trip: {
        edited: 'Bewerkt',
        transitionTo: 'Overgang naar',
        transitionToNewPlace: 'Overgang naar nieuwe plaats',
        durationLabel: 'Duur:',
        distanceLabel: 'Afstand:',
        movementLabel: 'Beweging:',
        editMovementType: 'Bewegingstype bewerken',
        movementEditDisabledDemo: 'Bewegingstypebewerkingen zijn uitgeschakeld in demo-modus',
        setMovementType: 'Bewegingstype instellen',
        unrecognizedHint: 'Algoritme heeft deze reis niet herkend.',
        changeMovementType: 'Bewegingstype wijzigen...',
        whyThisClassification: 'Waarom deze classificatie?',
        splitTripWithStay: 'Reis splitsen met verblijfplaats...',
        mapMatchingDetails: 'Kaartmatching details...'
    },
    explanation: {
        trigger: {
            stay: 'Waarom deze verblijfplaats? Zie de GPS-punten en hoe het werd bepaald',
            trip: 'Waarom deze reis? Zie de GPS-punten en hoe het werd bepaald',
            dataGap: 'Waarom een datagap? Zie de GPS-punten eromheen en hoe het werd bepaald'
        },
        toggle: {
            stay: 'Waarom deze verblijfplaats?',
            trip: 'Waarom deze reis?',
            dataGap: 'Waarom een datagap?'
        },
        start: {
            FIRST_POINT: 'Begint met het eerste GPS-punt dat er is.',
            AFTER_DATA_GAP: 'Begint met het eerste GPS-punt na een datagap.',
            ARRIVAL: 'Begint waar u stopt met bewegen: {points} punten langzamer dan {stopSpeedKmh} km/u gedurende {stopSeconds}.',
            SPARSE_STOP: 'Begint waar u stopte: het volgende GPS-punt kwam {gapSeconds} later, slechts {distanceMeters} verderop.',
            LEFT_STAY: 'Begint wanneer een punt op {distanceMeters} van de verblijfplaats werd opgenomen (verblijfradius {radiusMeters}).',
            SHORT_STOP: 'Begint met een stop van {stopSeconds}, te kort om een verblijfplaats te zijn ({minMinutes} min nodig), dus telt als deel van deze reis.',
            WATER_TRANSITION_TO_WATER: 'Begint waar u van land op water overging.',
            WATER_TRANSITION_TO_LAND: 'Begint waar u van water op land overging.',
            INFERRED_DURING_GAP: 'Er zijn geen GPS-punten hier gedurende {gapSeconds}, maar de punten vóór en na zijn slechts {distanceMeters} uit elkaar, dus u bent waarschijnlijk hier gebleven.',
            INFERRED_TRIP_DURING_GAP: 'Er zijn geen GPS-punten hier gedurende {gapSeconds}; de punten vóór en na zijn {distanceMeters} uit elkaar, dus u bent gegarandeerd gereisd.',
            CONTINUITY: 'Geen reis gedetecteerd tussen de verblijfplaatsen vóór en na, die {distanceMeters} uit elkaar liggen, dus deze reis verbindt ze.',
            NO_POINTS: 'Geen GPS-punten gedurende {gapSeconds}. Breuken langer dan {thresholdSeconds} worden getoond als datagaps.',
            NO_POINTS_SINCE_LAST: 'Geen GPS-punten meer sinds het laatste. Een breuk langer dan {thresholdSeconds} wordt getoond als datagap totdat nieuwe punten arriveren.',
            STOPPED_BEFORE_GAP: 'Begint waar u stopte, vlak voor een datagap.',
            MANUAL_SPLIT: 'U hebt deze verblijfplaats toegevoegd door een reis te splitsen.',
            MANUAL_RECONSTRUCTION: "Gemaakt voor het exacte interval dat u invoerde tijdens reconstructie.",
            MANUAL_GAP_STAY: 'U hebt een datagap omgezet in deze verblijfplaats.'
        },
        note: {
            CONFIRMED: 'Telt als verblijfplaats: de punten bleven binnen {radiusMeters} gedurende ten minste {minMinutes} min.',
            FAVORITE_AREA: 'Punten buiten de verblijfradius werden in deze verblijfplaats gehouden omdat ze zich in hetzelfde favoriete gebied bevinden.',
            CONTINUED_OVER_GAP: 'Gaat door over een {gapSeconds} datagap: de punten vóór en daarna bevinden zich op dezelfde plaats.',
            MERGED: '{stays} verblijfplaatsen op dezelfde plaats zijn samengevoegd tot deze.',
            JOINED_SEGMENTS: '{segments} delen van beweging zijn samengevoegd tot deze reis.',
            SHORT_STOP: 'Omvat een stop van {stopSeconds}, te kort om een verblijfplaats te zijn ({minMinutes} min nodig).',
            MANUAL_SPLIT: 'Gewijzigd door uw handmatige reisopdeling.',
            MANUAL_RECONSTRUCTION: "Gemaakt voor het exacte interval dat u invoerde tijdens reconstructie.",
            MANUAL_GAP_STAY: 'Uitgebreid door uw handmatige datagapconversie.'
        },
        end: {
            MOVED_AWAY: 'Eindigt wanneer een punt {distanceMeters} verderop werd opgenomen (verblijfradius {radiusMeters}).',
            ARRIVAL: 'Eindigt waar u stopt met bewegen: {points} punten langzamer dan {stopSpeedKmh} km/u gedurende {stopSeconds}.',
            SPARSE_STOP: 'Eindigt waar u stopte: het volgende GPS-punt kwam {gapSeconds} later, slechts {distanceMeters} verderop.',
            DATA_GAP: 'Eindigt bij een datagap van {gapSeconds}.',
            LAST_POINT: 'Eindigt bij het laatste GPS-punt dat er is, dus het is mogelijk nog aan de gang.',
            WATER_TRANSITION_TO_WATER: 'Eindigt waar u van land op water overging.',
            WATER_TRANSITION_TO_LAND: 'Eindigt waar u van water op land overging.',
            STOPPED_BEFORE_GAP: 'Eindigt waar u stopte, vlak voor een datagap.'
        }
    },
    explanationDialog: {
        header: {
            stay: 'Waarom deze verblijfplaats?',
            trip: 'Waarom deze reis?',
            dataGap: 'Waarom een datagap?'
        },
        loading: 'GPS-punten laden...',
        loadFailed: 'Kan de uitleg niet laden.',
        period: '{start} – {end} ({duration})',
        sections: {
            map: 'GPS-punten',
            steps: 'Hoe het werd bepaald',
            filtered: 'Punten die buiten beschouwing gelaten',
            ingestion: 'Punten die nooit zijn opgeslagen'
        },
        legend: {
            used: 'Gebruikt',
            filtered: 'Buiten beschouwing gelaten (onjuist)',
            context: 'Vóór en na',
            deciding: 'Beslissend',
            radius: 'Verblijfradius',
            anchor: 'Opgeslagen plaats'
        },
        mapHint: 'Selecteer een stap om de punten op de kaart te zien. Selecteer een punt voor details.',
        noPoints: 'Er zijn geen opgeslagen GPS-punten voor deze periode.',
        truncated: 'Deze gebeurtenis heeft {total} GPS-punten. De kaart toont {shown} ervan, inclusief elk punt dat buiten beschouwing werd gelaten of een beslissing nam.',
        steps: {
            any: {
                start: {
                    FIRST_POINT: 'Begint op {time} met uw eerste GPS-punt.',
                    AFTER_DATA_GAP: 'Begint op {time}, met het eerste GPS-punt na een onderbreking in uw gegevens.',
                    ARRIVAL: 'Begint op {time}, waar u stopte met bewegen.',
                    SPARSE_STOP: 'Begint op {time}, waar u stopte. Er werden geen GPS-punten opgenomen terwijl u hier was: het volgende kwam {gapSeconds} later, slechts {distanceMeters} verderop.',
                    LEFT_STAY: 'Begon op {time}, toen u {distanceMeters} van de plaats was waar u verbleef, meer dan de {radiusMeters} radius.',
                    SHORT_STOP: 'Begint met een stop van {time} tot {endTime} ({stopSeconds}). Een verblijfplaats heeft minstens {minMinutes} nodig, dus de stop wordt geteld als deel van deze reis.',
                    WATER_TRANSITION_TO_WATER: 'Begint op {time}, waar u van land op water overging.',
                    WATER_TRANSITION_TO_LAND: 'Begint op {time}, waar u van water op land overging.',
                    INFERRED_DURING_GAP: 'Er is geen GPS-data gedurende {gapSeconds}, maar de punten vóór en na de onderbreking liggen slechts {distanceMeters} uit elkaar, dus GeoPulse neemt aan dat u hier verbleef.',
                    INFERRED_TRIP_DURING_GAP: 'Er is geen GPS-data gedurende {gapSeconds}, en de punten vóór en na de onderbreking liggen {distanceMeters} uit elkaar, dus u moet ertussen hebben gereisd.',
                    CONTINUITY: 'Er werd geen beweging opgenomen tussen de verblijfplaatsen vóór en na deze reis, maar ze liggen {distanceMeters} uit elkaar, dus GeoPulse voegt deze reis toe om ze te verbinden. Het heeft geen eigen GPS-punten.',
                    STOPPED_BEFORE_GAP: 'U stopte hier op {time}, vlak voordat uw GPS-data stopte, en was op dezelfde plaats toen deze terugkwam.',
                    MANUAL_SPLIT: 'U hebt dit toegevoegd door een reis te splitsen.',
                    MANUAL_RECONSTRUCTION: "Gemaakt voor het exacte interval dat u invoerde tijdens reconstructie.",
                    MANUAL_GAP_STAY: 'U hebt een datagap omgezet in deze verblijfplaats.',
                    NO_POINTS: 'Geen GPS-punten van {time} tot {endTime} ({gapSeconds}). Breuken langer dan {thresholdSeconds} worden getoond als datagaps.',
                    NO_POINTS_SINCE_LAST: 'Geen GPS-punten meer sinds {time}. Een breuk langer dan {thresholdSeconds} wordt getoond als datagap totdat nieuwe punten arriveren.'
                },
                note: {
                    CONFIRMED: 'Geteld als verblijfplaats vanaf {time}: op dat moment was u al {minMinutes} minuten binnen {radiusMeters} van deze plaats.',
                    FAVORITE_AREA: '{count} punten lagen verder van het centrum dan de verblijfradius, maar ze bevinden zich in hetzelfde favoriete gebied, dus ze bleven deel van deze verblijfplaats.',
                    CONTINUED_OVER_GAP: 'Er is geen GPS-data van {time} tot {endTime} ({gapSeconds}), maar u was vóór en daarna op dezelfde plaats, dus de verblijfplaats gaat door over de onderbreking.',
                    MERGED: '{stays} verblijfplaatsen op deze plaats, dicht bij elkaar in plaats en tijd, zijn samengevoegd tot deze.',
                    JOINED_SEGMENTS: '{segments} opeenvolgende reizen zijn samengevoegd tot deze.',
                    SHORT_STOP: 'Omvat een stop van {stopSeconds}, korter dan de {minMinutes} die een verblijfplaats nodig heeft.',
                    MANUAL_SPLIT: 'Gewijzigd door uw handmatige reisopdeling.',
                    MANUAL_RECONSTRUCTION: "Gemaakt voor het exacte interval dat u invoerde tijdens reconstructie.",
                    MANUAL_GAP_STAY: 'Uitgebreid door uw handmatige datagapconversie.'
                },
                end: {
                    MOVED_AWAY: 'Eindigde op {time}, toen u {distanceMeters} van het centrum was, meer dan de {radiusMeters} verblijfradius.',
                    ARRIVAL: 'Eindigde op {time}, toen u stopte met bewegen.',
                    SPARSE_STOP: 'Eindigde op {time}, waar u stopte: het volgende GPS-punt kwam {gapSeconds} later, slechts {distanceMeters} verderop.',
                    DATA_GAP: 'Eindigde op {time}, toen uw GPS-data stopte voor {gapSeconds}.',
                    LAST_POINT: 'Uw nieuwste GPS-punt is op {time}, dus dit is mogelijk nog aan de gang.',
                    WATER_TRANSITION_TO_WATER: 'Eindigde op {time}, waar u van land op water overging.',
                    WATER_TRANSITION_TO_LAND: 'Eindigde op {time}, waar u van water op land overging.',
                    STOPPED_BEFORE_GAP: 'Eindigde op {time}, waar u stopte vlak voordat uw GPS-data stopte.'
                }
            },
            stay: {
                start: {
                    ARRIVAL: 'U arriveerde op {time}, toen u stopte met bewegen.',
                    MANUAL_SPLIT: 'U hebt dit bezoek toegevoegd door een reis te splitsen.'
                }
            }
        },
        callouts: {
            gapFiltered: '{count} GPS-punten werden gedurende deze tijd opgenomen, maar elk was nauwkeuriger dan {thresholdMeters} ({setting}), dus ze werden buiten beschouwing gelaten en de tijd wordt getoond als een datagap.',
            settingChanged: '{setting} was {builtWith} toen dit werd gebouwd en is {current} nu. Regenerer de tijdlijn om de nieuwe waarde toe te passen.'
        },
        filtered: {
            none: 'Elk GPS-punt van deze gebeurtenis werd gebruikt.',
            some: '{filtered} van {total} GPS-punten werden buiten beschouwing gelaten omdat ze minder nauwkeurig waren dan {thresholdMeters} ({setting}).',
            worst: 'Het minst nauwkeurige was ±{accuracyMeters}.',
            context: '{count} punten vóór of daarna werden ook buiten beschouwing gelaten.',
            off: 'GPS-punten worden niet gefilterd op nauwkeurigheid, omdat {setting} uit staat.'
        },
        ingestion: {
            intro: 'Uw GPS-bronnen laten enkele punten vallen voordat ze worden opgeslagen, dus die punten kunnen hier niet worden getoond:',
            source: '{source}: {rules}',
            accuracy: 'punten minder nauwkeurig dan {value}',
            speed: 'punten sneller dan {value}',
            duplicates: 'herhalingen van dezelfde plaats binnen {value}',
            link: 'Locatiebronnen'
        },
        point: {
            used: 'Gebruikt voor deze gebeurtenis.',
            before: 'Vóór deze gebeurtenis.',
            after: 'Na deze gebeurtenis.',
            filtered: 'Buiten beschouwing gelaten: nauwkeurigheid ±{accuracyMeters}, slechter dan de {thresholdMeters} GPS-nauwkeurigheiddrempel.',
            outsideRadius: '{distanceMeters} vanaf het centrum, buiten de {radiusMeters} radius.',
            accuracy: 'Nauwkeurigheid ±{value}',
            noAccuracy: 'Geen nauwkeurigheid opgenomen',
            speed: 'Snelheid {value}',
            reason: {
                FIRST_POINT: 'Uw eerste GPS-punt.',
                AFTER_DATA_GAP: 'Het eerste punt na een onderbreking in GPS-data.',
                ARRIVAL: 'Deel van de stop waar u arriveerde.',
                SPARSE_STOP: 'Waar u stopte: het volgende punt kwam veel later, dichtbij.',
                LEFT_STAY: 'Startte deze reis: het ligt buiten de verblijfradius.',
                SHORT_STOP: 'Deel van een stop die te kort is om een verblijfplaats te zijn.',
                WATER_TRANSITION: 'Waar u tussen land en water overging.',
                INFERRED_DURING_GAP: 'Naast de onderbreking waar deze verblijfplaats werd aangenomen.',
                INFERRED_TRIP_DURING_GAP: 'Naast de onderbreking waar deze reis werd aangenomen.',
                STOPPED_BEFORE_GAP: 'Waar u stopte vóór de onderbreking.',
                NO_POINTS: 'Naast de onderbreking in GPS-data.',
                NO_POINTS_SINCE_LAST: 'Het laatste punt vóór de onderbreking.',
                CONFIRMED: 'Vanaf hier telde het als een verblijfplaats.',
                FAVORITE_AREA: 'Buiten de verblijfradius maar binnen het favoriete gebied.',
                CONTINUED_OVER_GAP: 'Naast een onderbreking waar de verblijfplaats doorging.',
                MOVED_AWAY: 'Beëindigde de verblijfplaats: het ligt buiten de verblijfradius.',
                DATA_GAP: 'Naast een onderbreking in GPS-data.',
                LAST_POINT: 'Uw nieuwste GPS-punt.'
            }
        },
        settingsUsed: 'Instellingen',
        changeSettings: 'Tijdlijnvoorkeuren',
        whyClassification: 'Waarom dit bewegingstype?',
        close: 'Sluiten',
        on: 'aan',
        off: 'uit',
        minutes: '{value} min'
    },
    container: {
        unknownActivityHidden: 'Geen opgenomen verblijfplaatsen of reizen in deze periode. Onbekende activiteit is verborgen.',
        displaySettings: 'Tijdlijnweergave-instellingen',
        noData: 'Geen tijdlijn voor het gegeven datumbereik.',
        showingItems: '{shown} van {total} items tonen.',
        loadMore: 'Meer laden',
        viewAllInReports: 'Of alles bekijken in Tijdlijnrapporten',
        viewPeriodAria: '{name} periode bekijken',
        showOnlyDayOnMap: 'Alleen deze dag op de kaart tonen',
        showAllDaysOnMap: 'Alle dagen op de kaart tonen',
        exportStartedTitle: 'Export gestart',
        tripExportingDetail: 'Reis wordt geëxporteerd als GPX',
        stayExportingDetail: 'Verblijfplaats wordt geëxporteerd als GPX',
        exportFailedTitle: 'Export mislukt',
        tripExportFailedDetail: 'Reis exporteren is mislukt',
        stayExportFailedDetail: 'Verblijfplaats exporteren is mislukt'
    },
    focusedDay: {
        showing: '{day} tonen',
        showAll: 'Alle dagen op de kaart tonen'
    },
    splitLayout: {
        defaultCollapsedLabel: 'Tijdlijn',
        defaultExpandedLabel: 'Bewegingstijdlijn',
        dayNavigationAriaLabel: 'Tijdlijn dagnavigatie',
        previousDay: 'Vorige dag',
        nextDay: 'Volgende dag',
        show: '{label} tonen',
        collapse: '{label} inklappen'
    },
    largeDataset: {
        title: 'Groot Datumbereik Geselecteerd',
        itemsCount: 'Dit bereik bevat {count} items',
        breakdown: '({stays} verblijfplaatsen, {trips} reizen)',
        breakdownWithGaps: '({stays} verblijfplaatsen, {trips} reizen, {dataGaps} datagaps)',
        recommendation: 'De tijdlijnweergave werkt het beste voor dagelijkse/wekelijkse bladering. Voor het analyseren van grote perioden, gebruik Tijdlijnrapporten:',
        openReports: 'Tijdlijnrapporten openen',
        orViewRecent: 'Of bekijk recente data:',
        last7Days: 'Laatste 7 dagen',
        last30Days: 'Laatste 30 dagen',
        loadAnyway: 'Toch laden (kan prestaties beïnvloeden)',
        continueWithRange: 'Doorgaan met huidig bereik'
    },
    notesViewer: {
        header: 'Notities',
        empty: 'Geen notities voor dit tijdlijnitem.',
        editNote: 'Notitie bewerken',
        deleteNote: 'Notitie verwijderen',
        openInMemos: 'Open in Memos',
        truncatedNotice: 'Deze Memos-notitie is groot, dus GeoPulse toont een ingekort voorbeeld.',
        deleteConfirm: 'Deze GeoPulse-notitie verwijderen?',
        cancel: 'Annuleren',
        delete: 'Verwijderen',
        close: 'Sluiten',
        locationSources: {
            explicit: 'Geotagged',
            derivedStay: 'Verblijfplaatslocatie',
            derivedTripGps: 'Reis GPS',
            derivedTripInterpolated: 'Reisschatting'
        },
        toasts: {
            deletedTitle: 'Notitie verwijderd',
            deletedDetail: 'GeoPulse-notitie is verwijderd',
            deleteFailedTitle: 'Verwijderen mislukt',
            deleteFailedDetail: 'Notitie verwijderen is mislukt'
        }
    },
    noteEditor: {
        titleLabel: 'Titel',
        titlePlaceholder: 'Optionele titel',
        saveToLabel: 'Opslaan bij',
        visibilityLabel: 'Memos-zichtbaarheid',
        noteLabel: 'Notitie',
        contentPlaceholder: 'Schrijf een notitie...',
        contentRequired: 'Notitie-inhoud is verplicht',
        cancel: 'Annuleren',
        editHeader: 'Notitie Bewerken',
        addHeader: 'Notitie Toevoegen',
        update: 'Bijwerken',
        save: 'Opslaan',
        destinations: {
            geopulse: 'GeoPulse',
            memos: 'Memos'
        },
        visibilityOptions: {
            private: 'Privé',
            protected: 'Beschermd',
            public: 'Openbaar'
        },
        toasts: {
            updatedTitle: 'Notitie bijgewerkt',
            savedTitle: 'Notitie opgeslagen',
            updatedDetail: 'GeoPulse-notitie is bijgewerkt',
            savedInMemos: 'Notitie is gemaakt in Memos',
            savedInGeoPulse: 'Notitie is opgeslagen in GeoPulse',
            saveFailedTitle: 'Opslaan mislukt',
            saveFailedDetail: 'Notitie opslaan is mislukt'
        }
    },
    dataGapHelp: {
        toggleAriaLabel: 'Hulpinformatie omschakelen',
        explanation: 'Datagaps ontstaan wanneer GPS-tracking werd onderbroken. U kunt instellen hoe GeoPulse omgaat met gaps in Tijdlijninstellingen.',
        recommendationsHeader: 'Op basis van deze {duration} gap:',
        openSettings: 'Tijdlijninstellingen openen',
        toggleShort: 'Hulp nodig bij gaps?',
        toggleLong: 'Waarom gebeurt dit?',
        tips: {
            lowerThreshold: 'Verlaag de Gap Detection Threshold om kortere gaps te detecteren',
            enableStayInference: 'Schakel Gap Stay Inference in als u op dezelfde locatie was',
            enableTripInference: 'Schakel Gap Trip Inference in als u een lange afstand hebt afgelegd'
        }
    },
    layout: {
        collapsedLabel: 'Tijdlijn',
        expandedLabel: 'Bewegingstijdlijn'
    },
    labels: {
        page: {
            title: 'Tijdlijnlabels',
            subtitleLabelsCount: '{count} label | {count} labels',
            subtitleDaysTagged: '{count} dag getagd | {count} dagen getagd',
            subtitleFallback: 'Geavanceerde tijdlijnlabels (gebruikt door tijdlijnchips en imports)'
        },
        actions: {
            create: 'Label maken',
            timeline: 'Tijdlijn bekijken',
            more: 'Meer'
        },
        help: 'Tijdlijnlabels taggen een periode van uw tijdlijn (zoals een reis of gebeurtenis) zodat het opduikt als een snelle filter en kan worden omgezet in een reisplan.',
        activeBanner: {
            kicker: 'Actief Label',
            since: 'Sinds {date}'
        },
        sourceNames: {
            manual: 'Handmatig',
            ownTracks: 'OwnTracks'
        },
        filters: {
            search: 'Zoek labels...',
            source: {
                all: 'Alle Bronnen'
            },
            linkState: {
                all: 'Alles',
                linked: 'Gekoppeld',
                unlinked: 'Ontkoppeld'
            }
        },
        bulkDelete: 'Verwijderen ({count})',
        badges: {
            active: 'Actief',
            hidden: 'Verborgen',
            visible: 'Zichtbaar'
        },
        now: 'Nu',
        table: {
            pageReport: '{first} tot {last} van {totalRecords} labels tonen',
            columns: {
                label: 'Label',
                period: 'Periode',
                tripPlan: 'Reisplan',
                preset: 'Preset',
                actions: 'Acties'
            },
            notLinked: 'Niet gekoppeld',
            empty: 'Geen tijdlijnlabels gevonden',
            actionsAria: 'Meer acties voor {name}',
            selectAria: '{name} selecteren'
        },
        cards: {
            period: 'Periode',
            duration: 'Duur',
            datePreset: 'Preset',
            linkedTripPlan: 'Gekoppeld Reisplan',
            showingCount: '{count} labels tonen'
        },
        linkedDelete: {
            header: 'Gekoppeld Label Verwijderen',
            linkedToLead: 'Dit tijdlijnlabel is gekoppeld aan reisplan',
            choose: 'Hoe wilt u doorgaan?',
            cancel: 'Annuleren',
            onlyLabel: 'Alleen Label Verwijderen',
            labelAndTripPlan: 'Label & Reisplan Verwijderen'
        }
    },
    page: {
        workspaceBanner: {
            lead: 'Dit datumbereik komt overeen met uw reisplan:',
            openPlanner: 'Planner openen'
        },
        map: {
            noData: 'Geen locatiegegevens voor het geselecteerde datumbereik.'
        },
        favorite: {
            deleteConfirm: {
                header: 'Favoriet verwijderen',
                message: 'Weet u zeker dat u deze favoriete plaats wilt verwijderen?'
            },
            deleteSuccess: "'{name}' is verwijderd",
            deleteFailed: 'Favoriet verwijderen is mislukt'
        },
        toasts: {
            noLocationData: 'Geen locatiegegevens beschikbaar voor het geselecteerde datumbereik.',
            locationFetchFailed: 'Locatiegegevens ophalen is mislukt',
            noTimelineData: 'Geen tijdlijngegevens beschikbaar voor het geselecteerde datumbereik.',
            timelineFetchFailed: 'Tijdlijngegevens ophalen is mislukt',
            timelineLoadFailed: 'Tijdlijngegevens laden is mislukt',
            viewingTag: '{name} bekijken',
            tagPeriod: "De volledige periode voor '{name}' tonen"
        },
        resetDataGapOverride: {
            confirmHeader: 'Datagap resetten',
            confirmMessage: 'Weet u zeker dat u deze datagap wilt resetten naar automatische detectie?',
            successSummary: 'Datagap Reset',
            successDetail: 'De datagap is gereset naar automatische detectie.',
            failedDetail: 'Datagapoverschrijving resetten is mislukt.'
        },
        resetFailedSummary: 'Reset mislukt',
        correctionQueuedSummary: 'Tijdlijnupdate in wachtrij',
        correctionQueuedDetail: 'De correctie is opgeslagen. De tijdlijn wordt bijgewerkt wanneer de lopende regeneratie is voltooid.',
        resetTripSplitOverride: {
            confirmHeader: 'Reisopdeling ongedaan maken',
            confirmMessage: 'Weet u zeker dat u deze handmatige reisopdeling wilt ongedaan maken?',
            successSummary: 'Reisopdeling Ongedaan Gemaakt',
            successDetail: 'De handmatige reisopdeling is ongedaan gemaakt.',
            failedDetail: 'Reisopdeling ongedaan maken is mislukt.'
        },
        reconstruction: {
            updatedSummary: 'Tijdlijn bijgewerkt',
            updatedDetail: 'Uw tijdlijn is bijgewerkt met de gereconstrueerde gegevens.',
            failedSummary: 'Reconstructie mislukt',
            failedDetail: 'Tijdlijngegevens reconstrueren is mislukt.',
            trackingFailedSummary: 'Tracking mislukt'
        }
    },
    preferences: {
        gpsPathSimplification: {
            title: 'GPS-padversimpelinginstellingen',
            description: 'Configureer hoe GPS-paden worden versimpeld om data te verminderen terwijl route-nauwkeurigheid behouden blijft',
            enabled: {
                title: 'Padversimpeling inschakelen',
                description: 'Of GPS-padversimpeling is ingeschakeld voor tijdlijnreizen',
                details: 'Wanneer ingeschakeld, worden reisroutes versimpeld met het Douglas-Peucker-algoritme om het aantal GPS-punten te verminderen terwijl route-nauwkeurigheid behouden blijft'
            },
            tolerance: {
                title: 'Versimpeleingtolerantie',
                description: 'Basistolerantie in meters voor GPS-padversimpeling',
                detailsLowerLabel: 'Lagere waarden',
                detailsLowerValue: 'Meer detail behouden, minder compressie',
                detailsHigherLabel: 'Hogere waarden',
                detailsHigherValue: 'Meer compressie, minder detail',
                labelLow: '1m (Hoge detail)',
                labelMid: '15m (Gebalanceerd)',
                labelHigh: '50m (Hoge compressie)',
                suffix: ' m'
            },
            maxPoints: {
                title: 'Maximale Punten',
                description: 'Maximaal aantal GPS-punten om te behouden in versimpelde paden',
                details: 'Als een versimpeld pad deze limiet nog overschrijdt, wordt de tolerantie automatisch verhoogd tot de limiet is bereikt. Stel 0 in voor geen limiet',
                labelLow: '0 (Geen limiet)',
                labelMid: '100 (Gebalanceerd)',
                labelHigh: '500 (Hoge limiet)',
                suffix: ' punten'
            },
            adaptive: {
                title: 'Adaptieve Versimpeling',
                description: 'Schakelt adaptieve versimpeling in die de tolerantie aanpast op basis van reiseigenschappen',
                details: 'Wanneer ingeschakeld, gebruiken langere reizen hogere tolerantiewaarden voor betere compressie, terwijl kortere reizen hogere nauwkeurigheid behouden met lagere tolerantie'
            }
        },
        stayMerging: {
            title: 'Verblijfpunt samenvoeginstellingen',
            description: 'Configureer hoe nabijgelegen verblijfpunten worden geconsolideerd naar enkele locaties',
            enabled: {
                title: 'Verblijfpunt samenvoeging inschakelen',
                description: 'Of nabijgelegen verblijfpunten die dicht bij elkaar liggen in tijd en afstand moeten worden samengevoegd',
                details: 'Helpt om meerdere GPS-clusters op dezelfde algemene locatie te consolideren naar enkele verblijfpunten'
            },
            maxDistance: {
                title: 'Maximale Samenvoegafstand',
                description: 'Maximale afstand tussen verblijfpunten om ze te overwegen voor samenvoeging',
                detailsLowerLabel: 'Lagere waarden',
                detailsLowerValue: 'Samenvoegen alleen zeer dichtbijgelegen punten',
                detailsHigherLabel: 'Hogere waarden',
                detailsHigherValue: 'Punten verder uit elkaar samenvoegen',
                labelLow: '20m (Precies)',
                labelMid: '150m (Gebalanceerd)',
                labelHigh: '500m (Ruim)',
                suffix: ' m'
            },
            maxTimeGap: {
                title: 'Maximale Tijdsleemte',
                description: 'Maximale tijdsleemte tussen verblijfpunten om ze te overwegen voor samenvoeging',
                detailsLowerLabel: 'Lagere waarden',
                detailsLowerValue: 'Alleen opeenvolgende verblijfplaatsen samenvoegen',
                detailsHigherLabel: 'Hogere waarden',
                detailsHigherValue: 'Verblijfplaatsen samensmelten die door langere leemtes gescheiden zijn',
                labelLow: '1 min (Strikt)',
                labelMid: '10 min (Gebalanceerd)',
                labelHigh: '60 min (Ruim)',
                suffix: ' min'
            }
        },
        gpsGaps: {
            title: 'GPS-gaps detectieinstellingen',
            description: 'Configureer hoe GPS-data gaps worden gedetecteerd en opgenomen in uw tijdlijn',
            threshold: {
                title: 'Data Gap Drempel',
                description: 'Maximale tijdsleemte in seconden die tussen GPS-punten is toegestaan voordat het als een GPS-data gap wordt beschouwd',
                details: 'Wanneer het tijdsverschil tussen twee opeenvolgende GPS-punten deze drempel overschrijdt, wordt een GPS Data Gap-entiteit aangemaakt in plaats van het huidige verblijf of de huidige reis te verlengen. Dit voorkomt kunstmatige verlenging van activiteiten tijdens perioden met ontbrekende GPS-data.',
                labelLow: '5 min (Gevoelig)',
                labelMid: '3 uur (Gebalanceerd)',
                labelHigh: '6 uur (Soepel)',
                suffix: ' seconden'
            },
            minDuration: {
                title: 'Minimale Gap Duur',
                description: 'Minimale duur in seconden voor een gap om te worden opgenomen als een GPS Data Gap',
                details: 'Gaps korter dan deze drempel worden genegeerd om ruis te verminderen. Dit voorkomt dat zeer korte verbindingsproblemen onnodige gap-records creëren.',
                labelLow: '5 min (Strikt)',
                labelMid: '30 min (Gebalanceerd)',
                labelHigh: '2 uur (Soepel)',
                suffix: ' seconden'
            },
            stayInferenceEnabled: {
                title: 'Gap Verblijfplaats Afleiding',
                description: 'Verblijfplaatsen afleiden wanneer GPS-data gaps optreden maar locaties vóór en na hetzelfde zijn',
                detailsWhenLabel: 'Wanneer ingeschakeld',
                detailsWhenValue: 'Als u op een locatie was en de GPS-data stopt, en vervolgens hervat op dezelfde locatie, concludeert het systeem dat u daar verbleef',
                detailsUseCaseLabel: 'Gebruikscasus',
                detailsUseCaseValue: 'Overnachts gaps thuis worden weergegeven als een verblijfplaats in plaats van een datagap'
            },
            stayInferenceMaxGap: {
                title: 'Maximale Gap Duur voor Afleiding',
                description: 'Maximale duur van GPS-data gap om een verblijfplaats af te leiden',
                detailsLowerLabel: 'Lagere waarden',
                detailsLowerValue: 'Alleen verblijfplaatsen afleiden voor kortere gaps (bijv. korte telefoonuitval)',
                detailsHigherLabel: 'Hogere waarden',
                detailsHigherValue: 'Verblijfplaatsen afleiden voor langere gaps (bijv. overnachting, hele dag)',
                labelLow: '1 uur (Strikt)',
                labelMid: '24 uur (Standaard)',
                labelHigh: '72 uur (Soepel)',
                suffix: ' uur'
            },
            tripInferenceEnabled: {
                title: 'Gap Reis Afleiding',
                description: 'Reizen afleiden wanneer GPS-data gaps optreden met langeafstandsbeweging',
                detailsWhenLabel: 'Wanneer ingeschakeld',
                detailsWhenValue: 'Als GPS-data stopt, en vervolgens hervat op een verre locatie, concludeert het systeem dat een reis heeft plaatsgevonden',
                detailsUseCaseLabel: 'Gebruikscasus',
                detailsUseCaseValue: 'Overnachtvluchten of lange ritten waarbij de telefoon uit stond, worden weergegeven als afgeleide reizen in plaats van datagaps',
                detailsClassificationLabel: 'Reisclassificatie',
                detailsClassificationValue: 'Reisomdracht wordt automatisch bepaald op basis van afstand, duur en snelheid (bijv. vlucht, auto, trein)'
            },
            tripInferenceMinDistance: {
                title: 'Minimale Afstand voor Reisafleiding',
                description: 'Minimale afstand tussen GPS-punten om een reis af te leiden tijdens een gap',
                detailsLowerLabel: 'Lagere waarden',
                detailsLowerValue: 'Reizen afleiden voor kortere bewegingen (bijv. 10 km stadsritten)',
                detailsHigherLabel: 'Hogere waarden',
                detailsHigherValue: 'Alleen reizen afleiden voor langere bewegingen (bijv. 100 km+ intercityreis)',
                labelLow: '10 km (Korte reizen)',
                labelMid: '100 km (Standaard)',
                labelHigh: '500 km (Lange afstand)',
                suffix: ' m'
            },
            tripInferenceMinGap: {
                title: 'Minimale Gap Duur voor Reisafleiding',
                description: 'Minimale gapduur om te overwegen voor reisafleiding',
                detailsLowerLabel: 'Lagere waarden',
                detailsLowerValue: 'Reizen afleiden voor korte gaps (bijv. 30 minuten)',
                detailsHigherLabel: 'Hogere waarden',
                detailsHigherValue: 'Alleen reizen afleiden voor langere gaps (bijv. enkele uren)',
                labelLow: '0 uur (Elke gap)',
                labelMid: '1 uur (Standaard)',
                labelHigh: '12 uur (Lange gaps)',
                suffix: ' uur'
            },
            tripInferenceMaxGap: {
                title: 'Maximale Gap Duur voor Reisafleiding',
                description: 'Maximale gapduur om een reis af te leiden (langere gaps worden datagaps)',
                detailsLowerLabel: 'Lagere waarden',
                detailsLowerValue: 'Alleen reizen afleiden voor kortere gaps (bijv. 12 uur)',
                detailsHigherLabel: 'Hogere waarden',
                detailsHigherValue: 'Reizen afleiden voor langere gaps (bijv. multi-daagse reizen)',
                labelLow: '1 uur (Strikt)',
                labelMid: '24 uur (Standaard)',
                labelHigh: '168 uur (Eén week)',
                suffix: ' uur'
            }
        },
        stayPointDetection: {
            title: 'Verblijfplaatsdetectieinstellingen',
            description: "Configureer hoe GPS-data wordt geanalyseerd om plaatsen te identificeren waar u verbleven hebt",
            radius: {
                title: 'Verblijfplaatsdetectieradius',
                description: 'Afstandsdrempel voor het groeperen van GPS-punten in verblijflocaties. Definieert ook de minimale afstand tussen verblijfplaatsen om een reis te creëren',
                detailsLowerLabel: 'Lagere waarden',
                detailsLowerValue: 'Gevoeligere verblijfplaatsdetectie, nabijgelegen locaties afzonderlijk houden',
                detailsHigherLabel: 'Hogere waarden',
                detailsHigherValue: 'Minder gevoelig, nabijgelegen locaties samenvoegen tot enkele verblijfplaatsen',
                labelLow: '10m (Gevoelig)',
                labelMid: '50m (Gebalanceerd)',
                labelHigh: '500m (Conservatief)',
                suffix: ' m'
            },
            minDuration: {
                title: 'Minimale Verblijfsduur',
                description: 'De minimale duur (in minuten) voor een verblijfpunt om te worden bevestigd',
                detailsLowerLabel: 'Lagere waarden',
                detailsLowerValue: 'Te korte verblijfplaatsen kunnen false positives zijn',
                detailsHigherLabel: 'Hogere waarden',
                detailsHigherValue: 'Langere verblijfplaatsen zijn waarschijnlijker echt',
                labelLow: '1 min (Gevoelig)',
                labelMid: '7 min (Gebalanceerd)',
                labelHigh: '60 min (Conservatief)',
                suffix: ' min'
            },
            enhancedFiltering: {
                title: 'Geavanceerde Filtering',
                description: 'Gebruik snelheid en nauwkeurigheidsdata voor betere verblijfplaatsdetectie',
                details: 'Filtert slechte kwaliteit GPS-punten en verbetert tijdlijnnauwkeurigheid'
            },
            velocityThreshold: {
                title: 'Snelheidsdrempel',
                description: 'Maximale snelheid om een punt als stationair te beschouwen',
                detailsLowerLabel: 'Lagere waarden',
                detailsLowerValue: 'Strengere filtering',
                detailsHigherLabel: 'Hogere waarden',
                detailsHigherValue: 'Sta meer beweging binnen verblijfplaatsen toe',
                labelLow: '1 km/u (Strikt)',
                labelMid: '9 km/u (Gebalanceerd)',
                labelHigh: '20 km/u (Soepel)',
                suffix: ' km/u'
            },
            accuracyThreshold: {
                title: 'GPS-nauwkeurigheiddrempel',
                description: 'Minimale GPS-nauwkeurigheid die vereist is om een locatiepunt te gebruiken',
                detailsLowerLabel: 'Lagere waarden',
                detailsLowerValue: 'Nauwkeurigere GPS vereisen',
                detailsHigherLabel: 'Hogere waarden',
                detailsHigherValue: 'Minder nauwkeurige GPS-punten accepteren',
                labelLow: '5m (Hoge nauwkeurigheid)',
                labelMid: '60m (Gebalanceerd)',
                labelHigh: '200m (Lage nauwkeurigheid)',
                suffix: ' m'
            }
        },
        tripClassification: {
            title: 'Reisclassificatie-instellingen',
            description: 'Configureer hoe reizen worden gedetecteerd en geclassificeerd per bewegingstype',
            priorityBanner: {
                title: 'Classificatieprioriteitsvolgorde',
                steps: {
                    flight: 'VLIEGTUIG',
                    boat: 'BOOT',
                    train: 'TREIN',
                    bicycle: 'FIETS',
                    running: 'HARDLOPEN',
                    motorVehicle: 'MOTORVOERTUIG',
                    walk: 'LOPEN',
                    unknown: 'ONBEKEND'
                },
                description: 'Reizen worden geclassificeerd in prioriteitsvolgorde van boven naar beneden. Zodra een match is gevonden, stopt de classificatie. Deze volgorde behandelt water- en snelheidsbewijs conservatief - bijv. over-water vluchten blijven VLIEGTUIG, en overlappende landsnelheden komen overeen met FIETS voordat HARDLOPEN en motorvoertuigen.'
            },
            algorithm: {
                title: 'Reisdetectiealgoritme',
                description: 'Het algoritme dat wordt gebruikt om reizen tussen verblijfplaatsen te identificeren',
                detailsSingleLabel: 'Enkelvoudig',
                detailsSingleValue: 'Altijd één reis tussen verblijfplaatsen',
                detailsMultipleLabel: 'Meerdere',
                detailsMultipleValue: 'Gebaseerd op snelheid één of meer reizen tussen verblijfplaatsen (bijv. AUTO → LOPEN, LOPEN → AUTO, enz.)',
                placeholder: 'Selecteer algoritme',
                optionSingle: 'Enkele reis',
                optionMultiple: 'Meerdere reizen'
            },
            walk: {
                title: 'Lopen',
                subtitle: 'Verplicht transporttype',
                description: 'Detecteert trage beweging te voet (0-8 km/u typisch)',
                maxAvgSpeed: {
                    label: 'Maximale Gemiddelde Snelheid',
                    description: 'Reizen met gemiddelde snelheden boven deze worden geclassificeerd als niet-loopend',
                    labelLow: '3.0 km/u (Traag)',
                    labelMid: '5.5 km/u (Normaal)',
                    labelHigh: '10.0 km/u (Snel)'
                },
                maxMaxSpeed: {
                    label: 'Maximale Pieksnelheid',
                    description: 'Korte snelheidsuitbarstingen boven deze zullen de reis reclassificeren',
                    labelLow: '5.0 km/u (Conservatief)',
                    labelMid: '8.0 km/u (Normaal)',
                    labelHigh: '15.0 km/u (Ruim)'
                }
            },
            bicycle: {
                title: 'Fiets',
                subtitle: 'Optioneel transporttype',
                description: 'Detecteert fietsritten (8-25 km/u typisch bereik). Prioriteitsvolgorde zorgt voor correcte classificatie zelfs met snelheidsoverlap met hardlopen en auto\'s.',
                minAvgSpeed: {
                    label: 'Minimale Gemiddelde Snelheid',
                    description: 'Reizen langzamer dan deze worden geclassificeerd als hardlopen of lopen',
                    labelLow: '5.0 km/u (Traag)',
                    labelMid: '8.0 km/u (Standaard)',
                    labelHigh: '15.0 km/u (Snel)'
                },
                maxAvgSpeed: {
                    label: 'Maximale Gemiddelde Snelheid',
                    description: 'Reizen sneller dan deze worden geclassificeerd als gemotoriseerd transport',
                    labelLow: '15.0 km/u (Traag)',
                    labelMid: '25.0 km/u (Standaard)',
                    labelHigh: '35.0 km/u (E-bike)'
                },
                maxMaxSpeed: {
                    label: 'Maximale Pieksnelheid',
                    description: 'Staat hellingsegmenten, racefietsen of e-bikes toe',
                    labelLow: '20.0 km/u (Stad)',
                    labelMid: '35.0 km/u (Standaard)',
                    labelHigh: '80.0 km/u (Racefiets)'
                }
            },
            running: {
                title: 'Hardlopen',
                subtitle: 'Optioneel transporttype',
                description: 'Detecteert hardloop-/joggingritten (7-14 km/u typisch bereik). Conservatieve drempels scheiden hardlopen van snel lopen en traag fietsen. Wanneer uitgeschakeld, worden hardloopsnelheden opgevangen door FIETS (indien ingeschakeld) of AUTO/ONBEKEND.',
                minAvgSpeed: {
                    label: 'Minimale Gemiddelde Snelheid',
                    description: 'Reizen langzamer dan deze worden geclassificeerd als lopen',
                    labelLow: '5.0 km/u (Traag)',
                    labelMid: '7.0 km/u (Standaard)',
                    labelHigh: '10.0 km/u (Snel)'
                },
                maxAvgSpeed: {
                    label: 'Maximale Gemiddelde Snelheid',
                    description: 'Reizen sneller dan deze worden geclassificeerd als fietsen of gemotoriseerd transport',
                    labelLow: '10.0 km/u (Traag)',
                    labelMid: '14.0 km/u (Standaard)',
                    labelHigh: '18.0 km/u (Snel)'
                },
                maxMaxSpeed: {
                    label: 'Maximale Pieksnelheid',
                    description: 'Staat sprintsegmenten toe terwijl de snelheid onder fietsniveau blijft',
                    labelLow: '12.0 km/u (Traag)',
                    labelMid: '18.0 km/u (Standaard)',
                    labelHigh: '25.0 km/u (Sprint)'
                }
            },
            car: {
                title: 'Motorvoertuig',
                subtitle: 'Auto, motorfiets, of openbaar vervoer label',
                description: 'Detecteert auto-achtige reizen met gedeelde motorvoertuigsnelheidsdrempels. Openbaar Vervoer is een label voor die reizen, niet busdetectie.',
                carLabel: {
                    title: 'Auto-label',
                    description: 'Sta toe dat gedetecteerde motorvoertuigritten worden gelabeld als auto.',
                    ariaEnable: 'Auto-label inschakelen'
                },
                motorcycleLabel: {
                    title: 'Motorfiets-label',
                    description: 'Sta toe dat gedetecteerde motorvoertuigritten worden gelabeld als motorfiets.',
                    ariaEnable: 'Motorfiets-label inschakelen'
                },
                publicTransportLabel: {
                    title: 'Openbaar Vervoer-label',
                    description: 'Sta toe dat gedetecteerde auto-achtige reizen worden gelabeld als openbaar vervoer.',
                    ariaEnable: 'Openbaar vervoer-label inschakelen'
                },
                preferredLabel: {
                    label: 'Voorkeursmotorvoertuiglabel',
                    description: 'Wanneer meerdere labels zijn ingeschakeld, gebruiken gedetecteerde motorvoertuigritten dit label standaard.',
                    placeholder: 'Selecteer voorkeurslabel',
                    optionCar: 'Auto',
                    optionMotorcycle: 'Motorfiets',
                    optionPublicTransport: 'Openbaar Vervoer'
                },
                minAvgSpeed: {
                    label: 'Minimale Gemiddelde Snelheid',
                    description: 'Reizen met gemiddelde snelheden onder deze worden geclassificeerd als lopen, hardlopen, of fietsen als die eerst overeenkomen',
                    labelLow: '5.0 km/u (Gevoelig)',
                    labelMid: '10.0 km/u (Standaard)',
                    labelHigh: '25.0 km/u (Conservatief)'
                },
                minMaxSpeed: {
                    label: 'Minimale Pieksnelheid',
                    description: 'Reizen die nooit deze snelheid bereiken worden niet geclassificeerd als motorvoertuig tenzij gemiddelde snelheid overeenkomt',
                    labelLow: '10.0 km/u (Stad)',
                    labelMid: '15.0 km/u (Standaard)',
                    labelHigh: '50.0 km/u (Snelweg)'
                }
            },
            train: {
                title: 'Trein',
                subtitle: 'Optioneel transporttype',
                description: 'Detecteert treinreizen door hoge snelheid met lage variantie (30-150 km/u, stabiele beweging). Gebruikt snelheidsvariantie om van auto\'s te onderscheiden.',
                minAvgSpeed: {
                    label: 'Minimale Gemiddelde Snelheid',
                    description: 'Scheidt van auto\'s in druk verkeer',
                    labelLow: '20.0 km/u (Stad)',
                    labelMid: '30.0 km/u (Standaard)',
                    labelHigh: '50.0 km/u (Snel)'
                },
                maxAvgSpeed: {
                    label: 'Maximale Gemiddelde Snelheid',
                    description: 'Dekt regionale en intercitytreinen',
                    labelLow: '80.0 km/u (Regionaal)',
                    labelMid: '150.0 km/u (Standaard)',
                    labelHigh: '400.0 km/u (Hogesnelheid)'
                },
                minMaxSpeed: {
                    label: 'Minimale Pieksnelheid (Stationfilter)',
                    description: 'Filtert reizen uit met alleen stationswachtijd (critisch!)',
                    labelLow: '60.0 km/u (Soepel)',
                    labelMid: '80.0 km/u (Standaard)',
                    labelHigh: '120.0 km/u (Strikt)'
                },
                maxMaxSpeed: {
                    label: 'Maximale Pieksnelheid',
                    description: 'Bovengrens voor treinsnelheden',
                    labelLow: '100.0 km/u (Regionaal)',
                    labelMid: '180.0 km/u (Standaard)',
                    labelHigh: '500.0 km/u (Hogesnelheid)'
                },
                maxSpeedVariance: {
                    label: 'Maximale Snelheidsvariantie (Belangrijkste Discriminator)',
                    description: 'Treinen hebben lage variantie (< 15), auto\'s hebben hoge variantie (> 25). Dit is de sleutel om treinen van auto\'s te onderscheiden!',
                    labelLow: '5.0 (Strikt)',
                    labelMid: '15.0 (Standaard)',
                    labelHigh: '30.0 (Soepel)'
                }
            },
            flight: {
                title: 'Vlieg',
                subtitle: 'Optioneel transporttype',
                description: 'Detecteert luchtreizen door zeer hoge snelheden (400+ km/u gemiddeld OF 500+ km/u piek). OR-logica verwerkt verlengde taxi/grondtijd. GPS-ruis boven 1200 km/u wordt automatisch afgewezen.',
                minAvgSpeed: {
                    label: 'Minimale Gemiddelde Snelheid',
                    description: 'Conservatieve standaard voor typische vluchten (inclusief taxi/start/landtijd)',
                    labelLow: '100.0 km/u (Regionaal)',
                    labelMid: '400.0 km/u (Standaard)',
                    labelHigh: '600.0 km/u (Langafstand)'
                },
                minMaxSpeed: {
                    label: 'Minimale Pieksnelheid',
                    description: 'Vangt vluchten met lange taxi/wachtijd (OR-logica met gemiddelde snelheid)',
                    labelLow: '150.0 km/u (Licht vliegtuig)',
                    labelMid: '500.0 km/u (Standaard)',
                    labelHigh: '900.0 km/u (Straalvliegtuig)'
                },
                minDistance: {
                    label: 'Minimale Afstand',
                    description: 'Reizen korter dan deze worden nooit geclassificeerd als vlucht. Voorkomt dat korte reizen met GPS-sprongen worden gedetecteerd als vluchten (0 = geen minimum)',
                    labelLow: '0 km (Standaard)',
                    labelMid: '250 km',
                    labelHigh: '500 km (Strikt)'
                },
                altitudeDetection: {
                    title: 'Hoogtegebaseerde Detectie',
                    description: 'Classificeer ook een reis als vlucht wanneer deze goed boven het start- en eindpunt blijft en sterk stijgt of daalt. Bedoeld voor zweefvliegtuigen, ballonnen en licht vliegtuig dat te traag vliegt voor de snelheidsregels. Heeft GPS-hoogtdata nodig. Skidagen of kabelcarlussen kunnen verkeerd worden gedetecteerd als vluchten.',
                    ariaEnable: 'Hoogtegebaseerde vluchtdetectie inschakelen'
                },
                altitudeMinHeight: {
                    label: 'Minimale Hoogte Boven Start/Eind',
                    description: 'Hoe hoog boven het hogere van zijn start- en eindhoogte de reis ten minste een kwart van zijn tijd moet blijven. Lagere waarden vangen kortere vluchten maar kunnen ritten of auto\'s in heuvelachtig of bergachtig terrein verkeerd detecteren.',
                    labelLow: '50 m (Soepel)',
                    labelMid: '300 m (Standaard)',
                    labelHigh: '5000 m (Strikt)'
                },
                altitudeMinVerticalSpeed: {
                    label: 'Minimale Verticale Snelheid',
                    description: 'Hoe snel de reis moet stijgen of dalen, gemeten over eenminutenvensters (90e percentiel, dus korte pieken tellen niet). Zweefvliegtuigen en ballonnen stijgen of dalen gewoonlijk 1-5 m/s; lagere waarden kunnen ook kabelcars of skiliften matchen.',
                    labelLow: '0.3 m/s (Soepel)',
                    labelMid: '1.2 m/s (Standaard)',
                    labelHigh: '10.0 m/s (Strikt)'
                }
            },
            boat: {
                title: 'Boot',
                subtitle: 'Optioneel transporttype',
                description: 'Detecteert bootreizen door aanhoudend waterbewijs. Snelheid wordt alleen als sanity check gebruikt, niet als bewijs van bootreizen.',
                setup: {
                    requiredTitle: 'Bootsetup vereist',
                    readyTitle: 'Bootsetup gereed',
                    failedTitle: 'Bootsetup mislukt',
                    inProgressTitle: 'Bootsetup bezig',
                    readyPhaseText: 'Gecachet waterbewijs is beschikbaar.',
                    docsLink: 'Offline setup-documentatie',
                    retry: 'Opnieuw proberen',
                    phases: {
                        BOAT_SETUP_IS_READY: 'Bootsetup is gereed',
                        GPS_WATER_EVIDENCE_NEEDS_ENRICHMENT: 'GPS waterbewijs heeft verrijking nodig',
                        WATER_DATASET_IS_NOT_IMPORTED: 'Waterdataset is niet geïmporteerd',
                        STARTING_BOAT_SETUP: 'Bootsetup starten',
                        BOAT_SETUP_COMPLETED: 'Bootsetup voltooid',
                        RECLASSIFYING_EXISTING_TRIPS: 'Bestaande reizen herclassificeren',
                        WATER_DATASET_READY: 'Waterdataset gereed',
                        PREPARING_WATER_DATASET_ARTIFACT: 'Waterdataset-artefact voorbereiden',
                        IMPORTING_WATER_POLYGONS: 'Waterpolygonen importeren',
                        USING_LOCAL_WATER_DATASET_FILE: 'Lokale waterdatasetbestand gebruiken',
                        DOWNLOADING_WATER_DATASET: 'Waterdataset downloaden',
                        GPS_WATER_EVIDENCE_READY: 'GPS waterbewijs gereed',
                        CLASSIFYING_GPS_WATER_EVIDENCE: 'GPS waterbewijs classificeren',
                        WAITING_FOR_WATER_DATASET_IMPORT: 'Wachten op waterdatasetimport',
                        WAITING_FOR_GPS_WATER_EVIDENCE: 'Wachten op GPS waterbewijs',
                        BOAT_SETUP_WORKER_DID_NOT_START: 'Bootsetupwerker is niet gestart',
                        WATER_DATASET_SETUP_FAILED: 'Waterdatasetsetup mislukt',
                        BOAT_SETUP_FAILED: 'Bootsetup mislukt',
                        fallback: 'Bootsetup voorbereiden...'
                    },
                    errorFallback: 'Waterdatasetsetup mislukt. Controleer de offline setup-instructies.'
                },
                minWaterRatio: {
                    label: 'Minimale Waterverhouding',
                    description: 'Aandeel bruikbare reismafstand dat over water moet zijn',
                    labelLow: '10% (Soepel)',
                    labelMid: '60% (Standaard)',
                    labelHigh: '100% (Strikt)'
                },
                minWaterDistance: {
                    label: 'Minimale Waterafstand',
                    description: 'Totale waterafstand vereist voordat een reis kan worden geclassificeerd als boot',
                    labelLow: '100 m (Soepel)',
                    labelMid: '2000 m (Standaard)',
                    labelHigh: '10000 m (Strikt)'
                },
                minContinuousWaterDistance: {
                    label: 'Minimale Continue Waterafstand',
                    description: 'Filtert korte brug-, tunnel- en kustovergangen eruit',
                    labelLow: '100 m (Soepel)',
                    labelMid: '1000 m (Standaard)',
                    labelHigh: '10000 m (Strikt)'
                },
                maxPlausibleSpeed: {
                    label: 'Maximale Plausibele Snelheid',
                    description: 'Alleen saniteitplafond; trage beweging kan nog steeds bootreizen zijn',
                    labelLow: '20 km/u (Strikt)',
                    labelMid: '120 km/u (Standaard)',
                    labelHigh: '300 km/u (Soepel)'
                }
            },
            shortDistance: {
                title: 'Korte Reisafstand Drempel',
                description: 'Afstandsdrempel voor het toepassen van ontspannen lopend snelheidsdetectie',
                details: 'Reizen korter dan deze afstand krijgen iets soepelere lopend snelheidsclassificatie om GPS-onnauwkeurigheden te compenseren',
                labelLow: '0.1 km (Strikt)',
                labelMid: '1.0 km (Normaal)',
                labelHigh: '3.0 km (Soepel)'
            },
            arrivalDetection: {
                title: 'Aankomstdetectieduur',
                description: 'Minimale tijd dat GPS-punten langzaam geclusterd moeten zijn om aankomst op bestemming te detecteren',
                detailsLowerLabel: 'Lagere waarden',
                detailsLowerValue: 'Gevoeliger aankomstdetectie, kan korte stops opvangen',
                detailsHigherLabel: 'Hogere waarden',
                detailsHigherValue: 'Meer conservatief, alleen aanhoudende aankomsten detecteren',
                labelLow: '10s (Gevoelig)',
                labelMid: '90s (Normaal)',
                labelHigh: '300s (Conservatief)'
            },
            sustainedStop: {
                title: 'Aanhoudende Stopduur',
                description: 'Minimale tijd voor consistente trage beweging om een echte stop te detecteren (filtert verkeerslichten)',
                detailsLowerLabel: 'Lagere waarden',
                detailsLowerValue: 'Kortere stops detecteren, kan verkeersvertragingen omvatten',
                detailsHigherLabel: 'Hogere waarden',
                detailsHigherValue: 'Alleen langere stops detecteren, beter verkeersfilteren',
                labelLow: '10s (Gevoelig)',
                labelMid: '60s (Normaal)',
                labelHigh: '600s (Conservatief)'
            },
            arrivalMinPoints: {
                title: 'Minimale Stoppunten voor Aankomstdetectie',
                description: 'Minimaal aantal GPS-punten vereist om aankomst op bestemming te detecteren',
                detailsLowerLabel: 'Lagere waarden (2)',
                detailsLowerValue: 'Snellere detectie, ideaal voor onregelmatige GPS (10-15 min intervallen)',
                detailsHigherLabel: 'Hogere waarden (3-4)',
                detailsHigherValue: 'Betrouwbaarder, ideaal voor frequente GPS (30-60 sec intervallen)',
                labelLow: '2 punten (Snel)',
                labelMid: '3 punten (Gebalanceerd)',
                labelHigh: '5 punten (Conservatief)'
            }
        }
    },
    reports: {
        page: {
            title: 'Tijdlijnrapporten',
            subtitle: 'Gedetailleerde tabellen van uw verblijfplaatsen, reizen en datagaps'
        },
        demoNotice: 'Data-export is uitgeschakeld in demo-modus.',
        exportAll: 'Alles exporteren',
        exportAllTooltip: 'Verblijfplaatsen, reizen en datagaps exporteren naar CSV',
        demoExportDisabled: 'Export is uitgeschakeld in demo-modus',
        demoExportDisabledToast: 'Data-export is uitgeschakeld in demo-modus.',
        sections: {
            stays: 'Verblijfplaatsen',
            trips: 'Reizen',
            dataGaps: 'Datagaps'
        },
        noDateRange: 'Geen datumbereik geselecteerd',
        noData: {
            title: 'Geen Data Gevonden',
            message: 'Geen tijdlijngegevens gevonden voor het geselecteerde datumbereik.',
            suggestion: 'Probeer een ander datumbereik te selecteren of controleer uw GPS-bronnen.'
        },
        fetchFailed: 'Tijdlijngegevens ophalen is mislukt',
        export: {
            successSummary: 'Export Geslaagd',
            failedSummary: 'Export Mislukt',
            staysDone: 'Verblijfplaatsen succesvol geëxporteerd',
            staysFailed: 'Verblijfplaatsen exporteren is mislukt',
            tripsDone: 'Reizen succesvol geëxporteerd',
            tripsFailed: 'Reizen exporteren is mislukt',
            dataGapsDone: 'Datagaps succesvol geëxporteerd',
            dataGapsFailed: 'Datagaps exporteren is mislukt',
            allDone: 'Alle data succesvol geëxporteerd',
            failedGeneric: 'Data exporteren is mislukt'
        }
    }
}


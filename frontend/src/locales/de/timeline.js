/**
 * Timeline page, its preferences, labels, reports, and the timeline components (German).
 */
export default {
    locationEdit: {
        editFavoriteHeader: 'Favorit bearbeiten'
    },
    notes: {
        openSingle: 'Notiz öffnen',
        openMultiple: '{count} Notizen öffnen'
    },
    photos: {
        openSingle: 'Foto öffnen',
        openMultiple: '{count} Fotos öffnen',
        fallbackAlt: 'Foto'
    },
    dataGap: {
        subtitle: 'Datenlücke – unbekannte Aktivität',
        endTimeLabel: 'Ende:',
        durationLabel: 'Dauer:',
        totalDuration: 'Gesamtdauer:',
        onThisDay: 'An diesem Tag:',
        convertTooltip: 'Diese Datenlücke in einen Aufenthalt umwandeln',
        convertButton: 'In Aufenthalt umwandeln'
    },
    overnight: {
        continuedFrom: 'Fortgesetzt von {time}'
    },
    localTime: {
        resolved: 'Ortszeit in {zone}, nach der nächstgelegenen Stadt {city} ({country}, {distance} km entfernt)',
        beyondMaxDistance: 'Ortszeitzone unbekannt: Die nächste Stadt {city} ({country}) ist {distance} km entfernt. Es wird die Zeitzone aus deinem Profil verwendet.',
        noGeonamesData: 'Ortszeitzone unbekannt: Die GeoNames-Städtedaten sind auf diesem Server nicht geladen. Es wird die Zeitzone aus deinem Profil verwendet.',
        invalidTimezone: 'Ortszeitzone unbekannt: Die Zeitzone der nächsten Stadt {city} ({country}) ist nicht bekannt. Es wird die Zeitzone aus deinem Profil verwendet.',
        notProvided: 'Für diesen Eintrag ist keine Ortszeitzone verfügbar. Es wird die Zeitzone aus deinem Profil verwendet.'
    },
    card: {
        showGpsPoints: 'GPS-Punkte anzeigen',
        viewAllVisits: 'Alle Besuche an diesem Ort anzeigen',
        renamePlace: 'Ort umbenennen …',
        resetDataGap: 'Auf automatische Datenlücke zurücksetzen',
        undoTripSplit: 'Manuelle Teilung der Fahrt rückgängig machen',
        manageNoteSingle: 'Notiz verwalten …',
        manageNotesMultiple: 'Notizen verwalten ({count}) …',
        viewNoteSingle: 'Notiz ansehen …',
        viewNotesMultiple: 'Notizen ansehen ({count}) …',
        addNote: 'Notiz hinzufügen …',
        viewCityDetails: 'Details zu {city}',
        viewCountryDetails: 'Details zu {country}',
        exportGpx: 'Als GPX exportieren',
        renameStayAria: 'Ort des Aufenthalts umbenennen',
        renameDisabledDemo: 'Umbenennen ist im Demo-Modus deaktiviert',
        resetDisabledDemo: 'Zurücksetzen ist im Demo-Modus deaktiviert'
    },
    stay: {
        stayedAt: 'Aufenthalt:',
        manualIndicator: '(manuell)',
        forDuration: 'Für'
    },
    trip: {
        transitionTo: 'Fahrt zu',
        transitionToNewPlace: 'Fahrt zu neuem Ort',
        durationLabel: 'Dauer:',
        distanceLabel: 'Strecke:',
        movementLabel: 'Fortbewegung:',
        editMovementType: 'Fortbewegungsart bearbeiten',
        movementEditDisabledDemo: 'Die Fortbewegungsart lässt sich im Demo-Modus nicht ändern',
        setMovementType: 'Fortbewegungsart festlegen',
        unrecognizedHint: 'Der Algorithmus hat diese Fahrt nicht erkannt.',
        changeMovementType: 'Fortbewegungsart ändern …',
        whyThisClassification: 'Warum diese Einstufung?',
        splitTripWithStay: 'Fahrt mit Aufenthalt teilen …',
        mapMatchingDetails: 'Details zum Map-Matching …'
    },
    explanation: {
        trigger: {
            stay: 'Warum dieser Aufenthalt? Sieh dir die GPS-Punkte und die Entscheidung an',
            trip: 'Warum diese Fahrt? Sieh dir die GPS-Punkte und die Entscheidung an',
            dataGap: 'Warum eine Datenlücke? Sieh dir die GPS-Punkte drumherum und die Entscheidung an'
        },
        toggle: {
            stay: 'Warum dieser Aufenthalt?',
            trip: 'Warum diese Fahrt?',
            dataGap: 'Warum eine Datenlücke?'
        },
        start: {
            FIRST_POINT: 'Beginnt mit dem allerersten GPS-Punkt.',
            AFTER_DATA_GAP: 'Beginnt mit dem ersten GPS-Punkt nach einer Datenlücke.',
            ARRIVAL: 'Beginnt dort, wo du stehen geblieben bist: {points} Punkte langsamer als {stopSpeedKmh} km/h über {stopSeconds}.',
            SPARSE_STOP: 'Beginnt dort, wo du angehalten hast: Der nächste GPS-Punkt kam {gapSeconds} später, nur {distanceMeters} entfernt.',
            LEFT_STAY: 'Beginnt mit einem Punkt {distanceMeters} vom Aufenthalt entfernt (Aufenthaltsradius {radiusMeters}).',
            SHORT_STOP: 'Beginnt mit einem Halt von {stopSeconds}. Das ist zu kurz für einen Aufenthalt ({minMinutes} Min. nötig), daher zählt er zu dieser Fahrt.',
            WATER_TRANSITION_TO_WATER: 'Beginnt dort, wo du vom Land aufs Wasser gewechselt bist.',
            WATER_TRANSITION_TO_LAND: 'Beginnt dort, wo du vom Wasser an Land gewechselt bist.',
            INFERRED_DURING_GAP: 'Hier gibt es {gapSeconds} lang keine GPS-Punkte, aber die Punkte davor und danach liegen nur {distanceMeters} auseinander. Du warst also vermutlich hier.',
            INFERRED_TRIP_DURING_GAP: 'Hier gibt es {gapSeconds} lang keine GPS-Punkte, und die Punkte davor und danach liegen {distanceMeters} auseinander. Du musst also unterwegs gewesen sein.',
            CONTINUITY: 'Zwischen den Aufenthalten davor und danach wurde keine Fahrt erkannt, sie liegen aber {distanceMeters} auseinander. Diese Fahrt verbindet sie.',
            NO_POINTS: '{gapSeconds} lang keine GPS-Punkte. Pausen über {thresholdSeconds} werden als Datenlücke angezeigt.',
            NO_POINTS_SINCE_LAST: 'Seit dem letzten GPS-Punkt keine neuen. Eine Pause über {thresholdSeconds} wird als Datenlücke angezeigt, bis neue Punkte kommen.',
            STOPPED_BEFORE_GAP: 'Beginnt dort, wo du angehalten hast, direkt vor einer Datenlücke.',
            MANUAL_SPLIT: 'Du hast diesen Aufenthalt durch Teilen einer Fahrt angelegt.',
            MANUAL_GAP_STAY: 'Du hast eine Datenlücke in diesen Aufenthalt umgewandelt.'
        },
        note: {
            CONFIRMED: 'Zählt als Aufenthalt: Die Punkte lagen mindestens {minMinutes} Min. innerhalb von {radiusMeters}.',
            FAVORITE_AREA: 'Punkte außerhalb des Aufenthaltsradius gehören weiter zu diesem Aufenthalt, weil sie im selben Favoritenbereich liegen.',
            CONTINUED_OVER_GAP: 'Geht über eine Datenlücke von {gapSeconds} hinweg: Die Punkte davor und danach liegen am selben Ort.',
            MERGED: '{stays} Aufenthalte am selben Ort wurden zu diesem zusammengefasst.',
            JOINED_SEGMENTS: '{segments} Bewegungsabschnitte wurden zu dieser Fahrt zusammengefasst.',
            SHORT_STOP: 'Enthält einen Halt von {stopSeconds}, zu kurz für einen Aufenthalt ({minMinutes} Min. nötig).',
            MANUAL_SPLIT: 'Durch deine manuelle Teilung der Fahrt geändert.',
            MANUAL_GAP_STAY: 'Durch deine manuelle Umwandlung einer Datenlücke verlängert.'
        },
        end: {
            MOVED_AWAY: 'Endet mit einem Punkt {distanceMeters} entfernt (Aufenthaltsradius {radiusMeters}).',
            ARRIVAL: 'Endet dort, wo du stehen geblieben bist: {points} Punkte langsamer als {stopSpeedKmh} km/h über {stopSeconds}.',
            SPARSE_STOP: 'Endet dort, wo du angehalten hast: Der nächste GPS-Punkt kam {gapSeconds} später, nur {distanceMeters} entfernt.',
            DATA_GAP: 'Endet an einer Datenlücke von {gapSeconds}.',
            LAST_POINT: 'Endet mit dem letzten vorhandenen GPS-Punkt, läuft also vielleicht noch.',
            WATER_TRANSITION_TO_WATER: 'Endet dort, wo du vom Land aufs Wasser gewechselt bist.',
            WATER_TRANSITION_TO_LAND: 'Endet dort, wo du vom Wasser an Land gewechselt bist.',
            STOPPED_BEFORE_GAP: 'Endet dort, wo du angehalten hast, direkt vor einer Datenlücke.'
        }
    },
    explanationDialog: {
        header: {
            stay: 'Warum dieser Aufenthalt?',
            trip: 'Warum diese Fahrt?',
            dataGap: 'Warum eine Datenlücke?'
        },
        loading: 'GPS-Punkte werden geladen …',
        loadFailed: 'Die Erklärung konnte nicht geladen werden.',
        period: '{start} – {end} ({duration})',
        sections: {
            map: 'GPS-Punkte',
            steps: 'So wurde entschieden',
            filtered: 'Ausgelassene Punkte',
            ingestion: 'Nie gespeicherte Punkte'
        },
        legend: {
            used: 'Verwendet',
            filtered: 'Ausgelassen (ungenau)',
            context: 'Davor und danach',
            deciding: 'Ausschlaggebend',
            radius: 'Aufenthaltsradius',
            anchor: 'Gespeicherter Ort'
        },
        mapHint: 'Wähle einen Schritt, um seine Punkte auf der Karte zu sehen. Wähle einen Punkt für Details.',
        noPoints: 'Für diesen Zeitraum sind keine GPS-Punkte gespeichert.',
        truncated: 'Dieses Ereignis hat {total} GPS-Punkte. Die Karte zeigt {shown} davon, darunter alle ausgelassenen und ausschlaggebenden Punkte.',
        steps: {
            any: {
                start: {
                    FIRST_POINT: 'Beginnt um {time} mit deinem ersten GPS-Punkt.',
                    AFTER_DATA_GAP: 'Beginnt um {time} mit dem ersten GPS-Punkt nach einer Lücke in deinen Daten.',
                    ARRIVAL: 'Beginnt um {time}, wo du stehen geblieben bist.',
                    SPARSE_STOP: 'Beginnt um {time}, wo du angehalten hast. Während du hier warst, wurden keine GPS-Punkte aufgezeichnet: Der nächste kam {gapSeconds} später, nur {distanceMeters} entfernt.',
                    LEFT_STAY: 'Begann um {time}, als du {distanceMeters} vom Aufenthaltsort entfernt warst, also außerhalb seines Radius von {radiusMeters}.',
                    SHORT_STOP: 'Beginnt mit einem Halt von {time} bis {endTime} ({stopSeconds}). Ein Aufenthalt braucht mindestens {minMinutes}, daher zählt der Halt zu dieser Fahrt.',
                    WATER_TRANSITION_TO_WATER: 'Beginnt um {time}, wo du vom Land aufs Wasser gewechselt bist.',
                    WATER_TRANSITION_TO_LAND: 'Beginnt um {time}, wo du vom Wasser an Land gewechselt bist.',
                    INFERRED_DURING_GAP: '{gapSeconds} lang gibt es keine GPS-Daten, aber die Punkte vor und nach der Lücke liegen nur {distanceMeters} auseinander. GeoPulse nimmt daher an, dass du hier warst.',
                    INFERRED_TRIP_DURING_GAP: '{gapSeconds} lang gibt es keine GPS-Daten, und die Punkte vor und nach der Lücke liegen {distanceMeters} auseinander. Du musst also dazwischen unterwegs gewesen sein.',
                    CONTINUITY: 'Zwischen den Aufenthalten vor und nach dieser Fahrt wurde keine Bewegung aufgezeichnet, sie liegen aber {distanceMeters} auseinander. GeoPulse fügt diese Fahrt ein, um sie zu verbinden. Sie hat keine eigenen GPS-Punkte.',
                    STOPPED_BEFORE_GAP: 'Du hast hier um {time} angehalten, direkt bevor deine GPS-Daten aussetzten, und warst am selben Ort, als sie wiederkamen.',
                    MANUAL_SPLIT: 'Du hast das durch Teilen einer Fahrt angelegt.',
                    MANUAL_GAP_STAY: 'Du hast eine Datenlücke in diesen Aufenthalt umgewandelt.',
                    NO_POINTS: 'Keine GPS-Punkte von {time} bis {endTime} ({gapSeconds}). Pausen über {thresholdSeconds} werden als Datenlücke angezeigt.',
                    NO_POINTS_SINCE_LAST: 'Keine GPS-Punkte seit {time}. Eine Pause über {thresholdSeconds} wird als Datenlücke angezeigt, bis neue Punkte kommen.'
                },
                note: {
                    CONFIRMED: 'Ab {time} als Aufenthalt gezählt: Bis dahin warst du {minMinutes} lang innerhalb von {radiusMeters} um diesen Ort.',
                    FAVORITE_AREA: '{count} Punkte lagen weiter vom Mittelpunkt entfernt als der Aufenthaltsradius, aber im selben Favoritenbereich. Sie gehören daher weiter zu diesem Aufenthalt.',
                    CONTINUED_OVER_GAP: 'Von {time} bis {endTime} ({gapSeconds}) gibt es keine GPS-Daten, aber du warst davor und danach am selben Ort. Der Aufenthalt geht also über die Lücke hinweg.',
                    MERGED: '{stays} Aufenthalte an diesem Ort, räumlich und zeitlich nah beieinander, wurden zu diesem zusammengefasst.',
                    JOINED_SEGMENTS: '{segments} aufeinanderfolgende Fahrten wurden zu dieser zusammengefasst.',
                    SHORT_STOP: 'Enthält einen Halt von {stopSeconds}, kürzer als die {minMinutes}, die ein Aufenthalt braucht.',
                    MANUAL_SPLIT: 'Durch deine manuelle Teilung der Fahrt geändert.',
                    MANUAL_GAP_STAY: 'Durch deine manuelle Umwandlung einer Datenlücke verlängert.'
                },
                end: {
                    MOVED_AWAY: 'Endete um {time}, als du {distanceMeters} vom Mittelpunkt entfernt warst, also außerhalb des Aufenthaltsradius von {radiusMeters}.',
                    ARRIVAL: 'Endete um {time}, als du stehen geblieben bist.',
                    SPARSE_STOP: 'Endete um {time}, wo du angehalten hast: Der nächste GPS-Punkt kam {gapSeconds} später, nur {distanceMeters} entfernt.',
                    DATA_GAP: 'Endete um {time}, als deine GPS-Daten für {gapSeconds} aussetzten.',
                    LAST_POINT: 'Dein neuester GPS-Punkt ist von {time}, das läuft also vielleicht noch.',
                    WATER_TRANSITION_TO_WATER: 'Endete um {time}, wo du vom Land aufs Wasser gewechselt bist.',
                    WATER_TRANSITION_TO_LAND: 'Endete um {time}, wo du vom Wasser an Land gewechselt bist.',
                    STOPPED_BEFORE_GAP: 'Endete um {time}, wo du direkt vor dem Aussetzen deiner GPS-Daten angehalten hast.'
                }
            },
            stay: {
                start: {
                    ARRIVAL: 'Du bist um {time} angekommen und stehen geblieben.',
                    MANUAL_SPLIT: 'Du hast diesen Aufenthalt durch Teilen einer Fahrt angelegt.'
                }
            }
        },
        callouts: {
            gapFiltered: 'In diesem Zeitraum wurden {count} GPS-Punkte aufgezeichnet, aber alle waren ungenauer als {thresholdMeters} ({setting}). Sie wurden ausgelassen, deshalb erscheint der Zeitraum als Datenlücke.',
            settingChanged: '{setting} war beim Erstellen {builtWith} und ist jetzt {current}. Berechne die Zeitleiste neu, um den neuen Wert anzuwenden.'
        },
        filtered: {
            none: 'Alle GPS-Punkte dieses Ereignisses wurden verwendet.',
            some: '{filtered} von {total} GPS-Punkten wurden ausgelassen, weil sie ungenauer als {thresholdMeters} waren ({setting}).',
            worst: 'Der ungenaueste lag bei ±{accuracyMeters}.',
            context: '{count} Punkte davor oder danach wurden ebenfalls ausgelassen.',
            off: 'GPS-Punkte werden nicht nach Genauigkeit gefiltert, weil {setting} aus ist.'
        },
        ingestion: {
            intro: 'Deine GPS-Quellen verwerfen manche Punkte schon vor dem Speichern. Diese Punkte können hier nicht angezeigt werden:',
            source: '{source}: {rules}',
            accuracy: 'Punkte ungenauer als {value}',
            speed: 'Punkte schneller als {value}',
            duplicates: 'Wiederholungen am selben Ort innerhalb von {value}',
            link: 'Standortquellen'
        },
        point: {
            used: 'Für dieses Ereignis verwendet.',
            before: 'Vor diesem Ereignis.',
            after: 'Nach diesem Ereignis.',
            filtered: 'Ausgelassen: Genauigkeit ±{accuracyMeters}, schlechter als die GPS-Genauigkeitsschwelle von {thresholdMeters}.',
            outsideRadius: '{distanceMeters} vom Mittelpunkt, außerhalb des Radius von {radiusMeters}.',
            accuracy: 'Genauigkeit ±{value}',
            noAccuracy: 'Keine Genauigkeit erfasst',
            speed: 'Geschwindigkeit {value}',
            reason: {
                FIRST_POINT: 'Dein erster GPS-Punkt.',
                AFTER_DATA_GAP: 'Der erste Punkt nach einer Lücke in den GPS-Daten.',
                ARRIVAL: 'Teil des Halts bei deiner Ankunft.',
                SPARSE_STOP: 'Hier hast du angehalten: Der nächste Punkt kam viel später, ganz in der Nähe.',
                LEFT_STAY: 'Hat diese Fahrt begonnen: Er liegt außerhalb des Aufenthaltsradius.',
                SHORT_STOP: 'Teil eines Halts, der für einen Aufenthalt zu kurz war.',
                WATER_TRANSITION: 'Hier hast du zwischen Land und Wasser gewechselt.',
                INFERRED_DURING_GAP: 'Neben der Lücke, über die dieser Aufenthalt angenommen wurde.',
                INFERRED_TRIP_DURING_GAP: 'Neben der Lücke, über die diese Fahrt angenommen wurde.',
                STOPPED_BEFORE_GAP: 'Hier hast du vor der Lücke angehalten.',
                NO_POINTS: 'Neben der Lücke in den GPS-Daten.',
                NO_POINTS_SINCE_LAST: 'Der letzte Punkt vor der Lücke.',
                CONFIRMED: 'Ab hier zählte es als Aufenthalt.',
                FAVORITE_AREA: 'Außerhalb des Aufenthaltsradius, aber im Favoritenbereich.',
                CONTINUED_OVER_GAP: 'Neben einer Lücke, über die der Aufenthalt weiterlief.',
                MOVED_AWAY: 'Hat den Aufenthalt beendet: Er liegt außerhalb des Aufenthaltsradius.',
                DATA_GAP: 'Neben einer Lücke in den GPS-Daten.',
                LAST_POINT: 'Dein neuester GPS-Punkt.'
            }
        },
        settingsUsed: 'Einstellungen',
        changeSettings: 'Zeitleisten-Einstellungen',
        whyClassification: 'Warum diese Fortbewegungsart?',
        close: 'Schließen',
        on: 'an',
        off: 'aus',
        minutes: '{value} Min.'
    },
    container: {
        noData: 'Für diesen Zeitraum gibt es keine Zeitleiste.',
        showingItems: '{shown} von {total} Einträgen werden angezeigt.',
        loadMore: 'Mehr laden',
        viewAllInReports: 'Oder alle in den Zeitleisten-Berichten ansehen',
        viewPeriodAria: 'Zeitraum {name} ansehen',
        showOnlyDayOnMap: 'Nur diesen Tag auf der Karte zeigen',
        showAllDaysOnMap: 'Alle Tage auf der Karte zeigen',
        exportStartedTitle: 'Export gestartet',
        tripExportingDetail: 'Fahrt wird als GPX exportiert',
        stayExportingDetail: 'Aufenthalt wird als GPX exportiert',
        exportFailedTitle: 'Export fehlgeschlagen',
        tripExportFailedDetail: 'Fahrt konnte nicht exportiert werden',
        stayExportFailedDetail: 'Aufenthalt konnte nicht exportiert werden'
    },
    focusedDay: {
        showing: '{day} wird angezeigt',
        showAll: 'Alle Tage auf der Karte zeigen'
    },
    splitLayout: {
        defaultCollapsedLabel: 'Zeitleiste',
        defaultExpandedLabel: 'Bewegungszeitleiste',
        dayNavigationAriaLabel: 'Tagesnavigation der Zeitleiste',
        previousDay: 'Vorheriger Tag',
        nextDay: 'Nächster Tag',
        show: '{label} anzeigen',
        collapse: '{label} einklappen'
    },
    largeDataset: {
        title: 'Großer Zeitraum ausgewählt',
        itemsCount: 'Dieser Zeitraum enthält {count} Einträge',
        breakdown: '({stays} Aufenthalte, {trips} Fahrten)',
        breakdownWithGaps: '({stays} Aufenthalte, {trips} Fahrten, {dataGaps} Datenlücken)',
        recommendation: 'Die Zeitleiste eignet sich am besten für einzelne Tage oder Wochen. Für lange Zeiträume nimm die Zeitleisten-Berichte:',
        openReports: 'Zeitleisten-Berichte öffnen',
        orViewRecent: 'Oder aktuelle Daten ansehen:',
        last7Days: 'Letzte 7 Tage',
        last30Days: 'Letzte 30 Tage',
        loadAnyway: 'Trotzdem laden (kann langsam sein)',
        continueWithRange: 'Mit aktuellem Zeitraum fortfahren'
    },
    notesViewer: {
        header: 'Notizen',
        empty: 'Keine Notizen zu diesem Eintrag.',
        editNote: 'Notiz bearbeiten',
        deleteNote: 'Notiz löschen',
        openInMemos: 'In Memos öffnen',
        truncatedNotice: 'Diese Memos-Notiz ist groß, deshalb zeigt GeoPulse nur eine gekürzte Vorschau.',
        deleteConfirm: 'Diese GeoPulse-Notiz löschen?',
        cancel: 'Abbrechen',
        delete: 'Löschen',
        close: 'Schließen',
        locationSources: {
            explicit: 'Mit Geotag',
            derivedStay: 'Ort des Aufenthalts',
            derivedTripGps: 'GPS der Fahrt',
            derivedTripInterpolated: 'Geschätzt aus der Fahrt'
        },
        toasts: {
            deletedTitle: 'Notiz gelöscht',
            deletedDetail: 'Die GeoPulse-Notiz wurde gelöscht',
            deleteFailedTitle: 'Löschen fehlgeschlagen',
            deleteFailedDetail: 'Notiz konnte nicht gelöscht werden'
        }
    },
    noteEditor: {
        titleLabel: 'Titel',
        titlePlaceholder: 'Titel (optional)',
        saveToLabel: 'Speichern in',
        visibilityLabel: 'Sichtbarkeit in Memos',
        noteLabel: 'Notiz',
        contentPlaceholder: 'Schreib eine Notiz …',
        contentRequired: 'Die Notiz darf nicht leer sein',
        cancel: 'Abbrechen',
        editHeader: 'Notiz bearbeiten',
        addHeader: 'Notiz hinzufügen',
        update: 'Aktualisieren',
        save: 'Speichern',
        destinations: {
            geopulse: 'GeoPulse',
            memos: 'Memos'
        },
        visibilityOptions: {
            private: 'Privat',
            protected: 'Geschützt',
            public: 'Öffentlich'
        },
        toasts: {
            updatedTitle: 'Notiz aktualisiert',
            savedTitle: 'Notiz gespeichert',
            updatedDetail: 'Die GeoPulse-Notiz wurde aktualisiert',
            savedInMemos: 'Die Notiz wurde in Memos angelegt',
            savedInGeoPulse: 'Die Notiz wurde in GeoPulse gespeichert',
            saveFailedTitle: 'Speichern fehlgeschlagen',
            saveFailedDetail: 'Notiz konnte nicht gespeichert werden'
        }
    },
    dataGapHelp: {
        toggleAriaLabel: 'Hilfe ein- oder ausblenden',
        explanation: 'Datenlücken entstehen, wenn die GPS-Aufzeichnung unterbrochen war. Wie GeoPulse mit Lücken umgeht, stellst du in den Zeitleisten-Einstellungen ein.',
        recommendationsHeader: 'Für diese Lücke von {duration}:',
        openSettings: 'Zeitleisten-Einstellungen öffnen',
        toggleShort: 'Hilfe zu Lücken?',
        toggleLong: 'Warum passiert das?',
        tips: {
            lowerThreshold: 'Senke die Schwelle für Datenlücken, um auch kürzere Lücken zu erkennen',
            enableStayInference: 'Aktiviere „Aufenthalt in Lücken ableiten“, wenn du am selben Ort warst',
            enableTripInference: 'Aktiviere „Fahrt in Lücken ableiten“, wenn du eine weite Strecke zurückgelegt hast'
        }
    },
    layout: {
        collapsedLabel: 'Zeitleiste',
        expandedLabel: 'Bewegungszeitleiste'
    },
    labels: {
        page: {
            title: 'Zeitleisten-Labels',
            subtitleLabelsCount: '{count} Label | {count} Labels',
            subtitleDaysTagged: '{count} Tag markiert | {count} Tage markiert',
            subtitleFallback: 'Erweiterte Zeitleisten-Labels (für Filter-Chips der Zeitleiste und Importe)'
        },
        actions: {
            create: 'Label erstellen',
            timeline: 'Zeitleiste ansehen',
            more: 'Mehr'
        },
        help: 'Zeitleisten-Labels markieren einen Zeitraum deiner Zeitleiste (etwa eine Reise oder ein Ereignis). Er erscheint dann als Schnellfilter und lässt sich in einen Reiseplan umwandeln.',
        activeBanner: {
            kicker: 'Aktives Label',
            since: 'Seit {date}'
        },
        sourceNames: {
            manual: 'Manuell',
            ownTracks: 'OwnTracks'
        },
        filters: {
            search: 'Labels durchsuchen …',
            source: {
                all: 'Alle Quellen'
            },
            linkState: {
                all: 'Alle',
                linked: 'Verknüpft',
                unlinked: 'Nicht verknüpft'
            }
        },
        bulkDelete: 'Löschen ({count})',
        badges: {
            active: 'Aktiv',
            hidden: 'Ausgeblendet',
            visible: 'Sichtbar'
        },
        now: 'Jetzt',
        table: {
            pageReport: '{first} bis {last} von {totalRecords} Labels',
            columns: {
                label: 'Label',
                period: 'Zeitraum',
                tripPlan: 'Reiseplan',
                preset: 'Vorlage',
                actions: 'Aktionen'
            },
            notLinked: 'Nicht verknüpft',
            empty: 'Keine Zeitleisten-Labels gefunden',
            actionsAria: 'Weitere Aktionen für {name}',
            selectAria: '{name} auswählen'
        },
        cards: {
            period: 'Zeitraum',
            duration: 'Dauer',
            datePreset: 'Vorlage',
            linkedTripPlan: 'Verknüpfter Reiseplan',
            showingCount: '{count} Labels'
        },
        linkedDelete: {
            header: 'Verknüpftes Label löschen',
            linkedToLead: 'Dieses Zeitleisten-Label ist mit folgendem Reiseplan verknüpft:',
            choose: 'Wie möchtest du fortfahren?',
            cancel: 'Abbrechen',
            onlyLabel: 'Nur Label löschen',
            labelAndTripPlan: 'Label & Reiseplan löschen'
        }
    },
    page: {
        workspaceBanner: {
            lead: 'Dieser Zeitraum passt zu deinem Reiseplan:',
            openPlanner: 'Planer öffnen'
        },
        map: {
            noData: 'Keine Standortdaten für den gewählten Zeitraum.'
        },
        favorite: {
            deleteConfirm: {
                header: 'Favorit löschen',
                message: 'Möchtest du diesen Favoriten wirklich löschen?'
            },
            deleteSuccess: '„{name}“ wurde gelöscht',
            deleteFailed: 'Favorit konnte nicht gelöscht werden'
        },
        toasts: {
            noLocationData: 'Für den gewählten Zeitraum sind keine Standortdaten vorhanden.',
            locationFetchFailed: 'Standortdaten konnten nicht abgerufen werden',
            noTimelineData: 'Für den gewählten Zeitraum sind keine Zeitleistendaten vorhanden.',
            timelineFetchFailed: 'Zeitleistendaten konnten nicht abgerufen werden',
            timelineLoadFailed: 'Zeitleistendaten konnten nicht geladen werden',
            viewingTag: '{name} wird angezeigt',
            tagPeriod: 'Der gesamte Zeitraum von „{name}“ wird angezeigt'
        },
        resetDataGapOverride: {
            confirmHeader: 'Datenlücke zurücksetzen',
            confirmMessage: 'Möchtest du diese Datenlücke wirklich auf automatische Erkennung zurücksetzen?',
            successSummary: 'Datenlücke zurückgesetzt',
            successDetail: 'Die Datenlücke wird wieder automatisch erkannt.',
            failedDetail: 'Die Änderung an der Datenlücke konnte nicht zurückgesetzt werden.'
        },
        resetFailedSummary: 'Zurücksetzen fehlgeschlagen',
        resetTripSplitOverride: {
            confirmHeader: 'Teilung der Fahrt rückgängig machen',
            confirmMessage: 'Möchtest du diese manuelle Teilung der Fahrt wirklich rückgängig machen?',
            successSummary: 'Teilung rückgängig gemacht',
            successDetail: 'Die manuelle Teilung der Fahrt wurde rückgängig gemacht.',
            failedDetail: 'Die Teilung der Fahrt konnte nicht rückgängig gemacht werden.'
        },
        reconstruction: {
            updatedSummary: 'Zeitleiste aktualisiert',
            updatedDetail: 'Deine Zeitleiste wurde mit den rekonstruierten Daten aktualisiert.',
            failedSummary: 'Rekonstruktion fehlgeschlagen',
            failedDetail: 'Die Zeitleistendaten konnten nicht rekonstruiert werden.',
            trackingFailedSummary: 'Aufzeichnung fehlgeschlagen'
        }
    },
    preferences: {
        gpsPathSimplification: {
            title: 'Vereinfachung der GPS-Strecke',
            description: 'Lege fest, wie GPS-Strecken vereinfacht werden, um Daten zu sparen und die Route trotzdem genau zu halten',
            enabled: {
                title: 'Streckenvereinfachung aktivieren',
                description: 'Ob die GPS-Strecken von Fahrten in der Zeitleiste vereinfacht werden',
                details: 'Wenn aktiviert, werden Strecken mit dem Douglas-Peucker-Algorithmus vereinfacht. Das reduziert die Zahl der GPS-Punkte, ohne die Route spürbar zu verfälschen'
            },
            tolerance: {
                title: 'Toleranz der Vereinfachung',
                description: 'Grundtoleranz in Metern für die Vereinfachung der GPS-Strecke',
                detailsLowerLabel: 'Niedrigere Werte',
                detailsLowerValue: 'Mehr Details, weniger Komprimierung',
                detailsHigherLabel: 'Höhere Werte',
                detailsHigherValue: 'Mehr Komprimierung, weniger Details',
                labelLow: '1 m (viele Details)',
                labelMid: '15 m (ausgewogen)',
                labelHigh: '50 m (starke Komprimierung)',
                suffix: ' m'
            },
            maxPoints: {
                title: 'Maximale Punktzahl',
                description: 'Höchstzahl an GPS-Punkten, die eine vereinfachte Strecke behält',
                details: 'Liegt eine vereinfachte Strecke noch über diesem Limit, wird die Toleranz automatisch erhöht, bis es passt. 0 bedeutet kein Limit',
                labelLow: '0 (kein Limit)',
                labelMid: '100 (ausgewogen)',
                labelHigh: '500 (hohes Limit)',
                suffix: ' Punkte'
            },
            adaptive: {
                title: 'Adaptive Vereinfachung',
                description: 'Passt die Toleranz an die Eigenschaften der Fahrt an',
                details: 'Wenn aktiviert, nutzen längere Fahrten eine höhere Toleranz für stärkere Komprimierung, kürzere Fahrten bleiben mit niedrigerer Toleranz genauer'
            }
        },
        stayMerging: {
            title: 'Zusammenführen von Aufenthalten',
            description: 'Lege fest, wie nahe beieinanderliegende Aufenthalte zu einem Ort zusammengefasst werden',
            enabled: {
                title: 'Aufenthalte zusammenführen',
                description: 'Ob Aufenthalte zusammengeführt werden, die zeitlich und räumlich nah beieinander liegen',
                details: 'Fasst mehrere GPS-Cluster am selben Ort zu einem Aufenthalt zusammen'
            },
            maxDistance: {
                title: 'Maximaler Abstand',
                description: 'Größter Abstand zwischen Aufenthalten, bei dem sie noch zusammengeführt werden',
                detailsLowerLabel: 'Niedrigere Werte',
                detailsLowerValue: 'Nur sehr nahe Punkte zusammenführen',
                detailsHigherLabel: 'Höhere Werte',
                detailsHigherValue: 'Auch weiter entfernte Punkte zusammenführen',
                labelLow: '20 m (genau)',
                labelMid: '150 m (ausgewogen)',
                labelHigh: '500 m (großzügig)',
                suffix: ' m'
            },
            maxTimeGap: {
                title: 'Maximaler zeitlicher Abstand',
                description: 'Größter zeitlicher Abstand zwischen Aufenthalten, bei dem sie noch zusammengeführt werden',
                detailsLowerLabel: 'Niedrigere Werte',
                detailsLowerValue: 'Nur direkt aufeinanderfolgende Aufenthalte zusammenführen',
                detailsHigherLabel: 'Höhere Werte',
                detailsHigherValue: 'Auch Aufenthalte mit längerer Pause dazwischen zusammenführen',
                labelLow: '1 Min. (streng)',
                labelMid: '10 Min. (ausgewogen)',
                labelHigh: '60 Min. (großzügig)',
                suffix: ' Min.'
            }
        },
        gpsGaps: {
            title: 'Erkennung von GPS-Lücken',
            description: 'Lege fest, wie Lücken in den GPS-Daten erkannt und in deiner Zeitleiste erfasst werden',
            threshold: {
                title: 'Schwelle für Datenlücken',
                description: 'Größter zeitlicher Abstand in Sekunden zwischen zwei GPS-Punkten, bevor eine Datenlücke angenommen wird',
                details: 'Ist der Abstand zwischen zwei aufeinanderfolgenden GPS-Punkten größer als diese Schwelle, wird eine Datenlücke angelegt, statt den aktuellen Aufenthalt oder die Fahrt zu verlängern. So werden Aktivitäten nicht künstlich über Zeiten ohne GPS-Daten gestreckt.',
                labelLow: '5 Min. (empfindlich)',
                labelMid: '3 Stunden (ausgewogen)',
                labelHigh: '6 Stunden (tolerant)',
                suffix: ' Sekunden'
            },
            minDuration: {
                title: 'Mindestdauer einer Lücke',
                description: 'Mindestdauer in Sekunden, ab der eine Lücke als Datenlücke erfasst wird',
                details: 'Kürzere Lücken werden ignoriert, um Rauschen zu vermeiden. So erzeugen kurze Verbindungsprobleme keine unnötigen Einträge.',
                labelLow: '5 Min. (streng)',
                labelMid: '30 Min. (ausgewogen)',
                labelHigh: '2 Stunden (tolerant)',
                suffix: ' Sekunden'
            },
            stayInferenceEnabled: {
                title: 'Aufenthalt in Lücken ableiten',
                description: 'Leitet einen Aufenthalt ab, wenn GPS-Daten fehlen, der Ort davor und danach aber derselbe ist',
                detailsWhenLabel: 'Wenn aktiviert',
                detailsWhenValue: 'Warst du an einem Ort, setzen die GPS-Daten aus und beginnen am selben Ort wieder, nimmt das System an, dass du dort geblieben bist',
                detailsUseCaseLabel: 'Anwendungsfall',
                detailsUseCaseValue: 'Lücken über Nacht zu Hause erscheinen als Aufenthalt statt als Datenlücke'
            },
            stayInferenceMaxGap: {
                title: 'Maximale Lückendauer zum Ableiten',
                description: 'Längste Datenlücke, für die noch ein Aufenthalt abgeleitet wird',
                detailsLowerLabel: 'Niedrigere Werte',
                detailsLowerValue: 'Aufenthalte nur bei kürzeren Lücken ableiten (z. B. kurz ausgeschaltetes Handy)',
                detailsHigherLabel: 'Höhere Werte',
                detailsHigherValue: 'Aufenthalte auch bei längeren Lücken ableiten (z. B. über Nacht oder einen ganzen Tag)',
                labelLow: '1 Stunde (streng)',
                labelMid: '24 Stunden (Standard)',
                labelHigh: '72 Stunden (tolerant)',
                suffix: ' Stunden'
            },
            tripInferenceEnabled: {
                title: 'Fahrt in Lücken ableiten',
                description: 'Leitet eine Fahrt ab, wenn GPS-Daten fehlen und dabei eine weite Strecke zurückgelegt wurde',
                detailsWhenLabel: 'Wenn aktiviert',
                detailsWhenValue: 'Setzen die GPS-Daten aus und beginnen an einem weit entfernten Ort wieder, nimmt das System an, dass eine Fahrt stattfand',
                detailsUseCaseLabel: 'Anwendungsfall',
                detailsUseCaseValue: 'Nachtflüge oder lange Autofahrten mit ausgeschaltetem Handy erscheinen als abgeleitete Fahrten statt als Datenlücken',
                detailsClassificationLabel: 'Einstufung der Fahrt',
                detailsClassificationValue: 'Die Fortbewegungsart ergibt sich automatisch aus Strecke, Dauer und Geschwindigkeit (z. B. Flug, Auto, Zug)'
            },
            tripInferenceMinDistance: {
                title: 'Mindeststrecke zum Ableiten einer Fahrt',
                description: 'Kleinster Abstand zwischen GPS-Punkten, ab dem während einer Lücke eine Fahrt abgeleitet wird',
                detailsLowerLabel: 'Niedrigere Werte',
                detailsLowerValue: 'Fahrten auch bei kürzeren Strecken ableiten (z. B. 10 km in der Stadt)',
                detailsHigherLabel: 'Höhere Werte',
                detailsHigherValue: 'Fahrten nur bei längeren Strecken ableiten (z. B. ab 100 km zwischen Städten)',
                labelLow: '10 km (kurze Fahrten)',
                labelMid: '100 km (Standard)',
                labelHigh: '500 km (Langstrecke)',
                suffix: ' m'
            },
            tripInferenceMinGap: {
                title: 'Mindestdauer einer Lücke zum Ableiten einer Fahrt',
                description: 'Kürzeste Lückendauer, ab der eine Fahrt abgeleitet wird',
                detailsLowerLabel: 'Niedrigere Werte',
                detailsLowerValue: 'Fahrten auch bei kurzen Lücken ableiten (z. B. 30 Minuten)',
                detailsHigherLabel: 'Höhere Werte',
                detailsHigherValue: 'Fahrten nur bei längeren Lücken ableiten (z. B. mehrere Stunden)',
                labelLow: '0 Stunden (jede Lücke)',
                labelMid: '1 Stunde (Standard)',
                labelHigh: '12 Stunden (lange Lücken)',
                suffix: ' Stunden'
            },
            tripInferenceMaxGap: {
                title: 'Maximale Lückendauer zum Ableiten einer Fahrt',
                description: 'Längste Lücke, für die noch eine Fahrt abgeleitet wird (längere werden zu Datenlücken)',
                detailsLowerLabel: 'Niedrigere Werte',
                detailsLowerValue: 'Fahrten nur bei kürzeren Lücken ableiten (z. B. 12 Stunden)',
                detailsHigherLabel: 'Höhere Werte',
                detailsHigherValue: 'Fahrten auch bei längeren Lücken ableiten (z. B. mehrtägige Reisen)',
                labelLow: '1 Stunde (streng)',
                labelMid: '24 Stunden (Standard)',
                labelHigh: '168 Stunden (eine Woche)',
                suffix: ' Stunden'
            }
        },
        stayPointDetection: {
            title: 'Erkennung von Aufenthalten',
            description: 'Lege fest, wie GPS-Daten ausgewertet werden, um Orte zu finden, an denen du dich aufgehalten hast',
            radius: {
                title: 'Radius für Aufenthalte',
                description: 'Abstand, innerhalb dessen GPS-Punkte zu einem Aufenthalt gruppiert werden. Legt auch den Mindestabstand zwischen Aufenthalten fest, ab dem eine Fahrt entsteht',
                detailsLowerLabel: 'Niedrigere Werte',
                detailsLowerValue: 'Empfindlichere Erkennung, nahe Orte bleiben getrennt',
                detailsHigherLabel: 'Höhere Werte',
                detailsHigherValue: 'Weniger empfindlich, nahe Orte werden zu einem Aufenthalt',
                labelLow: '10 m (empfindlich)',
                labelMid: '50 m (ausgewogen)',
                labelHigh: '500 m (zurückhaltend)',
                suffix: ' m'
            },
            minDuration: {
                title: 'Mindestdauer eines Aufenthalts',
                description: 'Mindestdauer (in Minuten), ab der ein Aufenthalt bestätigt wird',
                detailsLowerLabel: 'Niedrigere Werte',
                detailsLowerValue: 'Sehr kurze Aufenthalte können Fehlerkennungen sein',
                detailsHigherLabel: 'Höhere Werte',
                detailsHigherValue: 'Längere Aufenthalte sind eher echt',
                labelLow: '1 Min. (empfindlich)',
                labelMid: '7 Min. (ausgewogen)',
                labelHigh: '60 Min. (zurückhaltend)',
                suffix: ' Min.'
            },
            enhancedFiltering: {
                title: 'Erweiterte Filterung',
                description: 'Geschwindigkeit und Genauigkeit für eine bessere Erkennung von Aufenthalten nutzen',
                details: 'Filtert GPS-Punkte schlechter Qualität heraus und macht die Zeitleiste genauer'
            },
            velocityThreshold: {
                title: 'Geschwindigkeitsschwelle',
                description: 'Höchstgeschwindigkeit, bei der ein Punkt noch als Stillstand gilt',
                detailsLowerLabel: 'Niedrigere Werte',
                detailsLowerValue: 'Strengere Filterung',
                detailsHigherLabel: 'Höhere Werte',
                detailsHigherValue: 'Mehr Bewegung innerhalb von Aufenthalten erlaubt',
                labelLow: '1 km/h (streng)',
                labelMid: '9 km/h (ausgewogen)',
                labelHigh: '20 km/h (tolerant)',
                suffix: ' km/h'
            },
            accuracyThreshold: {
                title: 'GPS-Genauigkeitsschwelle',
                description: 'Mindestgenauigkeit, die ein GPS-Punkt haben muss, um verwendet zu werden',
                detailsLowerLabel: 'Niedrigere Werte',
                detailsLowerValue: 'Genaueres GPS erforderlich',
                detailsHigherLabel: 'Höhere Werte',
                detailsHigherValue: 'Auch ungenauere GPS-Punkte zulassen',
                labelLow: '5 m (hohe Genauigkeit)',
                labelMid: '60 m (ausgewogen)',
                labelHigh: '200 m (geringe Genauigkeit)',
                suffix: ' m'
            }
        },
        tripClassification: {
            title: 'Einstufung von Fahrten',
            description: 'Lege fest, wie Fahrten erkannt und nach Fortbewegungsart eingestuft werden',
            priorityBanner: {
                title: 'Reihenfolge der Einstufung',
                steps: {
                    flight: 'FLUG',
                    boat: 'BOOT',
                    train: 'ZUG',
                    bicycle: 'FAHRRAD',
                    running: 'LAUFEN',
                    motorVehicle: 'KRAFTFAHRZEUG',
                    walk: 'ZU FUSS',
                    unknown: 'UNBEKANNT'
                },
                description: 'Fahrten werden in dieser Reihenfolge von oben nach unten geprüft. Sobald eine passt, ist die Einstufung fertig. Die Reihenfolge wertet Wasser- und Geschwindigkeitshinweise vorsichtig aus – z. B. bleiben Flüge über Wasser FLUG, und bei überlappenden Geschwindigkeiten an Land greift FAHRRAD vor LAUFEN und Kraftfahrzeugen.'
            },
            algorithm: {
                title: 'Algorithmus zur Fahrterkennung',
                description: 'Der Algorithmus, der Fahrten zwischen Aufenthalten erkennt',
                detailsSingleLabel: 'Einzeln',
                detailsSingleValue: 'Immer genau eine Fahrt zwischen zwei Aufenthalten',
                detailsMultipleLabel: 'Mehrfach',
                detailsMultipleValue: 'Je nach Geschwindigkeit eine oder mehrere Fahrten zwischen Aufenthalten (etwa AUTO → ZU FUSS, ZU FUSS → AUTO usw.)',
                placeholder: 'Algorithmus wählen',
                optionSingle: 'Eine Fahrt',
                optionMultiple: 'Mehrere Fahrten'
            },
            walk: {
                title: 'Zu Fuß',
                subtitle: 'Pflicht-Fortbewegungsart',
                description: 'Erkennt langsame Bewegung zu Fuß (typisch 0–8 km/h)',
                maxAvgSpeed: {
                    label: 'Maximale Durchschnittsgeschwindigkeit',
                    description: 'Fahrten mit höherem Durchschnitt gelten nicht als zu Fuß',
                    labelLow: '3,0 km/h (langsam)',
                    labelMid: '5,5 km/h (normal)',
                    labelHigh: '10,0 km/h (schnell)'
                },
                maxMaxSpeed: {
                    label: 'Maximale Spitzengeschwindigkeit',
                    description: 'Kurze Spitzen über diesem Wert führen zu einer anderen Einstufung',
                    labelLow: '5,0 km/h (zurückhaltend)',
                    labelMid: '8,0 km/h (normal)',
                    labelHigh: '15,0 km/h (großzügig)'
                }
            },
            bicycle: {
                title: 'Fahrrad',
                subtitle: 'Optionale Fortbewegungsart',
                description: 'Erkennt Fahrradfahrten (typisch 8–25 km/h). Die Reihenfolge sorgt für eine richtige Einstufung, auch wenn sich die Geschwindigkeit mit Laufen und Auto überschneidet.',
                minAvgSpeed: {
                    label: 'Minimale Durchschnittsgeschwindigkeit',
                    description: 'Langsamere Fahrten gelten als Laufen oder zu Fuß',
                    labelLow: '5,0 km/h (langsam)',
                    labelMid: '8,0 km/h (Standard)',
                    labelHigh: '15,0 km/h (schnell)'
                },
                maxAvgSpeed: {
                    label: 'Maximale Durchschnittsgeschwindigkeit',
                    description: 'Schnellere Fahrten gelten als motorisiert',
                    labelLow: '15,0 km/h (langsam)',
                    labelMid: '25,0 km/h (Standard)',
                    labelHigh: '35,0 km/h (E-Bike)'
                },
                maxMaxSpeed: {
                    label: 'Maximale Spitzengeschwindigkeit',
                    description: 'Lässt Bergabfahrten, Rennräder oder E-Bikes zu',
                    labelLow: '20,0 km/h (Stadt)',
                    labelMid: '35,0 km/h (Standard)',
                    labelHigh: '80,0 km/h (Rennrad)'
                }
            },
            running: {
                title: 'Laufen',
                subtitle: 'Optionale Fortbewegungsart',
                description: 'Erkennt Laufen und Joggen (typisch 7–14 km/h). Vorsichtige Schwellen trennen Laufen von schnellem Gehen und langsamem Radfahren. Ist es deaktiviert, fallen Laufgeschwindigkeiten unter FAHRRAD (falls aktiviert) oder AUTO/UNBEKANNT.',
                minAvgSpeed: {
                    label: 'Minimale Durchschnittsgeschwindigkeit',
                    description: 'Langsamere Fahrten gelten als zu Fuß',
                    labelLow: '5,0 km/h (langsam)',
                    labelMid: '7,0 km/h (Standard)',
                    labelHigh: '10,0 km/h (schnell)'
                },
                maxAvgSpeed: {
                    label: 'Maximale Durchschnittsgeschwindigkeit',
                    description: 'Schnellere Fahrten gelten als Fahrrad oder motorisiert',
                    labelLow: '10,0 km/h (langsam)',
                    labelMid: '14,0 km/h (Standard)',
                    labelHigh: '18,0 km/h (schnell)'
                },
                maxMaxSpeed: {
                    label: 'Maximale Spitzengeschwindigkeit',
                    description: 'Lässt Sprints zu, bleibt aber unter Radgeschwindigkeit',
                    labelLow: '12,0 km/h (langsam)',
                    labelMid: '18,0 km/h (Standard)',
                    labelHigh: '25,0 km/h (Sprint)'
                }
            },
            car: {
                title: 'Kraftfahrzeug',
                subtitle: 'Bezeichnung Auto, Motorrad oder öffentliche Verkehrsmittel',
                description: 'Erkennt autoähnliche Fahrten über gemeinsame Geschwindigkeitsschwellen für Kraftfahrzeuge. „Öffentliche Verkehrsmittel“ ist nur eine Bezeichnung für solche Fahrten, keine Bus-Erkennung.',
                carLabel: {
                    title: 'Bezeichnung „Auto“',
                    description: 'Erkannte Kraftfahrzeug-Fahrten dürfen als Auto bezeichnet werden.',
                    ariaEnable: 'Bezeichnung „Auto“ aktivieren'
                },
                motorcycleLabel: {
                    title: 'Bezeichnung „Motorrad“',
                    description: 'Erkannte Kraftfahrzeug-Fahrten dürfen als Motorrad bezeichnet werden.',
                    ariaEnable: 'Bezeichnung „Motorrad“ aktivieren'
                },
                publicTransportLabel: {
                    title: 'Bezeichnung „Öffentliche Verkehrsmittel“',
                    description: 'Erkannte autoähnliche Fahrten dürfen als öffentliche Verkehrsmittel bezeichnet werden.',
                    ariaEnable: 'Bezeichnung „Öffentliche Verkehrsmittel“ aktivieren'
                },
                preferredLabel: {
                    label: 'Bevorzugte Bezeichnung für Kraftfahrzeuge',
                    description: 'Sind mehrere Bezeichnungen aktiviert, bekommen erkannte Kraftfahrzeug-Fahrten standardmäßig diese.',
                    placeholder: 'Bevorzugte Bezeichnung wählen',
                    optionCar: 'Auto',
                    optionMotorcycle: 'Motorrad',
                    optionPublicTransport: 'Öffentliche Verkehrsmittel'
                },
                minAvgSpeed: {
                    label: 'Minimale Durchschnittsgeschwindigkeit',
                    description: 'Langsamere Fahrten gelten als zu Fuß, Laufen oder Fahrrad, wenn diese zuerst passen',
                    labelLow: '5,0 km/h (empfindlich)',
                    labelMid: '10,0 km/h (Standard)',
                    labelHigh: '25,0 km/h (zurückhaltend)'
                },
                minMaxSpeed: {
                    label: 'Minimale Spitzengeschwindigkeit',
                    description: 'Fahrten, die diese Geschwindigkeit nie erreichen, gelten nur dann als Kraftfahrzeug, wenn der Durchschnitt passt',
                    labelLow: '10,0 km/h (Stadt)',
                    labelMid: '15,0 km/h (Standard)',
                    labelHigh: '50,0 km/h (Autobahn)'
                }
            },
            train: {
                title: 'Zug',
                subtitle: 'Optionale Fortbewegungsart',
                description: 'Erkennt Zugfahrten an hoher, gleichmäßiger Geschwindigkeit (30–150 km/h). Die Schwankung der Geschwindigkeit unterscheidet sie von Autofahrten.',
                minAvgSpeed: {
                    label: 'Minimale Durchschnittsgeschwindigkeit',
                    description: 'Grenzt Züge von Autos im dichten Verkehr ab',
                    labelLow: '20,0 km/h (Stadt)',
                    labelMid: '30,0 km/h (Standard)',
                    labelHigh: '50,0 km/h (schnell)'
                },
                maxAvgSpeed: {
                    label: 'Maximale Durchschnittsgeschwindigkeit',
                    description: 'Deckt Regional- und Fernzüge ab',
                    labelLow: '80,0 km/h (Regional)',
                    labelMid: '150,0 km/h (Standard)',
                    labelHigh: '400,0 km/h (Hochgeschwindigkeit)'
                },
                minMaxSpeed: {
                    label: 'Minimale Spitzengeschwindigkeit (Bahnhofsfilter)',
                    description: 'Filtert Fahrten heraus, die nur aus Wartezeit am Bahnhof bestehen (wichtig!)',
                    labelLow: '60,0 km/h (tolerant)',
                    labelMid: '80,0 km/h (Standard)',
                    labelHigh: '120,0 km/h (streng)'
                },
                maxMaxSpeed: {
                    label: 'Maximale Spitzengeschwindigkeit',
                    description: 'Obergrenze für Zuggeschwindigkeiten',
                    labelLow: '100,0 km/h (Regional)',
                    labelMid: '180,0 km/h (Standard)',
                    labelHigh: '500,0 km/h (Hochgeschwindigkeit)'
                },
                maxSpeedVariance: {
                    label: 'Maximale Geschwindigkeitsschwankung (entscheidendes Merkmal)',
                    description: 'Züge schwanken wenig (< 15), Autos stark (> 25). Daran lassen sich Züge am besten von Autos unterscheiden!',
                    labelLow: '5,0 (streng)',
                    labelMid: '15,0 (Standard)',
                    labelHigh: '30,0 (tolerant)'
                }
            },
            flight: {
                title: 'Flug',
                subtitle: 'Optionale Fortbewegungsart',
                description: 'Erkennt Flüge an sehr hoher Geschwindigkeit (Durchschnitt ab 400 km/h ODER Spitze ab 500 km/h). Die ODER-Verknüpfung fängt lange Roll- und Bodenzeiten ab. GPS-Ausreißer über 1200 km/h werden automatisch verworfen.',
                minAvgSpeed: {
                    label: 'Minimale Durchschnittsgeschwindigkeit',
                    description: 'Vorsichtiger Standard für typische Flüge (inklusive Rollen, Start und Landung)',
                    labelLow: '100,0 km/h (Regional)',
                    labelMid: '400,0 km/h (Standard)',
                    labelHigh: '600,0 km/h (Langstrecke)'
                },
                minMaxSpeed: {
                    label: 'Minimale Spitzengeschwindigkeit',
                    description: 'Erkennt Flüge mit langen Roll- oder Wartezeiten (ODER-Verknüpfung mit dem Durchschnitt)',
                    labelLow: '150,0 km/h (Kleinflugzeug)',
                    labelMid: '500,0 km/h (Standard)',
                    labelHigh: '900,0 km/h (Jet)'
                },
                minDistance: {
                    label: 'Mindeststrecke',
                    description: 'Kürzere Fahrten werden nie als Flug eingestuft. So werden kurze Fahrten mit GPS-Sprüngen nicht als Flug erkannt (0 = kein Minimum)',
                    labelLow: '0 km (Standard)',
                    labelMid: '250 km',
                    labelHigh: '500 km (streng)'
                },
                altitudeDetection: {
                    title: 'Erkennung über die Höhe',
                    description: 'Stuft eine Fahrt auch dann als Flug ein, wenn sie deutlich über Start- und Endpunkt bleibt und stark steigt oder sinkt. Gedacht für Segelflugzeuge, Ballons und Kleinflugzeuge, die für die Geschwindigkeitsregeln zu langsam sind. Braucht GPS-Höhendaten. Skitage oder Seilbahnrunden können fälschlich als Flug erkannt werden.',
                    ariaEnable: 'Flugerkennung über die Höhe aktivieren'
                },
                altitudeMinHeight: {
                    label: 'Mindesthöhe über Start/Ziel',
                    description: 'Wie hoch über dem höheren von Start- und Zielpunkt die Fahrt mindestens ein Viertel der Zeit bleiben muss. Niedrigere Werte erkennen kürzere Flüge, können aber Fahrten in hügeligem oder bergigem Gelände fälschlich erkennen.',
                    labelLow: '50 m (tolerant)',
                    labelMid: '300 m (Standard)',
                    labelHigh: '5000 m (streng)'
                },
                altitudeMinVerticalSpeed: {
                    label: 'Minimale Vertikalgeschwindigkeit',
                    description: 'Wie schnell die Fahrt steigen oder sinken muss, gemessen über Minutenfenster (90. Perzentil, kurze Spitzen zählen also nicht). Segelflugzeuge und Ballons steigen oder sinken meist mit 1–5 m/s; niedrigere Werte können auch Seilbahnen oder Skilifte erfassen.',
                    labelLow: '0,3 m/s (tolerant)',
                    labelMid: '1,2 m/s (Standard)',
                    labelHigh: '10,0 m/s (streng)'
                }
            },
            boat: {
                title: 'Boot',
                subtitle: 'Optionale Fortbewegungsart',
                description: 'Erkennt Bootsfahrten an anhaltenden Hinweisen auf Wasser. Die Geschwindigkeit dient nur zur Plausibilitätsprüfung, nicht als Beweis.',
                setup: {
                    requiredTitle: 'Boot-Einrichtung erforderlich',
                    readyTitle: 'Boot-Einrichtung bereit',
                    failedTitle: 'Boot-Einrichtung fehlgeschlagen',
                    inProgressTitle: 'Boot-Einrichtung läuft',
                    readyPhaseText: 'Zwischengespeicherte Wasserdaten sind verfügbar.',
                    docsLink: 'Anleitung zur Offline-Einrichtung',
                    retry: 'Erneut versuchen',
                    phases: {
                        BOAT_SETUP_IS_READY: 'Boot-Einrichtung ist bereit',
                        GPS_WATER_EVIDENCE_NEEDS_ENRICHMENT: 'Wasserdaten der GPS-Punkte müssen ergänzt werden',
                        WATER_DATASET_IS_NOT_IMPORTED: 'Gewässerdatensatz ist nicht importiert',
                        STARTING_BOAT_SETUP: 'Boot-Einrichtung wird gestartet',
                        BOAT_SETUP_COMPLETED: 'Boot-Einrichtung abgeschlossen',
                        RECLASSIFYING_EXISTING_TRIPS: 'Vorhandene Fahrten werden neu eingestuft',
                        WATER_DATASET_READY: 'Gewässerdatensatz bereit',
                        PREPARING_WATER_DATASET_ARTIFACT: 'Gewässerdatensatz wird vorbereitet',
                        IMPORTING_WATER_POLYGONS: 'Gewässerflächen werden importiert',
                        USING_LOCAL_WATER_DATASET_FILE: 'Lokale Gewässerdatei wird verwendet',
                        DOWNLOADING_WATER_DATASET: 'Gewässerdatensatz wird heruntergeladen',
                        GPS_WATER_EVIDENCE_READY: 'Wasserdaten der GPS-Punkte bereit',
                        CLASSIFYING_GPS_WATER_EVIDENCE: 'GPS-Punkte werden auf Wasser geprüft',
                        WAITING_FOR_WATER_DATASET_IMPORT: 'Warte auf Import des Gewässerdatensatzes',
                        WAITING_FOR_GPS_WATER_EVIDENCE: 'Warte auf Wasserdaten der GPS-Punkte',
                        BOAT_SETUP_WORKER_DID_NOT_START: 'Hintergrundprozess der Boot-Einrichtung ist nicht gestartet',
                        WATER_DATASET_SETUP_FAILED: 'Einrichtung des Gewässerdatensatzes fehlgeschlagen',
                        BOAT_SETUP_FAILED: 'Boot-Einrichtung fehlgeschlagen',
                        fallback: 'Boot-Einrichtung wird vorbereitet …'
                    },
                    errorFallback: 'Einrichtung des Gewässerdatensatzes fehlgeschlagen. Sieh in der Anleitung zur Offline-Einrichtung nach.'
                },
                minWaterRatio: {
                    label: 'Minimaler Wasseranteil',
                    description: 'Anteil der verwertbaren Strecke, der über Wasser liegen muss',
                    labelLow: '10 % (tolerant)',
                    labelMid: '60 % (Standard)',
                    labelHigh: '100 % (streng)'
                },
                minWaterDistance: {
                    label: 'Minimale Strecke auf dem Wasser',
                    description: 'Gesamtstrecke auf dem Wasser, die nötig ist, damit eine Fahrt als Boot gilt',
                    labelLow: '100 m (tolerant)',
                    labelMid: '2000 m (Standard)',
                    labelHigh: '10000 m (streng)'
                },
                minContinuousWaterDistance: {
                    label: 'Minimale durchgehende Strecke auf dem Wasser',
                    description: 'Filtert kurze Brücken, Tunnel und Uferquerungen heraus',
                    labelLow: '100 m (tolerant)',
                    labelMid: '1000 m (Standard)',
                    labelHigh: '10000 m (streng)'
                },
                maxPlausibleSpeed: {
                    label: 'Maximale plausible Geschwindigkeit',
                    description: 'Nur eine Obergrenze zur Plausibilität; auch langsame Bewegung kann eine Bootsfahrt sein',
                    labelLow: '20 km/h (streng)',
                    labelMid: '120 km/h (Standard)',
                    labelHigh: '300 km/h (tolerant)'
                }
            },
            shortDistance: {
                title: 'Schwelle für kurze Fahrten',
                description: 'Strecke, unterhalb der eine großzügigere Erkennung von Gehgeschwindigkeit greift',
                details: 'Kürzere Fahrten werden bei der Gehgeschwindigkeit etwas toleranter eingestuft, um GPS-Ungenauigkeiten auszugleichen',
                labelLow: '0,1 km (streng)',
                labelMid: '1,0 km (normal)',
                labelHigh: '3,0 km (tolerant)'
            },
            arrivalDetection: {
                title: 'Dauer zur Ankunftserkennung',
                description: 'Wie lange GPS-Punkte mindestens gebündelt und langsam sein müssen, damit eine Ankunft am Ziel erkannt wird',
                detailsLowerLabel: 'Niedrigere Werte',
                detailsLowerValue: 'Empfindlichere Ankunftserkennung, erfasst evtl. auch kurze Halte',
                detailsHigherLabel: 'Höhere Werte',
                detailsHigherValue: 'Zurückhaltender, erkennt nur anhaltende Ankünfte',
                labelLow: '10 s (empfindlich)',
                labelMid: '90 s (normal)',
                labelHigh: '300 s (zurückhaltend)'
            },
            sustainedStop: {
                title: 'Dauer eines echten Halts',
                description: 'Wie lange gleichmäßig langsame Bewegung mindestens dauern muss, damit ein echter Halt erkannt wird (filtert Ampeln heraus)',
                detailsLowerLabel: 'Niedrigere Werte',
                detailsLowerValue: 'Erkennt kürzere Halte, evtl. auch Verkehrsstaus',
                detailsHigherLabel: 'Höhere Werte',
                detailsHigherValue: 'Erkennt nur längere Halte, filtert Verkehr besser',
                labelLow: '10 s (empfindlich)',
                labelMid: '60 s (normal)',
                labelHigh: '600 s (zurückhaltend)'
            },
            arrivalMinPoints: {
                title: 'Mindestzahl an Haltepunkten zur Ankunftserkennung',
                description: 'Wie viele GPS-Punkte mindestens nötig sind, um eine Ankunft am Ziel zu erkennen',
                detailsLowerLabel: 'Niedrigere Werte (2)',
                detailsLowerValue: 'Schnellere Erkennung, ideal bei seltenem GPS (Abstand 10–15 Min.)',
                detailsHigherLabel: 'Höhere Werte (3–4)',
                detailsHigherValue: 'Zuverlässiger, ideal bei häufigem GPS (Abstand 30–60 s)',
                labelLow: '2 Punkte (schnell)',
                labelMid: '3 Punkte (ausgewogen)',
                labelHigh: '5 Punkte (zurückhaltend)'
            }
        }
    },
    reports: {
        page: {
            title: 'Zeitleisten-Berichte',
            subtitle: 'Detaillierte Tabellen deiner Aufenthalte, Fahrten und Datenlücken'
        },
        demoNotice: 'Der Datenexport ist im Demo-Modus deaktiviert.',
        exportAll: 'Alles exportieren',
        exportAllTooltip: 'Aufenthalte, Fahrten und Datenlücken als CSV exportieren',
        demoExportDisabled: 'Der Export ist im Demo-Modus deaktiviert',
        demoExportDisabledToast: 'Der Datenexport ist im Demo-Modus deaktiviert.',
        sections: {
            stays: 'Aufenthalte',
            trips: 'Fahrten',
            dataGaps: 'Datenlücken'
        },
        noDateRange: 'Kein Zeitraum ausgewählt',
        noData: {
            title: 'Keine Daten gefunden',
            message: 'Für den gewählten Zeitraum wurden keine Zeitleistendaten gefunden.',
            suggestion: 'Wähle einen anderen Zeitraum oder prüf deine GPS-Quellen.'
        },
        fetchFailed: 'Zeitleistendaten konnten nicht abgerufen werden',
        export: {
            successSummary: 'Export erfolgreich',
            failedSummary: 'Export fehlgeschlagen',
            staysDone: 'Aufenthalte exportiert',
            staysFailed: 'Aufenthalte konnten nicht exportiert werden',
            tripsDone: 'Fahrten exportiert',
            tripsFailed: 'Fahrten konnten nicht exportiert werden',
            dataGapsDone: 'Datenlücken exportiert',
            dataGapsFailed: 'Datenlücken konnten nicht exportiert werden',
            allDone: 'Alle Daten exportiert',
            failedGeneric: 'Daten konnten nicht exportiert werden'
        }
    }
}

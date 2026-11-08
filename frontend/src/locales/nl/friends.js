/**
 * Friends: live map, timeline, invitations, and management tables.
 *
 * EN values are copied verbatim from the literals they replace: the test suite is pinned to English
 * and asserts exact copy, so these files are a snapshot rather than a chance to reword.
 */
export default {
    sentInvites: {
        header: 'Verzonden Uitnodigingen',
        loading: 'Verzonden uitnodigingen laden...',
        sentAgo: 'Verzonden {time}',
        pending: 'In afwachting',
        cancelTooltip: 'Uitnodiging annuleren',
        cancelAllPending: 'Alle in afwachting Annuleren',
        emptyTitle: 'Geen in afwachting zijnde uitnodigingen',
        emptyMessage: "Wanneer u vriendenverzoeken verzendt, verschijnen ze hier totdat ze worden geaccepteerd of geweigerd.",
        cancelDialog: {
            header: 'Uitnodiging Annuleren',
            message: 'Uitnodiging aan {name} annuleren?',
            note: 'Deze actie kan niet ongedaan worden gemaakt. U kunt later een nieuwe uitnodiging sturen.',
            keep: 'Uitnodiging Behouden',
            confirm: 'Uitnodiging Annuleren'
        }
    },
    receivedInvites: {
        header: 'Ontvangen Uitnodigingen',
        loading: 'Ontvangen uitnodigingen laden...',
        wantsToConnect: 'Wil verbinding met u maken',
        acceptTooltip: 'Uitnodiging accepteren',
        declineTooltip: 'Uitnodiging weigeren',
        acceptAll: 'Alle Accepteren',
        declineAll: 'Alle Weigeren',
        emptyTitle: 'Geen in afwachting zijnde uitnodigingen',
        emptyMessage: 'Vriendenverzoeken van andere gebruikers verschijnen hier. Deel uw gebruikersnaam om uitnodigingen te ontvangen!',
        acceptDialog: {
            header: 'Vriendenverzoek Accepteren',
            message: '{name} als vriend toevoegen?',
            note: "U kunt elkaars locaties op de kaart zien.",
            confirm: 'Vriend Toevoegen'
        },
        rejectDialog: {
            header: 'Vriendenverzoek Weigeren',
            message: 'Vriendenverzoek van {name} weigeren?',
            note: "Deze persoon wordt niet geïnformeerd, maar kan later een nieuw verzoek sturen.",
            keep: 'Verzoek Behouden',
            confirm: 'Weigeren'
        },
        bulkDialog: {
            acceptHeader: 'Alle Verzoeken Accepteren',
            declineHeader: 'Alle Verzoeken Weigeren',
            message: '{action} alle {count} in afwachting zijnde vriendenverzoeken?',
            acceptAction: 'Accepteren',
            declineAction: 'Weigeren',
            acceptNote: 'Alle deze gebruikers worden uw vrienden en zien uw locatie.',
            declineNote: 'Alle in afwachting zijnde verzoeken worden geweigerd. Gebruikers kunnen later nieuwe verzoeken sturen.'
        },
        relativeTime: {
            daysAgo: '{count} dag geleden | {count} dagen geleden',
            hoursAgo: '{count} uur geleden | {count} uur geleden',
            minutesAgo: '{count} minuut geleden | {count} minuten geleden',
            justNow: 'net nu',
            recently: 'recent'
        }
    },
    timelineTab: {
        loading: 'Tijdlijnen laden...',
        selectFriends: 'Selecteer vrienden om hun tijdlijnen te bekijken',
        noSharedTitle: 'Geen Gedeelde Tijdlijnen',
        noSharedMessage: 'Geen van uw vrienden heeft nog tijdlijn delen ingeschakeld. Vraag hen het in te schakelen in Vriendeninstellingen!',
        loadFailedTitle: 'Tijdlijnen Laden is Mislukt',
        loadFailedDetail: 'Vriendentijdlijnen konden niet worden geladen'
    },
    mergedList: {
        header: 'Tijdlijnitems',
        loading: 'Tijdlijnitems laden...',
        empty: 'Geen tijdlijngegevens voor het geselecteerde datumbereik',
        noDataFor: 'Geen gegevens voor {duration}',
        loadMore: 'Meer Laden',
        showingCount: '{shown} van {total} items tonen'
    },
    locationTab: {
        liveLocation: 'Live Locatie',
        timelineHistory: 'Tijdlijn Geschiedenis'
    },
    selection: {
        title: 'Vrienden Selecteren',
        youLabel: '(U)'
    },
    invite: {
        header: 'Een vriend uitnodigen',
        subtitle: 'Stuur een vriendschapsverzoek',
        nameLabel: 'Vriendnaam',
        submit: 'Uitnodigen!'
    },
    list: {
        header: 'Vrienden',
        inviteFriend: 'Vriend Uitnodigen',
        columns: {
            friendName: 'Vriendnaam',
            lastSeen: 'Laatst gezien',
            lastLocation: 'Laatste locatie',
            remove: 'Verwijderen'
        },
        removeTooltip: 'Vriend verwijderen',
        empty: 'Nog geen vrienden. Nodig uw eerste vriend uit om locaties te gaan delen!',
        removeDialog: {
            header: 'Vriend Verwijderen',
            message: '{name} uit uw vrienden verwijderen?',
            note: "U ziet elkaars locaties niet langer. U kunt indien nodig later een nieuw vriendenverzoek sturen.",
            confirm: 'Vriend Verwijderen'
        }
    },
    datePicker: {
        rangeLabel: 'Bereik:',
        placeholder: 'Datumbereik selecteren',
        presetPlaceholder: 'Snelle Presets',
        today: 'Vandaag',
        yesterday: 'Gisteren'
    },
    filters: {
        all: 'Alle',
        none: 'Geen',
        online: 'Online',
        onlineNow: 'Online nu',
        lastSeenRecently: 'Recent gezien'
    },
    demo: {
        invitationsTooltip: 'Uitnodigingen zijn uitgeschakeld in demo-modus'
    },
    listTab: {
        empty: {
            title: 'Nog Geen Vrienden',
            description: 'Begin met het opbouwen van uw netwerk door vrienden uit te nodigen om verbinding te maken en locaties te delen',
            demoDisabled: 'Vrienden uitnodigen is uitgeschakeld in demo-modus.',
            inviteFirst: 'Uw Eerste Vriend Uitnodigen'
        },
        lastSeenLabel: 'Laatst gezien: {text}',
        sharesWithYou: 'Wat deze vriend met u deelt:',
        sharesWithFriend: 'Wat u met deze vriend deelt:',
        liveLocation: 'Live Locatie',
        timelineHistory: 'Tijdlijn Geschiedenis',
        shared: 'Gedeeld',
        notShared: 'Niet Gedeeld',
        demoPermissionsReadOnly: 'Delmachtigingen zijn alleen-lezen in demo-modus.',
        liveLocationInfo: 'Sta deze vriend toe uw huidige locatie in real-time te zien',
        timelineHistoryInfo: 'Sta deze vriend toe uw volledige locatiegeschiedenis te bekijken',
        actions: {
            live: 'Live',
            liveTooltip: 'Vriend op live kaart tonen',
            timeline: 'Tijdlijn',
            timelineTooltip: 'Tijdlijn geschiedenis van vriend bekijken',
            removeTooltip: 'Vriend verwijderen',
            removeTooltipDemo: 'Vrienden verwijderen is uitgeschakeld in demo-modus'
        },
        status: {
            noLocation: 'Geen Locatie',
            online: 'Online',
            recent: 'Recent',
            offline: 'Offline'
        },
        lastSeenText: {
            never: 'Nooit',
            justNow: 'Net nu',
            minutesAgo: '{count}m geleden',
            hoursAgo: '{count}u geleden',
            daysAgo: '{count}d geleden'
        },
        permissionDialog: {
            header: 'Machtigingswijziging Bevestigen',
            timelineAllow: 'Dit stelt {name} in staat uw volledige locatiegeschiedenis (alle eerdere verblijfplaatsen en reizen) te bekijken. Doorgaan?',
            timelineRevoke: "Dit zal {name}'s toegang tot uw locatiegeschiedenis intrekken. Doorgaan?",
            liveAllow: 'Dit stelt {name} in staat uw huidige locatie in real-time te zien. Doorgaan?',
            liveRevoke: "Dit zal {name}'s toegang tot uw live locatie intrekken. Doorgaan?"
        },
        permissionToast: {
            updatedSummary: 'Machtiging Bijgewerkt',
            timelineGranted: '{name} kan nu uw tijdlijn geschiedenis bekijken',
            timelineRevoked: '{name} kan uw tijdlijn geschiedenis niet langer bekijken',
            liveGranted: '{name} kan nu uw live locatie bekijken',
            liveRevoked: '{name} kan uw live locatie niet langer bekijken',
            failedSummary: 'Machtiging Bijwerken is Mislukt',
            timelineFailedDetail: 'Vriendenmachtigingen bijwerken is mislukt',
            liveFailedDetail: 'Live locatie machtiging bijwerken is mislukt'
        }
    },
    mapTab: {
        loading: 'Vriendenkaart laden...',
        noFriends: {
            title: 'Geen Vrienden om te Tonen',
            description: 'Voeg vrienden toe om hun locaties op de kaart te zien',
            demoDisabled: 'Vrienden uitnodigen is uitgeschakeld in demo-modus.',
            invite: 'Vrienden Uitnodigen'
        },
        noLocation: {
            title: 'Geen Locatiegegevens Beschikbaar',
            description: "Uw vrienden hebben nog geen locatie gedeeld. Dit kan zijn omdat ze locatie delen hebben uitgeschakeld of geen locatie tracking apps hebben gebruikt.",
            demoDisabled: 'Meer vrienden uitnodigen is uitgeschakeld in demo-modus.',
            refresh: 'Vernieuwen',
            inviteMore: 'Meer Vrienden Uitnodigen'
        },
        noSelection: {
            title: 'Geen Vrienden Geselecteerd',
            description: 'Kies ten minste één vriend in de filter om locaties op de kaart te tonen.',
            showAll: 'Alle Vrienden Tonen'
        }
    },
    invitationsTab: {
        demoDisabled: 'Uitnodigingsacties zijn uitgeschakeld in demo-modus.',
        received: {
            header: 'Ontvangen Uitnodigingen',
            acceptAll: 'Alle Accepteren',
            rejectAll: 'Alle Weigeren'
        },
        sent: {
            header: 'Verzonden Uitnodigingen',
            cancelAll: 'Alle Annuleren',
            pending: 'In afwachting'
        },
        actions: {
            accept: 'Accepteren',
            reject: 'Weigeren',
            cancel: 'Annuleren'
        },
        empty: {
            title: 'Geen in afwachting zijnde uitnodigingen',
            description: 'Al uw uitnodigingen zijn verwerkt'
        }
    },
    page: {
        demoReadOnlyMessage: 'Demo-modus: vrienduitnodigingen, machtigingswijzigingen en vriendverwijdering zijn uitgeschakeld. Bestaande gedeelde demo-locaties blijven bekijkbaar.',
        demoChangesDisabled: 'Vriendwijzigingen zijn uitgeschakeld in demo-modus.',
        inviteDialog: {
            header: 'Vriend Uitnodigen',
            emailLabel: "E-mailadres of naam van vriend",
            emailPlaceholder: 'Voer e-mailadres in of zoek gebruikers',
            send: 'Uitnodiging Verzenden'
        },
        tabs: {
            live: 'Live',
            timeline: 'Tijdlijn',
            friends: 'Vrienden',
            invitations: 'Uitnodigingen'
        },
        validation: {
            emailRequired: 'E-mailadres is verplicht',
            emailInvalid: 'Voer een geldig e-mailadres in'
        },
        toasts: {
            invitationSentSummary: 'Uitnodiging Verzonden',
            invitationSentDetail: 'Vriendenverzoek verzonden naar {email}',
            invitationFailedSummary: 'Uitnodiging Mislukt',
            invitationFailedDetail: 'Uitnodiging verzenden is mislukt',
            removeFriendConfirm: 'Weet u zeker dat u {name} uit uw vrienden wilt verwijderen?',
            removeFriendHeader: 'Vriend Verwijderen',
            removeFriendConfirmLabel: 'Verwijderen',
            friendRemovedSummary: 'Vriend Verwijderd',
            friendRemovedDetail: 'De vriend is uit uw lijst verwijderd',
            removeFailedSummary: 'Verwijderen Mislukt',
            removeFailedDetail: 'Vriend verwijderen is mislukt',
            acceptedSummary: 'Uitnodiging Geaccepteerd',
            acceptedDetail: 'U bent nu vrienden!',
            acceptFailedSummary: 'Accepteren Mislukt',
            acceptFailedDetail: 'Uitnodiging accepteren is mislukt',
            rejectedSummary: 'Uitnodiging Geweigerd',
            rejectedDetail: 'De uitnodiging is geweigerd',
            rejectFailedSummary: 'Weigeren Mislukt',
            rejectFailedDetail: 'Uitnodiging weigeren is mislukt',
            cancelledSummary: 'Uitnodiging Geannuleerd',
            cancelledDetail: 'De uitnodiging is geannuleerd',
            cancelFailedSummary: 'Annuleren Mislukt',
            cancelFailedDetail: 'Uitnodiging annuleren is mislukt',
            allAcceptedSummary: 'Alle Uitnodigingen Geaccepteerd',
            allAcceptedDetail: '{count} uitnodiging(en) geaccepteerd',
            bulkAcceptFailedSummary: 'Samenvoegen Accepteren Mislukt',
            bulkAcceptFailedDetail: 'Alle uitnodigingen accepteren is mislukt',
            allRejectedSummary: 'Alle Uitnodigingen Geweigerd',
            allRejectedDetail: '{count} uitnodiging(en) geweigerd',
            bulkRejectFailedSummary: 'Samenvoegen Weigeren Mislukt',
            bulkRejectFailedDetail: 'Alle uitnodigingen weigeren is mislukt',
            allCancelledSummary: 'Alle Uitnodigingen Geannuleerd',
            allCancelledDetail: '{count} uitnodiging(en) geannuleerd',
            bulkCancelFailedSummary: 'Samenvoegen Annuleren Mislukt',
            bulkCancelFailedDetail: 'Alle uitnodigingen annuleren is mislukt',
            trailsEnabledSummary: 'Locatiepaden Ingeschakeld',
            trailsEnabledDetailWithPoints: '{points} punten weergeven over {trailCount} vriendspad(en) voor {range}',
            trailsEnabledDetailEmpty: 'Geen padpunten gevonden voor {range}',
            trailLoadFailedSummary: 'Pad Laden Mislukt',
            trailLoadFailedDetail: 'Vriendlocatiepaden laden is mislukt',
            dataRefreshedSummary: 'Gegevens Vernieuwd',
            dataRefreshedDetail: 'Vriendengegevens en locaties zijn bijgewerkt',
            refreshFailedSummary: 'Vernieuwen Mislukt',
            refreshFailedDetail: 'Vriendengegevens vernieuwen is mislukt',
            loadingFailedSummary: 'Laden Mislukt',
            loadingFailedDetail: 'Paginagegevens laden is mislukt'
        }
    },
    liveFilter: {
        selectPlaceholder: 'Selecteer vrienden om te tonen',
        trailDurationLabel: 'Padduur',
        mobileFilterHeader: 'Vrienden Filteren',
        doneButton: 'Klaar',
        friendFallbackLabel: 'Vriend',
        noEmailFallback: 'Geen e-mail',
        summary: {
            none: 'Geen vrienden beschikbaar',
            noneSelected: 'Geen geselecteerd',
            allSelected: 'Alle {count} geselecteerd',
            partialSelected: '{selected} van {total} geselecteerd'
        },
        mobileButton: {
            allSelected: 'Vrienden Filteren',
            partial: 'Filteren ({count})'
        },
        selectionSummary: {
            none: 'Selecteer vrienden',
            all: 'Alle vrienden',
            oneFriend: '1 vriend',
            multiple: '{count} vrienden'
        }
    }
}

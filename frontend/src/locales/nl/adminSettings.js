/**
 * The admin settings catalog (metadata table plus settings tabs).
 *
 * EN values are copied verbatim from the literals they replace: the test suite is pinned to English
 * and asserts exact copy, so these files are a snapshot rather than a chance to reword.
 *
 * `metadata.*` mirrors `SETTING_METADATA` in `src/constants/adminSettingsMetadata.js` one-to-one: each
 * dotted backend setting key (e.g. `map-matching.provider`) becomes a nested path
 * (`metadata.map-matching.provider.label`), so `getSettingMetadata()` can build the t() key by simply
 * interpolating the setting key into the path -- no separate key-mapping table to keep in sync.
 */
export default {
    metadata: {
        system: {
            logging: {
                'application-level': {
                    label: 'Applicatielogniveau',
                    description: 'ERROR, WARN, INFO, of DEBUG. Reset om GEOPULSE_LOG_LEVEL of de applicatiestandaard te gebruiken; TRACE is niet beschikbaar in productie.'
                }
            },
            user: {
                'default-distance-unit': {
                    label: 'Standaard Afstandseenheid',
                    description: 'Afstandseenheid toegewezen aan nieuw aangemaakte gebruikers'
                },
                'default-temperature-unit': {
                    label: 'Standaard Temperatureenheid',
                    description: 'Temperatureenheid toegewezen aan nieuw aangemaakte gebruikers'
                }
            },
            timeline: {
                view: {
                    'item-limit': {
                        label: 'Tijdlijnweergave Itemlimiet',
                        description: 'Maximaal aantal tijdlijnitems dat in één weergaveverzoek wordt geretourneerd'
                    }
                },
                processing: {
                    'thread-pool-size': {
                        label: 'Tijdlijnverwerkingsthreads',
                        description: 'Aantal werkerthreads dat wordt gebruikt voor tijdlijnverwerking'
                    }
                }
            },
            'version-check': {
                'github-api-url': {
                    label: 'Release API-URL',
                    description: 'GitHub-compatibele API URL die wordt gebruikt om te controleren op GeoPulse-updates'
                },
                'release-url': {
                    label: 'Release Pagina URL',
                    description: 'Fallback release-pagina URL die met update-informatie wordt getoond'
                },
                'cache-ttl-minutes': {
                    label: 'Cache TTL bijwerken',
                    description: 'Minuten om versie-updatecontroles in cache te plaatsen'
                },
                'connect-timeout-seconds': {
                    label: 'Verbindingstimeout bijwerken',
                    description: 'Seconden om te wachten bij verbinding met de update API'
                },
                'read-timeout-seconds': {
                    label: 'Leestimeout bijwerken',
                    description: 'Seconden om te wachten op update API-antwoorden'
                }
            },
            'water-dataset': {
                url: {
                    label: 'Water Dataset URL',
                    description: 'Water dataset archief URL die voor Boot-installatie wordt gebruikt'
                },
                sha256: {
                    label: 'Water Dataset Controlesom',
                    description: 'Optionele verwachte SHA-256 controlesom voor het water dataset archief'
                },
                'auto-import': {
                    label: 'Water Auto-Import',
                    description: 'Water dataset automatisch importeren wanneer Boot-installatie dit vereist'
                },
                'connect-timeout-seconds': {
                    label: 'Water verbindingstimeout',
                    description: 'Seconden om te wachten bij verbinding met de water dataset download'
                },
                'download-timeout-hours': {
                    label: 'Water downloadtimeout',
                    description: 'Maximale duur voor een water dataset download'
                },
                'download-stall-timeout-seconds': {
                    label: 'Water stilstandtimeout',
                    description: 'Seconden zonder gedownloade bytes voordat de water download mislukt'
                },
                'setup-start-timeout-minutes': {
                    label: 'Boot-installatie Start Timeout',
                    description: 'Minuten voordat een in de wachtrij geplaatste Boot-installatie taak als mislukt om te starten wordt beschouwd'
                }
            },
            notifications: {
                apprise: {
                    enabled: {
                        label: 'Apprise Meldingen inschakelen',
                        description: 'Bezorging van geofence-waarschuwingen naar Apprise-bestemmingen inschakelen'
                    },
                    'api-url': {
                        label: 'Apprise API URL',
                        description: 'Basis URL van de Apprise API-service (bijv. http://apprise-api:8000)'
                    },
                    'auth-token': {
                        label: 'Apprise API Token',
                        description: 'Optionele API-token/sleutel die wordt gebruikt voor authenticatie bij Apprise (versleuteld)'
                    },
                    'timeout-ms': {
                        label: 'Apprise Timeout (ms)',
                        description: 'HTTP-verzoektimeout bij het verzenden van meldingen naar Apprise'
                    },
                    'verify-tls': {
                        label: 'TLS-certificaten verifiëren',
                        description: 'Of HTTPS-certificaatvalidatie is ingeschakeld voor Apprise-verzoeken'
                    }
                },
                'geofence-events': {
                    cleanup: {
                        enabled: {
                            label: 'Geofence Gebeurtenis Opruimen inschakelen',
                            description: 'Automatisch opruimen van oude geofence-meldingsgebeurtenissen inschakelen'
                        }
                    },
                    'retention-days': {
                        label: 'Bewaartermijn (dagen)',
                        description: 'Verwijder geofence-meldingsgebeurtenissen die ouder zijn dan dit aantal dagen'
                    }
                }
            }
        },
        auth: {
            registration: {
                enabled: {
                    label: 'Registratie ingeschakeld',
                    description: 'Nieuwe gebruikers toestaan te registreren (hoofdschakelaar)'
                }
            },
            'password-registration': {
                enabled: {
                    label: 'Wachtwoordregistratie',
                    description: 'Registratie met e-mail/wachtwoord toestaan'
                }
            },
            oidc: {
                registration: {
                    enabled: {
                        label: 'OIDC Registratie',
                        description: 'Registratie via OIDC-providers toestaan'
                    }
                },
                'auto-link-accounts': {
                    label: 'OIDC-accounts automatisch koppelen',
                    description: 'OIDC-accounts automatisch koppelen op basis van e-mail (beveiligingsrisico)'
                },
                'callback-base-url': {
                    label: 'OIDC Callback Basis URL',
                    description: 'Optionele publieke basis URL die wordt gebruikt om OIDC callback-redirects op te bouwen'
                },
                'rp-logout': {
                    enabled: {
                        label: 'Uit OIDC-provider afmelden',
                        description: 'Wanneer u uit GeoPulse afmeldt, meld u zich ook af bij de OIDC-provider als deze dit ondersteunt'
                    }
                },
                'jwks-cache': {
                    'ttl-hours': {
                        label: 'JWKS Cache TTL',
                        description: 'Uren om OIDC-provider ondertekeningssleutels in cache te plaatsen'
                    }
                },
                cleanup: {
                    'session-states': {
                        enabled: {
                            label: 'OIDC Sessie Opruimen',
                            description: 'Verlopen OIDC inlog sessie-statusrecords opschonen'
                        }
                    }
                },
                login: {
                    enabled: {
                        label: 'OIDC Inloggen',
                        description: 'Inloggen via OIDC-providers toestaan'
                    }
                },
                'auto-launch': {
                    enabled: {
                        label: 'OIDC Inloggen automatisch starten',
                        description: 'Bij uitgeschakeld wachtwoord-inloggen en exact één OIDC-provider, ga rechtstreeks naar die provider vanaf de inlogpagina. Open /login?autoLaunch=0 om de inlogpagina te zien.'
                    }
                }
            },
            login: {
                enabled: {
                    label: 'Inloggen ingeschakeld',
                    description: 'Gebruikers toestaan in te loggen (hoofdschakelaar)'
                }
            },
            'password-login': {
                enabled: {
                    label: 'Wachtwoordinloggen',
                    description: 'Inloggen met e-mail/wachtwoord toestaan'
                }
            },
            'admin-login-bypass': {
                enabled: {
                    label: 'Admin Inloggen omzeilen',
                    description: 'Beheerders toestaan inlogbeperkingen te omzeilen (voorkomt uitsluiting)'
                }
            },
            'guest-root-redirect-to-login': {
                enabled: {
                    label: 'Gasten vanaf root omleiden',
                    description: 'Afgemelde gebruikers van "/" naar "/login" omleiden in plaats van Start te tonen'
                }
            }
        },
        ai: {
            logging: {
                enabled: {
                    label: 'AI-verzoek/antwoordlogboek inschakelen',
                    description: 'Gedetailleerde AI-verzoeken en antwoorden voor debuggen loggen (wijzigingen worden direct doorgevoerd)'
                }
            },
            'chat-memory': {
                'max-messages': {
                    label: 'Chatgeheugen grootte',
                    description: 'Maximaal aantal berichten dat in gespreksgeschiedenis per gebruiker wordt bewaard'
                }
            },
            'tool-result': {
                'max-length': {
                    label: 'Toolresultaat maximale lengte',
                    description: 'Maximaal aantal tekens in toolresultaten om tokenlimietfouten te voorkomen (12000 ≈ 3000 tokens)'
                }
            }
        },
        geocoding: {
            'primary-provider': {
                label: 'Primaire provider',
                description: 'Primaire geocoderingsdienst voor nieuwe opzoeken; bestaande gecachete records blijven ongewijzigd totdat ze worden geherleid'
            },
            'fallback-provider': {
                label: 'Fallback-provider',
                description: 'Fallback voor nieuwe provideroproepen; herschrijft geen bestaande gecachete records'
            },
            'delay-ms': {
                label: 'Verzoekvertraging',
                description: 'Vertraging tussen geocoderingsverzoeken (milliseconden)'
            },
            nominatim: {
                enabled: {
                    label: 'Nominatim',
                    description: 'Nominatim geocoderingsprovider inschakelen'
                },
                'public-host-forward-search-enabled': {
                    label: 'Nominatim Voorwaartse Zoekopdracht',
                    description: 'Nominatim zoekopdracht/automatische aanvulling op openbare nominatim.openstreetmap.org toestaan (zelf-gehoste Nominatim is altijd toegestaan)'
                },
                url: {
                    label: 'Nominatim URL',
                    description: 'Aangepaste Nominatim-server URL (optioneel)'
                },
                language: {
                    label: 'Nominatim Taal',
                    description: 'Taalvoorkeur (BCP 47: en-US, de, uk, ja, enz.)'
                }
            },
            photon: {
                enabled: {
                    label: 'Photon',
                    description: 'Photon geocoderingsprovider inschakelen'
                },
                url: {
                    label: 'Photon URL',
                    description: 'Aangepaste Photon-server URL (optioneel)'
                },
                language: {
                    label: 'Photon Taal',
                    description: 'Photon taalcode (toegestaan: de, pl, el, en, es, fa, fr, it, ja, ko). Laat leeg voor providerstandaard'
                }
            },
            googlemaps: {
                enabled: {
                    label: 'Google Maps',
                    description: 'Google Maps geocoderingsprovider inschakelen'
                },
                'api-key': {
                    label: 'Google Maps API-sleutel',
                    description: 'API-sleutel voor Google Maps (versleuteld, invoeren om bij te werken)'
                },
                language: {
                    label: 'Google Maps Taal',
                    description: 'Zie ondersteunde talen: https://developers.google.com/maps/faq#languagesupport'
                }
            },
            mapbox: {
                enabled: {
                    label: 'Mapbox',
                    description: 'Mapbox geocoderingsprovider inschakelen'
                },
                'access-token': {
                    label: 'Mapbox Toegangstoken',
                    description: 'Toegangstoken voor Mapbox (versleuteld, invoeren om bij te werken)'
                }
            },
            geoapify: {
                enabled: {
                    label: 'Geoapify',
                    description: 'Geoapify geocoderingsprovider inschakelen'
                },
                'api-key': {
                    label: 'Geoapify API-sleutel',
                    description: 'API-sleutel voor Geoapify (versleuteld, invoeren om bij te werken)'
                },
                language: {
                    label: 'Geoapify Taal',
                    description: 'Taalvoorkeur voor Geoapify-antwoorden (optioneel)'
                },
                'delay-ms': {
                    label: 'Geoapify Vertraging',
                    description: 'Vertraging tussen Geoapify-verzoeken (milliseconden)'
                }
            },
            chibigeo: {
                enabled: {
                    label: 'ChibiGeo',
                    description: 'ChibiGeo geocoderingsprovider inschakelen'
                },
                url: {
                    label: 'ChibiGeo URL',
                    description: 'Photon-compatibele ChibiGeo URL'
                },
                'api-key': {
                    label: 'ChibiGeo API-sleutel',
                    description: 'API-sleutel voor ChibiGeo (versleuteld, invoeren om bij te werken)'
                },
                language: {
                    label: 'ChibiGeo Taal',
                    description: 'Photon-compatibele taalcode (toegestaan: de, pl, el, en, es, fa, fr, it, ja, ko). Laat leeg voor providerstandaard'
                },
                'delay-ms': {
                    label: 'ChibiGeo Vertraging',
                    description: 'Vertraging tussen ChibiGeo-verzoeken (milliseconden)'
                }
            },
            cache: {
                'max-bbox-area-km2': {
                    label: 'Cache BBox-limiet',
                    description: 'Maximale provider begrenzingsvakoppervlakte die voor geocoderingscache-overeenkomst wordt geaccepteerd'
                }
            },
            reconcile: {
                item: {
                    'max-attempts': {
                        label: 'Pogingen bij herleiden',
                        description: 'Maximaal aantal pogingen bij het herleiden van één gecachete geocoderingsrecord'
                    }
                },
                'circuit-open-wait-ms': {
                    label: 'Circuit Open Wacht',
                    description: 'Millisekunden om te wachten wanneer provider circuit breaker tijdens herleiden open is'
                },
                'inter-item-delay-ms': {
                    label: 'Herleiden Snelheid',
                    description: 'Vertraging tussen herleidde geocoderingsrecords in milliseconden'
                }
            }
        },
        weather: {
            enabled: {
                label: 'Weer inschakelen',
                description: 'Weer-gedempte gegevens voor tijdlijn verblijfplaatsen en reizen inschakelen'
            },
            'primary-provider': {
                label: 'Primaire provider',
                description: 'Weerprovider die eerst wordt gebruikt voor nieuwe weer-gedempte gegevens'
            },
            'secondary-provider': {
                label: 'Secundaire provider',
                description: 'Optionele fallback-provider die wordt gebruikt wanneer de primaire provider geen gegevens kan retourneren'
            },
            'open-meteo': {
                enabled: {
                    label: 'Open-Meteo inschakelen',
                    description: 'Open-Meteo als selecteerbare weerprovider inschakelen'
                },
                'forecast-url': {
                    label: 'Voorspelling URL',
                    description: 'Open-Meteo voorspelling API basis URL'
                },
                'archive-url': {
                    label: 'Archief URL',
                    description: 'Open-Meteo historisch archief API basis URL'
                },
                'api-key': {
                    label: 'Open-Meteo API-sleutel',
                    description: 'Optionele Open-Meteo API-sleutel (versleuteld)'
                },
                'connect-timeout-seconds': {
                    label: 'Open-Meteo Connect Timeout',
                    description: 'Seconden om te wachten bij het openen van een Open-Meteo-verbinding'
                },
                'read-timeout-seconds': {
                    label: 'Open-Meteo Read Timeout',
                    description: 'Seconden om te wachten op Open-Meteo-antwoorden'
                }
            },
            pirate: {
                enabled: {
                    label: 'Pirate Weather inschakelen',
                    description: 'Pirate Weather als selecteerbare weerprovider inschakelen'
                },
                'base-url': {
                    label: 'Pirate Weather Voorspelling URL',
                    description: 'Pirate Weather voorspelling API basis URL'
                },
                'time-machine-url': {
                    label: 'Pirate Weather Tijdmachine URL',
                    description: 'Pirate Weather historische tijdmachine API basis URL'
                },
                'api-key': {
                    label: 'Pirate Weather API-sleutel',
                    description: 'Pirate Weather API-sleutel (versleuteld)'
                },
                'connect-timeout-seconds': {
                    label: 'Pirate Connect Timeout',
                    description: 'Seconden om te wachten bij het openen van een Pirate Weather-verbinding'
                },
                'read-timeout-seconds': {
                    label: 'Pirate Read Timeout',
                    description: 'Seconden om te wachten op Pirate Weather-antwoorden'
                }
            },
            ongoing: {
                enabled: {
                    label: 'Doorgaande Weer',
                    description: 'Weer-doelen maken voor actuele laatste verblijfplaatsen en reizen'
                },
                'interval-minutes': {
                    label: 'Doorgaand Interval',
                    description: 'Minimum minuten tussen doorgaande weer-gedempte gegevens (minimum 30)'
                }
            },
            backfill: {
                enabled: {
                    label: 'Historisch Weer Backfill',
                    description: 'Historische weer-doelen automatisch ontdekken'
                }
            },
            quota: {
                'daily-request-limit': {
                    label: 'Dagelijkse Verzoeklimiet',
                    description: 'Maximaal aantal providerverzoeken per UTC-dag'
                },
                'ongoing-reserve': {
                    label: 'Doorgaande Reserve',
                    description: 'Verzoeken gereserveerd voor doorgaande weer-gedempte gegevens per dag'
                }
            },
            'coordinate-precision': {
                label: 'Coördinatennauwkeurigheid',
                description: 'Decimaalnauwkeurigheid voor weerlocatiebuckets'
            },
            'failed-target-retry': {
                enabled: {
                    label: 'Mislukte Doelen Opnieuw Proberen',
                    description: 'Verouderde mislukte weer-doelen na afkoeltijd opnieuw proberen'
                },
                'cooldown-hours': {
                    label: 'Mislukte Herprobeer Afkoeling',
                    description: 'Uren voordat een mislukt weer-doel opnieuw kan worden geprobeerd'
                }
            },
            targets: {
                'completed-retention-days': {
                    label: 'Bewaartermijn Voltooide Doelen',
                    description: 'Dagen om voltooide weer-doelrecords te bewaren'
                },
                'failed-retention-days': {
                    label: 'Bewaartermijn Mislukte Doelen',
                    description: 'Dagen om mislukte weer-doelrecords te bewaren'
                },
                'in-progress-timeout-minutes': {
                    label: 'Bezig Timeout',
                    description: 'Minuten voordat bezig zijnde weer-doelen als verouderd worden beschouwd'
                }
            }
        },
        'map-matching': {
            enabled: {
                label: 'Map Matching inschakelen',
                description: 'Globale vereiste voor gebruikersopt-in map matching'
            },
            automatic: {
                enabled: {
                    label: 'Automatische Toekomstige Overeenkomst',
                    description: 'Stabiele nieuwe reizen voor alle gebruikers na de stilteperiode automatisch afstemmen'
                },
                'quiet-period-minutes': {
                    label: 'Stilteperiode',
                    description: 'Minuten na het eindigen van een reis voordat deze automatisch wordt afgestemd'
                }
            },
            backfill: {
                enabled: {
                    label: 'Historische Backfill',
                    description: 'Historische reizen voor alle gebruikers op de achtergrond ontdekken; voortgang is hervatbaar'
                }
            },
            provider: {
                label: 'Provider',
                description: 'Map matching-engine die wordt gebruikt voor reisroute verfijning'
            },
            valhalla: {
                'base-url': {
                    label: 'Valhalla Basis URL',
                    description: 'Basis URL voor de zelf-gehoste Valhalla-service'
                },
                'connect-timeout-seconds': {
                    label: 'Verbindingstimeout',
                    description: 'Seconden om te wachten bij het openen van een Valhalla-verbinding'
                },
                'read-timeout-seconds': {
                    label: 'Leestimeout',
                    description: 'Seconden om te wachten op Valhalla map matching-antwoorden'
                }
            },
            'max-input-points': {
                label: 'Maximale Invoerpunten',
                description: 'Maximaal aantal GPS-punten dat naar Valhalla per reis wordt verzonden'
            },
            'max-trip-duration-hours': {
                label: 'Maximale Reisduur',
                description: 'Reizen langer dan dit aantal uren worden overgeslagen'
            },
            worker: {
                'batch-size': {
                    label: 'Werker Batchgrootte',
                    description: 'Map matching-doelen per geplande werkeruitvoering verwerkt'
                }
            },
            'max-attempts': {
                label: 'Maximale Pogingen',
                description: 'Maximaal aantal herprobeerpogingen voor elk map matching-doel'
            },
            quality: {
                'min-raw-distance-meters': {
                    label: 'Kwaliteitscontrole Afstand',
                    description: 'Minimale ruwe stukafstand voordat gedeeltelijke overeenkomst-kwaliteitscontroles van toepassing zijn'
                },
                'min-distance-coverage-percent': {
                    label: 'Minimale Dekking',
                    description: 'Minimale afgestemde afstand als percentage van ruwe stukafstand'
                },
                'max-discontinuity-percent': {
                    label: 'Maximale Leemtepercentage',
                    description: 'Maximale ongematche leemteafstand tussen afgestemde fragmenten als percentage van ruwe stukafstand'
                },
                'max-short-discontinuity-meters': {
                    label: 'Korte Leemte Toeslag',
                    description: 'Minimale absolute leemte toeslag tussen afgestemde fragmenten'
                }
            }
        },
        panoramax: {
            enabled: {
                label: 'Panoramax-dekking inschakelen',
                description: 'Panoramax straatniveau-afbeeldingdekking op tijdlijnkaarten tonen'
            },
            endpoint: {
                label: 'Panoramax STAC Endpoint',
                description: 'Openbare Panoramax STAC API-endpoint die voor dekking en afbeeldingen wordt gebruikt'
            }
        },
        poi: {
            enabled: {
                label: 'Plaatsontdekking inschakelen',
                description: 'Plaatsen voorstellen die de moeite waard zijn om te bezoeken, met foto\'s, bij het plannen van een reis'
            },
            'user-agent': {
                label: 'User-Agent',
                description: 'Worden verzonden naar Wikidata en Commons. Houd het identificerend: een anonieme client is degene die aan snelheidslimieten wordt onderworpen of wordt geblokkeerd'
            },
            language: {
                label: 'Voorkeurstaal',
                description: 'Taal voor plaatsnamen en beschrijvingen (bijv. en, de, uk)'
            },
            attribution: {
                enabled: {
                    label: 'Toekenning tonen',
                    description: 'Fotocredits en data-toekenning weergeven. Vereist door de Wikimedia-licenties'
                }
            },
            wikidata: {
                endpoint: {
                    label: 'Wikidata Endpoint',
                    description: 'Wikidata Query Service basis URL. Richt op een zelf-gehoste instantie om de publieke snelheidslimieten te vermijden'
                }
            },
            commons: {
                endpoint: {
                    label: 'Commons Endpoint',
                    description: 'Wikimedia Commons API basis URL, gebruikt om fotocredits op te lossen'
                },
                'thumb-width': {
                    label: 'Foto-miniatuurbreedte',
                    description: 'Aangevraagde miniatuurbreedte in pixels. Commons doet het formaat aanpassen, dus groter betekent langzamer'
                }
            },
            'max-results': {
                label: 'Maximale Resultaten per Gebied',
                description: 'Bovengrens op plaatsen die per gebied van Wikidata worden opgehaald'
            },
            cache: {
                'ttl-days': {
                    label: 'Plaats Cache TTL (dagen)',
                    description: 'Hoe lang gecachete plaatsgegevens worden hergebruikt voordat ze opnieuw worden opgehaald. Langer is vriendelijker voor de gedeelde API'
                },
                'image-ttl-days': {
                    label: 'Foto Cache TTL (dagen)',
                    description: 'Hoe lang gecachete foto-bytes worden hergebruikt voordat ze opnieuw worden opgehaald'
                }
            }
        },
        import: {
            'bulk-insert-batch-size': {
                label: 'Bulk Invoeg Batchgrootte',
                description: 'Aantal GPS-punten dat in één databasebatch wordt ingevoegd'
            },
            'merge-batch-size': {
                label: 'Samenvoeg Batchgrootte',
                description: 'Batchgrootte bij samenvoegen van gegevens met dubbeldetectie'
            },
            'large-file-threshold-mb': {
                label: 'Groot Bestandsdrempel (MB)',
                description: 'Bestanden groter dan dit worden als temp-bestanden in plaats van in het geheugen opgeslagen'
            },
            'temp-file-retention-hours': {
                label: 'Temp-bestand Bewaartermijn (uren)',
                description: 'Hoe lang tijdelijke importbestanden worden bewaard voordat ze worden opgeruimd'
            },
            'drop-folder': {
                enabled: {
                    label: 'Drop-map ingeschakeld',
                    description: 'Drop-map imports inschakelen'
                },
                path: {
                    label: 'Drop-map Pad',
                    description: 'Bestandssysteem-pad voor drop-map imports'
                },
                'poll-interval-seconds': {
                    label: 'Drop Scan Interval (seconden)',
                    description: 'Hoe vaak de drop-map wordt gescand'
                },
                'stable-age-seconds': {
                    label: 'Drop-bestand Stabile Leeftijd (seconden)',
                    description: 'Minimale bestandsleeftijd voordat import begint'
                },
                'geopulse-max-size-mb': {
                    label: 'Drop GeoPulse Max-grootte (MB)',
                    description: 'Maximale GeoPulse ZIP-grootte voor drop imports'
                },
                'runtime-identity': {
                    label: 'Drop-map Procesidentiteit',
                    description: 'Effectieve gebruiker/groep die het backend-proces uitvoert (alleen-lezen)'
                }
            },
            'chunk-size-mb': {
                label: 'Stukgrootte (MB)',
                description: 'Grootte van elk uploadgedeelte voor grote bestandsuploads'
            },
            'max-file-size-gb': {
                label: 'Maximale Bestandsgrootte (GB)',
                description: 'Maximale bestandsgrootte die voor imports is toegestaan'
            },
            'upload-timeout-hours': {
                label: 'Upload Timeout (uren)',
                description: 'Hoe lang een uploadsessie geldig blijft'
            },
            'transaction-timeout-minutes': {
                label: 'Import Transactietimeout',
                description: 'Maximale transactieduur voor één importtaak'
            },
            'upload-cleanup-minutes': {
                label: 'Upload Opruim Interval',
                description: 'Hoe vaak verlopen gedeelde uploadsessies worden opgeruimd'
            },
            geonames: {
                cities: {
                    enabled: {
                        label: 'Stad Import ingeschakeld',
                        description: 'GeoNames stad-dataset import inschakelen'
                    },
                    url: {
                        label: 'Stad Dataset URL',
                        description: 'GeoNames stad-dataset ZIP-archief URL'
                    },
                    'batch-size': {
                        label: 'Stad Batchgrootte',
                        description: 'Rijen per GeoNames-stad importbatch verwerkt'
                    },
                    'min-row-threshold': {
                        label: 'Stad Rij Drempel',
                        description: 'Minimaal gestage GeoNames-stad rijen vereist voordat gegevens worden vervangen'
                    },
                    'force-refresh': {
                        label: 'Stad Verversen forceren',
                        description: 'Stadgegevens opnieuw importeren, zelfs wanneer bestaande gegevens de drempel passeren'
                    },
                    'connect-timeout-seconds': {
                        label: 'Stad Connect Timeout',
                        description: 'Seconden om te wachten bij verbinding met de GeoNames-stad download'
                    },
                    'read-timeout-seconds': {
                        label: 'Stad Read Timeout',
                        description: 'Seconden om te wachten op GeoNames-stad download leesbewerkingen'
                    }
                },
                countries: {
                    enabled: {
                        label: 'Land Import ingeschakeld',
                        description: 'GeoNames land-dataset import inschakelen'
                    },
                    url: {
                        label: 'Land Dataset URL',
                        description: 'GeoNames land-dataset URL'
                    },
                    'batch-size': {
                        label: 'Land Batchgrootte',
                        description: 'Rijen per GeoNames-land importbatch verwerkt'
                    },
                    'min-row-threshold': {
                        label: 'Land Rij Drempel',
                        description: 'Minimaal gestage GeoNames-land rijen vereist voordat gegevens worden vervangen'
                    },
                    'force-refresh': {
                        label: 'Land Verversen forceren',
                        description: 'Landgegevens opnieuw importeren, zelfs wanneer bestaande gegevens de drempel passeren'
                    },
                    'connect-timeout-seconds': {
                        label: 'Land Connect Timeout',
                        description: 'Seconden om te wachten bij verbinding met de GeoNames-land download'
                    },
                    'read-timeout-seconds': {
                        label: 'Land Read Timeout',
                        description: 'Seconden om te wachten op GeoNames-land download leesbewerkingen'
                    }
                }
            },
            'geojson-streaming-batch-size': {
                label: 'GeoJSON Batchgrootte',
                description: 'Batchgrootte voor streaming GeoJSON-parser'
            },
            'googletimeline-streaming-batch-size': {
                label: 'Google Timeline Batchgrootte',
                description: 'Batchgrootte voor streaming Google Timeline-parser'
            },
            'gpx-streaming-batch-size': {
                label: 'GPX Batchgrootte',
                description: 'Batchgrootte voor streaming GPX-parser'
            },
            'csv-streaming-batch-size': {
                label: 'CSV Batchgrootte',
                description: 'Batchgrootte voor streaming CSV-parser'
            },
            'owntracks-streaming-batch-size': {
                label: 'OwnTracks Batchgrootte',
                description: 'Batchgrootte voor streaming OwnTracks-parser'
            }
        },
        export: {
            'max-jobs-per-user': {
                label: 'Maximale taken per gebruiker',
                description: 'Maximaal aantal exporttaken dat een gebruiker tegelijk kan hebben'
            },
            'job-expiry-hours': {
                label: 'Taakverloop (uren)',
                description: 'Uren voordat voltooide exporttaken automatisch worden verwijderd'
            },
            'concurrent-jobs-limit': {
                label: 'Limiet gelijktijdige taken',
                description: 'Maximaal aantal exporttaken dat tegelijk wordt verwerkt'
            },
            'batch-size': {
                label: 'Batchgrootte',
                description: 'Aantal records dat in elke batch tijdens export wordt verwerkt'
            },
            'trip-point-limit': {
                label: 'Reispuntlimiet',
                description: 'Maximaal aantal GPS-punten per enkele reisexport'
            },
            'temp-file-retention-hours': {
                label: 'Temp-bestand Bewaartermijn (uren)',
                description: 'Hoe lang tijdelijke exportbestanden worden bewaard voordat ze worden opgeruimd'
            }
        },
        backup: {
            scheduled: {
                enabled: {
                    label: 'Geplande Back-ups',
                    description: 'Automatische volledige back-ups schrijven naar de lokale back-upmap inschakelen'
                },
                cron: {
                    label: 'Back-upschema Cron',
                    description: 'Cron-expressie die wordt gebruikt om automatische volledige back-ups in te plannen'
                }
            },
            local: {
                path: {
                    label: 'Back-upmap Pad',
                    description: 'Backend-containermap waar lokale volledige back-up ZIP-bestanden worden geschreven'
                }
            },
            retention: {
                count: {
                    label: 'Back-upbewaaraan-telling',
                    description: 'Aantal lokale volledige back-upbestanden dat wordt bewaard'
                }
            },
            operation: {
                'timeout-minutes': {
                    label: 'Back-up Timeout Minuten',
                    description: 'Maximale duur voor volledige back-up- en herstelbewerkingen'
                }
            }
        }
    },
    unitOptions: {
        distanceKilometers: 'Kilometers (km, m)',
        distanceMiles: 'Mijl (mi, ft)',
        temperatureCelsius: 'Celsius (°C)',
        temperatureFahrenheit: 'Fahrenheit (°F)'
    },
    shell: {
        readOnly: 'Alleen-lezen',
        default: 'Standaard',
        reset: 'Resetten',
        plannedFeatures: 'Geplande functies:',
        comingSoon: 'Binnenkort beschikbaar'
    },
    gpsProcessingTab: {
        title: 'GPS-verwerkingsinstellingen',
        description: 'Standaard gedrag voor GPS-gegevensverwerking configureren',
        features: {
            stayDetection: {
                name: 'Verblijfplaatsdetectie-algoritme',
                description: 'Standaardalgoritme voor het detecteren van verblijfplaatsen vanuit GPS-punten'
            },
            accuracyFiltering: {
                name: 'Nauwkeurigheidsfiltering',
                description: 'Minimale GPS-nauwkeurigheidsdrempel voor verwerking'
            },
            batchSize: {
                name: 'Batchgrootte',
                description: 'Aantal GPS-punten dat in elke batch wordt verwerkt'
            },
            distanceThresholds: {
                name: 'Afstandsdrempels',
                description: 'Configureer verblijfplaats/reis-afstandparameters'
            },
            timeWindows: {
                name: 'Tijdvensters',
                description: 'Minimum/maximumtijd voor verblijfplaatsdetectie'
            }
        }
    },
    panoramaxTab: {
        testEndpoint: 'Endpoint testen',
        testSuccess: 'Panoramax STAC-vectortegels gevonden',
        testFailedFallback: 'Endpoint-test is mislukt'
    },
    authenticationTab: {
        title: 'Authenticatie-instellingen',
        oidcAdvanced: 'OIDC Geavanceerd'
    },
    exportTab: {
        jobManagement: 'Taakbeheer',
        batchProcessing: 'Batchverwerking',
        tempFileStorage: 'Tijdelijke bestandsopslag'
    },
    poiTab: {
        testEndpoints: 'Endpoints testen',
        advancedSettings: 'Geavanceerde instellingen',
        endpointsLimitsCaching: 'Endpoints, limieten en caching',
        advancedHint: 'Endpoints kunnen worden gericht op zelf-gehoste instanties. Langere cache-levensduur betekent minder verzoeken aan gedeelde publieke infrastructuur.',
        wikidataOk: 'Wikidata OK',
        wikidataFailed: 'Wikidata mislukt: {detail}',
        commonsOk: 'Commons OK',
        commonsFailed: 'Commons mislukt: {detail}',
        testFailedFallback: 'Endpoint-test is mislukt'
    },
    systemTab: {
        selectDefaultUnit: 'Standaard eenheid selecteren',
        observability: 'Observabiliteit',
        applicationLogging: 'Applicatielogboek',
        useEnvironmentDefault: 'Gebruik omgeving/standaard',
        selectLogLevel: 'Logniveau selecteren',
        configuredLabel: 'Geconfigureerd:',
        effectiveLabel: 'Effectief:',
        sourceLabel: 'Bron:',
        debugLoggingWarning: 'DEBUG-logboek is uitgebreid en moet alleen tijdelijk in productie zijn ingeschakeld.',
        updateCheck: 'Updatecontrole',
        releaseMetadata: 'Releasemetadata',
        waterDataset: 'Water Dataset',
        datasetSource: 'Datasetbron',
        noSettingsTitle: 'Geen Algemene Systeeminstellingen',
        noSettingsDescription: 'Meldingsinstellingen zijn verplaatst naar het Meldingen-tabblad.'
    },
    backupTab: {
        exportSection: {
            title: 'Admin-instellingen export',
            subtitle: 'Draagbare door beheerder beheerde instellingen en providerconfiguratie.',
            panelTitle: 'Instellingen exporteren',
            panelDescription: 'Download een JSON-back-up met draagbare door beheerder beheerde gedrag en providerreferenties.',
            button: 'Instellingen exporteren'
        },
        importSection: {
            panelTitle: 'Instellingen importeren',
            panelDescription: 'Herstel een JSON-back-up en vervang huidige door beheerder beheerde instellingen.',
            chooseFile: 'Backup-bestand kiezen',
            button: 'Instellingen importeren'
        },
        warnMessage: 'De instellingenexport bevat platte tekst API-sleutels, OIDC-clientgeheimen, tokens en aangepaste providerkoppen.',
        infoMessage: 'Instellingenexportbereik is door beheerder beheerd app-gedrag plus OIDC/aangepaste providerconfiguraties.',
        importDialog: {
            header: 'Admin-instellingen vervangen?',
            message: 'Het importeren van dit bestand vervangt huidige globale instellingen, OIDC-providers en aangepaste geocoderingsproviders.',
            note: 'Deploy-infrastructuur en gebruikersdata-exporten worden niet beïnvloed.',
            import: 'Importeren'
        },
        toasts: {
            exportStarted: 'Export gestart',
            exportStartedDetail: 'Admin-instellingen backup-download is gestart.',
            exportFailed: 'Export mislukt',
            exportFailedFallback: 'Kon admin-instellingen backup niet exporteren',
            importComplete: 'Import voltooid',
            importCompleteDetail: '{settings} instellingen, {oidc} OIDC-providers en {custom} aangepaste geocoderingsproviders hersteld.',
            importFailed: 'Import mislukt',
            importFailedFallback: 'Kon admin-instellingen backup niet importeren'
        }
    },
    fullBackupSection: {
        title: 'Volledige App Back-up',
        subtitle: 'Wachtwoordversleutelde PostgreSQL-back-up van toepassingsgegevens en geheimen.',
        downloadPanel: {
            title: 'Volledige back-up downloaden',
            description: 'Genereer een volledige back-up en download deze in deze browser.',
            button: 'Volledige back-up downloaden'
        },
        runNowPanel: {
            title: 'Nu back-up uitvoeren',
            description: 'Maak een back-upbestand in de gekoppelde lokale map.',
            button: 'Nu back-up uitvoeren'
        },
        warnMessage: 'Volledige back-ups worden versleuteld met uw back-upwachtwoord. Bewaar dat wachtwoord buiten GeoPulse: zonder het kan de back-up niet worden hersteld. Herstel alleen vertrouwde back-ups. Herstelvoorbereiding wordt online uitgevoerd; activering stopt GeoPulse kort en start de backend automatisch opnieuw.',
        restorePreparationFailedTitle: 'Herstelvoorbereiding mislukt',
        restorePreparationFailedFallback: 'Herstelvoorbereiding mislukt.',
        originalDatabaseActive: 'De oorspronkelijke database blijft actief en er werden geen herstelde gegevens toegepast.',
        backupFileLabel: 'Back-upbestand: {fileName}',
        activationRetryableFallback: 'Activering is niet voltooid. De oorspronkelijke database is actief en de voorbereide herstel wordt behouden.',
        retryActivation: 'Activering opnieuw proberen',
        discardPreparedRestore: 'Voorbereide herstel verwijderen',
        progress: {
            restoreInProgress: 'Herstel bezig',
            restorePreparationFailed: 'Herstelvoorbereiding mislukt',
            backupInProgress: 'Back-up bezig',
            lastBackupCompleted: 'Laatste back-upbewerking voltooid',
            lastBackupFailed: 'Laatste back-upbewerking mislukt',
            backupStatus: 'Back-upstatus',
            restorePreparingMessage: 'Herstel wordt op de achtergrond voorbereid. GeoPulse blijft beschikbaar tot activering.',
            waitingForStatus: 'Wachten op status'
        },
        scheduledSection: {
            title: 'Geplande Lokale Back-ups',
            description: 'Configureer automatische back-ups die naar de gekoppelde back-upmap worden geschreven.',
            backupPassword: 'Back-upwachtwoord',
            changePasswordLabel: 'Wachtwoord wijzigen (laat leeg om te behouden)',
            passwordRequiredLabel: 'Wachtwoord vereist voor alle volledige back-ups',
            confirmNewPassword: 'Nieuw wachtwoord bevestigen',
            passwordLengthHint: 'Nieuwe wachtwoorden moeten 12–1024 tekens bevatten.',
            schedule: 'Schema',
            enabled: 'Ingeschakeld',
            cron: 'Cron',
            storage: 'Opslag',
            folderPath: 'Map-pad',
            backupsToKeep: 'Back-ups om te bewaren',
            timeoutMinutes: 'Timeout minuten',
            healthAlerts: 'Gezondheidswaarschuwingen',
            warnAfterDays: 'Waarschuwen na dagen',
            healthWarningHint: 'Stel 0 in om back-upversmeldingswaarschuwingen en meldingen uit te schakelen. Actieve beheerders krijgen in-app waarschuwingen wanneer ingeschakeld.',
            sendExternalAlert: 'Externe waarschuwing verzenden',
            appriseRouting: 'Apprise-routing',
            appriseRoutingOptions: {
                destinationUrls: 'Bestemming-URL\'s',
                configKeyAndTag: 'Config-sleutel en -tag'
            },
            configKey: 'Config-sleutel',
            destinationUrls: 'Bestemming-URL\'s',
            tagOptional: 'Tag (optioneel)',
            saveButton: 'Back-upinstellingen opslaan'
        },
        localBackups: {
            title: 'Lokale Back-ups',
            description: 'Bestanden beschikbaar in de geconfigureerde server-side back-upmap.',
            refreshAriaLabel: 'Lokale back-ups vernieuwen',
            columnFile: 'Bestand',
            columnSize: 'Grootte',
            columnModified: 'Gewijzigd',
            columnActions: 'Acties',
            downloadAriaLabel: 'Back-up downloaden',
            restoreAriaLabel: 'Back-up herstellen',
            deleteAriaLabel: 'Back-up verwijderen'
        },
        restoreUploadSection: {
            title: 'Geüploade Volledige Back-up Herstellen',
            description: 'Upload een versleuteld .gpb-back-upbestand. Voorbereiding wordt op de achtergrond uitgevoerd terwijl GeoPulse beschikbaar blijft; activering vervangt vervolgens nieuwere gegevens en start de backend opnieuw.',
            chooseFile: 'Volledige back-up kiezen',
            button: 'Geüploade back-up herstellen'
        },
        restoreDialog: {
            header: 'Volledige back-up herstellen?',
            warning: 'Het herstellen van een volledige back-up kan gebruikers, app-instellingen, GPS-gegevens, vrienden en machtigingen vervangen.',
            note: 'Voorbereiding wordt uitgevoerd terwijl GeoPulse beschikbaar blijft. Activering blokkeert kort de applicatiewerkzaamheden, vervangt nieuwere gegevens en start de backend automatisch opnieuw.',
            sourcePasswordLabel: 'Bron-back-upwachtwoord',
            restore: 'Herstellen',
            restoring: 'Herstellen'
        },
        deleteDialog: {
            header: 'Back-up verwijderen?',
            message: '{fileName} verwijderen uit de geconfigureerde lokale back-upmap?',
            note: 'Dit verwijdert alleen het server-side back-upbestand.',
            delete: 'Verwijderen'
        },
        toasts: {
            loadFilesFailed: 'Laden mislukt',
            loadFilesFailedFallback: 'Lokale back-ups laden is mislukt',
            loadConfigFailedFallback: 'Back-upinstellingen laden is mislukt',
            downloadStarted: 'Download gestart',
            downloadStartedDetail: 'Volledige back-updownload is gestart.',
            downloadFailed: 'Download mislukt',
            downloadFailedFallback: 'Volledige back-up downloaden is mislukt',
            backupComplete: 'Back-up voltooid',
            backupCompleteDetail: '{fileName} gemaakt',
            backupFailed: 'Back-up mislukt',
            backupFailedFallback: 'Back-up uitvoeren is mislukt',
            saveFailed: 'Opslaan mislukt',
            passwordsDoNotMatch: 'Back-upwachtwoorden komen niet overeen',
            passwordLengthError: 'Nieuw back-upwachtwoord moet 12–1024 tekens bevatten',
            saveConfigFailedFallback: 'Back-upinstellingen opslaan is mislukt',
            settingsSaved: 'Instellingen opgeslagen',
            settingsSavedDetail: 'Back-upinstellingen bijgewerkt.',
            downloadLocalFailedFallback: 'Lokale back-up downloaden is mislukt',
            backupDeleted: 'Back-up verwijderd',
            backupDeletedDetail: '{fileName} verwijderd',
            deleteFailed: 'Verwijderen mislukt',
            deleteFailedFallback: 'Back-up verwijderen is mislukt',
            restoreFailed: 'Herstellen mislukt',
            restorePasswordLengthError: 'Herstelwachtwoord moet 1–1024 tekens bevatten',
            restoreFailedFallback: 'Volledige back-up herstellen is mislukt',
            activationRetryFailed: 'Activering opnieuw proberen mislukt',
            activationRetryFailedFallback: 'Activering opnieuw proberen is mislukt',
            discardFailed: 'Verwijderen mislukt',
            discardFailedFallback: 'Voorbereide herstel verwijderen is mislukt'
        }
    },
    composable: {
        loadFailedSummary: 'Fout',
        loadFailedDetail: '{category}-instellingen laden is mislukt',
        validationErrorSummary: 'Validatiefout',
        updateSuccessSummary: 'Succes',
        settingUpdatedDetail: '{label} bijgewerkt',
        updateErrorSummary: 'Fout',
        updateFailedFallback: 'Instelling bijwerken is mislukt',
        resetSuccessDetail: '{label} gereset naar standaard',
        resetFailedDetail: 'Instelling resetten is mislukt'
    }
}


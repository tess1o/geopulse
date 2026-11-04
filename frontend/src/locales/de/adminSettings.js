/**
 * The admin settings catalog (German): metadata table plus settings tabs.
 *
 * `metadata.*` mirrors `SETTING_METADATA` in `src/constants/adminSettingsMetadata.js` one-to-one; see
 * en/adminSettings.js. Environment variable names and setting keys are never translated.
 */
export default {
    metadata: {
        system: {
            logging: {
                'application-level': {
                    label: 'Log-Level der Anwendung',
                    description: 'ERROR, WARN, INFO oder DEBUG. Zurücksetzen, um GEOPULSE_LOG_LEVEL oder den Standard der Anwendung zu nutzen; TRACE ist im Produktivbetrieb nicht verfügbar.'
                }
            },
            user: {
                'default-distance-unit': {
                    label: 'Standard-Entfernungseinheit',
                    description: 'Entfernungseinheit für neu angelegte Benutzer'
                },
                'default-temperature-unit': {
                    label: 'Standard-Temperatureinheit',
                    description: 'Temperatureinheit für neu angelegte Benutzer'
                }
            },
            timeline: {
                view: {
                    'item-limit': {
                        label: 'Einträge pro Zeitleistenansicht',
                        description: 'Höchstzahl an Einträgen, die eine einzelne Anfrage der Zeitleiste zurückgibt'
                    }
                },
                processing: {
                    'thread-pool-size': {
                        label: 'Threads für die Zeitleiste',
                        description: 'Anzahl der Worker-Threads für die Berechnung der Zeitleiste'
                    }
                }
            },
            'version-check': {
                'github-api-url': {
                    label: 'URL der Release-API',
                    description: 'GitHub-kompatible API-URL, über die nach GeoPulse-Updates gesucht wird'
                },
                'release-url': {
                    label: 'URL der Release-Seite',
                    description: 'Ersatz-URL der Release-Seite, die mit Update-Informationen angezeigt wird'
                },
                'cache-ttl-minutes': {
                    label: 'Cache-Dauer für Updates',
                    description: 'Minuten, die das Ergebnis der Update-Prüfung zwischengespeichert wird'
                },
                'connect-timeout-seconds': {
                    label: 'Verbindungs-Timeout für Updates',
                    description: 'Sekunden, die beim Verbinden mit der Update-API gewartet wird'
                },
                'read-timeout-seconds': {
                    label: 'Lese-Timeout für Updates',
                    description: 'Sekunden, die auf Antworten der Update-API gewartet wird'
                }
            },
            'water-dataset': {
                url: {
                    label: 'URL des Gewässerdatensatzes',
                    description: 'URL des Archivs mit dem Gewässerdatensatz für die Boot-Einrichtung'
                },
                sha256: {
                    label: 'Prüfsumme des Gewässerdatensatzes',
                    description: 'Optionale erwartete SHA-256-Prüfsumme des Archivs'
                },
                'auto-import': {
                    label: 'Gewässer automatisch importieren',
                    description: 'Den Gewässerdatensatz automatisch importieren, wenn die Boot-Einrichtung ihn braucht'
                },
                'connect-timeout-seconds': {
                    label: 'Verbindungs-Timeout für Gewässerdaten',
                    description: 'Sekunden, die beim Verbinden zum Download des Gewässerdatensatzes gewartet wird'
                },
                'download-timeout-hours': {
                    label: 'Download-Timeout für Gewässerdaten',
                    description: 'Höchstdauer für den Download des Gewässerdatensatzes'
                },
                'download-stall-timeout-seconds': {
                    label: 'Timeout bei stockendem Download',
                    description: 'Sekunden ohne empfangene Daten, bevor der Download der Gewässerdaten abbricht'
                },
                'setup-start-timeout-minutes': {
                    label: 'Start-Timeout der Boot-Einrichtung',
                    description: 'Minuten, nach denen ein wartender Job der Boot-Einrichtung als nicht gestartet gilt'
                }
            },
            notifications: {
                apprise: {
                    enabled: {
                        label: 'Apprise-Benachrichtigungen aktivieren',
                        description: 'Geofence-Hinweise an Apprise-Ziele zustellen'
                    },
                    'api-url': {
                        label: 'Apprise-API-URL',
                        description: 'Basis-URL des Apprise-API-Dienstes (zum Beispiel http://apprise-api:8000)'
                    },
                    'auth-token': {
                        label: 'Apprise-API-Token',
                        description: 'Optionales API-Token bzw. Schlüssel für die Anmeldung bei Apprise (verschlüsselt)'
                    },
                    'timeout-ms': {
                        label: 'Apprise-Timeout (ms)',
                        description: 'Timeout für HTTP-Anfragen beim Senden an Apprise'
                    },
                    'verify-tls': {
                        label: 'TLS-Zertifikate prüfen',
                        description: 'Ob HTTPS-Zertifikate bei Anfragen an Apprise geprüft werden'
                    }
                },
                'geofence-events': {
                    cleanup: {
                        enabled: {
                            label: 'Geofence-Ereignisse aufräumen',
                            description: 'Alte Geofence-Ereignisse automatisch löschen'
                        }
                    },
                    'retention-days': {
                        label: 'Aufbewahrung (Tage)',
                        description: 'Geofence-Ereignisse löschen, die älter als diese Anzahl Tage sind'
                    }
                }
            }
        },
        auth: {
            registration: {
                enabled: {
                    label: 'Registrierung aktiviert',
                    description: 'Neue Benutzer dürfen sich registrieren (Hauptschalter)'
                }
            },
            'password-registration': {
                enabled: {
                    label: 'Registrierung mit Passwort',
                    description: 'Registrierung mit E-Mail und Passwort erlauben'
                }
            },
            oidc: {
                registration: {
                    enabled: {
                        label: 'Registrierung über OIDC',
                        description: 'Registrierung über OIDC-Anbieter erlauben'
                    }
                },
                'auto-link-accounts': {
                    label: 'OIDC-Konten automatisch verknüpfen',
                    description: 'OIDC-Konten automatisch anhand der E-Mail-Adresse verknüpfen (Sicherheitsrisiko)'
                },
                'callback-base-url': {
                    label: 'Basis-URL für OIDC-Callbacks',
                    description: 'Optionale öffentliche Basis-URL für die Weiterleitungen nach der OIDC-Anmeldung'
                },
                'rp-logout': {
                    enabled: {
                        label: 'Auch beim OIDC-Anbieter abmelden',
                        description: 'Beim Abmelden von GeoPulse auch beim OIDC-Anbieter abmelden, sofern er das unterstützt'
                    }
                },
                'jwks-cache': {
                    'ttl-hours': {
                        label: 'Cache-Dauer für JWKS',
                        description: 'Stunden, die die Signaturschlüssel der OIDC-Anbieter zwischengespeichert werden'
                    }
                },
                cleanup: {
                    'session-states': {
                        enabled: {
                            label: 'OIDC-Sitzungen aufräumen',
                            description: 'Abgelaufene Sitzungsdaten von OIDC-Anmeldungen löschen'
                        }
                    }
                },
                login: {
                    enabled: {
                        label: 'Anmeldung über OIDC',
                        description: 'Anmeldung über OIDC-Anbieter erlauben'
                    }
                },
                'auto-launch': {
                    enabled: {
                        label: 'OIDC-Anmeldung automatisch starten',
                        description: 'Ist die Anmeldung mit Passwort deaktiviert und genau ein OIDC-Anbieter eingerichtet, geht es von der Anmeldeseite direkt zu diesem Anbieter. Öffne /login?autoLaunch=0, um die Anmeldeseite zu sehen.'
                    }
                }
            },
            login: {
                enabled: {
                    label: 'Anmeldung aktiviert',
                    description: 'Benutzer dürfen sich anmelden (Hauptschalter)'
                }
            },
            'password-login': {
                enabled: {
                    label: 'Anmeldung mit Passwort',
                    description: 'Anmeldung mit E-Mail und Passwort erlauben'
                }
            },
            'admin-login-bypass': {
                enabled: {
                    label: 'Anmeldesperre für Admins umgehen',
                    description: 'Admins dürfen Anmeldebeschränkungen umgehen (verhindert, dass man sich aussperrt)'
                }
            },
            'guest-root-redirect-to-login': {
                enabled: {
                    label: 'Gäste von der Startseite umleiten',
                    description: 'Abgemeldete Besucher von „/“ zu „/login“ umleiten, statt die Startseite zu zeigen'
                }
            }
        },
        ai: {
            logging: {
                enabled: {
                    label: 'KI-Anfragen und -Antworten protokollieren',
                    description: 'Ausführliche KI-Anfragen und -Antworten zur Fehlersuche protokollieren (gilt sofort)'
                }
            },
            'chat-memory': {
                'max-messages': {
                    label: 'Größe des Chat-Gedächtnisses',
                    description: 'Höchstzahl an Nachrichten, die pro Benutzer im Gesprächsverlauf behalten werden'
                }
            },
            'tool-result': {
                'max-length': {
                    label: 'Maximale Länge von Tool-Ergebnissen',
                    description: 'Höchstzahl an Zeichen in Tool-Ergebnissen, um Token-Limits nicht zu überschreiten (12.000 ≈ 3.000 Tokens)'
                }
            }
        },
        geocoding: {
            'primary-provider': {
                label: 'Primärer Anbieter',
                description: 'Primärer Geocodierungsdienst, nur für neue Abfragen; vorhandene Cache-Einträge bleiben unverändert, bis sie abgeglichen werden'
            },
            'fallback-provider': {
                label: 'Ersatzanbieter',
                description: 'Ersatz nur für neue Anbieterabfragen; ändert vorhandene Cache-Einträge nicht'
            },
            'delay-ms': {
                label: 'Verzögerung zwischen Anfragen',
                description: 'Verzögerung zwischen Geocodierungsanfragen (Millisekunden)'
            },
            nominatim: {
                enabled: {
                    label: 'Nominatim',
                    description: 'Geocodierungsanbieter Nominatim aktivieren'
                },
                'public-host-forward-search-enabled': {
                    label: 'Vorwärtssuche mit Nominatim',
                    description: 'Suche und Autovervollständigung über das öffentliche nominatim.openstreetmap.org erlauben (selbst gehostetes Nominatim ist immer erlaubt)'
                },
                url: {
                    label: 'Nominatim-URL',
                    description: 'Eigene URL des Nominatim-Servers (optional)'
                },
                language: {
                    label: 'Sprache für Nominatim',
                    description: 'Bevorzugte Sprache (BCP 47: en-US, de, uk, ja usw.)'
                }
            },
            photon: {
                enabled: {
                    label: 'Photon',
                    description: 'Geocodierungsanbieter Photon aktivieren'
                },
                url: {
                    label: 'Photon-URL',
                    description: 'Eigene URL des Photon-Servers (optional)'
                },
                language: {
                    label: 'Sprache für Photon',
                    description: 'Sprachcode für Photon (erlaubt: de, pl, el, en, es, fa, fr, it, ja, ko). Leer lassen für den Standard des Anbieters'
                }
            },
            googlemaps: {
                enabled: {
                    label: 'Google Maps',
                    description: 'Geocodierungsanbieter Google Maps aktivieren'
                },
                'api-key': {
                    label: 'Google-Maps-API-Schlüssel',
                    description: 'API-Schlüssel für Google Maps (verschlüsselt, zum Ändern neu eingeben)'
                },
                language: {
                    label: 'Sprache für Google Maps',
                    description: 'Unterstützte Sprachen: https://developers.google.com/maps/faq#languagesupport'
                }
            },
            mapbox: {
                enabled: {
                    label: 'Mapbox',
                    description: 'Geocodierungsanbieter Mapbox aktivieren'
                },
                'access-token': {
                    label: 'Mapbox-Zugriffstoken',
                    description: 'Zugriffstoken für Mapbox (verschlüsselt, zum Ändern neu eingeben)'
                }
            },
            geoapify: {
                enabled: {
                    label: 'Geoapify',
                    description: 'Geocodierungsanbieter Geoapify aktivieren'
                },
                'api-key': {
                    label: 'Geoapify-API-Schlüssel',
                    description: 'API-Schlüssel für Geoapify (verschlüsselt, zum Ändern neu eingeben)'
                },
                language: {
                    label: 'Sprache für Geoapify',
                    description: 'Bevorzugte Sprache für Antworten von Geoapify (optional)'
                },
                'delay-ms': {
                    label: 'Verzögerung für Geoapify',
                    description: 'Verzögerung zwischen Anfragen an Geoapify (Millisekunden)'
                }
            },
            chibigeo: {
                enabled: {
                    label: 'ChibiGeo',
                    description: 'Geocodierungsanbieter ChibiGeo aktivieren'
                },
                url: {
                    label: 'ChibiGeo-URL',
                    description: 'Photon-kompatible URL von ChibiGeo'
                },
                'api-key': {
                    label: 'ChibiGeo-API-Schlüssel',
                    description: 'API-Schlüssel für ChibiGeo (verschlüsselt, zum Ändern neu eingeben)'
                },
                language: {
                    label: 'Sprache für ChibiGeo',
                    description: 'Photon-kompatibler Sprachcode (erlaubt: de, pl, el, en, es, fa, fr, it, ja, ko). Leer lassen für den Standard des Anbieters'
                },
                'delay-ms': {
                    label: 'Verzögerung für ChibiGeo',
                    description: 'Verzögerung zwischen Anfragen an ChibiGeo (Millisekunden)'
                }
            },
            cache: {
                'max-bbox-area-km2': {
                    label: 'BBox-Limit für den Cache',
                    description: 'Größte Fläche einer Bounding Box des Anbieters, die für Cache-Treffer akzeptiert wird'
                }
            },
            reconcile: {
                item: {
                    'max-attempts': {
                        label: 'Versuche beim Abgleich',
                        description: 'Höchstzahl an Versuchen beim Abgleich eines Cache-Eintrags'
                    }
                },
                'circuit-open-wait-ms': {
                    label: 'Wartezeit bei offenem Circuit',
                    description: 'Millisekunden Wartezeit, wenn der Circuit Breaker des Anbieters während des Abgleichs offen ist'
                },
                'inter-item-delay-ms': {
                    label: 'Tempo des Abgleichs',
                    description: 'Verzögerung zwischen abgeglichenen Geocodierungseinträgen in Millisekunden'
                }
            }
        },
        weather: {
            enabled: {
                label: 'Wetter aktivieren',
                description: 'Wettermesswerte für Aufenthalte und Strecken der Zeitleiste aktivieren'
            },
            'primary-provider': {
                label: 'Primärer Anbieter',
                description: 'Wetteranbieter, der für neue Messwerte zuerst genutzt wird'
            },
            'secondary-provider': {
                label: 'Zweiter Anbieter',
                description: 'Optionaler Ersatz, wenn der primäre Anbieter keinen Messwert liefern kann'
            },
            'open-meteo': {
                enabled: {
                    label: 'Open-Meteo aktivieren',
                    description: 'Open-Meteo als auswählbaren Wetteranbieter aktivieren'
                },
                'forecast-url': {
                    label: 'URL für Vorhersagen',
                    description: 'Basis-URL der Vorhersage-API von Open-Meteo'
                },
                'archive-url': {
                    label: 'URL für das Archiv',
                    description: 'Basis-URL der historischen Archiv-API von Open-Meteo'
                },
                'api-key': {
                    label: 'Open-Meteo-API-Schlüssel',
                    description: 'Optionaler API-Schlüssel für Open-Meteo (verschlüsselt)'
                },
                'connect-timeout-seconds': {
                    label: 'Verbindungs-Timeout für Open-Meteo',
                    description: 'Sekunden, die beim Verbinden mit Open-Meteo gewartet wird'
                },
                'read-timeout-seconds': {
                    label: 'Lese-Timeout für Open-Meteo',
                    description: 'Sekunden, die auf Antworten von Open-Meteo gewartet wird'
                }
            },
            pirate: {
                enabled: {
                    label: 'Pirate Weather aktivieren',
                    description: 'Pirate Weather als auswählbaren Wetteranbieter aktivieren'
                },
                'base-url': {
                    label: 'Vorhersage-URL von Pirate Weather',
                    description: 'Basis-URL der Vorhersage-API von Pirate Weather'
                },
                'time-machine-url': {
                    label: 'Time-Machine-URL von Pirate Weather',
                    description: 'Basis-URL der historischen Time-Machine-API von Pirate Weather'
                },
                'api-key': {
                    label: 'Pirate-Weather-API-Schlüssel',
                    description: 'API-Schlüssel für Pirate Weather (verschlüsselt)'
                },
                'connect-timeout-seconds': {
                    label: 'Verbindungs-Timeout für Pirate',
                    description: 'Sekunden, die beim Verbinden mit Pirate Weather gewartet wird'
                },
                'read-timeout-seconds': {
                    label: 'Lese-Timeout für Pirate',
                    description: 'Sekunden, die auf Antworten von Pirate Weather gewartet wird'
                }
            },
            ongoing: {
                enabled: {
                    label: 'Laufende Wettererfassung',
                    description: 'Wetterziele für die aktuellen Aufenthalte und Strecken anlegen'
                },
                'interval-minutes': {
                    label: 'Intervall der laufenden Erfassung',
                    description: 'Mindestabstand in Minuten zwischen laufenden Wettermesswerten (mindestens 30)'
                }
            },
            backfill: {
                enabled: {
                    label: 'Historische Wetterdaten nachladen',
                    description: 'Historische Wetterziele automatisch ermitteln'
                }
            },
            quota: {
                'daily-request-limit': {
                    label: 'Tägliches Anfragelimit',
                    description: 'Höchstzahl an Anbieteranfragen pro UTC-Tag'
                },
                'ongoing-reserve': {
                    label: 'Reserve für laufende Erfassung',
                    description: 'Täglich für laufende Wettermesswerte reservierte Anfragen'
                }
            },
            'coordinate-precision': {
                label: 'Koordinatengenauigkeit',
                description: 'Nachkommastellen für die Ortsgruppen der Wetterdaten'
            },
            'failed-target-retry': {
                enabled: {
                    label: 'Fehlgeschlagene Ziele erneut versuchen',
                    description: 'Veraltete fehlgeschlagene Wetterziele nach einer Wartezeit erneut versuchen'
                },
                'cooldown-hours': {
                    label: 'Wartezeit bis zur Wiederholung',
                    description: 'Stunden, bevor ein fehlgeschlagenes Wetterziel erneut versucht werden darf'
                }
            },
            targets: {
                'completed-retention-days': {
                    label: 'Aufbewahrung abgeschlossener Ziele',
                    description: 'Tage, die abgeschlossene Wetterziele aufbewahrt werden'
                },
                'failed-retention-days': {
                    label: 'Aufbewahrung fehlgeschlagener Ziele',
                    description: 'Tage, die fehlgeschlagene Wetterziele aufbewahrt werden'
                },
                'in-progress-timeout-minutes': {
                    label: 'Timeout für laufende Ziele',
                    description: 'Minuten, nach denen laufende Wetterziele als veraltet gelten'
                }
            }
        },
        'map-matching': {
            enabled: {
                label: 'Kartenabgleich aktivieren',
                description: 'Globale Voraussetzung dafür, dass Benutzer den Kartenabgleich einschalten können'
            },
            automatic: {
                enabled: {
                    label: 'Neue Strecken automatisch anpassen',
                    description: 'Abgeschlossene neue Strecken aller Benutzer nach der Ruhezeit anpassen'
                },
                'quiet-period-minutes': {
                    label: 'Ruhezeit',
                    description: 'Minuten nach dem Ende einer Strecke, bevor sie automatisch angepasst wird'
                }
            },
            backfill: {
                enabled: {
                    label: 'Historisches Nachladen',
                    description: 'Vergangene Strecken aller Benutzer im Hintergrund ermitteln; der Fortschritt lässt sich fortsetzen'
                }
            },
            provider: {
                label: 'Anbieter',
                description: 'Engine für den Kartenabgleich der Routen'
            },
            valhalla: {
                'base-url': {
                    label: 'Basis-URL von Valhalla',
                    description: 'Basis-URL des selbst gehosteten Valhalla-Dienstes'
                },
                'connect-timeout-seconds': {
                    label: 'Verbindungs-Timeout',
                    description: 'Sekunden, die beim Verbinden mit Valhalla gewartet wird'
                },
                'read-timeout-seconds': {
                    label: 'Lese-Timeout',
                    description: 'Sekunden, die auf Antworten von Valhalla gewartet wird'
                }
            },
            'max-input-points': {
                label: 'Maximale Eingabepunkte',
                description: 'Höchstzahl an GPS-Punkten, die pro Strecke an Valhalla gehen'
            },
            'max-trip-duration-hours': {
                label: 'Maximale Streckendauer',
                description: 'Strecken, die länger als so viele Stunden dauern, werden übersprungen'
            },
            worker: {
                'batch-size': {
                    label: 'Stapelgröße des Workers',
                    description: 'Ziele, die pro geplantem Worker-Durchlauf verarbeitet werden'
                }
            },
            'max-attempts': {
                label: 'Maximale Versuche',
                description: 'Höchstzahl an Wiederholungen pro Ziel des Kartenabgleichs'
            },
            quality: {
                'min-raw-distance-meters': {
                    label: 'Distanz für die Qualitätsprüfung',
                    description: 'Mindestdistanz eines Rohabschnitts, ab der die Prüfung auf Teiltreffer greift'
                },
                'min-distance-coverage-percent': {
                    label: 'Mindestabdeckung',
                    description: 'Angepasste Distanz mindestens in Prozent der Rohdistanz'
                },
                'max-discontinuity-percent': {
                    label: 'Maximale Lücke in Prozent',
                    description: 'Größte nicht angepasste Lücke zwischen angepassten Teilen in Prozent der Rohdistanz'
                },
                'max-short-discontinuity-meters': {
                    label: 'Erlaubte kurze Lücke',
                    description: 'Absolute Mindestlücke, die zwischen angepassten Teilen erlaubt ist'
                }
            }
        },
        panoramax: {
            enabled: {
                label: 'Panoramax-Abdeckung aktivieren',
                description: 'Abdeckung mit Straßenbildern von Panoramax auf den Zeitleistenkarten anzeigen'
            },
            endpoint: {
                label: 'Panoramax-STAC-Endpunkt',
                description: 'Öffentlicher STAC-API-Endpunkt von Panoramax für Abdeckung und Bilder'
            }
        },
        poi: {
            enabled: {
                label: 'Ortssuche aktivieren',
                description: 'Beim Planen einer Reise sehenswerte Orte mit Fotos vorschlagen'
            },
            'user-agent': {
                label: 'User-Agent',
                description: 'Wird an Wikidata und Commons gesendet. Er sollte dich erkennbar machen: anonyme Clients werden gedrosselt oder gesperrt'
            },
            language: {
                label: 'Bevorzugte Sprache',
                description: 'Sprache für Ortsnamen und Beschreibungen (z. B. en, de, uk)'
            },
            attribution: {
                enabled: {
                    label: 'Quellenangaben zeigen',
                    description: 'Bildnachweise und Datenquellen anzeigen. Die Wikimedia-Lizenzen verlangen das'
                }
            },
            wikidata: {
                endpoint: {
                    label: 'Wikidata-Endpunkt',
                    description: 'Basis-URL des Wikidata Query Service. Zeig auf eine selbst gehostete Instanz, um die öffentlichen Limits zu umgehen'
                }
            },
            commons: {
                endpoint: {
                    label: 'Commons-Endpunkt',
                    description: 'Basis-URL der Wikimedia-Commons-API, über die Bildnachweise ermittelt werden'
                },
                'thumb-width': {
                    label: 'Breite der Vorschaubilder',
                    description: 'Angeforderte Breite der Vorschaubilder in Pixeln. Commons skaliert, größer heißt also langsamer'
                }
            },
            'max-results': {
                label: 'Maximale Treffer pro Gegend',
                description: 'Obergrenze an Orten, die für eine Gegend von Wikidata abgerufen werden'
            },
            cache: {
                'ttl-days': {
                    label: 'Cache-Dauer für Orte (Tage)',
                    description: 'Wie lange zwischengespeicherte Ortsdaten genutzt werden, bevor sie neu abgerufen werden. Länger schont die gemeinsame API'
                },
                'image-ttl-days': {
                    label: 'Cache-Dauer für Fotos (Tage)',
                    description: 'Wie lange zwischengespeicherte Fotos genutzt werden, bevor sie neu abgerufen werden'
                }
            }
        },
        import: {
            'bulk-insert-batch-size': {
                label: 'Stapelgröße beim Masseneinfügen',
                description: 'Anzahl der GPS-Punkte, die in einem Datenbank-Stapel eingefügt werden'
            },
            'merge-batch-size': {
                label: 'Stapelgröße beim Zusammenführen',
                description: 'Stapelgröße beim Zusammenführen mit Duplikaterkennung'
            },
            'large-file-threshold-mb': {
                label: 'Schwelle für große Dateien (MB)',
                description: 'Größere Dateien werden als temporäre Dateien statt im Speicher gehalten'
            },
            'temp-file-retention-hours': {
                label: 'Aufbewahrung temporärer Dateien (Stunden)',
                description: 'Wie lange temporäre Importdateien vor dem Aufräumen behalten werden'
            },
            'drop-folder': {
                enabled: {
                    label: 'Ablageordner aktiviert',
                    description: 'Import über den Ablageordner aktivieren'
                },
                path: {
                    label: 'Pfad des Ablageordners',
                    description: 'Dateisystempfad für Importe über den Ablageordner'
                },
                'poll-interval-seconds': {
                    label: 'Prüfintervall (Sekunden)',
                    description: 'Wie oft der Ablageordner geprüft wird'
                },
                'stable-age-seconds': {
                    label: 'Mindestalter der Datei (Sekunden)',
                    description: 'Wie alt eine Datei mindestens sein muss, bevor der Import beginnt'
                },
                'geopulse-max-size-mb': {
                    label: 'Maximale GeoPulse-Größe (MB)',
                    description: 'Maximale Größe einer GeoPulse-ZIP-Datei im Ablageordner'
                },
                'runtime-identity': {
                    label: 'Prozessidentität für den Ablageordner',
                    description: 'Benutzer/Gruppe, unter der der Backend-Prozess läuft (schreibgeschützt)'
                }
            },
            'chunk-size-mb': {
                label: 'Teilgröße (MB)',
                description: 'Größe jedes Teils beim Hochladen großer Dateien'
            },
            'max-file-size-gb': {
                label: 'Maximale Dateigröße (GB)',
                description: 'Größte erlaubte Dateigröße für Importe'
            },
            'upload-timeout-hours': {
                label: 'Upload-Timeout (Stunden)',
                description: 'Wie lange eine Upload-Sitzung gültig bleibt'
            },
            'transaction-timeout-minutes': {
                label: 'Transaktions-Timeout beim Import',
                description: 'Höchstdauer einer Transaktion pro Importjob'
            },
            'upload-cleanup-minutes': {
                label: 'Aufräumintervall für Uploads',
                description: 'Wie oft abgelaufene Upload-Sitzungen aufgeräumt werden'
            },
            geonames: {
                cities: {
                    enabled: {
                        label: 'Import der Städte aktiviert',
                        description: 'Import des GeoNames-Städtedatensatzes aktivieren'
                    },
                    url: {
                        label: 'URL des Städtedatensatzes',
                        description: 'URL des ZIP-Archivs mit dem GeoNames-Städtedatensatz'
                    },
                    'batch-size': {
                        label: 'Stapelgröße für Städte',
                        description: 'Zeilen pro Stapel beim Import der GeoNames-Städte'
                    },
                    'min-row-threshold': {
                        label: 'Mindestzeilen für Städte',
                        description: 'Mindestzahl vorbereiteter Städtezeilen, bevor vorhandene Daten ersetzt werden'
                    },
                    'force-refresh': {
                        label: 'Städte neu importieren erzwingen',
                        description: 'Städtedaten neu importieren, auch wenn die vorhandenen Daten die Schwelle erfüllen'
                    },
                    'connect-timeout-seconds': {
                        label: 'Verbindungs-Timeout für Städte',
                        description: 'Sekunden, die beim Verbinden zum Download der GeoNames-Städte gewartet wird'
                    },
                    'read-timeout-seconds': {
                        label: 'Lese-Timeout für Städte',
                        description: 'Sekunden, die beim Lesen des Downloads der GeoNames-Städte gewartet wird'
                    }
                },
                countries: {
                    enabled: {
                        label: 'Import der Länder aktiviert',
                        description: 'Import des GeoNames-Länderdatensatzes aktivieren'
                    },
                    url: {
                        label: 'URL des Länderdatensatzes',
                        description: 'URL des GeoNames-Länderdatensatzes'
                    },
                    'batch-size': {
                        label: 'Stapelgröße für Länder',
                        description: 'Zeilen pro Stapel beim Import der GeoNames-Länder'
                    },
                    'min-row-threshold': {
                        label: 'Mindestzeilen für Länder',
                        description: 'Mindestzahl vorbereiteter Länderzeilen, bevor vorhandene Daten ersetzt werden'
                    },
                    'force-refresh': {
                        label: 'Länder neu importieren erzwingen',
                        description: 'Länderdaten neu importieren, auch wenn die vorhandenen Daten die Schwelle erfüllen'
                    },
                    'connect-timeout-seconds': {
                        label: 'Verbindungs-Timeout für Länder',
                        description: 'Sekunden, die beim Verbinden zum Download der GeoNames-Länder gewartet wird'
                    },
                    'read-timeout-seconds': {
                        label: 'Lese-Timeout für Länder',
                        description: 'Sekunden, die beim Lesen des Downloads der GeoNames-Länder gewartet wird'
                    }
                }
            },
            'geojson-streaming-batch-size': {
                label: 'Stapelgröße für GeoJSON',
                description: 'Stapelgröße beim Einlesen von GeoJSON'
            },
            'googletimeline-streaming-batch-size': {
                label: 'Stapelgröße für Google Timeline',
                description: 'Stapelgröße beim Einlesen von Google Timeline'
            },
            'gpx-streaming-batch-size': {
                label: 'Stapelgröße für GPX',
                description: 'Stapelgröße beim Einlesen von GPX'
            },
            'csv-streaming-batch-size': {
                label: 'Stapelgröße für CSV',
                description: 'Stapelgröße beim Einlesen von CSV'
            },
            'owntracks-streaming-batch-size': {
                label: 'Stapelgröße für OwnTracks',
                description: 'Stapelgröße beim Einlesen von OwnTracks'
            }
        },
        export: {
            'max-jobs-per-user': {
                label: 'Maximale Jobs pro Benutzer',
                description: 'Höchstzahl gleichzeitiger Exportjobs pro Benutzer'
            },
            'job-expiry-hours': {
                label: 'Ablauf der Jobs (Stunden)',
                description: 'Stunden, nach denen abgeschlossene Exportjobs automatisch gelöscht werden'
            },
            'concurrent-jobs-limit': {
                label: 'Limit gleichzeitiger Jobs',
                description: 'Höchstzahl an Exportjobs, die gleichzeitig verarbeitet werden'
            },
            'batch-size': {
                label: 'Stapelgröße',
                description: 'Anzahl der Datensätze pro Stapel beim Export'
            },
            'trip-point-limit': {
                label: 'Punktlimit pro Strecke',
                description: 'Höchstzahl an GPS-Punkten beim Export einer einzelnen Strecke'
            },
            'temp-file-retention-hours': {
                label: 'Aufbewahrung temporärer Dateien (Stunden)',
                description: 'Wie lange temporäre Exportdateien vor dem Aufräumen behalten werden'
            }
        },
        backup: {
            scheduled: {
                enabled: {
                    label: 'Geplante Sicherungen',
                    description: 'Automatische vollständige Sicherungen in den lokalen Sicherungsordner schreiben'
                },
                cron: {
                    label: 'Cron-Zeitplan für Sicherungen',
                    description: 'Cron-Ausdruck für automatische vollständige Sicherungen'
                }
            },
            local: {
                path: {
                    label: 'Pfad des Sicherungsordners',
                    description: 'Ordner im Backend-Container, in den die ZIP-Dateien der vollständigen Sicherungen geschrieben werden'
                }
            },
            retention: {
                count: {
                    label: 'Anzahl aufbewahrter Sicherungen',
                    description: 'Wie viele lokale vollständige Sicherungen behalten werden'
                }
            },
            operation: {
                'timeout-minutes': {
                    label: 'Sicherungs-Timeout (Minuten)',
                    description: 'Höchstdauer für vollständige Sicherungen und Wiederherstellungen'
                }
            }
        }
    },
    unitOptions: {
        distanceKilometers: 'Kilometer (km, m)',
        distanceMiles: 'Meilen (mi, ft)',
        temperatureCelsius: 'Celsius (°C)',
        temperatureFahrenheit: 'Fahrenheit (°F)'
    },
    shell: {
        readOnly: 'Schreibgeschützt',
        default: 'Standard',
        reset: 'Zurücksetzen',
        plannedFeatures: 'Geplante Funktionen:',
        comingSoon: 'Demnächst'
    },
    gpsProcessingTab: {
        title: 'GPS-Verarbeitung',
        description: 'Standardverhalten bei der Verarbeitung von GPS-Daten festlegen',
        features: {
            stayDetection: {
                name: 'Algorithmus zur Erkennung von Aufenthalten',
                description: 'Standardalgorithmus, der Aufenthalte aus GPS-Punkten erkennt'
            },
            accuracyFiltering: {
                name: 'Filterung nach Genauigkeit',
                description: 'Mindestgenauigkeit der GPS-Punkte für die Verarbeitung'
            },
            batchSize: {
                name: 'Stapelgröße',
                description: 'Anzahl der GPS-Punkte pro Verarbeitungsstapel'
            },
            distanceThresholds: {
                name: 'Abstandsschwellen',
                description: 'Abstandsparameter für Aufenthalte und Strecken'
            },
            timeWindows: {
                name: 'Zeitfenster',
                description: 'Mindest- und Höchstdauer für die Erkennung von Aufenthalten'
            }
        }
    },
    panoramaxTab: {
        testEndpoint: 'Endpunkt testen',
        testSuccess: 'Panoramax-STAC-Vektorkacheln gefunden',
        testFailedFallback: 'Test des Endpunkts fehlgeschlagen'
    },
    authenticationTab: {
        title: 'Anmeldeeinstellungen',
        oidcAdvanced: 'OIDC (erweitert)'
    },
    exportTab: {
        jobManagement: 'Jobverwaltung',
        batchProcessing: 'Stapelverarbeitung',
        tempFileStorage: 'Temporäre Dateien'
    },
    poiTab: {
        testEndpoints: 'Endpunkte testen',
        advancedSettings: 'Erweiterte Einstellungen',
        endpointsLimitsCaching: 'Endpunkte, Limits und Cache',
        advancedHint: 'Die Endpunkte können auf selbst gehostete Instanzen zeigen. Längere Cache-Dauern bedeuten weniger Anfragen an gemeinsam genutzte öffentliche Infrastruktur.',
        wikidataOk: 'Wikidata OK',
        wikidataFailed: 'Wikidata fehlgeschlagen: {detail}',
        commonsOk: 'Commons OK',
        commonsFailed: 'Commons fehlgeschlagen: {detail}',
        testFailedFallback: 'Test des Endpunkts fehlgeschlagen'
    },
    systemTab: {
        selectDefaultUnit: 'Standardeinheit wählen',
        observability: 'Beobachtbarkeit',
        applicationLogging: 'Protokollierung der Anwendung',
        useEnvironmentDefault: 'Umgebung/Standard verwenden',
        selectLogLevel: 'Log-Level wählen',
        configuredLabel: 'Eingestellt:',
        effectiveLabel: 'Wirksam:',
        sourceLabel: 'Quelle:',
        debugLoggingWarning: 'DEBUG-Protokollierung ist sehr ausführlich und sollte im Produktivbetrieb nur vorübergehend aktiviert werden.',
        updateCheck: 'Update-Prüfung',
        releaseMetadata: 'Release-Metadaten',
        waterDataset: 'Gewässerdatensatz',
        datasetSource: 'Quelle des Datensatzes',
        noSettingsTitle: 'Keine allgemeinen Systemeinstellungen',
        noSettingsDescription: 'Die Benachrichtigungseinstellungen sind in den Tab Benachrichtigungen umgezogen.'
    },
    backupTab: {
        exportSection: {
            title: 'Export der Admin-Einstellungen',
            subtitle: 'Übertragbare Admin-Einstellungen und Anbieterkonfiguration.',
            panelTitle: 'Einstellungen exportieren',
            panelDescription: 'Lade eine JSON-Sicherung mit den übertragbaren Admin-Einstellungen und den Zugangsdaten der Anbieter herunter.',
            button: 'Einstellungen exportieren'
        },
        importSection: {
            panelTitle: 'Einstellungen importieren',
            panelDescription: 'Stelle eine JSON-Sicherung wieder her und ersetze die aktuellen Admin-Einstellungen.',
            chooseFile: 'Sicherungsdatei wählen',
            button: 'Einstellungen importieren'
        },
        warnMessage: 'Der Export der Einstellungen enthält API-Schlüssel, OIDC-Client-Secrets, Tokens und eigene Anbieter-Header im Klartext.',
        infoMessage: 'Der Export umfasst das von der Administration gesteuerte Verhalten der App sowie OIDC- und eigene Anbieterkonfigurationen.',
        importDialog: {
            header: 'Admin-Einstellungen ersetzen?',
            message: 'Der Import dieser Datei ersetzt die aktuellen globalen Einstellungen, OIDC-Anbieter und eigenen Geocodierungsanbieter.',
            note: 'Die Infrastruktur der Installation und Exporte von Benutzerdaten sind nicht betroffen.',
            import: 'Importieren'
        },
        toasts: {
            exportStarted: 'Export gestartet',
            exportStartedDetail: 'Der Download der Sicherung der Admin-Einstellungen hat begonnen.',
            exportFailed: 'Export fehlgeschlagen',
            exportFailedFallback: 'Sicherung der Admin-Einstellungen konnte nicht exportiert werden',
            importComplete: 'Import abgeschlossen',
            importCompleteDetail: '{settings} Einstellungen, {oidc} OIDC-Anbieter und {custom} eigene Geocodierungsanbieter wiederhergestellt.',
            importFailed: 'Import fehlgeschlagen',
            importFailedFallback: 'Sicherung der Admin-Einstellungen konnte nicht importiert werden'
        }
    },
    fullBackupSection: {
        title: 'Vollständige App-Sicherung',
        subtitle: 'Passwortverschlüsselte PostgreSQL-Sicherung der Anwendungsdaten und Geheimnisse.',
        downloadPanel: {
            title: 'Vollständige Sicherung herunterladen',
            description: 'Eine vollständige Sicherung erstellen und in diesem Browser herunterladen.',
            button: 'Vollständige Sicherung herunterladen'
        },
        runNowPanel: {
            title: 'Sicherung jetzt ausführen',
            description: 'Eine Sicherungsdatei im eingebundenen lokalen Ordner erstellen.',
            button: 'Sicherung jetzt ausführen'
        },
        warnMessage: 'Vollständige Sicherungen werden mit deinem Sicherungspasswort verschlüsselt. Bewahre dieses Passwort außerhalb von GeoPulse auf: Ohne es lässt sich die Sicherung nicht wiederherstellen. Stelle nur vertrauenswürdige Sicherungen wieder her. Die Vorbereitung läuft im laufenden Betrieb; bei der Aktivierung hält GeoPulse kurz an und startet das Backend automatisch neu.',
        restorePreparationFailedTitle: 'Vorbereitung der Wiederherstellung fehlgeschlagen',
        restorePreparationFailedFallback: 'Vorbereitung der Wiederherstellung fehlgeschlagen.',
        originalDatabaseActive: 'Die ursprüngliche Datenbank ist weiter aktiv, es wurden keine wiederhergestellten Daten übernommen.',
        backupFileLabel: 'Sicherungsdatei: {fileName}',
        activationRetryableFallback: 'Die Aktivierung wurde nicht abgeschlossen. Die ursprüngliche Datenbank ist aktiv, die vorbereitete Wiederherstellung bleibt erhalten.',
        retryActivation: 'Aktivierung erneut versuchen',
        discardPreparedRestore: 'Vorbereitete Wiederherstellung verwerfen',
        progress: {
            restoreInProgress: 'Wiederherstellung läuft',
            restorePreparationFailed: 'Vorbereitung der Wiederherstellung fehlgeschlagen',
            backupInProgress: 'Sicherung läuft',
            lastBackupCompleted: 'Letzter Sicherungsvorgang abgeschlossen',
            lastBackupFailed: 'Letzter Sicherungsvorgang fehlgeschlagen',
            backupStatus: 'Sicherungsstatus',
            restorePreparingMessage: 'Die Wiederherstellung wird im Hintergrund vorbereitet. GeoPulse bleibt bis zur Aktivierung verfügbar.',
            waitingForStatus: 'Warte auf Status'
        },
        scheduledSection: {
            title: 'Geplante lokale Sicherungen',
            description: 'Automatische Sicherungen in den eingebundenen Sicherungsordner einrichten.',
            backupPassword: 'Sicherungspasswort',
            changePasswordLabel: 'Passwort ändern (leer lassen, um es zu behalten)',
            passwordRequiredLabel: 'Passwort für alle vollständigen Sicherungen erforderlich',
            confirmNewPassword: 'Neues Passwort bestätigen',
            passwordLengthHint: 'Neue Passwörter müssen 12–1.024 Zeichen lang sein.',
            schedule: 'Zeitplan',
            enabled: 'Aktiviert',
            cron: 'Cron',
            storage: 'Speicherort',
            folderPath: 'Ordnerpfad',
            backupsToKeep: 'Aufbewahrte Sicherungen',
            timeoutMinutes: 'Timeout (Minuten)',
            healthAlerts: 'Statushinweise',
            warnAfterDays: 'Warnen nach Tagen',
            healthWarningHint: '0 schaltet Warnungen und Benachrichtigungen zum Alter der Sicherungen ab. Wenn aktiviert, bekommen aktive Admins Hinweise in der App.',
            sendExternalAlert: 'Externen Hinweis senden',
            appriseRouting: 'Apprise-Zustellung',
            appriseRoutingOptions: {
                destinationUrls: 'Ziel-URLs',
                configKeyAndTag: 'Konfigurationsschlüssel und Tag'
            },
            configKey: 'Konfigurationsschlüssel',
            destinationUrls: 'Ziel-URLs',
            tagOptional: 'Schlagwort (optional)',
            saveButton: 'Sicherungseinstellungen speichern'
        },
        localBackups: {
            title: 'Lokale Sicherungen',
            description: 'Dateien im eingerichteten Sicherungsordner auf dem Server.',
            refreshAriaLabel: 'Lokale Sicherungen aktualisieren',
            columnFile: 'Datei',
            columnSize: 'Größe',
            columnModified: 'Geändert',
            columnActions: 'Aktionen',
            downloadAriaLabel: 'Sicherung herunterladen',
            restoreAriaLabel: 'Sicherung wiederherstellen',
            deleteAriaLabel: 'Sicherung löschen'
        },
        restoreUploadSection: {
            title: 'Hochgeladene Sicherung wiederherstellen',
            description: 'Lade eine verschlüsselte .gpb-Sicherung hoch. Die Vorbereitung läuft im Hintergrund, während GeoPulse verfügbar bleibt; die Aktivierung ersetzt dann neuere Daten und startet das Backend neu.',
            chooseFile: 'Vollständige Sicherung wählen',
            button: 'Hochgeladene Sicherung wiederherstellen'
        },
        restoreDialog: {
            header: 'Vollständige Sicherung wiederherstellen?',
            warning: 'Eine vollständige Sicherung kann Benutzer, App-Einstellungen, GPS-Daten, Freunde und Freigaben ersetzen.',
            note: 'Die Vorbereitung läuft, während GeoPulse verfügbar bleibt. Die Aktivierung blockiert die Anwendung kurz, ersetzt neuere Daten und startet das Backend automatisch neu.',
            sourcePasswordLabel: 'Passwort der Sicherung',
            restore: 'Wiederherstellen',
            restoring: 'Wird wiederhergestellt'
        },
        deleteDialog: {
            header: 'Sicherung löschen?',
            message: '{fileName} aus dem lokalen Sicherungsordner löschen?',
            note: 'Dabei wird nur die Sicherungsdatei auf dem Server entfernt.',
            delete: 'Löschen'
        },
        toasts: {
            loadFilesFailed: 'Laden fehlgeschlagen',
            loadFilesFailedFallback: 'Lokale Sicherungen konnten nicht geladen werden',
            loadConfigFailedFallback: 'Sicherungseinstellungen konnten nicht geladen werden',
            downloadStarted: 'Download gestartet',
            downloadStartedDetail: 'Der Download der vollständigen Sicherung hat begonnen.',
            downloadFailed: 'Download fehlgeschlagen',
            downloadFailedFallback: 'Vollständige Sicherung konnte nicht heruntergeladen werden',
            backupComplete: 'Sicherung abgeschlossen',
            backupCompleteDetail: '{fileName} erstellt',
            backupFailed: 'Sicherung fehlgeschlagen',
            backupFailedFallback: 'Sicherung konnte nicht ausgeführt werden',
            saveFailed: 'Speichern fehlgeschlagen',
            passwordsDoNotMatch: 'Die Sicherungspasswörter stimmen nicht überein',
            passwordLengthError: 'Das neue Sicherungspasswort muss 12–1.024 Zeichen lang sein',
            saveConfigFailedFallback: 'Sicherungseinstellungen konnten nicht gespeichert werden',
            settingsSaved: 'Einstellungen gespeichert',
            settingsSavedDetail: 'Sicherungseinstellungen aktualisiert.',
            downloadLocalFailedFallback: 'Lokale Sicherung konnte nicht heruntergeladen werden',
            backupDeleted: 'Sicherung gelöscht',
            backupDeletedDetail: '{fileName} gelöscht',
            deleteFailed: 'Löschen fehlgeschlagen',
            deleteFailedFallback: 'Sicherung konnte nicht gelöscht werden',
            restoreFailed: 'Wiederherstellung fehlgeschlagen',
            restorePasswordLengthError: 'Das Passwort für die Wiederherstellung muss 1–1.024 Zeichen lang sein',
            restoreFailedFallback: 'Vollständige Sicherung konnte nicht wiederhergestellt werden',
            activationRetryFailed: 'Erneute Aktivierung fehlgeschlagen',
            activationRetryFailedFallback: 'Aktivierung konnte nicht erneut versucht werden',
            discardFailed: 'Verwerfen fehlgeschlagen',
            discardFailedFallback: 'Vorbereitete Wiederherstellung konnte nicht verworfen werden'
        }
    },
    composable: {
        loadFailedSummary: 'Fehler',
        loadFailedDetail: 'Einstellungen für {category} konnten nicht geladen werden',
        validationErrorSummary: 'Eingaben prüfen',
        updateSuccessSummary: 'Gespeichert',
        settingUpdatedDetail: '{label} aktualisiert',
        updateErrorSummary: 'Fehler',
        updateFailedFallback: 'Einstellung konnte nicht aktualisiert werden',
        resetSuccessDetail: '{label} auf Standard zurückgesetzt',
        resetFailedDetail: 'Einstellung konnte nicht zurückgesetzt werden'
    }
}

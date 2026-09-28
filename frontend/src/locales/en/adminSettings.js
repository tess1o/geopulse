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
                    label: 'Application Log Level',
                    description: 'ERROR, WARN, INFO, or DEBUG. Reset to use GEOPULSE_LOG_LEVEL or the application default; TRACE is unavailable in production.'
                }
            },
            user: {
                'default-distance-unit': {
                    label: 'Default Distance Unit',
                    description: 'Distance unit assigned to newly created users'
                },
                'default-temperature-unit': {
                    label: 'Default Temperature Unit',
                    description: 'Temperature unit assigned to newly created users'
                }
            },
            timeline: {
                view: {
                    'item-limit': {
                        label: 'Timeline View Item Limit',
                        description: 'Maximum number of timeline items returned in a single view request'
                    }
                },
                processing: {
                    'thread-pool-size': {
                        label: 'Timeline Processing Threads',
                        description: 'Number of worker threads used for timeline processing'
                    }
                }
            },
            'version-check': {
                'github-api-url': {
                    label: 'Release API URL',
                    description: 'GitHub-compatible API URL used to check for GeoPulse updates'
                },
                'release-url': {
                    label: 'Release Page URL',
                    description: 'Fallback release page URL shown with update information'
                },
                'cache-ttl-minutes': {
                    label: 'Update Cache TTL',
                    description: 'Minutes to cache version update checks'
                },
                'connect-timeout-seconds': {
                    label: 'Update Connect Timeout',
                    description: 'Seconds to wait while connecting to the update API'
                },
                'read-timeout-seconds': {
                    label: 'Update Read Timeout',
                    description: 'Seconds to wait for update API responses'
                }
            },
            'water-dataset': {
                url: {
                    label: 'Water Dataset URL',
                    description: 'Water dataset archive URL used for Boat setup'
                },
                sha256: {
                    label: 'Water Dataset Checksum',
                    description: 'Optional expected SHA-256 checksum for the water dataset archive'
                },
                'auto-import': {
                    label: 'Water Auto-Import',
                    description: 'Automatically import the water dataset when Boat setup requires it'
                },
                'connect-timeout-seconds': {
                    label: 'Water Connect Timeout',
                    description: 'Seconds to wait while connecting to the water dataset download'
                },
                'download-timeout-hours': {
                    label: 'Water Download Timeout',
                    description: 'Maximum duration for a water dataset download'
                },
                'download-stall-timeout-seconds': {
                    label: 'Water Stall Timeout',
                    description: 'Seconds without downloaded bytes before the water download fails'
                },
                'setup-start-timeout-minutes': {
                    label: 'Boat Setup Start Timeout',
                    description: 'Minutes before a queued Boat setup job is treated as failed to start'
                }
            },
            notifications: {
                apprise: {
                    enabled: {
                        label: 'Enable Apprise Notifications',
                        description: 'Enable delivery of geofence alerts to Apprise destinations'
                    },
                    'api-url': {
                        label: 'Apprise API URL',
                        description: 'Base URL of the Apprise API service (for example http://apprise-api:8000)'
                    },
                    'auth-token': {
                        label: 'Apprise API Token',
                        description: 'Optional API token/key used for authenticating with Apprise (encrypted)'
                    },
                    'timeout-ms': {
                        label: 'Apprise Timeout (ms)',
                        description: 'HTTP request timeout when sending notifications to Apprise'
                    },
                    'verify-tls': {
                        label: 'Verify TLS Certificates',
                        description: 'Whether HTTPS certificate validation is enabled for Apprise requests'
                    }
                },
                'geofence-events': {
                    cleanup: {
                        enabled: {
                            label: 'Enable Geofence Event Cleanup',
                            description: 'Enable automatic cleanup of old geofence notification events'
                        }
                    },
                    'retention-days': {
                        label: 'Retention (Days)',
                        description: 'Delete geofence notification events older than this number of days'
                    }
                }
            }
        },
        auth: {
            registration: {
                enabled: {
                    label: 'Registration Enabled',
                    description: 'Allow new users to register'
                }
            },
            'password-registration': {
                enabled: {
                    label: 'Password Registration',
                    description: 'Allow registration with email/password'
                }
            },
            oidc: {
                registration: {
                    enabled: {
                        label: 'OIDC Registration',
                        description: 'Allow registration via OIDC providers'
                    }
                },
                'auto-link-accounts': {
                    label: 'Auto-Link OIDC Accounts',
                    description: 'Automatically link OIDC accounts by email (security risk)'
                },
                'callback-base-url': {
                    label: 'OIDC Callback Base URL',
                    description: 'Optional public base URL used to build OIDC callback redirects'
                },
                'jwks-cache': {
                    'ttl-hours': {
                        label: 'JWKS Cache TTL',
                        description: 'Hours to cache OIDC provider signing keys'
                    }
                },
                cleanup: {
                    'session-states': {
                        enabled: {
                            label: 'OIDC Session Cleanup',
                            description: 'Clean up expired OIDC login session state records'
                        }
                    }
                },
                login: {
                    enabled: {
                        label: 'OIDC Login',
                        description: 'Allow login via OIDC providers'
                    }
                }
            },
            login: {
                enabled: {
                    label: 'Login Enabled',
                    description: 'Allow users to log in (master switch)'
                }
            },
            'password-login': {
                enabled: {
                    label: 'Password Login',
                    description: 'Allow login with email/password'
                }
            },
            'admin-login-bypass': {
                enabled: {
                    label: 'Admin Login Bypass',
                    description: 'Allow admins to bypass login restrictions (prevents lockout)'
                }
            },
            'guest-root-redirect-to-login': {
                enabled: {
                    label: 'Redirect Guests From Root',
                    description: 'Redirect signed-out users from "/" to "/login" instead of showing Home'
                }
            }
        },
        ai: {
            logging: {
                enabled: {
                    label: 'Enable AI Request/Response Logging',
                    description: 'Log detailed AI requests and responses for debugging (changes apply immediately)'
                }
            },
            'chat-memory': {
                'max-messages': {
                    label: 'Chat Memory Size',
                    description: 'Maximum number of messages to keep in conversation history per user'
                }
            },
            'tool-result': {
                'max-length': {
                    label: 'Tool Result Max Length',
                    description: 'Maximum characters in tool results to prevent token limit errors (12000 ≈ 3000 tokens)'
                }
            }
        },
        geocoding: {
            'primary-provider': {
                label: 'Primary Provider',
                description: 'Primary geocoding service for new lookups only; existing cached records stay unchanged until reconciled'
            },
            'fallback-provider': {
                label: 'Fallback Provider',
                description: 'Fallback for new provider calls only; does not rewrite existing cached records'
            },
            'delay-ms': {
                label: 'Request Delay',
                description: 'Delay between geocoding requests (milliseconds)'
            },
            nominatim: {
                enabled: {
                    label: 'Nominatim',
                    description: 'Enable Nominatim geocoding provider'
                },
                'public-host-forward-search-enabled': {
                    label: 'Nominatim Forward Search',
                    description: 'Allow Nominatim search/autocomplete on public nominatim.openstreetmap.org (self-hosted Nominatim is always allowed)'
                },
                url: {
                    label: 'Nominatim URL',
                    description: 'Custom Nominatim server URL (optional)'
                },
                language: {
                    label: 'Nominatim Language',
                    description: 'Language preference (BCP 47: en-US, de, uk, ja, etc.)'
                }
            },
            photon: {
                enabled: {
                    label: 'Photon',
                    description: 'Enable Photon geocoding provider'
                },
                url: {
                    label: 'Photon URL',
                    description: 'Custom Photon server URL (optional)'
                },
                language: {
                    label: 'Photon Language',
                    description: 'Photon language code (allowed: de, pl, el, en, es, fa, fr, it, ja, ko). Leave empty for provider default'
                }
            },
            googlemaps: {
                enabled: {
                    label: 'Google Maps',
                    description: 'Enable Google Maps geocoding provider'
                },
                'api-key': {
                    label: 'Google Maps API Key',
                    description: 'API key for Google Maps (encrypted, enter to update)'
                },
                language: {
                    label: 'Google Maps Language',
                    description: 'See supported languages: https://developers.google.com/maps/faq#languagesupport'
                }
            },
            mapbox: {
                enabled: {
                    label: 'Mapbox',
                    description: 'Enable Mapbox geocoding provider'
                },
                'access-token': {
                    label: 'Mapbox Access Token',
                    description: 'Access token for Mapbox (encrypted, enter to update)'
                }
            },
            geoapify: {
                enabled: {
                    label: 'Geoapify',
                    description: 'Enable Geoapify geocoding provider'
                },
                'api-key': {
                    label: 'Geoapify API Key',
                    description: 'API key for Geoapify (encrypted, enter to update)'
                },
                language: {
                    label: 'Geoapify Language',
                    description: 'Language preference for Geoapify responses (optional)'
                },
                'delay-ms': {
                    label: 'Geoapify Delay',
                    description: 'Delay between Geoapify requests (milliseconds)'
                }
            },
            chibigeo: {
                enabled: {
                    label: 'ChibiGeo',
                    description: 'Enable ChibiGeo geocoding provider'
                },
                url: {
                    label: 'ChibiGeo URL',
                    description: 'Photon-compatible ChibiGeo URL'
                },
                'api-key': {
                    label: 'ChibiGeo API Key',
                    description: 'API key for ChibiGeo (encrypted, enter to update)'
                },
                language: {
                    label: 'ChibiGeo Language',
                    description: 'Photon-compatible language code (allowed: de, pl, el, en, es, fa, fr, it, ja, ko). Leave empty for provider default'
                },
                'delay-ms': {
                    label: 'ChibiGeo Delay',
                    description: 'Delay between ChibiGeo requests (milliseconds)'
                }
            },
            cache: {
                'max-bbox-area-km2': {
                    label: 'Cache BBox Limit',
                    description: 'Maximum provider bounding box area accepted for geocoding cache matching'
                }
            },
            reconcile: {
                item: {
                    'max-attempts': {
                        label: 'Reconcile Attempts',
                        description: 'Maximum attempts when reconciling one cached geocoding record'
                    }
                },
                'circuit-open-wait-ms': {
                    label: 'Circuit Open Wait',
                    description: 'Milliseconds to wait when provider circuit breaker is open during reconciliation'
                },
                'inter-item-delay-ms': {
                    label: 'Reconcile Pacing',
                    description: 'Delay between reconciled geocoding records in milliseconds'
                }
            }
        },
        weather: {
            enabled: {
                label: 'Enable Weather',
                description: 'Enable weather samples for timeline stays and trips'
            },
            'primary-provider': {
                label: 'Primary Provider',
                description: 'Weather provider used first for new weather samples'
            },
            'secondary-provider': {
                label: 'Secondary Provider',
                description: 'Optional fallback provider used when the primary provider cannot return a sample'
            },
            'open-meteo': {
                enabled: {
                    label: 'Enable Open-Meteo',
                    description: 'Enable Open-Meteo as a selectable weather provider'
                },
                'forecast-url': {
                    label: 'Forecast URL',
                    description: 'Open-Meteo forecast API base URL'
                },
                'archive-url': {
                    label: 'Archive URL',
                    description: 'Open-Meteo historical archive API base URL'
                },
                'api-key': {
                    label: 'Open-Meteo API Key',
                    description: 'Optional Open-Meteo API key (encrypted)'
                },
                'connect-timeout-seconds': {
                    label: 'Open-Meteo Connect Timeout',
                    description: 'Seconds to wait while opening an Open-Meteo connection'
                },
                'read-timeout-seconds': {
                    label: 'Open-Meteo Read Timeout',
                    description: 'Seconds to wait for Open-Meteo responses'
                }
            },
            pirate: {
                enabled: {
                    label: 'Enable Pirate Weather',
                    description: 'Enable Pirate Weather as a selectable weather provider'
                },
                'base-url': {
                    label: 'Pirate Weather Forecast URL',
                    description: 'Pirate Weather forecast API base URL'
                },
                'time-machine-url': {
                    label: 'Pirate Weather Time Machine URL',
                    description: 'Pirate Weather historical time machine API base URL'
                },
                'api-key': {
                    label: 'Pirate Weather API Key',
                    description: 'Pirate Weather API key (encrypted)'
                },
                'connect-timeout-seconds': {
                    label: 'Pirate Connect Timeout',
                    description: 'Seconds to wait while opening a Pirate Weather connection'
                },
                'read-timeout-seconds': {
                    label: 'Pirate Read Timeout',
                    description: 'Seconds to wait for Pirate Weather responses'
                }
            },
            ongoing: {
                enabled: {
                    label: 'Ongoing Weather',
                    description: 'Create weather targets for active latest stays and trips'
                },
                'interval-minutes': {
                    label: 'Ongoing Interval',
                    description: 'Minimum minutes between ongoing weather samples (minimum 30)'
                }
            },
            backfill: {
                enabled: {
                    label: 'Historical Weather Backfill',
                    description: 'Discover historical weather targets automatically'
                }
            },
            quota: {
                'daily-request-limit': {
                    label: 'Daily Request Limit',
                    description: 'Maximum provider requests per UTC day'
                },
                'ongoing-reserve': {
                    label: 'Ongoing Reserve',
                    description: 'Requests reserved for ongoing weather samples each day'
                }
            },
            'coordinate-precision': {
                label: 'Coordinate Precision',
                description: 'Decimal precision for weather location buckets'
            },
            'failed-target-retry': {
                enabled: {
                    label: 'Retry Failed Targets',
                    description: 'Retry stale failed weather targets after cooldown'
                },
                'cooldown-hours': {
                    label: 'Failed Retry Cooldown',
                    description: 'Hours before a failed weather target can be retried'
                }
            },
            targets: {
                'completed-retention-days': {
                    label: 'Completed Target Retention',
                    description: 'Days to retain completed weather target records'
                },
                'failed-retention-days': {
                    label: 'Failed Target Retention',
                    description: 'Days to retain failed weather target records'
                },
                'in-progress-timeout-minutes': {
                    label: 'In-Progress Target Timeout',
                    description: 'Minutes before in-progress weather targets are considered stale'
                }
            }
        },
        'map-matching': {
            enabled: {
                label: 'Enable Map Matching',
                description: 'Global prerequisite for user opt-in map matching'
            },
            automatic: {
                enabled: {
                    label: 'Automatic Future Matching',
                    description: 'Map-match stable new trips for all users after the quiet period'
                },
                'quiet-period-minutes': {
                    label: 'Quiet Period',
                    description: 'Minutes after a timeline change before automatic matching starts'
                }
            },
            backfill: {
                enabled: {
                    label: 'Historical Backfill',
                    description: 'Discover historical trips for all users in the background; progress is resumable'
                }
            },
            provider: {
                label: 'Provider',
                description: 'Map matching engine used for trip route refinement'
            },
            valhalla: {
                'base-url': {
                    label: 'Valhalla Base URL',
                    description: 'Base URL for the self-hosted Valhalla service'
                },
                'connect-timeout-seconds': {
                    label: 'Connect Timeout',
                    description: 'Seconds to wait while opening a Valhalla connection'
                },
                'read-timeout-seconds': {
                    label: 'Read Timeout',
                    description: 'Seconds to wait for Valhalla map matching responses'
                }
            },
            'max-input-points': {
                label: 'Max Input Points',
                description: 'Maximum GPS points sent to Valhalla per trip'
            },
            'max-trip-duration-hours': {
                label: 'Max Trip Duration',
                description: 'Trips longer than this many hours are skipped'
            },
            worker: {
                'batch-size': {
                    label: 'Worker Batch Size',
                    description: 'Map matching targets processed per scheduled worker run'
                }
            },
            'max-attempts': {
                label: 'Max Attempts',
                description: 'Maximum retry attempts for each map matching target'
            },
            quality: {
                'min-raw-distance-meters': {
                    label: 'Quality Check Distance',
                    description: 'Minimum raw chunk distance before partial-match quality checks apply'
                },
                'min-distance-coverage-percent': {
                    label: 'Minimum Coverage',
                    description: 'Minimum matched distance as a percent of raw chunk distance'
                },
                'max-discontinuity-percent': {
                    label: 'Maximum Gap Percent',
                    description: 'Maximum unmatched gap distance between matched fragments as a percent of raw chunk distance'
                },
                'max-short-discontinuity-meters': {
                    label: 'Short Gap Allowance',
                    description: 'Minimum absolute gap allowance between matched fragments'
                }
            }
        },
        panoramax: {
            enabled: {
                label: 'Enable Panoramax Coverage',
                description: 'Show Panoramax street-level imagery coverage on Timeline maps'
            },
            endpoint: {
                label: 'Panoramax STAC Endpoint',
                description: 'Public Panoramax STAC API endpoint used for coverage and imagery'
            }
        },
        poi: {
            enabled: {
                label: 'Enable Place Discovery',
                description: 'Suggest places worth visiting, with photos, when planning a trip'
            },
            'user-agent': {
                label: 'User-Agent',
                description: 'Sent to Wikidata and Commons. Keep it identifying: an anonymous client is the one that gets rate-limited or blocked'
            },
            language: {
                label: 'Preferred Language',
                description: 'Language for place names and descriptions (e.g. en, de, uk)'
            },
            attribution: {
                enabled: {
                    label: 'Show Attribution',
                    description: 'Display photo credits and data attribution. Required by the Wikimedia licences'
                }
            },
            wikidata: {
                endpoint: {
                    label: 'Wikidata Endpoint',
                    description: 'Wikidata Query Service base URL. Point at a self-hosted instance to avoid the public rate limits'
                }
            },
            commons: {
                endpoint: {
                    label: 'Commons Endpoint',
                    description: 'Wikimedia Commons API base URL, used to resolve photo credits'
                },
                'thumb-width': {
                    label: 'Photo Thumbnail Width',
                    description: 'Requested thumbnail width in pixels. Commons does the resizing, so larger means slower'
                }
            },
            'max-results': {
                label: 'Max Results Per Area',
                description: 'Upper limit on places fetched from Wikidata for one area'
            },
            cache: {
                'ttl-days': {
                    label: 'Place Cache TTL (days)',
                    description: 'How long cached place data is reused before refetching. Longer is kinder to the shared API'
                },
                'image-ttl-days': {
                    label: 'Photo Cache TTL (days)',
                    description: 'How long cached photo bytes are reused before refetching'
                }
            }
        },
        import: {
            'bulk-insert-batch-size': {
                label: 'Bulk Insert Batch Size',
                description: 'Number of GPS points to insert in a single database batch'
            },
            'merge-batch-size': {
                label: 'Merge Batch Size',
                description: 'Batch size when merging data with duplicate detection'
            },
            'large-file-threshold-mb': {
                label: 'Large File Threshold (MB)',
                description: 'Files larger than this are stored as temp files instead of in memory'
            },
            'temp-file-retention-hours': {
                label: 'Temp File Retention (Hours)',
                description: 'How long to keep temporary import files before cleanup'
            },
            'drop-folder': {
                enabled: {
                    label: 'Drop Folder Enabled',
                    description: 'Enable drop folder imports'
                },
                path: {
                    label: 'Drop Folder Path',
                    description: 'Filesystem path for drop folder imports'
                },
                'poll-interval-seconds': {
                    label: 'Drop Scan Interval (Seconds)',
                    description: 'How often to scan the drop folder'
                },
                'stable-age-seconds': {
                    label: 'Drop File Stable Age (Seconds)',
                    description: 'Minimum file age before import begins'
                },
                'geopulse-max-size-mb': {
                    label: 'Drop GeoPulse Max Size (MB)',
                    description: 'Max GeoPulse ZIP size for drop imports'
                },
                'runtime-identity': {
                    label: 'Drop Folder Process Identity',
                    description: 'Effective user/group running the backend process (read-only)'
                }
            },
            'chunk-size-mb': {
                label: 'Chunk Size (MB)',
                description: 'Size of each upload chunk for large file uploads'
            },
            'max-file-size-gb': {
                label: 'Max File Size (GB)',
                description: 'Maximum file size allowed for imports'
            },
            'upload-timeout-hours': {
                label: 'Upload Timeout (Hours)',
                description: 'How long an upload session remains valid'
            },
            'transaction-timeout-minutes': {
                label: 'Import Transaction Timeout',
                description: 'Maximum transaction duration for one import job'
            },
            'upload-cleanup-minutes': {
                label: 'Upload Cleanup Interval',
                description: 'How often expired chunked upload sessions are cleaned up'
            },
            geonames: {
                cities: {
                    enabled: {
                        label: 'City Import Enabled',
                        description: 'Enable GeoNames city dataset import'
                    },
                    url: {
                        label: 'City Dataset URL',
                        description: 'GeoNames city dataset ZIP archive URL'
                    },
                    'batch-size': {
                        label: 'City Batch Size',
                        description: 'Rows processed per GeoNames city import batch'
                    },
                    'min-row-threshold': {
                        label: 'City Row Threshold',
                        description: 'Minimum staged GeoNames city rows required before replacing data'
                    },
                    'force-refresh': {
                        label: 'Force City Refresh',
                        description: 'Reimport city data even when existing data passes the threshold'
                    },
                    'connect-timeout-seconds': {
                        label: 'City Connect Timeout',
                        description: 'Seconds to wait while connecting to the GeoNames city download'
                    },
                    'read-timeout-seconds': {
                        label: 'City Read Timeout',
                        description: 'Seconds to wait for GeoNames city download reads'
                    }
                },
                countries: {
                    enabled: {
                        label: 'Country Import Enabled',
                        description: 'Enable GeoNames country dataset import'
                    },
                    url: {
                        label: 'Country Dataset URL',
                        description: 'GeoNames country dataset URL'
                    },
                    'batch-size': {
                        label: 'Country Batch Size',
                        description: 'Rows processed per GeoNames country import batch'
                    },
                    'min-row-threshold': {
                        label: 'Country Row Threshold',
                        description: 'Minimum staged GeoNames country rows required before replacing data'
                    },
                    'force-refresh': {
                        label: 'Force Country Refresh',
                        description: 'Reimport country data even when existing data passes the threshold'
                    },
                    'connect-timeout-seconds': {
                        label: 'Country Connect Timeout',
                        description: 'Seconds to wait while connecting to the GeoNames country download'
                    },
                    'read-timeout-seconds': {
                        label: 'Country Read Timeout',
                        description: 'Seconds to wait for GeoNames country download reads'
                    }
                }
            },
            'geojson-streaming-batch-size': {
                label: 'GeoJSON Batch Size',
                description: 'Batch size for streaming GeoJSON parser'
            },
            'googletimeline-streaming-batch-size': {
                label: 'Google Timeline Batch Size',
                description: 'Batch size for streaming Google Timeline parser'
            },
            'gpx-streaming-batch-size': {
                label: 'GPX Batch Size',
                description: 'Batch size for streaming GPX parser'
            },
            'csv-streaming-batch-size': {
                label: 'CSV Batch Size',
                description: 'Batch size for streaming CSV parser'
            },
            'owntracks-streaming-batch-size': {
                label: 'OwnTracks Batch Size',
                description: 'Batch size for streaming OwnTracks parser'
            }
        },
        export: {
            'max-jobs-per-user': {
                label: 'Max Jobs Per User',
                description: 'Maximum number of export jobs a user can have at once'
            },
            'job-expiry-hours': {
                label: 'Job Expiry (Hours)',
                description: 'Hours before completed export jobs are automatically deleted'
            },
            'concurrent-jobs-limit': {
                label: 'Concurrent Jobs Limit',
                description: 'Maximum number of export jobs processed simultaneously'
            },
            'batch-size': {
                label: 'Batch Size',
                description: 'Number of records to process in each batch during export'
            },
            'trip-point-limit': {
                label: 'Trip Point Limit',
                description: 'Maximum GPS points per single trip export'
            },
            'temp-file-retention-hours': {
                label: 'Temp File Retention (Hours)',
                description: 'How long to keep temporary export files before cleanup'
            }
        },
        backup: {
            scheduled: {
                enabled: {
                    label: 'Scheduled Backups',
                    description: 'Enable automatic full backups written to the local backup folder'
                },
                cron: {
                    label: 'Backup Schedule Cron',
                    description: 'Cron expression used to schedule automatic full backups'
                }
            },
            local: {
                path: {
                    label: 'Backup Folder Path',
                    description: 'Backend container folder where local full backup ZIP files are written'
                }
            },
            retention: {
                count: {
                    label: 'Backup Retention Count',
                    description: 'Number of local full backup files to keep'
                }
            },
            operation: {
                'timeout-minutes': {
                    label: 'Backup Timeout Minutes',
                    description: 'Maximum duration for full backup and restore operations'
                }
            }
        }
    },
    unitOptions: {
        distanceKilometers: 'Kilometers (km, m)',
        distanceMiles: 'Miles (mi, ft)',
        temperatureCelsius: 'Celsius (°C)',
        temperatureFahrenheit: 'Fahrenheit (°F)'
    },
    shell: {
        readOnly: 'Read-only',
        default: 'Default',
        reset: 'Reset',
        plannedFeatures: 'Planned Features:',
        comingSoon: 'Coming Soon'
    },
    gpsProcessingTab: {
        title: 'GPS Processing Settings',
        description: 'Configure default GPS data processing behavior',
        features: {
            stayDetection: {
                name: 'Stay Detection Algorithm',
                description: 'Default algorithm for detecting stays from GPS points'
            },
            accuracyFiltering: {
                name: 'Accuracy Filtering',
                description: 'Minimum GPS accuracy threshold for processing'
            },
            batchSize: {
                name: 'Batch Size',
                description: 'Number of GPS points to process in each batch'
            },
            distanceThresholds: {
                name: 'Distance Thresholds',
                description: 'Configure stay/trip distance parameters'
            },
            timeWindows: {
                name: 'Time Windows',
                description: 'Minimum/maximum time for stay detection'
            }
        }
    },
    panoramaxTab: {
        testEndpoint: 'Test endpoint',
        testSuccess: 'Panoramax STAC vector tiles found',
        testFailedFallback: 'Endpoint test failed'
    },
    authenticationTab: {
        title: 'Authentication Settings',
        oidcAdvanced: 'OIDC Advanced'
    },
    exportTab: {
        jobManagement: 'Job Management',
        batchProcessing: 'Batch Processing',
        tempFileStorage: 'Temporary File Storage'
    },
    poiTab: {
        testEndpoints: 'Test endpoints',
        advancedSettings: 'Advanced settings',
        endpointsLimitsCaching: 'Endpoints, limits and caching',
        advancedHint: 'Endpoints can be pointed at self-hosted instances. Longer cache lifetimes mean fewer requests to shared public infrastructure.',
        wikidataOk: 'Wikidata OK',
        wikidataFailed: 'Wikidata failed: {detail}',
        commonsOk: 'Commons OK',
        commonsFailed: 'Commons failed: {detail}',
        testFailedFallback: 'Endpoint test failed'
    },
    systemTab: {
        selectDefaultUnit: 'Select default unit',
        observability: 'Observability',
        applicationLogging: 'Application Logging',
        useEnvironmentDefault: 'Use environment/default',
        selectLogLevel: 'Select log level',
        configuredLabel: 'Configured:',
        effectiveLabel: 'Effective:',
        sourceLabel: 'Source:',
        debugLoggingWarning: 'DEBUG logging is verbose and should only be enabled temporarily in production.',
        updateCheck: 'Update Check',
        releaseMetadata: 'Release Metadata',
        waterDataset: 'Water Dataset',
        datasetSource: 'Dataset Source',
        noSettingsTitle: 'No General System Settings',
        noSettingsDescription: 'Notification settings were moved to the Notifications tab.'
    },
    backupTab: {
        exportSection: {
            title: 'Admin Settings Export',
            subtitle: 'Portable admin-managed settings and provider configuration.',
            panelTitle: 'Export Settings',
            panelDescription: 'Download a JSON backup with portable admin-managed behavior and provider credentials.',
            button: 'Export Settings'
        },
        importSection: {
            panelTitle: 'Import Settings',
            panelDescription: 'Restore a JSON backup and replace current admin-managed settings.',
            chooseFile: 'Choose Backup File',
            button: 'Import Settings'
        },
        warnMessage: 'The settings export includes plaintext API keys, OIDC client secrets, tokens, and custom provider headers.',
        infoMessage: 'Settings export scope is admin-managed app behavior plus OIDC/custom provider configs.',
        importDialog: {
            header: 'Replace Admin Settings?',
            message: 'Importing this file will replace current global settings, OIDC providers, and custom geocoding providers.',
            note: 'Deployment infrastructure and user data exports are not affected.',
            import: 'Import'
        },
        toasts: {
            exportStarted: 'Export Started',
            exportStartedDetail: 'Admin settings backup download has started.',
            exportFailed: 'Export Failed',
            exportFailedFallback: 'Failed to export admin settings backup',
            importComplete: 'Import Complete',
            importCompleteDetail: 'Restored {settings} settings, {oidc} OIDC providers, and {custom} custom geocoding providers.',
            importFailed: 'Import Failed',
            importFailedFallback: 'Failed to import admin settings backup'
        }
    },
    fullBackupSection: {
        title: 'Full App Backup',
        subtitle: 'Password-encrypted PostgreSQL backup of application data and secrets.',
        downloadPanel: {
            title: 'Download Full Backup',
            description: 'Generate a full backup and download it in this browser.',
            button: 'Download Full Backup'
        },
        runNowPanel: {
            title: 'Run Backup Now',
            description: 'Create a backup file in the mounted local folder.',
            button: 'Run Backup Now'
        },
        warnMessage: 'Full backups are encrypted with your backup password. Save that password outside GeoPulse: without it, the backup cannot be recovered. Restore only trusted backups. Restore preparation runs online; activation briefly stops GeoPulse and restarts the backend automatically.',
        restorePreparationFailedTitle: 'Restore preparation failed',
        restorePreparationFailedFallback: 'Restore preparation failed.',
        originalDatabaseActive: 'The original database remains active and no restored data was applied.',
        backupFileLabel: 'Backup file: {fileName}',
        activationRetryableFallback: 'Activation did not complete. The original database is active and the prepared restore is retained.',
        retryActivation: 'Retry Activation',
        discardPreparedRestore: 'Discard Prepared Restore',
        progress: {
            restoreInProgress: 'Restore in progress',
            restorePreparationFailed: 'Restore preparation failed',
            backupInProgress: 'Backup in progress',
            lastBackupCompleted: 'Last backup operation completed',
            lastBackupFailed: 'Last backup operation failed',
            backupStatus: 'Backup status',
            restorePreparingMessage: 'Restoration is being prepared in the background. GeoPulse remains available until activation.',
            waitingForStatus: 'Waiting for status'
        },
        scheduledSection: {
            title: 'Scheduled Local Backups',
            description: 'Configure automatic backups written to the mounted backup folder.',
            backupPassword: 'Backup password',
            changePasswordLabel: 'Change password (leave empty to keep)',
            passwordRequiredLabel: 'Password required for all full backups',
            confirmNewPassword: 'Confirm new password',
            passwordLengthHint: 'New passwords must contain 12–1024 characters.',
            schedule: 'Schedule',
            enabled: 'Enabled',
            cron: 'Cron',
            storage: 'Storage',
            folderPath: 'Folder Path',
            backupsToKeep: 'Backups to Keep',
            timeoutMinutes: 'Timeout Minutes',
            healthAlerts: 'Health Alerts',
            warnAfterDays: 'Warn After Days',
            healthWarningHint: 'Set to 0 to disable backup freshness warnings and notifications. Active admins receive in-app alerts when enabled.',
            sendExternalAlert: 'Send External Alert',
            appriseRouting: 'Apprise Routing',
            appriseRoutingOptions: {
                destinationUrls: 'Destination URLs',
                configKeyAndTag: 'Config Key and Tag'
            },
            configKey: 'Config Key',
            destinationUrls: 'Destination URLs',
            tagOptional: 'Tag (optional)',
            saveButton: 'Save Backup Settings'
        },
        localBackups: {
            title: 'Local Backups',
            description: 'Files available in the configured server-side backup folder.',
            refreshAriaLabel: 'Refresh local backups',
            columnFile: 'File',
            columnSize: 'Size',
            columnModified: 'Modified',
            columnActions: 'Actions',
            downloadAriaLabel: 'Download backup',
            restoreAriaLabel: 'Restore backup',
            deleteAriaLabel: 'Delete backup'
        },
        restoreUploadSection: {
            title: 'Restore Uploaded Full Backup',
            description: 'Upload an encrypted .gpb backup. Preparation runs in the background while GeoPulse stays available; activation then replaces newer data and restarts the backend.',
            chooseFile: 'Choose Full Backup',
            button: 'Restore Uploaded Backup'
        },
        restoreDialog: {
            header: 'Restore Full Backup?',
            warning: 'Restoring a full backup can replace users, app settings, GPS data, friends, and permissions.',
            note: 'Preparation runs while GeoPulse remains available. Activation briefly blocks application work, replaces newer data, and restarts the backend automatically.',
            sourcePasswordLabel: 'Source backup password',
            restore: 'Restore',
            restoring: 'Restoring'
        },
        deleteDialog: {
            header: 'Delete Backup?',
            message: 'Delete {fileName} from the configured local backup folder?',
            note: 'This only removes the server-side backup file.',
            delete: 'Delete'
        },
        toasts: {
            loadFilesFailed: 'Load Failed',
            loadFilesFailedFallback: 'Failed to load local backups',
            loadConfigFailedFallback: 'Failed to load backup settings',
            downloadStarted: 'Download Started',
            downloadStartedDetail: 'Full backup download has started.',
            downloadFailed: 'Download Failed',
            downloadFailedFallback: 'Failed to download full backup',
            backupComplete: 'Backup Complete',
            backupCompleteDetail: 'Created {fileName}',
            backupFailed: 'Backup Failed',
            backupFailedFallback: 'Failed to run backup',
            saveFailed: 'Save Failed',
            passwordsDoNotMatch: 'Backup passwords do not match',
            passwordLengthError: 'New backup password must contain 12–1024 characters',
            saveConfigFailedFallback: 'Failed to save backup settings',
            settingsSaved: 'Settings Saved',
            settingsSavedDetail: 'Backup settings updated.',
            downloadLocalFailedFallback: 'Failed to download local backup',
            backupDeleted: 'Backup Deleted',
            backupDeletedDetail: 'Deleted {fileName}',
            deleteFailed: 'Delete Failed',
            deleteFailedFallback: 'Failed to delete backup',
            restoreFailed: 'Restore Failed',
            restorePasswordLengthError: 'Restore password must contain 1–1024 characters',
            restoreFailedFallback: 'Failed to restore full backup',
            activationRetryFailed: 'Activation Retry Failed',
            activationRetryFailedFallback: 'Failed to retry activation',
            discardFailed: 'Discard Failed',
            discardFailedFallback: 'Failed to discard prepared restore'
        }
    },
    composable: {
        loadFailedSummary: 'Error',
        loadFailedDetail: 'Failed to load {category} settings',
        validationErrorSummary: 'Validation Error',
        updateSuccessSummary: 'Success',
        settingUpdatedDetail: '{label} updated',
        updateErrorSummary: 'Error',
        updateFailedFallback: 'Failed to update setting',
        resetSuccessDetail: '{label} reset to default',
        resetFailedDetail: 'Failed to reset setting'
    }
}

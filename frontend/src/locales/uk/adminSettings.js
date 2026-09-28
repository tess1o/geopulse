/**
 * The admin settings catalog (metadata table plus settings tabs). (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 */
export default {
    metadata: {
        system: {
            logging: {
                'application-level': {
                    label: 'Рівень журналювання застосунку',
                    description: 'ERROR, WARN, INFO або DEBUG. Скиньте, щоб використовувати GEOPULSE_LOG_LEVEL або типове значення застосунку; TRACE недоступний у продакшені.'
                }
            },
            user: {
                'default-distance-unit': {
                    label: 'Типова одиниця відстані',
                    description: 'Одиниця відстані, призначена новоствореним користувачам'
                },
                'default-temperature-unit': {
                    label: 'Типова одиниця температури',
                    description: 'Одиниця температури, призначена новоствореним користувачам'
                }
            },
            timeline: {
                view: {
                    'item-limit': {
                        label: 'Ліміт елементів перегляду хронології',
                        description: 'Максимальна кількість елементів хронології, що повертаються в одному запиті перегляду'
                    }
                },
                processing: {
                    'thread-pool-size': {
                        label: 'Потоки обробки хронології',
                        description: 'Кількість робочих потоків, що використовуються для обробки хронології'
                    }
                }
            },
            'version-check': {
                'github-api-url': {
                    label: 'URL-адреса API релізів',
                    description: 'URL-адреса API, сумісного з GitHub, для перевірки оновлень GeoPulse'
                },
                'release-url': {
                    label: 'URL-адреса сторінки релізу',
                    description: 'Резервна URL-адреса сторінки релізу, що показується з інформацією про оновлення'
                },
                'cache-ttl-minutes': {
                    label: 'TTL кешу оновлень',
                    description: 'Кількість хвилин кешування перевірок оновлень версії'
                },
                'connect-timeout-seconds': {
                    label: 'Тайм-аут з\'єднання з оновленнями',
                    description: 'Секунди очікування під час з\'єднання з API оновлень'
                },
                'read-timeout-seconds': {
                    label: 'Тайм-аут читання оновлень',
                    description: 'Секунди очікування відповідей API оновлень'
                }
            },
            'water-dataset': {
                url: {
                    label: 'URL-адреса набору даних води',
                    description: 'URL-адреса архіву набору даних води, що використовується для налаштування Човна'
                },
                sha256: {
                    label: 'Контрольна сума набору даних води',
                    description: 'Необов\'язкова очікувана контрольна сума SHA-256 для архіву набору даних води'
                },
                'auto-import': {
                    label: 'Автоімпорт даних води',
                    description: 'Автоматично імпортувати набір даних води, коли цього вимагає налаштування Човна'
                },
                'connect-timeout-seconds': {
                    label: 'Тайм-аут з\'єднання з даними води',
                    description: 'Секунди очікування під час з\'єднання із завантаженням набору даних води'
                },
                'download-timeout-hours': {
                    label: 'Тайм-аут завантаження даних води',
                    description: 'Максимальна тривалість завантаження набору даних води'
                },
                'download-stall-timeout-seconds': {
                    label: 'Тайм-аут зупинки завантаження води',
                    description: 'Секунди без завантажених байтів до збою завантаження даних води'
                },
                'setup-start-timeout-minutes': {
                    label: 'Тайм-аут запуску налаштування Човна',
                    description: 'Кількість хвилин до того, як завдання налаштування Човна в черзі вважається невдалим'
                }
            },
            notifications: {
                apprise: {
                    enabled: {
                        label: 'Увімкнути сповіщення Apprise',
                        description: 'Увімкнути доставку сповіщень геозони до призначень Apprise'
                    },
                    'api-url': {
                        label: 'URL-адреса API Apprise',
                        description: 'Базова URL-адреса служби API Apprise (наприклад, http://apprise-api:8000)'
                    },
                    'auth-token': {
                        label: 'Токен API Apprise',
                        description: 'Необов\'язковий токен/ключ API для автентифікації з Apprise (зашифровано)'
                    },
                    'timeout-ms': {
                        label: 'Тайм-аут Apprise (мс)',
                        description: 'Тайм-аут HTTP-запиту під час надсилання сповіщень до Apprise'
                    },
                    'verify-tls': {
                        label: 'Перевіряти сертифікати TLS',
                        description: 'Чи увімкнено перевірку сертифіката HTTPS для запитів Apprise'
                    }
                },
                'geofence-events': {
                    cleanup: {
                        enabled: {
                            label: 'Увімкнути очищення подій геозони',
                            description: 'Увімкнути автоматичне очищення старих подій сповіщень геозони'
                        }
                    },
                    'retention-days': {
                        label: 'Зберігання (днів)',
                        description: 'Видаляти події сповіщень геозони, старші за цю кількість днів'
                    }
                }
            }
        },
        auth: {
            registration: {
                enabled: {
                    label: 'Реєстрацію увімкнено',
                    description: 'Дозволити новим користувачам реєструватися'
                }
            },
            'password-registration': {
                enabled: {
                    label: 'Реєстрація за паролем',
                    description: 'Дозволити реєстрацію за електронною поштою/паролем'
                }
            },
            oidc: {
                registration: {
                    enabled: {
                        label: 'Реєстрація через OIDC',
                        description: 'Дозволити реєстрацію через провайдерів OIDC'
                    }
                },
                'auto-link-accounts': {
                    label: 'Автоприв\'язка облікових записів OIDC',
                    description: 'Автоматично прив\'язувати облікові записи OIDC за електронною поштою (ризик безпеки)'
                },
                'callback-base-url': {
                    label: 'Базова URL-адреса зворотного виклику OIDC',
                    description: 'Необов\'язкова публічна базова URL-адреса для побудови переспрямувань зворотного виклику OIDC'
                },
                'jwks-cache': {
                    'ttl-hours': {
                        label: 'TTL кешу JWKS',
                        description: 'Кількість годин кешування ключів підпису провайдера OIDC'
                    }
                },
                cleanup: {
                    'session-states': {
                        enabled: {
                            label: 'Очищення сесій OIDC',
                            description: 'Очищати прострочені записи стану сесії входу OIDC'
                        }
                    }
                },
                login: {
                    enabled: {
                        label: 'Вхід через OIDC',
                        description: 'Дозволити вхід через провайдерів OIDC'
                    }
                }
            },
            login: {
                enabled: {
                    label: 'Вхід увімкнено',
                    description: 'Дозволити користувачам входити (головний перемикач)'
                }
            },
            'password-login': {
                enabled: {
                    label: 'Вхід за паролем',
                    description: 'Дозволити вхід за електронною поштою/паролем'
                }
            },
            'admin-login-bypass': {
                enabled: {
                    label: 'Обхід входу для адміністраторів',
                    description: 'Дозволити адміністраторам обходити обмеження входу (запобігає блокуванню)'
                }
            },
            'guest-root-redirect-to-login': {
                enabled: {
                    label: 'Переспрямування гостей з кореня',
                    description: 'Переспрямовувати неавторизованих користувачів з "/" на "/login" замість показу Головної'
                }
            }
        },
        ai: {
            logging: {
                enabled: {
                    label: 'Увімкнути журналювання запитів/відповідей AI',
                    description: 'Журналювати детальні запити та відповіді AI для налагодження (зміни застосовуються негайно)'
                }
            },
            'chat-memory': {
                'max-messages': {
                    label: 'Розмір пам\'яті чату',
                    description: 'Максимальна кількість повідомлень, що зберігаються в історії розмови на користувача'
                }
            },
            'tool-result': {
                'max-length': {
                    label: 'Максимальна довжина результату інструменту',
                    description: 'Максимальна кількість символів у результатах інструментів, щоб уникнути помилок ліміту токенів (12000 ≈ 3000 токенів)'
                }
            }
        },
        geocoding: {
            'primary-provider': {
                label: 'Основний провайдер',
                description: 'Основний сервіс геокодування лише для нових запитів; наявні кешовані записи залишаються незмінними до узгодження'
            },
            'fallback-provider': {
                label: 'Резервний провайдер',
                description: 'Резервний провайдер лише для нових запитів; не перезаписує наявні кешовані записи'
            },
            'delay-ms': {
                label: 'Затримка запиту',
                description: 'Затримка між запитами геокодування (мілісекунди)'
            },
            nominatim: {
                enabled: {
                    label: 'Nominatim',
                    description: 'Увімкнути провайдер геокодування Nominatim'
                },
                'public-host-forward-search-enabled': {
                    label: 'Прямий пошук Nominatim',
                    description: 'Дозволити пошук/автозаповнення Nominatim на публічному nominatim.openstreetmap.org (власний Nominatim завжди дозволено)'
                },
                url: {
                    label: 'URL-адреса Nominatim',
                    description: 'Власна URL-адреса сервера Nominatim (необов\'язково)'
                },
                language: {
                    label: 'Мова Nominatim',
                    description: 'Перевага мови (BCP 47: en-US, de, uk, ja тощо)'
                }
            },
            photon: {
                enabled: {
                    label: 'Photon',
                    description: 'Увімкнути провайдер геокодування Photon'
                },
                url: {
                    label: 'URL-адреса Photon',
                    description: 'Власна URL-адреса сервера Photon (необов\'язково)'
                },
                language: {
                    label: 'Мова Photon',
                    description: 'Код мови Photon (дозволено: de, pl, el, en, es, fa, fr, it, ja, ko). Залиште порожнім для типового значення провайдера'
                }
            },
            googlemaps: {
                enabled: {
                    label: 'Google Maps',
                    description: 'Увімкнути провайдер геокодування Google Maps'
                },
                'api-key': {
                    label: 'Ключ API Google Maps',
                    description: 'Ключ API для Google Maps (зашифровано, введіть для оновлення)'
                },
                language: {
                    label: 'Мова Google Maps',
                    description: 'Перелік підтримуваних мов: https://developers.google.com/maps/faq#languagesupport'
                }
            },
            mapbox: {
                enabled: {
                    label: 'Mapbox',
                    description: 'Увімкнути провайдер геокодування Mapbox'
                },
                'access-token': {
                    label: 'Токен доступу Mapbox',
                    description: 'Токен доступу для Mapbox (зашифровано, введіть для оновлення)'
                }
            },
            geoapify: {
                enabled: {
                    label: 'Geoapify',
                    description: 'Увімкнути провайдер геокодування Geoapify'
                },
                'api-key': {
                    label: 'Ключ API Geoapify',
                    description: 'Ключ API для Geoapify (зашифровано, введіть для оновлення)'
                },
                language: {
                    label: 'Мова Geoapify',
                    description: 'Перевага мови для відповідей Geoapify (необов\'язково)'
                },
                'delay-ms': {
                    label: 'Затримка Geoapify',
                    description: 'Затримка між запитами Geoapify (мілісекунди)'
                }
            },
            chibigeo: {
                enabled: {
                    label: 'ChibiGeo',
                    description: 'Увімкнути провайдер геокодування ChibiGeo'
                },
                url: {
                    label: 'URL-адреса ChibiGeo',
                    description: 'URL-адреса ChibiGeo, сумісна з Photon'
                },
                'api-key': {
                    label: 'Ключ API ChibiGeo',
                    description: 'Ключ API для ChibiGeo (зашифровано, введіть для оновлення)'
                },
                language: {
                    label: 'Мова ChibiGeo',
                    description: 'Код мови, сумісний з Photon (дозволено: de, pl, el, en, es, fa, fr, it, ja, ko). Залиште порожнім для типового значення провайдера'
                },
                'delay-ms': {
                    label: 'Затримка ChibiGeo',
                    description: 'Затримка між запитами ChibiGeo (мілісекунди)'
                }
            },
            cache: {
                'max-bbox-area-km2': {
                    label: 'Ліміт області кешу',
                    description: 'Максимальна площа обмежувальної рамки провайдера, прийнятна для зіставлення кешу геокодування'
                }
            },
            reconcile: {
                item: {
                    'max-attempts': {
                        label: 'Спроби узгодження',
                        description: 'Максимальна кількість спроб узгодження одного кешованого запису геокодування'
                    }
                },
                'circuit-open-wait-ms': {
                    label: 'Очікування розімкненого запобіжника',
                    description: 'Мілісекунди очікування, коли запобіжник провайдера розімкнений під час узгодження'
                },
                'inter-item-delay-ms': {
                    label: 'Темп узгодження',
                    description: 'Затримка між узгодженими записами геокодування в мілісекундах'
                }
            }
        },
        weather: {
            enabled: {
                label: 'Увімкнути погоду',
                description: 'Увімкнути зразки погоди для зупинок і поїздок хронології'
            },
            'primary-provider': {
                label: 'Основний провайдер',
                description: 'Провайдер погоди, що використовується першим для нових зразків погоди'
            },
            'secondary-provider': {
                label: 'Резервний провайдер',
                description: 'Необов\'язковий резервний провайдер, коли основний не може повернути зразок'
            },
            'open-meteo': {
                enabled: {
                    label: 'Увімкнути Open-Meteo',
                    description: 'Увімкнути Open-Meteo як доступний для вибору провайдер погоди'
                },
                'forecast-url': {
                    label: 'URL-адреса прогнозу',
                    description: 'Базова URL-адреса API прогнозу Open-Meteo'
                },
                'archive-url': {
                    label: 'URL-адреса архіву',
                    description: 'Базова URL-адреса API історичного архіву Open-Meteo'
                },
                'api-key': {
                    label: 'Ключ API Open-Meteo',
                    description: 'Необов\'язковий ключ API Open-Meteo (зашифровано)'
                },
                'connect-timeout-seconds': {
                    label: 'Тайм-аут з\'єднання Open-Meteo',
                    description: 'Секунди очікування під час встановлення з\'єднання з Open-Meteo'
                },
                'read-timeout-seconds': {
                    label: 'Тайм-аут читання Open-Meteo',
                    description: 'Секунди очікування відповідей Open-Meteo'
                }
            },
            pirate: {
                enabled: {
                    label: 'Увімкнути Pirate Weather',
                    description: 'Увімкнути Pirate Weather як доступний для вибору провайдер погоди'
                },
                'base-url': {
                    label: 'URL-адреса прогнозу Pirate Weather',
                    description: 'Базова URL-адреса API прогнозу Pirate Weather'
                },
                'time-machine-url': {
                    label: 'URL-адреса Time Machine Pirate Weather',
                    description: 'Базова URL-адреса API історичної машини часу Pirate Weather'
                },
                'api-key': {
                    label: 'Ключ API Pirate Weather',
                    description: 'Ключ API Pirate Weather (зашифровано)'
                },
                'connect-timeout-seconds': {
                    label: 'Тайм-аут з\'єднання Pirate',
                    description: 'Секунди очікування під час встановлення з\'єднання з Pirate Weather'
                },
                'read-timeout-seconds': {
                    label: 'Тайм-аут читання Pirate',
                    description: 'Секунди очікування відповідей Pirate Weather'
                }
            },
            ongoing: {
                enabled: {
                    label: 'Поточна погода',
                    description: 'Створювати цілі погоди для активних останніх зупинок і поїздок'
                },
                'interval-minutes': {
                    label: 'Інтервал поточної погоди',
                    description: 'Мінімальна кількість хвилин між зразками поточної погоди (мінімум 30)'
                }
            },
            backfill: {
                enabled: {
                    label: 'Історичне заповнення погоди',
                    description: 'Автоматично виявляти цілі історичної погоди'
                }
            },
            quota: {
                'daily-request-limit': {
                    label: 'Денний ліміт запитів',
                    description: 'Максимальна кількість запитів до провайдера за добу UTC'
                },
                'ongoing-reserve': {
                    label: 'Резерв поточної погоди',
                    description: 'Запити, зарезервовані щодня для зразків поточної погоди'
                }
            },
            'coordinate-precision': {
                label: 'Точність координат',
                description: 'Десяткова точність для сегментів місцезнаходження погоди'
            },
            'failed-target-retry': {
                enabled: {
                    label: 'Повторювати невдалі цілі',
                    description: 'Повторювати застарілі невдалі цілі погоди після періоду очікування'
                },
                'cooldown-hours': {
                    label: 'Період очікування повтору',
                    description: 'Кількість годин до можливості повторної спроби для невдалої цілі погоди'
                }
            },
            targets: {
                'completed-retention-days': {
                    label: 'Зберігання завершених цілей',
                    description: 'Кількість днів зберігання записів завершених цілей погоди'
                },
                'failed-retention-days': {
                    label: 'Зберігання невдалих цілей',
                    description: 'Кількість днів зберігання записів невдалих цілей погоди'
                },
                'in-progress-timeout-minutes': {
                    label: 'Тайм-аут цілі в процесі',
                    description: 'Кількість хвилин до того, як цілі погоди в процесі вважаються застарілими'
                }
            }
        },
        'map-matching': {
            enabled: {
                label: 'Увімкнути прив\'язку до карти',
                description: 'Загальна передумова для прив\'язки до карти за вибором користувача'
            },
            automatic: {
                enabled: {
                    label: 'Автоматичне майбутнє зіставлення',
                    description: 'Прив\'язувати до карти стабільні нові поїздки для всіх користувачів після періоду затишшя'
                },
                'quiet-period-minutes': {
                    label: 'Період затишшя',
                    description: 'Хвилини після зміни хронології до початку автоматичного зіставлення'
                }
            },
            backfill: {
                enabled: {
                    label: 'Історичне заповнення',
                    description: 'Виявляти історичні поїздки для всіх користувачів у фоновому режимі; прогрес можна відновити'
                }
            },
            provider: {
                label: 'Провайдер',
                description: 'Рушій прив\'язки до карти, що використовується для уточнення маршруту поїздки'
            },
            valhalla: {
                'base-url': {
                    label: 'Базова URL-адреса Valhalla',
                    description: 'Базова URL-адреса для власного сервісу Valhalla'
                },
                'connect-timeout-seconds': {
                    label: 'Тайм-аут з\'єднання',
                    description: 'Секунди очікування під час встановлення з\'єднання з Valhalla'
                },
                'read-timeout-seconds': {
                    label: 'Тайм-аут читання',
                    description: 'Секунди очікування відповідей прив\'язки до карти Valhalla'
                }
            },
            'max-input-points': {
                label: 'Максимум вхідних точок',
                description: 'Максимальна кількість GPS-точок, що надсилаються до Valhalla за поїздку'
            },
            'max-trip-duration-hours': {
                label: 'Максимальна тривалість поїздки',
                description: 'Поїздки, довші за цю кількість годин, пропускаються'
            },
            worker: {
                'batch-size': {
                    label: 'Розмір пакета обробника',
                    description: 'Кількість цілей прив\'язки до карти, оброблюваних за один запуск обробника'
                }
            },
            'max-attempts': {
                label: 'Максимум спроб',
                description: 'Максимальна кількість повторних спроб для кожної цілі прив\'язки до карти'
            },
            quality: {
                'min-raw-distance-meters': {
                    label: 'Відстань перевірки якості',
                    description: 'Мінімальна відстань необробленого фрагмента перед застосуванням перевірок якості часткового зіставлення'
                },
                'min-distance-coverage-percent': {
                    label: 'Мінімальне покриття',
                    description: 'Мінімальна зіставлена відстань у відсотках від відстані необробленого фрагмента'
                },
                'max-discontinuity-percent': {
                    label: 'Максимальний відсоток розриву',
                    description: 'Максимальна незіставлена відстань розриву між зіставленими фрагментами у відсотках від відстані необробленого фрагмента'
                },
                'max-short-discontinuity-meters': {
                    label: 'Допуск короткого розриву',
                    description: 'Мінімальний абсолютний допуск розриву між зіставленими фрагментами'
                }
            }
        },
        panoramax: {
            enabled: {
                label: 'Увімкнути покриття Panoramax',
                description: 'Показувати покриття наземних знімків Panoramax на картах хронології'
            },
            endpoint: {
                label: 'Кінцева точка STAC Panoramax',
                description: 'Публічна кінцева точка API STAC Panoramax, що використовується для покриття та знімків'
            }
        },
        poi: {
            enabled: {
                label: 'Увімкнути пошук місць',
                description: 'Пропонувати місця, варті відвідування, з фотографіями, під час планування поїздки'
            },
            'user-agent': {
                label: 'User-Agent',
                description: 'Надсилається до Wikidata та Commons. Тримайте його ідентифікованим: анонімний клієнт — це той, кого обмежують чи блокують'
            },
            language: {
                label: 'Бажана мова',
                description: 'Мова для назв і описів місць (напр., en, de, uk)'
            },
            attribution: {
                enabled: {
                    label: 'Показувати атрибуцію',
                    description: 'Показувати авторство фото та атрибуцію даних. Вимагається ліцензіями Wikimedia'
                }
            },
            wikidata: {
                endpoint: {
                    label: 'Кінцева точка Wikidata',
                    description: 'Базова URL-адреса служби запитів Wikidata. Вкажіть власний екземпляр, щоб уникнути публічних обмежень швидкості'
                }
            },
            commons: {
                endpoint: {
                    label: 'Кінцева точка Commons',
                    description: 'Базова URL-адреса API Wikimedia Commons, що використовується для визначення авторства фото'
                },
                'thumb-width': {
                    label: 'Ширина мініатюри фото',
                    description: 'Запитувана ширина мініатюри в пікселях. Commons виконує масштабування, тому більше значення означає повільніше'
                }
            },
            'max-results': {
                label: 'Максимум результатів на область',
                description: 'Верхня межа місць, отриманих з Wikidata для однієї області'
            },
            cache: {
                'ttl-days': {
                    label: 'TTL кешу місць (днів)',
                    description: 'Як довго повторно використовуються кешовані дані місць перед повторним запитом. Довше — краще для спільного API'
                },
                'image-ttl-days': {
                    label: 'TTL кешу фото (днів)',
                    description: 'Як довго повторно використовуються кешовані байти фото перед повторним запитом'
                }
            }
        },
        import: {
            'bulk-insert-batch-size': {
                label: 'Розмір пакета масової вставки',
                description: 'Кількість GPS-точок для вставки в один пакет бази даних'
            },
            'merge-batch-size': {
                label: 'Розмір пакета об\'єднання',
                description: 'Розмір пакета під час об\'єднання даних з виявленням дублікатів'
            },
            'large-file-threshold-mb': {
                label: 'Поріг великого файлу (МБ)',
                description: 'Файли, більші за це значення, зберігаються як тимчасові файли замість пам\'яті'
            },
            'temp-file-retention-hours': {
                label: 'Зберігання тимчасових файлів (годин)',
                description: 'Як довго зберігати тимчасові файли імпорту перед очищенням'
            },
            'drop-folder': {
                enabled: {
                    label: 'Папку скидання увімкнено',
                    description: 'Увімкнути імпорт з папки скидання'
                },
                path: {
                    label: 'Шлях папки скидання',
                    description: 'Шлях у файловій системі для імпорту з папки скидання'
                },
                'poll-interval-seconds': {
                    label: 'Інтервал сканування скидання (секунд)',
                    description: 'Як часто сканувати папку скидання'
                },
                'stable-age-seconds': {
                    label: 'Стабільний вік файлу скидання (секунд)',
                    description: 'Мінімальний вік файлу перед початком імпорту'
                },
                'geopulse-max-size-mb': {
                    label: 'Максимальний розмір GeoPulse для скидання (МБ)',
                    description: 'Максимальний розмір ZIP GeoPulse для імпорту з папки скидання'
                },
                'runtime-identity': {
                    label: 'Ідентичність процесу папки скидання',
                    description: 'Фактичний користувач/група, що виконує бекенд-процес (лише читання)'
                }
            },
            'chunk-size-mb': {
                label: 'Розмір фрагмента (МБ)',
                description: 'Розмір кожного фрагмента завантаження для великих файлів'
            },
            'max-file-size-gb': {
                label: 'Максимальний розмір файлу (ГБ)',
                description: 'Максимальний дозволений розмір файлу для імпорту'
            },
            'upload-timeout-hours': {
                label: 'Тайм-аут завантаження (годин)',
                description: 'Як довго сесія завантаження залишається дійсною'
            },
            'transaction-timeout-minutes': {
                label: 'Тайм-аут транзакції імпорту',
                description: 'Максимальна тривалість транзакції для одного завдання імпорту'
            },
            'upload-cleanup-minutes': {
                label: 'Інтервал очищення завантажень',
                description: 'Як часто очищаються прострочені сесії фрагментованого завантаження'
            },
            geonames: {
                cities: {
                    enabled: {
                        label: 'Імпорт міст увімкнено',
                        description: 'Увімкнути імпорт набору даних міст GeoNames'
                    },
                    url: {
                        label: 'URL-адреса набору даних міст',
                        description: 'URL-адреса ZIP-архіву набору даних міст GeoNames'
                    },
                    'batch-size': {
                        label: 'Розмір пакета міст',
                        description: 'Кількість рядків, оброблюваних за один пакет імпорту міст GeoNames'
                    },
                    'min-row-threshold': {
                        label: 'Поріг рядків міст',
                        description: 'Мінімальна кількість підготовлених рядків міст GeoNames перед заміною даних'
                    },
                    'force-refresh': {
                        label: 'Примусове оновлення міст',
                        description: 'Повторно імпортувати дані міст, навіть якщо наявні дані відповідають порогу'
                    },
                    'connect-timeout-seconds': {
                        label: 'Тайм-аут з\'єднання міст',
                        description: 'Секунди очікування під час з\'єднання із завантаженням міст GeoNames'
                    },
                    'read-timeout-seconds': {
                        label: 'Тайм-аут читання міст',
                        description: 'Секунди очікування читання завантаження міст GeoNames'
                    }
                },
                countries: {
                    enabled: {
                        label: 'Імпорт країн увімкнено',
                        description: 'Увімкнути імпорт набору даних країн GeoNames'
                    },
                    url: {
                        label: 'URL-адреса набору даних країн',
                        description: 'URL-адреса набору даних країн GeoNames'
                    },
                    'batch-size': {
                        label: 'Розмір пакета країн',
                        description: 'Кількість рядків, оброблюваних за один пакет імпорту країн GeoNames'
                    },
                    'min-row-threshold': {
                        label: 'Поріг рядків країн',
                        description: 'Мінімальна кількість підготовлених рядків країн GeoNames перед заміною даних'
                    },
                    'force-refresh': {
                        label: 'Примусове оновлення країн',
                        description: 'Повторно імпортувати дані країн, навіть якщо наявні дані відповідають порогу'
                    },
                    'connect-timeout-seconds': {
                        label: 'Тайм-аут з\'єднання країн',
                        description: 'Секунди очікування під час з\'єднання із завантаженням країн GeoNames'
                    },
                    'read-timeout-seconds': {
                        label: 'Тайм-аут читання країн',
                        description: 'Секунди очікування читання завантаження країн GeoNames'
                    }
                }
            },
            'geojson-streaming-batch-size': {
                label: 'Розмір пакета GeoJSON',
                description: 'Розмір пакета для потокового парсера GeoJSON'
            },
            'googletimeline-streaming-batch-size': {
                label: 'Розмір пакета Google Timeline',
                description: 'Розмір пакета для потокового парсера Google Timeline'
            },
            'gpx-streaming-batch-size': {
                label: 'Розмір пакета GPX',
                description: 'Розмір пакета для потокового парсера GPX'
            },
            'csv-streaming-batch-size': {
                label: 'Розмір пакета CSV',
                description: 'Розмір пакета для потокового парсера CSV'
            },
            'owntracks-streaming-batch-size': {
                label: 'Розмір пакета OwnTracks',
                description: 'Розмір пакета для потокового парсера OwnTracks'
            }
        },
        export: {
            'max-jobs-per-user': {
                label: 'Максимум завдань на користувача',
                description: 'Максимальна кількість завдань експорту, які користувач може мати одночасно'
            },
            'job-expiry-hours': {
                label: 'Термін дії завдання (годин)',
                description: 'Кількість годин до автоматичного видалення завершених завдань експорту'
            },
            'concurrent-jobs-limit': {
                label: 'Ліміт одночасних завдань',
                description: 'Максимальна кількість завдань експорту, що обробляються одночасно'
            },
            'batch-size': {
                label: 'Розмір пакета',
                description: 'Кількість записів для обробки в кожному пакеті під час експорту'
            },
            'trip-point-limit': {
                label: 'Ліміт точок поїздки',
                description: 'Максимальна кількість GPS-точок для однієї поїздки в експорті'
            },
            'temp-file-retention-hours': {
                label: 'Зберігання тимчасових файлів (годин)',
                description: 'Як довго зберігати тимчасові файли експорту перед очищенням'
            }
        },
        backup: {
            scheduled: {
                enabled: {
                    label: 'Заплановані резервні копії',
                    description: 'Увімкнути автоматичні повні резервні копії, що записуються в локальну папку резервних копій'
                },
                cron: {
                    label: 'Cron-розклад резервного копіювання',
                    description: 'Вираз cron для планування автоматичних повних резервних копій'
                }
            },
            local: {
                path: {
                    label: 'Шлях папки резервних копій',
                    description: 'Папка контейнера бекенда, куди записуються файли ZIP повних локальних резервних копій'
                }
            },
            retention: {
                count: {
                    label: 'Кількість збережених резервних копій',
                    description: 'Кількість локальних файлів повного резервного копіювання, що зберігаються'
                }
            },
            operation: {
                'timeout-minutes': {
                    label: 'Тайм-аут резервного копіювання (хвилин)',
                    description: 'Максимальна тривалість операцій повного резервного копіювання та відновлення'
                }
            }
        }
    },
    unitOptions: {
        distanceKilometers: 'Кілометри (км, м)',
        distanceMiles: 'Милі (mi, ft)',
        temperatureCelsius: 'Цельсій (°C)',
        temperatureFahrenheit: 'Фаренгейт (°F)'
    },
    shell: {
        readOnly: 'Лише для читання',
        default: 'За замовчуванням',
        reset: 'Скинути',
        plannedFeatures: 'Заплановані функції:',
        comingSoon: 'Скоро'
    },
    gpsProcessingTab: {
        title: 'Налаштування обробки GPS',
        description: 'Налаштування типової поведінки обробки даних GPS',
        features: {
            stayDetection: {
                name: 'Алгоритм визначення зупинок',
                description: 'Типовий алгоритм визначення зупинок за точками GPS'
            },
            accuracyFiltering: {
                name: 'Фільтрація точності',
                description: 'Мінімальний поріг точності GPS для обробки'
            },
            batchSize: {
                name: 'Розмір пакету',
                description: 'Кількість точок GPS, що обробляються в одному пакеті'
            },
            distanceThresholds: {
                name: 'Порогові значення відстані',
                description: 'Налаштування параметрів відстані для зупинок/поїздок'
            },
            timeWindows: {
                name: 'Часові вікна',
                description: 'Мінімальний/максимальний час для визначення зупинки'
            }
        }
    },
    panoramaxTab: {
        testEndpoint: 'Перевірити кінцеву точку',
        testSuccess: 'Знайдено векторні плитки Panoramax STAC',
        testFailedFallback: 'Перевірка кінцевої точки не вдалася'
    },
    authenticationTab: {
        title: 'Налаштування автентифікації',
        oidcAdvanced: 'Додаткові налаштування OIDC'
    },
    exportTab: {
        jobManagement: 'Керування завданнями',
        batchProcessing: 'Пакетна обробка',
        tempFileStorage: 'Тимчасове зберігання файлів'
    },
    poiTab: {
        testEndpoints: 'Перевірити кінцеві точки',
        advancedSettings: 'Додаткові налаштування',
        endpointsLimitsCaching: 'Кінцеві точки, ліміти та кешування',
        advancedHint: 'Кінцеві точки можна спрямувати на власні (self-hosted) інстанси. Довший час життя кешу означає менше запитів до спільної публічної інфраструктури.',
        wikidataOk: 'Wikidata OK',
        wikidataFailed: 'Помилка Wikidata: {detail}',
        commonsOk: 'Commons OK',
        commonsFailed: 'Помилка Commons: {detail}',
        testFailedFallback: 'Перевірка кінцевої точки не вдалася'
    },
    systemTab: {
        selectDefaultUnit: 'Оберіть типову одиницю',
        observability: 'Спостережуваність',
        applicationLogging: 'Логування застосунку',
        useEnvironmentDefault: 'Використовувати середовище/типове значення',
        selectLogLevel: 'Оберіть рівень логування',
        configuredLabel: 'Налаштовано:',
        effectiveLabel: 'Діюче значення:',
        sourceLabel: 'Джерело:',
        debugLoggingWarning: 'Логування DEBUG є докладним і має вмикатися в продакшні лише тимчасово.',
        updateCheck: 'Перевірка оновлень',
        releaseMetadata: 'Метадані релізу',
        waterDataset: 'Набір даних про водойми',
        datasetSource: 'Джерело набору даних',
        noSettingsTitle: 'Немає загальних системних налаштувань',
        noSettingsDescription: 'Налаштування сповіщень переміщено на вкладку "Сповіщення".'
    },
    backupTab: {
        exportSection: {
            title: 'Експорт налаштувань адміністратора',
            subtitle: 'Портативні керовані адміністратором налаштування та конфігурація провайдерів.',
            panelTitle: 'Експортувати налаштування',
            panelDescription: 'Завантажити JSON-резервну копію портативної адміністративної поведінки та облікових даних провайдерів.',
            button: 'Експортувати налаштування'
        },
        importSection: {
            panelTitle: 'Імпортувати налаштування',
            panelDescription: 'Відновити JSON-резервну копію та замінити поточні адміністративні налаштування.',
            chooseFile: 'Вибрати файл резервної копії',
            button: 'Імпортувати налаштування'
        },
        warnMessage: 'Експорт налаштувань містить незашифровані API-ключі, секрети клієнтів OIDC, токени та заголовки власних провайдерів.',
        infoMessage: 'Область експорту налаштувань — це поведінка застосунку, керована адміністратором, плюс конфігурації OIDC/власних провайдерів.',
        importDialog: {
            header: 'Замінити налаштування адміністратора?',
            message: 'Імпорт цього файлу замінить поточні глобальні налаштування, OIDC-провайдери та власні провайдери геокодування.',
            note: 'Інфраструктура розгортання та експорти даних користувачів не постраждають.',
            import: 'Імпортувати'
        },
        toasts: {
            exportStarted: 'Експорт розпочато',
            exportStartedDetail: 'Завантаження резервної копії налаштувань адміністратора розпочато.',
            exportFailed: 'Помилка експорту',
            exportFailedFallback: 'Не вдалося експортувати налаштування адміністратора',
            importComplete: 'Імпорт завершено',
            importCompleteDetail: 'Відновлено {settings} налаштувань, {oidc} OIDC-провайдерів та {custom} власних провайдерів геокодування.',
            importFailed: 'Помилка імпорту',
            importFailedFallback: 'Не вдалося імпортувати налаштування адміністратора'
        }
    },
    fullBackupSection: {
        title: 'Повна резервна копія застосунку',
        subtitle: 'Зашифрована паролем резервна копія PostgreSQL з даними застосунку та секретами.',
        downloadPanel: {
            title: 'Завантажити повну резервну копію',
            description: 'Створити повну резервну копію та завантажити її в цьому браузері.',
            button: 'Завантажити повну резервну копію'
        },
        runNowPanel: {
            title: 'Запустити резервне копіювання зараз',
            description: 'Створити файл резервної копії в змонтованій локальній папці.',
            button: 'Запустити резервне копіювання зараз'
        },
        warnMessage: 'Повні резервні копії шифруються вашим паролем резервного копіювання. Зберігайте цей пароль поза GeoPulse: без нього резервну копію неможливо відновити. Відновлюйте лише довірені резервні копії. Підготовка відновлення виконується онлайн; активація ненадовго зупиняє GeoPulse та автоматично перезапускає бекенд.',
        restorePreparationFailedTitle: 'Підготовка відновлення не вдалася',
        restorePreparationFailedFallback: 'Підготовка відновлення не вдалася.',
        originalDatabaseActive: 'Оригінальна база даних залишається активною, відновлені дані не застосовано.',
        backupFileLabel: 'Файл резервної копії: {fileName}',
        activationRetryableFallback: 'Активація не завершилася. Оригінальна база даних активна, підготовлене відновлення збережено.',
        retryActivation: 'Повторити активацію',
        discardPreparedRestore: 'Скасувати підготовлене відновлення',
        progress: {
            restoreInProgress: 'Відновлення триває',
            restorePreparationFailed: 'Підготовка відновлення не вдалася',
            backupInProgress: 'Резервне копіювання триває',
            lastBackupCompleted: 'Остання операція резервного копіювання завершена',
            lastBackupFailed: 'Остання операція резервного копіювання не вдалася',
            backupStatus: 'Статус резервного копіювання',
            restorePreparingMessage: 'Відновлення готується у фоновому режимі. GeoPulse залишається доступним до активації.',
            waitingForStatus: 'Очікування статусу'
        },
        scheduledSection: {
            title: 'Заплановані локальні резервні копії',
            description: 'Налаштування автоматичних резервних копій, що записуються в змонтовану папку резервних копій.',
            backupPassword: 'Пароль резервного копіювання',
            changePasswordLabel: 'Змінити пароль (залиште порожнім, щоб зберегти поточний)',
            passwordRequiredLabel: 'Пароль обов\'язковий для всіх повних резервних копій',
            confirmNewPassword: 'Підтвердіть новий пароль',
            passwordLengthHint: 'Нові паролі мають містити від 12 до 1024 символів.',
            schedule: 'Розклад',
            enabled: 'Увімкнено',
            cron: 'Cron',
            storage: 'Сховище',
            folderPath: 'Шлях до папки',
            backupsToKeep: 'Кількість резервних копій для зберігання',
            timeoutMinutes: 'Тайм-аут у хвилинах',
            healthAlerts: 'Сповіщення про стан',
            warnAfterDays: 'Попереджати через (днів)',
            healthWarningHint: 'Встановіть 0, щоб вимкнути попередження про застарілість резервних копій. Активні адміністратори отримують сповіщення в застосунку, коли увімкнено.',
            sendExternalAlert: 'Надсилати зовнішнє сповіщення',
            appriseRouting: 'Маршрутизація Apprise',
            appriseRoutingOptions: {
                destinationUrls: 'URL-адреси призначення',
                configKeyAndTag: 'Ключ конфігурації та тег'
            },
            configKey: 'Ключ конфігурації',
            destinationUrls: 'URL-адреси призначення',
            tagOptional: 'Тег (необов\'язково)',
            saveButton: 'Зберегти налаштування резервного копіювання'
        },
        localBackups: {
            title: 'Локальні резервні копії',
            description: 'Файли, доступні в налаштованій серверній папці резервних копій.',
            refreshAriaLabel: 'Оновити локальні резервні копії',
            columnFile: 'Файл',
            columnSize: 'Розмір',
            columnModified: 'Змінено',
            columnActions: 'Дії',
            downloadAriaLabel: 'Завантажити резервну копію',
            restoreAriaLabel: 'Відновити резервну копію',
            deleteAriaLabel: 'Видалити резервну копію'
        },
        restoreUploadSection: {
            title: 'Відновити завантажену повну резервну копію',
            description: 'Завантажте зашифровану резервну копію .gpb. Підготовка виконується у фоновому режимі, поки GeoPulse залишається доступним; активація потім замінює новіші дані та перезапускає бекенд.',
            chooseFile: 'Вибрати повну резервну копію',
            button: 'Відновити завантажену резервну копію'
        },
        restoreDialog: {
            header: 'Відновити повну резервну копію?',
            warning: 'Відновлення повної резервної копії може замінити користувачів, налаштування застосунку, GPS-дані, друзів та дозволи.',
            note: 'Підготовка виконується, поки GeoPulse залишається доступним. Активація ненадовго блокує роботу застосунку, замінює новіші дані та автоматично перезапускає бекенд.',
            sourcePasswordLabel: 'Пароль вихідної резервної копії',
            restore: 'Відновити',
            restoring: 'Відновлення'
        },
        deleteDialog: {
            header: 'Видалити резервну копію?',
            message: 'Видалити {fileName} з налаштованої локальної папки резервних копій?',
            note: 'Це видаляє лише файл резервної копії на сервері.',
            delete: 'Видалити'
        },
        toasts: {
            loadFilesFailed: 'Помилка завантаження',
            loadFilesFailedFallback: 'Не вдалося завантажити локальні резервні копії',
            loadConfigFailedFallback: 'Не вдалося завантажити налаштування резервного копіювання',
            downloadStarted: 'Завантаження розпочато',
            downloadStartedDetail: 'Завантаження повної резервної копії розпочато.',
            downloadFailed: 'Помилка завантаження',
            downloadFailedFallback: 'Не вдалося завантажити повну резервну копію',
            backupComplete: 'Резервне копіювання завершено',
            backupCompleteDetail: 'Створено {fileName}',
            backupFailed: 'Помилка резервного копіювання',
            backupFailedFallback: 'Не вдалося виконати резервне копіювання',
            saveFailed: 'Помилка збереження',
            passwordsDoNotMatch: 'Паролі резервного копіювання не збігаються',
            passwordLengthError: 'Новий пароль резервного копіювання має містити від 12 до 1024 символів',
            saveConfigFailedFallback: 'Не вдалося зберегти налаштування резервного копіювання',
            settingsSaved: 'Налаштування збережено',
            settingsSavedDetail: 'Налаштування резервного копіювання оновлено.',
            downloadLocalFailedFallback: 'Не вдалося завантажити локальну резервну копію',
            backupDeleted: 'Резервну копію видалено',
            backupDeletedDetail: 'Видалено {fileName}',
            deleteFailed: 'Помилка видалення',
            deleteFailedFallback: 'Не вдалося видалити резервну копію',
            restoreFailed: 'Помилка відновлення',
            restorePasswordLengthError: 'Пароль відновлення має містити від 1 до 1024 символів',
            restoreFailedFallback: 'Не вдалося відновити повну резервну копію',
            activationRetryFailed: 'Помилка повторної активації',
            activationRetryFailedFallback: 'Не вдалося повторити активацію',
            discardFailed: 'Помилка скасування',
            discardFailedFallback: 'Не вдалося скасувати підготовлене відновлення'
        }
    },
    composable: {
        loadFailedSummary: 'Помилка',
        loadFailedDetail: 'Не вдалося завантажити налаштування {category}',
        validationErrorSummary: 'Помилка перевірки',
        updateSuccessSummary: 'Успіх',
        settingUpdatedDetail: '{label} оновлено',
        updateErrorSummary: 'Помилка',
        updateFailedFallback: 'Не вдалося оновити налаштування',
        resetSuccessDetail: '{label} скинуто до значення за замовчуванням',
        resetFailedDetail: 'Не вдалося скинути налаштування'
    }
}

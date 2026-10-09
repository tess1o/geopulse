/**
 * Sign-in, registration, invitations, and the guest-facing entry pages (Ukrainian).
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 *
 * 'OIDC' stays as the protocol name, and 'GeoPulse' is a product name. `login.title`
 * ('Welcome Back') and `login.toasts.signedIn.title` ('Welcome Back!') are different strings in
 * English and keep separate Ukrainian renderings ('З поверненням' vs 'З поверненням!').
 */
export default {
    validation: {
        emailRequired: 'Електронна пошта обовʼязкова',
        emailInvalid: 'Введіть дійсну адресу електронної пошти',
        passwordRequired: 'Пароль обовʼязковий',
        confirmRequired: 'Підтвердьте свій пароль',
        mismatch: 'Паролі не збігаються'
    },
    toasts: {
        registrationDisabled: 'Реєстрацію вимкнено',
        registrationDisabledDetail: 'Реєстрація нових користувачів наразі вимкнена. Увійдіть, якщо у вас уже є обліковий запис.'
    },
    oidc: {
        continueWith: 'Продовжити через {provider}',
        dividerDefault: 'Або продовжити через',
        providerIconAlt: 'Значок {provider}'
    },
    callback: {
        processingTitle: 'Завершуємо автентифікацію...',
        processingDescription: 'Зачекайте, поки ми безпечно виконуємо вхід.',
        errorTitle: 'Помилка автентифікації',
        returnToLogin: 'Повернутися до входу',
        missingParams: 'Недійсний запит зворотного виклику. Відсутні обовʼязкові параметри.',
        genericError: 'Сталася невідома помилка автентифікації.',
        toasts: {
            welcomeNew: 'Ласкаво просимо до GeoPulse!',
            welcomeBack: 'З поверненням!',
            authenticated: 'Автентифікацію успішно виконано',
            linked: {
                title: 'Обліковий запис успішно привʼязано!',
                detail: 'Ваші облікові записи привʼязано, і ви увійшли в систему.'
            }
        }
    },
    linking: {
        dialogHeader: 'Привʼязати обліковий запис',
        title: 'Обліковий запис уже існує',
        description: 'Обліковий запис з {email} уже існує. Щоб продовжити, підтвердьте свою особу, і ми привʼяжемо ваш обліковий запис {provider}.',
        verifyPassword: 'Підтвердити паролем',
        linkAndContinue: 'Привʼязати та продовжити',
        verifyWithLinked: 'Підтвердити привʼязаним обліковим записом',
        or: 'АБО',
        cancel: 'Скасувати',
        errors: {
            passwordVerificationFailed: 'Не вдалося підтвердити пароль'
        }
    },
    login: {
        disabledMessage: 'Вхід наразі вимкнено. Зверніться до адміністратора.',
        emailDisabledMessage: 'Вхід за електронною поштою та паролем вимкнено. Скористайтеся постачальниками OIDC нижче.',
        adminAccess: 'Доступ адміністратора',
        adminBypassNotice: 'Доступ адміністратора — обмеження входу обійдено',
        demoTitle: 'Спробуйте GeoPulse',
        title: 'З поверненням',
        demoSubtitle: 'Виберіть демо-профіль',
        subtitle: 'Увійдіть, щоб продовжити подорож',
        emailLabel: 'Електронна пошта',
        emailPlaceholder: 'Введіть свою електронну пошту',
        passwordLabel: 'Пароль',
        passwordPlaceholder: 'Введіть свій пароль',
        signIn: 'Увійти',
        noAccount: 'Немає облікового запису?',
        createAccount: 'Створити обліковий запис',
        copyReferenceAria: 'Скопіювати ID помилки',
        toasts: {
            signedIn: {
                title: 'З поверненням!',
                detail: 'Ви успішно увійшли'
            },
            demoFailed: 'Не вдалося увійти в демо',
            loginFailed: 'Не вдалося увійти',
            optionsLoadFailed: 'Не вдалося завантажити параметри входу',
            optionsLoadFailedDetail: 'Не вдалося отримати зовнішніх постачальників входу. Ви все ще можете увійти за допомогою електронної пошти та пароля.',
            oidcInitFailed: 'Не вдалося ініціалізувати вхід через {provider}. Спробуйте ще раз.'
        },
        errors: {
            invalidCredentials: 'Невірна електронна пошта або пароль. Перевірте свої дані та спробуйте ще раз.',
            lockedOut: 'Ваш обліковий запис заблоковано або призупинено. Зверніться до підтримки.',
            tooManyAttempts: 'Забагато спроб входу. Зачекайте кілька хвилин і спробуйте знову.',
            badFormat: 'Перевірте формат електронної пошти та пароля.',
            chooseProfile: 'Виберіть демо-профіль і спробуйте ще раз.',
            demoDisabled: 'Демо-вхід наразі вимкнено.',
            profileUnavailable: 'Цей демо-профіль зараз недоступний.',
            demoGeneric: 'Не вдалося увійти в демо. Спробуйте ще раз.'
        }
    },
    register: {
        disabledMessage: 'Реєстрація за допомогою електронної пошти та пароля наразі вимкнена. Використовуйте OIDC для реєстрації',
        disabledMessageShort: 'Реєстрація за допомогою електронної пошти та пароля наразі вимкнена.',
        title: 'Створити обліковий запис',
        subtitle: 'Приєднуйтеся до GeoPulse, щоб відстежувати та аналізувати свої переміщення',
        emailLabel: 'Електронна пошта',
        fullNameLabel: 'Повне імʼя',
        passwordLabel: 'Пароль',
        confirmPasswordLabel: 'Підтвердьте пароль',
        emailPlaceholder: 'Введіть свою електронну пошту',
        fullNamePlaceholder: 'Введіть повне імʼя',
        passwordPlaceholder: 'Створіть пароль',
        confirmPasswordPlaceholder: 'Підтвердьте свій пароль',
        createAccount: 'Створити обліковий запис',
        alreadyHaveAccount: 'Уже маєте обліковий запис?',
        signIn: 'Увійти',
        localisationNote: 'Визначено автоматично за вашим браузером. Змінити можна пізніше в профілі.',
        timezoneLabel: 'Часовий пояс',
        validation: {
            fullNameRequired: 'Повне імʼя обовʼязкове',
            fullNameTooShort: 'Повне імʼя має містити щонайменше 2 символи',
            passwordTooShort: 'Пароль має містити щонайменше 6 символів'
        },
        toasts: {
            created: {
                title: 'Ласкаво просимо до GeoPulse!',
                detail: 'Ваш обліковий запис успішно створено'
            },
            failed: 'Не вдалося зареєструватися',
            optionsLoadFailed: 'Не вдалося завантажити параметри реєстрації',
            optionsLoadFailedDetail: 'Не вдалося отримати конфігурацію реєстрації.',
            oidcInitFailed: 'Не вдалося ініціалізувати реєстрацію через {provider}. Спробуйте ще раз.',
            signInRequired: {
                title: 'Обліковий запис створено',
                detail: 'Ваш обліковий запис готовий. Увійдіть, щоб продовжити.'
            }
        },
        errors: {
            emailExists: 'Обліковий запис із цією електронною поштою вже існує',
            checkInformation: 'Перевірте свої дані та спробуйте ще раз',
            serverError: 'Помилка сервера. Спробуйте пізніше',
            failed: 'Не вдалося зареєструватися. Спробуйте ще раз.',
            emailTooLong: 'Електронна пошта задовга (максимум {max})',
            fullNameLength: 'Повне імʼя має містити від {min} до {max} символів',
            passwordLength: 'Пароль має містити від {min} до {max} символів'
        }
    },
    invitation: {
        brand: 'GeoPulse',
        title: 'Завершіть реєстрацію',
        validating: 'Перевіряємо запрошення...',
        invalidTitle: 'Недійсне запрошення',
        invalidMessage: 'Це запрошення недійсне',
        emailLabel: 'Електронна пошта',
        fullNameLabel: 'Повне імʼя',
        passwordLabel: 'Пароль',
        confirmPasswordLabel: 'Підтвердьте пароль',
        timezoneLabel: 'Часовий пояс',
        emailPlaceholder: 'Введіть свою електронну пошту',
        fullNamePlaceholder: 'Введіть повне імʼя',
        passwordPlaceholder: 'Введіть пароль',
        confirmPasswordPlaceholder: 'Підтвердьте пароль',
        createAccount: 'Створити обліковий запис',
        goToLogin: 'Перейти до входу',
        timezoneDetected: 'Визначено: {timezone}',
        loginLink: 'Уже маєте обліковий запис? Увійти',
        validation: {
            emailInvalid: 'Недійсний формат електронної пошти',
            passwordTooShort: 'Пароль має містити щонайменше 3 символи'
        },
        toasts: {
            created: {
                title: 'Ласкаво просимо до GeoPulse!',
                detail: 'Ваш обліковий запис успішно створено'
            }
        },
        status: {
            used: 'Це запрошення вже використано',
            expired: 'Термін дії цього запрошення минув',
            revoked: 'Це запрошення відкликано'
        },
        errors: {
            validateFailed: 'Не вдалося перевірити запрошення',
            notFound: 'Це посилання-запрошення недійсне. Попросіть адміністратора надіслати нове.'
        }
    }
}

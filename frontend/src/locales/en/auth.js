/**
 * Sign-in, registration, invitations, and the guest-facing entry pages.
 *
 * EN values are copied verbatim from the literals they replace: the test suite is pinned to English
 * and asserts exact copy.
 *
 * `validation.*` and `toasts.registrationDisabled*` are shared by the sign-in and registration forms,
 * which showed the same sentences. `shareNote`-style duplication is avoided; update both call sites
 * if either form's wording needs to diverge.
 *
 * The clipboard toasts reuse `common.clipboard.*`, which already held those exact sentences.
 */
export default {
    validation: {
        emailRequired: 'Email is required',
        emailInvalid: 'Please enter a valid email address',
        passwordRequired: 'Password is required',
        confirmRequired: 'Please confirm your password',
        mismatch: 'Passwords do not match'
    },
    toasts: {
        registrationDisabled: 'Registration Disabled',
        registrationDisabledDetail: 'New user registration is currently disabled. Please log in if you already have an account.'
    },
    oidc: {
        continueWith: 'Continue with {provider}',
        dividerDefault: 'Or continue with',
        providerIconAlt: '{provider} icon'
    },
    callback: {
        processingTitle: 'Completing authentication...',
        processingDescription: 'Please wait while we securely log you in.',
        errorTitle: 'Authentication Failed',
        returnToLogin: 'Return to Login',
        missingParams: 'Invalid callback request. Missing required parameters.',
        genericError: 'An unknown authentication error occurred.',
        toasts: {
            welcomeNew: 'Welcome to GeoPulse!',
            welcomeBack: 'Welcome back!',
            authenticated: 'Successfully authenticated',
            linked: {
                title: 'Account Linked Successfully!',
                detail: 'Your accounts have been linked and you are now logged in.'
            }
        }
    },
    linking: {
        dialogHeader: 'Link Your Account',
        title: 'Account Already Exists',
        description: 'An account with {email} already exists. To continue, please verify your identity and we\'ll link your {provider} account.',
        verifyPassword: 'Verify with Password',
        linkAndContinue: 'Link Account & Continue',
        verifyWithLinked: 'Verify with Linked Account',
        or: 'OR',
        cancel: 'Cancel',
        errors: {
            passwordVerificationFailed: 'Password verification failed'
        }
    },
    login: {
        disabledMessage: 'Login is currently disabled. Please contact your administrator.',
        emailDisabledMessage: 'Email/password login is disabled. Please use OIDC providers below.',
        adminAccess: 'Administrator Access',
        adminBypassNotice: 'Administrator access - login restrictions bypassed',
        demoTitle: 'Try GeoPulse',
        title: 'Welcome Back',
        demoSubtitle: 'Choose a demo profile',
        subtitle: 'Sign in to continue your journey',
        emailLabel: 'Email Address',
        emailPlaceholder: 'Enter your email',
        passwordLabel: 'Password',
        passwordPlaceholder: 'Enter your password',
        signIn: 'Sign In',
        noAccount: "Don't have an account?",
        createAccount: 'Create account',
        copyReferenceAria: 'Copy error reference id',
        toasts: {
            signedIn: {
                title: 'Welcome Back!',
                detail: 'You have successfully signed in'
            },
            demoFailed: 'Demo Login Failed',
            loginFailed: 'Login Failed',
            optionsLoadFailed: 'Could not load login options',
            optionsLoadFailedDetail: 'Failed to retrieve external login providers. You can still log in with email and password.',
            oidcInitFailed: 'Failed to initialize login with {provider}. Please try again.'
        },
        errors: {
            invalidCredentials: 'Invalid email or password. Please check your credentials and try again.',
            lockedOut: 'Your account is locked or suspended. Please contact support.',
            tooManyAttempts: 'Too many login attempts. Please wait a few minutes before trying again.',
            badFormat: 'Please check your email and password format.',
            chooseProfile: 'Please choose a demo profile and try again.',
            demoDisabled: 'Demo login is currently disabled.',
            profileUnavailable: 'This demo profile is not available right now.',
            demoGeneric: 'Demo login failed. Please try again.'
        }
    },
    register: {
        disabledMessage: 'Sign up using email/password is currently disabled. Use OIDC for registration',
        disabledMessageShort: 'Sign up using email/password is currently disabled.',
        title: 'Create Account',
        subtitle: 'Join GeoPulse to start tracking and analyzing your location journey',
        emailLabel: 'Email Address',
        fullNameLabel: 'Full Name',
        passwordLabel: 'Password',
        confirmPasswordLabel: 'Confirm Password',
        emailPlaceholder: 'Enter your email',
        fullNamePlaceholder: 'Enter your full name',
        passwordPlaceholder: 'Create a password',
        confirmPasswordPlaceholder: 'Confirm your password',
        createAccount: 'Create Account',
        alreadyHaveAccount: 'Already have an account?',
        signIn: 'Sign In',
        localisationNote: 'Auto-detected from your browser. Change it later from your profile.',
        timezoneLabel: 'Timezone',
        validation: {
            fullNameRequired: 'Full name is required',
            fullNameTooShort: 'Full name must be at least 2 characters',
            passwordTooShort: 'Password must be at least 6 characters'
        },
        toasts: {
            created: {
                title: 'Welcome to GeoPulse!',
                detail: 'Your account has been created successfully'
            },
            failed: 'Registration Failed',
            optionsLoadFailed: 'Could not load registration options',
            optionsLoadFailedDetail: 'Failed to retrieve registration configuration.',
            oidcInitFailed: 'Failed to initialize registration with {provider}. Please try again.'
        },
        errors: {
            emailExists: 'An account with this email already exists',
            checkInformation: 'Please check your information and try again',
            invalidData: 'Invalid registration data provided',
            serverError: 'Server error. Please try again later'
        }
    },
    invitation: {
        brand: 'GeoPulse',
        title: 'Complete your registration',
        validating: 'Validating invitation...',
        invalidTitle: 'Invalid Invitation',
        invalidMessage: 'This invitation is not valid',
        emailLabel: 'Email',
        fullNameLabel: 'Full Name',
        passwordLabel: 'Password',
        confirmPasswordLabel: 'Confirm Password',
        timezoneLabel: 'Timezone',
        emailPlaceholder: 'Enter your email',
        fullNamePlaceholder: 'Enter your full name',
        passwordPlaceholder: 'Enter password',
        confirmPasswordPlaceholder: 'Confirm password',
        createAccount: 'Create Account',
        goToLogin: 'Go to Login',
        timezoneDetected: 'Detected: {timezone}',
        loginLink: 'Already have an account? Sign in',
        validation: {
            emailInvalid: 'Invalid email format',
            passwordTooShort: 'Password must be at least 3 characters'
        },
        toasts: {
            created: {
                title: 'Welcome to GeoPulse!',
                detail: 'Your account has been created successfully'
            }
        },
        errors: {
            validateFailed: 'Failed to validate invitation',
            registrationFailed: 'Registration failed. Please try again.'
        }
    }
}

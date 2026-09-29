import {defineStore} from 'pinia'
import apiService from '../utils/apiService'
import {useTimezone} from '@/composables/useTimezone'
import {useLocale} from '@/composables/useLocale'
import {clearCachedUserProfile, readCachedUserProfile, writeCachedUserProfile} from '@/utils/userProfileCache'
import {resolvePreferredLocale} from '@/locales'
import {isBackendDown} from '@/utils/errorHandler'
import {normalizeApiError} from '@/utils/apiErrorDetail'

let authReconcilePromise = null

function shouldPreserveCachedProfile(error) {
    if (!error) {
        return false
    }

    if (isBackendDown(error)) {
        return true
    }

    // Errors travelling through fail() have already been normalized by normalizeApiError, which flattens
    // response.status to a top-level status and drops .response entirely.
    const status = error.response?.status ?? error.status
    return typeof status === 'number' && status >= 500
}

// Preference values mirrored flat onto the in-memory user and the cached profile. The API nests them
// (uiPreferences, timelineDisplay.preferences, timelineDisplay.capabilities) and already applies defaults;
// these defaults cover partial payloads and cached profiles written by older builds.
const UI_PREFERENCE_DEFAULTS = {
    distanceUnit: 'KILOMETERS',
    temperatureUnit: 'CELSIUS',
    defaultRedirectUrl: '',
    dateFormat: 'MDY',
    timeFormat: '24h',
    language: 'en'
}

const TIMELINE_DISPLAY_PREFERENCE_DEFAULTS = {
    customMapTileUrl: '',
    customMapStyleUrl: '',
    mapRenderMode: 'VECTOR',
    defaultDateRangePreset: '',
    showCurrentLocationTelemetry: true,
    autoShowTripReplayControls: true,
    enable3dBuildingsByDefault: false,
    mapMatchingEnabled: false,
    mapMatchingExcludedMovementTypes: [],
    defaultPathColor: '',
    activePathColor: ''
}

const TIMELINE_DISPLAY_CAPABILITY_DEFAULTS = {
    mapMatchingAvailable: false
}

// Picks the keys of `defaults` from `source`, falling back to the default for missing, null or empty values.
function withDefaults(defaults, source) {
    return Object.fromEntries(Object.entries(defaults).map(([key, fallback]) => {
        const value = source?.[key]
        const resolved = value === undefined || value === null || value === '' ? fallback : value
        return [key, Array.isArray(resolved) ? [...resolved] : resolved]
    }))
}

function flattenTimelineDisplaySettings(settings) {
    return settings ? {...settings.preferences, ...settings.capabilities} : settings
}

function normalizeUser(source) {
    if (!source) {
        return null
    }

    const raw = source.user || source
    const id = raw.id || raw.userId || null
    if (!id) {
        return null
    }

    // API payloads nest preferences; the cached profile and patched in-memory user are already flat.
    const timelineDisplay = raw.timelineDisplay || {}

    return {
        id,
        userId: id,
        fullName: raw.fullName || '',
        email: raw.email || '',
        avatar: raw.avatar || null,
        timezone: raw.timezone || 'UTC',
        createdAt: raw.createdAt || null,
        hasPassword: !!raw.hasPassword,
        ...withDefaults(UI_PREFERENCE_DEFAULTS, raw.uiPreferences || raw),
        ...withDefaults(TIMELINE_DISPLAY_PREFERENCE_DEFAULTS, timelineDisplay.preferences || raw),
        ...withDefaults(TIMELINE_DISPLAY_CAPABILITY_DEFAULTS, timelineDisplay.capabilities || raw),
        demoMode: !!raw.demoMode,
        canViewAdmin: !!raw.canViewAdmin || raw.role === 'ADMIN',
        adminReadOnly: !!raw.adminReadOnly,
        role: raw.role || 'USER'
    }
}

export const useAuthStore = defineStore('auth', {
    state: () => ({
        user: null,
        isAuthenticated: false,
        error: null,
        accountLinking: null,
        authStatus: {
            demoModeEnabled: false,
            demoAdminReadOnlyEnabled: false,
            demoResetIntervalHours: 2,
            demoDeployUrl: 'https://geopulse.cc',
            demoPersonas: []
        }
    }),

    getters: {
        getCurrentUser: (state) => state.user,
        getIsAuthenticated: (state) => state.isAuthenticated,
        userName: (state) => state.user?.fullName || '',
        currentUser: (state) => state.user?.avatar || '',
        userEmail: (state) => state.user?.email || '',
        userId: (state) => state.user?.userId || null,
        userAvatar: (state) => state.user?.avatar || '',
        userTimezone: (state) => state.user?.timezone || 'UTC',
        hasPassword: (state) => state.user?.hasPassword || false,
        customMapTileUrl: (state) => state.user?.customMapTileUrl || '',
        customMapStyleUrl: (state) => state.user?.customMapStyleUrl || '',
        mapRenderMode: (state) => state.user?.mapRenderMode || 'VECTOR',
        distanceUnit: (state) => state.user?.distanceUnit || 'KILOMETERS',
        temperatureUnit: (state) => state.user?.temperatureUnit || 'CELSIUS',
        defaultRedirectUrl: (state) => state.user?.defaultRedirectUrl || '',
        dateFormat: (state) => state.user?.dateFormat || 'MDY',
        timeFormat: (state) => state.user?.timeFormat || '24h',
        language: (state) => state.user?.language || 'en',
        defaultDateRangePreset: (state) => state.user?.defaultDateRangePreset || '',
        autoShowTripReplayControls: (state) => state.user?.autoShowTripReplayControls ?? true,
        enable3dBuildingsByDefault: (state) => state.user?.enable3dBuildingsByDefault ?? false,
        mapMatchingEnabled: (state) => state.user?.mapMatchingEnabled ?? false,
        mapMatchingExcludedMovementTypes: (state) => state.user?.mapMatchingExcludedMovementTypes ?? [],
        mapMatchingAvailable: (state) => state.user?.mapMatchingAvailable ?? false,
        defaultPathColor: (state) => state.user?.defaultPathColor || '',
        activePathColor: (state) => state.user?.activePathColor || '',
        userRole: (state) => state.user?.role || 'USER',
        isAdmin: (state) => state.user?.role === 'ADMIN',
        demoMode: (state) => !!state.user?.demoMode,
        demoModeEnabled: (state) => !!state.authStatus?.demoModeEnabled || !!state.user?.demoMode,
        demoReadOnly: (state) => (!!state.authStatus?.demoModeEnabled || !!state.user?.demoMode) && state.user?.role !== 'ADMIN',
        demoResetIntervalHours: (state) => state.authStatus?.demoResetIntervalHours || 2,
        demoDeployUrl: (state) => state.authStatus?.demoDeployUrl || 'https://geopulse.cc',
        demoPersonas: (state) => state.authStatus?.demoPersonas || [],
        canViewAdmin: (state) => !!state.user?.canViewAdmin || state.user?.role === 'ADMIN',
        adminReadOnly: (state) => !!state.user?.adminReadOnly,
    },

    actions: {
        _applyUserState(rawUser, {persist = true} = {}) {
            const user = normalizeUser(rawUser)
            this.user = user
            this.isAuthenticated = !!user

            const timezone = useTimezone()
            const locale = useLocale()
            if (user) {
                if (persist) {
                    writeCachedUserProfile(user)
                }
                timezone.setTimezone(user.timezone || 'UTC')
                timezone.setDateFormat(user.dateFormat || 'MDY')
                timezone.setTimeFormat(user.timeFormat || '24h')
                // The profile is the authority for language, so persist:false -- writing it back would
                // be a pointless round trip. Not awaited: setUser stays synchronous for its callers,
                // and the locale ref is reactive, so the UI re-renders once the catalog resolves.
                //
                // Only when the payload actually carries a language: defaulting an absent field to
                // 'en' would silently reset a Ukrainian user's UI on any partial response. Sign-out
                // below is the one place that deliberately returns to the default.
                if (user.language) {
                    void locale.setLocale(user.language, { persist: false })
                }
            } else if (persist) {
                clearCachedUserProfile()
                timezone.setDateFormat('MDY')
                timezone.setTimeFormat('24h')
                void locale.setLocale('en', { persist: false })
            }

            return user
        },

        setUser(user) {
            return this._applyUserState(user, {persist: true})
        },

        hydrateUserFromCachedProfile(cachedProfile) {
            return this._applyUserState(cachedProfile, {persist: false})
        },

        patchCurrentUser(patch) {
            if (!this.user) {
                return null
            }
            return this.setUser({...this.user, ...patch})
        },

        consumeBrowserAuthResponse(browserAuthPayload) {
            const normalizedUser = this.setUser(browserAuthPayload?.user || browserAuthPayload)
            return {
                ...(browserAuthPayload || {}),
                user: normalizedUser,
                ...(normalizedUser || {})
            }
        },

        fail(error, fallback) {
            this.error = normalizeApiError(error, fallback)
            return this.error
        },

        clearUser() {
            this.user = null
            this.isAuthenticated = false
            clearCachedUserProfile()
            const timezone = useTimezone()
            timezone.setTimezone('UTC')
            timezone.setDateFormat('MDY')
            timezone.setTimeFormat('24h')
            // Falls back to the guest's own choice or browser language, not a hardcoded 'en' --
            // otherwise a Ukrainian-speaking user loses their language on every public page on sign-out.
            void useLocale().setLocale(resolvePreferredLocale(), { persist: false })
            apiService.clearAuthData()
        },

        async login(email, password) {
            try {
                const response = await apiService.login(email, password)
                return this.consumeBrowserAuthResponse(response)
            } catch (error) {
                if (!shouldPreserveCachedProfile(error)) {
                    this.clearUser()
                }
                throw this.fail(error, 'Login failed')
            }
        },

        async demoLogin(personaId) {
            try {
                const response = await apiService.post('/auth/demo-sessions', {personaId})
                return this.consumeBrowserAuthResponse(response)
            } catch (error) {
                if (!shouldPreserveCachedProfile(error)) {
                    this.clearUser()
                }
                throw this.fail(error, 'Demo login failed')
            }
        },

        async register(email, password, fullName, timezone, language) {
            await apiService.post('/registrations', {
                email,
                password,
                fullName,
                timezone,
                language
            })
            await this.login(email, password)
        },

        async logout() {
            await apiService.logout()
            this.clearUser()
        },

        async logoutStrict() {
            await apiService.logoutStrict()
            this.clearUser()
        },

        async fetchTimelineDisplayPreferences() {
            try {
                return flattenTimelineDisplaySettings(await apiService.get('/preferences/timeline-display'))
            } catch (error) {
                throw this.fail(error, 'Failed to load timeline display preferences')
            }
        },

        async updateProfile({fullName, avatar, timezone, ...uiPreferences}) {
            const response = await apiService.patch('/users/me', {
                fullName,
                avatar,
                timezone,
                uiPreferences
            })

            const updatedUser = response
            if (updatedUser) {
                this.setUser(updatedUser)
                return this.user
            }

            return this.fetchCurrentUserProfile()
        },

        async uploadAvatar(file) {
            const formData = new FormData()
            formData.append('file', file)

            const response = await apiService.put('/users/me/avatar', formData, {
                headers: {
                    'Content-Type': 'multipart/form-data'
                }
            })

            const avatarPath = response?.avatar
            if (!avatarPath) {
                throw new Error('Avatar upload succeeded but no avatar path was returned')
            }

            return avatarPath
        },

        async updateTimelineDisplayPreferences(displayPreferences) {
            const response = await apiService.put('/preferences/timeline-display', displayPreferences)
            if (!response) {
                return null
            }

            this.patchCurrentUser({
                ...withDefaults(TIMELINE_DISPLAY_PREFERENCE_DEFAULTS, response.preferences),
                ...withDefaults(TIMELINE_DISPLAY_CAPABILITY_DEFAULTS, response.capabilities)
            })

            return flattenTimelineDisplaySettings(response)
        },

        updateUserTimezone(timezone) {
            if (!this.user) {
                return
            }
            this.patchCurrentUser({timezone})
        },

        async changePassword(oldPassword, newPassword) {
            const response = await apiService.put('/users/me/password', {
                oldPassword,
                newPassword
            })

            if (response?.hasPassword) {
                this.patchCurrentUser({hasPassword: true})
            }

            return response || null
        },

        async _reconcileAuthState() {
            if (authReconcilePromise) {
                return authReconcilePromise
            }

            authReconcilePromise = (async () => {
                try {
                    if (apiService.isTokenExpired()) {
                        const refreshed = await apiService.refreshToken()
                        if (!refreshed) {
                            this.clearUser()
                            return null
                        }
                    }

                    return await this.fetchCurrentUserProfile()
                } catch (error) {
                    if (error && !error.userMessage && !error.userTitle) {
                        apiService.handleError(error)
                    }
                    if (!shouldPreserveCachedProfile(error)) {
                        this.clearUser()
                    }
                    return null
                } finally {
                    authReconcilePromise = null
                }
            })()

            return authReconcilePromise
        },

        async checkAuth() {
            try {
                const cachedProfile = readCachedUserProfile()
                if (cachedProfile.id) {
                    if (!this.user) {
                        this.hydrateUserFromCachedProfile(cachedProfile)
                    }
                    if (apiService.isTokenExpired()) {
                        const refreshed = await apiService.refreshToken()
                        if (!refreshed) {
                            this.clearUser()
                            return null
                        }
                    }

                    return await this.fetchCurrentUserProfile()
                }

                return await this._reconcileAuthState()
            } catch (error) {
                if (error && !error.userMessage && !error.userTitle) {
                    apiService.handleError(error)
                }
                if (shouldPreserveCachedProfile(error)) {
                    return this.user
                }
                this.clearUser()
                return null
            }
        },

        async getOidcProviders() {
            try {
                return await apiService.get('/auth/oidc/providers') || []
            } catch (error) {
                console.error('Failed to get OIDC providers:', error)
                return []
            }
        },

        async initiateOidcLogin(providerName, redirectUri = null) {
            try {
                const response = await apiService.post(`/auth/oidc/login-authorizations/${providerName}`, {}, {
                    params: redirectUri ? {redirectUri} : {}
                })
                window.location.href = response.authorizationUrl
            } catch (error) {
                console.error('Failed to initiate OIDC login:', error)
                throw this.fail(error, 'Failed to initiate OIDC login')
            }
        },

        async handleOidcCallback(code, state) {
            try {
                const response = await apiService.post('/auth/oidc/callbacks', {code, state})
                this.accountLinking = null
                return this.consumeBrowserAuthResponse(response)
            } catch (error) {
                if (error?.response?.data?.code === 'OIDC_ACCOUNT_LINKING_REQUIRED') {
                    this.accountLinking = error.response.data.linking || null
                }
                if (!shouldPreserveCachedProfile(error)) {
                    this.clearUser()
                }
                throw this.fail(error, 'OIDC authentication failed')
            }
        },

        async linkOidcProvider(providerName) {
            try {
                const response = await apiService.post(`/auth/oidc/connections/${providerName}/authorizations`)
                window.location.href = response.authorizationUrl
            } catch (error) {
                console.error('Failed to initiate OIDC linking:', error)
                throw this.fail(error, 'Failed to initiate OIDC linking')
            }
        },

        async unlinkOidcProvider(providerName) {
            try {
                await apiService.delete(`/auth/oidc/connections/${providerName}`)
            } catch (error) {
                console.error('Failed to unlink OIDC provider:', error)
                throw this.fail(error, 'Failed to unlink OIDC provider')
            }
        },

        async getLinkedProviders() {
            try {
                return await apiService.get('/auth/oidc/connections')
            } catch (error) {
                console.error('Failed to get linked OIDC providers:', error)
                return []
            }
        },

        async fetchCurrentUserProfile() {
            try {
                const user = await apiService.get('/users/me')
                this.setUser(user)
                return this.user
            } catch (error) {
                console.error('Failed to fetch current user profile:', error)
                throw this.fail(error, 'Failed to fetch user profile')
            }
        },

        async getRegistrationStatus() {
            try {
                return await apiService.get('/auth/sessions/current')
            } catch (error) {
                console.error('Failed to fetch registration status:', error)
                return { passwordRegistrationEnabled: false, oidcRegistrationEnabled: false }
            }
        },

        async getAuthStatus() {
            const fallback = {
                passwordRegistrationEnabled: false,
                oidcRegistrationEnabled: false,
                passwordLoginEnabled: true,
                oidcLoginEnabled: true,
                adminLoginBypassEnabled: true,
                guestRootRedirectToLoginEnabled: false,
                demoModeEnabled: false,
                demoAdminReadOnlyEnabled: false,
                demoResetIntervalHours: 2,
                demoDeployUrl: 'https://geopulse.cc',
                demoPersonas: []
            }
            try {
                const response = await apiService.get('/auth/sessions/current')
                const status = {...fallback, ...response}
                this.authStatus = status
                return status
            } catch (error) {
                console.error('Failed to get auth status:', error)
                this.authStatus = fallback
                return fallback
            }
        },

        async generateMobileAuth() {
            try {
                return await apiService.post('/auth/mobile-codes', {})
            } catch (error) {
                throw this.fail(error, 'Unable to create authentication link')
            }
        },

        async validateInvitation(token) {
            try {
                return await apiService.get(`/registration-invitations/${token}`)
            } catch (error) {
                throw this.fail(error, 'Failed to validate invitation')
            }
        },

        async registerInvitation(token, payload) {
            try {
                return await apiService.post(`/registration-invitations/${token}/registrations`, payload)
            } catch (error) {
                throw this.fail(error, 'Registration failed')
            }
        },

        async listApiTokens() {
            try {
                return await apiService.get('/api-tokens')
            } catch (error) {
                throw this.fail(error, 'Failed to load API tokens')
            }
        },

        async saveApiToken(id, payload) {
            try {
                return id
                    ? await apiService.put(`/api-tokens/${id}`, payload)
                    : await apiService.post('/api-tokens', payload)
            } catch (error) {
                throw this.fail(error, 'Failed to save API token')
            }
        },

        async revokeApiToken(id) {
            try {
                await apiService.delete(`/api-tokens/${id}`)
            } catch (error) {
                throw this.fail(error, 'Failed to revoke API token')
            }
        },

        async linkAccountWithPassword(payload) {
            try {
                return this.consumeBrowserAuthResponse(
                    await apiService.post('/auth/oidc/account-links/password', payload))
            } catch (error) {
                throw this.fail(error, 'Password verification failed')
            }
        },

        async initiateOidcAccountVerification(payload) {
            try {
                return await apiService.post('/auth/oidc/account-links/oidc', payload)
            } catch (error) {
                throw this.fail(error, 'OIDC verification initiation failed')
            }
        }
    }
})

import {defineStore} from 'pinia'
import apiService from '../utils/apiService'
import {useTimezone} from '@/composables/useTimezone'
import {clearUserSnapshot, readUserSnapshot, writeUserSnapshot} from '@/utils/authSnapshotStorage'

let authReconcilePromise = null

function normalizeUser(source) {
    if (!source) {
        return null
    }

    const raw = source.user || source
    const id = raw.id || raw.userId || null
    if (!id) {
        return null
    }

    return {
        id,
        userId: id,
        fullName: raw.fullName || '',
        email: raw.email || '',
        avatar: raw.avatar || null,
        timezone: raw.timezone || 'UTC',
        createdAt: raw.createdAt || null,
        hasPassword: !!raw.hasPassword,
        customMapTileUrl: raw.customMapTileUrl || '',
        measureUnit: raw.measureUnit || 'METRIC',
        defaultRedirectUrl: raw.defaultRedirectUrl || '',
        role: raw.role || 'USER'
    }
}

export const useAuthStore = defineStore('auth', {
    state: () => ({
        user: null,
        isAuthenticated: false
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
        measureUnit: (state) => state.user?.measureUnit || 'METRIC',
        defaultRedirectUrl: (state) => state.user?.defaultRedirectUrl || '',
        userRole: (state) => state.user?.role || 'USER',
        isAdmin: (state) => state.user?.role === 'ADMIN',
    },

    actions: {
        _applyUserState(rawUser, {persist = true} = {}) {
            const user = normalizeUser(rawUser)
            this.user = user
            this.isAuthenticated = !!user

            const timezone = useTimezone()
            if (user) {
                if (persist) {
                    writeUserSnapshot(user)
                }
                timezone.setTimezone(user.timezone || 'UTC')
            } else if (persist) {
                clearUserSnapshot()
            }

            return user
        },

        setUser(user) {
            return this._applyUserState(user, {persist: true})
        },

        hydrateUserFromSnapshot(snapshot) {
            return this._applyUserState(snapshot, {persist: false})
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

        clearUser() {
            this.user = null
            this.isAuthenticated = false
            clearUserSnapshot()
            apiService.clearAuthData()
        },

        async login(email, password) {
            try {
                const response = await apiService.login(email, password)
                return this.consumeBrowserAuthResponse(response?.data)
            } catch (error) {
                this.clearUser()
                throw error
            }
        },

        async register(email, password, fullName, timezone) {
            await apiService.post('/users/register', {
                email,
                password,
                fullName,
                timezone
            })
            await this.login(email, password)
        },

        async logout() {
            await apiService.logout()
            this.clearUser()
        },

        async updateProfile({fullName, avatar, timezone, measureUnit, defaultRedirectUrl}) {
            const response = await apiService.post('/users/update', {
                fullName,
                avatar,
                timezone,
                measureUnit,
                defaultRedirectUrl
            })

            const updatedUser = response?.data
            if (updatedUser) {
                this.setUser(updatedUser)
                return this.user
            }

            return this.fetchCurrentUserProfile()
        },

        async updateTimelineDisplayPreferences(displayPreferences) {
            const response = await apiService.put('/users/preferences/timeline/display', displayPreferences)
            const updatedPreferences = response?.data || null

            if (updatedPreferences && Object.prototype.hasOwnProperty.call(updatedPreferences, 'customMapTileUrl')) {
                this.patchCurrentUser({customMapTileUrl: updatedPreferences.customMapTileUrl || ''})
            }

            return updatedPreferences
        },

        updateUserTimezone(timezone) {
            if (!this.user) {
                return
            }
            this.patchCurrentUser({timezone})
        },

        async changePassword(oldPassword, newPassword) {
            const response = await apiService.post('/users/changePassword', {
                oldPassword,
                newPassword
            })

            if (response?.data?.hasPassword) {
                this.patchCurrentUser({hasPassword: true})
            }

            return response?.data || null
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
                    this.clearUser()
                    return null
                } finally {
                    authReconcilePromise = null
                }
            })()

            return authReconcilePromise
        },

        async checkAuth() {
            try {
                const snapshot = readUserSnapshot()
                if (snapshot.id) {
                    if (!this.user) {
                        this.hydrateUserFromSnapshot(snapshot)
                    }
                    // Keep optimistic snapshot semantics on reloads.
                    // Many flows (including tests) intentionally mutate the cached snapshot
                    // and expect the app to render from it after reload.
                    if (apiService.isTokenExpired()) {
                        const refreshed = await apiService.refreshToken()
                        if (!refreshed) {
                            this.clearUser()
                            return null
                        }
                    }
                    return this.user
                }

                return await this._reconcileAuthState()
            } catch (error) {
                this.clearUser()
                return null
            }
        },

        async getOidcProviders() {
            try {
                const response = await apiService.get('/auth/oidc/providers')
                return response?.data || []
            } catch (error) {
                console.error('Failed to get OIDC providers:', error)
                return []
            }
        },

        async initiateOidcLogin(providerName) {
            try {
                const response = await apiService.post(`/auth/oidc/login/${providerName}`)
                window.location.href = response.data.authorizationUrl
            } catch (error) {
                console.error('Failed to initiate OIDC login:', error)
                throw error
            }
        },

        async handleOidcCallback(code, state) {
            try {
                const response = await apiService.post('/auth/oidc/callback', {code, state})
                return this.consumeBrowserAuthResponse(response?.data)
            } catch (error) {
                this.clearUser()
                throw error
            }
        },

        async linkOidcProvider(providerName) {
            try {
                const response = await apiService.post(`/auth/oidc/link/${providerName}`)
                window.location.href = response.data.authorizationUrl
            } catch (error) {
                console.error('Failed to initiate OIDC linking:', error)
                throw error
            }
        },

        async unlinkOidcProvider(providerName) {
            try {
                await apiService.delete(`/auth/oidc/unlink/${providerName}`)
            } catch (error) {
                console.error('Failed to unlink OIDC provider:', error)
                throw error
            }
        },

        async getLinkedProviders() {
            try {
                const response = await apiService.get('/auth/oidc/connections')
                return response.data
            } catch (error) {
                console.error('Failed to get linked OIDC providers:', error)
                return []
            }
        },

        async fetchCurrentUserProfile() {
            try {
                const response = await apiService.get('/users/me')
                const userData = response?.data
                this.setUser(userData)
                return this.user
            } catch (error) {
                console.error('Failed to fetch current user profile:', error)
                throw error
            }
        },

        async getRegistrationStatus() {
            try {
                const response = await apiService.get('/auth/status')
                return response.data
            } catch (error) {
                console.error('Failed to fetch registration status:', error)
                return { passwordRegistrationEnabled: false, oidcRegistrationEnabled: false }
            }
        },

        async getAuthStatus() {
            try {
                const response = await apiService.get('/auth/status')
                return response.data || {
                    passwordRegistrationEnabled: false,
                    oidcRegistrationEnabled: false,
                    passwordLoginEnabled: true,
                    oidcLoginEnabled: true,
                    adminLoginBypassEnabled: true
                }
            } catch (error) {
                console.error('Failed to get auth status:', error)
                return {
                    passwordRegistrationEnabled: false,
                    oidcRegistrationEnabled: false,
                    passwordLoginEnabled: true,
                    oidcLoginEnabled: true,
                    adminLoginBypassEnabled: true
                }
            }
        }
    }
})

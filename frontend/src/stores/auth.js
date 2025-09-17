import {defineStore} from 'pinia'
import apiService from '../utils/apiService'

export const useAuthStore = defineStore('auth', {
    state: () => ({
        user: null,
        isAuthenticated: false
    }),

    getters: {
        getCurrentUser: (state) => state.user,
        getIsAuthenticated: (state) => state.isAuthenticated,

        // You can also add computed getters
        userName: (state) => state.user?.fullName || '',
        currentUser: (state) => state.user?.avatar || '',
        userEmail: (state) => state.user?.email || '',
        userId: (state) => state.user?.userId || null,
        userAvatar: (state) => state.user?.avatar || '',
        hasPassword: (state) => state.user?.hasPassword || false,
    },

    actions: {
        // Set user data in the state
        setUser(user) {
            this.user = user;
            this.isAuthenticated = !!user;
            if (user) {
                // Persist essential, non-sensitive data to localStorage
                const userInfo = {
                    id: user.id,
                    userId: user.id, // For backward compatibility with other parts of the app
                    fullName: user.fullName,
                    email: user.email,
                    avatar: user.avatar,
                    createdAt: user.createdAt,
                    hasPassword: user.hasPassword
                };
                localStorage.setItem('userInfo', JSON.stringify(userInfo));
            } else {
                localStorage.removeItem('userInfo');
            }
        },

        // Clear user data from state and storage
        clearUser() {
            this.user = null;
            this.isAuthenticated = false;
            localStorage.removeItem('userInfo');
            apiService.clearAuthData();
        },

        async login(email, password) {
            try {
                const loginResponse = await apiService.login(email, password);
                this.setUser(loginResponse); // setUser will handle localStorage
                return loginResponse;
            } catch (error) {
                this.clearUser();
                throw error;
            }
        },

        async register(email, password, fullName) {
            try {
                await apiService.post('/users/register', {
                    email,
                    password,
                    fullName
                });
                await this.login(email, password);
            } catch (error) {
                throw error
            }
        },

        async logout() {
            await apiService.logout()
            this.clearUser()
        },

        //TODO: remove userId, find it on backend.
        async updateProfile(fullName, avatar, userId) {
            try {
                await apiService.post(`/users/update`, {
                    fullName,
                    avatar,
                    userId
                });
                
                // Update user info in localStorage
                const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}');
                userInfo.fullName = fullName;
                userInfo.avatar = avatar;
                localStorage.setItem('userInfo', JSON.stringify(userInfo));

                // Update the user in store
                if (this.user) {
                    this.user = {...this.user, fullName, avatar}
                }
            } catch (error) {
                throw error
            }
        },

        //TODO: remove userId, find it on backend.
        async changePassword(oldPassword, newPassword, userId) {
            try {
                await apiService.post(`/users/changePassword`, {
                    oldPassword,
                    newPassword,
                    userId
                });
            } catch (error) {
                throw error
            }
        },

        async checkAuth() {
            try {
                const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
                
                // Check if we have user profile data
                if (!userInfo.id) {
                    this.clearUser()
                    return null
                }
                
                // Check cookie expiration and refresh if needed
                if (apiService.isTokenExpired()) {
                    const refreshed = await apiService.refreshToken()
                    if (!refreshed) {
                        this.clearUser()
                        return null
                    }
                }
                
                // Use user data from localStorage (profile info only)
                const user = {
                    id: userInfo.id,
                    userId: userInfo.id,
                    fullName: userInfo.fullName,
                    email: userInfo.email,
                    avatar: userInfo.avatar,
                    createdAt: userInfo.createdAt,
                    hasPassword: userInfo.hasPassword,
                }
                
                this.setUser(user)
                return user
            } catch (error) {
                apiService.clearAuthData()
                this.clearUser()
                return null
            }
        },

        // OIDC Actions
        async getOidcProviders() {
            try {
                const response = await apiService.get('/auth/oidc/providers');
                // Defensively return an array to prevent template errors
                return response?.data || [];
            } catch (error) {
                console.error('Failed to get OIDC providers:', error);
                return [];
            }
        },

        async initiateOidcLogin(providerName) {
            try {
                const response = await apiService.post(`/auth/oidc/login/${providerName}`);
                // Redirect to provider's authorization URL
                window.location.href = response.data.authorizationUrl;
            } catch (error) {
                console.error('Failed to initiate OIDC login:', error);
                throw error;
            }
        },

        async handleOidcCallback(code, state) {
            try {
                const response = await apiService.post('/auth/oidc/callback', { code, state });
                this.setUser(response.data);
                return response.data;
            } catch (error) {
                this.clearUser();
                throw error;
            }
        },

        async linkOidcProvider(providerName) {
            try {
                const response = await apiService.post(`/auth/oidc/link/${providerName}`);
                // Redirect to provider for linking
                window.location.href = response.data.authorizationUrl;
            } catch (error) {
                console.error('Failed to initiate OIDC linking:', error);
                throw error;
            }
        },

        async unlinkOidcProvider(providerName) {
            try {
                await apiService.delete(`/auth/oidc/unlink/${providerName}`);
            } catch (error) {
                console.error('Failed to unlink OIDC provider:', error);
                throw error;
            }
        },

        async getLinkedProviders() {
            try {
                const response = await apiService.get('/auth/oidc/connections');
                return response.data;
            } catch (error) {
                console.error('Failed to get linked OIDC providers:', error);
                return [];
            }
        }
    }
})
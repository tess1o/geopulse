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
    },

    actions: {
        // Set user data (replaces SET_USER mutation)
        setUser(user) {
            this.user = user
            this.isAuthenticated = !!user
        },

        // Clear user data (used in logout)
        clearUser() {
            this.user = null
            this.isAuthenticated = false
        },

        async login(email, password) {
            try {
                const loginResponse = await apiService.login(email, password)
                this.setUser(loginResponse)
                return loginResponse;
            } catch (error) {
                apiService.clearAuthData()
                throw error
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
                    userId: userInfo.id,
                    fullName: userInfo.fullName,
                    email: userInfo.email,
                    avatar: userInfo.avatar,
                    createdAt: userInfo.createdAt,
                }
                
                this.setUser(user)
                return user
            } catch (error) {
                apiService.clearAuthData()
                this.clearUser()
                return null
            }
        }
    }
})
import {defineStore} from 'pinia'
import apiService from '../utils/apiService'
import dayjs from 'dayjs';
import {useTimezone} from "@/composables/useTimezone";

export const useShareLinksStore = defineStore('shareLinks', {
    state: () => ({
        links: [],
        activeCount: 0,
        maxLinks: 10,
        loading: false,
        error: null,
        // For shared location viewing
        sharedLocationInfo: null,
        sharedLocationData: null,
        sharedAccessToken: null,
        sharedLocationLoading: false
    }),

    getters: {
        getLinks: (state) => state.links,
        getActiveLinks: (state) => {
            return state.links.filter(link => {
                // Handle null/undefined expires_at
                const timezone = useTimezone()
                const isExpired = link.expires_at ? timezone.fromUtc(link.expires_at).isBefore(timezone.now()) : false
                return link.is_active && !isExpired
            })
        },
        getExpiredLinks: (state) => {
            return state.links.filter(link => {
                // Handle null/undefined expires_at
                const timezone = useTimezone()
                const isExpired = link.expires_at ? timezone.fromUtc(link.expires_at).isBefore(timezone.now()) : false
                return !link.is_active || isExpired
            })
        },
        canCreateNewLink: (state) => state.activeCount < state.maxLinks,
        isLoading: (state) => state.loading,
        getError: (state) => state.error,

        // Shared location getters
        getSharedLocationInfo: (state) => state.sharedLocationInfo,
        getSharedLocationData: (state) => state.sharedLocationData,
        isSharedLocationLoading: (state) => state.sharedLocationLoading
    },

    actions: {
        setLoading(loading) {
            this.loading = loading
        },

        setError(error) {
            this.error = error
        },

        clearError() {
            this.error = null
        },

        // Fetch all share links for current user
        async fetchShareLinks() {
            this.setLoading(true)
            this.clearError()
            try {
                const response = await apiService.get('/share-links')
                this.links = response.data.links
                this.maxLinks = response.data.max_links
                // Calculate active count client-side to ensure consistency
                const timezone = useTimezone()
                const isExpired = (link) => link.expires_at ? timezone.fromUtc(link.expires_at).isBefore(timezone.now()) : false
                this.activeCount = this.links.filter(link => link.is_active && !isExpired(link)).length
            } catch (error) {
                console.error('Failed to fetch share links:', error)
                this.setError(error.message || 'Failed to fetch share links')
                throw error
            } finally {
                this.setLoading(false)
            }
        },

        // Create new share link
        async createShareLink(linkData) {
            this.setLoading(true)
            this.clearError()
            try {
                // Set default expiration to 7 days from now if not provided
                if (!linkData.expires_at) {
                    const timezone = useTimezone()
                    linkData.expires_at = timezone.add(timezone.now(), 7, 'day').toISOString();
                }
                
                const response = await apiService.post('/share-links', linkData)
                console.log(response);

                // Ensure is_active is set if missing from backend response
                if (response.is_active === undefined) {
                    response.is_active = !this.isLinkExpired(response)
                }

                // Add new link to the beginning of the array
                this.links.unshift(response)
                // Only increment activeCount if the link is actually active
                if (response.is_active && !this.isLinkExpired(response)) {
                    this.activeCount++
                }
                return response
            } catch (error) {
                this.setError(error.message || 'Failed to create share link')
                throw error
            } finally {
                this.setLoading(false)
            }
        },

        // Update existing share link
        async updateShareLink(linkId, linkData) {
            this.setLoading(true)
            this.clearError()
            try {
                const response = await apiService.put(`/share-links/${linkId}`, linkData)
                // Update the link in the array
                const index = this.links.findIndex(link => link.id === linkId)
                if (index !== -1) {
                    this.links[index] = response
                }
                return response
            } catch (error) {
                this.setError(error.message || 'Failed to update share link')
                throw error
            } finally {
                this.setLoading(false)
            }
        },

        // Delete share link
        async deleteShareLink(linkId) {
            this.setLoading(true)
            this.clearError()
            try {
                await apiService.delete(`/share-links/${linkId}`)
                // Remove from array and update active count
                const linkIndex = this.links.findIndex(link => link.id === linkId)
                if (linkIndex !== -1) {
                    const wasActive = this.links[linkIndex].is_active
                    this.links.splice(linkIndex, 1)
                    if (wasActive) {
                        this.activeCount--
                    }
                }
            } catch (error) {
                this.setError(error.message || 'Failed to delete share link')
                throw error
            } finally {
                this.setLoading(false)
            }
        },

        // Public methods for shared location viewing
        async fetchSharedLocationInfo(linkId) {
            this.sharedLocationLoading = true
            this.clearError()
            try {
                const response = await apiService.get(`/shared/${linkId}/info`)
                this.sharedLocationInfo = response
                return response
            } catch (error) {
                this.setError(error.message || 'Link not found or expired')
                throw error
            } finally {
                this.sharedLocationLoading = false
            }
        },

        async verifySharedLink(linkId, password = null) {
            this.sharedLocationLoading = true
            this.clearError()
            try {
                const payload = password ? {password} : {}
                const response = await apiService.post(`/shared/${linkId}/verify`, payload)
                this.sharedAccessToken = response.access_token
                return response
            } catch (error) {
                console.error('Failed to verify shared link:', error)
                this.setError(error.userMessage || error.message || 'Access denied')
                throw error
            } finally {
                this.sharedLocationLoading = false
            }
        },

        async fetchSharedLocation(linkId) {
            if (!this.sharedAccessToken) {
                throw new Error('No access token available')
            }

            this.sharedLocationLoading = true
            this.clearError()
            try {
                const response = await apiService.getWithCustomHeaders(`/shared/${linkId}/location`, {
                    'Authorization': `Bearer ${this.sharedAccessToken}`
                })
                this.sharedLocationData = response
                return response
            } catch (error) {
                this.setError(error.message || 'Failed to fetch location data')
                throw error
            } finally {
                this.sharedLocationLoading = false
            }
        },

        // Clear shared location data when leaving shared view
        clearSharedLocationData() {
            this.sharedLocationInfo = null
            this.sharedLocationData = null
            this.sharedAccessToken = null
            this.sharedLocationLoading = false
            this.clearError()
        },

        // Helper method to check if link is expired
        async isLinkExpired(link) {
            // Handle null/undefined expires_at (should never expire)
            if (!link.expires_at) {
                return false
            }
            
            const timezone = useTimezone()
            const expirationDate = timezone.fromUtc(link.expires_at)
            const now = timezone.now()
            
            // Handle invalid date
            if (!timezone.isValidDate(link.expires_at)) {
                console.warn('Invalid expiration date:', link.expires_at)
                return false // Treat invalid dates as non-expired
            }
            
            const isExpired = expirationDate.isBefore(now)

            // Debug logging
            console.log('Link expiration check:', {
                linkId: link.id,
                expires_at: link.expires_at,
                expirationDate: expirationDate.toISOString(),
                now: now.toISOString(),
                isExpired
            })

            return isExpired
        }
    }
})
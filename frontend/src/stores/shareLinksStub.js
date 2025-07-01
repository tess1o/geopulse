import { defineStore } from 'pinia'

// Mock data generator utilities
const generateId = () => Math.random().toString(36).substr(2, 9)
const generateDate = (daysFromNow) => {
    const date = new Date()
    date.setDate(date.getDate() + daysFromNow)
    return date.toISOString()
}

// Mock location data
const mockLocationHistory = [
    { latitude: 37.7749, longitude: -122.4194, timestamp: generateDate(-2) },
    { latitude: 37.7849, longitude: -122.4094, timestamp: generateDate(-1.5) },
    { latitude: 37.7949, longitude: -122.3994, timestamp: generateDate(-1) },
    { latitude: 37.8049, longitude: -122.3894, timestamp: generateDate(-0.5) },
    { latitude: 37.8149, longitude: -122.3794, timestamp: generateDate(0) }
]

const mockCurrentLocation = {
    latitude: 37.8149,
    longitude: -122.3794,
    timestamp: generateDate(0),
    accuracy: 5.0
}

// Initial mock links
const initialMockLinks = [
    {
        id: 'link-1',
        name: 'Trip to San Francisco',
        expires_at: generateDate(7),
        has_password: false,
        show_history: true,
        is_active: true,
        created_at: generateDate(-3),
        view_count: 12
    },
    {
        id: 'link-2',
        name: 'Current Location Share',
        expires_at: generateDate(1),
        has_password: true,
        show_history: false,
        is_active: true,
        created_at: generateDate(-1),
        view_count: 3
    },
    {
        id: 'link-3',
        name: 'Expired Link',
        expires_at: generateDate(-2),
        has_password: false,
        show_history: true,
        is_active: false,
        created_at: generateDate(-10),
        view_count: 25
    }
]

export const useShareLinksStore = defineStore('shareLinksStub', {
    state: () => ({
        links: [...initialMockLinks],
        activeCount: 2,
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
        getActiveLinks: (state) => state.links.filter(link => link.is_active),
        getExpiredLinks: (state) => state.links.filter(link => !link.is_active),
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

        // Simulate API delay
        async delay(ms = 500) {
            return new Promise(resolve => setTimeout(resolve, ms))
        },

        // Mock fetch all share links
        async fetchShareLinks() {
            this.setLoading(true)
            this.clearError()
            try {
                await this.delay(800)
                
                // Update active count
                this.activeCount = this.links.filter(link => link.is_active).length
                
                console.log('📡 [STUB] Fetched share links:', this.links.length)
                return {
                    links: this.links,
                    active_count: this.activeCount,
                    max_links: this.maxLinks
                }
            } catch (error) {
                this.setError('Failed to fetch share links')
                throw error
            } finally {
                this.setLoading(false)
            }
        },

        // Mock create new share link
        async createShareLink(linkData) {
            this.setLoading(true)
            this.clearError()
            try {
                await this.delay(600)
                
                if (this.activeCount >= this.maxLinks) {
                    throw new Error('Maximum number of active links reached (10)')
                }
                
                const newLink = {
                    id: generateId(),
                    name: linkData.name || 'Untitled Link',
                    expires_at: linkData.expires_at,
                    has_password: !!linkData.password,
                    show_history: linkData.show_history,
                    is_active: new Date(linkData.expires_at) > new Date(),
                    created_at: new Date().toISOString(),
                    view_count: 0
                }
                
                this.links.unshift(newLink)
                if (newLink.is_active) {
                    this.activeCount++
                }
                
                console.log('📡 [STUB] Created share link:', newLink)
                return newLink
            } catch (error) {
                this.setError(error.message || 'Failed to create share link')
                throw error
            } finally {
                this.setLoading(false)
            }
        },

        // Mock update existing share link
        async updateShareLink(linkId, linkData) {
            this.setLoading(true)
            this.clearError()
            try {
                await this.delay(500)
                
                const index = this.links.findIndex(link => link.id === linkId)
                if (index === -1) {
                    throw new Error('Link not found')
                }
                
                const wasActive = this.links[index].is_active
                const updatedLink = {
                    ...this.links[index],
                    name: linkData.name || this.links[index].name,
                    expires_at: linkData.expires_at || this.links[index].expires_at,
                    has_password: linkData.password !== null ? !!linkData.password : this.links[index].has_password,
                    show_history: linkData.show_history !== undefined ? linkData.show_history : this.links[index].show_history,
                    is_active: new Date(linkData.expires_at || this.links[index].expires_at) > new Date()
                }
                
                this.links[index] = updatedLink
                
                // Update active count
                if (wasActive !== updatedLink.is_active) {
                    this.activeCount += updatedLink.is_active ? 1 : -1
                }
                
                console.log('📡 [STUB] Updated share link:', updatedLink)
                return updatedLink
            } catch (error) {
                this.setError(error.message || 'Failed to update share link')
                throw error
            } finally {
                this.setLoading(false)
            }
        },

        // Mock delete share link
        async deleteShareLink(linkId) {
            this.setLoading(true)
            this.clearError()
            try {
                await this.delay(400)
                
                const linkIndex = this.links.findIndex(link => link.id === linkId)
                if (linkIndex === -1) {
                    throw new Error('Link not found')
                }
                
                const wasActive = this.links[linkIndex].is_active
                this.links.splice(linkIndex, 1)
                if (wasActive) {
                    this.activeCount--
                }
                
                console.log('📡 [STUB] Deleted share link:', linkId)
            } catch (error) {
                this.setError(error.message || 'Failed to delete share link')
                throw error
            } finally {
                this.setLoading(false)
            }
        },

        // Mock public shared location info
        async fetchSharedLocationInfo(linkId) {
            this.sharedLocationLoading = true
            this.clearError()
            try {
                await this.delay(600)
                
                // First check if link exists in our store
                const link = this.links.find(l => l.id === linkId)
                if (!link) {
                    throw new Error('Link not found')
                }
                
                // Check if link is expired or inactive
                if (!link.is_active || this.isLinkExpired(link)) {
                    throw new Error('Link expired or inactive')
                }
                
                // Build response based on actual link data
                const scenario = {
                    id: link.id,
                    name: link.name,
                    expires_at: link.expires_at,
                    has_password: link.has_password,
                    show_history: link.show_history,
                    is_active: link.is_active,
                    created_at: link.created_at,
                    view_count: link.view_count,
                    shared_by: 'Demo User'
                }
                
                // Special test cases for demo purposes (if no real link found)
                if (linkId.includes('password-test')) {
                    scenario.has_password = true
                    scenario.name = 'Password Protected Demo'
                } else if (linkId.includes('public-test')) {
                    scenario.has_password = false
                    scenario.name = 'Public Demo Link'
                } else if (linkId.includes('history-test')) {
                    scenario.show_history = true
                    scenario.name = 'History Demo Link'
                }
                
                this.sharedLocationInfo = scenario
                console.log('📡 [STUB] Fetched shared location info:', scenario)
                return scenario
            } catch (error) {
                this.setError(error.message || 'Link not found or expired')
                throw error
            } finally {
                this.sharedLocationLoading = false
            }
        },

        // Mock verify shared link
        async verifySharedLink(linkId, password = null) {
            this.sharedLocationLoading = true
            this.clearError()
            try {
                await this.delay(400)
                
                // Simulate password check
                if (this.sharedLocationInfo?.has_password) {
                    if (!password || password !== 'demo123') {
                        throw new Error('Invalid password')
                    }
                }
                
                const response = {
                    access_token: `mock_token_${generateId()}`,
                    expires_in: 3600
                }
                
                this.sharedAccessToken = response.access_token
                console.log('📡 [STUB] Verified shared link, token:', response.access_token)
                return response
            } catch (error) {
                this.setError(error.message || 'Access denied')
                throw error
            } finally {
                this.sharedLocationLoading = false
            }
        },

        // Mock fetch shared location data
        async fetchSharedLocation(linkId) {
            if (!this.sharedAccessToken) {
                throw new Error('No access token available')
            }
            
            this.sharedLocationLoading = true
            this.clearError()
            try {
                await this.delay(800)
                
                let response
                
                if (this.sharedLocationInfo?.show_history) {
                    // Return location with history
                    response = {
                        current: mockCurrentLocation,
                        history: mockLocationHistory
                    }
                } else {
                    // Return current location only
                    response = mockCurrentLocation
                }
                
                this.sharedLocationData = response
                console.log('📡 [STUB] Fetched shared location data:', response)
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
            console.log('📡 [STUB] Cleared shared location data')
        },

        // Helper method to check if link is expired
        isLinkExpired(link) {
            return new Date(link.expires_at) < new Date()
        },

        // Development helper methods
        addSampleLinks() {
            const sampleLinks = [
                {
                    id: generateId(),
                    name: 'Morning Jog Route',
                    expires_at: generateDate(3),
                    has_password: false,
                    show_history: true,
                    is_active: true,
                    created_at: generateDate(-0.5),
                    view_count: 7
                },
                {
                    id: generateId(),
                    name: 'Secret Meeting Location',
                    expires_at: generateDate(1),
                    has_password: true,
                    show_history: false,
                    is_active: true,
                    created_at: generateDate(-0.2),
                    view_count: 1
                }
            ]
            
            sampleLinks.forEach(link => {
                this.links.unshift(link)
                if (link.is_active) {
                    this.activeCount++
                }
            })
            
            console.log('📡 [STUB] Added sample links')
        },

        resetMockData() {
            this.links = [...initialMockLinks]
            this.activeCount = this.links.filter(link => link.is_active).length
            this.clearError()
            this.clearSharedLocationData()
            console.log('📡 [STUB] Reset to initial mock data')
        }
    }
})
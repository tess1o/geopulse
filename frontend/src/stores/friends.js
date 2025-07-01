import { defineStore } from 'pinia'
import apiService from '../utils/apiService'

export const useFriendsStore = defineStore('friends', {
    state: () => ({
        friends: [],
        receivedInvites: [],
        sentInvitations: []
    }),

    getters: {
        // Direct access getters
        getFriends: (state) => state.friends,
        getReceivedInvites: (state) => state.receivedInvites,
        getSentInvitations: (state) => state.sentInvitations,

        // Count getters
        friendsCount: (state) => state.friends.length,
        receivedInvitesCount: (state) => state.receivedInvites.length,
        sentInvitationsCount: (state) => state.sentInvitations.length,

        // Check if data exists
        hasFriends: (state) => state.friends.length > 0,
        hasReceivedInvites: (state) => state.receivedInvites.length > 0,
        hasSentInvitations: (state) => state.sentInvitations.length > 0,
        hasPendingActivity: (state) => state.receivedInvites.length > 0 || state.sentInvitations.length > 0,

        // Find specific friends/invites
        getFriendById: (state) => (id) => {
            return state.friends.find(friend => friend.id === id)
        },

        getReceivedInviteById: (state) => (id) => {
            return state.receivedInvites.find(invite => invite.id === id)
        },

        getSentInvitationById: (state) => (id) => {
            return state.sentInvitations.find(invitation => invitation.id === id)
        },

        // Check if user is already a friend or has pending invitation
        isFriend: (state) => (userId) => {
            return state.friends.some(friend => friend.id === userId || friend.userId === userId)
        },

        hasReceivedInviteFrom: (state) => (userId) => {
            return state.receivedInvites.some(invite => invite.senderId === userId)
        },

        hasSentInvitationTo: (state) => (userId) => {
            return state.sentInvitations.some(invitation => invitation.receiverId === userId)
        },

        // Get friends with last known locations (for map display)
        getFriendsWithLocations: (state) => {
            return state.friends.filter(friend =>
                friend.lastLatitude && friend.lastLongitude
            )
        },

        // Search friends by name
        searchFriends: (state) => (searchTerm) => {
            if (!searchTerm) return state.friends
            const term = searchTerm.toLowerCase()
            return state.friends.filter(friend =>
                friend.name?.toLowerCase().includes(term) ||
                friend.fullName?.toLowerCase().includes(term) ||
                friend.email?.toLowerCase().includes(term)
            )
        },

        // Get summary of all social activity
        getSocialSummary: (state) => {
            return {
                totalFriends: state.friends.length,
                pendingReceived: state.receivedInvites.length,
                pendingSent: state.sentInvitations.length,
                friendsWithLocations: state.friends.filter(f => f.lastLatitude && f.lastLongitude).length
            }
        }
    },

    actions: {
        // Set data (replaces mutations)
        setFriends(friends) {
            this.friends = friends
        },

        setReceivedInvites(invites) {
            this.receivedInvites = invites
        },

        setSentInvitations(invitations) {
            this.sentInvitations = invitations
        },

        removeFriend(friendId) {
            this.friends = this.friends.filter(friend => friend.id !== friendId)
        },

        removeReceivedInvite(inviteId) {
            this.receivedInvites = this.receivedInvites.filter(invite => invite.id !== inviteId)
        },

        removeSentInvitation(invitationId) {
            this.sentInvitations = this.sentInvitations.filter(invitation => invitation.id !== invitationId)
        },

        // API Actions - Fetch data
        async fetchFriends() {
            try {
                const response = await apiService.get('/friends')
                this.setFriends(response.data)
                return response
            } catch (error) {
                throw error
            }
        },

        async fetchReceivedInvitations() {
            try {
                const response = await apiService.get('/friends/invitations/received')
                this.setReceivedInvites(response.data)
                return response
            } catch (error) {
                throw error
            }
        },

        async fetchSentInvitations() {
            try {
                const response = await apiService.get('/friends/invitations/sent')
                this.setSentInvitations(response)
                return response
            } catch (error) {
                throw error
            }
        },

        // API Actions - Friend management
        async sendFriendRequest(receiverEmail) {
            try {
                await apiService.post('/friends/invitations', {
                    receiverEmail
                })
                // Refresh sent invitations to show the new request
                await this.fetchSentInvitations()
            } catch (error) {
                throw error
            }
        },

        async deleteFriendship(friendId) {
            try {
                await apiService.delete(`/friends/${friendId}`, {})
                // Remove from local state immediately
                this.removeFriend(friendId)
                // Refresh to ensure data consistency
                await this.fetchFriends()
            } catch (error) {
                // Re-fetch friends to revert any optimistic updates
                await this.fetchFriends()
                throw error
            }
        },

        // API Actions - Invitation management
        async acceptInvitation(id) {
            try {
                await apiService.put(`/friends/invitations/${id}/accept`, {})
                // Remove from received invites
                this.removeReceivedInvite(id)
                // Refresh both friends and invites to reflect the change
                await Promise.all([
                    this.fetchFriends(),
                    this.fetchReceivedInvitations()
                ])
            } catch (error) {
                // Refresh invites on error
                await this.fetchReceivedInvitations()
                throw error
            }
        },

        async rejectInvitation(id) {
            try {
                await apiService.put(`/friends/invitations/${id}/reject`, {})
                // Remove from received invites
                this.removeReceivedInvite(id)
                // Refresh to ensure consistency
                await this.fetchReceivedInvitations()
            } catch (error) {
                // Refresh invites on error
                await this.fetchReceivedInvitations()
                throw error
            }
        },

        async cancelInvitation(id) {
            try {
                await apiService.put(`/friends/invitations/${id}/cancel`, {})
                // Remove from sent invitations
                this.removeSentInvitation(id)
                // Refresh to ensure consistency
                await this.fetchSentInvitations()
            } catch (error) {
                // Refresh invitations on error
                await this.fetchSentInvitations()
                throw error
            }
        },

        // Convenience methods
        async refreshAllFriendsData() {
            try {
                await Promise.all([
                    this.fetchFriends(),
                    this.fetchReceivedInvitations(),
                    this.fetchSentInvitations()
                ])
            } catch (error) {
                console.error('Error refreshing friends data:', error)
                throw error
            }
        },

        // Check relationship status with a user
        getRelationshipStatus(userId) {
            if (this.isFriend(userId)) return 'friend'
            if (this.hasReceivedInviteFrom(userId)) return 'received_invite'
            if (this.hasSentInvitationTo(userId)) return 'sent_invitation'
            return 'none'
        },

        // Batch operations
        async acceptMultipleInvitations(inviteIds) {
            try {
                const acceptPromises = inviteIds.map(id => this.acceptInvitation(id))
                await Promise.all(acceptPromises)
            } catch (error) {
                // Refresh all data to ensure consistency
                await this.refreshAllFriendsData()
                throw error
            }
        },

        async rejectMultipleInvitations(inviteIds) {
            try {
                const rejectPromises = inviteIds.map(id => this.rejectInvitation(id))
                await Promise.all(rejectPromises)
            } catch (error) {
                // Refresh all data to ensure consistency
                await this.refreshAllFriendsData()
                throw error
            }
        }
    }
})
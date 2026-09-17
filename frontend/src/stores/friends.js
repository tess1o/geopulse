import { defineStore } from 'pinia'
import apiService from '@/utils/apiService'
import { normalizeApiError } from '@/utils/apiErrorDetail'

export const useFriendsStore = defineStore('friends', {
  state: () => ({
    friends: [],
    receivedInvites: [],
    sentInvitations: [],
    loading: false,
    error: null
  }),

  getters: {
    receivedInvitesCount: (state) => state.receivedInvites.length
  },

  actions: {
    fail(error, fallback) {
      this.error = normalizeApiError(error, fallback)
      return this.error
    },

    async fetchFriends() {
      try {
        this.friends = await apiService.get('/friends') || []
        return this.friends
      } catch (error) {
        throw this.fail(error, 'Failed to load friends')
      }
    },

    async fetchReceivedInvitations() {
      try {
        this.receivedInvites = await apiService.get('/friends/invitations/received') || []
        return this.receivedInvites
      } catch (error) {
        throw this.fail(error, 'Failed to load received invitations')
      }
    },

    async fetchSentInvitations() {
      try {
        this.sentInvitations = await apiService.get('/friends/invitations/sent') || []
        return this.sentInvitations
      } catch (error) {
        throw this.fail(error, 'Failed to load sent invitations')
      }
    },

    async sendFriendRequest(receiverEmail) {
      try {
        const invitation = await apiService.post('/friends/invitations', { receiverEmail })
        this.sentInvitations.push(invitation)
        return invitation
      } catch (error) {
        throw this.fail(error, 'Failed to send invitation')
      }
    },

    async deleteFriendship(friendId) {
      try {
        await apiService.delete(`/friends/${friendId}`)
        this.friends = this.friends.filter(friend => (friend.friendId || friend.userId || friend.id) !== friendId)
      } catch (error) {
        throw this.fail(error, 'Failed to remove friend')
      }
    },

    async acceptInvitation(id) {
      try {
        const invitation = await apiService.put(`/friends/invitations/${id}/accept`, {})
        this.receivedInvites = this.receivedInvites.filter(item => item.id !== id)
        await this.fetchFriends()
        return invitation
      } catch (error) {
        throw this.fail(error, 'Failed to accept invitation')
      }
    },

    async rejectInvitation(id) {
      try {
        const invitation = await apiService.put(`/friends/invitations/${id}/reject`, {})
        this.receivedInvites = this.receivedInvites.filter(item => item.id !== id)
        return invitation
      } catch (error) {
        throw this.fail(error, 'Failed to reject invitation')
      }
    },

    async cancelInvitation(id) {
      try {
        const invitation = await apiService.put(`/friends/invitations/${id}/cancel`, {})
        this.sentInvitations = this.sentInvitations.filter(item => item.id !== id)
        return invitation
      } catch (error) {
        throw this.fail(error, 'Failed to cancel invitation')
      }
    },

    async refreshAllFriendsData() {
      this.loading = true
      this.error = null
      try {
        await Promise.all([
          this.fetchFriends(),
          this.fetchReceivedInvitations(),
          this.fetchSentInvitations()
        ])
      } finally {
        this.loading = false
      }
    },

    async searchUsersToInvite(query) {
      try {
        return await apiService.get('/friends/search-users-to-invite', { query }) || []
      } catch (error) {
        throw this.fail(error, 'Failed to search users')
      }
    },

    async getFriendPermissions(friendId) {
      try {
        return await apiService.get(`/friends/${friendId}/permissions`)
      } catch (error) {
        throw this.fail(error, 'Failed to load friend permissions')
      }
    },

    async updateFriendPermissions(friendId, shareTimeline) {
      try {
        return await apiService.put(`/friends/${friendId}/permissions`, { shareTimeline })
      } catch (error) {
        throw this.fail(error, 'Failed to update friend permissions')
      }
    },

    async updateLiveLocationPermission(friendId, shareLiveLocation) {
      try {
        return await apiService.put(`/friends/${friendId}/permissions/live`, { shareLiveLocation })
      } catch (error) {
        throw this.fail(error, 'Failed to update live location permission')
      }
    },

    async getFriendsLocationTrails(minutes = 60, endTime = null) {
      const utcEndTime = endTime instanceof Date
        ? endTime.toISOString()
        : (typeof endTime === 'string' && endTime.trim() ? endTime.trim() : null)
      try {
        return await apiService.get('/friends/location/trails', {
          minutes,
          ...(utcEndTime ? { endTime: utcEndTime } : {})
        }) || []
      } catch (error) {
        throw this.fail(error, 'Failed to load friend location trails')
      }
    },

    async acceptMultipleInvitations(inviteIds) {
      for (const id of inviteIds) await this.acceptInvitation(id)
    },

    async rejectMultipleInvitations(inviteIds) {
      for (const id of inviteIds) await this.rejectInvitation(id)
    }
  }
})

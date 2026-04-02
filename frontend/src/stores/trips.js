import { defineStore } from 'pinia'
import apiService from '@/utils/apiService'
import { useTimezone } from '@/composables/useTimezone'

const timezone = useTimezone()

const normalizeTimelineData = (timelinePayload) => {
  if (!timelinePayload || typeof timelinePayload !== 'object') {
    return []
  }

  const stays = Array.isArray(timelinePayload.stays)
    ? timelinePayload.stays.map((stay) => ({ ...stay, type: 'stay' }))
    : []

  const trips = Array.isArray(timelinePayload.trips)
    ? timelinePayload.trips.map((trip) => ({ ...trip, type: 'trip' }))
    : []

  const dataGaps = Array.isArray(timelinePayload.dataGaps)
    ? timelinePayload.dataGaps.map((gap) => ({
        ...gap,
        type: 'dataGap',
        timestamp: gap.startTime
      }))
    : []

  return [...stays, ...trips, ...dataGaps].sort(
    (a, b) => timezone.fromUtc(a.timestamp).valueOf() - timezone.fromUtc(b.timestamp).valueOf()
  )
}

const normalizePathData = (pathPayload) => {
  if (!pathPayload || typeof pathPayload !== 'object') {
    return {
      points: [],
      segments: [],
      pointCount: 0
    }
  }

  const points = Array.isArray(pathPayload.points) ? pathPayload.points : []
  const segments = Array.isArray(pathPayload.segments) && pathPayload.segments.length > 0
    ? pathPayload.segments
    : (points.length > 0 ? [points] : [])

  return {
    ...pathPayload,
    points,
    segments,
    pointCount: pathPayload.pointCount ?? points.length
  }
}

export const useTripsStore = defineStore('trips', {
  state: () => ({
    trips: [],
    currentTrip: null,
    tripSummary: null,
    tripPlanItems: [],
    workspaceTimeline: [],
    workspacePath: {
      points: [],
      segments: [],
      pointCount: 0
    },
    visitSuggestions: [],
    loading: {
      trips: false,
      trip: false,
      summary: false,
      planItems: false
    },
    error: null
  }),

  getters: {
    hasTrips: (state) => Array.isArray(state.trips) && state.trips.length > 0,
    getTripById: (state) => (tripId) => state.trips.find((trip) => Number(trip.id) === Number(tripId)) || null
  },

  actions: {
    clearWorkspaceState() {
      this.currentTrip = null
      this.tripSummary = null
      this.tripPlanItems = []
      this.workspaceTimeline = []
      this.workspacePath = {
        points: [],
        segments: [],
        pointCount: 0
      }
      this.visitSuggestions = []
    },

    async fetchTrips(status = null) {
      this.loading.trips = true
      this.error = null
      try {
        const params = {}
        if (status && status !== 'ALL') {
          params.status = status
        }
        const response = await apiService.get('/trips', params)
        this.trips = Array.isArray(response.data) ? response.data : []
        return this.trips
      } catch (error) {
        this.error = error.message || 'Failed to load trips'
        throw error
      } finally {
        this.loading.trips = false
      }
    },

    async fetchTrip(tripId) {
      this.loading.trip = true
      this.error = null
      try {
        const response = await apiService.get(`/trips/${tripId}`)
        this.currentTrip = response.data || null
        return this.currentTrip
      } catch (error) {
        this.error = error.message || 'Failed to load trip'
        throw error
      } finally {
        this.loading.trip = false
      }
    },

    async createTrip(payload) {
      this.error = null
      const response = await apiService.post('/trips', payload)
      const created = response.data
      if (created) {
        this.trips = [created, ...this.trips.filter((trip) => trip.id !== created.id)]
      }
      return created
    },

    async updateTrip(tripId, payload) {
      this.error = null
      const response = await apiService.put(`/trips/${tripId}`, payload)
      const updated = response.data
      if (updated) {
        this.trips = this.trips.map((trip) => (trip.id === updated.id ? updated : trip))
        if (this.currentTrip?.id === updated.id) {
          this.currentTrip = updated
        }
      }
      return updated
    },

    async deleteTrip(tripId, mode = 'unlink_only') {
      this.error = null
      await apiService.delete(`/trips/${tripId}?mode=${encodeURIComponent(mode)}`)
      this.trips = this.trips.filter((trip) => Number(trip.id) !== Number(tripId))
      if (this.currentTrip?.id === Number(tripId)) {
        this.clearWorkspaceState()
      }
    },

    async unlinkTripFromPeriodTag(tripId) {
      this.error = null
      const response = await apiService.post(`/trips/${tripId}/unlink`)
      const updated = response.data
      if (updated) {
        this.trips = this.trips.map((trip) => (trip.id === updated.id ? updated : trip))
        if (this.currentTrip?.id === updated.id) {
          this.currentTrip = updated
        }
      }
      return updated
    },

    async createTripFromPeriodTag(periodTagId) {
      this.error = null
      const response = await apiService.post(`/trips/from-period-tag/${periodTagId}`)
      const created = response.data
      if (created) {
        this.trips = [created, ...this.trips.filter((trip) => trip.id !== created.id)]
      }
      return created
    },

    async fetchTripCollaborators(tripId) {
      this.error = null
      const response = await apiService.get(`/trips/${tripId}/collaborators`)
      return Array.isArray(response.data) ? response.data : []
    },

    async setTripCollaborator(tripId, friendId, accessRole) {
      this.error = null
      const response = await apiService.put(`/trips/${tripId}/collaborators/${friendId}`, { accessRole })
      return response.data || null
    },

    async removeTripCollaborator(tripId, friendId) {
      this.error = null
      await apiService.delete(`/trips/${tripId}/collaborators/${friendId}`)
    },

    async fetchTripSummary(tripId) {
      this.loading.summary = true
      this.error = null
      try {
        const response = await apiService.get(`/trips/${tripId}/summary`)
        this.tripSummary = response.data || null
        return this.tripSummary
      } catch (error) {
        this.error = error.message || 'Failed to load trip summary'
        throw error
      } finally {
        this.loading.summary = false
      }
    },

    async fetchTripPlanItems(tripId) {
      this.loading.planItems = true
      this.error = null
      try {
        const response = await apiService.get(`/trips/${tripId}/plan-items`)
        this.tripPlanItems = Array.isArray(response.data) ? response.data : []
        return this.tripPlanItems
      } catch (error) {
        this.error = error.message || 'Failed to load trip plan items'
        throw error
      } finally {
        this.loading.planItems = false
      }
    },

    async createTripPlanItem(tripId, payload) {
      this.error = null
      const response = await apiService.post(`/trips/${tripId}/plan-items`, payload)
      const created = response.data
      if (created) {
        this.tripPlanItems = [...this.tripPlanItems, created].sort((a, b) => {
          if (a.orderIndex !== b.orderIndex) {
            return (a.orderIndex ?? 0) - (b.orderIndex ?? 0)
          }
          return Number(a.id) - Number(b.id)
        })
      }
      return created
    },

    async updateTripPlanItem(tripId, itemId, payload) {
      this.error = null
      const response = await apiService.put(`/trips/${tripId}/plan-items/${itemId}`, payload)
      const updated = response.data
      if (updated) {
        this.tripPlanItems = this.tripPlanItems.map((item) => (item.id === updated.id ? updated : item))
      }
      return updated
    },

    async deleteTripPlanItem(tripId, itemId) {
      this.error = null
      await apiService.delete(`/trips/${tripId}/plan-items/${itemId}`)
      this.tripPlanItems = this.tripPlanItems.filter((item) => Number(item.id) !== Number(itemId))
    },

    async applyVisitOverride(tripId, itemId, action, visitedAt = null) {
      this.error = null
      const response = await apiService.post(`/trips/${tripId}/plan-items/${itemId}/visit-override`, {
        action,
        visitedAt
      })
      const updated = response.data
      if (updated) {
        this.tripPlanItems = this.tripPlanItems.map((item) => (item.id === updated.id ? updated : item))
      }
      return updated
    },

    async fetchWorkspaceTimeline(tripId, startTime = null, endTime = null) {
      this.error = null
      try {
        const params = {}
        if (startTime) {
          params.startTime = startTime
        }
        if (endTime) {
          params.endTime = endTime
        }
        const response = await apiService.get(`/trips/${tripId}/timeline`, params)
        this.workspaceTimeline = normalizeTimelineData(response.data)
        return this.workspaceTimeline
      } catch (error) {
        this.error = error.message || 'Failed to load trip timeline'
        throw error
      }
    },

    async fetchWorkspacePath(tripId, startTime = null, endTime = null) {
      this.error = null
      try {
        const params = {}
        if (startTime) {
          params.startTime = startTime
        }
        if (endTime) {
          params.endTime = endTime
        }
        const response = await apiService.get(`/trips/${tripId}/path`, params)
        this.workspacePath = normalizePathData(response.data)
        return this.workspacePath
      } catch (error) {
        this.error = error.message || 'Failed to load trip path'
        throw error
      }
    },

    async fetchVisitSuggestions(tripId) {
      this.error = null
      try {
        const response = await apiService.get(`/trips/${tripId}/visit-suggestions`)
        this.visitSuggestions = Array.isArray(response.data) ? response.data : []
        return this.visitSuggestions
      } catch (error) {
        this.error = error.message || 'Failed to load visit suggestions'
        throw error
      }
    },

    async getPlanSuggestion(lat, lon) {
      this.error = null
      try {
        const response = await apiService.get('/trips/plan-suggestion', {
          lat,
          lon
        })
        return response.data || null
      } catch (error) {
        this.error = error.message || 'Failed to resolve plan suggestion'
        throw error
      }
    }
  }
})

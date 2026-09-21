import { defineStore } from 'pinia'
import apiService from '@/utils/apiService'
import { useTimezone } from '@/composables/useTimezone'
import { normalizeApiError } from '@/utils/apiErrorDetail'
import {
  applyStayFavoriteUpdateToTimelineItems,
  applyStayGeocodingUpdateToTimelineItems,
  applyTripMovementUpdateToTimelineItems
} from '@/utils/timelineItemPatchers'

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

const normalizeLongitude = (longitude) => ((longitude + 180) % 360 + 360) % 360 - 180

const fail = (store, error, fallback) => {
  store.error = normalizeApiError(error, fallback)
  throw store.error
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
        this.trips = Array.isArray(response) ? response : []
        return this.trips
      } catch (error) {
        this.error = normalizeApiError(error, 'Failed to load trips')
        throw this.error
      } finally {
        this.loading.trips = false
      }
    },

    async fetchTrip(tripId) {
      this.loading.trip = true
      this.error = null
      try {
        this.currentTrip = await apiService.get(`/trips/${tripId}`)
        return this.currentTrip
      } catch (error) {
        this.error = normalizeApiError(error, 'Failed to load trip')
        throw this.error
      } finally {
        this.loading.trip = false
      }
    },

    async createTrip(payload) {
      this.error = null
      try {
        const created = await apiService.post('/trips', payload)
        if (created) {
          this.trips = [created, ...this.trips.filter((trip) => trip.id !== created.id)]
        }
        return created
      } catch (error) {
        fail(this, error, 'Failed to create trip')
      }
    },

    async updateTrip(tripId, payload) {
      this.error = null
      try {
        const updated = await apiService.put(`/trips/${tripId}`, payload)
        if (updated) {
          this.trips = this.trips.map((trip) => (trip.id === updated.id ? updated : trip))
          if (this.currentTrip?.id === updated.id) this.currentTrip = updated
        }
        return updated
      } catch (error) {
        fail(this, error, 'Failed to update trip')
      }
    },

    async deleteTrip(tripId, mode = 'unlink_only') {
      this.error = null
      try {
        await apiService.delete(`/trips/${tripId}?mode=${encodeURIComponent(mode)}`)
        this.trips = this.trips.filter((trip) => Number(trip.id) !== Number(tripId))
        if (this.currentTrip?.id === Number(tripId)) this.clearWorkspaceState()
      } catch (error) {
        fail(this, error, 'Failed to delete trip')
      }
    },

    async unlinkTripFromTimelineLabel(tripId) {
      this.error = null
      try {
        const updated = await apiService.delete(`/trips/${tripId}/timeline-label`)
        if (updated) {
          this.trips = this.trips.map((trip) => (trip.id === updated.id ? updated : trip))
          if (this.currentTrip?.id === updated.id) this.currentTrip = updated
        }
        return updated
      } catch (error) {
        fail(this, error, 'Failed to unlink trip')
      }
    },

    async createTripFromTimelineLabel(timelineLabelId) {
      this.error = null
      try {
        const created = await apiService.post(`/trips/from-timeline-label/${timelineLabelId}`)
        if (created) this.trips = [created, ...this.trips.filter((trip) => trip.id !== created.id)]
        return created
      } catch (error) {
        fail(this, error, 'Failed to create trip from timeline label')
      }
    },

    async fetchTripCollaborators(tripId) {
      this.error = null
      try {
        const response = await apiService.get(`/trips/${tripId}/collaborators`)
        return Array.isArray(response) ? response : []
      } catch (error) {
        fail(this, error, 'Failed to load trip collaborators')
      }
    },

    async setTripCollaborator(tripId, friendId, accessRole) {
      this.error = null
      try {
        return await apiService.put(`/trips/${tripId}/collaborators/${friendId}`, { accessRole })
      } catch (error) {
        fail(this, error, 'Failed to update trip collaborator')
      }
    },

    async removeTripCollaborator(tripId, friendId) {
      this.error = null
      try {
        await apiService.delete(`/trips/${tripId}/collaborators/${friendId}`)
      } catch (error) {
        fail(this, error, 'Failed to remove trip collaborator')
      }
    },

    async fetchTripSummary(tripId) {
      this.loading.summary = true
      this.error = null
      try {
        this.tripSummary = await apiService.get(`/trips/${tripId}/summary`)
        return this.tripSummary
      } catch (error) {
        this.error = normalizeApiError(error, 'Failed to load trip summary')
        throw this.error
      } finally {
        this.loading.summary = false
      }
    },

    async fetchTripPlanItems(tripId) {
      this.loading.planItems = true
      this.error = null
      try {
        const response = await apiService.get(`/trips/${tripId}/plan-items`)
        this.tripPlanItems = Array.isArray(response) ? response : []
        return this.tripPlanItems
      } catch (error) {
        this.error = normalizeApiError(error, 'Failed to load trip plan items')
        throw this.error
      } finally {
        this.loading.planItems = false
      }
    },

    async createTripPlanItem(tripId, payload) {
      this.error = null
      try {
        const created = await apiService.post(`/trips/${tripId}/plan-items`, payload)
        if (created) {
          this.tripPlanItems = [...this.tripPlanItems, created].sort((a, b) => {
            if (a.orderIndex !== b.orderIndex) return (a.orderIndex ?? 0) - (b.orderIndex ?? 0)
            return Number(a.id) - Number(b.id)
          })
        }
        return created
      } catch (error) {
        fail(this, error, 'Failed to create trip plan item')
      }
    },

    async updateTripPlanItem(tripId, itemId, payload) {
      this.error = null
      try {
        const updated = await apiService.put(`/trips/${tripId}/plan-items/${itemId}`, payload)
        if (updated) this.tripPlanItems = this.tripPlanItems.map((item) => (item.id === updated.id ? updated : item))
        return updated
      } catch (error) {
        fail(this, error, 'Failed to update trip plan item')
      }
    },

    async deleteTripPlanItem(tripId, itemId) {
      this.error = null
      try {
        await apiService.delete(`/trips/${tripId}/plan-items/${itemId}`)
        this.tripPlanItems = this.tripPlanItems.filter((item) => Number(item.id) !== Number(itemId))
      } catch (error) {
        fail(this, error, 'Failed to delete trip plan item')
      }
    },

    async applyVisitOverride(tripId, itemId, action, visitedAt = null) {
      this.error = null
      try {
        const updated = await apiService.put(`/trips/${tripId}/plan-items/${itemId}/visit-override`, {
          action,
          visitedAt
        })
        if (updated) this.tripPlanItems = this.tripPlanItems.map((item) => (item.id === updated.id ? updated : item))
        return updated
      } catch (error) {
        fail(this, error, 'Failed to update trip visit')
      }
    },

    async previewReconstruction(payload) {
      this.error = null
      try {
        return await apiService.post('/trip-planning/reconstructions/preview', payload)
      } catch (error) {
        this.error = normalizeApiError(error, 'Failed to preview reconstruction')
        throw this.error
      }
    },

    async commitReconstruction(payload) {
      this.error = null
      try {
        return await apiService.post('/trip-planning/reconstructions/commit', payload)
      } catch (error) {
        this.error = normalizeApiError(error, 'Failed to commit reconstruction')
        throw this.error
      }
    },

    async fetchWorkspaceTimeline(tripId, startTime = null, endTime = null) {
      this.error = null
      try {
        const params = {}
        if (startTime) {
          params.from = startTime
        }
        if (endTime) {
          params.to = endTime
        }
        const response = await apiService.get(`/trips/${tripId}/timeline`, params)
        this.workspaceTimeline = normalizeTimelineData(response)
        return this.workspaceTimeline
      } catch (error) {
        this.error = normalizeApiError(error, 'Failed to load trip timeline')
        throw this.error
      }
    },

    async fetchWorkspacePath(tripId, startTime = null, endTime = null) {
      this.error = null
      try {
        const params = {}
        if (startTime) {
          params.from = startTime
        }
        if (endTime) {
          params.to = endTime
        }
        const response = await apiService.get(`/trips/${tripId}/path`, params)
        this.workspacePath = normalizePathData(response)
        return this.workspacePath
      } catch (error) {
        this.error = normalizeApiError(error, 'Failed to load trip path')
        throw this.error
      }
    },

    applyWorkspaceTripMovementUpdate(updatedTrip) {
      this.workspaceTimeline = applyTripMovementUpdateToTimelineItems(this.workspaceTimeline, updatedTrip)
    },

    applyWorkspaceStayFavoriteUpdate(updatedFavorite) {
      this.workspaceTimeline = applyStayFavoriteUpdateToTimelineItems(this.workspaceTimeline, updatedFavorite)
    },

    applyWorkspaceStayGeocodingUpdate(oldGeocodingId, updatedGeocoding) {
      this.workspaceTimeline = applyStayGeocodingUpdateToTimelineItems(this.workspaceTimeline, oldGeocodingId, updatedGeocoding)
    },

    async fetchVisitSuggestions(tripId) {
      this.error = null
      try {
        const response = await apiService.get(`/trips/${tripId}/visit-suggestions`)
        this.visitSuggestions = Array.isArray(response) ? response : []
        return this.visitSuggestions
      } catch (error) {
        this.error = normalizeApiError(error, 'Failed to load visit suggestions')
        throw this.error
      }
    },

    async getPlanSuggestion(lat, lon) {
      this.error = null
      try {
        const normalizedLon = Number.isFinite(lon) ? normalizeLongitude(lon) : lon
        return await apiService.get('/trip-planning/suggestions', {
          latitude: lat,
          longitude: normalizedLon
        })
      } catch (error) {
        this.error = normalizeApiError(error, 'Failed to resolve plan suggestion')
        throw this.error
      }
    },

    async searchPlanLocations(query, options = {}) {
      this.error = null
      try {
        const params = {
          q: query
        }

        if (Number.isFinite(options?.lat) && Number.isFinite(options?.lon)) {
          params.latitude = options.lat
          params.longitude = normalizeLongitude(options.lon)
        }

        if (Number.isFinite(options?.limit)) {
          params.limit = options.limit
        }

        const response = await apiService.get('/trip-planning/searches', params)
        return Array.isArray(response) ? response : []
      } catch (error) {
        this.error = normalizeApiError(error, 'Failed to search locations')
        throw this.error
      }
    }
  }
})

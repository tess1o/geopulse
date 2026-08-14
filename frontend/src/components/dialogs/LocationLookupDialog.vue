<template>
  <Dialog
    v-model:visible="internalVisible"
    header="Visits near this point"
    :modal="true"
    class="gp-dialog-md location-lookup-dialog"
    :dismissable-mask="!loading"
    @hide="$emit('close')"
  >
    <div class="location-lookup-content">
      <div v-if="point" class="lookup-point-summary">
        <i class="pi pi-map-marker"></i>
        <span>{{ point.lat.toFixed(6) }}, {{ point.lng.toFixed(6) }}</span>
        <span v-if="result" class="lookup-radius">within {{ result.matchRadiusMeters }} m</span>
      </div>

      <div v-if="loading" class="lookup-loading">
        <ProgressSpinner />
        <span>Checking your recorded stays…</span>
      </div>

      <Message v-else-if="error" severity="error" :closable="false">
        {{ error }}
        <Button label="Try again" text size="small" @click="$emit('retry')" />
      </Message>

      <template v-else-if="result">
        <Message v-if="result.visitMatches?.length" severity="success" :closable="false">
          You have recorded visits at this location.
        </Message>

        <section v-if="result.visitMatches?.length" class="lookup-section">
          <div
            v-for="match in result.visitMatches"
            :key="matchKey(match)"
            class="lookup-match"
          >
            <div class="lookup-match-header">
              <div>
                <h3>{{ match.name || 'Recorded stay' }}</h3>
                <p>{{ match.matchReason }}<span v-if="match.nearestDistanceMeters != null"> · {{ formatDistance(match.nearestDistanceMeters) }}</span></p>
              </div>
              <Button
                v-if="placeDetailsRoute(match)"
                label="Place Details"
                icon="pi pi-external-link"
                text
                size="small"
                @click="openPlaceDetails(match)"
              />
            </div>

            <div class="lookup-match-stats">
              <span>{{ match.visitCount }} visit{{ match.visitCount === 1 ? '' : 's' }}</span>
              <span v-if="match.firstVisit">First: {{ formatDate(match.firstVisit) }}</span>
              <span v-if="match.lastVisit">Last: {{ formatDate(match.lastVisit) }}</span>
            </div>

            <div v-if="match.visits?.length" class="lookup-visits">
              <div v-for="visit in match.visits" :key="visit.id" class="lookup-visit-row">
                <span>{{ formatDateTime(visit.timestamp) }}</span>
                <span class="lookup-visit-actions">
                  <span>{{ formatDuration(visit.stayDuration) }}</span>
                  <Button
                    icon="pi pi-calendar"
                    text
                    size="small"
                    aria-label="Open visit day in timeline"
                    title="Open visit day in timeline"
                    v-tooltip.top="'Open visit day in timeline'"
                    @click="openVisitInTimeline(visit)"
                  />
                </span>
              </div>
            </div>
          </div>
        </section>

        <section v-if="result.favoriteMatches?.length" class="lookup-section lookup-favorites">
          <h3>Saved places at this point</h3>
          <div v-for="favorite in result.favoriteMatches" :key="favorite.id" class="lookup-favorite-row">
            <span><i class="pi pi-star"></i> {{ favorite.name || 'Unnamed favorite' }}</span>
            <span>{{ favorite.relation }}</span>
          </div>
        </section>

        <section v-if="!result.visitMatches?.length" class="lookup-section">
          <Message severity="info" :closable="false">
            No recorded visit was found within {{ result.matchRadiusMeters }} m of this point.
          </Message>

          <div v-if="result.nearestStays?.length" class="lookup-fallback">
            <h3>Closest recorded stays</h3>
            <div v-for="stay in result.nearestStays" :key="stay.id" class="lookup-visit-row">
              <span>{{ formatDateTime(stay.timestamp) }} · {{ stay.locationName || 'Unknown location' }}</span>
              <span class="lookup-fallback-actions">
                <span>{{ formatDistance(stay.distanceMeters) }}</span>
                <Button
                  v-if="placeDetailsRoute(stay)"
                  label="Place Details"
                  icon="pi pi-external-link"
                  text
                  size="small"
                  @click="openPlaceDetails(stay)"
                />
                <Button
                  icon="pi pi-calendar"
                  text
                  size="small"
                  aria-label="Open visit day in timeline"
                  title="Open visit day in timeline"
                  v-tooltip.top="'Open visit day in timeline'"
                  @click="openVisitInTimeline(stay)"
                />
              </span>
            </div>
          </div>
          <p v-else class="lookup-empty">No recorded stays were found.</p>
        </section>
      </template>
    </div>

    <template #footer>
      <Button label="Close" outlined @click="internalVisible = false" />
    </template>
  </Dialog>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import Message from 'primevue/message'
import ProgressSpinner from 'primevue/progressspinner'
import { useTimezone } from '@/composables/useTimezone'
import { formatDistance, formatDuration } from '@/utils/calculationsHelpers'
import { getStayPlaceDetailsRoute } from '@/maps/shared/timelinePlaceRoute'

const props = defineProps({
  visible: { type: Boolean, default: false },
  point: { type: Object, default: null },
  result: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' }
})

const emit = defineEmits(['close', 'retry'])
const router = useRouter()
const timezone = useTimezone()

const internalVisible = computed({
  get: () => props.visible,
  set: (value) => {
    if (!value) emit('close')
  }
})

const formatDate = (value) => timezone.formatDateDisplay(value)
const formatDateTime = (value) => `${timezone.formatDateDisplay(value)} ${timezone.formatTime(value)}`
const matchKey = (match) => `${match.sourceType}:${match.favoriteId || match.geocodingId || match.name || 'unknown'}`

const placeDetailsRoute = (match) => getStayPlaceDetailsRoute({
  type: 'stay',
  favoriteId: match?.favoriteId,
  geocodingId: match?.geocodingId
})

const openPlaceDetails = (match) => {
  const route = placeDetailsRoute(match)
  if (!route) return
  emit('close')
  router.push(route)
}

const openVisitInTimeline = (visit) => {
  if (!visit?.timestamp) return

  const visitDay = timezone.formatUrlDate(visit.timestamp)
  const query = {
    start: visitDay,
    end: visitDay,
    focusTime: visit.timestamp
  }

  if (visit.id !== undefined && visit.id !== null) {
    query.focusStay = String(visit.id)
  }

  const resolvedRoute = router.resolve({
    path: '/app/timeline',
    query
  })
  const newWindow = window.open(resolvedRoute.href, '_blank')
  if (!newWindow) {
    emit('close')
    router.push(resolvedRoute.fullPath)
    return
  }
  newWindow.opener = null
}
</script>

<style scoped>
.location-lookup-content {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.lookup-point-summary,
.lookup-match-stats,
.lookup-favorite-row,
.lookup-visit-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.lookup-point-summary {
  color: var(--gp-text-secondary, #64748b);
  font-size: 0.9rem;
}

.lookup-point-summary i {
  color: var(--gp-primary, #3b82f6);
}

.lookup-radius {
  margin-left: auto;
}

.lookup-loading {
  min-height: 8rem;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 0.75rem;
}

.lookup-section {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.lookup-match {
  border: 1px solid var(--gp-border-color, #e2e8f0);
  border-radius: var(--gp-radius-medium, 0.5rem);
  padding: 0.9rem;
}

.lookup-match-header {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
}

.lookup-match h3,
.lookup-favorites h3,
.lookup-fallback h3 {
  margin: 0;
  font-size: 1rem;
}

.lookup-match p {
  margin: 0.25rem 0 0;
  color: var(--gp-text-secondary, #64748b);
  font-size: 0.85rem;
}

.lookup-match-stats,
.lookup-favorite-row,
.lookup-visit-row {
  font-size: 0.85rem;
}

.lookup-match-stats {
  margin-top: 0.75rem;
  color: var(--gp-text-secondary, #64748b);
  flex-wrap: wrap;
  justify-content: flex-start;
}

.lookup-visits,
.lookup-fallback {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  margin-top: 0.75rem;
}

.lookup-visit-row {
  border-top: 1px solid var(--gp-border-color, #e2e8f0);
  padding-top: 0.45rem;
}

.lookup-visit-actions {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  white-space: nowrap;
}

.lookup-fallback-actions {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  white-space: nowrap;
}

.lookup-favorites {
  border-top: 1px solid var(--gp-border-color, #e2e8f0);
  padding-top: 0.9rem;
}

.lookup-favorite-row span:last-child {
  color: var(--gp-text-secondary, #64748b);
  font-size: 0.8rem;
}

.lookup-empty {
  margin: 0;
  color: var(--gp-text-secondary, #64748b);
}
</style>

<template>
  <div class="shared-timeline-page">
    <!-- Header -->
    <div class="shared-header">
      <div class="header-content">
        <div class="header-left">
          <h1 class="brand">GeoPulse</h1>
          <div v-if="authenticated && shareInfo && timelineData" class="header-info">
            <span class="timeline-name">{{ shareInfo.name }}</span>
            <Tag :value="getStatusLabel()" :severity="getStatusSeverity()" class="status-tag" />
          </div>
        </div>

        <!-- Date Filter Controls (shown when timeline is loaded) -->
        <div v-if="authenticated && shareInfo && timelineData" class="header-controls">
          <span class="header-meta">
            <i class="pi pi-user"></i>
            {{ shareInfo.shared_by }}
          </span>
          <span class="header-separator">•</span>
          <span class="header-meta">
            <i class="pi pi-clock"></i>
            Expires {{ formatExpiration() }}
          </span>
          <span class="header-separator">•</span>
          <DatePicker
              v-model="dateRange"
              selectionMode="range"
              class="header-datepicker"
              :manualInput="false"
              iconDisplay="input"
              showIcon
              :showOnFocus="true"
              dateFormat="mm/dd/yy"
              placeholder="Filter date range"
              :minDate="shareStartDate"
              :maxDate="shareEndDate"
          />
          <Button icon="pi pi-times" text rounded @click="resetDateFilter"
                 v-if="filterStartDate || filterEndDate"
                 v-tooltip.bottom="'Clear filter'" />
          <Button v-if="shareInfo.timeline_status === 'active'"
                 icon="pi pi-refresh" text rounded @click="refreshData"
                 v-tooltip.bottom="'Refresh'" />
        </div>

        <DarkModeSwitcher />
      </div>
    </div>

    <!-- Main Content -->
    <div class="shared-content">
      <!-- Loading State -->
      <div v-if="loading" class="state-container">
        <ProgressSpinner />
        <p>Loading shared timeline...</p>
      </div>

      <!-- Password Required State -->
      <Card v-else-if="needsPassword && !authenticated" class="password-card">
        <template #header>
          <div class="card-header">
            <i class="pi pi-lock" style="font-size: 2rem; color: var(--primary-color)"></i>
            <h2>Password Required</h2>
          </div>
        </template>
        <template #content>
          <form @submit.prevent="handlePasswordSubmit" class="password-form">
            <div class="field">
              <label for="password">Enter password to view this timeline</label>
              <Password id="password" v-model="password" :feedback="false"
                       placeholder="Password" toggleMask class="w-full"
                       :class="{'p-invalid': passwordError}" />
              <small v-if="passwordError" class="p-error">{{ passwordError }}</small>
            </div>
            <Button type="submit" label="Access Timeline" icon="pi pi-unlock"
                   :loading="verifying" class="w-full" />
          </form>
        </template>
      </Card>

      <!-- Upcoming Timeline State -->
      <Card v-else-if="shareInfo && shareInfo.timeline_status === 'upcoming'" class="info-card">
        <template #header>
          <div class="card-header">
            <i class="pi pi-calendar" style="font-size: 2rem; color: var(--blue-500)"></i>
            <h2>{{ shareInfo.name }}</h2>
          </div>
        </template>
        <template #content>
          <div class="info-content">
            <p class="info-message">This trip hasn't started yet.</p>
            <div class="info-details">
              <div class="detail-item">
                <i class="pi pi-calendar-plus"></i>
                <span>Trip begins: {{ formatDate(shareInfo.start_date) }}</span>
              </div>
              <div class="detail-item">
                <i class="pi pi-user"></i>
                <span>Shared by: {{ shareInfo.shared_by }}</span>
              </div>
            </div>
            <p class="info-hint">Check back after the trip starts to see the timeline.</p>
          </div>
        </template>
      </Card>

      <!-- Error State -->
      <Card v-else-if="error" class="error-card">
        <template #header>
          <div class="card-header">
            <i class="pi pi-exclamation-circle" style="font-size: 2rem; color: var(--red-500)"></i>
            <h2>Unable to Load Timeline</h2>
          </div>
        </template>
        <template #content>
          <p>{{ error }}</p>
        </template>
      </Card>

      <!-- Timeline View -->
      <div v-else-if="authenticated && shareInfo && timelineData" class="timeline-view">
        <!-- Timeline Display -->
        <div class="timeline-container">
          <div class="timeline-map">
            <TimelineMap
                ref="mapRef"
                :pathData="pathData"
                :timelineData="timelineData"
                :currentLocation="currentLocation"
                :showCurrentLocation="shareInfo.show_current_location && shareInfo.timeline_status === 'active'"
                @timeline-marker-click="handleTimelineItemClick"
            />
          </div>
          <div class="timeline-sidebar">
            <TimelineContainer
                ref="timelineRef"
                :timeline-data="timelineData"
                :is-public-view="true"
                @timeline-item-click="handleTimelineItemClick"
            />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useShareLinksStore } from '@/stores/shareLinks'
import { useDateRangeStore } from '@/stores/dateRange'
import { useHighlightStore } from '@/stores/highlight'
import { useTimezone } from '@/composables/useTimezone'
import Card from 'primevue/card'
import Button from 'primevue/button'
import Password from 'primevue/password'
import ProgressSpinner from 'primevue/progressspinner'
import Tag from 'primevue/tag'
import DatePicker from 'primevue/datepicker'
import DarkModeSwitcher from '@/components/DarkModeSwitcher.vue'
import TimelineMap from '@/components/maps/TimelineMap.vue'
import TimelineContainer from '@/components/timeline/TimelineContainer.vue'

const route = useRoute()
const shareLinksStore = useShareLinksStore()
const dateRangeStore = useDateRangeStore()
const highlightStore = useHighlightStore()
const timezone = useTimezone()

const linkId = route.params.linkId
const loading = ref(true)
const error = ref(null)
const needsPassword = ref(false)
const authenticated = ref(false)
const password = ref('')
const passwordError = ref(null)
const verifying = ref(false)

const shareInfo = ref(null)
const timelineData = ref(null)
const pathData = ref(null)
const currentLocation = ref(null)

// Component refs
const mapRef = ref(null)
const timelineRef = ref(null)

// Date filter state
const filterStartDate = ref(null)
const filterEndDate = ref(null)

// Computed properties for share date range
const shareStartDate = computed(() => {
  return shareInfo.value?.start_date ? new Date(shareInfo.value.start_date) : null
})

const shareEndDate = computed(() => {
  return shareInfo.value?.end_date ? new Date(shareInfo.value.end_date) : null
})

// Two-way binding for DatePicker range mode
const dateRange = computed({
  get() {
    // Show full share date range as default, or selected filter dates
    if (filterStartDate.value && filterEndDate.value) {
      return [filterStartDate.value, filterEndDate.value]
    }
    // Default to full share date range if no filter is set
    if (shareStartDate.value && shareEndDate.value) {
      return [shareStartDate.value, shareEndDate.value]
    }
    return null
  },
  set(value) {
    if (value && Array.isArray(value) && value.length === 2) {
      // Only update when both dates are selected (complete range)
      if (value[0] && value[1]) {
        filterStartDate.value = value[0]
        filterEndDate.value = value[1]
      }
    } else if (!value || (Array.isArray(value) && value.length === 0)) {
      // Clear filter
      filterStartDate.value = null
      filterEndDate.value = null
    }
    // Ignore single date selections (incomplete range)
  }
})

// Watch for date range changes and refetch from backend
watch([filterStartDate, filterEndDate], async ([newStart, newEnd], [oldStart, oldEnd]) => {
  // Sync with date range store for Immich
  if (newStart && newEnd) {
    // Use the same date range conversion as the main timeline page
    const { start: startUtc, end: endUtc } = timezone.createDateRangeFromPicker(newStart, newEnd)
    dateRangeStore.setDateRange([startUtc, endUtc])
  } else if (shareStartDate.value && shareEndDate.value) {
    // Use full share range if no filter
    const { start: startUtc, end: endUtc } = timezone.createDateRangeFromPicker(
      shareStartDate.value,
      shareEndDate.value
    )
    dateRangeStore.setDateRange([startUtc, endUtc])
  }

  // Refetch timeline data if already loaded and dates changed
  if (!timelineData.value) return  // Only refetch if timeline is already loaded
  if (newStart === oldStart && newEnd === oldEnd) return  // Skip if dates haven't changed

  // Refetch timeline data with new date range
  await loadTimelineData()
})

// Initialize date range store when timeline data loads
watch([shareInfo, timelineData], ([info, data]) => {
  if (info && data && !filterStartDate.value && !filterEndDate.value) {
    // Set initial date range to full share range using proper timezone conversion
    const start = new Date(info.start_date)
    const end = new Date(info.end_date)
    const { start: startUtc, end: endUtc } = timezone.createDateRangeFromPicker(start, end)
    dateRangeStore.setDateRange([startUtc, endUtc])
  }
})

onMounted(async () => {
  await loadShareInfo()
})

async function loadShareInfo() {
  loading.value = true
  error.value = null

  try {
    // Fetch share info (public, no auth required)
    shareInfo.value = await shareLinksStore.fetchSharedLocationInfo(linkId)

    // Check if password is required
    if (shareInfo.value.has_password) {
      // Check for stored token
      const storedToken = getStoredToken()
      if (storedToken && isTokenValid(storedToken)) {
        shareLinksStore.sharedAccessToken = storedToken.access_token
        authenticated.value = true
        await loadTimelineData()
      } else {
        needsPassword.value = true
      }
    } else {
      // No password required, verify without password
      await handlePasswordSubmit()
    }
  } catch (err) {
    console.error('Failed to load share info:', err)
    error.value = err.userMessage || err.message || 'Link not found or expired'
  } finally {
    loading.value = false
  }
}

async function handlePasswordSubmit() {
  passwordError.value = null
  verifying.value = true

  try {
    const response = await shareLinksStore.verifySharedLink(linkId, password.value || null)

    // Store token
    storeToken(response)

    authenticated.value = true
    await loadTimelineData()
  } catch (err) {
    console.error('Password verification failed:', err)
    passwordError.value = err.userMessage || 'Invalid password'
  } finally {
    verifying.value = false
  }
}

async function loadTimelineData() {
  if (shareInfo.value.timeline_status === 'upcoming') {
    // Don't load timeline for upcoming trips
    return
  }

  try {
    // Determine date range to fetch
    let startTime = null
    let endTime = null

    if (filterStartDate.value && filterEndDate.value) {
      // User has applied a date filter - use it
      const { start, end } = timezone.createDateRangeFromPicker(
        filterStartDate.value,
        filterEndDate.value
      )
      startTime = start  // ISO-8601 UTC string
      endTime = end      // ISO-8601 UTC string
    }
    // If no filter, pass null - backend will use full share range

    // Fetch timeline and path data in parallel with date parameters
    const [timeline, path] = await Promise.all([
      shareLinksStore.fetchSharedTimeline(linkId, startTime, endTime),
      shareLinksStore.fetchSharedPath(linkId, startTime, endTime)
    ])

    timelineData.value = timeline
    pathData.value = path

    // Load current location if available
    if (shareInfo.value.show_current_location &&
        shareInfo.value.timeline_status === 'active') {
      currentLocation.value = await shareLinksStore.fetchSharedCurrentLocation(linkId)
    }
  } catch (err) {
    console.error('Failed to load timeline data:', err)
    error.value = 'Failed to load timeline data'
  }
}

async function refreshData() {
  await loadTimelineData()
}

function getStoredToken() {
  try {
    const stored = localStorage.getItem(`shareLink_${linkId}`)
    return stored ? JSON.parse(stored) : null
  } catch {
    return null
  }
}

function isTokenValid(tokenData) {
  if (!tokenData || !tokenData.expires_at) return false
  const timeLeft = tokenData.expires_at - Date.now()
  return timeLeft > 5 * 60 * 1000 // 5 minute buffer
}

function storeToken(response) {
  const tokenData = {
    access_token: response.access_token,
    expires_at: Date.now() + (response.expires_in * 1000)
  }
  localStorage.setItem(`shareLink_${linkId}`, JSON.stringify(tokenData))
}

function formatDate(dateStr) {
  if (!dateStr) return 'N/A'
  return timezone.fromUtc(dateStr).format('MMM D, YYYY')
}

function formatExpiration() {
  if (!shareInfo.value?.expires_at) return 'Never'
  return formatDate(shareInfo.value.expires_at)
}

function getStatusLabel() {
  const status = shareInfo.value?.timeline_status
  if (status === 'upcoming') return 'Upcoming'
  if (status === 'active') return 'Active'
  if (status === 'completed') return 'Completed'
  return 'Unknown'
}

function getStatusSeverity() {
  const status = shareInfo.value?.timeline_status
  if (status === 'upcoming') return 'info'
  if (status === 'active') return 'success'
  if (status === 'completed') return 'secondary'
  return 'secondary'
}

async function resetDateFilter() {
  filterStartDate.value = null
  filterEndDate.value = null

  // Refetch with full share range (no date params)
  await loadTimelineData()
}

function handleTimelineItemClick(item) {
  // Check if this item is already highlighted
  if (highlightStore.isItemHighlighted(item)) {
    highlightStore.clearAllHighlights()
    return
  }

  // Highlight the clicked item
  highlightStore.setHighlightedItem(item)
}
</script>

<style scoped>
.shared-timeline-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: var(--gp-surface-ground);
}

/* Dark mode background */
.p-dark .shared-timeline-page {
  background-color: var(--gp-surface-dark);
}

.shared-header {
  background-color: var(--gp-surface-white);
  border-bottom: 1px solid var(--gp-border-light);
  padding: 1rem 2rem;
  position: sticky;
  top: 0;
  z-index: 100;
}

.p-dark .shared-header {
  background-color: var(--gp-surface-card);
  border-bottom: 1px solid var(--gp-border-dark);
}

.header-content {
  max-width: 100%;
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 2rem;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  flex-shrink: 0;
}

.brand {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--gp-primary);
  letter-spacing: -0.025em;
  margin: 0;
  flex-shrink: 0;
}

.header-info {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.timeline-name {
  font-size: 1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.status-tag {
  flex-shrink: 0;
}

.header-controls {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex: 1;
  justify-content: flex-end;
}

.header-meta {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  white-space: nowrap;
}

.header-meta i {
  font-size: 0.85rem;
}

.header-separator {
  color: var(--gp-text-secondary);
  opacity: 0.5;
  font-weight: 500;
}

.header-datepicker {
  width: 240px;
  flex-shrink: 0;
}

.shared-content {
  flex: 1;
  padding: 1rem 2rem;
  max-width: 100%;
  width: 100%;
  margin: 0;
}

.state-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  padding: 4rem 2rem;
}

.password-card,
.info-card,
.error-card {
  max-width: 500px;
  margin: 2rem auto;
}

.card-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  padding: 1.5rem;
  text-align: center;
}

.card-header h2 {
  margin: 0;
  font-size: 1.5rem;
}

.password-form {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.field label {
  font-weight: 600;
}

.w-full {
  width: 100%;
}

.info-content {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  padding: 1rem;
}

.info-message {
  font-size: 1.1rem;
  text-align: center;
  margin: 0;
}

.info-details {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.detail-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem;
  background-color: var(--surface-100);
  border-radius: var(--border-radius);
}

.detail-item i {
  color: var(--primary-color);
}

.info-hint {
  text-align: center;
  color: var(--text-color-secondary);
  font-size: 0.95rem;
  margin: 0;
}

.timeline-container {
  display: grid;
  grid-template-columns: 1fr 400px;
  gap: 1.5rem;
  height: calc(100vh - 140px);
  min-height: 600px;
}

.timeline-map,
.timeline-sidebar {
  background-color: var(--gp-surface-white);
  border-radius: var(--gp-radius-medium);
  overflow: hidden;
  height: 100%;
}

.p-dark .timeline-map,
.p-dark .timeline-sidebar {
  background-color: var(--gp-surface-card);
}

.timeline-map {
  position: relative;
  min-height: 500px;
}

.timeline-sidebar {
  /* Match the right-pane from TimelinePage.vue */
  flex: 1;
  overflow-y: auto !important;
  overflow-x: hidden;
  height: 100%;
  min-height: 350px;
  border-radius: var(--gp-radius-medium);
  width: 400px;
  min-width: 400px;
  max-width: 400px;
}

/* Ensure timeline container is vertical */
.timeline-sidebar :deep(.timeline-container) {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
}

/* Mobile Responsive */
@media (max-width: 1024px) {
  .header-content {
    flex-wrap: wrap;
    gap: 1rem;
  }

  .header-left {
    flex: 1 1 100%;
    order: 1;
  }

  .header-controls {
    flex: 1 1 100%;
    order: 2;
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .header-meta {
    font-size: 0.85rem;
  }

  .header-datepicker {
    width: 200px;
  }
}

@media (max-width: 768px) {
  .shared-header {
    padding: 1rem;
  }

  .header-left {
    gap: 1rem;
  }

  .timeline-name {
    font-size: 0.9rem;
  }

  .header-meta {
    font-size: 0.8rem;
  }

  .header-separator {
    display: none;
  }

  .header-datepicker {
    width: 180px;
  }

  .shared-content {
    padding: 0.75rem;
  }

  .timeline-container {
    grid-template-columns: 1fr;
    grid-template-rows: 450px auto;
    height: auto;
    min-height: auto;
    max-height: none;
  }

  .timeline-map {
    height: 450px;
    min-height: 450px;
  }

  .timeline-sidebar {
    height: 600px;
    max-height: 600px;
  }
}

@media (max-width: 480px) {
  .brand {
    font-size: 1.25rem;
  }

  .header-info {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }

  .timeline-name {
    font-size: 0.85rem;
  }

  .header-meta {
    font-size: 0.75rem;
  }

  .header-datepicker {
    width: 160px;
  }

  .header-controls {
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .shared-content {
    padding: 0.5rem;
  }

  .timeline-container {
    grid-template-rows: 300px 1fr;
  }
}
</style>

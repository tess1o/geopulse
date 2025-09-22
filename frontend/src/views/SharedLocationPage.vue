<template>
  <div class="shared-location-page">
    <!-- Header with Theme Toggle -->
    <div class="shared-header">
      <div class="header-content">
        <div class="logo-section" @click="navigateToHome">
          <img src="/geopulse-logo.svg" alt="GeoPulse" class="logo"/>
          <h1 class="app-title">GeoPulse</h1>
        </div>
        <div class="header-right">
          <DarkModeSwitcher class="large-theme-toggle"/>
        </div>
      </div>
    </div>

    <!-- Main Content -->
    <div class="shared-content">
      <!-- Loading State -->
      <div v-if="loading" class="loading-state">
        <ProgressSpinner/>
        <p>Loading shared location...</p>
      </div>

      <!-- Error State -->
      <div v-else-if="error" class="error-state">
        <Card class="error-card">
          <template #content>
            <div class="error-content">
              <i class="pi pi-exclamation-triangle error-icon"></i>
              <div class="error-message">
                <h3>Error Loading Location</h3>
                <p>{{ error }}</p>
                <Button
                    label="Try Again"
                    @click="initializeSharedView"
                    class="retry-btn"
                />
              </div>
            </div>
          </template>
        </Card>
      </div>

      <!-- Password Required -->
      <div v-else-if="needsPassword" class="password-state">
        <Card class="password-card">
          <template #content>
            <div class="password-content">
              <i class="pi pi-lock password-icon"></i>
              <div class="password-form">
                <h3>Password Required</h3>
                <p>This shared location is password protected.</p>
                <form @submit.prevent="verifyPassword" class="password-input-form">
                  <div class="input-group">
                    <Password
                        v-model="password"
                        placeholder="Enter password"
                        :feedback="false"
                        class="password-input"
                        autofocus
                    />
                    <Button
                        type="submit"
                        label="Access"
                        :loading="loading"
                        class="access-btn"
                    />
                  </div>
                </form>
              </div>
            </div>
          </template>
        </Card>
      </div>

      <!-- Location Data Display -->
      <div v-else-if="shareData" class="location-display">
        <!-- Location Info -->
        <Card class="location-info-card">
          <template #content>
            <div class="location-info">
              <h2 class="share-title">{{ shareData.shareName || 'Shared Location' }}</h2>
              <p v-if="shareData.description" class="share-description">{{ shareData.description }}</p>

              <div class="info-grid">
                <div class="info-item">
                  <span class="info-label">Shared by:</span>
                  <span class="info-value">{{ shareLinksStore.getSharedLocationInfo?.shared_by || 'Unknown' }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">Last seen:</span>
                  <span class="info-value">{{ timezone.timeAgo(shareData.sharedAt) }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">Expires:</span>
                  <span class="info-value" :class="getExpirationClass()">{{ formatExpiration() }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">Scope:</span>
                  <span class="info-value">{{
                      shareLinksStore.getSharedLocationInfo?.show_history ? 'Location History' : 'Current Location Only'
                    }}</span>
                </div>
              </div>
            </div>
          </template>
        </Card>

        <div v-if="shareData.latitude">
          <!-- Map Container -->
          <Card class="map-card">
            <template #header>
              <div class="map-header">
                <h3 class="map-title">{{ hasHistoryData ? 'Location & History' : 'Current Location' }}</h3>
                <Button
                    icon="pi pi-refresh"
                    @click="refreshLocationData"
                    :loading="refreshing"
                    severity="secondary"
                    outlined
                    size="small"
                    class="refresh-btn"
                    v-tooltip.bottom="'Refresh location data'"
                />
              </div>
            </template>
            <template #content>
              <div class="map-container">
                <MapContainer
                    ref="mapContainerRef"
                    map-id="shared-location-map"
                    :center="[shareData.latitude, shareData.longitude]"
                    :zoom="15"
                    height="100%"
                    width="100%"
                    :show-controls="false"
                    @map-ready="handleMapReady"
                >
                  <template #overlays="{ map, isReady }">
                    <!-- Path Layer for history -->
                    <PathLayer
                        v-if="map && isReady && hasHistoryData"
                        :map="map"
                        :path-data="pathData"
                        :visible="true"
                        :path-options="{
                      color: '#007bff',
                      weight: 3,
                      opacity: 0.7,
                      smoothFactor: 1
                    }"
                    />

                    <!-- Current location marker -->
                    <SharedLocationMarker
                        v-if="map && isReady"
                        :map="map"
                        :latitude="shareData.latitude"
                        :longitude="shareData.longitude"
                        :share-data="shareData"
                        :open-popup="true"
                    />
                  </template>
                </MapContainer>
              </div>
            </template>
          </Card>
        </div>
        <div v-else class="no-data-state">
          <Card class="no-data-card">
            <template #content>
              <div class="no-data-content">
                <i class="pi pi-info-circle no-data-icon"></i>
                <div class="no-data-message">
                  <h3>No Location Data Available</h3>
                  <p>The user hasn't recorded any GPS location data yet.</p>
                </div>
              </div>
            </template>
          </Card>
        </div>
      </div>
    </div>

    <!-- Footer -->
    <div class="shared-footer">
      <div class="footer-content">
        <p class="footer-text">
          Powered by <strong>GeoPulse</strong> - Location Analytics Platform
        </p>
        <Button
            label="Get GeoPulse"
            severity="secondary"
            @click="visitGeoPulse"
            class="get-app-btn"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import {computed, onMounted, ref} from 'vue'
import {useRoute} from 'vue-router'
import {Button, Card, Password, ProgressSpinner} from 'primevue'
import DarkModeSwitcher from '@/components/DarkModeSwitcher.vue'
import {MapContainer} from '@/components/maps'
import PathLayer from '@/components/maps/layers/PathLayer.vue'
import SharedLocationMarker from '@/components/maps/SharedLocationMarker.vue'
import {useShareLinksStore} from '@/stores/shareLinks'
import { useTimezone } from '@/composables/useTimezone'

const timezone = useTimezone()


const route = useRoute()
const linkId = route.params.linkId
const shareLinksStore = useShareLinksStore()

// State
const loading = ref(true)
const error = ref(null)
const shareData = ref(null)
const needsPassword = ref(false)
const password = ref('')
const refreshing = ref(false)
const pathData = ref([])

// Template refs
const mapContainerRef = ref(null)

// Computed
const hasHistoryData = computed(() => {
  return shareLinksStore.getSharedLocationInfo?.show_history &&
      pathData.value &&
      pathData.value.length > 0
})

// Check if we have a valid stored access token for this link
const hasValidStoredToken = () => {
  try {
    const tokenData = localStorage.getItem(`shareLink_${linkId}`)
    if (!tokenData) return false

    const {access_token, expires_at} = JSON.parse(tokenData)
    const now = Date.now()
    const timeLeft = expires_at - now
    const bufferTime = 5 * 60 * 1000 // 5 minutes

    // Check if token exists and hasn't expired (with buffer)
    if (access_token && expires_at && timeLeft > bufferTime) {
      shareLinksStore.sharedAccessToken = access_token
      return true
    } else {
      // Clean up expired token
      localStorage.removeItem(`shareLink_${linkId}`)
      return false
    }
  } catch (error) {
    localStorage.removeItem(`shareLink_${linkId}`)
    return false
  }
}

// Initialize shared view
const initializeSharedView = async () => {
  try {
    loading.value = true
    error.value = null

    // Step 1: Get link info (this works without authentication for public access)
    const linkInfo = await shareLinksStore.fetchSharedLocationInfo(linkId)

    if (linkInfo.has_password) {
      // Check if we have a valid stored token first
      if (hasValidStoredToken()) {
        // We have a valid token, skip password entry
        await loadLocationData()
      } else {
        // Need password
        needsPassword.value = true
        loading.value = false
      }
    } else {
      // For public links, verify with no password to get access token
      const tokenResponse = await shareLinksStore.verifySharedLink(linkId)

      // Store the token for future use (even for public links)
      storeAccessToken(tokenResponse)

      await loadLocationData()
    }
  } catch (err) {
    // Clean up stored token if link is not found, expired, or invalid
    if (err.message?.includes('not found') ||
        err.message?.includes('expired') ||
        err.message?.includes('invalid') ||
        err.status === 404) {
      localStorage.removeItem(`shareLink_${linkId}`)
    }

    error.value = err.message || 'Failed to load shared location'
    loading.value = false
  }
}

// Store access token for future use
const storeAccessToken = (tokenResponse) => {
  try {
    const tokenData = {
      access_token: tokenResponse.access_token,
      expires_at: Date.now() + (tokenResponse.expires_in * 1000)
    }
    localStorage.setItem(`shareLink_${linkId}`, JSON.stringify(tokenData))
  } catch (error) {
    // Silent fail
  }
}

// Verify password for protected links
const verifyPassword = async () => {
  try {
    loading.value = true
    error.value = null

    const tokenResponse = await shareLinksStore.verifySharedLink(linkId, password.value)

    // Store the token for future use
    storeAccessToken(tokenResponse)

    needsPassword.value = false
    await loadLocationData()
  } catch (err) {
    // Clean up any stored token if password verification fails
    if (err.message?.includes('Invalid password') || err.status === 401) {
      localStorage.removeItem(`shareLink_${linkId}`)
    }

    error.value = err.message || 'Invalid password'
    loading.value = false
  }
}

// Load location data after verification
const loadLocationData = async () => {
  try {
    loading.value = true

    await shareLinksStore.fetchSharedLocation(linkId)
    const locationData = shareLinksStore.getSharedLocationData

    // Convert to expected format
    if (locationData) {
      // Handle both current-only and current+history response formats
      const currentLocation = locationData.current || locationData

      shareData.value = {
        sharedBy: shareLinksStore.getSharedLocationInfo?.shared_by,
        shareName: shareLinksStore.getSharedLocationInfo?.name || 'Shared Location',
        description: shareLinksStore.getSharedLocationInfo?.description || '',
        latitude: currentLocation.latitude,
        longitude: currentLocation.longitude,
        sharedAt: currentLocation.timestamp
      }

      // Process history data if available
      if (shareLinksStore.getSharedLocationInfo?.show_history && locationData.history && locationData.history.length > 0) {
        pathData.value = [locationData.history.map(point => ({
          latitude: point.latitude,
          longitude: point.longitude,
          timestamp: point.timestamp
        }))]
      } else {
        pathData.value = []
      }
    }

    loading.value = false
  } catch (err) {
    // Clean up stored token if it's no longer valid for location access
    if (err.message?.includes('No access token') ||
        err.message?.includes('Access denied') ||
        err.status === 401 ||
        err.status === 403) {
      localStorage.removeItem(`shareLink_${linkId}`)

      // If token was invalid, redirect back to password entry for protected links
      if (shareLinksStore.getSharedLocationInfo?.has_password) {
        needsPassword.value = true
        error.value = null
        loading.value = false
        return
      }
    }

    error.value = err.message || 'Failed to load location data'
    loading.value = false
  }
}

// Refresh location data without re-authentication
const refreshLocationData = async () => {
  try {
    refreshing.value = true

    await shareLinksStore.fetchSharedLocation(linkId)
    const locationData = shareLinksStore.getSharedLocationData

    // Update shareData with new location info
    if (locationData) {
      // Handle both current-only and current+history response formats
      const currentLocation = locationData.current || locationData

      shareData.value = {
        ...shareData.value, // Keep existing name, description
        latitude: currentLocation.latitude,
        longitude: currentLocation.longitude,
        sharedAt: currentLocation.timestamp || timezone.now().toISOString()
      }

      // Update history data if available
      if (shareLinksStore.getSharedLocationInfo?.show_history && locationData.history && locationData.history.length > 0) {
        pathData.value = [locationData.history.map(point => ({
          latitude: point.latitude,
          longitude: point.longitude,
          timestamp: point.timestamp
        }))]
      } else {
        pathData.value = []
      }
    }
  } catch (err) {
    // Clean up stored token if refresh fails due to invalid token
    if (err.message?.includes('No access token') ||
        err.message?.includes('Access denied') ||
        err.status === 401 ||
        err.status === 403) {
      localStorage.removeItem(`shareLink_${linkId}`)

      // If token was invalid, redirect back to password entry for protected links
      if (shareLinksStore.getSharedLocationInfo?.has_password) {
        needsPassword.value = true
        error.value = 'Session expired. Please enter password again.'
        loading.value = false
      }
    }
  } finally {
    refreshing.value = false
  }
}

// Handle map ready
const handleMapReady = (mapInstance) => {
  // Map ready
}

// Format time until expiration (like timeAgo but for future dates)
const timeUntil = (futureDate) => {
  if (!futureDate) return 'Never'

  const dateObj = timezone.fromUtc(futureDate);

  if (!dateObj.isValid()) {
    return 'Invalid date'
  }

  const now = timezone.now();
  const diffMs = dateObj.diff(now);

  if (diffMs <= 0) {
    return 'Expired'
  }

  const diffMinutes = Math.floor(diffMs / (1000 * 60))
  const diffHours = Math.floor(diffMs / (1000 * 60 * 60))
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24))

  if (diffMinutes < 60) return `In ${diffMinutes} min`
  if (diffHours < 24) return `In ${diffHours} hours`
  if (diffDays < 30) return `In ${diffDays} days`

  // For longer periods, show actual date
  return `On ${timezone.format(dateObj, 'YYYY-MM-DD HH:mm')}`
}

// Format expiration using timeUntil
const formatExpiration = () => {
  const expiresAt = shareLinksStore.getSharedLocationInfo?.expires_at
  return timeUntil(expiresAt)
}

// Get expiration class for styling
const getExpirationClass = () => {
  const expiresAt = shareLinksStore.getSharedLocationInfo?.expires_at
  if (!expiresAt) return ''

  const expiration = timezone.fromUtc(expiresAt)
  const now = timezone.now()
  const timeLeft = expiration - now
  const hoursLeft = timeLeft / (1000 * 60 * 60)

  if (expiration < now) {
    return 'expired'
  } else if (hoursLeft < 24) {
    return 'expiring-soon'
  }

  return ''
}

const navigateToHome = () => {
  window.location.href = '/'
}

const visitGeoPulse = () => {
  window.open('/', '_blank')
}


// Lifecycle
onMounted(() => {
  initializeSharedView()
})

// Note: Not clearing store data on unmount to allow page refresh persistence
// The store data will be naturally replaced when accessing a new shared link
</script>

<style scoped>
.shared-location-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(135deg, var(--gp-surface-light) 0%, var(--gp-surface-gray) 100%);
}

/* Header */
.shared-header {
  background: var(--p-surface-card);
  border-bottom: 1px solid var(--p-surface-border);
  padding: 0.5rem 0;
}

.header-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 1rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.logo-section {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  cursor: pointer;
  transition: opacity 0.2s ease;
}

.logo-section:hover {
  opacity: 0.8;
}

.logo {
  height: 32px;
  width: auto;
}

.app-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--p-primary-color);
  margin: 0;
}

.header-right {
  flex-shrink: 0;
}

.large-theme-toggle :deep(.p-button) {
  width: 3rem;
  height: 3rem;
  font-size: 1.5rem;
}

/* Content */
.shared-content {
  flex: 1;
  max-width: 1200px;
  margin: 0 auto;
  padding: 2rem 1rem;
  width: 100%;
}

.loading-state, .error-state, .password-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  text-align: center;
}

.error-card, .password-card {
  max-width: 400px;
  margin: 0 auto;
}

.error-content, .password-content {
  display: flex;
  align-items: center;
  gap: 1rem;
  text-align: left;
}

.error-icon, .password-icon {
  font-size: 2rem;
  flex-shrink: 0;
}

.error-icon {
  color: var(--p-red-500);
}

.password-icon {
  color: var(--p-orange-500);
}

.error-message, .password-form {
  flex: 1;
}

.error-message h3, .password-form h3 {
  font-size: 1.1rem;
  font-weight: 600;
  margin: 0 0 0.5rem 0;
  color: var(--p-text-color);
}

.error-message p, .password-form p {
  color: var(--p-text-color-secondary);
  margin: 0 0 1rem 0;
}

.password-input-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.input-group {
  display: flex;
  gap: 0.5rem;
}

.password-input {
  flex: 1;
}

/* No Data State */
.no-data-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  text-align: center;
}

.no-data-card {
  max-width: 400px;
  margin: 0 auto;
}

.no-data-content {
  display: flex;
  align-items: center;
  gap: 1rem;
  text-align: left;
}

.no-data-icon {
  font-size: 2rem;
  color: var(--p-blue-500);
  flex-shrink: 0;
}

.no-data-message {
  flex: 1;
}

.no-data-message h3 {
  font-size: 1.1rem;
  font-weight: 600;
  margin: 0 0 0.5rem 0;
  color: var(--p-text-color);
}

.no-data-message p {
  color: var(--p-text-color-secondary);
  margin: 0;
}

.location-display {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.location-info-card, .map-card {
  width: 100%;
}

.location-info {
  text-align: center;
}

.share-title {
  font-size: 1.5rem;
  font-weight: 600;
  margin: 0 0 0.5rem 0;
  color: var(--p-text-color);
}

.share-description {
  color: var(--p-text-color-secondary);
  margin: 0 0 1rem 0;
  font-style: italic;
}

.share-meta {
  font-size: 0.9rem;
  color: var(--p-text-color-secondary);
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1rem;
  margin-top: 1.5rem;
}

/* 2 columns on larger mobile screens */
@media (min-width: 428px) {
  .info-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 1rem 1.5rem;
  }
}

/* 4 columns on tablets and larger */
@media (min-width: 768px) {
  .info-grid {
    grid-template-columns: repeat(4, 1fr);
    gap: 1.5rem;
  }
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.info-label {
  font-size: 0.875rem;
  color: var(--p-text-color-secondary);
  font-weight: 500;
}

.info-value {
  font-size: 1rem;
  color: var(--p-text-color);
  font-weight: 600;
}

.info-value.expired {
  color: var(--p-red-500);
}

.info-value.expiring-soon {
  color: var(--p-orange-500);
}

.map-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.5rem;
  border-bottom: 1px solid var(--p-surface-border);
}

.map-title {
  font-size: 1.1rem;
  font-weight: 600;
  margin: 0;
  color: var(--p-text-color);
}

.refresh-btn {
  min-width: auto;
  padding: 0.5rem;
}

.map-container {
  height: 500px;
  width: 100%;
  border-radius: 0.75rem;
  overflow: hidden;
}

/* Footer */
.shared-footer {
  background: var(--p-surface-50);
  border-top: 1px solid var(--p-surface-border);
  padding: 1.5rem 0;
  margin-top: auto;
}

.footer-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 1rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.footer-text {
  color: var(--p-text-color-secondary);
  margin: 0;
}

/* Dark Mode Support */
.p-dark .shared-location-page {
  background: linear-gradient(135deg, var(--gp-surface-dark) 0%, var(--gp-surface-darker) 100%);
}

.p-dark .shared-location-page .shared-footer {
  background: var(--gp-surface-darker) !important;
  border-top-color: var(--gp-border-dark);
}

.p-dark .shared-location-page .footer-text {
  color: var(--gp-text-primary) !important;
}

.p-dark .shared-location-page .share-title {
  color: var(--gp-text-primary) !important;
}

.p-dark .shared-location-page .info-value {
  color: var(--gp-text-primary) !important;
}

.p-dark .shared-location-page .info-label {
  color: var(--gp-text-secondary) !important;
}

.p-dark .shared-location-page .password-form h3,
.p-dark .shared-location-page .error-message h3 {
  color: var(--gp-text-primary) !important;
}

.p-dark .shared-location-page .info-value.expired {
  color: var(--gp-danger) !important;
}

.p-dark .shared-location-page .info-value.expiring-soon {
  color: var(--gp-warning) !important;
}

.p-dark .shared-location-page .map-title {
  color: var(--gp-text-primary) !important;
}

.p-dark .shared-location-page .map-header {
  border-bottom-color: var(--gp-border-dark) !important;
}

.p-dark .shared-location-page .no-data-message h3 {
  color: var(--gp-text-primary) !important;
}

.p-dark .shared-location-page .no-data-message p {
  color: var(--gp-text-secondary) !important;
}

.p-dark .shared-location-page .no-data-icon {
  color: var(--gp-primary) !important;
}

/* Responsive */
@media (max-width: 768px) {
  .shared-content {
    padding: 1rem 1rem;
  }

  .location-display {
    gap: 1rem;
  }

  .location-info {
    margin-top: 0;
  }

  .info-grid {
    margin-top: 1rem;
  }

  .header-content {
    align-items: flex-start;
    gap: 0.5rem;
  }

  .footer-content {
    flex-direction: column;
    gap: 1rem;
    text-align: center;
  }

  .map-container {
    height: 400px;
  }

  .large-theme-toggle :deep(.p-button) {
    width: 2.5rem;
    height: 2.5rem;
    font-size: 1.25rem;
  }
}
</style>
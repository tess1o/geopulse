<template>
  <div class="home-page">
    <section class="hero-shell" :class="{ 'hero-shell-no-divider': shouldShowQuickStats }">
      <div class="home-container">
        <div class="top-bar">
          <DarkModeSwitcher class="theme-button" />
        </div>

        <div class="hero-grid">
          <div class="hero-copy">
            <img
              src="/geopulse-logo.svg"
              alt="GeoPulse logo"
              class="hero-logo"
              loading="eager"
            />

            <p class="hero-eyebrow">Self-hosted location timeline</p>
            <h1 class="hero-title">{{ heroTitle }}</h1>
            <p v-if="!isMobileViewport" class="hero-subtitle">
              GeoPulse turns raw GPS points into stays, trips, maps, and insights while your data stays under your control.
            </p>

          </div>

          <div class="hero-panel">
            <div v-if="isResolvingAuth" class="status-card loading-card">
              <i class="pi pi-spin pi-spinner"></i>
              <h2>Preparing your workspace</h2>
              <p>Checking authentication and available sign-in options.</p>
            </div>

            <div v-else-if="!authStore.isAuthenticated" class="status-card guest-card">
              <p class="card-eyebrow">Get started</p>
              <h2 class="card-title">Welcome to GeoPulse</h2>
              <p class="card-subtitle">
                Choose how you want to enter the app.
              </p>

              <div
                v-if="!isLoginAvailable && !isRegistrationAvailable"
                class="status-message status-warning"
              >
                <i class="pi pi-lock"></i>
                <div>
                  <h3>Access is temporarily unavailable</h3>
                  <p>
                    Login and registration are currently disabled by the administrator.
                  </p>
                </div>
              </div>

              <template v-else>
                <div v-if="showRegistrationDisabledNotice" class="status-message status-info">
                  <i class="pi pi-info-circle"></i>
                  <div>
                    <h3>Registration is disabled</h3>
                    <p>Existing users can still sign in to continue.</p>
                  </div>
                </div>

                <div class="cta-group">
                  <Button
                    v-if="isRegistrationAvailable"
                    label="Start Your Journey"
                    icon="pi pi-arrow-right"
                    as="router-link"
                    to="/register"
                    class="cta-button cta-primary"
                    size="large"
                  />

                  <Button
                    v-if="isLoginAvailable"
                    label="Sign In"
                    icon="pi pi-sign-in"
                    as="router-link"
                    to="/login"
                    :severity="isRegistrationAvailable ? 'secondary' : undefined"
                    :outlined="isRegistrationAvailable"
                    class="cta-button"
                    :class="isRegistrationAvailable ? 'cta-secondary' : 'cta-primary'"
                    size="large"
                  />
                </div>
              </template>
            </div>

            <div v-else class="status-card signed-in-card">
              <p class="card-eyebrow">Welcome back</p>
              <h2 class="card-title">Continue where you left off</h2>
              <p v-if="!isMobileViewport" class="card-subtitle">
                Jump straight into your preferred view or pick a quick action.
              </p>

              <div class="continue-card" :class="{ 'continue-card-mobile': isMobileViewport }">
                <div v-if="!isMobileViewport" class="continue-meta">
                  <p class="continue-label">
                    {{ hasDefaultRedirectUrl ? 'Your default start page' : 'Recommended start page' }}
                  </p>
                  <p class="continue-value">{{ continueDestination.label }}</p>
                </div>
                <Button
                  :label="`Continue to ${continueDestination.label}`"
                  :icon="isMobileViewport ? undefined : continueDestination.icon"
                  as="router-link"
                  :to="continueDestination.path"
                  class="cta-button cta-primary continue-button"
                  size="large"
                />
              </div>

              <div class="quick-actions" :class="{ 'quick-actions-mobile': isMobileViewport }">
                <Button
                  v-for="action in isMobileViewport ? secondaryQuickActions : quickActions"
                  :key="action.to"
                  :label="action.label"
                  :icon="isMobileViewport ? undefined : action.icon"
                  as="router-link"
                  :to="action.to"
                  class="quick-action-button"
                  :class="{ 'quick-action-link': isMobileViewport }"
                  :text="isMobileViewport"
                />
              </div>

              <div class="signout-row">
                <Button
                  label="Sign out"
                  icon="pi pi-sign-out"
                  text
                  size="small"
                  class="signout-button"
                  :loading="isSigningOut"
                  @click="handleSignOut"
                />
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section v-if="shouldShowQuickStats" class="quick-stats-section">
      <div class="home-container">
        <div class="quick-stats-grid">
          <article class="quick-stat-tile quick-stat-latest">
            <div class="quick-stat-head">
              <span class="quick-stat-icon" aria-hidden="true"><i class="pi pi-history"></i></span>
              <p class="quick-stat-label">Latest GPS update</p>
            </div>
            <p class="quick-stat-value">{{ latestGpsUpdateLabel }}</p>
          </article>
          <article class="quick-stat-tile quick-stat-distance">
            <div class="quick-stat-head">
              <span class="quick-stat-icon" aria-hidden="true"><i class="pi pi-map-marker"></i></span>
              <p class="quick-stat-label">Distance today</p>
            </div>
            <p class="quick-stat-value">{{ distanceTodayLabel }}</p>
          </article>
          <article class="quick-stat-tile quick-stat-moving">
            <div class="quick-stat-head">
              <span class="quick-stat-icon" aria-hidden="true"><i class="pi pi-send"></i></span>
              <p class="quick-stat-label">Time moving today</p>
            </div>
            <p class="quick-stat-value">{{ timeMovingTodayLabel }}</p>
          </article>
        </div>
      </div>
    </section>

    <section v-if="!isResolvingAuth && !authStore.isAuthenticated" class="features-section">
      <div class="home-container">
        <div class="section-header">
          <h2>Core capabilities</h2>
          <p>Built for self-hosted tracking: ingest data, analyze movement, and control sharing.</p>
        </div>

        <div class="feature-track">
          <Card
            v-for="feature in visibleFeatures"
            :key="feature.id"
            class="feature-card"
          >
            <template #content>
              <div class="feature-content">
                <div class="feature-icon">
                  <i :class="feature.icon"></i>
                </div>
                <div class="feature-text">
                  <h3>{{ feature.title }}</h3>
                  <p>{{ feature.description }}</p>
                </div>
              </div>
            </template>
          </Card>
        </div>

        <Button
          v-if="!isDesktopViewport && !isMobileViewport && hasHiddenFeatures"
          :label="showAllFeatures ? 'Show fewer' : 'Show all capabilities'"
          :icon="showAllFeatures ? 'pi pi-angle-up' : 'pi pi-angle-down'"
          severity="secondary"
          outlined
          class="mobile-feature-toggle"
          @click="toggleFeatureVisibility"
        />
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import DarkModeSwitcher from '@/components/DarkModeSwitcher.vue'
import apiService from '@/utils/apiService'
import { useTimezone } from '@/composables/useTimezone'
import { formatDistance, formatDuration } from '@/utils/calculationsHelpers'

const DEFAULT_AUTH_STATUS = {
  passwordRegistrationEnabled: false,
  oidcRegistrationEnabled: false,
  passwordLoginEnabled: true,
  oidcLoginEnabled: true,
  adminLoginBypassEnabled: true,
  guestRootRedirectToLoginEnabled: false
}

const KNOWN_DESTINATIONS = {
  '/app/timeline': { label: 'Timeline', icon: 'pi pi-calendar' },
  '/app/dashboard': { label: 'Dashboard', icon: 'pi pi-chart-bar' },
  '/app/journey-insights': { label: 'Journey Insights', icon: 'pi pi-compass' },
  '/app/friends': { label: 'Friends', icon: 'pi pi-users' },
  '/app/location-sources': { label: 'Location Sources', icon: 'pi pi-map' },
  '/app/coverage': { label: 'Coverage Explorer', icon: 'pi pi-globe' },
  '/app/rewind': { label: 'Rewind', icon: 'pi pi-history' },
  '/app/gps-data': { label: 'GPS Data', icon: 'pi pi-map-marker' }
}

const quickActions = [
  { to: '/app/timeline', label: 'Timeline', icon: 'pi pi-calendar' },
  { to: '/app/dashboard', label: 'Dashboard', icon: 'pi pi-chart-bar' },
  { to: '/app/journey-insights', label: 'Journey Insights', icon: 'pi pi-compass' },
  { to: '/app/friends', label: 'Friends', icon: 'pi pi-users' }
]

const features = ref([
  {
    id: 1,
    icon: 'pi pi-send',
    title: 'Real-time Tracking Sources',
    description: 'Ingest live points from OwnTracks (HTTP/MQTT), Overland, GPSLogger, Home Assistant, Traccar, and Colota.'
  },
  {
    id: 2,
    icon: 'pi pi-download',
    title: 'Universal Import',
    description: 'Bulk import history from Google Timeline, GPX, GeoJSON, OwnTracks exports, and CSV.'
  },
  {
    id: 3,
    icon: 'pi pi-calendar',
    title: 'Smart Timeline Detection',
    description: 'Convert raw points into stays, trips, and gaps with configurable sensitivity and movement logic.'
  },
  {
    id: 4,
    icon: 'pi pi-chart-line',
    title: 'Deep Insights',
    description: 'Explore distance, visit frequency, and movement patterns through dashboard and journey analytics.'
  },
  {
    id: 5,
    icon: 'pi pi-images',
    title: 'Immich Integration',
    description: 'Show Immich photos directly on your map and timeline to connect places with memories.'
  },
  {
    id: 6,
    icon: 'pi pi-user-edit',
    title: 'Sharing, Roles, and SSO',
    description: 'Use friend visibility controls, secure guest links, invitations, audit logs, and OIDC login.'
  }
])

const initialVisibleFeatureCount = 3
const showAllFeatures = ref(false)
const isDesktopViewport = ref(false)
const isMobileViewport = ref(false)

const authStore = useAuthStore()
const router = useRouter()
const timezone = useTimezone()
const authStatus = ref({ ...DEFAULT_AUTH_STATUS })
const oidcProviders = ref([])
const isResolvingAuth = ref(true)
const isSigningOut = ref(false)
const quickStats = ref({
  latestPointTimestamp: null,
  totalDistanceMeters: 0,
  timeMoving: 0
})

const visibleFeatures = computed(() => {
  if (isDesktopViewport.value || isMobileViewport.value || showAllFeatures.value) {
    return features.value
  }
  return features.value.slice(0, initialVisibleFeatureCount)
})

const hasHiddenFeatures = computed(() => {
  return features.value.length > initialVisibleFeatureCount
})

const hasOidcProvidersAvailable = computed(() => {
  return authStatus.value.oidcLoginEnabled && oidcProviders.value.length > 0
})

const isRegistrationAvailable = computed(() => {
  return authStatus.value.passwordRegistrationEnabled || authStatus.value.oidcRegistrationEnabled
})

const isLoginAvailable = computed(() => {
  return authStatus.value.passwordLoginEnabled ||
    hasOidcProvidersAvailable.value ||
    authStatus.value.adminLoginBypassEnabled
})

const showRegistrationDisabledNotice = computed(() => {
  return !isRegistrationAvailable.value && isLoginAvailable.value
})

const hasDefaultRedirectUrl = computed(() => {
  return typeof authStore.defaultRedirectUrl === 'string' && authStore.defaultRedirectUrl.trim().length > 0
})

const continueDestination = computed(() => {
  const preferredPath = hasDefaultRedirectUrl.value
    ? authStore.defaultRedirectUrl
    : '/app/timeline'
  return buildDestination(preferredPath)
})

const heroTitle = computed(() => {
  if (isMobileViewport.value) {
    return 'Your movement. Your server.'
  }
  return 'Your movement history, on your server.'
})

const secondaryQuickActions = computed(() => {
  return quickActions.filter((action) => action.to !== continueDestination.value.path)
})

const hasMeaningfulQuickStats = computed(() => {
  return Boolean(
    quickStats.value.latestPointTimestamp ||
    quickStats.value.totalDistanceMeters > 0 ||
    quickStats.value.timeMoving > 0
  )
})

const shouldShowQuickStats = computed(() => {
  return !isResolvingAuth.value && authStore.isAuthenticated && hasMeaningfulQuickStats.value
})

const latestGpsUpdateLabel = computed(() => {
  if (!quickStats.value.latestPointTimestamp) {
    return 'No updates yet'
  }
  return timezone.timeAgo(quickStats.value.latestPointTimestamp)
})

const distanceTodayLabel = computed(() => {
  return formatDistance(quickStats.value.totalDistanceMeters)
})

const timeMovingTodayLabel = computed(() => {
  return formatDuration(quickStats.value.timeMoving)
})

const unwrapPayload = (response) => {
  if (response && typeof response === 'object' && response.data && typeof response.data === 'object') {
    return response.data
  }
  return response
}

const toNumberOrZero = (value) => {
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue : 0
}

const normalizeTimestamp = (value) => {
  if (!value) {
    return null
  }

  if (typeof value === 'number' && Number.isFinite(value)) {
    return new Date(value).toISOString()
  }

  return typeof value === 'string' ? value : null
}

const fetchLatestGpsUpdate = async () => {
  try {
    const response = await apiService.get('/gps/last-known-position')
    const payload = unwrapPayload(response)
    const pointPayload = payload?.data && typeof payload.data === 'object'
      ? payload.data
      : payload

    return normalizeTimestamp(pointPayload?.timestamp)
  } catch (error) {
    console.warn('Failed to load latest GPS update for home quick stats:', error)
    return null
  }
}

const fetchTodayStats = async () => {
  const todayRange = timezone.getTodayRangeUtc()

  try {
    const response = await apiService.get('/statistics', {
      startTime: todayRange.start,
      endTime: todayRange.end
    })
    const payload = unwrapPayload(response)

    return {
      totalDistanceMeters: toNumberOrZero(payload?.totalDistanceMeters),
      timeMoving: toNumberOrZero(payload?.timeMoving)
    }
  } catch (error) {
    console.warn('Failed to load today statistics for home quick stats:', error)
    return {
      totalDistanceMeters: 0,
      timeMoving: 0
    }
  }
}

const loadSignedInQuickStats = async () => {
  const [latestPointTimestamp, todayStats] = await Promise.all([
    fetchLatestGpsUpdate(),
    fetchTodayStats()
  ])

  quickStats.value = {
    latestPointTimestamp,
    totalDistanceMeters: todayStats.totalDistanceMeters,
    timeMoving: todayStats.timeMoving
  }
}

const handleSignOut = async () => {
  if (isSigningOut.value) {
    return
  }

  isSigningOut.value = true

  try {
    await authStore.logout()
  } catch (error) {
    console.error('Failed to sign out from home page:', error)
  } finally {
    isSigningOut.value = false
    await router.push('/')
  }
}

const normalizeDestinationPath = (path) => {
  if (typeof path !== 'string') {
    return '/app/timeline'
  }

  const trimmed = path.trim()
  if (!trimmed || !trimmed.startsWith('/')) {
    return '/app/timeline'
  }

  return trimmed
}

const humanizePath = (path) => {
  const segmentLabel = path
    .replace(/^\/+/, '')
    .split('/')
    .filter(Boolean)
    .map((segment) => {
      return segment
        .replace(/[-_]/g, ' ')
        .replace(/\b\w/g, (char) => char.toUpperCase())
    })
    .join(' / ')

  return segmentLabel || 'Timeline'
}

const buildDestination = (path) => {
  const normalizedPath = normalizeDestinationPath(path)
  const known = KNOWN_DESTINATIONS[normalizedPath]

  if (known) {
    return {
      path: normalizedPath,
      label: known.label,
      icon: known.icon
    }
  }

  return {
    path: normalizedPath,
    label: humanizePath(normalizedPath),
    icon: 'pi pi-arrow-right'
  }
}

const updateViewportState = () => {
  if (typeof window === 'undefined') {
    return
  }

  isDesktopViewport.value = window.innerWidth >= 1024
  isMobileViewport.value = window.innerWidth < 768
}

const toggleFeatureVisibility = () => {
  showAllFeatures.value = !showAllFeatures.value
}

onMounted(async () => {
  updateViewportState()
  window.addEventListener('resize', updateViewportState)

  isResolvingAuth.value = true

  try {
    await authStore.checkAuth()
  } catch (error) {
    console.error('Failed to reconcile auth state on home page:', error)
  }

  const [authStatusResult, oidcProvidersResult] = await Promise.allSettled([
    authStore.getAuthStatus(),
    authStore.getOidcProviders()
  ])

  if (authStatusResult.status === 'fulfilled' && authStatusResult.value) {
    authStatus.value = {
      ...DEFAULT_AUTH_STATUS,
      ...authStatusResult.value
    }
  } else {
    authStatus.value = { ...DEFAULT_AUTH_STATUS }
  }

  if (oidcProvidersResult.status === 'fulfilled' && Array.isArray(oidcProvidersResult.value)) {
    oidcProviders.value = oidcProvidersResult.value
  } else {
    oidcProviders.value = []
  }

  isResolvingAuth.value = false

  if (authStore.isAuthenticated) {
    await loadSignedInQuickStats()
  }
})

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', updateViewportState)
  }
})
</script>

<style scoped>
.home-page {
  --home-bg: var(--gp-surface-light, #f4f8ff);
  --home-card-bg: var(--gp-surface-white, #ffffff);
  --home-text-primary: var(--gp-text-primary, #0f172a);
  --home-text-secondary: var(--gp-text-secondary, #475569);
  --home-border: var(--gp-border-light, #dbe3ef);
  --home-border-strong: var(--gp-border-medium, #bcc8db);
  --home-shadow: var(--gp-shadow-light, 0 8px 30px rgba(15, 23, 42, 0.08));
  --home-shadow-strong: var(--gp-shadow-medium, 0 18px 40px rgba(15, 23, 42, 0.16));
  --home-accent: var(--gp-primary, #0f766e);
  --home-accent-hover: var(--gp-primary-hover, #0d615a);
  --home-accent-soft: rgba(15, 118, 110, 0.14);
  --home-focus: rgba(13, 148, 136, 0.28);
  --home-feature-card-bg: #fcfdff;
  --home-feature-border: #7a879b;
  --home-feature-icon-blob: linear-gradient(150deg, rgba(15, 118, 110, 0.24) 0%, rgba(14, 165, 233, 0.2) 100%);
  --home-feature-icon-blob-ring: rgba(15, 118, 110, 0.3);
  --home-feature-icon-shadow: 0 8px 18px rgba(14, 116, 144, 0.18);
  --home-feature-icon-color: var(--home-accent);
  --home-feature-icon-highlight: rgba(255, 255, 255, 0.34);
  --home-feature-icon-stroke: 0.25px currentColor;
  --home-feature-icon-glyph-shadow: 0 1px 0 rgba(255, 255, 255, 0.16);
  --home-tag-font-size: 0.78rem;
  --home-tag-letter-spacing: 0.08em;
  --home-tag-line-height: 1.2;
  --home-hero-title-size: clamp(2rem, 7vw, 3.2rem);
  --home-logo-size: calc(var(--home-hero-title-size) * 1.7);
  --home-logo-circle-offset: 29.2%;
  --home-logo-tag-gap: 3.375rem;
  --home-hero-gradient: linear-gradient(145deg, #f8fbff 0%, #edf3ff 52%, #e8f0ff 100%);
  --home-hero-glow: radial-gradient(ellipse at 24% 14%, rgba(37, 99, 235, 0.18) 0%, rgba(37, 99, 235, 0) 58%),
    radial-gradient(ellipse at 80% 88%, rgba(14, 165, 233, 0.16) 0%, rgba(14, 165, 233, 0) 62%);
  --home-continue-bg: linear-gradient(155deg, rgba(37, 99, 235, 0.12) 0%, rgba(14, 165, 233, 0.08) 100%);
  --home-continue-border: rgba(37, 99, 235, 0.24);
  --home-primary-shadow: 0 10px 24px rgba(37, 99, 235, 0.22);
  --home-secondary-bg: rgba(37, 99, 235, 0.05);
  --home-secondary-hover-bg: rgba(37, 99, 235, 0.11);
  --home-quick-bg: var(--home-card-bg);
  --home-quick-hover-bg: rgba(37, 99, 235, 0.08);
  --home-quick-border: rgba(148, 163, 184, 0.38);
  --home-quick-hover-border: rgba(37, 99, 235, 0.5);
  --home-stats-tile-bg: var(--home-card-bg);
  --home-stats-tile-border: rgba(148, 163, 184, 0.4);
  --home-stats-value: var(--home-text-primary);
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  background: var(--home-bg);
  color: var(--home-text-primary);
}

.home-page::before {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--home-hero-gradient);
  z-index: 0;
}

.home-page::after {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--home-hero-glow);
  z-index: 1;
}

.home-container {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 1200px;
  box-sizing: border-box;
  margin: 0 auto;
  padding: 0 1rem;
}

.hero-shell {
  position: relative;
  border-bottom: 1px solid var(--home-border);
}

.hero-shell-no-divider {
  border-bottom: 0;
}

.top-bar {
  display: flex;
  justify-content: flex-end;
  padding: 1rem 0 0.75rem;
}

.theme-button :deep(.p-button) {
  width: 2.6rem !important;
  height: 2.6rem !important;
  padding: 0 !important;
  border-radius: 50% !important;
  border: 1px solid var(--home-border) !important;
  background: var(--home-card-bg) !important;
  box-shadow: var(--home-shadow) !important;
}

.theme-button :deep(.p-button:hover) {
  border-color: var(--home-border-strong) !important;
  box-shadow: var(--home-shadow-strong) !important;
  transform: translateY(-1px);
}

.theme-button :deep(.p-button:focus) {
  box-shadow: var(--home-shadow), 0 0 0 4px var(--home-focus) !important;
}

.hero-grid {
  display: grid;
  gap: 1.2rem;
  padding: 0.5rem 0 2.5rem;
}

.hero-copy {
  position: relative;
  text-align: left;
}

.hero-logo {
  display: block;
  width: var(--home-logo-size);
  height: auto;
  transform: translateX(calc(-1 * var(--home-logo-circle-offset)));
  margin-bottom: var(--home-logo-tag-gap);
  filter: drop-shadow(0 8px 14px rgba(15, 23, 42, 0.1));
}

.hero-eyebrow {
  margin: 0;
  font-size: var(--home-tag-font-size);
  font-weight: 700;
  letter-spacing: var(--home-tag-letter-spacing);
  line-height: var(--home-tag-line-height);
  text-transform: uppercase;
  color: var(--home-accent);
}

.hero-title {
  margin: 0.45rem 0 0.65rem;
  max-width: 17ch;
  font-size: var(--home-hero-title-size);
  line-height: 1.13;
  letter-spacing: -0.025em;
  color: var(--home-text-primary);
}

.hero-subtitle {
  margin: 0;
  max-width: 58ch;
  color: var(--home-text-secondary);
  font-size: clamp(1rem, 2.1vw, 1.15rem);
  line-height: 1.74;
}

.hero-panel {
  width: 100%;
  min-width: 0;
}

.status-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 0.95rem;
  background: var(--home-card-bg);
  border: 1px solid var(--home-border);
  border-radius: 1.05rem;
  box-shadow: var(--home-shadow);
  padding: 1.15rem;
}

.loading-card {
  justify-content: center;
  align-items: center;
  text-align: center;
  min-height: 16rem;
}

.loading-card i {
  font-size: 1.7rem;
  color: var(--home-accent);
}

.loading-card h2 {
  margin: 0;
  font-size: 1.18rem;
}

.loading-card p {
  margin: 0;
  color: var(--home-text-secondary);
  line-height: 1.5;
}

.card-eyebrow {
  margin: 0;
  font-size: var(--home-tag-font-size);
  letter-spacing: var(--home-tag-letter-spacing);
  line-height: var(--home-tag-line-height);
  text-transform: uppercase;
  font-weight: 700;
  color: var(--home-accent);
}

.card-title {
  margin: 0;
  font-size: clamp(1.3rem, 3.6vw, 1.65rem);
  line-height: 1.2;
  color: var(--home-text-primary);
}

.card-subtitle {
  margin: 0;
  color: var(--home-text-secondary);
  font-size: 0.97rem;
  line-height: 1.48;
}

.status-message {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.8rem;
  align-items: start;
  border-radius: 0.82rem;
  border: 1px solid var(--home-border);
  padding: 0.78rem;
  background: #f8fafc;
}

.status-message i {
  margin-top: 0.15rem;
  font-size: 1.08rem;
}

.status-message h3 {
  margin: 0;
  font-size: 0.97rem;
  line-height: 1.3;
}

.status-message p {
  margin: 0.28rem 0 0;
  color: var(--home-text-secondary);
  font-size: 0.9rem;
  line-height: 1.46;
}

.status-warning {
  border-color: #f6d3b3;
  background: #fff8f1;
}

.status-warning i {
  color: #c2410c;
}

.status-info {
  border-color: #bfdbfe;
  background: #eff6ff;
}

.status-info i {
  color: #1d4ed8;
}

.cta-group {
  display: grid;
  gap: 0.72rem;
}

.cta-button {
  width: 100%;
  min-height: 2.95rem;
  border-radius: 0.8rem;
  font-weight: 700;
}

.cta-primary {
  background: linear-gradient(135deg, var(--home-accent) 0%, var(--home-accent-hover) 100%);
  border-color: var(--home-accent);
  color: #ffffff;
  box-shadow: var(--home-primary-shadow);
}

.cta-primary:hover {
  background: var(--home-accent-hover);
  border-color: var(--home-accent-hover);
}

.cta-primary:focus {
  box-shadow: 0 0 0 3px var(--home-focus);
}

.cta-secondary {
  border-color: var(--home-border-strong);
  color: var(--home-text-primary);
  background: var(--home-secondary-bg);
}

.cta-secondary:hover {
  border-color: var(--home-accent);
  color: var(--home-accent);
  background: var(--home-secondary-hover-bg);
}

.continue-card {
  border: 1px solid var(--home-continue-border);
  border-radius: 0.9rem;
  padding: 0.85rem;
  background: var(--home-continue-bg);
}

.continue-meta {
  margin-bottom: 0.65rem;
}

.continue-label {
  margin: 0;
  color: var(--home-text-secondary);
  font-size: 0.8rem;
}

.continue-value {
  margin: 0.18rem 0 0;
  font-size: 1.02rem;
  font-weight: 700;
  color: var(--home-text-primary);
}

.continue-button {
  margin-top: 0.2rem;
}

.continue-card-mobile {
  border: 0;
  padding: 0;
  background: transparent;
}

.quick-actions {
  display: grid;
  gap: 0.6rem;
}

.quick-actions-mobile {
  gap: 0.22rem;
  margin-top: 0.2rem;
}

.quick-action-button.p-button {
  width: 100%;
  justify-content: flex-start;
  border-radius: 0.76rem;
  border-width: 1px;
  border-style: solid;
  border-color: var(--home-quick-border);
  color: var(--home-text-primary);
  background: var(--home-quick-bg);
  font-weight: 600;
  box-shadow: none;
}

.quick-action-button.p-button:not(:disabled):hover {
  border-color: var(--home-quick-hover-border);
  color: var(--home-accent);
  background: var(--home-quick-hover-bg);
}

.quick-action-button.p-button:focus {
  box-shadow: 0 0 0 3px var(--home-focus);
}

.quick-action-link.p-button {
  min-height: auto;
  padding: 0.38rem 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--home-accent);
  font-weight: 600;
}

.quick-action-link.p-button:not(:disabled):hover {
  border: 0;
  background: transparent;
  color: var(--home-accent-hover);
  text-decoration: underline;
}

.quick-action-link.p-button:focus {
  box-shadow: none;
  text-decoration: underline;
}

.signout-row {
  margin-top: 0.18rem;
}

.signout-button.p-button {
  padding: 0.16rem 0;
  color: var(--home-text-secondary);
  font-weight: 600;
}

.signout-button.p-button:not(:disabled):hover {
  color: var(--home-accent);
  background: transparent;
  text-decoration: underline;
}

.quick-stats-section {
  position: relative;
  z-index: 2;
  padding: 0 0 0.9rem;
}

.quick-stats-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0.46rem;
}

.quick-stat-tile {
  border: 1px solid var(--home-stats-tile-border);
  border-radius: 0.92rem;
  background: var(--home-stats-tile-bg);
  box-shadow: none;
  padding: 0.66rem 0.74rem 0.7rem;
}

.quick-stat-head {
  display: flex;
  align-items: center;
  gap: 0.44rem;
}

.quick-stat-icon {
  width: 1.38rem;
  height: 1.38rem;
  border-radius: 0.35rem;
  border: 1px solid currentColor;
  color: var(--home-accent);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
}

.quick-stat-icon i {
  font-size: 0.66rem;
  -webkit-text-stroke: 0.2px currentColor;
}

.quick-stat-label {
  margin: 0;
  color: var(--home-text-secondary);
  font-size: 0.66rem;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  font-weight: 700;
  line-height: 1.18;
}

.quick-stat-value {
  margin: 0.3rem 0 0;
  color: var(--home-stats-value);
  font-size: clamp(1.02rem, 4vw, 1.16rem);
  line-height: 1.2;
  font-weight: 700;
}

.quick-stat-latest .quick-stat-icon {
  color: #0ea5e9;
}

.quick-stat-distance .quick-stat-icon {
  color: #2563eb;
}

.quick-stat-moving .quick-stat-icon {
  color: #0f766e;
}

.features-section {
  position: relative;
  z-index: 2;
  padding: 2.2rem 0 2.8rem;
}

.section-header {
  margin-bottom: 1rem;
}

.section-header h2 {
  margin: 0;
  font-size: clamp(1.35rem, 3.9vw, 1.95rem);
  letter-spacing: -0.015em;
}

.section-header p {
  margin: 0.5rem 0 0;
  max-width: 56ch;
  color: var(--home-text-secondary);
  line-height: 1.5;
}

.feature-track {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0.8rem;
  padding-bottom: 0;
}

.feature-card {
  width: 100%;
  border: 1px solid var(--home-feature-border);
  border-radius: 0.95rem;
  background: var(--home-feature-card-bg);
  box-shadow: var(--home-shadow);
}

.mobile-feature-toggle {
  margin-top: 0.9rem;
  width: 100%;
  justify-content: center;
  border-radius: 0.76rem;
  border-color: var(--home-border-strong);
}

.feature-card :deep(.p-card-body) {
  padding: 1.02rem 1.08rem 1.06rem;
}

.feature-card :deep(.p-card-content) {
  padding: 0;
}

.feature-content {
  display: flex;
  align-items: flex-start;
  gap: 0.7rem;
}

.feature-icon {
  position: relative;
  width: 2.3rem;
  height: 2.3rem;
  border-radius: 0.7rem;
  background: var(--home-feature-icon-blob);
  border: 1px solid var(--home-feature-icon-blob-ring);
  box-shadow: var(--home-feature-icon-shadow);
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  overflow: hidden;
}

.feature-icon::before {
  content: '';
  position: absolute;
  inset: 0.16rem;
  border-radius: 0.52rem;
  background: var(--home-feature-icon-highlight);
  pointer-events: none;
}

.feature-icon i {
  position: relative;
  z-index: 1;
  font-size: 1.12rem;
  color: var(--home-feature-icon-color);
  text-shadow: var(--home-feature-icon-glyph-shadow);
  -webkit-text-stroke: var(--home-feature-icon-stroke);
}

.feature-text h3 {
  margin: 0;
  font-size: 1rem;
}

.feature-text p {
  margin: 0.34rem 0 0;
  color: var(--home-text-secondary);
  font-size: 0.9rem;
  line-height: 1.45;
}

@media (max-width: 767px) {
  .top-bar {
    padding: 0.85rem 0 0.55rem;
  }

  .hero-grid {
    gap: 0.85rem;
    padding: 0.35rem 0 1.35rem;
  }

  .hero-copy {
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;
  }

  .hero-logo {
    width: auto;
    height: 80px;
    transform: none;
    margin-bottom: calc(var(--home-logo-tag-gap) - 0.25rem);
  }

  .hero-eyebrow,
  .hero-title {
    text-align: center;
  }

  .hero-title {
    font-size: calc(var(--home-hero-title-size) * 0.82);
    max-width: 12.8ch;
    margin-left: auto;
    margin-right: auto;
  }

  .hero-panel {
    margin-top: 0.1rem;
  }

  .status-card {
    padding: 1.1rem 1rem 1.05rem;
  }

  .signed-in-card {
    gap: 0.75rem;
  }

  .signed-in-card .card-title {
    margin-bottom: 0.08rem;
  }

  .signed-in-card .continue-button {
    margin-top: 0;
  }

  .signout-row {
    margin-top: 0.04rem;
  }

  .quick-stats-section {
    padding: 0 0 0.78rem;
  }

  .quick-stat-tile {
    padding: 0.58rem 0.66rem 0.62rem;
  }

  .quick-stat-value {
    font-size: 1rem;
    line-height: 1.16;
  }

  .features-section {
    padding: 1.35rem 0 2rem;
  }

  .section-header {
    margin-bottom: 0.72rem;
  }

  .feature-track {
    gap: 0.72rem;
  }

  .mobile-feature-toggle {
    margin-top: 0.75rem;
  }
}

@media (min-width: 768px) {
  .home-container {
    padding: 0 1.25rem;
  }

  .hero-grid {
    gap: 1.4rem;
    padding-bottom: 2.8rem;
  }

  .status-card {
    padding: 1.35rem;
  }

  .quick-stats-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 0.62rem;
  }

  .quick-stat-value {
    font-size: 1.08rem;
  }

  .quick-actions {
    grid-template-columns: 1fr 1fr;
  }
}

@media (min-width: 1024px) {
  .top-bar {
    padding-top: 1.2rem;
  }

  .hero-grid {
    grid-template-columns: minmax(0, 6.75fr) minmax(26.75rem, 5.25fr);
    align-items: center;
    gap: clamp(4rem, 6vw, 5rem);
    padding-bottom: 3.2rem;
  }

  .hero-copy {
    padding-top: 0;
  }

  .status-card {
    min-height: 26.5rem;
    padding: 1.35rem 2rem 1.4rem;
  }

  .feature-track {
    display: grid;
    gap: 1rem;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .mobile-feature-toggle {
    display: none;
  }
}

@media (max-width: 430px) {
  .hero-title {
    max-width: 11.8ch;
  }

  .hero-logo {
    height: 80px;
  }

  .status-card {
    padding: 1rem;
    border-radius: 0.95rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .theme-button :deep(.p-button),
  .cta-button,
  .quick-action-button {
    transition: none !important;
  }
}

.p-dark .home-page {
  --home-hero-gradient: linear-gradient(155deg, #0b1220 0%, #101d34 45%, #0d213f 100%);
  --home-hero-glow: radial-gradient(ellipse at 18% 12%, rgba(59, 130, 246, 0.2) 0%, rgba(59, 130, 246, 0) 60%),
    radial-gradient(ellipse at 84% 86%, rgba(14, 165, 233, 0.18) 0%, rgba(14, 165, 233, 0) 64%);
  --home-continue-bg: linear-gradient(155deg, rgba(37, 99, 235, 0.22) 0%, rgba(14, 165, 233, 0.12) 100%);
  --home-continue-border: rgba(147, 197, 253, 0.32);
  --home-primary-shadow: 0 12px 26px rgba(30, 64, 175, 0.46);
  --home-secondary-bg: rgba(59, 130, 246, 0.12);
  --home-secondary-hover-bg: rgba(96, 165, 250, 0.22);
  --home-quick-bg: rgba(15, 23, 42, 0.7);
  --home-quick-hover-bg: rgba(30, 64, 175, 0.24);
  --home-quick-border: rgba(148, 163, 184, 0.55);
  --home-quick-hover-border: rgba(96, 165, 250, 0.75);
  --home-stats-tile-bg: rgba(15, 23, 42, 0.9);
  --home-stats-tile-border: rgba(148, 163, 184, 0.52);
  --home-stats-value: #e2e8f0;
  --home-feature-card-bg: rgba(15, 23, 42, 0.72);
  --home-feature-border: rgba(148, 163, 184, 0.78);
  --home-feature-icon-blob: linear-gradient(150deg, rgba(30, 64, 175, 0.38) 0%, rgba(30, 64, 175, 0.22) 100%);
  --home-feature-icon-blob-ring: rgba(147, 197, 253, 0.65);
  --home-feature-icon-shadow: 0 8px 22px rgba(30, 64, 175, 0.45);
  --home-feature-icon-color: #e2e8f0;
  --home-feature-icon-highlight: rgba(15, 23, 42, 0.24);
  --home-feature-icon-stroke: 0.35px currentColor;
  --home-feature-icon-glyph-shadow: 0 1px 0 rgba(15, 23, 42, 0.55), 0 0 8px rgba(147, 197, 253, 0.35);
}

.p-dark .home-page::before {
  background: var(--home-hero-gradient);
}

.p-dark .home-page::after {
  background: var(--home-hero-glow);
}

.p-dark .cta-secondary {
  border-color: rgba(148, 163, 184, 0.65);
  color: var(--home-text-primary);
}

.p-dark .quick-action-button.p-button {
  border-color: var(--home-quick-border);
  color: #e2e8f0;
}

.p-dark .quick-action-button.p-button:not(:disabled):hover {
  color: #bfdbfe;
}

.p-dark .signout-button.p-button {
  color: #94a3b8;
}

.p-dark .signout-button.p-button:not(:disabled):hover {
  color: #bfdbfe;
}

.p-dark .quick-stat-latest .quick-stat-icon {
  color: #38bdf8;
}

.p-dark .quick-stat-distance .quick-stat-icon {
  color: #60a5fa;
}

.p-dark .quick-stat-moving .quick-stat-icon {
  color: #2dd4bf;
}
</style>

<template>
  <AppLayout>
    <div class="admin-dashboard">
      <Breadcrumb :home="breadcrumbHome" :model="breadcrumbItems" class="admin-breadcrumb" />

      <DemoReadOnlyBanner />

      <div class="page-header">
        <h1>Administration</h1>
        <p class="text-muted">Instance health, operations, and access management</p>
      </div>

      <div class="stats-header">
        <h2 class="stats-title">Instance Health</h2>
        <Button
          icon="pi pi-refresh"
          label="Refresh"
          size="small"
          text
          @click="loadStats"
          :loading="loading"
        />
      </div>

      <div v-if="healthLoaded" class="health-summary" :class="{ 'health-summary--warning': healthWarnings.length }">
        <i :class="healthWarnings.length ? 'pi pi-exclamation-triangle' : 'pi pi-check-circle'" />
        <span>{{ healthWarnings.length ? `${healthWarnings.length} item${healthWarnings.length === 1 ? '' : 's'} need attention` : 'No configuration issues detected' }}</span>
      </div>

      <div v-if="healthLoaded" class="health-grid">
        <Card v-for="card in healthCards" :key="card.label" class="health-card">
          <template #content>
            <div class="health-card-header">
              <span><i :class="card.icon" /> {{ card.label }}</span>
              <div class="health-card-tags">
                <Tag v-if="card.warning" :value="card.warning" severity="warn" />
                <Tag :value="card.statusLabel || card.status" :severity="card.severity" />
              </div>
            </div>
            <div v-if="card.providers?.length" class="geocoding-provider-list">
              <div v-for="provider in card.providers" :key="provider.name" class="geocoding-provider-row">
                <div>
                  <div class="geocoding-provider-name">
                    <strong>{{ provider.displayName }}</strong>
                    <Tag v-if="provider.primary" value="PRIMARY" severity="info" class="geocoding-provider-role" />
                    <Tag v-if="provider.fallback" value="FALLBACK" severity="warn" class="geocoding-provider-role" />
                  </div>
                  <small>{{ provider.detail }}</small>
                </div>
                <div class="geocoding-provider-tags">
                  <Tag :value="provider.statusLabel" :severity="provider.severity" />
                </div>
              </div>
            </div>
            <p v-else>{{ card.detail }}</p>
            <Button :label="card.action" size="small" text @click="router.push(card.to)" />
          </template>
        </Card>
      </div>
      <Message v-else-if="!loading" severity="warn" :closable="false">Health details are temporarily unavailable. Refresh to try again.</Message>

      <div class="stats-header usage-header">
        <h2 class="stats-title">Instance Usage</h2>
      </div>

      <div class="stats-grid">
        <!-- Quick Stats -->
        <Card class="stat-card">
          <template #content>
            <div class="stat-card-content">
              <div>
                <p class="stat-label">Total Users</p>
                <h3 class="stat-value">
                  <Skeleton v-if="loading" width="3rem" height="2rem" />
                  <template v-else>{{ stats.totalUsers }}</template>
                </h3>
              </div>
              <i class="pi pi-users stat-icon stat-icon-primary"></i>
            </div>
          </template>
        </Card>

        <Card class="stat-card">
          <template #content>
            <div class="stat-card-content">
              <div>
                <p class="stat-label">Active Users (24h)</p>
                <h3 class="stat-value">
                  <Skeleton v-if="loading" width="3rem" height="2rem" />
                  <template v-else>{{ stats.activeUsers24h }}</template>
                </h3>
              </div>
              <i class="pi pi-check-circle stat-icon stat-icon-green"></i>
            </div>
          </template>
        </Card>

        <Card class="stat-card">
          <template #content>
            <div class="stat-card-content">
              <div>
                <p class="stat-label">Total GPS Points</p>
                <h3 class="stat-value">
                  <Skeleton v-if="loading" width="4rem" height="2rem" />
                  <template v-else>{{ formatNumber(stats.totalGpsPoints) }}</template>
                </h3>
              </div>
              <i class="pi pi-map-marker stat-icon stat-icon-blue"></i>
            </div>
          </template>
        </Card>

        <Card class="stat-card">
          <template #content>
            <div class="stat-card-content">
              <div>
                <p class="stat-label">GPS Activity (24h)</p>
                <h3 class="stat-value">
                  <Skeleton v-if="loading" width="4rem" height="2rem" />
                  <template v-else>{{ formatNumber(stats.gpsActivity24h) }}</template>
                </h3>
              </div>
              <i class="pi pi-chart-line stat-icon stat-icon-purple"></i>
            </div>
          </template>
        </Card>
      </div>

      <div class="operational-health-grid">
      <Card class="weather-health-card">
        <template #content>
          <details class="operational-health-details">
            <summary class="weather-health-header">
              <div>
                <p class="stat-label">Weather Integration Health</p>
                <h3 class="weather-health-title">
                  <Skeleton v-if="loading" width="8rem" height="1.75rem" />
                  <template v-else>{{ weatherHealthLabel }}</template>
                </h3>
              </div>
              <Tag v-if="!loading" :value="weatherHealthBadge" :severity="weatherHealthSeverity" />
            </summary>
            <div v-if="loading" class="weather-health-details">
              <Skeleton width="100%" height="1rem" />
              <Skeleton width="75%" height="1rem" />
            </div>
            <div v-else class="weather-health-details">
              <div class="weather-health-row"><span>Provider</span><strong>{{ stats.weatherStatus?.provider || 'OPEN_METEO' }}</strong></div>
              <div class="weather-health-row"><span>Daily quota</span><strong>{{ formatNumber(stats.weatherStatus?.requestsRemainingToday || 0) }} remaining</strong></div>
              <div class="weather-health-row"><span>Pending targets</span><strong>{{ formatNumber(weatherPendingTargets) }}</strong></div>
              <div class="weather-health-row"><span>Historical ranges</span><strong>{{ weatherBacklogText }}</strong></div>
              <div class="weather-health-row"><span>Claimable pending</span><strong>{{ formatNumber(weatherClaimablePendingTargets) }}</strong></div>
              <div class="weather-health-row"><span>Fetch status</span><strong class="weather-health-status">{{ weatherFetchStatus }}</strong></div>
              <div v-if="weatherProviderHealth?.lastErrorMessage" class="weather-health-message">{{ weatherProviderHealth.lastErrorMessage }}</div>
              <div v-if="weatherNextAction" class="weather-health-row"><span>{{ weatherNextAction.label }}</span><strong>{{ weatherNextAction.value }}</strong></div>
            </div>
          </details>
        </template>
      </Card>

      <Card class="weather-health-card">
        <template #content>
          <details class="operational-health-details">
            <summary class="weather-health-header">
              <div>
                <p class="stat-label">Map Matching Integration Health</p>
                <h3 class="weather-health-title">{{ mapMatchingHealthLabel }}</h3>
              </div>
              <Tag :value="mapMatchingHealthBadge" :severity="mapMatchingHealthSeverity" />
            </summary>
            <div class="weather-health-details">
              <div class="weather-health-row"><span>Provider</span><strong>{{ mapMatchingHealth.provider || 'Valhalla' }}</strong></div>
              <div class="weather-health-row"><span>Queue</span><strong>{{ formatNumber(mapMatchingQueue.queued || 0) }} queued, {{ formatNumber(mapMatchingQueue.processing || 0) }} processing</strong></div>
              <div class="weather-health-row"><span>Historical backfill</span><strong>{{ mapMatchingBackfill.enabled ? `${formatNumber(mapMatchingBackfill.remainingTrips || 0)} trips remaining` : 'Disabled' }}</strong></div>
              <div class="weather-health-row"><span>Last activity</span><strong>{{ formatDateTime(mapMatchingWorker.lastActivityAt) }}</strong></div>
              <div v-if="mapMatchingProviderHealth?.lastSuccessAt" class="weather-health-row"><span>Last provider success</span><strong>{{ formatDateTime(mapMatchingProviderHealth.lastSuccessAt) }}</strong></div>
              <div v-if="mapMatchingProviderHealth?.lastErrorMessage" class="weather-health-message">{{ mapMatchingProviderHealth.lastErrorMessage }}</div>
              <div v-if="mapMatchingWorker.lastError" class="weather-health-message">{{ mapMatchingWorker.lastError }}</div>
            </div>
          </details>
        </template>
      </Card>
      </div>

    <!-- Quick Actions -->
    <div class="quick-actions-grid">
      <!-- People & Access -->
      <Card class="actions-card">
        <template #content>
          <h3 class="actions-title">
            <i class="pi pi-users actions-icon"></i>
            People & Access
          </h3>
          <div class="actions-buttons">
            <router-link to="/app/admin/users" class="no-underline">
              <Button label="Manage Users" icon="pi pi-users" class="action-button" />
            </router-link>
            <router-link to="/app/admin/invitations" class="no-underline">
              <Button label="User Invitations" icon="pi pi-send" severity="secondary" class="action-button" />
            </router-link>
            <router-link to="/app/admin/oidc-providers" class="no-underline">
              <Button label="OIDC Providers" icon="pi pi-key" severity="secondary" class="action-button" />
            </router-link>
            <router-link to="/app/admin/audit-logs" class="no-underline">
              <Button label="Audit Logs" icon="pi pi-history" severity="secondary" class="action-button" />
            </router-link>
          </div>
        </template>
      </Card>

      <!-- Operations -->
      <Card class="actions-card">
        <template #content>
          <h3 class="actions-title">
            <i class="pi pi-database actions-icon"></i>
            Operations
          </h3>
          <div class="actions-buttons">
            <router-link to="/app/admin/backups" class="no-underline">
              <Button label="Backups & Restore" icon="pi pi-database" class="action-button" />
            </router-link>
            <router-link to="/app/admin/timeline-regeneration-campaigns" class="no-underline">
              <Button label="Timeline Regeneration Campaigns" icon="pi pi-refresh" severity="secondary" class="action-button" />
            </router-link>
          </div>
        </template>
      </Card>
      <Card class="actions-card">
        <template #content>
          <h3 class="actions-title"><i class="pi pi-cog actions-icon"></i> Configuration</h3>
          <div class="actions-buttons">
            <router-link to="/app/admin/settings" class="no-underline">
              <Button label="System Settings" icon="pi pi-cog" class="action-button" />
            </router-link>
          </div>
        </template>
      </Card>
    </div>
    </div>
  </AppLayout>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Card from 'primevue/card'
import Skeleton from 'primevue/skeleton'
import Breadcrumb from 'primevue/breadcrumb'
import Tag from 'primevue/tag'
import Message from 'primevue/message'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import DemoReadOnlyBanner from '@/components/admin/DemoReadOnlyBanner.vue'
import { useAdminStore } from '@/stores/admin'

const adminService = useAdminStore()
const router = useRouter()

const breadcrumbHome = ref({
  icon: 'pi pi-home',
  command: () => router.push('/')
})
const breadcrumbItems = ref([
  {
    label: 'Administration',
    command: () => router.push('/app/admin')
  }
])

const stats = ref({
  totalUsers: 0,
  activeUsers24h: 0,
  totalGpsPoints: 0,
  gpsActivity24h: 0,
  weatherStatus: null
})

const loading = ref(false)
const healthLoaded = ref(false)
const GPS_INGESTION_STALE_AFTER_MS = 24 * 60 * 60 * 1000

const formatNumber = (num) => {
  if (num >= 1000000) {
    return (num / 1000000).toFixed(1) + 'M'
  } else if (num >= 1000) {
    return (num / 1000).toFixed(1) + 'K'
  }
  return num.toString()
}

const weatherProviderHealth = computed(() => stats.value.weatherStatus?.providerHealth || null)
const weatherProcessing = computed(() => stats.value.weatherStatus?.processing || null)
const weatherReconciliation = computed(() => stats.value.weatherStatus?.reconciliation || null)
const weatherPendingTargets = computed(() => stats.value.weatherStatus?.targetsByStatus?.PENDING || 0)
const weatherClaimablePendingTargets = computed(() => stats.value.weatherStatus?.claimablePendingTargets || 0)
const weatherFetchStatus = computed(() => stats.value.weatherStatus?.fetchBlockedReason || 'Ready')
const weatherBacklogText = computed(() => {
  const backlog = weatherReconciliation.value
  if (!backlog || !backlog.pendingUserRanges) return 'Clear'
  return `${formatNumber(backlog.pendingUserRanges)} waiting`
})

const hasWeatherStatus = computed(() => !!stats.value.weatherStatus)

const weatherHealthBadge = computed(() => weatherProcessing.value?.running ? 'PROCESSING' : weatherProviderHealth.value?.status || 'UNKNOWN')

const weatherHealthLabel = computed(() => {
  const status = weatherProviderHealth.value?.status
  if (weatherProcessing.value?.running) {
    return 'Processing weather'
  }
  switch (status) {
    case 'HEALTHY':
      return 'Operational'
    case 'PROVIDER_QUOTA_EXCEEDED':
      return 'Open-Meteo quota reached'
    case 'INTERNAL_QUOTA_EXCEEDED':
      return 'Internal quota reached'
    case 'PROVIDER_UNAVAILABLE':
      return 'Provider unavailable'
    case 'CONFIG_ERROR':
      return 'Configuration error'
    default:
      if (!hasWeatherStatus.value) return 'Unknown'
      return stats.value.weatherStatus?.enabled ? 'Unknown' : 'Disabled'
  }
})

const weatherHealthSeverity = computed(() => {
  if (weatherProcessing.value?.running) return 'info'
  const status = weatherProviderHealth.value?.status
  if (status === 'HEALTHY') return 'success'
  if (status === 'PROVIDER_QUOTA_EXCEEDED' || status === 'INTERNAL_QUOTA_EXCEEDED') return 'warn'
  if (status === 'PROVIDER_UNAVAILABLE' || status === 'CONFIG_ERROR') return 'danger'
  return 'secondary'
})

const weatherNextAction = computed(() => {
  const health = weatherProviderHealth.value
  if (!health) return null
  if (health.circuitOpenUntil && isFutureTimestamp(health.circuitOpenUntil)) {
    return { label: 'Paused until', value: formatDateTime(health.circuitOpenUntil) }
  }
  if (health.nextProbeAt && isFutureTimestamp(health.nextProbeAt)) {
    return { label: 'Next probe', value: formatDateTime(health.nextProbeAt) }
  }
  if (health.lastSuccessAt) {
    return { label: 'Last success', value: formatDateTime(health.lastSuccessAt) }
  }
  return null
})

const health = computed(() => stats.value.health || {})
const SECURITY_WARNING_MESSAGES = {
  SCHEDULED_BACKUPS_DISABLED: 'Scheduled backups are disabled'
}
const securityWarnings = computed(() => (health.value.security?.warnings || [])
  .map(code => SECURITY_WARNING_MESSAGES[code] || code))
const geocodingHealth = computed(() => health.value.geocoding || { status: 'UNKNOWN', providers: [] })
const mapMatchingHealth = computed(() => health.value.mapMatching || {})
const mapMatchingProviderHealth = computed(() => mapMatchingHealth.value.providerHealth || null)
const mapMatchingWorker = computed(() => mapMatchingHealth.value.status?.worker || {})
const mapMatchingQueue = computed(() => mapMatchingHealth.value.status?.queue || {})
const mapMatchingBackfill = computed(() => mapMatchingHealth.value.status?.backfill || {})
const mapMatchingHealthBadge = computed(() => {
  if (!mapMatchingHealth.value.enabled) return 'DISABLED'
  if (!mapMatchingHealth.value.configured) return 'NOT CONFIGURED'
  if (mapMatchingWorker.value.lastError) return 'BLOCKED'
  return mapMatchingProviderHealth.value?.status || 'UNKNOWN'
})
const mapMatchingHealthLabel = computed(() => {
  const badge = mapMatchingHealthBadge.value
  if (badge === 'HEALTHY') return mapMatchingWorker.value.running ? 'Processing map matching' : 'Operational'
  if (badge === 'UNKNOWN') return 'No provider request observed'
  return badge.replaceAll('_', ' ')
})
const mapMatchingHealthSeverity = computed(() => {
  const badge = mapMatchingHealthBadge.value
  if (badge === 'DISABLED' || badge === 'UNKNOWN') return 'secondary'
  if (badge === 'HEALTHY') return mapMatchingWorker.value.running ? 'info' : 'success'
  return badge === 'PROVIDER_UNAVAILABLE' || badge === 'CONFIG_ERROR' ? 'danger' : 'warn'
})
const isGpsIngestionStale = computed(() => {
  const latestReceivedAt = health.value.ingestion?.latestReceivedAt
  const receivedAt = new Date(latestReceivedAt).getTime()
  return Number.isFinite(receivedAt) && Date.now() - receivedAt > GPS_INGESTION_STALE_AFTER_MS
})
const healthWarnings = computed(() => {
  if (!healthLoaded.value) return []
  const warnings = [...securityWarnings.value]
  if (!health.value.backup?.latestBackupAt) warnings.push('No local backup found')
  else if (health.value.backup?.stale) warnings.push(`Latest backup is older than the configured ${health.value.backup.healthMaxAgeDays}-day limit`)
  if (!health.value.ingestion?.latestReceivedAt) warnings.push('No GPS data received')
  else if (isGpsIngestionStale.value) warnings.push('GPS ingestion has been inactive for over 24 hours')
  const geocodingStatus = geocodingHealth.value.status
  const fallbackConfigured = geocodingHealth.value.providers?.some(provider => provider.fallback)
  if (['NOT_CONFIGURED', 'DEGRADED', 'CIRCUIT_OPEN'].includes(geocodingStatus)) {
    warnings.push('Reverse geocoding needs attention')
  } else if (!fallbackConfigured) {
    warnings.push('No reverse-geocoding fallback configured')
  }
  if (mapMatchingHealth.value.enabled && (!mapMatchingHealth.value.configured
      || mapMatchingWorker.value.lastError
      || (mapMatchingProviderHealth.value?.status && mapMatchingProviderHealth.value.status !== 'HEALTHY'))) {
    warnings.push('Map matching needs attention')
  }
  return warnings
})
const geocodingSeverity = (status) => ({
  HEALTHY: 'success',
  UNKNOWN: 'secondary',
  DEGRADED: 'warn',
  CIRCUIT_OPEN: 'danger',
  NOT_CONFIGURED: 'warn',
}[status] || 'secondary')
const geocodingStatusLabel = (status) => status.replaceAll('_', ' ')
const geocodingProviderDetail = (provider) => {
  if (provider.status === 'UNKNOWN') return 'No provider requests observed.'
  if (provider.status === 'CIRCUIT_OPEN') return `Circuit breaker was last observed open ${formatDateTime(provider.circuitBreakerObservedOpenAt)}.`
  if (provider.status === 'DEGRADED') return provider.lastErrorMessage || `Last failure ${formatDateTime(provider.lastFailureAt)}.`
  return `Last success ${formatDateTime(provider.lastSuccessAt)}.`
}
const healthCards = computed(() => {
  const backup = health.value.backup || {}
  const ingestion = health.value.ingestion || {}
  const geocoding = geocodingHealth.value
  const geocodingStatus = geocoding.status || 'UNKNOWN'
  const geocodingProviders = Array.isArray(geocoding.providers) ? geocoding.providers : []
  const weather = stats.value.weatherStatus || {}
  const lastReceived = ingestion.latestReceivedAt
  const backupReady = Boolean(backup.latestBackupAt)
  const backupStale = backup.stale === true
  const weatherProblem = weather.enabled && weather.providerHealth?.status && weather.providerHealth.status !== 'HEALTHY'
  const mapMatchingBadge = mapMatchingHealthBadge.value
  return [
    {
      label: 'Backups', icon: 'pi pi-database', to: '/app/admin/backups', action: 'Open backups',
      status: backupReady ? backupStale ? 'STALE' : 'READY' : 'ACTION NEEDED', severity: backupReady && !backupStale ? 'success' : 'warn',
      detail: backupReady ? `Latest backup ${formatDateTime(backup.latestBackupAt)}${backupStale ? ` (older than ${backup.healthMaxAgeDays} days)` : ''}` : 'No local backup found yet.'
    },
    {
      label: 'GPS Ingestion', icon: 'pi pi-map-marker', to: '/app/admin/users', action: 'Manage users',
      status: lastReceived ? isGpsIngestionStale.value ? 'STALE' : 'RECEIVING' : 'NO DATA', severity: lastReceived && !isGpsIngestionStale.value ? 'success' : 'warn',
      detail: lastReceived ? `Latest point received ${formatDateTime(lastReceived)}` : 'No GPS points have been received.'
    },
    {
      label: 'Reverse Geocoding', icon: 'pi pi-map-marker', to: '/app/admin/settings?tab=geocoding', action: 'Open geocoding settings',
      status: geocodingStatus, statusLabel: geocodingStatusLabel(geocodingStatus), severity: geocodingSeverity(geocodingStatus),
      warning: geocodingStatus === 'NOT_CONFIGURED' || geocodingProviders.some(provider => provider.fallback) ? null : 'NO FALLBACK',
      providers: [...geocodingProviders].sort((a, b) => Number(b.primary) - Number(a.primary) || Number(b.fallback) - Number(a.fallback)).map(provider => ({
        ...provider,
        statusLabel: geocodingStatusLabel(provider.status),
        severity: geocodingSeverity(provider.status),
        detail: geocodingProviderDetail(provider)
      })),
      detail: geocodingStatus === 'NOT_CONFIGURED' ? 'No enabled providers configured.' : ''
    },
    {
      label: 'Weather', icon: 'pi pi-cloud', to: '/app/admin/settings?tab=weather', action: 'Open weather settings',
      status: weatherProblem ? 'ATTENTION' : weather.enabled === false ? 'DISABLED' : 'READY', severity: weatherProblem ? 'warn' : weather.enabled === false ? 'secondary' : 'success',
      detail: weatherProblem ? (weather.providerHealth?.status || 'Provider needs attention') : weather.enabled === false ? 'Weather enrichment is disabled.' : 'Weather provider is available.'
    },
    {
      label: 'Map Matching', icon: 'pi pi-directions-alt', to: '/app/admin/settings?tab=map-matching', action: 'Open map matching settings',
      status: mapMatchingBadge, statusLabel: mapMatchingBadge.replaceAll('_', ' '), severity: mapMatchingHealthSeverity.value,
      detail: !mapMatchingHealth.value.enabled ? 'Optional map matching is disabled.'
        : !mapMatchingHealth.value.configured ? 'Valhalla is not configured.'
          : mapMatchingProviderHealth.value?.lastSuccessAt ? `Last Valhalla success ${formatDateTime(mapMatchingProviderHealth.value.lastSuccessAt)}`
            : 'No Valhalla requests observed yet.'
    },
    {
      label: 'Security', icon: 'pi pi-shield', to: '/app/admin/settings?tab=authentication', action: 'Open security settings',
      status: securityWarnings.value.length ? 'ACTION NEEDED' : 'READY', severity: securityWarnings.value.length ? 'warn' : 'success',
      detail: securityWarnings.value.length ? securityWarnings.value[0] : 'No configuration warnings.'
    }
  ]
})

const isFutureTimestamp = (value) => {
  if (!value) return false
  const timestamp = new Date(value).getTime()
  return Number.isFinite(timestamp) && timestamp > Date.now()
}

const formatDateTime = (value) => {
  if (!value) return ''
  try {
    return new Intl.DateTimeFormat(undefined, {
      dateStyle: 'medium',
      timeStyle: 'short'
    }).format(new Date(value))
  } catch {
    return value
  }
}

const loadStats = async () => {
  loading.value = true
  try {
    const dashboardStats = await adminService.getDashboardStats()
    stats.value = dashboardStats
    healthLoaded.value = !!dashboardStats?.health
  } catch (error) {
    console.error('Failed to load admin stats:', error)
    // Keep existing values on error
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadStats()
})
</script>

<style scoped>
.admin-dashboard {
  padding: 1.5rem;
}

.admin-breadcrumb {
  margin-bottom: 1.5rem;
}

.page-header {
  margin-bottom: 1.5rem;
}

.page-header h1 {
  margin: 0;
  font-size: 1.75rem;
  color: var(--text-color);
}

.text-muted {
  color: var(--text-color-secondary);
  margin: 0;
}

/* Stats Header */
.stats-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.health-summary { display: flex; align-items: center; gap: .6rem; margin-bottom: 1rem; padding: .8rem 1rem; border-radius: var(--gp-radius-medium); background: var(--gp-success-light, #ecfdf5); color: var(--gp-success-dark, #166534); }
.health-summary--warning { background: var(--gp-warning-light, #fff7ed); color: var(--gp-warning-dark, #9a3412); }
.health-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 1rem; margin-bottom: 2rem; }
.health-card-header { display: flex; align-items: center; justify-content: space-between; gap: .5rem; font-weight: 600; }
.health-card-tags { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: .25rem; }
.health-card p { min-height: 2.8rem; margin: 1rem 0 .5rem; color: var(--text-color-secondary); font-size: .9rem; line-height: 1.4; }
.geocoding-provider-list { display: grid; gap: .6rem; margin: 1rem 0 .5rem; }
.geocoding-provider-row { display: flex; align-items: flex-start; justify-content: space-between; gap: .5rem; color: var(--text-color-secondary); font-size: .8rem; }
.geocoding-provider-name { display: flex; align-items: center; gap: .35rem; }
.geocoding-provider-row strong, .geocoding-provider-row small { display: block; }
.geocoding-provider-row strong { color: var(--text-color); }
.geocoding-provider-tags { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: .25rem; }
.geocoding-provider-role { font-size: .65rem; padding: .1rem .3rem; }
.usage-header { margin-top: .5rem; }

.stats-title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text-color);
}

/* Stats Grid */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1rem;
}

@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .health-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }
  .health-grid { grid-template-columns: 1fr; }
}

/* Stat Cards */
.stat-card {
  height: 100%;
}

.stat-card :deep(.p-card-body) {
  padding: 1.25rem;
}

.stat-card :deep(.p-card-content) {
  padding: 0;
}

.stat-card-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stat-label {
  margin: 0 0 0.5rem 0;
  font-size: 0.875rem;
  color: var(--text-color-secondary);
}

.stat-value {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--text-color);
}

.stat-icon {
  font-size: 2.5rem;
  opacity: 0.8;
}

.stat-icon-primary {
  color: var(--primary-color);
}

.stat-icon-orange {
  color: var(--orange-500);
}

.stat-icon-green {
  color: var(--green-500);
}

.stat-icon-blue {
  color: var(--blue-500);
}

.stat-icon-purple {
  color: var(--purple-500);
}

.operational-health-grid {
  margin-top: 1.5rem;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
}

.weather-health-card :deep(.p-card-body) {
  padding: 1.25rem;
}

.weather-health-card :deep(.p-card-content) {
  padding: 0;
}

.weather-health-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.operational-health-details summary {
  list-style: none;
  cursor: pointer;
}

.operational-health-details summary::-webkit-details-marker {
  display: none;
}

.operational-health-details summary::after {
  content: '⌄';
  color: var(--text-color-secondary);
  margin-left: auto;
}

.operational-health-details[open] summary::after {
  transform: rotate(180deg);
}

.weather-health-title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text-color);
}

.weather-health-details {
  margin-top: 1rem;
  display: grid;
  gap: 0.75rem;
}

.weather-health-row {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  color: var(--text-color-secondary);
}

.weather-health-row strong {
  color: var(--text-color);
  text-align: right;
}

.weather-health-status {
  max-width: 420px;
  overflow-wrap: anywhere;
}

.weather-health-message {
  padding: 0.75rem;
  border: 1px solid var(--surface-border);
  border-radius: 6px;
  color: var(--text-color);
  background: var(--surface-ground);
  overflow-wrap: anywhere;
}

/* Quick Actions Grid */
.quick-actions-grid {
  margin-top: 1.5rem;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
}

@media (max-width: 768px) {
  .operational-health-grid { grid-template-columns: 1fr; }
  .quick-actions-grid {
    grid-template-columns: 1fr;
  }
}

/* Actions Card */
.actions-card {
  height: 100%;
}

.actions-card :deep(.p-card-body) {
  padding: 1.25rem;
}

.actions-card :deep(.p-card-content) {
  padding: 0;
}

.actions-title {
  margin: 0 0 1rem 0;
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--text-color);
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.actions-icon {
  color: var(--primary-color);
  font-size: 1.25rem;
}

.actions-buttons {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.action-button {
  width: 100%;
  justify-content: flex-start;
}

.no-underline {
  text-decoration: none;
}
</style>

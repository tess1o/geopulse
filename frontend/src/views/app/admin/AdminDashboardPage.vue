<template>
  <AppLayout>
    <div class="admin-dashboard">
      <Breadcrumb :home="breadcrumbHome" :model="breadcrumbItems" class="admin-breadcrumb" />

      <DemoReadOnlyBanner />

      <div class="page-header">
        <h1>{{ t('admin.dashboardPage.title') }}</h1>
        <p class="text-muted">{{ t('admin.dashboardPage.subtitle') }}</p>
      </div>

      <div class="stats-header">
        <h2 class="stats-title">{{ t('admin.dashboardPage.instanceHealth') }}</h2>
        <Button
          icon="pi pi-refresh"
          :label="t('admin.dashboardPage.refresh')"
          size="small"
          text
          @click="loadStats"
          :loading="loading"
        />
      </div>

      <div v-if="healthLoaded" class="health-summary" :class="{ 'health-summary--warning': healthWarnings.length }">
        <i :class="healthWarnings.length ? 'pi pi-exclamation-triangle' : 'pi pi-check-circle'" />
        <span>{{ healthWarnings.length ? t('admin.dashboardPage.itemsNeedAttention', { count: healthWarnings.length }, healthWarnings.length) : t('admin.dashboardPage.noConfigurationIssues') }}</span>
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
                    <Tag v-if="provider.primary" :value="t('admin.dashboardPage.primaryBadge')" severity="info" class="geocoding-provider-role" />
                    <Tag v-if="provider.fallback" :value="t('admin.dashboardPage.fallbackBadge')" severity="warn" class="geocoding-provider-role" />
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
      <Message v-else-if="!loading" severity="warn" :closable="false">{{ t('admin.dashboardPage.healthUnavailable') }}</Message>

      <div class="stats-header usage-header">
        <h2 class="stats-title">{{ t('admin.dashboardPage.instanceUsage') }}</h2>
      </div>

      <div class="stats-grid">
        <!-- Quick Stats -->
        <Card class="stat-card">
          <template #content>
            <div class="stat-card-content">
              <div>
                <p class="stat-label">{{ t('admin.dashboardPage.totalUsers') }}</p>
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
                <p class="stat-label">{{ t('admin.dashboardPage.activeUsers24h') }}</p>
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
                <p class="stat-label">{{ t('admin.dashboardPage.totalGpsPoints') }}</p>
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
                <p class="stat-label">{{ t('admin.dashboardPage.gpsActivity24h') }}</p>
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
                <p class="stat-label">{{ t('admin.dashboardPage.weatherIntegrationHealth') }}</p>
                <h3 class="weather-health-title">
                  <Skeleton v-if="loading" width="8rem" height="1.75rem" />
                  <template v-else>{{ weatherHealthLabel }}</template>
                </h3>
              </div>
              <Tag v-if="!loading" :value="weatherHealthLabel" :severity="weatherHealthSeverity" />
            </summary>
            <div v-if="loading" class="weather-health-details">
              <Skeleton width="100%" height="1rem" />
              <Skeleton width="75%" height="1rem" />
            </div>
            <div v-else class="weather-health-details">
              <div class="weather-health-row"><span>{{ t('admin.dashboardPage.provider') }}</span><strong>{{ stats.weatherStatus?.provider || 'OPEN_METEO' }}</strong></div>
              <div class="weather-health-row"><span>{{ t('admin.dashboardPage.dailyQuota') }}</span><strong>{{ t('admin.dashboardPage.remaining', { count: formatNumber(stats.weatherStatus?.requestsRemainingToday || 0) }) }}</strong></div>
              <div class="weather-health-row"><span>{{ t('admin.dashboardPage.pendingTargets') }}</span><strong>{{ formatNumber(weatherPendingTargets) }}</strong></div>
              <div class="weather-health-row"><span>{{ t('admin.dashboardPage.historicalRanges') }}</span><strong>{{ weatherBacklogText }}</strong></div>
              <div class="weather-health-row"><span>{{ t('admin.dashboardPage.claimablePending') }}</span><strong>{{ formatNumber(weatherClaimablePendingTargets) }}</strong></div>
              <div class="weather-health-row"><span>{{ t('admin.dashboardPage.fetchStatus') }}</span><strong class="weather-health-status">{{ weatherFetchStatus }}</strong></div>
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
                <p class="stat-label">{{ t('admin.dashboardPage.mapMatchingIntegrationHealth') }}</p>
                <h3 class="weather-health-title">{{ mapMatchingHealthLabel }}</h3>
              </div>
              <Tag :value="mapMatchingHealthLabel" :severity="mapMatchingHealthSeverity" />
            </summary>
            <div class="weather-health-details">
              <div class="weather-health-row"><span>{{ t('admin.dashboardPage.provider') }}</span><strong>{{ mapMatchingHealth.provider || 'Valhalla' }}</strong></div>
              <div class="weather-health-row"><span>{{ t('admin.dashboardPage.queue') }}</span><strong>{{ t('admin.dashboardPage.queuedProcessing', { queued: formatNumber(mapMatchingQueue.queued || 0), processing: formatNumber(mapMatchingQueue.processing || 0) }) }}</strong></div>
              <div class="weather-health-row"><span>{{ t('admin.dashboardPage.historicalBackfill') }}</span><strong>{{ mapMatchingBackfill.enabled ? t('admin.dashboardPage.tripsRemaining', { count: formatNumber(mapMatchingBackfill.remainingTrips || 0) }) : t('admin.dashboardPage.disabled') }}</strong></div>
              <div class="weather-health-row"><span>{{ t('admin.dashboardPage.lastActivity') }}</span><strong>{{ formatDateTime(mapMatchingWorker.lastActivityAt) }}</strong></div>
              <div v-if="mapMatchingProviderHealth?.lastSuccessAt" class="weather-health-row"><span>{{ t('admin.dashboardPage.lastProviderSuccess') }}</span><strong>{{ formatDateTime(mapMatchingProviderHealth.lastSuccessAt) }}</strong></div>
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
            {{ t('admin.dashboardPage.peopleAccess') }}
          </h3>
          <div class="actions-buttons">
            <router-link to="/app/admin/users" class="no-underline">
              <Button :label="t('admin.dashboardPage.manageUsers')" icon="pi pi-users" class="action-button" />
            </router-link>
            <router-link to="/app/admin/invitations" class="no-underline">
              <Button :label="t('admin.dashboardPage.userInvitations')" icon="pi pi-send" severity="secondary" class="action-button" />
            </router-link>
            <router-link to="/app/admin/oidc-providers" class="no-underline">
              <Button :label="t('admin.dashboardPage.oidcProviders')" icon="pi pi-key" severity="secondary" class="action-button" />
            </router-link>
            <router-link to="/app/admin/audit-logs" class="no-underline">
              <Button :label="t('admin.dashboardPage.auditLogs')" icon="pi pi-history" severity="secondary" class="action-button" />
            </router-link>
          </div>
        </template>
      </Card>

      <!-- Operations -->
      <Card class="actions-card">
        <template #content>
          <h3 class="actions-title">
            <i class="pi pi-database actions-icon"></i>
            {{ t('admin.dashboardPage.operations') }}
          </h3>
          <div class="actions-buttons">
            <router-link to="/app/admin/backups" class="no-underline">
              <Button :label="t('admin.dashboardPage.backupsRestore')" icon="pi pi-database" class="action-button" />
            </router-link>
            <router-link to="/app/admin/timeline-regeneration-campaigns" class="no-underline">
              <Button :label="t('admin.dashboardPage.timelineRegenerationCampaigns')" icon="pi pi-refresh" severity="secondary" class="action-button" />
            </router-link>
          </div>
        </template>
      </Card>
      <Card class="actions-card">
        <template #content>
          <h3 class="actions-title"><i class="pi pi-cog actions-icon"></i> {{ t('admin.dashboardPage.configuration') }}</h3>
          <div class="actions-buttons">
            <router-link to="/app/admin/settings" class="no-underline">
              <Button :label="t('admin.dashboardPage.systemSettings')" icon="pi pi-cog" class="action-button" />
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
import { useI18n } from 'vue-i18n'
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

const { t, te } = useI18n()
const adminService = useAdminStore()
const router = useRouter()

const breadcrumbHome = ref({
  icon: 'pi pi-home',
  command: () => router.push('/')
})
const breadcrumbItems = computed(() => [
  {
    label: t('admin.breadcrumb.administration'),
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
const weatherFetchStatus = computed(() => stats.value.weatherStatus?.fetchBlockedReason || t('admin.dashboardPage.ready'))
const weatherBacklogText = computed(() => {
  const backlog = weatherReconciliation.value
  if (!backlog || !backlog.pendingUserRanges) return t('admin.dashboardPage.clear')
  return t('admin.dashboardPage.waiting', { count: formatNumber(backlog.pendingUserRanges) })
})

const hasWeatherStatus = computed(() => !!stats.value.weatherStatus)

const weatherHealthBadge = computed(() => weatherProcessing.value?.running ? 'PROCESSING' : weatherProviderHealth.value?.status || 'UNKNOWN')

const weatherHealthLabel = computed(() => {
  const status = weatherProviderHealth.value?.status
  if (weatherProcessing.value?.running) {
    return t('admin.dashboardPage.processingWeather')
  }
  switch (status) {
    case 'HEALTHY':
      return t('admin.dashboardPage.operational')
    case 'PROVIDER_QUOTA_EXCEEDED':
      return t('admin.dashboardPage.openMeteoQuotaReached')
    case 'INTERNAL_QUOTA_EXCEEDED':
      return t('admin.dashboardPage.internalQuotaReached')
    case 'PROVIDER_UNAVAILABLE':
      return t('admin.dashboardPage.providerUnavailable')
    case 'CONFIG_ERROR':
      return t('admin.dashboardPage.configurationError')
    default:
      if (!hasWeatherStatus.value) return t('admin.dashboardPage.unknown')
      return stats.value.weatherStatus?.enabled ? t('admin.dashboardPage.unknown') : t('admin.dashboardPage.disabled')
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
    return { label: t('admin.dashboardPage.pausedUntil'), value: formatDateTime(health.circuitOpenUntil) }
  }
  if (health.nextProbeAt && isFutureTimestamp(health.nextProbeAt)) {
    return { label: t('admin.dashboardPage.nextProbe'), value: formatDateTime(health.nextProbeAt) }
  }
  if (health.lastSuccessAt) {
    return { label: t('admin.dashboardPage.lastSuccess'), value: formatDateTime(health.lastSuccessAt) }
  }
  return null
})

const health = computed(() => stats.value.health || {})
const SECURITY_WARNING_MESSAGES = computed(() => ({
  SCHEDULED_BACKUPS_DISABLED: t('admin.dashboardPage.scheduledBackupsDisabled')
}))
const securityWarnings = computed(() => (health.value.security?.warnings || [])
  .map(code => SECURITY_WARNING_MESSAGES.value[code] || code))
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
  if (badge === 'HEALTHY') return mapMatchingWorker.value.running ? t('admin.dashboardPage.processingMapMatching') : t('admin.dashboardPage.operational')
  if (badge === 'UNKNOWN') return t('admin.dashboardPage.noProviderRequestObserved')
  if (badge === 'DISABLED') return t('admin.dashboardPage.cards.statusDisabledBadge')
  if (badge === 'NOT CONFIGURED') return t('admin.dashboardPage.geocodingStatusLabels.NOT_CONFIGURED')
  if (badge === 'BLOCKED') return t('admin.dashboardPage.blockedBadge')
  if (badge === 'PROVIDER_UNAVAILABLE') return t('admin.dashboardPage.providerUnavailable')
  if (badge === 'CONFIG_ERROR') return t('admin.dashboardPage.configurationError')
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
  if (!health.value.backup?.latestBackupAt) warnings.push(t('admin.dashboardPage.noLocalBackupFound'))
  else if (health.value.backup?.stale) warnings.push(t('admin.dashboardPage.backupOlderThanLimit', { days: health.value.backup.healthMaxAgeDays }))
  if (!health.value.ingestion?.latestReceivedAt) warnings.push(t('admin.dashboardPage.noGpsDataReceived'))
  else if (isGpsIngestionStale.value) warnings.push(t('admin.dashboardPage.gpsIngestionInactive'))
  const geocodingStatus = geocodingHealth.value.status
  const fallbackConfigured = geocodingHealth.value.providers?.some(provider => provider.fallback)
  if (['NOT_CONFIGURED', 'DEGRADED', 'CIRCUIT_OPEN'].includes(geocodingStatus)) {
    warnings.push(t('admin.dashboardPage.reverseGeocodingNeedsAttention'))
  } else if (!fallbackConfigured) {
    warnings.push(t('admin.dashboardPage.noFallbackConfigured'))
  }
  if (mapMatchingHealth.value.enabled && (!mapMatchingHealth.value.configured
      || mapMatchingWorker.value.lastError
      || (mapMatchingProviderHealth.value?.status && mapMatchingProviderHealth.value.status !== 'HEALTHY'))) {
    warnings.push(t('admin.dashboardPage.mapMatchingNeedsAttention'))
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
const geocodingStatusLabel = (status) => te(`admin.dashboardPage.geocodingStatusLabels.${status}`) ? t(`admin.dashboardPage.geocodingStatusLabels.${status}`) : status.replaceAll('_', ' ')
const geocodingProviderDetail = (provider) => {
  if (provider.status === 'UNKNOWN') return t('admin.dashboardPage.cards.noProviderRequestsObserved')
  if (provider.status === 'CIRCUIT_OPEN') return t('admin.dashboardPage.cards.circuitBreakerOpen', { date: formatDateTime(provider.circuitBreakerObservedOpenAt) })
  if (provider.status === 'DEGRADED') return provider.lastErrorMessage || t('admin.dashboardPage.cards.lastFailure', { date: formatDateTime(provider.lastFailureAt) })
  return t('admin.dashboardPage.cards.lastSuccessDetail', { date: formatDateTime(provider.lastSuccessAt) })
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
      label: t('admin.dashboardPage.cards.backups'), icon: 'pi pi-database', to: '/app/admin/backups', action: t('admin.dashboardPage.cards.openBackups'),
      status: backupReady ? backupStale ? t('admin.dashboardPage.cards.statusStale') : t('admin.dashboardPage.cards.statusReady') : t('admin.dashboardPage.cards.statusActionNeeded'), severity: backupReady && !backupStale ? 'success' : 'warn',
      detail: backupReady ? `${t('admin.dashboardPage.cards.latestBackup', { date: formatDateTime(backup.latestBackupAt) })}${backupStale ? t('admin.dashboardPage.cards.olderThanDays', { days: backup.healthMaxAgeDays }) : ''}` : t('admin.dashboardPage.cards.noBackupYet')
    },
    {
      label: t('admin.dashboardPage.cards.gpsIngestion'), icon: 'pi pi-map-marker', to: '/app/admin/users', action: t('admin.dashboardPage.cards.manageUsersAction'),
      status: lastReceived ? isGpsIngestionStale.value ? t('admin.dashboardPage.cards.statusStale') : t('admin.dashboardPage.cards.statusReceiving') : t('admin.dashboardPage.cards.statusNoData'), severity: lastReceived && !isGpsIngestionStale.value ? 'success' : 'warn',
      detail: lastReceived ? t('admin.dashboardPage.cards.latestPointReceived', { date: formatDateTime(lastReceived) }) : t('admin.dashboardPage.cards.noGpsPointsReceived')
    },
    {
      label: t('admin.dashboardPage.cards.reverseGeocoding'), icon: 'pi pi-map-marker', to: '/app/admin/settings?tab=geocoding', action: t('admin.dashboardPage.cards.openGeocodingSettings'),
      status: geocodingStatus, statusLabel: geocodingStatusLabel(geocodingStatus), severity: geocodingSeverity(geocodingStatus),
      warning: geocodingStatus === 'NOT_CONFIGURED' || geocodingProviders.some(provider => provider.fallback) ? null : t('admin.dashboardPage.cards.noFallback'),
      providers: [...geocodingProviders].sort((a, b) => Number(b.primary) - Number(a.primary) || Number(b.fallback) - Number(a.fallback)).map(provider => ({
        ...provider,
        statusLabel: geocodingStatusLabel(provider.status),
        severity: geocodingSeverity(provider.status),
        detail: geocodingProviderDetail(provider)
      })),
      detail: geocodingStatus === 'NOT_CONFIGURED' ? t('admin.dashboardPage.cards.noEnabledProviders') : ''
    },
    {
      label: t('admin.dashboardPage.cards.weather'), icon: 'pi pi-cloud', to: '/app/admin/settings?tab=weather', action: t('admin.dashboardPage.cards.openWeatherSettings'),
      status: weatherProblem ? t('admin.dashboardPage.cards.statusAttention') : weather.enabled === false ? t('admin.dashboardPage.cards.statusDisabledBadge') : t('admin.dashboardPage.cards.statusReady'), severity: weatherProblem ? 'warn' : weather.enabled === false ? 'secondary' : 'success',
      detail: weatherProblem ? (weather.providerHealth?.status || t('admin.dashboardPage.cards.providerNeedsAttention')) : weather.enabled === false ? t('admin.dashboardPage.cards.weatherDisabled') : t('admin.dashboardPage.cards.weatherReady')
    },
    {
      label: t('admin.dashboardPage.cards.mapMatching'), icon: 'pi pi-directions-alt', to: '/app/admin/settings?tab=map-matching', action: t('admin.dashboardPage.cards.openMapMatchingSettings'),
      status: mapMatchingBadge, statusLabel: mapMatchingHealthLabel.value, severity: mapMatchingHealthSeverity.value,
      detail: !mapMatchingHealth.value.enabled ? t('admin.dashboardPage.cards.mapMatchingDisabled')
        : !mapMatchingHealth.value.configured ? t('admin.dashboardPage.cards.valhallaNotConfigured')
          : mapMatchingProviderHealth.value?.lastSuccessAt ? t('admin.dashboardPage.cards.lastValhallaSuccess', { date: formatDateTime(mapMatchingProviderHealth.value.lastSuccessAt) })
            : t('admin.dashboardPage.cards.noValhallaRequests')
    },
    {
      label: t('admin.dashboardPage.cards.security'), icon: 'pi pi-shield', to: '/app/admin/settings?tab=authentication', action: t('admin.dashboardPage.cards.openSecuritySettings'),
      status: securityWarnings.value.length ? t('admin.dashboardPage.cards.statusActionNeeded') : t('admin.dashboardPage.cards.statusReady'), severity: securityWarnings.value.length ? 'warn' : 'success',
      detail: securityWarnings.value.length ? securityWarnings.value[0] : t('admin.dashboardPage.cards.noConfigurationWarnings')
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
  color: var(--gp-text-primary);
}

.text-muted {
  color: var(--gp-text-secondary);
  margin: 0;
}

/* Stats Header */
.stats-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.health-summary { display: flex; align-items: center; gap: .6rem; margin-bottom: 1rem; padding: .8rem 1rem; border-radius: var(--gp-radius-medium); background: var(--gp-success-soft, #ecfdf5); color: var(--gp-success-text, #166534); }
.health-summary--warning { background: var(--gp-warning-soft, #fff7ed); color: var(--gp-warning-text, #9a3412); }
.health-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 1rem; margin-bottom: 2rem; }
.health-card-header { display: flex; align-items: center; justify-content: space-between; gap: .5rem; font-weight: 600; }
.health-card-tags { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: .25rem; }
.health-card p { min-height: 2.8rem; margin: 1rem 0 .5rem; color: var(--gp-text-secondary); font-size: .9rem; line-height: 1.4; }
.geocoding-provider-list { display: grid; gap: .6rem; margin: 1rem 0 .5rem; }
.geocoding-provider-row { display: flex; align-items: flex-start; justify-content: space-between; gap: .5rem; color: var(--gp-text-secondary); font-size: .8rem; }
.geocoding-provider-name { display: flex; align-items: center; gap: .35rem; }
.geocoding-provider-row strong, .geocoding-provider-row small { display: block; }
.geocoding-provider-row strong { color: var(--gp-text-primary); }
.geocoding-provider-tags { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: .25rem; }
.geocoding-provider-role { font-size: .65rem; padding: .1rem .3rem; }
.usage-header { margin-top: .5rem; }

.stats-title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--gp-text-primary);
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
  color: var(--gp-text-secondary);
}

.stat-value {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.stat-icon {
  font-size: 2.5rem;
  opacity: 0.8;
}

.stat-icon-primary {
  color: var(--gp-primary);
}

.stat-icon-orange {
  color: var(--p-orange-500);
}

.stat-icon-green {
  color: var(--p-green-500);
}

.stat-icon-blue {
  color: var(--p-blue-500);
}

.stat-icon-purple {
  color: var(--p-purple-500);
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
  color: var(--gp-text-secondary);
  margin-left: auto;
}

.operational-health-details[open] summary::after {
  transform: rotate(180deg);
}

.weather-health-title {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--gp-text-primary);
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
  color: var(--gp-text-secondary);
}

.weather-health-row strong {
  color: var(--gp-text-primary);
  text-align: right;
}

.weather-health-status {
  max-width: 420px;
  overflow-wrap: anywhere;
}

.weather-health-message {
  padding: 0.75rem;
  border: 1px solid var(--gp-border);
  border-radius: 6px;
  color: var(--gp-text-primary);
  background: var(--gp-surface-ground);
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
  color: var(--gp-text-primary);
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.actions-icon {
  color: var(--gp-primary);
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

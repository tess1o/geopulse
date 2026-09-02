import { reactive } from 'vue'

const STORAGE_KEY = 'geopulse.restore-maintenance'
const SHARED_STORAGE_KEY = 'geopulse.restore-maintenance-active'
const RELAY_KEY = 'geopulse.restore-maintenance-event'
const COMPLETED_KEY = 'geopulse.restore-completed'
const ACTIVE_STATES = new Set(['PREPARING', 'ACTIVATING', 'SWAPPED_PENDING_RESTART'])
const INTERRUPTING_STATES = new Set(['ACTIVATING', 'SWAPPED_PENDING_RESTART', 'ACTIVATION_FAILED'])

function readStored() {
  try {
    const sessionValue = sessionStorage.getItem(STORAGE_KEY)
    if (sessionValue) return { data: JSON.parse(sessionValue), source: 'session' }
    const sharedValue = localStorage.getItem(SHARED_STORAGE_KEY)
    if (sharedValue) return { data: JSON.parse(sharedValue), source: 'shared' }
  } catch { /* ignore stale persisted state */ }
  return { data: null, source: null }
}

const saved = readStored()
const savedData = saved.data
export const maintenance = reactive({
  state: savedData?.state || 'IDLE',
  blocked: !!savedData?.blocked,
  warning: !!savedData?.warning,
  message: savedData?.message || '',
  backupCreatedAt: savedData?.backupCreatedAt || '',
  initialized: false,
  unavailable: !!savedData?.unavailable,
  activated: !!savedData?.activated,
  completedAt: savedData?.completedAt || null,
  offlineSince: savedData?.offlineSince || null,
  needsConfirmation: saved.source === 'shared',
  manualRestartRequired: false
})

export class MaintenanceInterruption extends Error {
  constructor(message = 'Request interrupted while GeoPulse activates a restoration') {
    super(message)
    this.name = 'MaintenanceInterruption'
    this.code = 'GEOPULSE_RESTORE_MAINTENANCE'
  }
}

export const isMaintenanceInterruption = error => error instanceof MaintenanceInterruption || error?.code === 'GEOPULSE_RESTORE_MAINTENANCE'
export const isKnownRestoreActive = () => ACTIVE_STATES.has(maintenance.state) || maintenance.activated
const confirmedOrUnavailable = () => !maintenance.needsConfirmation || maintenance.unavailable
export const interruptsApplicationRequests = () => confirmedOrUnavailable() && (maintenance.activated || INTERRUPTING_STATES.has(maintenance.state) || (maintenance.unavailable && isKnownRestoreActive()))
export const maintenanceScreenVisible = () => confirmedOrUnavailable() && (maintenance.activated || INTERRUPTING_STATES.has(maintenance.state) || (maintenance.unavailable && isKnownRestoreActive()))

let pending
let poller
let restartFallbackTimer
let channel

function dispatchChange(previousState) {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('geopulse:maintenance-change', { detail: { previousState, state: maintenance.state } }))
  }
}

function persistAndBroadcast(data, broadcast) {
  try {
    const shouldPersist = ACTIVE_STATES.has(data.state) || data.state === 'ACTIVATION_FAILED' || !!data.activated
    if (shouldPersist) {
      const serialized = JSON.stringify(data)
      sessionStorage.setItem(STORAGE_KEY, serialized)
      localStorage.setItem(SHARED_STORAGE_KEY, serialized)
    } else {
      sessionStorage.removeItem(STORAGE_KEY)
      localStorage.removeItem(SHARED_STORAGE_KEY)
    }
  } catch { /* keep the in-memory state */ }
  if (!broadcast) return
  try { channel?.postMessage(data) } catch { /* BroadcastChannel is optional */ }
  try {
    localStorage.setItem(RELAY_KEY, JSON.stringify({ ...data, nonce: crypto.randomUUID?.() || `${Date.now()}-${Math.random()}` }))
    localStorage.removeItem(RELAY_KEY)
  } catch { /* storage events are a fallback only */ }
}

function armManualRestartFallback() {
  if (restartFallbackTimer) clearTimeout(restartFallbackTimer)
  if (!maintenance.offlineSince) return
  const remaining = Math.max(0, 60000 - (Date.now() - maintenance.offlineSince))
  maintenance.manualRestartRequired = remaining === 0
  if (remaining > 0) {
    restartFallbackTimer = setTimeout(() => { maintenance.manualRestartRequired = true }, remaining)
  }
}

export function applyMaintenanceStatus(data, { broadcast = true, trusted = true } = {}) {
  if (!data?.state) return
  const previousState = maintenance.state
  const observedRestore = ACTIVE_STATES.has(previousState) || maintenance.unavailable || maintenance.blocked
  const shouldPreserveUnavailable = !trusted && maintenance.unavailable && ACTIVE_STATES.has(data.state)
  const unavailable = !!data.unavailable || shouldPreserveUnavailable
  Object.assign(maintenance, {
    state: data.state,
    blocked: !!data.blocked,
    warning: !!data.warning,
    message: data.message || '',
    backupCreatedAt: data.backupCreatedAt || '',
    initialized: true,
    needsConfirmation: false,
    unavailable,
    offlineSince: unavailable ? (data.offlineSince || maintenance.offlineSince || Date.now()) : null,
    manualRestartRequired: unavailable ? maintenance.manualRestartRequired : false
  })
  if (unavailable) armManualRestartFallback()
  if (data.state === 'COMPLETED') {
    let unseen = observedRestore
    try { unseen ||= !!data.completedAt && localStorage.getItem(COMPLETED_KEY) !== data.completedAt } catch { unseen = true }
    if (unseen) {
      maintenance.activated = true
      maintenance.completedAt = data.completedAt || ''
    }
  } else if (['PREPARATION_FAILED', 'ACTIVATION_RETRYABLE', 'DISCARDED', 'IDLE'].includes(data.state)) {
    maintenance.activated = false
  }
  persistAndBroadcast({ ...data, activated: maintenance.activated, completedAt: maintenance.completedAt || data.completedAt, unavailable, offlineSince: maintenance.offlineSince }, broadcast)
  dispatchChange(previousState)
}

export function markMaintenanceUnavailable() {
  if (!isKnownRestoreActive()) return false
  const previousState = maintenance.state
  maintenance.unavailable = true
  maintenance.needsConfirmation = false
  maintenance.blocked = true
  maintenance.warning = false
  maintenance.offlineSince ||= Date.now()
  persistAndBroadcast({ ...maintenance }, true)
  armManualRestartFallback()
  dispatchChange(previousState)
  return true
}

export async function refreshMaintenance() {
  if (pending) return pending
  pending = (async () => {
    try {
      const base = window.VUE_APP_CONFIG?.API_BASE_URL || '/api'
      const response = await fetch(`${base}/maintenance/status`, {
        cache: 'no-store', credentials: 'omit', signal: AbortSignal.timeout(5000)
      })
      if (!response.ok) throw new Error('Maintenance status unavailable')
      const payload = await response.json()
      applyMaintenanceStatus(payload.data, { trusted: true })
    } catch {
      maintenance.initialized = true
      markMaintenanceUnavailable()
    } finally { pending = null }
  })()
  return pending
}

export function startMaintenancePolling() {
  if (!poller) poller = window.setInterval(refreshMaintenance, 2000)
}

export function acknowledgeActivation() {
  try {
    if (maintenance.completedAt) localStorage.setItem(COMPLETED_KEY, maintenance.completedAt)
    sessionStorage.removeItem(STORAGE_KEY)
    localStorage.removeItem(SHARED_STORAGE_KEY)
  } catch { /* the page reload still clears its in-memory session */ }
}

if (typeof window !== 'undefined') {
  if ('BroadcastChannel' in window) {
    try {
      channel = new BroadcastChannel('geopulse-restore-maintenance')
      channel.onmessage = event => applyMaintenanceStatus(event.data, { broadcast: false, trusted: false })
    } catch { channel = null }
  }
  window.addEventListener('storage', event => {
    if (event.key !== RELAY_KEY || !event.newValue) return
    try { applyMaintenanceStatus(JSON.parse(event.newValue), { broadcast: false, trusted: false }) } catch { /* ignore malformed cross-tab state */ }
  })
  if (maintenance.unavailable) armManualRestartFallback()
}

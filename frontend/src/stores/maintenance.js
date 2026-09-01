import { reactive } from 'vue'

const STORAGE_KEY = 'geopulse.restore-maintenance'
const SHARED_STORAGE_KEY = 'geopulse.restore-maintenance-active'
const RELAY_KEY = 'geopulse.restore-maintenance-event'
const COMPLETED_KEY = 'geopulse.restore-completed'
const ACTIVE_STATES = new Set(['PREPARING', 'ACTIVATING', 'SWAPPED_PENDING_RESTART'])
const INTERRUPTING_STATES = new Set(['ACTIVATING', 'SWAPPED_PENDING_RESTART', 'ACTIVATION_FAILED'])

function readStored() {
  try { return JSON.parse(sessionStorage.getItem(STORAGE_KEY) || localStorage.getItem(SHARED_STORAGE_KEY) || 'null') }
  catch { return null }
}

const saved = readStored()
export const maintenance = reactive({
  state: saved?.state || 'IDLE',
  blocked: !!saved?.blocked,
  warning: !!saved?.warning,
  message: saved?.message || '',
  backupCreatedAt: saved?.backupCreatedAt || '',
  initialized: false,
  unavailable: !!saved?.unavailable,
  activated: !!saved?.activated,
  completedAt: saved?.completedAt || null,
  offlineSince: saved?.offlineSince || null,
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
export const interruptsApplicationRequests = () => maintenance.activated || INTERRUPTING_STATES.has(maintenance.state) || (maintenance.unavailable && isKnownRestoreActive())
export const maintenanceScreenVisible = () => maintenance.activated || INTERRUPTING_STATES.has(maintenance.state) || (maintenance.unavailable && isKnownRestoreActive())

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
  maintenance.manualRestartRequired = false
  if (!maintenance.offlineSince) return
  const remaining = Math.max(0, 60000 - (Date.now() - maintenance.offlineSince))
  restartFallbackTimer = setTimeout(() => { maintenance.manualRestartRequired = true }, remaining)
}

export function applyMaintenanceStatus(data, { broadcast = true } = {}) {
  if (!data?.state) return
  const previousState = maintenance.state
  const observedRestore = ACTIVE_STATES.has(previousState) || maintenance.unavailable || maintenance.blocked
  Object.assign(maintenance, {
    state: data.state,
    blocked: !!data.blocked,
    warning: !!data.warning,
    message: data.message || '',
    backupCreatedAt: data.backupCreatedAt || '',
    initialized: true,
    unavailable: false,
    offlineSince: null,
    manualRestartRequired: false
  })
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
  persistAndBroadcast({ ...data, activated: maintenance.activated, completedAt: maintenance.completedAt || data.completedAt, unavailable: false, offlineSince: null }, broadcast)
  dispatchChange(previousState)
}

export function markMaintenanceUnavailable() {
  if (!isKnownRestoreActive()) return false
  const previousState = maintenance.state
  maintenance.unavailable = true
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
      applyMaintenanceStatus(payload.data)
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
      channel.onmessage = event => applyMaintenanceStatus(event.data, { broadcast: false })
    } catch { channel = null }
  }
  window.addEventListener('storage', event => {
    if (event.key !== RELAY_KEY || !event.newValue) return
    try { applyMaintenanceStatus(JSON.parse(event.newValue), { broadcast: false }) } catch { /* ignore malformed cross-tab state */ }
  })
  if (maintenance.unavailable) armManualRestartFallback()
}

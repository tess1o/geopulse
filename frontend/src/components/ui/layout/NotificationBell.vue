<template>
  <div ref="rootRef" class="gp-notification-bell">
    <Button
      icon="pi pi-bell"
      text
      rounded
      aria-label="Open notifications inbox"
      class="gp-bell-trigger"
      @click="togglePanel"
    />
    <span v-if="unreadCount > 0" class="gp-bell-badge">{{ unreadBadgeValue }}</span>

    <div v-if="panelOpen" class="gp-notification-panel">
      <div class="gp-notification-panel-header">
        <div class="gp-notification-panel-title">Notifications</div>
        <Tag v-if="unreadCount > 0" :value="`${unreadCount} unread`" severity="danger" />
      </div>

      <div class="gp-notification-filters">
        <Button
          label="Unread"
          size="small"
          :severity="activeFilter === 'unread' ? 'primary' : 'secondary'"
          :outlined="activeFilter !== 'unread'"
          @click="activeFilter = 'unread'"
        />
        <Button
          label="All"
          size="small"
          :severity="activeFilter === 'all' ? 'primary' : 'secondary'"
          :outlined="activeFilter !== 'all'"
          @click="activeFilter = 'all'"
        />
      </div>

      <div class="gp-notification-browser">
        <label class="gp-notification-browser-label" for="browserNotificationToggle">Browser alerts</label>
        <InputSwitch
          inputId="browserNotificationToggle"
          :modelValue="browserNotificationsEnabled"
          :disabled="!browserNotificationsSupported"
          @update:modelValue="toggleBrowserNotifications"
        />
      </div>
      <small v-if="!browserNotificationsSupported" class="gp-notification-browser-help">
        Browser notifications are not available in this browser.
      </small>

      <div class="gp-notification-list">
        <div v-if="visibleItems.length === 0" class="gp-notification-empty">
          No {{ activeFilter === 'unread' ? 'unread' : '' }} notifications.
        </div>
        <div
          v-for="item in visibleItems"
          :key="item.id"
          class="gp-notification-item"
          :class="{ 'gp-notification-item--unread': !item.seen }"
        >
          <button type="button" class="gp-notification-item-main" @click="openEventsView">
            <div class="gp-notification-item-row">
              <span class="gp-notification-item-title">{{ item.title || `Geofence ${item.eventType || 'Event'}` }}</span>
              <Tag :value="item.deliveryStatus || 'UNKNOWN'" :severity="deliverySeverity(item.deliveryStatus)" />
            </div>
            <div class="gp-notification-item-message">{{ item.message || 'New geofence event.' }}</div>
            <div class="gp-notification-item-time">{{ formatOccurredAt(item.occurredAt) }}</div>
          </button>
          <Button
            v-if="!item.seen"
            label="Mark seen"
            size="small"
            text
            class="gp-notification-item-action"
            @click.stop="markSeen(item.id)"
          />
        </div>
      </div>

      <div class="gp-notification-footer">
        <Button
          label="Mark all seen"
          size="small"
          severity="secondary"
          outlined
          :disabled="unreadCount === 0"
          @click="markAllSeen"
        />
        <Button
          label="Open Events"
          size="small"
          severity="secondary"
          outlined
          @click="openEventsView"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import InputSwitch from 'primevue/inputswitch'
import { useToast } from 'primevue/usetoast'
import { useNotificationsStore } from '@/stores/notifications'
import { useTimezone } from '@/composables/useTimezone'

const toast = useToast()
const timezone = useTimezone()
const notificationsStore = useNotificationsStore()
const { items, unreadCount, browserNotificationsEnabled, browserNotificationsSupported } = storeToRefs(notificationsStore)

const rootRef = ref(null)
const panelOpen = ref(false)
const activeFilter = ref('unread')

const unreadBadgeValue = computed(() => {
  return unreadCount.value > 99 ? '99+' : unreadCount.value
})

const visibleItems = computed(() => {
  const source = Array.isArray(items.value) ? items.value : []
  const filtered = activeFilter.value === 'unread'
    ? source.filter(item => !item.seen)
    : source
  return filtered.slice(0, 20)
})

const togglePanel = () => {
  panelOpen.value = !panelOpen.value
}

const closePanel = () => {
  panelOpen.value = false
}

const openEventsView = () => {
  notificationsStore.openEventsView()
  closePanel()
}

const markSeen = async (eventId) => {
  try {
    await notificationsStore.markSeen(eventId)
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Notification Error',
      detail: extractApiErrorMessage(error, 'Failed to mark notification as seen'),
      life: 5000
    })
  }
}

const markAllSeen = async () => {
  try {
    await notificationsStore.markAllSeen()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Notification Error',
      detail: extractApiErrorMessage(error, 'Failed to mark all notifications as seen'),
      life: 5000
    })
  }
}

const toggleBrowserNotifications = async (enabled) => {
  await notificationsStore.setBrowserNotificationsEnabled(!!enabled)
}

const formatOccurredAt = (value) => {
  if (!value) {
    return '-'
  }
  return `${timezone.formatDateDisplay(value)} ${timezone.format(value, 'HH:mm:ss')}`
}

const deliverySeverity = (status) => {
  if (status === 'SENT') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PENDING') return 'info'
  return 'secondary'
}

const handleClickOutside = (event) => {
  if (!panelOpen.value) {
    return
  }
  if (rootRef.value && !rootRef.value.contains(event.target)) {
    closePanel()
  }
}

const handleKeydown = (event) => {
  if (event.key === 'Escape') {
    closePanel()
  }
}

const extractApiErrorMessage = (error, fallback) => (
  error?.response?.data?.message
  || error?.response?.data?.error
  || error?.response?.data?.data?.message
  || error?.userMessage
  || error?.message
  || fallback
)

onMounted(() => {
  document.addEventListener('mousedown', handleClickOutside)
  document.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  document.removeEventListener('mousedown', handleClickOutside)
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
.gp-notification-bell {
  position: relative;
}

.gp-bell-trigger {
  position: relative;
}

.gp-bell-badge {
  position: absolute;
  top: -0.15rem;
  right: -0.15rem;
  min-width: 1.1rem;
  height: 1.1rem;
  border-radius: 999px;
  padding: 0 0.3rem;
  background: #dc2626;
  color: #fff;
  font-size: 0.68rem;
  font-weight: 700;
  line-height: 1.1rem;
  text-align: center;
  pointer-events: none;
}

.gp-notification-panel {
  position: absolute;
  top: calc(100% + 0.55rem);
  right: 0;
  width: min(420px, calc(100vw - 2rem));
  max-height: 70vh;
  overflow: hidden;
  border: 1px solid var(--gp-border-light);
  border-radius: 12px;
  background: var(--gp-surface-white);
  box-shadow: 0 14px 38px rgba(0, 0, 0, 0.16);
  z-index: 1200;
  display: flex;
  flex-direction: column;
  color: var(--text-color);
}

.gp-notification-panel-header {
  padding: 0.8rem 0.9rem 0.5rem;
  border-bottom: 1px solid var(--gp-border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.gp-notification-panel-title {
  font-weight: 700;
  color: inherit;
}

.gp-notification-filters {
  display: flex;
  gap: 0.5rem;
  padding: 0.7rem 0.9rem 0.3rem;
}

.gp-notification-browser {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.2rem 0.9rem 0;
}

.gp-notification-browser-label {
  font-size: 0.85rem;
  font-weight: 600;
  color: inherit;
}

.gp-notification-browser-help {
  color: var(--gp-text-secondary);
  padding: 0.2rem 0.9rem 0;
}

.gp-notification-list {
  padding: 0.5rem 0.9rem 0.8rem;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.gp-notification-empty {
  border: 1px dashed var(--gp-border-light);
  border-radius: 10px;
  color: var(--gp-text-secondary);
  padding: 0.9rem;
  font-size: 0.88rem;
  background: color-mix(in srgb, var(--gp-surface-white) 92%, var(--gp-primary));
}

.gp-notification-item {
  border: 1px solid var(--gp-border-light);
  border-radius: 10px;
  padding: 0.65rem;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.gp-notification-item--unread {
  border-color: var(--gp-primary);
  background: color-mix(in srgb, var(--gp-primary) 7%, transparent);
}

.gp-notification-item-main {
  border: none;
  background: transparent;
  padding: 0;
  text-align: left;
  cursor: pointer;
  color: inherit;
}

.gp-notification-item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.gp-notification-item-title {
  font-weight: 700;
  font-size: 0.92rem;
}

.gp-notification-item-message {
  margin-top: 0.25rem;
  color: var(--gp-text-secondary);
  font-size: 0.85rem;
}

.gp-notification-item-time {
  margin-top: 0.25rem;
  color: var(--gp-text-muted);
  font-size: 0.78rem;
}

.gp-notification-item-action {
  align-self: flex-end;
}

.gp-notification-footer {
  border-top: 1px solid var(--gp-border-light);
  padding: 0.7rem 0.9rem;
  display: flex;
  justify-content: space-between;
  gap: 0.55rem;
}

.p-dark .gp-notification-panel {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
  color: #e5e7eb;
  box-shadow: 0 14px 38px rgba(2, 6, 23, 0.65);
}

.p-dark .gp-notification-panel-header,
.p-dark .gp-notification-footer {
  border-color: rgba(148, 163, 184, 0.35);
}

.p-dark .gp-notification-panel-title,
.p-dark .gp-notification-browser-label,
.p-dark .gp-notification-item-title {
  color: #f8fafc;
}

.p-dark .gp-notification-item {
  border-color: rgba(148, 163, 184, 0.38);
  background: rgba(15, 23, 42, 0.28);
}

.p-dark .gp-notification-item-message {
  color: #cbd5e1;
}

.p-dark .gp-notification-item-time,
.p-dark .gp-notification-browser-help {
  color: #94a3b8;
}

.p-dark .gp-notification-empty {
  border-color: rgba(148, 163, 184, 0.45);
  background: rgba(30, 41, 59, 0.55);
  color: #cbd5e1;
}

.p-dark .gp-notification-item--unread {
  border-color: color-mix(in srgb, var(--gp-primary) 70%, #ffffff);
  background: color-mix(in srgb, var(--gp-primary) 18%, rgba(15, 23, 42, 0.35));
}

.p-dark .gp-notification-panel :deep(.p-button.p-button-outlined.p-button-secondary) {
  color: #e2e8f0;
  border-color: rgba(203, 213, 225, 0.82);
  background: rgba(30, 41, 59, 0.25);
}

.p-dark .gp-notification-panel :deep(.p-button.p-button-outlined.p-button-secondary:hover) {
  background: rgba(59, 130, 246, 0.2);
  border-color: #93c5fd;
  color: #f8fafc;
}

.p-dark .gp-notification-panel :deep(.p-button.p-button-outlined.p-button-secondary:disabled) {
  color: #94a3b8;
  border-color: rgba(148, 163, 184, 0.55);
  background: rgba(15, 23, 42, 0.22);
}

.p-dark .gp-notification-panel :deep(.p-inputswitch.p-disabled) {
  opacity: 0.9;
}

@media (max-width: 640px) {
  .gp-notification-panel {
    right: -1rem;
  }
}
</style>

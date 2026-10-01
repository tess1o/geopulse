<template>
  <div ref="rootRef" class="gp-notification-bell">
    <Button
      icon="pi pi-bell"
      text
      rounded
      :aria-label="t('notifications.bell.openAriaLabel')"
      class="gp-bell-trigger"
      @click="togglePanel"
    />
    <span v-if="unreadCount > 0" class="gp-bell-badge">{{ unreadBadgeValue }}</span>

    <teleport to="body">
      <div
        v-if="panelOpen"
        ref="panelRef"
        class="gp-notification-panel"
        :style="panelInlineStyle"
      >
        <div class="gp-notification-panel-header">
          <div class="gp-notification-panel-title">{{ t('notifications.page.title') }}</div>
          <Tag v-if="unreadCount > 0" :value="t('notifications.bell.unreadTag', { count: unreadCount })" severity="danger" />
        </div>

        <div class="gp-notification-filters">
          <Button
            :label="t('notifications.filters.unread')"
            size="small"
            :severity="activeFilter === 'unread' ? 'primary' : 'secondary'"
            :outlined="activeFilter !== 'unread'"
            @click="activeFilter = 'unread'"
          />
          <Button
            :label="t('notifications.filters.all')"
            size="small"
            :severity="activeFilter === 'all' ? 'primary' : 'secondary'"
            :outlined="activeFilter !== 'all'"
            @click="activeFilter = 'all'"
          />
        </div>

        <div class="gp-notification-browser">
          <label class="gp-notification-browser-label" for="browserNotificationToggle">{{ t('notifications.bell.browserAlerts') }}</label>
          <InputSwitch
            inputId="browserNotificationToggle"
            :modelValue="browserNotificationsEnabled"
            :disabled="!browserNotificationsSupported"
            @update:modelValue="toggleBrowserNotifications"
          />
        </div>
        <small v-if="!browserNotificationsSupported" class="gp-notification-browser-help">
          {{ t('notifications.bell.browserNotSupported') }}
        </small>

        <div class="gp-notification-list">
          <div v-if="visibleItems.length === 0" class="gp-notification-empty">
            {{ emptyMessage }}
          </div>
          <div
            v-for="item in visibleItems"
            :key="item.id"
            class="gp-notification-item"
            :class="{ 'gp-notification-item--unread': !item.seen }"
          >
            <button type="button" class="gp-notification-item-main" @click="openNotification(item)">
              <div class="gp-notification-item-meta">
                <Tag
                  :value="notificationDisplay(item).sourceLabel"
                  :severity="notificationDisplay(item).severity"
                  :icon="notificationDisplay(item).icon"
                />
                <span v-if="showTypeLabel(item)">{{ notificationDisplay(item).typeLabel }}</span>
              </div>
              <div class="gp-notification-item-row">
                <span class="gp-notification-item-title">
                  <span>{{ itemTitle(item) }}</span>
                </span>
                <Tag
                  v-if="showDeliveryStatus(item)"
                  :value="item.deliveryStatus"
                  :severity="deliverySeverity(item.deliveryStatus)"
                />
              </div>
              <div class="gp-notification-item-message">{{ item.message || t('notifications.bell.defaultMessage') }}</div>
              <div class="gp-notification-item-time">{{ formatOccurredAt(item.occurredAt) }}</div>
            </button>
            <div class="gp-notification-item-actions">
              <Button
                :label="notificationDisplay(item).actionLabel"
                icon="pi pi-external-link"
                size="small"
                text
                class="gp-notification-item-action"
                @click.stop="openNotification(item)"
              />
              <Button
                v-if="!item.seen"
                :label="t('notifications.bell.markSeen')"
                size="small"
                text
                class="gp-notification-item-action"
                @click.stop="markSeen(item.id)"
              />
            </div>
          </div>
        </div>

        <div class="gp-notification-footer">
          <Button
            :label="t('notifications.bell.markAllSeen')"
            size="small"
            severity="secondary"
            outlined
            :disabled="unreadCount === 0"
            @click="markAllSeen"
          />
          <Button
            :label="t('ui.appLayout.viewAllNotifications')"
            size="small"
            severity="secondary"
            outlined
            @click="openNotificationCenter"
          />
        </div>
      </div>
    </teleport>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import InputSwitch from 'primevue/inputswitch'
import { useToast } from 'primevue/usetoast'
import { useNotificationsStore } from '@/stores/notifications'
import { useTimezone } from '@/composables/useTimezone'

const { t } = useI18n()
const toast = useToast()
const timezone = useTimezone()
const notificationsStore = useNotificationsStore()
const { items, unreadCount, browserNotificationsEnabled, browserNotificationsSupported } = storeToRefs(notificationsStore)

const rootRef = ref(null)
const panelRef = ref(null)
const panelOpen = ref(false)
const activeFilter = ref('unread')
const panelInlineStyle = ref({})

const unreadBadgeValue = computed(() => {
  return unreadCount.value > 99 ? '99+' : unreadCount.value
})

const emptyMessage = computed(() => {
  return activeFilter.value === 'unread' ? t('notifications.bell.noUnread') : t('notifications.bell.noNotifications')
})

const visibleItems = computed(() => {
  const source = Array.isArray(items.value) ? items.value : []
  const filtered = activeFilter.value === 'unread'
    ? source.filter(item => !item.seen)
    : source
  return filtered.slice(0, 20)
})

const togglePanel = async () => {
  panelOpen.value = !panelOpen.value
  if (panelOpen.value) {
    try {
      await notificationsStore.refresh({
        emitToasts: false,
        emitBrowser: false,
        emitStartupSummary: false
      })
    } catch (error) {
      // Polling will retry; keep the bell usable if a refresh fails.
    }
  }
}

const clamp = (value, min, max) => Math.min(Math.max(value, min), max)

const updatePanelPosition = () => {
  if (!panelOpen.value || !rootRef.value || !panelRef.value) {
    return
  }

  const rootRect = rootRef.value.getBoundingClientRect()
  const panelEl = panelRef.value
  const viewportWidth = window.innerWidth
  const viewportHeight = window.innerHeight
  const horizontalGap = 8
  const verticalGap = 8

  const panelWidth = panelEl.offsetWidth
  const panelHeight = panelEl.offsetHeight

  const preferredLeft = rootRect.right - panelWidth
  const minLeft = horizontalGap
  const maxLeft = Math.max(minLeft, viewportWidth - panelWidth - horizontalGap)
  const clampedLeft = clamp(preferredLeft, minLeft, maxLeft)

  const preferredTop = rootRect.bottom + verticalGap
  const minTop = verticalGap
  const maxTop = Math.max(minTop, viewportHeight - panelHeight - verticalGap)
  const clampedTop = clamp(preferredTop, minTop, maxTop)

  panelInlineStyle.value = {
    position: 'fixed',
    left: `${clampedLeft}px`,
    top: `${clampedTop}px`,
    right: 'auto',
    zIndex: 3200
  }
}

const closePanel = () => {
  panelOpen.value = false
}

const openNotification = (item) => {
  notificationsStore.openNotification(item)
  closePanel()
}

const openNotificationCenter = () => {
  notificationsStore.openNotificationCenter()
  closePanel()
}

const markSeen = async (eventId) => {
  try {
    await notificationsStore.markSeen(eventId)
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('notifications.bell.errorSummary'),
      detail: extractApiErrorMessage(error, t('notifications.toast.markSeenFailed')),
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
      summary: t('notifications.bell.errorSummary'),
      detail: extractApiErrorMessage(error, t('notifications.toast.markAllSeenFailed')),
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
  return `${timezone.formatDateDisplay(value)} ${timezone.formatTime(value, { withSeconds: true })}`
}

const deliverySeverity = (status) => {
  if (status === 'SENT') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PENDING') return 'info'
  return 'secondary'
}

const showDeliveryStatus = (item) => {
  return item?.source === 'GEOFENCE' && !!item.deliveryStatus
}

const notificationDisplay = (item) => {
  return notificationsStore.notificationDisplay(item)
}

const itemTitle = (item) => {
  return notificationDisplay(item).title || t('notifications.titleFallback')
}

const showTypeLabel = (item) => {
  const display = notificationDisplay(item)
  const title = String(display.title || '').trim().toLowerCase()
  const typeLabel = String(display.typeLabel || '').trim().toLowerCase()
  return !!typeLabel && typeLabel !== title
}

const handleClickOutside = (event) => {
  if (!panelOpen.value) {
    return
  }
  const clickedInsideTrigger = rootRef.value?.contains(event.target)
  const clickedInsidePanel = panelRef.value?.contains(event.target)

  if (!clickedInsideTrigger && !clickedInsidePanel) {
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
  window.addEventListener('resize', updatePanelPosition)
  window.addEventListener('scroll', updatePanelPosition, true)
})

onUnmounted(() => {
  document.removeEventListener('mousedown', handleClickOutside)
  document.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('resize', updatePanelPosition)
  window.removeEventListener('scroll', updatePanelPosition, true)
})

watch(panelOpen, async (isOpen) => {
  if (!isOpen) {
    panelInlineStyle.value = {}
    return
  }
  await nextTick()
  updatePanelPosition()
})
</script>

<style scoped>
.gp-notification-bell {
  position: relative;
  --bell-trigger-fg: var(--gp-text-primary);
  --bell-trigger-bg: var(--gp-surface-card);
  --bell-trigger-border: var(--gp-border-medium);
  --bell-trigger-shadow: var(--gp-shadow-subtle);
  --bell-trigger-fg-hover: var(--gp-primary-dark);
  --bell-trigger-bg-hover: color-mix(in srgb, var(--gp-primary) 9%, var(--gp-surface-card));
  --bell-trigger-border-hover: color-mix(in srgb, var(--gp-primary) 42%, var(--gp-border-medium));
  --bell-trigger-bg-active: color-mix(in srgb, var(--gp-primary) 16%, var(--gp-surface-card));
  --bell-trigger-border-active: color-mix(in srgb, var(--gp-primary) 58%, var(--gp-border-medium));

  --mark-seen-fg: var(--gp-primary-text);
  --mark-seen-border: color-mix(in srgb, var(--gp-primary) 42%, var(--gp-border-medium));
  --mark-seen-bg: color-mix(in srgb, var(--gp-primary) 9%, var(--gp-surface-card));
  --mark-seen-fg-hover: var(--gp-primary);
  --mark-seen-border-hover: color-mix(in srgb, var(--gp-primary) 58%, var(--gp-border-medium));
  --mark-seen-bg-hover: color-mix(in srgb, var(--gp-primary) 16%, var(--gp-surface-card));

  --footer-btn-fg: var(--gp-text-primary);
  --footer-btn-border: var(--gp-border-medium);
  --footer-btn-bg: var(--gp-surface-card);
  --footer-btn-fg-hover: var(--gp-primary-dark);
  --footer-btn-border-hover: color-mix(in srgb, var(--gp-primary) 45%, var(--gp-border-medium));
  --footer-btn-bg-hover: color-mix(in srgb, var(--gp-primary) 9%, var(--gp-surface-card));
  --footer-btn-fg-disabled: var(--gp-text-muted);
  --footer-btn-border-disabled: var(--gp-border-medium);
  --footer-btn-bg-disabled: var(--gp-surface-emphasis);
}

.gp-bell-trigger {
  position: relative;
  color: var(--bell-trigger-fg) !important;
  background: var(--bell-trigger-bg) !important;
  border: 1px solid var(--bell-trigger-border) !important;
  box-shadow: var(--bell-trigger-shadow) !important;
}

.gp-bell-trigger :deep(.p-button-icon) {
  color: inherit !important;
}

.gp-bell-trigger:hover {
  color: var(--bell-trigger-fg-hover) !important;
  background: var(--bell-trigger-bg-hover) !important;
  border-color: var(--bell-trigger-border-hover) !important;
}

.gp-bell-trigger:focus-visible {
  outline: 2px solid var(--gp-primary-light);
  outline-offset: 2px;
}

.gp-bell-trigger:active {
  background: var(--bell-trigger-bg-active) !important;
  border-color: var(--bell-trigger-border-active) !important;
}

.gp-bell-badge {
  position: absolute;
  top: -0.15rem;
  right: -0.15rem;
  min-width: 1.1rem;
  height: 1.1rem;
  border-radius: 999px;
  padding: 0 0.3rem;
  background: var(--gp-danger-strong);
  color: var(--gp-neutral-white);
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
  border: 1px solid var(--gp-border);
  border-radius: 12px;
  background: var(--gp-surface-card);
  box-shadow: var(--gp-shadow-medium);
  z-index: 1200;
  display: flex;
  flex-direction: column;
  color: var(--gp-text-primary);
}

.gp-notification-panel-header {
  padding: 0.8rem 0.9rem 0.5rem;
  border-bottom: 1px solid var(--gp-border);
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
  border: 1px dashed var(--gp-border);
  border-radius: 10px;
  color: var(--gp-text-secondary);
  padding: 0.9rem;
  font-size: 0.88rem;
  background: color-mix(in srgb, var(--gp-surface-card) 92%, var(--gp-primary));
}

.gp-notification-item {
  border: 1px solid var(--gp-border);
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

.gp-notification-item-meta {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  color: var(--gp-text-muted);
  font-size: 0.75rem;
  margin-bottom: 0.35rem;
}

.gp-notification-item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.gp-notification-item-title {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-weight: 700;
  font-size: 0.92rem;
  min-width: 0;
}

.gp-notification-item-title span {
  overflow-wrap: anywhere;
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

.gp-notification-item-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.45rem;
  flex-wrap: wrap;
}

.gp-notification-footer {
  border-top: 1px solid var(--gp-border);
  padding: 0.7rem 0.9rem;
  display: flex;
  justify-content: space-between;
  gap: 0.55rem;
}

.gp-notification-panel :deep(.p-button.p-button-text.gp-notification-item-action) {
  color: var(--mark-seen-fg);
  border: 1px solid var(--mark-seen-border);
  background: var(--mark-seen-bg);
  border-radius: 10px;
  padding: 0.3rem 0.75rem;
}

.gp-notification-panel :deep(.p-button.p-button-text.gp-notification-item-action .p-button-label) {
  color: inherit;
  font-weight: 600;
}

.gp-notification-panel :deep(.p-button.p-button-text.gp-notification-item-action:hover) {
  color: var(--mark-seen-fg-hover);
  border-color: var(--mark-seen-border-hover);
  background: var(--mark-seen-bg-hover);
}

.gp-notification-panel :deep(.p-button.p-button-outlined.p-button-secondary) {
  color: var(--footer-btn-fg);
  border-color: var(--footer-btn-border);
  background: var(--footer-btn-bg);
}

.gp-notification-panel :deep(.p-button.p-button-outlined.p-button-secondary:hover) {
  color: var(--footer-btn-fg-hover);
  border-color: var(--footer-btn-border-hover);
  background: var(--footer-btn-bg-hover);
}

.gp-notification-panel :deep(.p-button.p-button-outlined.p-button-secondary:disabled) {
  color: var(--footer-btn-fg-disabled);
  border-color: var(--footer-btn-border-disabled);
  background: var(--footer-btn-bg-disabled);
}

.gp-notification-panel :deep(.p-tag) {
  font-weight: 700;
}

.gp-notification-panel :deep(.p-tag.p-tag-success) {
  background: var(--gp-success-soft);
  color: var(--gp-success-text);
}

.gp-notification-panel :deep(.p-tag.p-tag-info) {
  background: var(--gp-info-soft);
  color: var(--gp-info-text);
}

.gp-notification-panel :deep(.p-tag.p-tag-warning) {
  background: var(--gp-warning-soft);
  color: var(--gp-warning-text);
}

.gp-notification-panel :deep(.p-tag.p-tag-secondary) {
  background: var(--gp-surface-emphasis);
  color: var(--gp-text-primary);
}

.gp-notification-panel :deep(.p-tag.p-tag-danger) {
  background: var(--gp-danger-soft);
  color: var(--gp-danger-text);
}

@media (max-width: 640px) {
  .gp-notification-panel {
    width: min(420px, calc(100vw - 1rem));
  }
}
</style>

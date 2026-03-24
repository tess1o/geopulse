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

    <teleport to="body">
      <div
        v-if="panelOpen"
        ref="panelRef"
        class="gp-notification-panel"
        :style="panelInlineStyle"
      >
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
                <span class="gp-notification-item-title">{{ itemTitle(item) }}</span>
                <Tag :value="item.deliveryStatus || 'UNKNOWN'" :severity="deliverySeverity(item.deliveryStatus)" />
              </div>
              <div class="gp-notification-item-message">{{ item.message || 'New notification.' }}</div>
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
    </teleport>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
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
const panelRef = ref(null)
const panelOpen = ref(false)
const activeFilter = ref('unread')
const panelInlineStyle = ref({})

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

const itemTitle = (item) => {
  if (item?.title) {
    return item.title
  }
  if (item?.source && item?.type) {
    return `${item.source}: ${item.type}`
  }
  if (item?.source) {
    return `${item.source} notification`
  }
  return 'Notification'
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
  --gp-bell-trigger-fg: var(--gp-text-primary);
  --gp-bell-trigger-bg: var(--gp-surface-white);
  --gp-bell-trigger-border: var(--gp-border-medium);
  --gp-bell-trigger-shadow: var(--gp-shadow-subtle);
  --gp-bell-trigger-fg-hover: var(--gp-primary-dark);
  --gp-bell-trigger-bg-hover: color-mix(in srgb, var(--gp-primary) 9%, var(--gp-surface-white));
  --gp-bell-trigger-border-hover: color-mix(in srgb, var(--gp-primary) 42%, var(--gp-border-medium));
  --gp-bell-trigger-bg-active: color-mix(in srgb, var(--gp-primary) 16%, var(--gp-surface-white));
  --gp-bell-trigger-border-active: color-mix(in srgb, var(--gp-primary) 58%, var(--gp-border-medium));

  --gp-mark-seen-fg: var(--gp-primary-dark);
  --gp-mark-seen-border: color-mix(in srgb, var(--gp-primary) 42%, var(--gp-border-medium));
  --gp-mark-seen-bg: color-mix(in srgb, var(--gp-primary) 9%, var(--gp-surface-white));
  --gp-mark-seen-fg-hover: var(--gp-primary);
  --gp-mark-seen-border-hover: color-mix(in srgb, var(--gp-primary) 58%, var(--gp-border-medium));
  --gp-mark-seen-bg-hover: color-mix(in srgb, var(--gp-primary) 16%, var(--gp-surface-white));

  --gp-footer-btn-fg: var(--gp-text-primary);
  --gp-footer-btn-border: var(--gp-border-medium);
  --gp-footer-btn-bg: var(--gp-surface-white);
  --gp-footer-btn-fg-hover: var(--gp-primary-dark);
  --gp-footer-btn-border-hover: color-mix(in srgb, var(--gp-primary) 45%, var(--gp-border-medium));
  --gp-footer-btn-bg-hover: color-mix(in srgb, var(--gp-primary) 9%, var(--gp-surface-white));
  --gp-footer-btn-fg-disabled: var(--gp-text-muted);
  --gp-footer-btn-border-disabled: var(--gp-border-medium);
  --gp-footer-btn-bg-disabled: var(--gp-surface-gray);
}

.gp-bell-trigger {
  position: relative;
  color: var(--gp-bell-trigger-fg) !important;
  background: var(--gp-bell-trigger-bg) !important;
  border: 1px solid var(--gp-bell-trigger-border) !important;
  box-shadow: var(--gp-bell-trigger-shadow) !important;
}

.gp-bell-trigger :deep(.p-button-icon) {
  color: inherit !important;
}

.gp-bell-trigger:hover {
  color: var(--gp-bell-trigger-fg-hover) !important;
  background: var(--gp-bell-trigger-bg-hover) !important;
  border-color: var(--gp-bell-trigger-border-hover) !important;
}

.gp-bell-trigger:focus-visible {
  outline: 2px solid var(--gp-primary-light);
  outline-offset: 2px;
}

.gp-bell-trigger:active {
  background: var(--gp-bell-trigger-bg-active) !important;
  border-color: var(--gp-bell-trigger-border-active) !important;
}

.gp-bell-badge {
  position: absolute;
  top: -0.15rem;
  right: -0.15rem;
  min-width: 1.1rem;
  height: 1.1rem;
  border-radius: 999px;
  padding: 0 0.3rem;
  background: var(--gp-danger-dark);
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
  border: 1px solid var(--gp-border-light);
  border-radius: 12px;
  background: var(--gp-surface-white);
  box-shadow: var(--gp-shadow-medium);
  z-index: 1200;
  display: flex;
  flex-direction: column;
  color: var(--gp-text-primary);
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

.gp-notification-panel :deep(.p-button.p-button-text.gp-notification-item-action) {
  color: var(--gp-mark-seen-fg);
  border: 1px solid var(--gp-mark-seen-border);
  background: var(--gp-mark-seen-bg);
  border-radius: 10px;
  padding: 0.3rem 0.75rem;
}

.gp-notification-panel :deep(.p-button.p-button-text.gp-notification-item-action .p-button-label) {
  color: inherit;
  font-weight: 600;
}

.gp-notification-panel :deep(.p-button.p-button-text.gp-notification-item-action:hover) {
  color: var(--gp-mark-seen-fg-hover);
  border-color: var(--gp-mark-seen-border-hover);
  background: var(--gp-mark-seen-bg-hover);
}

.gp-notification-panel :deep(.p-button.p-button-outlined.p-button-secondary) {
  color: var(--gp-footer-btn-fg);
  border-color: var(--gp-footer-btn-border);
  background: var(--gp-footer-btn-bg);
}

.gp-notification-panel :deep(.p-button.p-button-outlined.p-button-secondary:hover) {
  color: var(--gp-footer-btn-fg-hover);
  border-color: var(--gp-footer-btn-border-hover);
  background: var(--gp-footer-btn-bg-hover);
}

.gp-notification-panel :deep(.p-button.p-button-outlined.p-button-secondary:disabled) {
  color: var(--gp-footer-btn-fg-disabled);
  border-color: var(--gp-footer-btn-border-disabled);
  background: var(--gp-footer-btn-bg-disabled);
}

.gp-notification-panel :deep(.p-tag) {
  font-weight: 700;
}

.gp-notification-panel :deep(.p-tag.p-tag-success) {
  background: var(--gp-success-light);
  color: var(--gp-success-dark);
}

.gp-notification-panel :deep(.p-tag.p-tag-info) {
  background: var(--gp-info-light);
  color: var(--gp-info-dark);
}

.gp-notification-panel :deep(.p-tag.p-tag-secondary) {
  background: var(--gp-surface-gray);
  color: var(--gp-text-primary);
}

.gp-notification-panel :deep(.p-tag.p-tag-danger) {
  background: var(--gp-danger-light);
  color: var(--gp-danger-dark);
}

.p-dark .gp-notification-bell {
  --gp-bell-trigger-fg: var(--gp-primary-light);
  --gp-bell-trigger-bg: color-mix(in srgb, var(--gp-surface-darker) 80%, var(--gp-primary) 20%);
  --gp-bell-trigger-border: color-mix(in srgb, var(--gp-border-medium) 70%, var(--gp-primary) 30%);
  --gp-bell-trigger-shadow: var(--gp-shadow-subtle);
  --gp-bell-trigger-fg-hover: var(--gp-neutral-white);
  --gp-bell-trigger-bg-hover: color-mix(in srgb, var(--gp-surface-darker) 55%, var(--gp-primary) 45%);
  --gp-bell-trigger-border-hover: color-mix(in srgb, var(--gp-border-medium) 50%, var(--gp-primary-light) 50%);
  --gp-bell-trigger-bg-active: color-mix(in srgb, var(--gp-surface-darker) 45%, var(--gp-primary) 55%);
  --gp-bell-trigger-border-active: color-mix(in srgb, var(--gp-border-medium) 40%, var(--gp-primary-light) 60%);

  --gp-mark-seen-fg: var(--gp-primary-light);
  --gp-mark-seen-border: color-mix(in srgb, var(--gp-primary-light) 65%, var(--gp-border-medium));
  --gp-mark-seen-bg: color-mix(in srgb, var(--gp-primary) 26%, var(--gp-surface-dark));
  --gp-mark-seen-fg-hover: var(--gp-neutral-white);
  --gp-mark-seen-border-hover: color-mix(in srgb, var(--gp-primary-light) 85%, var(--gp-border-medium));
  --gp-mark-seen-bg-hover: color-mix(in srgb, var(--gp-primary) 36%, var(--gp-surface-dark));

  --gp-footer-btn-fg: var(--gp-text-primary);
  --gp-footer-btn-border: var(--gp-border-medium);
  --gp-footer-btn-bg: color-mix(in srgb, var(--gp-surface-dark) 75%, var(--gp-surface-darker));
  --gp-footer-btn-fg-hover: var(--gp-neutral-white);
  --gp-footer-btn-border-hover: color-mix(in srgb, var(--gp-primary-light) 55%, var(--gp-border-medium));
  --gp-footer-btn-bg-hover: color-mix(in srgb, var(--gp-primary) 22%, var(--gp-surface-dark));
  --gp-footer-btn-fg-disabled: var(--gp-text-muted);
  --gp-footer-btn-border-disabled: var(--gp-border-medium);
  --gp-footer-btn-bg-disabled: color-mix(in srgb, var(--gp-surface-dark) 88%, var(--gp-surface-darker));
}

.p-dark .gp-notification-panel :deep(.p-inputswitch.p-disabled) {
  opacity: 0.9;
}

@media (max-width: 640px) {
  .gp-notification-panel {
    width: min(420px, calc(100vw - 1rem));
  }
}
</style>

<template>
  <teleport to="body">
    <div
      v-if="visible"
      class="gp-user-menu-hint"
      role="status"
      :style="positionStyle"
    >
      <div class="gp-user-menu-hint-title">{{ t('nav.userMenu.hint.title') }}</div>
      <p class="gp-user-menu-hint-body">{{ t('nav.userMenu.hint.body') }}</p>
      <div class="gp-user-menu-hint-actions">
        <Button :label="t('ui.appLayout.gotIt')" size="small" @click="dismiss" />
      </div>
    </div>
  </teleport>
</template>

<script setup>
/**
 * One-time pointer to the avatar menu for people who used the pre-2.0 side menu, where profile,
 * settings and logout used to live. Temporary: delete this component (and its catalog keys) once
 * most installs have moved past 2.0.
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import Button from 'primevue/button'
import { useAuthStore } from '@/stores/auth'

const props = defineProps({
  // The avatar button element; null until the parent's template ref is set.
  anchor: {
    type: null,
    default: null
  }
})

const USER_MENU_HINT_STORAGE_KEY = 'geopulse-hint-user-menu-2.0'
// Accounts younger than this never saw the old menu (same cut-off as the onboarding tour).
const NEW_ACCOUNT_WINDOW_MS = 60 * 60 * 1000

const { t } = useI18n()
const { user } = storeToRefs(useAuthStore())

const visible = ref(false)
const anchorRect = ref(null)

const readDismissed = () => {
  try {
    return localStorage.getItem(USER_MENU_HINT_STORAGE_KEY) === '1'
  } catch (_) {
    // Storage blocked: showing it every visit would nag, so treat it as seen.
    return true
  }
}

const isNewAccount = () => {
  const createdAt = Date.parse(user.value?.createdAt || '')
  return Number.isFinite(createdAt) && Date.now() - createdAt < NEW_ACCOUNT_WINDOW_MS
}

const updatePosition = () => {
  const rect = props.anchor?.getBoundingClientRect?.()
  // A hidden anchor (zero size) means the navbar is not showing it; hide the hint with it.
  anchorRect.value = rect && rect.width > 0 ? rect : null
}

// Pinned under the avatar's right edge; the arrow in CSS points back up at it.
const positionStyle = computed(() => {
  const rect = anchorRect.value
  if (!rect) {
    return { display: 'none' }
  }
  return {
    top: `${rect.bottom + 12}px`,
    right: `${Math.max(8, window.innerWidth - rect.right - 4)}px`
  }
})

const dismiss = () => {
  if (!visible.value) {
    return
  }
  visible.value = false
  try {
    localStorage.setItem(USER_MENU_HINT_STORAGE_KEY, '1')
  } catch (_) {
    // Nothing to do: it simply shows again next visit.
  }
}

watch(() => props.anchor, updatePosition)

onMounted(() => {
  if (!user.value || readDismissed() || isNewAccount()) {
    return
  }
  updatePosition()
  visible.value = true
  window.addEventListener('resize', updatePosition)
})

onUnmounted(() => {
  window.removeEventListener('resize', updatePosition)
})

defineExpose({ dismiss })
</script>

<style>
/* Teleported to <body>. Above the timeline sheet (1050) but below PrimeVue modals (1100+), so the
   "What's new" dialog covers it and the hint is waiting once that dialog is dismissed. */
.gp-user-menu-hint {
  position: fixed;
  z-index: 1090;
  width: min(17rem, calc(100vw - 1rem));
  box-sizing: border-box;
  padding: var(--gp-spacing-md) var(--gp-spacing-lg);
  border: 1px solid var(--gp-primary-soft-border);
  border-radius: var(--gp-radius-large);
  background: var(--gp-surface-card);
  box-shadow: var(--gp-shadow-large);
  color: var(--gp-text-primary);
}

.gp-user-menu-hint::before {
  content: '';
  position: absolute;
  top: -7px;
  right: 1rem;
  width: 12px;
  height: 12px;
  border-top: 1px solid var(--gp-primary-soft-border);
  border-left: 1px solid var(--gp-primary-soft-border);
  background: var(--gp-surface-card);
  transform: rotate(45deg);
}

.gp-user-menu-hint-title {
  font-size: 0.9375rem;
  font-weight: 600;
  color: var(--gp-primary-text);
}

.gp-user-menu-hint-body {
  margin: var(--gp-spacing-xs) 0 var(--gp-spacing-md);
  font-size: 0.875rem;
  line-height: 1.45;
  color: var(--gp-text-secondary);
}

.gp-user-menu-hint-actions {
  display: flex;
  justify-content: flex-end;
}
</style>

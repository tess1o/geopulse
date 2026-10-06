<template>
  <div class="gp-user-menu">
    <button
      ref="triggerRef"
      type="button"
      class="gp-user-menu-trigger"
      :class="{ 'gp-user-menu-trigger--open': open }"
      :aria-label="t('nav.userMenu.open')"
      aria-haspopup="true"
      :aria-expanded="open"
      :aria-controls="panelId"
      @click="toggle"
    >
      <Avatar :image="avatarImage" shape="circle" class="gp-user-menu-avatar" />
      <!-- Phones hide the navbar bell (no room next to the date picker), so the avatar carries its count. -->
      <span v-if="notificationUnreadCount > 0" class="gp-user-menu-unread-badge" aria-hidden="true">{{ unreadBadgeValue }}</span>
      <span v-if="updateAvailable" class="gp-user-menu-update-dot" aria-hidden="true" />
    </button>

    <UserMenuHint ref="hintRef" :anchor="triggerRef" />

    <!-- PrimeVue's overlay layer (~1000) sits under the timeline sheet (1050 on phones); the base lifts the
         menu to the notification panel's layer (3200) so it opens above everything on the page. -->
    <Popover
      ref="popover"
      :id="panelId"
      :baseZIndex="2200"
      class="gp-user-menu-panel"
      @show="open = true"
      @hide="open = false"
    >
      <div class="gp-user-menu-content">
        <div class="gp-user-menu-identity">
          <Avatar :image="avatarImage" shape="circle" size="large" />
          <div class="gp-user-menu-identity-text">
            <div class="gp-user-menu-name-row">
              <span class="gp-user-menu-name">{{ userName }}</span>
              <span v-if="canViewAdmin" class="gp-user-menu-badge">{{ t('nav.userMenu.adminBadge') }}</span>
            </div>
            <span v-if="userEmail" class="gp-user-menu-email">{{ userEmail }}</span>
          </div>
        </div>

        <div class="gp-user-menu-divider" />

        <nav class="gp-user-menu-links">
          <router-link to="/app/notifications" class="gp-user-menu-item gp-user-menu-notifications" @click="hide">
            <i class="pi pi-bell gp-user-menu-item-icon" />
            <span class="gp-user-menu-item-label">{{ t('nav.items.notifications') }}</span>
            <span v-if="notificationUnreadCount > 0" class="gp-user-menu-count">{{ unreadBadgeValue }}</span>
          </router-link>
          <router-link
            v-for="link in accountLinks"
            :key="link.key"
            :to="link.to"
            class="gp-user-menu-item"
            @click="hide"
          >
            <i :class="link.icon" class="gp-user-menu-item-icon" />
            <span>{{ link.label }}</span>
          </router-link>
        </nav>

        <!-- Shortcuts to the two most-used admin pages; the drawer lists all of them. -->
        <template v-if="canViewAdmin">
          <div class="gp-user-menu-divider" />
          <nav class="gp-user-menu-links">
            <router-link
              v-for="link in adminLinks"
              :key="link.key"
              :to="link.to"
              class="gp-user-menu-item"
              @click="hide"
            >
              <i :class="link.icon" class="gp-user-menu-item-icon" />
              <span>{{ link.label }}</span>
            </router-link>
          </nav>
        </template>

        <div class="gp-user-menu-divider" />

        <div class="gp-user-menu-settings">
          <div class="gp-user-menu-setting">
            <span :id="`${panelId}-theme`" class="gp-user-menu-setting-label">{{ t('nav.theme.title') }}</span>
            <SelectButton
              :modelValue="themeMode"
              :options="themeOptions"
              optionValue="value"
              dataKey="value"
              :allowEmpty="false"
              :ariaLabelledby="`${panelId}-theme`"
              class="gp-user-menu-theme"
              @update:modelValue="setThemeMode"
            >
              <template #option="{ option }">
                <i :class="option.icon" />
                <span>{{ option.label }}</span>
              </template>
            </SelectButton>
          </div>

          <div class="gp-user-menu-setting">
            <label :for="`${panelId}-language`" class="gp-user-menu-setting-label">{{ t('nav.userMenu.language') }}</label>
            <Select
              :inputId="`${panelId}-language`"
              :modelValue="locale"
              :options="localeOptions"
              optionLabel="label"
              optionValue="value"
              appendTo="self"
              size="small"
              class="gp-user-menu-language"
              @update:modelValue="changeLanguage"
            />
          </div>
        </div>

        <div class="gp-user-menu-divider" />

        <router-link to="/app/help" class="gp-user-menu-item" @click="hide">
          <i class="pi pi-question-circle gp-user-menu-item-icon" />
          <span>{{ t('nav.items.help') }}</span>
        </router-link>
        <button type="button" class="gp-user-menu-item gp-user-menu-logout" @click="handleLogout">
          <i class="pi pi-sign-out gp-user-menu-item-icon" />
          <span>{{ t('nav.logout') }}</span>
        </button>

        <div class="gp-user-menu-version">
          <span>{{ t('nav.version') }} {{ appVersion }}</span>
          <a
            v-if="updateAvailable && latestVersion"
            :href="releaseUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="gp-user-menu-version-update"
          >
            <i class="pi pi-arrow-circle-up" />
            <span>{{ t('nav.newVersionAvailable', { version: latestVersion }) }}</span>
          </a>
        </div>
      </div>
    </Popover>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import Avatar from 'primevue/avatar'
import Popover from 'primevue/popover'
import Select from 'primevue/select'
import SelectButton from 'primevue/selectbutton'
import { useAuthStore } from '@/stores/auth'
import { useVersionStore } from '@/stores/version'
import { useNotificationsStore } from '@/stores/notifications'
import { useThemeMode } from '@/composables/useThemeMode'
import { useLocale } from '@/composables/useLocale'
import { useErrorHandler } from '@/composables/useErrorHandler'
import UserMenuHint from './UserMenuHint.vue'

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const versionStore = useVersionStore()
const { handleError } = useErrorHandler()
const { themeMode, setThemeMode, themeModes } = useThemeMode()
const { locale, localeOptions, setLocale } = useLocale()

const { userName, userEmail, userAvatar, canViewAdmin, demoReadOnly } = storeToRefs(authStore)
const { unreadCount: notificationUnreadCount } = storeToRefs(useNotificationsStore())

const popover = ref()
const open = ref(false)
const panelId = `gp-user-menu-${Math.random().toString(36).slice(2, 10)}`

const appVersion = ref('')
const latestVersion = ref('')
const updateAvailable = ref(false)
const DEFAULT_RELEASE_URL = 'https://github.com/tess1o/geopulse/releases'
const releaseUrl = ref(DEFAULT_RELEASE_URL)

const unreadBadgeValue = computed(() => (notificationUnreadCount.value > 99 ? '99+' : notificationUnreadCount.value))

// Same fallback the profile page shows for a user who never picked an avatar.
const avatarImage = computed(() => userAvatar.value || '/avatars/avatar1.png')

const accountLinks = computed(() => [
  { label: t('nav.items.profile'), icon: 'pi pi-user', to: '/app/profile', key: 'profile' },
  { label: t('nav.items.preferences'), icon: 'pi pi-cog', to: '/app/timeline/preferences', key: 'preferences' }
])

const adminLinks = computed(() => [
  { label: t('nav.userMenu.adminDashboard'), icon: 'pi pi-th-large', to: '/app/admin', key: 'admin-dashboard' },
  { label: t('nav.items.admin-settings'), icon: 'pi pi-sliders-h', to: '/app/admin/settings', key: 'admin-settings' }
])

const themeOptions = computed(() => [
  { value: themeModes.LIGHT, label: t('nav.theme.modes.light'), icon: 'pi pi-sun' },
  { value: themeModes.DARK, label: t('nav.theme.modes.dark'), icon: 'pi pi-moon' },
  { value: themeModes.SYSTEM, label: t('nav.theme.modes.system'), icon: 'pi pi-desktop' }
])

const triggerRef = ref(null)
const hintRef = ref(null)

const toggle = (event) => {
  // Opening the menu is the hint's whole point, so it counts as dismissing it.
  hintRef.value?.dismiss()
  popover.value?.toggle(event)
}
const hide = () => popover.value?.hide()

// Switch immediately so the menu itself re-renders in the new language, then save to the profile.
// Demo read-only accounts keep the switch for this session only, like other blocked profile edits.
const changeLanguage = async (next) => {
  const previous = locale.value
  if (!next || next === previous) {
    return
  }

  await setLocale(next, { persistTo: 'profile' })
  if (demoReadOnly.value) {
    return
  }

  try {
    await authStore.updateLanguage(next)
  } catch (error) {
    await setLocale(previous, { persistTo: 'profile' })
    handleError(error, { summary: t('nav.userMenu.languageSaveFailed') })
  }
}

const handleLogout = async () => {
  hide()
  try {
    await authStore.logout()
  } catch (error) {
    console.error('Logout error:', error)
  }
  await router.push('/')
}

const fetchVersionStatus = async () => {
  try {
    const response = await versionStore.fetchStatus()
    appVersion.value = response.currentVersion || response.version || 'Unknown'
    latestVersion.value = response.latestVersion || ''
    updateAvailable.value = response.updateAvailable === true
    releaseUrl.value = response.releaseUrl || DEFAULT_RELEASE_URL
  } catch (error) {
    console.warn('Failed to fetch version status:', error)
    latestVersion.value = ''
    updateAvailable.value = false
    releaseUrl.value = DEFAULT_RELEASE_URL

    try {
      const response = await versionStore.fetchVersion()
      appVersion.value = response.version || 'Unknown'
    } catch (fallbackError) {
      console.warn('Failed to fetch app version fallback:', fallbackError)
      appVersion.value = 'Unknown'
    }
  }
}

onMounted(fetchVersionStatus)
</script>

<style scoped>
.gp-user-menu {
  position: relative;
  display: flex;
  align-items: center;
}

.gp-user-menu-trigger {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 2px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  cursor: pointer;
  transition: box-shadow 0.15s ease;
}

.gp-user-menu-trigger:hover,
.gp-user-menu-trigger--open {
  box-shadow: 0 0 0 2px var(--gp-primary);
}

.gp-user-menu-trigger:focus-visible {
  outline: 2px solid var(--gp-primary);
  outline-offset: 2px;
}

.gp-user-menu-avatar {
  width: 2.25rem;
  height: 2.25rem;
}

.gp-user-menu-unread-badge {
  display: none;
  position: absolute;
  top: -4px;
  right: -6px;
  min-width: 1.125rem;
  height: 1.125rem;
  padding: 0 0.25rem;
  box-sizing: border-box;
  border: 2px solid var(--gp-surface-card);
  border-radius: var(--gp-radius-pill);
  background: var(--gp-danger-strong);
  color: var(--gp-neutral-white);
  font-size: 0.625rem;
  font-weight: 700;
  line-height: 0.875rem;
  text-align: center;
}

.gp-user-menu-update-dot {
  position: absolute;
  top: 0;
  right: 0;
  width: 0.625rem;
  height: 0.625rem;
  border: 2px solid var(--gp-surface-card);
  border-radius: 50%;
  background: var(--gp-warning);
}

@media (max-width: 768px) {
  .gp-user-menu-avatar {
    width: 2rem;
    height: 2rem;
  }
}

/* Matches the breakpoint where the navbars hide the bell. */
@media (max-width: 480px) {
  .gp-user-menu-unread-badge {
    display: block;
  }

  .gp-user-menu-unread-badge + .gp-user-menu-update-dot {
    display: none;
  }
}
</style>

<style>
/* The popover is teleported to <body>, so its styles cannot be scoped. */
.gp-user-menu-panel.p-popover {
  width: min(19rem, calc(100vw - 1rem));
}

.gp-user-menu-panel .p-popover-content {
  padding: var(--gp-spacing-xs);
}

.gp-user-menu-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.gp-user-menu-identity {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-md);
  padding: var(--gp-spacing-sm) var(--gp-spacing-sm) var(--gp-spacing-md);
}

.gp-user-menu-identity-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.gp-user-menu-name-row {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  min-width: 0;
}

.gp-user-menu-name {
  font-size: 0.9375rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gp-user-menu-badge {
  flex-shrink: 0;
  padding: 1px 8px;
  border-radius: var(--gp-radius-pill);
  background: var(--gp-primary-soft);
  color: var(--gp-primary-text);
  font-size: 0.6875rem;
  font-weight: 600;
}

.gp-user-menu-email {
  font-size: 0.8125rem;
  color: var(--gp-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gp-user-menu-divider {
  height: 1px;
  margin: 2px var(--gp-spacing-sm);
  background: var(--gp-border);
}

.gp-user-menu-links {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.gp-user-menu-item {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-md);
  width: 100%;
  min-height: 2.5rem;
  padding: 0 var(--gp-spacing-md);
  border: 0;
  border-radius: var(--gp-radius-medium);
  background: transparent;
  color: var(--gp-text-primary);
  font: inherit;
  font-size: 0.875rem;
  text-align: left;
  text-decoration: none;
  cursor: pointer;
}

.gp-user-menu-item:hover,
.gp-user-menu-item:focus-visible {
  background: var(--gp-surface-hover);
  outline: none;
}

.gp-user-menu-item.router-link-active {
  color: var(--gp-primary-text);
}

.gp-user-menu-item-icon {
  font-size: 1rem;
  color: var(--gp-text-secondary);
}

.gp-user-menu-item.router-link-active .gp-user-menu-item-icon {
  color: var(--gp-primary-text);
}

.gp-user-menu-item-label {
  flex: 1;
}

.gp-user-menu-count {
  min-width: 1.25rem;
  padding: 0 0.375rem;
  border-radius: var(--gp-radius-pill);
  background: var(--gp-danger-strong);
  color: var(--gp-neutral-white);
  font-size: 0.6875rem;
  font-weight: 700;
  line-height: 1.25rem;
  text-align: center;
}

/* The navbar bell covers notifications everywhere except phones. */
.gp-user-menu-notifications {
  display: none;
}

@media (max-width: 480px) {
  .gp-user-menu-notifications {
    display: flex;
  }
}

.gp-user-menu-settings {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-md);
  padding: var(--gp-spacing-sm) var(--gp-spacing-md) var(--gp-spacing-md);
}

.gp-user-menu-setting {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-xs);
}

.gp-user-menu-setting-label {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--gp-text-secondary);
}

.gp-user-menu-theme {
  display: flex;
  width: 100%;
}

.gp-user-menu-theme .p-togglebutton {
  flex: 1;
  gap: 0.375rem;
  padding: 0.375rem 0.25rem;
  font-size: 0.8125rem;
}

.gp-user-menu-language {
  width: 100%;
}

.gp-user-menu-version {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--gp-spacing-xs);
  margin-top: var(--gp-spacing-xs);
  padding: var(--gp-spacing-sm) var(--gp-spacing-md) var(--gp-spacing-xs);
  border-top: 1px solid var(--gp-border);
  font-size: 0.75rem;
  color: var(--gp-text-secondary);
}

.gp-user-menu-version-update {
  display: inline-flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
  padding: 2px 8px;
  border-radius: var(--gp-radius-pill);
  background: var(--gp-warning-soft);
  color: var(--gp-warning-text);
  font-weight: 600;
  text-decoration: none;
}

.gp-user-menu-version-update:hover {
  opacity: 0.85;
}
</style>

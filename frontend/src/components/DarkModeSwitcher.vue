<template>
  <div class="dark-mode-switcher">
    <Button
      :icon="currentTheme.icon"
      :label="props.showLabel ? currentTheme.label : undefined"
      @click="toggleThemeMenu"
      severity="secondary"
      outlined
      size="small"
      v-tooltip.bottom="t('ui.darkModeSwitcher.themeTooltip', { label: currentTheme.label })"
      aria-haspopup="true"
      :aria-controls="menuId"
      :aria-label="t('ui.darkModeSwitcher.themeAriaLabel', { label: currentTheme.label })"
    />
    <Menu ref="themeMenu" :id="menuId" :model="themeMenuItems" popup />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Button from 'primevue/button'
import Menu from 'primevue/menu'
import { useThemeMode } from '@/composables/useThemeMode'

const props = defineProps({
  showLabel: {
    type: Boolean,
    default: false
  }
})

const { t } = useI18n()
const { themeMode, setThemeMode, themeModes } = useThemeMode()
const themeMenu = ref()
const menuId = `theme-mode-menu-${Math.random().toString(36).slice(2, 10)}`

const themeDefinitions = computed(() => ({
  [themeModes.LIGHT]: {
    label: t('ui.darkModeSwitcher.light'),
    icon: 'pi pi-sun'
  },
  [themeModes.DARK]: {
    label: t('ui.darkModeSwitcher.dark'),
    icon: 'pi pi-moon'
  },
  [themeModes.SYSTEM]: {
    label: t('ui.darkModeSwitcher.system'),
    icon: 'pi pi-desktop'
  }
}))

const currentTheme = computed(() => themeDefinitions.value[themeMode.value] || themeDefinitions.value[themeModes.SYSTEM])

const createThemeMenuItem = (mode, label, icon) => ({
  label: themeMode.value === mode ? t('ui.darkModeSwitcher.currentSuffix', { label }) : label,
  icon,
  command: () => setThemeMode(mode)
})

const themeMenuItems = computed(() => [
  createThemeMenuItem(themeModes.LIGHT, t('ui.darkModeSwitcher.light'), 'pi pi-sun'),
  createThemeMenuItem(themeModes.DARK, t('ui.darkModeSwitcher.dark'), 'pi pi-moon'),
  createThemeMenuItem(themeModes.SYSTEM, t('ui.darkModeSwitcher.system'), 'pi pi-desktop')
])

const toggleThemeMenu = (event) => {
  themeMenu.value?.toggle(event)
}
</script>

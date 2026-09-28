<template>
  <div class="locale-switcher">
    <Button
      icon="pi pi-globe"
      :label="showLabel ? currentOption.label : undefined"
      @click="toggleMenu"
      severity="secondary"
      outlined
      size="small"
      v-tooltip.bottom="ariaLabel"
      aria-haspopup="true"
      :aria-controls="menuId"
      :aria-label="ariaLabel"
    />
    <Menu ref="menu" :id="menuId" :model="menuItems" popup />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import Button from 'primevue/button'
import Menu from 'primevue/menu'
import { useI18n } from 'vue-i18n'
import { useLocale } from '@/composables/useLocale'

defineProps({
  showLabel: {
    type: Boolean,
    default: false
  }
})

const { t } = useI18n()
const { locale, localeOptions, setLocale } = useLocale()
const menu = ref()
const menuId = `locale-switcher-menu-${Math.random().toString(36).slice(2, 10)}`

// Endonym labels ('English', 'Українська') come from useLocale.js and are deliberately never
// translated -- see LOCALE_OPTIONS there.
const currentOption = computed(() => localeOptions.find(option => option.value === locale.value) || localeOptions[0])
const ariaLabel = computed(() => t('common.locale.ariaLabel', { language: currentOption.value.label }))

const menuItems = computed(() => localeOptions.map(option => ({
  label: locale.value === option.value ? `${option.label} ${t('common.locale.current')}` : option.label,
  command: () => setLocale(option.value)
})))

const toggleMenu = (event) => {
  menu.value?.toggle(event)
}
</script>

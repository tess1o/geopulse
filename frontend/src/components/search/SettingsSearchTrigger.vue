<template>
  <div class="settings-search" :class="{ 'settings-search--inline': isInline }">
    <AutoComplete
      v-if="isInline"
      ref="inlineSearchRef"
      v-model="searchQuery"
      :suggestions="filteredResults"
      :placeholder="placeholder"
      :min-length="1"
      :delay="120"
      option-label="displayName"
      force-selection
      @complete="handleSearch"
      @item-select="handleSelect"
    >
      <template #option="{ option }">
        <div class="settings-search-item">
          <i :class="option.icon || 'pi pi-sliders-h'" class="settings-search-item-icon"></i>
          <div class="settings-search-item-content">
            <div class="settings-search-item-title">{{ option.displayName }}</div>
            <div class="settings-search-item-subtitle">{{ option.metaLine }}</div>
          </div>
        </div>
      </template>
    </AutoComplete>

    <Button
      v-else
      type="button"
      icon="pi pi-search"
      :label="buttonLabel"
      :size="buttonSize"
      severity="secondary"
      outlined
      class="settings-search-trigger"
      @click="openSearch"
    />

    <Popover
      ref="searchPopoverRef"
      class="settings-search-popover"
      @hide="resetSearch"
    >
      <div class="settings-search-popover-content" @keydown.esc="closeSearch">
        <AutoComplete
          ref="popoverSearchRef"
          v-model="searchQuery"
          :suggestions="filteredResults"
          :placeholder="placeholder"
          :min-length="1"
          :delay="120"
          option-label="displayName"
          force-selection
          @complete="handleSearch"
          @item-select="handleSelect"
        >
          <template #option="{ option }">
            <div class="settings-search-item">
              <i :class="option.icon || 'pi pi-sliders-h'" class="settings-search-item-icon"></i>
              <div class="settings-search-item-content">
                <div class="settings-search-item-title">{{ option.displayName }}</div>
                <div class="settings-search-item-subtitle">{{ option.metaLine }}</div>
              </div>
            </div>
          </template>
        </AutoComplete>
      </div>
    </Popover>
  </div>
</template>

<script setup>
import { computed, nextTick, ref } from 'vue'
import { storeToRefs } from 'pinia'
import Button from 'primevue/button'
import Popover from 'primevue/popover'
import AutoComplete from 'primevue/autocomplete'
import { useAuthStore } from '@/stores/auth'
import { buildSettingsIndexForPage } from '@/constants/globalSearchRegistry'
import { searchAndRankItems } from '@/utils/globalSearchScoring'

const props = defineProps({
  pageKey: {
    type: String,
    required: true,
    validator: (value) => ['profile', 'timeline', 'admin'].includes(value)
  },
  placeholder: {
    type: String,
    default: 'Search settings...'
  },
  triggerMode: {
    type: String,
    default: 'icon',
    validator: (value) => ['icon', 'inline'].includes(value)
  },
  buttonLabel: {
    type: String,
    default: 'Find Setting'
  },
  buttonSize: {
    type: String,
    default: null
  }
})

const emit = defineEmits(['navigate'])

const authStore = useAuthStore()
const { isAdmin } = storeToRefs(authStore)

const searchQuery = ref('')
const filteredResults = ref([])
const inlineSearchRef = ref(null)
const popoverSearchRef = ref(null)
const searchPopoverRef = ref(null)

const isInline = computed(() => props.triggerMode === 'inline')

const scopedSettingItems = computed(() => {
  return buildSettingsIndexForPage(props.pageKey, isAdmin.value).map((item) => ({
    id: item.id,
    displayName: item.title,
    metaLine: item.subtitle,
    icon: item.icon,
    to: item.to,
    tab: item.tab,
    setting: item.setting,
    keywords: item.keywords || []
  }))
})

const focusInputFromRef = (inputRef) => {
  nextTick(() => {
    const input = inputRef.value?.$el?.querySelector('input')
    input?.focus()
  })
}

const openSearch = (event) => {
  searchPopoverRef.value?.toggle(event)
  focusInputFromRef(popoverSearchRef)
}

const closeSearch = () => {
  searchPopoverRef.value?.hide()
}

const resetSearch = () => {
  searchQuery.value = ''
  filteredResults.value = []
}

const handleSearch = (event) => {
  const query = event.query?.trim() || ''

  if (query.length < 1) {
    filteredResults.value = []
    return
  }

  filteredResults.value = searchAndRankItems(query, scopedSettingItems.value, { minScore: 120 })
    .map((entry) => entry.item)
    .slice(0, 18)
}

const handleSelect = (event) => {
  const item = event.value
  if (!item) return

  emit('navigate', item)

  if (!isInline.value) {
    closeSearch()
  }

  resetSearch()

  if (isInline.value) {
    focusInputFromRef(inlineSearchRef)
  }
}
</script>

<style scoped>
.settings-search {
  display: flex;
  align-items: center;
}

.settings-search--inline {
  width: 100%;
}

.settings-search-trigger {
  white-space: nowrap;
}

:deep(.settings-search-trigger.p-button.p-button-outlined) {
  border-color: var(--gp-border-medium);
  color: var(--gp-text-primary);
  background: transparent;
}

:deep(.settings-search-trigger.p-button.p-button-outlined:hover) {
  border-color: var(--gp-primary);
  color: var(--gp-primary);
  background: rgba(59, 130, 246, 0.08);
}

:deep(.settings-search-trigger.p-button.p-button-outlined:focus-visible) {
  border-color: var(--gp-primary);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.2);
}

.settings-search-popover-content {
  width: min(520px, 68vw);
}

.settings-search-popover-content :deep(.p-autocomplete) {
  width: 100%;
}

.settings-search-popover-content :deep(.p-autocomplete-input) {
  width: 100%;
  padding: 0.45rem 0.65rem;
  font-size: 0.92rem;
}

.settings-search-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.35rem 0;
}

.settings-search-item-icon {
  font-size: 1rem;
  color: var(--gp-primary);
  min-width: 18px;
}

.settings-search-item-content {
  min-width: 0;
}

.settings-search-item-title {
  font-weight: 600;
  color: var(--gp-text-primary);
}

.settings-search-item-subtitle {
  font-size: 0.82rem;
  color: var(--gp-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

:deep(.settings-search-popover .p-popover-content) {
  padding: 0.625rem;
}

@media (max-width: 768px) {
  .settings-search-popover-content {
    width: min(520px, calc(100vw - 1.25rem));
  }

  .settings-search-trigger {
    padding-left: 0.55rem;
    padding-right: 0.55rem;
  }

  .settings-search-trigger :deep(.p-button-label) {
    display: none;
  }
}
</style>

<style>
/* Global overrides for teleported settings search popover in dark mode */
.p-dark .settings-search-popover.p-popover {
  background: #0f172a !important;
  border-color: rgba(148, 163, 184, 0.35) !important;
  box-shadow: 0 14px 28px rgba(2, 6, 23, 0.55) !important;
}

.p-dark .settings-search-popover.p-popover .p-popover-content {
  background: #0f172a !important;
}

.p-dark .settings-search-popover.p-popover::after {
  border-bottom-color: #0f172a !important;
}

.p-dark .settings-search-popover.p-popover::before {
  border-bottom-color: rgba(148, 163, 184, 0.35) !important;
}

.p-dark .settings-search-popover.p-popover.p-popover-flipped::after {
  border-top-color: #0f172a !important;
}

.p-dark .settings-search-popover.p-popover.p-popover-flipped::before {
  border-top-color: rgba(148, 163, 184, 0.35) !important;
}

.p-dark .settings-search-popover .p-autocomplete-input {
  background: #1e293b !important;
  color: #e2e8f0 !important;
  border-color: rgba(148, 163, 184, 0.35) !important;
}
</style>

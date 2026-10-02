<template>
  <div class="live-friends-filter">
    <div class="filter-desktop">
      <div class="filter-main">
        <MultiSelect
            v-model="selectionModel"
            :options="friendOptions"
            optionLabel="label"
            optionValue="key"
            filter
            display="chip"
            :maxSelectedLabels="2"
            :disabled="totalFriends === 0"
            class="friends-select"
            :placeholder="t('friends.liveFilter.selectPlaceholder')"
        >
          <template #option="slotProps">
            <FriendFilterOptionRow :option="slotProps.option" />
          </template>

          <template #value="slotProps">
            <span v-if="slotProps.value?.length" class="selected-summary">
              {{ getSelectionSummary(slotProps.value) }}
            </span>
            <span v-else class="selected-placeholder">{{ slotProps.placeholder }}</span>
          </template>
        </MultiSelect>

        <div class="filter-summary">{{ summaryLabel }}</div>

        <Select
            v-model="trailRangeModel"
            :options="trailRangeSelectOptions"
            optionLabel="label"
            optionValue="value"
            class="trail-range-select"
            :aria-label="t('friends.liveFilter.trailDurationLabel')"
        />
      </div>

      <div class="filter-actions">
        <FriendFilterQuickActions
            :disabled="totalFriends === 0"
            @select-all="selectAllFriends"
            @select-none="clearSelection"
            @select-online="selectOnlineFriends"
        />
      </div>
    </div>

    <div class="filter-mobile">
      <Button
          :label="mobileButtonLabel"
          icon="pi pi-filter"
          outlined
          class="mobile-filter-trigger"
          :disabled="totalFriends === 0"
          @click="mobileDialogVisible = true"
      />
    </div>

    <Dialog
        v-model:visible="mobileDialogVisible"
        :header="t('friends.liveFilter.mobileFilterHeader')"
        modal
        position="bottom"
        :draggable="false"
        :style="{ width: '100%', maxWidth: '100%', margin: 0 }"
        class="mobile-filter-dialog"
    >
      <div class="mobile-dialog-content">
        <p class="mobile-summary">{{ summaryLabel }}</p>

        <div class="mobile-trail-range">
          <label class="mobile-trail-range-label" for="mobileTrailRangeSelect">{{ t('friends.liveFilter.trailDurationLabel') }}</label>
          <Select
              input-id="mobileTrailRangeSelect"
              v-model="trailRangeModel"
              :options="trailRangeSelectOptions"
              optionLabel="label"
              optionValue="value"
              class="mobile-trail-range-select"
              :aria-label="t('friends.liveFilter.trailDurationLabel')"
          />
        </div>

        <MultiSelect
            v-model="selectionModel"
            :options="friendOptions"
            optionLabel="label"
            optionValue="key"
            filter
            display="chip"
            :maxSelectedLabels="1"
            :disabled="totalFriends === 0"
            class="mobile-friends-select"
            :placeholder="t('friends.liveFilter.selectPlaceholder')"
        >
          <template #option="slotProps">
            <FriendFilterOptionRow :option="slotProps.option" />
          </template>
        </MultiSelect>

        <div class="mobile-actions">
          <FriendFilterQuickActions
              :disabled="totalFriends === 0"
              @select-all="selectAllFriends"
              @select-none="clearSelection"
              @select-online="selectOnlineFriends"
          />
        </div>

        <Button :label="t('friends.liveFilter.doneButton')" class="mobile-done-button" @click="mobileDialogVisible = false" />
      </div>
    </Dialog>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Select from 'primevue/select'
import FriendFilterOptionRow from '@/components/friends/FriendFilterOptionRow.vue'
import FriendFilterQuickActions from '@/components/friends/FriendFilterQuickActions.vue'
import { FRIEND_TRAIL_RANGE_OPTIONS, normalizeFriendTrailRange } from '@/utils/friendsTrailRange'

const { t } = useI18n()
const ONLINE_WINDOW_MS = 15 * 60 * 1000

const props = defineProps({
  friends: {
    type: Array,
    default: () => []
  },
  modelValue: {
    type: Array,
    default: () => []
  },
  trailRange: {
    type: String,
    default: '1h'
  },
  trailRangeOptions: {
    type: Array,
    default: () => FRIEND_TRAIL_RANGE_OPTIONS
  }
})

const emit = defineEmits(['update:modelValue', 'update:trailRange'])
const mobileDialogVisible = ref(false)

const getFriendKey = (friend) => {
  const key = friend?.friendId || friend?.userId || friend?.id || friend?.email
  return key !== null && key !== undefined ? String(key) : null
}

const isFriendOnline = (friend) => {
  const lastSeenValue = friend?.lastSeen || friend?.timestamp
  if (!lastSeenValue) {
    return false
  }

  const timestamp = Date.parse(lastSeenValue)
  if (Number.isNaN(timestamp)) {
    return false
  }

  return Date.now() - timestamp <= ONLINE_WINDOW_MS
}

const friendOptions = computed(() => {
  const options = []
  const seenKeys = new Set()

  props.friends.forEach(friend => {
    const key = getFriendKey(friend)
    if (!key || seenKeys.has(key)) {
      return
    }

    seenKeys.add(key)
    options.push({
      key,
      label: friend.fullName || friend.name || friend.email || t('friends.liveFilter.friendFallbackLabel'),
      email: friend.email || t('friends.liveFilter.noEmailFallback'),
      avatar: friend.avatar,
      isOnline: isFriendOnline(friend)
    })
  })

  return options
})

const optionKeys = computed(() => friendOptions.value.map(option => option.key))
const optionLabelByKey = computed(() => {
  const lookup = new Map()
  friendOptions.value.forEach(option => lookup.set(option.key, option.label))
  return lookup
})

const normalizeSelection = (selection) => {
  if (!Array.isArray(selection)) {
    return []
  }

  const selectionSet = new Set(
      selection
          .map(key => (key !== null && key !== undefined ? String(key) : null))
          .filter(Boolean)
  )

  return optionKeys.value.filter(key => selectionSet.has(key))
}

const selectionModel = computed({
  get: () => normalizeSelection(props.modelValue),
  set: (value) => emit('update:modelValue', normalizeSelection(value))
})

const trailRangeSelectOptions = computed(() => {
  if (!Array.isArray(props.trailRangeOptions) || props.trailRangeOptions.length === 0) {
    return FRIEND_TRAIL_RANGE_OPTIONS
  }

  return props.trailRangeOptions.map((option) => ({
    value: normalizeFriendTrailRange(option?.value),
    label: option?.label || option?.value || ''
  }))
})

const trailRangeModel = computed({
  get: () => normalizeFriendTrailRange(props.trailRange),
  set: (value) => emit('update:trailRange', normalizeFriendTrailRange(value))
})

const totalFriends = computed(() => optionKeys.value.length)
const selectedCount = computed(() => selectionModel.value.length)

const summaryLabel = computed(() => {
  if (totalFriends.value === 0) {
    return t('friends.liveFilter.summary.none')
  }

  if (selectedCount.value === 0) {
    return t('friends.liveFilter.summary.noneSelected')
  }

  if (selectedCount.value === totalFriends.value) {
    return t('friends.liveFilter.summary.allSelected', { count: totalFriends.value })
  }

  return t('friends.liveFilter.summary.partialSelected', { selected: selectedCount.value, total: totalFriends.value })
})

const mobileButtonLabel = computed(() => {
  if (selectedCount.value === totalFriends.value) {
    return t('friends.liveFilter.mobileButton.allSelected')
  }

  return t('friends.liveFilter.mobileButton.partial', { count: selectedCount.value })
})

const getSelectionSummary = (keys) => {
  if (!Array.isArray(keys) || keys.length === 0) {
    return t('friends.liveFilter.selectionSummary.none')
  }

  if (keys.length === totalFriends.value) {
    return t('friends.liveFilter.selectionSummary.all')
  }

  if (keys.length === 1) {
    return optionLabelByKey.value.get(keys[0]) || t('friends.liveFilter.selectionSummary.oneFriend')
  }

  return t('friends.liveFilter.selectionSummary.multiple', { count: keys.length })
}

const selectAllFriends = () => {
  emit('update:modelValue', [...optionKeys.value])
}

const clearSelection = () => {
  emit('update:modelValue', [])
}

const selectOnlineFriends = () => {
  const onlineKeys = friendOptions.value
      .filter(option => option.isOnline)
      .map(option => option.key)

  emit('update:modelValue', onlineKeys)
}
</script>

<style scoped>
.live-friends-filter {
  width: 100%;
}

.filter-desktop {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.75rem;
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-card);
}

.filter-main {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex: 1;
  min-width: 0;
}

.friends-select {
  width: min(100%, 560px);
}

.filter-summary {
  flex-shrink: 0;
  font-size: 0.85rem;
  color: var(--gp-text-secondary);
}

.trail-range-select {
  width: 7rem;
  flex-shrink: 0;
}

.filter-actions {
  flex-shrink: 0;
}

.selected-summary {
  color: var(--gp-text-primary);
  font-weight: 500;
}

.selected-placeholder {
  color: var(--gp-text-secondary);
}

.filter-mobile {
  display: none;
}

.mobile-filter-trigger {
  width: 100%;
}

.mobile-dialog-content {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.mobile-summary {
  margin: 0;
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
}

.mobile-trail-range {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.mobile-trail-range-label {
  font-size: 0.8rem;
  color: var(--gp-text-secondary);
}

.mobile-trail-range-select {
  width: 100%;
}

.mobile-friends-select {
  width: 100%;
}

.mobile-actions {
  width: 100%;
}

.mobile-done-button {
  width: 100%;
}

@media (max-width: 768px) {
  .filter-desktop {
    display: none;
  }

  .filter-mobile {
    display: block;
  }
}
</style>

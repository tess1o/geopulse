<template>
  <div class="friend-option-row">
    <Avatar
        :image="option.avatar || '/avatars/avatar1.png'"
        size="small"
        shape="circle"
    />
    <div class="friend-meta">
      <span class="friend-name">{{ option.label }}</span>
      <span class="friend-email">{{ option.email }}</span>
    </div>
    <!-- Online is a filled dot, recently seen a hollow ring, so the state does not depend on color. -->
    <span
        class="friend-status-dot"
        :class="{ 'friend-status-dot--online': option.isOnline }"
        role="img"
        :aria-label="statusLabel"
        :title="statusLabel"
    ></span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps({
  option: {
    type: Object,
    required: true
  }
})

const statusLabel = computed(() => (
  props.option.isOnline ? t('friends.filters.onlineNow') : t('friends.filters.lastSeenRecently')
))
</script>

<style scoped>
.friend-option-row {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  width: 100%;
}

.friend-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.friend-name {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--gp-text-primary);
}

.friend-email {
  font-size: 0.75rem;
  color: var(--gp-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.friend-status-dot {
  width: 0.6rem;
  height: 0.6rem;
  border-radius: 50%;
  background: transparent;
  border: 2px solid var(--gp-warning);
  box-sizing: border-box;
  flex-shrink: 0;
}

.friend-status-dot--online {
  background: var(--gp-success);
  border-color: var(--gp-success);
}
</style>

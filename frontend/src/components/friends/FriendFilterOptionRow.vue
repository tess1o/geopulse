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
    <span
        class="friend-status-dot"
        :class="{ 'friend-status-dot--online': option.isOnline }"
        :title="option.isOnline ? 'Online now' : 'Last seen recently'"
    ></span>
  </div>
</template>

<script setup>
defineProps({
  option: {
    type: Object,
    required: true
  }
})
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
  width: 0.55rem;
  height: 0.55rem;
  border-radius: 50%;
  background: var(--gp-warning);
  flex-shrink: 0;
}

.friend-status-dot--online {
  background: var(--gp-success);
}
</style>

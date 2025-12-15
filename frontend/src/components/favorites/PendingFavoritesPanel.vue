<template>
  <BaseCard v-if="hasPendingFavorites" class="pending-panel" variant="highlighted">
    <template #header>
      <div class="panel-header">
        <h3 class="panel-title">Pending Favorites</h3>
        <div class="panel-actions">
          <Tag :value="`${pendingCount} pending`" severity="warning" />
          <Button
            label="Clear All"
            icon="pi pi-times"
            severity="secondary"
            size="small"
            outlined
            @click="$emit('clear-all')"
          />
          <Button
            label="Save All"
            icon="pi pi-check"
            severity="success"
            size="small"
            @click="$emit('save-all')"
          />
        </div>
      </div>
    </template>

    <div class="pending-list">
      <div
        v-for="item in pendingItems"
        :key="item.tempId"
        class="pending-item"
      >
        <div class="item-icon">
          <i :class="item.type === 'point' ? 'pi pi-map-marker' : 'pi pi-stop'" />
        </div>
        <div class="item-details">
          <div class="item-name">{{ item.name }}</div>
          <div class="item-location">
            <span v-if="item.type === 'point'">
              Point: {{ item.lat.toFixed(4) }}, {{ item.lon.toFixed(4) }}
            </span>
            <span v-else>
              Area: {{ item.southWestLat.toFixed(2) }}, {{ item.southWestLon.toFixed(2) }} to
              {{ item.northEastLat.toFixed(2) }}, {{ item.northEastLon.toFixed(2) }}
            </span>
          </div>
        </div>
        <Button
          icon="pi pi-trash"
          severity="danger"
          size="small"
          text
          rounded
          @click="$emit('remove', item.tempId)"
        />
      </div>
    </div>

    <template #footer>
      <div class="footer-info">
        <i class="pi pi-info-circle" />
        <span>Timeline regeneration will happen once for all favorites</span>
      </div>
    </template>
  </BaseCard>
</template>

<script setup>
import { computed } from 'vue'
import { useFavoritesStore } from '@/stores/favorites'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import Button from 'primevue/button'
import Tag from 'primevue/tag'

defineEmits(['clear-all', 'save-all', 'remove'])

const favoritesStore = useFavoritesStore()

const hasPendingFavorites = computed(() => favoritesStore.hasPendingFavorites)
const pendingCount = computed(() => favoritesStore.pendingCount)
const pendingItems = computed(() => favoritesStore.getAllPending)
</script>

<style scoped>
.pending-panel {
  margin-bottom: 1.5rem;
  border: 2px solid var(--yellow-500);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.panel-title {
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-color);
  margin: 0;
}

.panel-actions {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.pending-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.pending-item {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem;
  background: var(--surface-50);
  border: 1px dashed var(--yellow-500);
  border-radius: 6px;
  transition: background-color 0.2s;
}

.pending-item:hover {
  background: var(--surface-100);
}

.item-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem;
  height: 2.5rem;
  background: var(--yellow-100);
  border-radius: 50%;
  color: var(--yellow-700);
  font-size: 1.25rem;
}

.item-details {
  flex: 1;
  min-width: 0;
}

.item-name {
  font-weight: 600;
  color: var(--text-color);
  margin-bottom: 0.25rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-location {
  font-size: 0.875rem;
  color: var(--text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.footer-info {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--text-color-secondary);
  font-size: 0.875rem;
}

.footer-info i {
  color: var(--blue-500);
}

/* Dark mode */
.p-dark .pending-item {
  background: var(--surface-800);
}

.p-dark .pending-item:hover {
  background: var(--surface-700);
}

.p-dark .item-icon {
  background: var(--yellow-900);
  color: var(--yellow-300);
}

/* Responsive */
@media (max-width: 768px) {
  .panel-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 1rem;
  }

  .panel-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .item-location {
    font-size: 0.75rem;
  }
}
</style>

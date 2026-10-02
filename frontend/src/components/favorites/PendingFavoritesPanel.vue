<template>
  <BaseCard v-if="hasPendingFavorites" class="pending-panel" variant="highlighted">
    <template #header>
      <div class="panel-header">
        <h3 class="panel-title">{{ t('sharing.favorites.pendingPanel.title') }}</h3>
        <div class="panel-actions">
          <Tag :value="t('sharing.favorites.pendingPanel.pendingCount', { count: pendingCount })" severity="warning" />
          <Button
            :label="t('sharing.favorites.pendingPanel.clearAll')"
            icon="pi pi-times"
            severity="secondary"
            size="small"
            outlined
            @click="$emit('clear-all')"
          />
          <Button
            :label="t('sharing.favorites.pendingPanel.saveAll')"
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
              {{ t('sharing.favorites.pendingPanel.pointLocation', { lat: item.lat.toFixed(4), lon: item.lon.toFixed(4) }) }}
            </span>
            <span v-else>
              {{ t('sharing.favorites.pendingPanel.areaLocation', {
                southWestLat: item.southWestLat.toFixed(2),
                southWestLon: item.southWestLon.toFixed(2),
                northEastLat: item.northEastLat.toFixed(2),
                northEastLon: item.northEastLon.toFixed(2)
              }) }}
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
        <span>{{ t('sharing.favorites.pendingPanel.footerInfo') }}</span>
      </div>
    </template>
  </BaseCard>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useFavoritesStore } from '@/stores/favorites'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import Button from 'primevue/button'
import Tag from 'primevue/tag'

defineEmits(['clear-all', 'save-all', 'remove'])

const { t } = useI18n()
const favoritesStore = useFavoritesStore()

const hasPendingFavorites = computed(() => favoritesStore.hasPendingFavorites)
const pendingCount = computed(() => favoritesStore.pendingCount)
const pendingItems = computed(() => favoritesStore.getAllPending)
</script>

<style scoped>
.pending-panel {
  margin-bottom: 1.5rem;
  border: 2px solid var(--p-yellow-500);
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
  color: var(--gp-text-primary);
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
  background: var(--gp-surface-muted);
  border: 1px dashed var(--p-yellow-500);
  border-radius: 6px;
  transition: background-color 0.2s;
}

.pending-item:hover {
  background: var(--gp-surface-muted);
}

.item-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem;
  height: 2.5rem;
  background: var(--gp-warning-soft);
  border-radius: 50%;
  color: var(--gp-warning-text);
  font-size: 1.25rem;
}

.item-details {
  flex: 1;
  min-width: 0;
}

.item-name {
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: 0.25rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-location {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.footer-info {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--gp-text-secondary);
  font-size: 0.875rem;
}

.footer-info i {
  color: var(--p-blue-500);
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

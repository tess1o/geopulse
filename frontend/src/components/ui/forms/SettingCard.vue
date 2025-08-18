<template>
  <Card class="setting-card">
    <template #content>
      <div class="setting-layout">
        <div class="setting-info">
          <h3 class="setting-title">{{ title }}</h3>
          <p class="setting-description">{{ description }}</p>
          
          <!-- Details Section -->
          <div v-if="details || $slots.details" class="setting-details">
            <div v-if="typeof details === 'string'" class="detail-text">
              {{ details }}
            </div>
            <div v-else-if="typeof details === 'object'" class="detail-list">
              <div v-for="(value, key) in details" :key="key" class="detail-item">
                <strong>{{ key }}:</strong> {{ value }}
              </div>
            </div>
            <slot name="details" />
          </div>
          
        </div>
        
        <div class="setting-control">
          <slot name="control" />
        </div>
      </div>
    </template>
  </Card>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    required: true
  },
  description: {
    type: String,
    required: true
  },
  details: {
    type: [String, Object],
    default: null
  },
})
</script>

<style scoped>
.setting-card {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  box-shadow: var(--gp-shadow-light);
  transition: all 0.2s ease;
  width: 100%;
  box-sizing: border-box;
}

.setting-card:hover {
  box-shadow: var(--gp-shadow-medium);
  transform: translateY(-1px);
}

.setting-layout {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 2rem;
  align-items: start;
}

.setting-info {
  flex: 1;
  min-width: 0;
  width: 100%;
  box-sizing: border-box;
}

.setting-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.5rem 0;
}

.setting-description {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin: 0 0 1rem 0;
  line-height: 1.4;
}

.setting-details {
  margin-bottom: 1rem;
}

.detail-text {
  font-size: 0.85rem;
  color: var(--gp-text-secondary);
  font-style: italic;
  line-height: 1.3;
}

.detail-list {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.detail-item {
  font-size: 0.85rem;
  color: var(--gp-text-secondary);
  line-height: 1.3;
}

.detail-item strong {
  color: var(--gp-text-primary);
}


.setting-control {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 200px;
  flex-shrink: 0;
  max-width: 100%;
  box-sizing: border-box;
}

/* Responsive Design */
@media (max-width: 768px) {
  .setting-card {
    margin: 0;
    width: 100%;
  }
  
  .setting-layout {
    grid-template-columns: 1fr;
    gap: 1.25rem;
    padding: 0;
  }
  
  .setting-control {
    min-width: 0;
    width: 100%;
    align-items: stretch;
    max-width: 100%;
  }
  
  .setting-title {
    font-size: 1rem;
  }
  
  .setting-description {
    font-size: 0.85rem;
    word-wrap: break-word;
    overflow-wrap: break-word;
  }
}

@media (max-width: 480px) {
  .setting-card {
    padding: 0;
  }
  
  .setting-layout {
    gap: 1rem;
    padding: 0;
  }
  
  .setting-info {
    padding: 0;
    width: 100%;
    overflow: hidden;
  }
  
  .setting-title {
    font-size: 0.95rem;
    margin-bottom: 0.4rem;
  }
  
  .setting-description {
    font-size: 0.8rem;
    margin-bottom: 0.75rem;
    word-wrap: break-word;
    overflow-wrap: break-word;
    line-height: 1.3;
  }
  
  .setting-control {
    padding: 0;
    width: 100%;
    overflow: hidden;
  }
  
  .detail-item {
    font-size: 0.8rem;
  }
  
}
</style>
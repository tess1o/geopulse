<template>
  <div class="gp-nav-section">
    <h3 v-if="title" class="gp-nav-section-title">{{ title }}</h3>
    <ul class="gp-nav-section-list">
      <li v-for="item in items" :key="item.key" class="gp-nav-section-item">
        <router-link
          v-if="item.to"
          :to="item.to"
          class="gp-nav-item-link"
          :class="{ 'gp-nav-item-link--disabled': item.disabled }"
          @click="!item.disabled && handleItemClick(item)"
        >
          <div class="gp-nav-item-content">
            <div class="gp-nav-item-icon-wrapper">
              <OverlayBadge
                v-if="item.badge && item.badge > 0"
                :value="item.badge"
                :severity="item.badgeType || 'danger'"
              >
                <i :class="item.icon" class="gp-nav-item-icon" />
              </OverlayBadge>
              <i v-else :class="item.icon" class="gp-nav-item-icon" />
            </div>
            <span class="gp-nav-item-label">{{ item.label }}</span>
          </div>
          <i v-if="item.disabled" class="pi pi-lock gp-nav-item-disabled-icon" />
        </router-link>
        
        <button
          v-else
          type="button"
          class="gp-nav-item-link gp-nav-item-button"
          :class="{ 'gp-nav-item-link--disabled': item.disabled }"
          :disabled="item.disabled"
          @click="!item.disabled && handleItemClick(item)"
        >
          <div class="gp-nav-item-content">
            <div class="gp-nav-item-icon-wrapper">
              <OverlayBadge
                v-if="item.badge && item.badge > 0"
                :value="item.badge"
                :severity="item.badgeType || 'danger'"
              >
                <i :class="item.icon" class="gp-nav-item-icon" />
              </OverlayBadge>
              <i v-else :class="item.icon" class="gp-nav-item-icon" />
            </div>
            <span class="gp-nav-item-label">{{ item.label }}</span>
          </div>
          <i v-if="item.disabled" class="pi pi-lock gp-nav-item-disabled-icon" />
        </button>
      </li>
    </ul>
  </div>
</template>

<script setup>
import OverlayBadge from 'primevue/overlaybadge'

defineProps({
  title: {
    type: String,
    default: ''
  },
  items: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['item-click'])

const handleItemClick = (item) => {
  emit('item-click', item)
}
</script>

<style scoped>
/* Navigation Section */
.gp-nav-section {
  margin-bottom: var(--gp-spacing-lg);
}

.gp-nav-section:last-child {
  margin-bottom: 0;
}

.gp-nav-section-title {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  color: var(--gp-text-muted);
  letter-spacing: 0.05em;
  margin: 0 0 var(--gp-spacing-sm);
  padding: 0 var(--gp-spacing-lg);
}

.gp-nav-section-list {
  list-style: none;
  margin: 0;
  padding: 0 var(--gp-spacing-md);
}

.gp-nav-section-item {
  margin-bottom: var(--gp-spacing-xs);
}

.gp-nav-section-item:last-child {
  margin-bottom: 0;
}

/* Navigation Item Link */
.gp-nav-item-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: var(--gp-spacing-md);
  border-radius: var(--gp-radius-medium);
  text-decoration: none;
  color: var(--gp-text-secondary);
  font-weight: 500;
  font-size: 0.875rem;
  transition: all 0.2s cubic-bezier(0.25, 0.8, 0.25, 1);
  cursor: pointer;
  border: 1px solid transparent;
  background: transparent;
}

.gp-nav-item-button {
  font-family: inherit;
}

.gp-nav-item-content {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}

.gp-nav-item-icon-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: var(--gp-spacing-md);
  flex-shrink: 0;
}

.gp-nav-item-icon {
  font-size: 1rem;
  color: var(--gp-primary);
  transition: color 0.2s ease;
}

.gp-nav-item-label {
  color: var(--gp-text-secondary);
  transition: color 0.2s ease;
  font-weight: 500;
}

.gp-nav-item-disabled-icon {
  font-size: 0.75rem;
  color: var(--gp-text-muted);
  margin-left: var(--gp-spacing-sm);
}

/* Hover States */
.gp-nav-item-link:hover:not(.gp-nav-item-link--disabled) {
  background: var(--gp-timeline-blue);
  border-color: var(--gp-primary-light);
  transform: translateX(2px);
}

.gp-nav-item-link:hover:not(.gp-nav-item-link--disabled) .gp-nav-item-label {
  color: var(--gp-primary-dark);
}

.gp-nav-item-link:hover:not(.gp-nav-item-link--disabled) .gp-nav-item-icon {
  color: var(--gp-primary-dark);
}

/* Active States */
.gp-nav-item-link.router-link-active {
  background: var(--gp-primary);
  color: white;
  border-color: var(--gp-primary);
  box-shadow: var(--gp-shadow-button);
}

.gp-nav-item-link.router-link-active .gp-nav-item-label {
  color: white;
  font-weight: 600;
}

.gp-nav-item-link.router-link-active .gp-nav-item-icon {
  color: white;
}

.gp-nav-item-link.router-link-active:hover {
  background: var(--gp-primary-dark);
  border-color: var(--gp-primary-dark);
  transform: translateX(0);
}

/* Disabled States */
.gp-nav-item-link--disabled {
  opacity: 0.6;
  cursor: not-allowed;
  pointer-events: none;
}

.gp-nav-item-link--disabled .gp-nav-item-icon {
  color: var(--gp-text-muted);
}

.gp-nav-item-link--disabled .gp-nav-item-label {
  color: var(--gp-text-muted);
}

/* Focus States */
.gp-nav-item-link:focus {
  outline: 2px solid var(--gp-primary);
  outline-offset: 2px;
}

/* Dark Mode */
.p-dark .gp-nav-section-title {
  color: var(--gp-text-muted);
}

.p-dark .gp-nav-item-label {
  color: var(--gp-text-secondary);
}

.p-dark .gp-nav-item-link:hover:not(.gp-nav-item-link--disabled) {
  background: var(--gp-primary-dark);
}

.p-dark .gp-nav-item-link:hover:not(.gp-nav-item-link--disabled) .gp-nav-item-label {
  color: var(--gp-primary-light);
}

.p-dark .gp-nav-item-link:hover:not(.gp-nav-item-link--disabled) .gp-nav-item-icon {
  color: var(--gp-primary-light);
}

.p-dark .gp-nav-item-link.router-link-active {
  background: var(--gp-primary);
  border-color: var(--gp-primary);
}

/* Badge Styling */
.gp-nav-item-icon-wrapper :deep(.p-overlaybadge .p-badge) {
  min-width: 1rem;
  height: 1rem;
  line-height: 1rem;
  font-size: 0.6rem;
  padding: 0;
  border-radius: 50%;
}

/* Animation for new badges */
.gp-nav-item-icon-wrapper :deep(.p-overlaybadge .p-badge) {
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.1);
  }
  100% {
    transform: scale(1);
  }
}

/* Responsive */
@media (max-width: 768px) {
  .gp-nav-section-list {
    padding: 0 var(--gp-spacing-sm);
  }

  .gp-nav-section-title {
    padding: 0 var(--gp-spacing-md);
  }

  .gp-nav-item-link {
    padding: var(--gp-spacing-sm) var(--gp-spacing-md);
  }

  .gp-nav-item-icon {
    font-size: 0.875rem;
  }

  .gp-nav-item-label {
    font-size: 0.8rem;
  }
}
</style>
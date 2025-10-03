<template>
  <div class="digest-achievements">
    <h3 class="achievements-title">
      <i class="pi pi-trophy"></i>
      Achievements
    </h3>

    <div class="achievements-list" v-if="achievements && achievements.length > 0">
      <div
        v-for="(achievement, index) in achievements"
        :key="index"
        class="achievement-item"
      >
        <i class="pi pi-check-circle achievement-icon"></i>
        <span class="achievement-text">{{ achievement }}</span>
      </div>
    </div>

    <div class="no-achievements" v-else>
      <i class="pi pi-star"></i>
      <p>Keep exploring to unlock achievements!</p>
    </div>
  </div>
</template>

<script setup>
defineProps({
  achievements: {
    type: Array,
    default: () => []
  }
})
</script>

<style scoped>
.digest-achievements {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-large);
  padding: var(--gp-spacing-xl);
  margin-bottom: var(--gp-spacing-xl);
}

.achievements-title {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--gp-text-primary);
  margin: 0 0 var(--gp-spacing-lg);
}

.achievements-title i {
  color: var(--gp-warning);
}

.achievements-list {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.achievement-item {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-md);
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.1) 0%, rgba(59, 130, 246, 0.1) 100%);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  padding: var(--gp-spacing-md);
  transition: all 0.3s ease;
}

.achievement-item:hover {
  transform: translateX(4px);
  box-shadow: var(--gp-shadow-card);
  border-color: var(--gp-success);
}

.achievement-icon {
  font-size: 1.5rem;
  color: var(--gp-success);
  flex-shrink: 0;
}

.achievement-text {
  font-size: 1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  flex: 1;
}

.no-achievements {
  text-align: center;
  padding: var(--gp-spacing-xl);
  color: var(--gp-text-muted);
}

.no-achievements i {
  font-size: 3rem;
  opacity: 0.5;
  margin-bottom: var(--gp-spacing-md);
}

.no-achievements p {
  margin: 0;
  font-style: italic;
}

/* Dark Mode */
.p-dark .digest-achievements {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark .achievement-item {
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.15) 0%, rgba(59, 130, 246, 0.15) 100%);
  border-color: var(--gp-border-dark);
}

.p-dark .achievements-title {
  color: var(--gp-text-primary);
}

/* Responsive */
@media (max-width: 768px) {
  .achievement-item {
    padding: var(--gp-spacing-sm);
  }

  .achievement-icon {
    font-size: 1.25rem;
  }

  .achievement-text {
    font-size: 0.875rem;
  }
}
</style>

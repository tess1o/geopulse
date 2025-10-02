<template>
  <div class="digest-milestones">
    <h3 class="milestones-title">
      <i class="pi pi-trophy"></i>
      Milestones
    </h3>

    <div class="milestones-grid" v-if="milestones && milestones.length > 0">
      <div
        v-for="milestone in milestones"
        :key="milestone.id"
        class="milestone-card"
        :class="`tier-${milestone.tier}`"
      >
        <div class="milestone-header">
          <div class="milestone-icon">{{ milestone.icon }}</div>
          <div class="tier-badge" :class="`badge-${milestone.tier}`">
            {{ getTierLabel(milestone.tier) }}
          </div>
        </div>
        <div class="milestone-content">
          <div class="milestone-title">{{ milestone.title }}</div>
          <div class="milestone-description">{{ milestone.description }}</div>
        </div>
      </div>
    </div>

    <div class="no-milestones-placeholder" v-else>
      <i class="pi pi-trophy"></i>
      <p>Keep exploring to unlock milestones!</p>
    </div>
  </div>
</template>

<script setup>
defineProps({
  milestones: {
    type: Array,
    default: () => []
  }
});

const getTierLabel = (tier) => {
  const labels = {
    bronze: 'Bronze',
    silver: 'Silver',
    gold: 'Gold',
    diamond: 'Diamond'
  }
  return labels[tier] || tier
}
</script>

<style scoped>
.digest-milestones {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-large);
  padding: var(--gp-spacing-xl);
  margin-bottom: var(--gp-spacing-xl);
  min-height: 00px;
}

.milestones-title {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--gp-text-primary);
  margin: 0 0 var(--gp-spacing-lg);
}

.milestones-title i {
  color: var(--gp-warning);
}

.milestones-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: var(--gp-spacing-md);
}

.milestone-card {
  background: var(--gp-surface-white);
  border: 2px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  padding: var(--gp-spacing-lg);
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.milestone-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  transition: all 0.3s ease;
}

.milestone-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--gp-shadow-card-hover);
}

/* Tier-specific styling */
.tier-bronze::before {
  background: linear-gradient(180deg, #cd7f32 0%, #8b5a2b 100%);
}

.tier-silver::before {
  background: linear-gradient(180deg, #c0c0c0 0%, #808080 100%);
}

.tier-gold::before {
  background: linear-gradient(180deg, #ffd700 0%, #ffA500 100%);
}

.tier-diamond::before {
  background: linear-gradient(180deg, #b9f2ff 0%, #00bfff 100%);
}

.tier-bronze:hover {
  border-color: #cd7f32;
}

.tier-silver:hover {
  border-color: #c0c0c0;
}

.tier-gold:hover {
  border-color: #ffd700;
}

.tier-diamond:hover {
  border-color: #b9f2ff;
}

.milestone-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--gp-spacing-md);
}

.milestone-icon {
  font-size: 2.5rem;
  line-height: 1;
}

.tier-badge {
  font-size: 0.625rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: 4px 8px;
  border-radius: var(--gp-radius-small);
  color: white;
}

.badge-bronze {
  background: linear-gradient(135deg, #cd7f32 0%, #8b5a2b 100%);
}

.badge-silver {
  background: linear-gradient(135deg, #c0c0c0 0%, #808080 100%);
}

.badge-gold {
  background: linear-gradient(135deg, #ffd700 0%, #ffA500 100%);
}

.badge-diamond {
  background: linear-gradient(135deg, #b9f2ff 0%, #00bfff 100%);
}

.milestone-content {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-xs);
}

.milestone-title {
  font-size: 1.125rem;
  font-weight: 700;
  color: var(--gp-text-primary);
  line-height: 1.3;
}

.milestone-description {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  font-weight: 500;
  line-height: 1.4;
}

.no-milestones-placeholder {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: var(--gp-spacing-xl);
  color: var(--gp-text-muted);
  font-style: italic;
}

.no-milestones-placeholder i {
  font-size: 2rem;
  opacity: 0.5;
  margin-bottom: var(--gp-spacing-md);
}

/* Dark Mode */
.p-dark .digest-milestones {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark .milestone-card {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
}

.p-dark .milestones-title {
  color: var(--gp-text-primary);
}

/* Responsive */
@media (max-width: 768px) {
  .milestones-grid {
    grid-template-columns: 1fr;
    gap: var(--gp-spacing-sm);
  }

  .milestone-card {
    padding: var(--gp-spacing-md);
  }

  .milestone-icon {
    font-size: 2rem;
  }

  .milestone-title {
    font-size: 1rem;
  }

  .milestone-description {
    font-size: 0.75rem;
  }
}
</style>

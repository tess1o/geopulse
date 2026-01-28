<template>
  <div class="preferences-section">
    <div class="section-header">
      <h2 class="section-title">{{ title }}</h2>
      <p class="section-description">{{ description }}</p>
    </div>

    <div class="settings-grid">
      <slot />
    </div>
  </div>
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
  }
})
</script>

<style scoped>
.preferences-section {
  padding: 2rem 0;
}

.section-header {
  margin-bottom: 2rem;
  text-align: center;
}

.section-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.5rem 0;
}

.section-description {
  font-size: 1rem;
  color: var(--gp-text-secondary);
  line-height: 1.5;
  max-width: 600px;
  margin: 0 auto;
  word-wrap: break-word;
  overflow-wrap: break-word;
  hyphens: auto;
}

.settings-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1.5rem;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
}

@media (max-width: 768px) {
  .section-header {
    text-align: left;
    margin-bottom: 1.5rem;
  }

  .section-title {
    font-size: 1.3rem;
  }

  .section-description {
    font-size: 0.9rem;
    max-width: 100%;
    padding: 0 0.5rem;
    word-wrap: break-word;
    overflow-wrap: break-word;
  }

  .preferences-section {
    padding: 1.5rem 0;
    width: 100%;
    overflow: hidden;
  }

  .settings-grid {
    gap: 1.25rem;
    padding: 0;
    margin: 0;
  }
}

@media (max-width: 480px) {
  .section-title {
    font-size: 1.2rem;
  }

  .section-description {
    font-size: 0.85rem;
    max-width: 100%;
    padding: 0;
    margin: 0;
    word-wrap: break-word;
    overflow-wrap: break-word;
    line-height: 1.4;
  }
}
</style>

<template>
  <div class="tip-card">
    <div class="tip-top">
      <p class="tip-label">{{ title }}</p>
      <button
        v-if="showNext"
        type="button"
        class="tip-next"
        @click="onNext?.()"
      >
        <i class="pi pi-refresh"></i>
        <span>Next tip</span>
      </button>
    </div>

    <div class="tip-head">
      <i :class="tipIcon" aria-hidden="true"></i>
      <h3>{{ tipTitle }}</h3>
    </div>

    <p class="tip-description">{{ tipDescription }}</p>

    <ul v-if="tipLinks.length" class="tip-links">
      <li v-for="link in tipLinks" :key="`${link.label}-${link.url}`">
        <a
          :href="link.url"
          :target="isExternal(link.url) ? '_blank' : undefined"
          :rel="isExternal(link.url) ? 'noopener noreferrer' : undefined"
        >
          <i class="pi pi-arrow-right"></i>
          <span>{{ link.label }}</span>
        </a>
      </li>
    </ul>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  tip: {
    type: Object,
    default: null,
  },
  title: {
    type: String,
    default: 'Tip of the day',
  },
  onNext: {
    type: Function,
    default: null,
  },
  showNext: {
    type: Boolean,
    default: false,
  },
})

const tipTitle = computed(() => props.tip?.title || 'No tip available yet')
const tipDescription = computed(() => props.tip?.description || 'Tips will appear here when home content is available.')
const tipIcon = computed(() => props.tip?.icon || 'pi pi-lightbulb')
const tipLinks = computed(() => {
  if (!Array.isArray(props.tip?.links)) {
    return []
  }

  return props.tip.links.filter(link => link?.label && link?.url)
})

const isExternal = (url) => /^https?:\/\//i.test(url || '')
</script>

<style scoped>
.tip-card {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

.tip-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.tip-label {
  margin: 0;
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: var(--home-text-secondary);
}

.tip-head {
  display: flex;
  align-items: center;
  gap: 0.55rem;
}

.tip-head i {
  color: #0f766e;
  font-size: 0.98rem;
}

.tip-head h3 {
  margin: 0;
  font-size: 0.99rem;
  line-height: 1.35;
  font-weight: 700;
  color: var(--home-text-primary);
}

.tip-description {
  margin: 0;
  font-size: 0.9rem;
  line-height: 1.55;
  color: var(--home-text-secondary);
}

.tip-links {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.tip-links a {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  color: #2563eb;
  text-decoration: none;
  font-size: 0.84rem;
  font-weight: 600;
}

.tip-links a:hover {
  color: #1d4ed8;
}

.tip-links i {
  font-size: 0.72rem;
  opacity: 0.75;
}

.tip-next {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  border: 1px solid rgba(148, 163, 184, 0.5);
  background: rgba(248, 250, 252, 0.82);
  color: #334155;
  border-radius: 999px;
  padding: 0.35rem 0.72rem;
  font-size: 0.79rem;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
}

.tip-next:hover {
  border-color: rgba(96, 165, 250, 0.6);
  background: rgba(255, 255, 255, 0.95);
  color: #1e40af;
}

.tip-next i {
  font-size: 0.75rem;
}

.p-dark .tip-head i {
  color: #34d399;
}

.p-dark .tip-description {
  color: #94a3b8;
}

.p-dark .tip-links a {
  color: #60a5fa;
}

.p-dark .tip-links a:hover {
  color: #93c5fd;
}

.p-dark .tip-next {
  background: rgba(30, 41, 59, 0.62);
  border-color: rgba(148, 163, 184, 0.3);
  color: #cbd5e1;
}

.p-dark .tip-next:hover {
  background: rgba(30, 41, 59, 0.9);
  border-color: rgba(96, 165, 250, 0.5);
  color: #e2e8f0;
}
</style>

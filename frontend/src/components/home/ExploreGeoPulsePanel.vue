<template>
  <section
    class="feature-panel"
    :class="[activeFeature ? activeFeature.colorClass : '', { 'feature-panel--mobile': mobile, 'feature-panel--embedded': embedded }]"
    aria-label="Explore GeoPulse"
    @mouseenter="stopFeatureAutoPlay"
    @mouseleave="startFeatureAutoPlay"
  >
    <div class="feature-panel-header">
      <p v-if="!mobile" class="feature-panel-kicker">Explore GeoPulse</p>
      <div class="feature-tabs" role="tablist" aria-label="GeoPulse features">
        <button
          v-for="feature in features"
          :key="feature.id"
          type="button"
          class="feature-tab"
          :class="{ active: activeFeatureId === feature.id }"
          :aria-selected="activeFeatureId === feature.id"
          @click="setActiveFeature(feature.id)"
        >
          <i :class="feature.icon"></i>
          <span>{{ feature.tabLabel }}</span>
        </button>
      </div>
    </div>

    <transition name="fade" mode="out-in">
      <div v-if="activeFeature" :key="activeFeature.id" class="feature-panel-body">
        <div class="feature-panel-copy">
          <h3>{{ activeFeature.title }}</h3>
          <p>{{ activeFeature.description }}</p>
          <ul class="feature-highlight-list">
            <li v-for="point in activeFeature.highlights" :key="point">
              <i class="pi pi-check-circle"></i>
              <span>{{ point }}</span>
            </li>
          </ul>

          <div class="feature-panel-actions">
            <a
              v-if="activeFeature.learnMoreType === 'docs'"
              :href="activeFeature.learnMoreUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="feature-learn-more"
            >
              <span>Read docs</span>
              <i class="pi pi-external-link"></i>
            </a>
            <button
              v-else-if="activeFeature?.learnMoreType"
              type="button"
              class="feature-learn-more feature-learn-more-app"
              @click="openInApp"
            >
              <span>Open in app</span>
              <i class="pi pi-arrow-right"></i>
            </button>
          </div>
        </div>
      </div>
    </transition>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { EXPLORE_FEATURES } from '@/content/exploreFeatures'

const props = defineProps({
  mobile: {
    type: Boolean,
    default: false,
  },
  autoPlay: {
    type: Boolean,
    default: true,
  },
  embedded: {
    type: Boolean,
    default: false,
  },
  autoPlayIntervalMs: {
    type: Number,
    default: 10000,
  },
})

const router = useRouter()
const features = EXPLORE_FEATURES
const activeFeatureId = ref(features[0]?.id || null)

let featureInterval = null

const activeFeature = computed(() => features.find(feature => feature.id === activeFeatureId.value) || null)

const getNextFeatureId = () => {
  if (features.length === 0) {
    return null
  }

  const currentIndex = features.findIndex(feature => feature.id === activeFeatureId.value)
  const normalizedIndex = currentIndex >= 0 ? currentIndex : 0
  const nextIndex = (normalizedIndex + 1) % features.length

  return features[nextIndex].id
}

const startFeatureAutoPlay = () => {
  stopFeatureAutoPlay()

  if (!props.autoPlay || features.length <= 1) {
    return
  }

  featureInterval = setInterval(() => {
    const nextFeatureId = getNextFeatureId()
    if (nextFeatureId) {
      activeFeatureId.value = nextFeatureId
    }
  }, props.autoPlayIntervalMs)
}

const stopFeatureAutoPlay = () => {
  if (featureInterval) {
    clearInterval(featureInterval)
    featureInterval = null
  }
}

const setActiveFeature = (id) => {
  activeFeatureId.value = id
  startFeatureAutoPlay()
}

const openInApp = () => {
  if (!activeFeature.value || activeFeature.value.learnMoreType !== 'app') {
    return
  }

  const destination = activeFeature.value.learnMoreUrl
  if (typeof destination !== 'string' || !destination.trim()) {
    return
  }

  router.push(destination)
}

onMounted(() => {
  startFeatureAutoPlay()
})

onBeforeUnmount(() => {
  stopFeatureAutoPlay()
})
</script>

<style scoped>
.feature-panel {
  --feature-accent: #2563eb;
  --feature-accent-soft: rgba(37, 99, 235, 0.11);
  --feature-accent-shadow: rgba(37, 99, 235, 0.26);
  width: 100%;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(203, 213, 225, 0.6);
  border-radius: 1rem;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.06);
  overflow: hidden;
}

.feature-panel.chip-1-color {
  --feature-accent: #0ea5e9;
  --feature-accent-soft: rgba(14, 165, 233, 0.13);
  --feature-accent-shadow: rgba(14, 165, 233, 0.3);
}

.feature-panel.chip-2-color {
  --feature-accent: #2563eb;
  --feature-accent-soft: rgba(37, 99, 235, 0.12);
  --feature-accent-shadow: rgba(37, 99, 235, 0.28);
}

.feature-panel.chip-3-color {
  --feature-accent: #0f766e;
  --feature-accent-soft: rgba(15, 118, 110, 0.12);
  --feature-accent-shadow: rgba(15, 118, 110, 0.28);
}

.feature-panel.chip-4-color {
  --feature-accent: #9333ea;
  --feature-accent-soft: rgba(147, 51, 234, 0.13);
  --feature-accent-shadow: rgba(147, 51, 234, 0.28);
}

.feature-panel.chip-5-color {
  --feature-accent: #ea580c;
  --feature-accent-soft: rgba(234, 88, 12, 0.13);
  --feature-accent-shadow: rgba(234, 88, 12, 0.28);
}

.feature-panel.chip-6-color {
  --feature-accent: #e11d48;
  --feature-accent-soft: rgba(225, 29, 72, 0.13);
  --feature-accent-shadow: rgba(225, 29, 72, 0.28);
}

.feature-panel.chip-7-color {
  --feature-accent: #4f46e5;
  --feature-accent-soft: rgba(79, 70, 229, 0.13);
  --feature-accent-shadow: rgba(79, 70, 229, 0.28);
}

.feature-panel.chip-8-color {
  --feature-accent: #0f766e;
  --feature-accent-soft: rgba(15, 118, 110, 0.13);
  --feature-accent-shadow: rgba(15, 118, 110, 0.3);
}

.feature-panel-header {
  padding: 0.92rem 1.1rem 0.72rem;
  border-bottom: 1px solid rgba(203, 213, 225, 0.55);
  background: rgba(248, 250, 252, 0.56);
}

.feature-panel-kicker {
  margin: 0 0 0.75rem;
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  color: var(--home-text-secondary);
}

.feature-tabs {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.feature-tab {
  border: 1px solid rgba(203, 213, 225, 0.9);
  background: rgba(255, 255, 255, 0.9);
  color: #334155;
  border-radius: 0.75rem;
  height: 2.5rem;
  padding: 0 0.8rem;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.84rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.25s ease;
}

.feature-tab i {
  font-size: 0.92rem;
  color: #64748b;
}

.feature-tab:hover {
  border-color: rgba(96, 165, 250, 0.7);
  color: #1e293b;
}

.feature-tab.active {
  background: var(--feature-accent);
  border-color: var(--feature-accent);
  color: #ffffff;
  box-shadow: 0 8px 18px var(--feature-accent-shadow);
}

.feature-tab.active i {
  color: #dbeafe;
}

.feature-panel-body {
  display: block;
  padding: 1.15rem 1.25rem 1.2rem;
  min-height: 214px;
}

.feature-panel-copy {
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.feature-panel-copy h3 {
  margin: 0 0 0.5rem;
  font-size: 1.3rem;
  font-weight: 700;
  color: var(--home-text-primary);
}

.feature-panel-copy p {
  margin: 0;
  font-size: 0.98rem;
  line-height: 1.62;
  color: var(--home-text-secondary);
  max-width: none;
  min-height: 0;
}

.feature-highlight-list {
  margin: 0.95rem 0 0;
  padding: 0;
  list-style: none;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.6rem 0.8rem;
}

.feature-highlight-list li {
  display: flex;
  align-items: flex-start;
  gap: 0.45rem;
  color: var(--home-text-secondary);
  font-size: 0.88rem;
  line-height: 1.45;
  border: 1px solid rgba(203, 213, 225, 0.62);
  border-left: 3px solid var(--feature-accent);
  background: linear-gradient(90deg, var(--feature-accent-soft), rgba(255, 255, 255, 0.2));
  border-radius: 0.65rem;
  padding: 0.48rem 0.58rem;
}

.feature-highlight-list i {
  font-size: 0.9rem;
  color: var(--feature-accent);
  margin-top: 0.08rem;
}

.feature-panel-actions {
  margin-top: 0.85rem;
}

.feature-learn-more {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.52rem 0.85rem;
  border-radius: 0.75rem;
  border: 1px solid rgba(203, 213, 225, 0.8);
  background: rgba(248, 250, 252, 0.7);
  color: var(--home-text-secondary);
  text-decoration: none;
  font-size: 0.83rem;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
}

.feature-learn-more:hover {
  background: rgba(255, 255, 255, 0.95);
  border-color: rgba(96, 165, 250, 0.6);
  color: #1e293b;
  transform: translateX(2px);
}

.feature-learn-more-app {
  font-family: inherit;
}

.feature-panel--mobile .feature-panel-header {
  border-top: 1px solid rgba(203, 213, 225, 0.52);
}

@media (max-width: 1023px) {
  .feature-tab {
    height: 2.35rem;
    padding: 0 0.7rem;
    font-size: 0.8rem;
  }
}

@media (max-width: 767px) {
  .feature-panel-header {
    padding: 0.95rem 0.95rem 0.75rem;
  }

  .feature-tabs {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0.45rem;
  }

  .feature-tab {
    justify-content: center;
  }

  .feature-panel-body {
    padding: 0.95rem;
    min-height: auto;
  }

  .feature-panel-copy h3 {
    font-size: 1.08rem;
    line-height: 1.35;
  }

  .feature-panel-copy p {
    font-size: 0.95rem;
    white-space: normal;
  }

  .feature-highlight-list {
    grid-template-columns: 1fr;
    gap: 0.5rem;
    margin-top: 0.8rem;
  }
}

@media (min-width: 1024px) {
  .feature-panel-copy p {
    white-space: nowrap;
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

.p-dark .feature-panel {
  background: linear-gradient(145deg, rgba(15, 23, 42, 0.78), rgba(15, 23, 42, 0.68));
  border-color: rgba(148, 163, 184, 0.26);
  box-shadow: 0 10px 22px rgba(0, 0, 0, 0.24);
}

.p-dark .feature-panel-header {
  border-bottom-color: rgba(148, 163, 184, 0.2);
  background: rgba(30, 41, 59, 0.5);
}

.p-dark .feature-tab {
  background: rgba(15, 23, 42, 0.85);
  border-color: rgba(148, 163, 184, 0.4);
  color: #cbd5e1;
}

.p-dark .feature-tab i {
  color: #94a3b8;
}

.p-dark .feature-tab:hover {
  border-color: rgba(96, 165, 250, 0.6);
  color: #e2e8f0;
}

.p-dark .feature-tab.active {
  background: var(--feature-accent);
  border-color: var(--feature-accent);
  color: #ffffff;
  box-shadow: 0 8px 18px var(--feature-accent-shadow);
}

.p-dark .feature-tab.active i {
  color: #dbeafe;
}

.p-dark .feature-highlight-list li {
  color: #94a3b8;
  border-color: rgba(148, 163, 184, 0.32);
  background: linear-gradient(90deg, var(--feature-accent-soft), rgba(15, 23, 42, 0.14));
}

.p-dark .feature-highlight-list i {
  color: var(--feature-accent);
}

.p-dark .feature-learn-more {
  background: rgba(30, 41, 59, 0.6);
  border-color: rgba(148, 163, 184, 0.3);
  color: #94a3b8;
}

.p-dark .feature-learn-more:hover {
  background: rgba(30, 41, 59, 0.9);
  border-color: rgba(96, 165, 250, 0.5);
  color: #e2e8f0;
}

.feature-panel--embedded,
.p-dark .feature-panel--embedded {
  background: transparent;
  border: 0;
  border-radius: 0;
  box-shadow: none;
}
</style>

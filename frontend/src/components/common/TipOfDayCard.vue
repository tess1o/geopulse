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
        <span>{{ t('ui.tipOfDayCard.nextTip') }}</span>
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
import { useI18n } from 'vue-i18n'
import { t as translate } from '@/locales'

const { t } = useI18n()

const props = defineProps({
  tip: {
    type: Object,
    default: null,
  },
  title: {
    type: String,
    default: () => translate('ui.home.panel.tipOfDayTitle'),
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

const tipTitle = computed(() => props.tip?.title || t('ui.tipOfDayCard.noTipTitle'))
const tipDescription = computed(() => props.tip?.description || t('ui.tipOfDayCard.noTipDescription'))
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
  color: var(--gp-landing-text-secondary);
}

.tip-head {
  display: flex;
  align-items: center;
  gap: 0.55rem;
}

.tip-head i {
  color: var(--gp-landing-accent);
  font-size: 0.98rem;
}

.tip-head h3 {
  margin: 0;
  font-size: 0.99rem;
  line-height: 1.35;
  font-weight: 700;
  color: var(--gp-landing-text-primary);
}

.tip-description {
  margin: 0;
  font-size: 0.9rem;
  line-height: 1.55;
  color: var(--gp-landing-text-secondary);
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
  color: var(--gp-primary-text);
  text-decoration: none;
  font-size: 0.84rem;
  font-weight: 600;
}

.tip-links a:hover {
  color: var(--gp-primary-hover);
}

.tip-links i {
  font-size: 0.72rem;
  opacity: 0.75;
}

.tip-next {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  border: 1px solid var(--gp-landing-glass-border);
  background: var(--gp-landing-glass);
  color: var(--gp-text-secondary);
  border-radius: 999px;
  padding: 0.35rem 0.72rem;
  font-size: 0.79rem;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s ease;
}

.tip-next:hover {
  border-color: rgba(96, 165, 250, 0.6);
  background: var(--gp-landing-glass-hover);
  color: var(--gp-primary-text);
}

.tip-next i {
  font-size: 0.75rem;
}
</style>

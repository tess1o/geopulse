<template>
  <div v-if="demoModeEnabled" class="demo-mode-banner" role="status">
    <div class="demo-mode-banner__inner">
      <i class="pi pi-exclamation-triangle demo-mode-banner__icon" aria-hidden="true"></i>
      <span class="demo-mode-banner__text">
        Demo Mode: Data resets every {{ demoResetIntervalHours }} hours. Settings and write actions are read-only or disabled.
        Do not store sensitive information.
      </span>
      <a
        class="demo-mode-banner__link"
        :href="deployUrl"
        target="_blank"
        rel="noopener noreferrer"
      >
        Deploy your own GeoPulse
        <i class="pi pi-external-link" aria-hidden="true"></i>
      </a>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const {
  demoModeEnabled,
  demoResetIntervalHours,
  demoDeployUrl
} = storeToRefs(authStore)

const deployUrl = computed(() => demoDeployUrl.value || 'https://geopulse.cc')

onMounted(() => {
  authStore.getAuthStatus()
})
</script>

<style scoped>
.demo-mode-banner {
  width: 100%;
  background: var(--gp-demo-alert-bg);
  border-bottom: 1px solid var(--gp-demo-alert-border);
  color: var(--gp-demo-alert-text);
  position: relative;
  z-index: 1000;
}

.demo-mode-banner__inner {
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
  padding: 0.625rem 1rem;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.625rem;
  line-height: 1.35;
  text-align: center;
  box-sizing: border-box;
}

.demo-mode-banner__icon {
  color: var(--gp-demo-alert-icon);
  flex: 0 0 auto;
}

.demo-mode-banner__text {
  min-width: 0;
  font-weight: 600;
}

.demo-mode-banner__link {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  color: var(--gp-demo-alert-text);
  font-weight: 700;
  text-decoration: none;
  white-space: nowrap;
}

.demo-mode-banner__link:hover {
  color: var(--gp-demo-alert-text);
  text-decoration: underline;
}

.p-dark .demo-mode-banner {
  box-shadow: inset 0 -1px 0 rgba(248, 113, 113, 0.45);
}

.p-dark .demo-mode-banner__link {
  color: #ffffff;
  text-decoration: underline;
  text-decoration-thickness: 1px;
  text-underline-offset: 3px;
}

.p-dark .demo-mode-banner__link:hover {
  color: var(--gp-demo-alert-icon);
}

@media (max-width: 640px) {
  .demo-mode-banner__inner {
    align-items: flex-start;
    justify-content: flex-start;
    text-align: left;
    flex-wrap: wrap;
    gap: 0.375rem 0.5rem;
    padding: 0.625rem 0.75rem;
    font-size: 0.875rem;
  }

  .demo-mode-banner__text {
    flex: 1 1 calc(100% - 1.75rem);
  }

  .demo-mode-banner__link {
    margin-left: 1.625rem;
    white-space: normal;
  }
}
</style>

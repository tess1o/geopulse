<template>
  <div id="app">
    <RestoreMaintenanceScreen v-if="showMaintenanceScreen" />
    <template v-else>
      <div v-if="maintenance.state === 'PREPARING'" class="restore-preparing-banner" role="status" aria-live="polite">
        <i class="pi pi-exclamation-triangle" aria-hidden="true" />
        <span>{{ maintenance.message }}</span>
      </div>
      <DemoModeBanner />
    </template>
    <div v-if="!showMaintenanceScreen" class="page-container">
      <div class="main-content">
        <router-view/>
      </div>
    </div>
  </div>
</template>

<script>
import DemoModeBanner from '@/components/demo/DemoModeBanner.vue'
import RestoreMaintenanceScreen from '@/components/admin/RestoreMaintenanceScreen.vue'
import { computed } from 'vue'
import { maintenance, maintenanceScreenVisible } from '@/stores/maintenance'

export default {
  name: 'App',
  setup() { return { maintenance, showMaintenanceScreen: computed(maintenanceScreenVisible) } },
  components: {
    DemoModeBanner,
    RestoreMaintenanceScreen
  }
}
</script>

<style scoped>
.restore-preparing-banner {
  position: sticky;
  top: 0;
  z-index: 5000;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: .65rem;
  padding: .75rem 1rem;
  color: #5f3900;
  background: #fff3cd;
  border-bottom: 1px solid #e3b341;
  font-weight: 600;
  line-height: 1.4;
}
</style>

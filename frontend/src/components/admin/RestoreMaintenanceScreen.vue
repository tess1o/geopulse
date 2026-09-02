<template>
  <main class="restore-maintenance" role="alert" aria-live="polite">
    <section class="restore-maintenance-card">
      <i :class="icon" aria-hidden="true" />
      <h1>{{ title }}</h1>
      <p>{{ message }}</p>
      <template v-if="maintenance.manualRestartRequired">
        <p class="restart-instructions"><strong>GeoPulse has not reconnected.</strong> Check the backend logs. If the backend stopped or did not restart, restart only the GeoPulse backend manually. Do not restart PostgreSQL.</p>
        <p>Use Docker Compose, Kubernetes, Unraid, Proxmox, or the service controls used by your installation.</p>
      </template>
      <p v-if="maintenance.unavailable" class="connection-note">Check the backend logs and make sure GeoPulse starts again. This page will update automatically when it reconnects.</p>
      <button v-if="maintenance.unavailable" type="button" @click="refreshMaintenance">Check connection</button>
      <p class="connection-note">PostgreSQL must remain running while the GeoPulse backend restarts.</p>
    </section>
  </main>
</template>

<script setup>
import { computed } from 'vue'
import { maintenance, refreshMaintenance } from '@/stores/maintenance'

const restarting = computed(() => maintenance.unavailable || maintenance.state === 'SWAPPED_PENDING_RESTART')
const title = computed(() => maintenance.activated
  ? 'Restoration completed. Signing out…'
  : maintenance.state === 'ACTIVATION_FAILED'
    ? 'Administrator action required'
    : restarting.value ? 'GeoPulse backend restart' : 'Activating restored data')
const message = computed(() => maintenance.activated
  ? 'Clearing the previous session before returning to sign in. This page will update automatically.'
  : restarting.value
    ? 'Restored data was activated and the backend stopped to complete restoration. A configured container or service manager may restart it automatically.'
    : maintenance.message || 'Activating restored data. Please wait.')
const icon = computed(() => maintenance.manualRestartRequired || maintenance.state === 'ACTIVATION_FAILED' ? 'pi pi-exclamation-triangle' : 'pi pi-spin pi-spinner')
</script>

<style scoped>
.restore-maintenance {
  --restore-accent: #b45309;
  min-height: 100dvh;
  display: grid;
  place-items: center;
  padding: 2rem;
  background: var(--gp-surface-light, #f8fafc);
  color: var(--gp-text-primary, #1e293b);
}
.restore-maintenance-card {
  width: min(100%, 44rem);
  border: 1px solid var(--gp-border-medium, #e2e8f0);
  border-radius: 1rem;
  padding: clamp(1.5rem, 5vw, 3rem);
  background: var(--gp-surface-white, #ffffff);
  box-shadow: var(--gp-shadow-dialog, 0 10px 25px rgba(0, 0, 0, .1));
}
.restore-maintenance-card > i { font-size: 2.5rem; color: var(--restore-accent); }
h1 { font-size: clamp(1.6rem, 4vw, 2.3rem); line-height: 1.2; margin: 1.25rem 0; }
p { line-height: 1.65; margin: 1rem 0; }
.restart-instructions { border-left: 4px solid var(--restore-accent); padding-left: 1rem; }
.connection-note { color: var(--gp-text-secondary, #64748b); font-size: .9rem; }
button {
  padding: .8rem 1rem;
  border: 1px solid var(--gp-border-medium, #cbd5e1);
  border-radius: .5rem;
  background: var(--gp-surface-light, #f8fafc);
  color: var(--gp-text-primary, #1e293b);
  cursor: pointer;
}
button:hover { background: var(--gp-surface-gray, #f1f5f9); }
button:focus-visible { outline: 3px solid color-mix(in srgb, var(--gp-primary, #1a56db) 35%, transparent); outline-offset: 2px; }
button:disabled { opacity: .5; cursor: wait; }
:global(.p-dark) .restore-maintenance { --restore-accent: #f59e0b; }
</style>

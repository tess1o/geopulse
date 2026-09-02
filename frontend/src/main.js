import "primeicons/primeicons.css";
import "@fortawesome/fontawesome-free/css/all.min.css";
import "leaflet/dist/leaflet.css";
import "./mapStyles.css"
import "./style.css";
import "./flags.css";

import {createApp, watch} from "vue";
import PrimeVue from "primevue/config";

import App from "./App.vue";
import { acknowledgeActivation, maintenance, markMaintenanceUnavailable, refreshMaintenance, startMaintenancePolling } from '@/stores/maintenance'
import apiService from '@/utils/apiService'
import { useAuthStore } from '@/stores/auth'
import GeopulsePreset from "@/presets/GeopulsePreset";
import { initializeThemeMode } from "@/utils/themeMode";
import router from "./router";
import ToastService from 'primevue/toastservice';
import ConfirmationService from 'primevue/confirmationservice';
import Tooltip from 'primevue/tooltip'
import { createPinia } from 'pinia'
import { useTimezone } from '@/composables/useTimezone'
import { clearAllFormatCaches } from '@/utils/formatMemoizer'

initializeThemeMode()

if (import.meta.env.DEV) {
    void cleanupDevelopmentServiceWorkers()
}

async function cleanupDevelopmentServiceWorkers() {
    try {
        if ('serviceWorker' in navigator) {
            const registrations = await navigator.serviceWorker.getRegistrations()
            await Promise.all(registrations.map((registration) => registration.unregister()))
        }
    } catch (error) {
        console.warn('Failed to unregister development service workers', error)
    }

    try {
        if ('caches' in window) {
            const cacheNames = await caches.keys()
            await Promise.all(cacheNames.map((cacheName) => caches.delete(cacheName)))
        }
    } catch (error) {
        console.warn('Failed to clear development caches', error)
    }
}

async function startApplication() {
await refreshMaintenance()
const app = createApp(App);
const timezone = useTimezone()

app.use(PrimeVue, {
    ripple: false,
    locale: {
        firstDayOfWeek: timezone.getPrimeVueFirstDayOfWeek()
    },
    theme: {
        preset: GeopulsePreset,
        options: {
            prefix: 'p',
            darkModeSelector: '.p-dark',
            cssLayer: false,
        }
    }
});

app.use(createPinia())
app.use(router);
app.use(ToastService);
app.use(ConfirmationService);
app.directive('tooltip', Tooltip)

watch(
    [timezone.userDateFormat, timezone.userTimeFormat, timezone.userTimezone],
    () => {
        const primevueConfig = app.config.globalProperties.$primevue?.config
        if (!primevueConfig) {
            return
        }

        if (!primevueConfig.locale) {
            primevueConfig.locale = {}
        }
        primevueConfig.locale.firstDayOfWeek = timezone.getPrimeVueFirstDayOfWeek()
        clearAllFormatCaches()
    },
    { immediate: true }
)

app.mount("#app");

startMaintenancePolling()
let signingOut = false
watch(() => [maintenance.blocked, maintenance.unavailable, maintenance.activated], async ([blocked, unavailable], previous) => {
    if (blocked || unavailable) return
    if (maintenance.activated) {
        if (signingOut) return
        signingOut = true
        try {
            // HttpOnly cookies require a server-confirmed logout, not just local cache removal.
            await apiService.logoutStrict()
            useAuthStore().clearUser()
            acknowledgeActivation()
            window.location.replace('/login')
        } catch {
            markMaintenanceUnavailable()
        } finally { signingOut = false }
    } else if (previous?.some(Boolean)) {
        void router.replace(window.location.pathname + window.location.search)
    }
}, { immediate: true })
}
void startApplication()

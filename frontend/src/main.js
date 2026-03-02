import "primeicons/primeicons.css";
import "leaflet/dist/leaflet.css";
import "./mapStyles.css"
import "./style.css";
import "./flags.css";

import {createApp, watch} from "vue";
import PrimeVue from "primevue/config";

import App from "./App.vue";
import GeopulsePreset from "@/presets/GeopulsePreset";
import { initializeThemeMode } from "@/utils/themeMode";
import router from "./router";
import ToastService from 'primevue/toastservice';
import ConfirmationService from 'primevue/confirmationservice';
import Tooltip from 'primevue/tooltip'
import { createPinia } from 'pinia'
import { useTimezone } from '@/composables/useTimezone'

initializeThemeMode()

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
    timezone.userDateFormat,
    () => {
        const primevueConfig = app.config.globalProperties.$primevue?.config
        if (!primevueConfig) {
            return
        }

        if (!primevueConfig.locale) {
            primevueConfig.locale = {}
        }
        primevueConfig.locale.firstDayOfWeek = timezone.getPrimeVueFirstDayOfWeek()
    },
    { immediate: true }
)

app.mount("#app");

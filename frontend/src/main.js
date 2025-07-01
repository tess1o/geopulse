import "primeicons/primeicons.css";
import "leaflet/dist/leaflet.css";
import "./mapStyles.css"
import "./style.css";
import "./flags.css";

import {createApp} from "vue";
import PrimeVue from "primevue/config";

import App from "./App.vue";
import GeopulsePreset from "@/presets/GeopulsePreset";
import router from "./router";
import ToastService from 'primevue/toastservice';
import ConfirmationService from 'primevue/confirmationservice';
import Tooltip from 'primevue/tooltip'
import { createPinia } from 'pinia'

// Initialize dark mode from localStorage before creating the app
const initializeDarkMode = () => {
  const savedDarkMode = localStorage.getItem('darkMode')
  if (savedDarkMode === 'true') {
    document.documentElement.classList.add('p-dark')
  } else if (savedDarkMode === 'false') {
    document.documentElement.classList.remove('p-dark')
  } else {
    // Check system preference if no saved preference
    if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
      document.documentElement.classList.add('p-dark')
      localStorage.setItem('darkMode', 'true')
    }
  }
}

initializeDarkMode()

const app = createApp(App);

app.use(PrimeVue, {
    ripple: false,
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

app.mount("#app");

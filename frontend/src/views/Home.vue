<template>
  <div class="home-page">
    <!-- Hero Section -->
    <section class="hero-section">
      <div class="hero-background">
        <div class="hero-gradient"></div>
      </div>
      
      <div class="hero-container">
        <!-- Theme Switcher -->
        <div class="theme-switcher">
          <Button
            :icon="isDarkMode ? 'pi pi-sun' : 'pi pi-moon'"
            @click="toggleDarkMode"
            class="theme-button"
            severity="secondary"
            outlined
            size="small"
            v-tooltip.bottom="isDarkMode ? 'Switch to Light Mode' : 'Switch to Dark Mode'"
          />
        </div>
        
        <div class="hero-content">
          <div class="brand-section">
            <div class="logo-container">
              <img
                src="/geopulse-logo.svg"
                alt="GeoPulse logo"
                class="logo"
                loading="eager"
              />
            </div>
            <h1 class="hero-title">GeoPulse</h1>
            <p class="hero-tagline">Turn Your GPS Data Into Rich Insights</p>
          </div>

          <div class="hero-description">
            <p class="description-text">
              Automatically track stays and trips, visualize your journeys on maps, analyze your movement patterns, and share locations with friends—all with complete privacy control.
            </p>
          </div>

          <div class="hero-actions">
            <div v-if="!authStore.isAuthenticated" class="auth-buttons">
              <Button
                v-if="registrationStatus.passwordRegistrationEnabled || registrationStatus.oidcRegistrationEnabled"
                label="Start Your Journey"
                icon="pi pi-arrow-right"
                as="router-link"
                to="/register"
                class="cta-button primary"
                size="large"
              />
              <Button
                label="Sign In"
                as="router-link"
                to="/login"
                severity="secondary"
                outlined
                class="cta-button secondary"
                size="large"
              />
            </div>
            <div v-if="authStore.isAuthenticated" class="app-buttons-grid">
              <Button
                label="Explore Your Timeline"
                icon="pi pi-calendar"
                as="router-link"
                to="/app/timeline"
                class="cta-button primary grid-button"
                size="large"
              />
              <Button
                label="View Your Dashboard"
                icon="pi pi-chart-bar"
                as="router-link"
                to="/app/dashboard"
                severity="secondary"
                outlined
                class="cta-button secondary grid-button"
                size="large"
              />
              <Button
                label="Journey Insights"
                icon="pi pi-compass"
                as="router-link"
                to="/app/journey-insights"
                severity="secondary"
                outlined
                class="cta-button secondary grid-button"
                size="large"
              />
              <Button
                label="Connect with Friends"
                icon="pi pi-users"
                as="router-link"
                to="/app/friends"
                severity="secondary"
                outlined
                class="cta-button secondary grid-button"
                size="large"
              />
            </div>
          </div>

          <div class="hero-badges">
            <div class="badge">
              <i class="pi pi-shield"></i>
              <span>Privacy First</span>
            </div>
            <div class="badge">
              <i class="pi pi-mobile"></i>
              <span>Cross Platform</span>
            </div>
            <div class="badge">
              <i class="pi pi-github"></i>
              <span>Open Source</span>
            </div>
          </div>
        </div>
        
      </div>
    </section>

    <!-- Features Section -->
    <section class="features-section">
      <div class="features-container">
        <div class="section-header">
          <h2 class="section-title">Powerful Features</h2>
          <p class="section-subtitle">
            Everything you need to understand and visualize your location data
          </p>
        </div>

        <div class="features-grid">
          <Card
            v-for="feature in features"
            :key="feature.id"
            class="feature-card"
          >
            <template #content>
              <div class="feature-content">
                <div class="feature-icon">
                  <i :class="feature.icon"></i>
                </div>
                <div class="feature-text">
                  <h3 class="feature-title">{{ feature.title }}</h3>
                  <p class="feature-description">{{ feature.description }}</p>
                </div>
              </div>
            </template>
          </Card>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

// Composables
const authStore = useAuthStore()
const registrationStatus = ref({ passwordRegistrationEnabled: false, oidcRegistrationEnabled: false });

// Dark mode state
const isDarkMode = ref(false)

// Features data
const features = ref([
  {
    id: 1,
    icon: 'pi pi-mobile',
    title: 'GPS Data Integration',
    description: 'Connect with OwnTracks, Overland or Dawarich apps to automatically receive your location data, or import/export your tracking history in OwnTracks format for seamless data management.'
  },
  {
    id: 2,
    icon: 'pi pi-map',
    title: 'Interactive Map Tracking',
    description: 'View your complete movement history on an interactive map for any selected time period, with detailed routes and location markers.'
  },
  {
    id: 3,
    icon: 'pi pi-calendar',
    title: 'Smart Timeline Analysis',
    description: 'Automatically generated timeline that intelligently categorizes your GPS data into stays and trips, showing your daily activities and travel patterns with precise timing.'
  },
  {
    id: 4,
    icon: 'pi pi-chart-bar',
    title: 'Comprehensive Dashboard',
    description: 'Rich statistics including places visited, total distance traveled, longest journeys, top locations, distance charts, and detailed analytics about your movement patterns.'
  },
  {
    id: 5,
    icon: 'pi pi-users',
    title: 'Friend Network',
    description: 'Add friends to share locations with each other, see real-time updates, and maintain connections while keeping full control over your privacy settings'
  },
  {
    id: 6,
    icon: 'pi pi-share-alt',
    title: 'Flexible Sharing',
    description: 'Share your current location with anyone - even non-registered users - through secure, temporary links that you control and can revoke anytime.'
  }
])

// Dark mode methods
const toggleDarkMode = () => {
  document.documentElement.classList.toggle('p-dark')
  isDarkMode.value = document.documentElement.classList.contains('p-dark')
  localStorage.setItem('darkMode', isDarkMode.value.toString())
}

// Lifecycle
onMounted(async () => {
  // Sync dark mode state with current DOM state (already initialized in main.js)
  isDarkMode.value = document.documentElement.classList.contains('p-dark')
  
  // Check authentication state to display correct buttons
  await authStore.checkAuth()

  authStore.getRegistrationStatus().then(status => {
    registrationStatus.value = status;
  });
})
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  background: var(--gp-surface-light);
}

/* Hero Section */
.hero-section {
  position: relative;
  min-height: 75vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.hero-background {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, var(--gp-surface-white) 0%, var(--gp-surface-light) 100%);
}

.hero-gradient {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: radial-gradient(ellipse at center, rgba(26, 86, 219, 0.1) 0%, transparent 70%);
}

.hero-container {
  position: relative;
  max-width: 1200px;
  margin: 0 auto;
  padding: 2rem 1rem 5rem 1rem;
  z-index: 1;
}

/* Theme Switcher */
.theme-switcher {
  position: absolute;
  top: 1rem;
  right: 1rem;
  z-index: 10;
}

.theme-button {
  width: 2.5rem !important;
  height: 2.5rem !important;
  padding: 0 !important;
  background: var(--gp-surface-white) !important;
  border: 1px solid var(--gp-border-light) !important;
  border-radius: 50% !important;
  box-shadow: var(--gp-shadow-light) !important;
  backdrop-filter: blur(10px);
  transition: all 0.2s ease !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
}

.theme-button:hover {
  box-shadow: var(--gp-shadow-medium) !important;
  border-color: var(--gp-border-medium) !important;
  transform: translateY(-1px) !important;
}

.theme-button:focus {
  box-shadow: var(--gp-shadow-medium), 0 0 0 3px rgba(26, 86, 219, 0.1) !important;
}

.theme-button .p-button-icon {
  font-size: 1rem !important;
  color: var(--gp-text-primary) !important;
}

.hero-content {
  text-align: center;
  max-width: 800px;
  margin: 0 auto;
}

/* Brand Section */
.brand-section {
  margin-bottom: 3rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.logo-container {
  margin-bottom: 1.5rem;
  display: flex;
  justify-content: center;
  align-items: center;
}

.logo {
  width: 200px;
  height: auto;
  filter: drop-shadow(0 4px 8px rgba(26, 86, 219, 0.2));
  transition: transform 0.3s ease;
  display: block;
}

.logo:hover {
  transform: scale(1.05);
}

.hero-title {
  font-size: clamp(2.5rem, 6vw, 4rem);
  font-weight: 700;
  margin-bottom: 1rem;
  color: var(--gp-text-primary);
  line-height: 1.1;
  letter-spacing: -0.02em;
}

.hero-tagline {
  font-size: clamp(1.1rem, 3vw, 1.5rem);
  color: var(--gp-text-secondary);
  margin: 0;
  font-weight: 500;
}

/* Hero Description */
.hero-description {
  margin-bottom: 3rem;
}

.description-text {
  font-size: clamp(1rem, 2.5vw, 1.25rem);
  line-height: 1.6;
  color: var(--gp-text-secondary);
  max-width: 600px;
  margin: 0 auto;
}

/* Hero Actions */
.hero-actions {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  align-items: center;
  margin-bottom: 2rem;
}

.auth-buttons {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  align-items: center;
}

.app-buttons-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
  max-width: 600px;
  width: 100%;
}

.grid-button {
  min-width: 200px;
  text-align: center;
}

.cta-button {
  min-width: 200px;
  padding: 1rem 2rem;
  font-weight: 600;
  border-radius: var(--gp-radius-medium);
  transition: all 0.3s ease;
}

.cta-button.primary {
  background: var(--gp-primary);
  border-color: var(--gp-primary);
  color: white;
}

.cta-button.primary:hover {
  background: var(--gp-primary-hover);
  border-color: var(--gp-primary-hover);
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(26, 86, 219, 0.3);
}

.cta-button.secondary {
  border-color: var(--gp-border-medium);
  color: var(--gp-text-primary);
}

.cta-button.secondary:hover {
  border-color: var(--gp-primary);
  color: var(--gp-primary);
  transform: translateY(-1px);
}

/* Hero Badges */
.hero-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  justify-content: center;
  margin-bottom: 3rem;
}

.badge {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-large);
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--gp-text-secondary);
  box-shadow: var(--gp-shadow-light);
}

.badge i {
  color: var(--gp-primary);
}


/* Features Section */
.features-section {
  padding: 3rem 0 5rem;
  background: var(--gp-surface-white);
}

.features-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 1rem;
}

.section-header {
  text-align: center;
  margin-bottom: 4rem;
}

.section-title {
  font-size: clamp(2rem, 4vw, 3rem);
  font-weight: 700;
  margin-bottom: 1rem;
  color: var(--gp-text-primary);
}

.section-subtitle {
  font-size: clamp(1rem, 2.5vw, 1.25rem);
  color: var(--gp-text-secondary);
  max-width: 600px;
  margin: 0 auto;
  line-height: 1.6;
}

.features-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 2rem;
}

.feature-card {
  border: 1px solid var(--gp-border-light);
  box-shadow: var(--gp-shadow-light);
  transition: all 0.3s ease;
}

.feature-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--gp-shadow-card);
  border-color: var(--gp-primary);
}

.feature-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 1.5rem;
  padding: 2rem 1.5rem;
}

.feature-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 4rem;
  height: 4rem;
  background: linear-gradient(135deg, var(--gp-primary), var(--gp-primary-hover));
  color: white;
  border-radius: 50%;
  font-size: 1.5rem;
  box-shadow: 0 4px 12px rgba(26, 86, 219, 0.3);
}

.feature-text {
  flex: 1;
}

.feature-title {
  font-size: 1.25rem;
  font-weight: 600;
  margin-bottom: 0.75rem;
  color: var(--gp-text-primary);
}

.feature-description {
  color: var(--gp-text-secondary);
  line-height: 1.6;
  font-size: 0.95rem;
}

/* Benefits Section */
.benefits-section {
  padding: 5rem 0;
  background: var(--gp-surface-light);
}

.benefits-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 1rem;
}

.benefits-content {
  display: flex;
  flex-direction: column;
  gap: 3rem;
}

.benefits-header {
  text-align: center;
  max-width: 600px;
  margin: 0 auto;
}

.benefits-title {
  font-size: clamp(2rem, 4vw, 2.5rem);
  font-weight: 700;
  margin-bottom: 1rem;
  color: var(--gp-text-primary);
}

.benefits-subtitle {
  font-size: clamp(1rem, 2.5vw, 1.25rem);
  color: var(--gp-text-secondary);
  line-height: 1.6;
  margin: 0;
}

.benefits-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 2rem;
}

.benefit-card {
  border: 1px solid var(--gp-border-light);
  box-shadow: var(--gp-shadow-light);
  transition: all 0.3s ease;
}

.benefit-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--gp-shadow-card);
  border-color: var(--gp-primary);
}

.benefit-content {
  display: flex;
  align-items: flex-start;
  gap: 1.5rem;
  padding: 2rem 1.5rem;
}

.benefit-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 3.5rem;
  height: 3.5rem;
  background: linear-gradient(135deg, var(--gp-primary), var(--gp-primary-hover));
  color: white;
  border-radius: 50%;
  font-size: 1.25rem;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(26, 86, 219, 0.3);
}

.benefit-text h3 {
  font-size: 1.25rem;
  font-weight: 600;
  margin-bottom: 0.75rem;
  color: var(--gp-text-primary);
}

.benefit-text p {
  color: var(--gp-text-secondary);
  line-height: 1.6;
  margin: 0;
  font-size: 0.95rem;
}

/* CTA Section */
.cta-section {
  padding: 5rem 0;
  background: var(--gp-surface-white);
}

.cta-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 0 1rem;
}

.cta-card {
  border: 1px solid var(--gp-border-light);
  box-shadow: var(--gp-shadow-card);
  background: linear-gradient(135deg, var(--gp-surface-white) 0%, var(--gp-surface-light) 100%);
}

.cta-content {
  text-align: center;
  padding: 3rem 2rem;
}

.cta-title {
  font-size: clamp(1.75rem, 4vw, 2.5rem);
  font-weight: 700;
  margin-bottom: 1rem;
  color: var(--gp-text-primary);
}

.cta-description {
  font-size: clamp(1rem, 2.5vw, 1.25rem);
  color: var(--gp-text-secondary);
  margin-bottom: 2rem;
  line-height: 1.6;
}

.cta-actions {
  display: flex;
  justify-content: center;
}

.cta-button-large {
  padding: 1.25rem 3rem;
  font-size: 1.1rem;
  font-weight: 600;
  background: var(--gp-primary);
  border-color: var(--gp-primary);
  border-radius: var(--gp-radius-medium);
  transition: all 0.3s ease;
}

.cta-button-large:hover {
  background: var(--gp-primary-hover);
  border-color: var(--gp-primary-hover);
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(26, 86, 219, 0.3);
}

/* Responsive Design */
@media (min-width: 640px) {
  .auth-buttons {
    flex-direction: row;
    justify-content: center;
  }
  
  .features-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .feature-content {
    flex-direction: row;
    text-align: left;
  }
  
  .feature-icon {
    flex-shrink: 0;
  }
}

@media (min-width: 1024px) {
  .features-grid {
    grid-template-columns: repeat(3, 1fr);
  }
  
  .benefits-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (min-width: 1200px) {
  .hero-container,
  .features-container,
  .benefits-container,
  .cta-container {
    padding: 0 2rem;
  }
}

/* Accessibility */
@media (prefers-reduced-motion: reduce) {
  .feature-card,
  .cta-button,
  .logo {
    transition: none;
    animation: none;
  }
  
  .feature-card:hover,
  .cta-button:hover {
    transform: none;
  }
}

@media (max-width: 768px) {
  .hero-section {
    min-height: 70vh;
  }
  
  .theme-switcher {
    top: 1rem;
    right: 0.5rem;
  }
  
  .theme-button {
    width: 2.25rem !important;
    height: 2.25rem !important;
  }
  
  .theme-button .p-button-icon {
    font-size: 0.9rem !important;
  }
  
  .app-buttons-grid {
    grid-template-columns: 1fr;
    gap: 0.75rem;
  }
  
  .grid-button {
    min-width: 250px;
  }
}

/* Dark Mode Styles */
.p-dark .home-page {
  background: var(--gp-surface-darker);
}

.p-dark .hero-background {
  background: linear-gradient(135deg, var(--gp-surface-dark) 0%, var(--gp-surface-darker) 100%);
}

.p-dark .hero-gradient {
  background: radial-gradient(ellipse at center, rgba(59, 130, 246, 0.15) 0%, transparent 70%);
}

.p-dark .theme-button {
  background: var(--gp-surface-dark) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .theme-button:hover {
  border-color: var(--gp-border-medium) !important;
}

.p-dark .theme-button .p-button-icon {
  color: var(--gp-text-primary) !important;
}

.p-dark .badge {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
  color: var(--gp-text-secondary);
}

.p-dark .features-section {
  background: var(--gp-surface-dark);
}

.p-dark .feature-card {
  background: var(--gp-surface-white);
  border-color: var(--gp-border-dark);
}

.p-dark .feature-card:hover {
  border-color: var(--gp-primary);
}

.p-dark .benefit-card {
  background: var(--gp-surface-white);
  border-color: var(--gp-border-dark);
}

.p-dark .benefit-card:hover {
  border-color: var(--gp-primary);
}

.p-dark .cta-card {
  background: var(--gp-surface-white);
  border-color: var(--gp-border-dark);
}

.p-dark .benefits-section {
  background: var(--gp-surface-darker);
}

.p-dark .cta-section {
  background: var(--gp-surface-dark);
}

/* Focus styles */
.cta-button:focus-visible {
  outline: 2px solid var(--gp-primary);
  outline-offset: 2px;
}

/* Print styles */
@media print {
  .hero-actions,
  .cta-section {
    display: none;
  }
}
</style>
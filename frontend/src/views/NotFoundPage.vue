<template>
  <div class="not-found-page">
    <div class="not-found-container">
      <div class="not-found-content">
        <!-- 404 Illustration -->
        <div class="not-found-illustration">
          <div class="number-404">
            <span class="digit-4">4</span>
            <span class="digit-0">
              <i class="pi pi-map-marker location-icon"></i>
            </span>
            <span class="digit-4">4</span>
          </div>
          <div class="illustration-subtitle">Location Not Found</div>
        </div>

        <!-- Error Message -->
        <div class="not-found-message">
          <h1 class="error-title">Oops! You've wandered off the map</h1>
          <p class="error-description">
            The page you're looking for doesn't exist or has been moved to a different location.
            Let's get you back on track.
          </p>
        </div>

        <!-- Navigation Options -->
        <div class="navigation-options">
          <h3 class="options-title">Where would you like to go?</h3>
          <div class="options-grid">
            <!-- Home -->
            <router-link to="/" class="option-card">
              <div class="option-icon">
                <i class="pi pi-home"></i>
              </div>
              <div class="option-content">
                <h4 class="option-title">Home</h4>
                <p class="option-description">Start your journey from the beginning</p>
              </div>
            </router-link>

            <!-- Dashboard (if authenticated) -->
            <router-link v-if="authStore.isAuthenticated" to="/app/timeline" class="option-card">
              <div class="option-icon">
                <i class="pi pi-calendar"></i>
              </div>
              <div class="option-content">
                <h4 class="option-title">Timeline</h4>
                <p class="option-description">View your location timeline</p>
              </div>
            </router-link>

            <!-- Login (if not authenticated) -->
            <router-link v-if="!authStore.isAuthenticated" to="/login" class="option-card">
              <div class="option-icon">
                <i class="pi pi-sign-in"></i>
              </div>
              <div class="option-content">
                <h4 class="option-title">Sign In</h4>
                <p class="option-description">Access your GeoPulse account</p>
              </div>
            </router-link>

            <!-- Dashboard (if authenticated) -->
            <router-link v-if="authStore.isAuthenticated" to="/app/dashboard" class="option-card">
              <div class="option-icon">
                <i class="pi pi-chart-bar"></i>
              </div>
              <div class="option-content">
                <h4 class="option-title">Dashboard</h4>
                <p class="option-description">Check your location insights</p>
              </div>
            </router-link>

            <!-- Register (if not authenticated) -->
            <router-link v-if="!authStore.isAuthenticated" to="/register" class="option-card">
              <div class="option-icon">
                <i class="pi pi-user-plus"></i>
              </div>
              <div class="option-content">
                <h4 class="option-title">Sign Up</h4>
                <p class="option-description">Create a new account</p>
              </div>
            </router-link>
          </div>
        </div>

        <!-- Additional Actions -->
        <div class="additional-actions">
          <Button
            label="Go Back"
            icon="pi pi-arrow-left"
            severity="secondary"
            outlined
            @click="goBack"
            class="back-button"
          />
        </div>

        <!-- Help Section -->
        <div class="help-section">
          <details class="help-details">
            <summary class="help-summary">Need help finding what you're looking for?</summary>
            <div class="help-content">
              <p>Here are some common pages you might be looking for:</p>
              <ul class="help-list">
                <li><router-link to="/app/timeline">Timeline</router-link> - View your location history</li>
                <li><router-link to="/app/dashboard">Dashboard</router-link> - Location insights and analytics</li>
                <li><router-link to="/app/journey-insights">Journey Insights</router-link> - Discover travel patterns</li>
                <li><router-link to="/app/friends">Friends</router-link> - Manage your friend connections</li>
                <li v-if="!authStore.isAuthenticated"><router-link to="/login">Sign In</router-link> - Access your account</li>
              </ul>
              <p class="help-note">
                If you believe this is a broken link, please contact support and we'll fix it.
              </p>
            </div>
          </details>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import Button from 'primevue/button'

const router = useRouter()
const authStore = useAuthStore()

// Methods
const goBack = () => {
  if (window.history.length > 1) {
    router.go(-1)
  } else {
    router.push('/')
  }
}


// Lifecycle
onMounted(() => {
  // Log 404 for analytics (optional)
  console.warn('404 Page Not Found:', window.location.pathname)
})
</script>

<style scoped>
.not-found-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gp-surface-light);
  padding: var(--gp-spacing-lg);
}

.not-found-container {
  max-width: 800px;
  width: 100%;
  text-align: center;
}

.not-found-content {
  background: var(--gp-surface-white);
  border-radius: var(--gp-radius-large);
  padding: var(--gp-spacing-xxl);
  box-shadow: var(--gp-shadow-card);
  border: 1px solid var(--gp-border-light);
}

/* 404 Illustration */
.not-found-illustration {
  margin-bottom: var(--gp-spacing-xxl);
}

.number-404 {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--gp-spacing-md);
  margin-bottom: var(--gp-spacing-lg);
  font-family: 'Inter', sans-serif;
  font-weight: 900;
}

.digit-4,
.digit-0 {
  font-size: clamp(4rem, 15vw, 8rem);
  line-height: 1;
  color: var(--gp-primary);
  text-shadow: 0 4px 8px rgba(26, 86, 219, 0.2);
}

.digit-0 {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 1em;
  height: 1em;
  border: 0.1em solid var(--gp-primary);
  border-radius: 50%;
  background: transparent;
  font-size: clamp(4rem, 15vw, 8rem);
}

.location-icon {
  font-size: 0.4em;
  color: var(--gp-primary);
  animation: pulse 2s infinite;
}

.illustration-subtitle {
  font-size: 1.25rem;
  color: var(--gp-text-secondary);
  font-weight: 600;
  margin-top: var(--gp-spacing-md);
}

@keyframes pulse {
  0%, 100% {
    transform: scale(1);
    opacity: 1;
  }
  50% {
    transform: scale(1.1);
    opacity: 0.8;
  }
}

/* Error Message */
.not-found-message {
  margin-bottom: var(--gp-spacing-xxl);
  padding: 0 var(--gp-spacing-md);
}

.error-title {
  font-size: clamp(1.5rem, 3.5vw, 2.25rem);
  font-weight: 700;
  color: var(--gp-text-primary);
  margin: 0 0 var(--gp-spacing-xl);
  line-height: 1.3;
}

.error-description {
  font-size: clamp(0.95rem, 2vw, 1.125rem);
  color: var(--gp-text-secondary);
  line-height: 1.7;
  margin: 0;
  max-width: 550px;
  margin-left: auto;
  margin-right: auto;
}

/* Navigation Options */
.navigation-options {
  margin-bottom: var(--gp-spacing-xxl);
  padding: 0 var(--gp-spacing-sm);
}

.options-title {
  font-size: clamp(1.25rem, 2.5vw, 1.5rem);
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 var(--gp-spacing-xl);
}

.options-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: var(--gp-spacing-xl);
  margin-bottom: var(--gp-spacing-xl);
}

.option-card {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-lg);
  padding: var(--gp-spacing-xl);
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  text-decoration: none;
  transition: all 0.3s ease;
  text-align: left;
  min-height: 80px;
}

.option-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--gp-shadow-card-hover);
  border-color: var(--gp-primary);
  background: var(--gp-surface-white);
}

.option-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gp-primary);
  color: white;
  border-radius: var(--gp-radius-medium);
  flex-shrink: 0;
  font-size: 1.5rem;
}

.option-content {
  flex: 1;
}

.option-title {
  font-size: 1.125rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 var(--gp-spacing-sm);
}

.option-description {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.5;
}

/* Additional Actions */
.additional-actions {
  display: flex;
  gap: var(--gp-spacing-md);
  justify-content: center;
  flex-wrap: wrap;
  margin-bottom: var(--gp-spacing-xxl);
  padding: var(--gp-spacing-lg) 0;
}

.back-button {
  min-width: 140px;
}

/* Help Section */
.help-section {
  border-top: 1px solid var(--gp-border-light);
  padding-top: var(--gp-spacing-lg);
  text-align: left;
}

.help-details {
  max-width: 600px;
  margin: 0 auto;
}

.help-summary {
  font-size: 1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  cursor: pointer;
  padding: var(--gp-spacing-md);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  transition: all 0.2s ease;
}

.help-summary:hover {
  background: var(--gp-surface-white);
  border-color: var(--gp-primary);
}

.help-content {
  padding: var(--gp-spacing-lg);
  margin-top: var(--gp-spacing-md);
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-light);
}

.help-content p {
  margin: 0 0 var(--gp-spacing-md);
  color: var(--gp-text-secondary);
  line-height: 1.6;
}

.help-list {
  list-style: none;
  padding: 0;
  margin: 0 0 var(--gp-spacing-md);
}

.help-list li {
  padding: var(--gp-spacing-xs) 0;
}

.help-list a {
  color: var(--gp-primary);
  text-decoration: none;
  font-weight: 500;
  transition: color 0.2s ease;
}

.help-list a:hover {
  color: var(--gp-primary-dark);
  text-decoration: underline;
}

.help-note {
  font-size: 0.875rem;
  color: var(--gp-text-muted);
  margin: 0;
}


/* Dark Mode */
.p-dark .not-found-page {
  background: var(--gp-surface-darker);
}

.p-dark .not-found-content {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark .option-card {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
}

.p-dark .option-card:hover {
  background: var(--gp-surface-dark);
  border-color: var(--gp-primary);
}

.p-dark .help-summary {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
}

.p-dark .help-summary:hover {
  background: var(--gp-surface-dark);
}

.p-dark .help-content {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
}

.p-dark .help-section {
  border-top-color: var(--gp-border-dark);
}

/* Responsive Design */
@media (max-width: 768px) {
  .not-found-content {
    padding: var(--gp-spacing-xl);
  }
  
  .error-title {
    font-size: clamp(1.25rem, 4vw, 1.75rem);
    margin-bottom: var(--gp-spacing-lg);
  }
  
  .options-grid {
    grid-template-columns: 1fr;
    gap: var(--gp-spacing-lg);
  }
  
  .option-card {
    padding: var(--gp-spacing-lg);
    gap: var(--gp-spacing-md);
  }
  
  .additional-actions {
    flex-direction: column;
    align-items: center;
  }
  
  .back-button {
    width: 100%;
    max-width: 200px;
  }
}

@media (max-width: 480px) {
  .not-found-content {
    padding: var(--gp-spacing-lg);
  }
  
  .not-found-message {
    padding: 0 var(--gp-spacing-xs);
  }
  
  .error-title {
    font-size: clamp(1.125rem, 5vw, 1.5rem);
    line-height: 1.4;
  }
  
  .error-description {
    font-size: 0.875rem;
  }
  
  .option-card {
    flex-direction: column;
    text-align: center;
    gap: var(--gp-spacing-sm);
    padding: var(--gp-spacing-md);
  }
  
  .option-icon {
    width: 40px;
    height: 40px;
    font-size: 1.25rem;
  }
}
</style>
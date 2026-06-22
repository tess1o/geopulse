<template>
  <AppLayout>
    <PageContainer>
      <div class="help-page">
        <!-- Page Header -->
        <div class="page-header">
          <div class="header-content">
            <div class="header-text">
              <h1 class="page-title">Help & Support</h1>
              <p class="page-description">
                Get help, access documentation, and report issues
              </p>
            </div>
          </div>
        </div>

        <!-- Section 1: Having Issues? -->
        <Card class="help-section">
          <template #content>
            <div class="section-content">
              <div class="section-header">
                <div class="section-icon issues-icon">
                  <i class="pi pi-exclamation-circle"></i>
                </div>
                <div class="section-text">
                  <h2 class="section-title">Having Issues?</h2>
                  <p class="section-description">
                    If you're experiencing problems with timeline generation, trip classification, or other features,
                    you can export anonymized debug data to help us troubleshoot.
                  </p>
                </div>
              </div>

              <div class="section-actions">
                <div class="action-card debug-export-card">
                  <div class="action-icon">
                    <i class="pi pi-download"></i>
                  </div>
                  <div class="action-content">
                    <h3 class="action-title">Export Debug Data</h3>
                    <p class="action-description">
                      Export your GPS data with privacy-preserving coordinate shifts.
                      All coordinates are anonymized by shifting them by a random offset,
                      making it safe to share for troubleshooting.
                    </p>
                    <ul class="action-list">
                      <li>Coordinates are shifted to protect your privacy</li>
                      <li>Location names are anonymized</li>
                      <li>Includes timeline configuration for analysis</li>
                    </ul>
                    <Button
                      label="Export Debug Data"
                      icon="pi pi-download"
                      severity="info"
                      @click="goToDebugExport"
                      class="action-button"
                    />
                  </div>
                </div>

                <div class="action-card report-issue-card">
                  <div class="action-icon">
                    <i class="pi pi-github"></i>
                  </div>
                  <div class="action-content">
                    <h3 class="action-title">Report an Issue</h3>
                    <p class="action-description">
                      Found a bug or have a feature request? Report it on GitHub.
                    </p>
                    <div class="report-steps">
                      <p class="step-title">How to report effectively:</p>
                      <ol class="steps-list">
                        <li>Export debug data using the button above</li>
                        <li>Create a new issue on GitHub</li>
                        <li>Attach the debug export ZIP file</li>
                        <li>Describe the problem and expected behavior</li>
                      </ol>
                    </div>
                    <Button
                      label="Create GitHub Issue"
                      icon="pi pi-external-link"
                      severity="secondary"
                      outlined
                      @click="openGitHubIssues"
                      class="action-button"
                    />
                  </div>
                </div>
              </div>
            </div>
          </template>
        </Card>

        <!-- Section 2: Documentation -->
        <Card class="help-section">
          <template #content>
            <div class="section-content">
              <div class="section-header">
                <div class="section-icon docs-icon">
                  <i class="pi pi-book"></i>
                </div>
                <div class="section-text">
                  <h2 class="section-title">Documentation</h2>
                  <p class="section-description">
                    Comprehensive guides and documentation for all GeoPulse features
                  </p>
                </div>
              </div>

              <div class="docs-grid">
                <a
                  v-for="doc in docLinks"
                  :key="doc.title"
                  :href="doc.url"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="doc-link-card"
                >
                  <div class="doc-icon">
                    <i :class="doc.icon"></i>
                  </div>
                  <div class="doc-content">
                    <h3 class="doc-title">{{ doc.title }}</h3>
                    <p class="doc-description">{{ doc.description }}</p>
                  </div>
                  <i class="pi pi-external-link doc-external-icon"></i>
                </a>
              </div>
            </div>
          </template>
        </Card>

        <!-- Section 3: About -->
        <Card class="help-section">
          <template #content>
            <div class="section-content">
              <div class="section-header">
                <div class="section-icon about-icon">
                  <i class="pi pi-info-circle"></i>
                </div>
                <div class="section-text">
                  <h2 class="section-title">About GeoPulse</h2>
                  <p class="section-description">
                    Application information and credits
                  </p>
                </div>
              </div>

              <div class="about-content">
                <div class="about-grid">
                  <div class="about-item">
                    <div class="about-label">Version</div>
                    <div class="about-value">{{ appVersion || 'Loading...' }}</div>
                  </div>
                  <div class="about-item">
                    <div class="about-label">License</div>
                    <div class="about-value">
                      <a
                        href="https://github.com/tess1o/geopulse/blob/main/LICENSE"
                        target="_blank"
                        rel="noopener noreferrer"
                        class="about-link"
                      >
                        BSL 1.1 License <i class="pi pi-external-link"></i>
                      </a>
                    </div>
                  </div>
                  <div class="about-item">
                    <div class="about-label">Repository</div>
                    <div class="about-value">
                      <a
                        href="https://github.com/tess1o/geopulse"
                        target="_blank"
                        rel="noopener noreferrer"
                        class="about-link"
                      >
                        GitHub <i class="pi pi-external-link"></i>
                      </a>
                    </div>
                  </div>
                  <div class="about-item">
                    <div class="about-label">Author</div>
                    <div class="about-value">
                      <a
                        href="https://github.com/tess1o"
                        target="_blank"
                        rel="noopener noreferrer"
                        class="about-link"
                      >
                        tess1o <i class="pi pi-external-link"></i>
                      </a>
                    </div>
                  </div>
                </div>

                <div class="tech-stack">
                  <h3 class="tech-title">Built With</h3>
                  <div class="tech-tags">
                    <Tag value="Vue 3" severity="success" />
                    <Tag value="PrimeVue" severity="info" />
                    <Tag value="Leaflet" severity="warning" />
                    <Tag value="Quarkus" severity="danger" />
                    <Tag value="PostgreSQL" severity="info" />
                    <Tag value="PostGIS" severity="success" />
                  </div>
                </div>
              </div>
            </div>
          </template>
        </Card>
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import Card from 'primevue/card'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import apiService from '@/utils/apiService'

const router = useRouter()
const appVersion = ref(null)

const docLinks = ref([
  {
    title: 'Getting Started',
    description: 'Quick start guide and initial setup',
    icon: 'pi pi-play',
    url: 'https://tess1o.github.io/geopulse/docs/getting-started/quick-start'
  },
  {
    title: 'Timeline Features',
    description: 'How timeline generation and processing works',
    icon: 'pi pi-calendar',
    url: 'https://tess1o.github.io/geopulse/docs/user-guide/core-features/timeline'
  },
  {
    title: 'Trip Classification',
    description: 'Understanding how trips are classified',
    icon: 'pi pi-car',
    url: 'https://tess1o.github.io/geopulse/docs/user-guide/timeline/travel_classification'
  },
  {
    title: 'Location Sources',
    description: 'Supported GPS tracking sources and setup',
    icon: 'pi pi-map-marker',
    url: 'https://tess1o.github.io/geopulse/docs/user-guide/gps-sources/overview'
  },
  {
    title: 'FAQ',
    description: 'Frequently asked questions',
    icon: 'pi pi-question-circle',
    url: 'https://tess1o.github.io/geopulse/docs/faq'
  },
  {
    title: 'Full Documentation',
    description: 'Complete documentation with search',
    icon: 'pi pi-book',
    url: 'https://tess1o.github.io/geopulse/'
  }
])

const goToDebugExport = () => {
  router.push('/app/debug-export')
}

const openGitHubIssues = () => {
  window.open('https://github.com/tess1o/geopulse/issues/new', '_blank')
}

const fetchVersion = async () => {
  try {
    const response = await apiService.get('/version')
    if (response?.version) {
      appVersion.value = response.version
    }
  } catch (error) {
    console.error('Failed to fetch version:', error)
    appVersion.value = 'Unknown'
  }
}

onMounted(() => {
  fetchVersion()
})
</script>

<style scoped>
.help-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 1rem;
  width: 100%;
  box-sizing: border-box;
}

/* Page Header */
.page-header {
  margin-bottom: 2rem;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 2rem;
}

.header-text {
  flex: 1;
}

.page-title {
  font-size: 2rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.5rem 0;
}

.page-description {
  font-size: 1.1rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.5;
}

/* Help Sections */
.help-section {
  margin-bottom: 2rem;
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-large);
}

.p-dark .help-section {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-medium);
}

.section-content {
  padding: 0.5rem;
}

.section-header {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  margin-bottom: 2rem;
}

.section-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 3rem;
  height: 3rem;
  border-radius: 50%;
  font-size: 1.5rem;
  flex-shrink: 0;
  color: white;
}

.issues-icon {
  background: #f59e0b;
}

.docs-icon {
  background: var(--gp-primary);
}

.about-icon {
  background: #10b981;
}

.section-text {
  flex: 1;
}

.section-title {
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.5rem 0;
}

.section-description {
  font-size: 1rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.5;
}

/* Action Cards */
.section-actions {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 1.5rem;
}

.action-card {
  display: flex;
  gap: 1rem;
  padding: 1.5rem;
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  transition: all 0.2s ease;
}

.p-dark .action-card {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
}

.debug-export-card {
  border-left: 4px solid #3b82f6;
}

.report-issue-card {
  border-left: 4px solid #64748b;
}

.action-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem;
  height: 2.5rem;
  background: var(--gp-primary);
  color: white;
  border-radius: 50%;
  font-size: 1.25rem;
  flex-shrink: 0;
}

.action-content {
  flex: 1;
}

.action-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.5rem 0;
}

.action-description {
  font-size: 0.95rem;
  color: var(--gp-text-secondary);
  margin: 0 0 1rem 0;
  line-height: 1.5;
}

.action-list {
  list-style: none;
  padding: 0;
  margin: 0 0 1rem 0;
}

.action-list li {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  padding: 0.25rem 0;
  padding-left: 1.5rem;
  position: relative;
}

.action-list li::before {
  content: '✓';
  position: absolute;
  left: 0;
  color: #10b981;
  font-weight: bold;
}

.report-steps {
  margin-bottom: 1rem;
}

.step-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.5rem 0;
}

.steps-list {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin: 0;
  padding-left: 1.5rem;
  line-height: 1.6;
}

.steps-list li {
  margin-bottom: 0.25rem;
}

.action-button {
  margin-top: 0.5rem;
}

/* Documentation Grid */
.docs-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 1rem;
}

.doc-link-card {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.25rem;
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  text-decoration: none;
  transition: all 0.2s ease;
  position: relative;
}

.doc-link-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--gp-shadow-medium);
  border-color: var(--gp-primary);
}

.p-dark .doc-link-card {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
}

.doc-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem;
  height: 2.5rem;
  background: var(--gp-primary);
  color: white;
  border-radius: 50%;
  font-size: 1.125rem;
  flex-shrink: 0;
}

.doc-content {
  flex: 1;
}

.doc-title {
  font-size: 1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.25rem 0;
}

.doc-description {
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.4;
}

.doc-external-icon {
  color: var(--gp-text-muted);
  font-size: 0.875rem;
}

/* About Section */
.about-content {
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.about-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1.5rem;
}

.about-item {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.about-label {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--gp-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.about-value {
  font-size: 1rem;
  color: var(--gp-text-primary);
  font-weight: 500;
}

.about-link {
  color: var(--gp-primary);
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  transition: all 0.2s ease;
}

.about-link:hover {
  text-decoration: underline;
}

.about-link i {
  font-size: 0.75rem;
}

.tech-stack {
  padding-top: 2rem;
  border-top: 1px solid var(--gp-border-light);
}

.p-dark .tech-stack {
  border-color: var(--gp-border-dark);
}

.tech-title {
  font-size: 1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 1rem 0;
}

.tech-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

/* Responsive Design */
@media (max-width: 768px) {
  .help-page {
    padding: 0 1rem;
  }

  .page-title {
    font-size: 1.5rem;
  }

  .page-description {
    font-size: 1rem;
  }

  .section-header {
    flex-direction: column;
    text-align: center;
  }

  .section-icon {
    margin: 0 auto;
  }

  .section-actions {
    grid-template-columns: 1fr;
  }

  .action-card {
    flex-direction: column;
    text-align: center;
  }

  .action-icon {
    margin: 0 auto;
  }

  .docs-grid {
    grid-template-columns: 1fr;
  }

  .about-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 480px) {
  .help-page {
    padding: 0 0.75rem;
  }

  .page-header {
    margin-bottom: 1.5rem;
  }

  .page-title {
    font-size: 1.3rem;
  }

  .section-title {
    font-size: 1.25rem;
  }

  .section-description {
    font-size: 0.9rem;
  }
}
</style>

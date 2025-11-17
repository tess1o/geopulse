<template>
  <AppLayout>
    <PageContainer>
      <div class="data-export-import-page">
        <!-- Page Header -->
        <div class="page-header">
          <div class="header-content">
            <div class="header-text">
              <h1 class="page-title">Data Export & Import</h1>
              <p class="page-description">
                Export your GeoPulse data for backup or import previously exported data
              </p>
            </div>
          </div>
        </div>

        <!-- Info Banner -->
        <Card class="info-banner">
          <template #content>
            <div class="banner-content">
              <div class="banner-icon">
                <i class="pi pi-info-circle"></i>
              </div>
              <div class="banner-text">
                <h3 class="banner-title">Data Security & Privacy</h3>
                <p class="banner-description">
                  Export files are not stored on the server. Make sure you download them before leaving this page.
                </p>
              </div>
            </div>
          </template>
        </Card>

        <!-- Main Tabs -->
        <TabContainer
            :tabs="tabItems"
            :activeIndex="activeTabIndex"
            @tab-change="handleTabChange"
            class="export-import-tabs"
        >
          <!-- Export Tab -->
          <DataExportTab v-if="activeTab === 'export'" />

          <!-- Import Tab -->
          <DataImportTab v-if="activeTab === 'import'" />
        </TabContainer>
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import {ref, computed} from 'vue'

// Layout components
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import TabContainer from '@/components/ui/layout/TabContainer.vue'

// Tab components
import DataExportTab from '@/components/data-export-import/DataExportTab.vue'
import DataImportTab from '@/components/data-export-import/DataImportTab.vue'

// State
const activeTab = ref('export')

// Tab configuration
const tabItems = ref([
  {
    label: 'Export Data',
    icon: 'pi pi-download',
    key: 'export'
  },
  {
    label: 'Import Data',
    icon: 'pi pi-upload',
    key: 'import'
  }
])

const activeTabIndex = computed(() => {
  return tabItems.value.findIndex(tab => tab.key === activeTab.value)
})

// Methods
const handleTabChange = (event) => {
  const selectedTab = tabItems.value[event.index]
  if (selectedTab) {
    activeTab.value = selectedTab.key
  }
}
</script>

<style scoped>
/* Page-level styles */
.data-export-import-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 1rem;
  width: 100%;
  box-sizing: border-box;
}

@media (max-width: 430px) {
  .data-export-import-page {
    padding: 0 0.75rem;
    max-width: calc(100vw - 1.5rem);
    box-sizing: border-box;
  }
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

/* Info Banner */
.info-banner {
  margin-bottom: 2rem;
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-medium);
  border-left: 4px solid var(--gp-primary);
  border-radius: var(--gp-radius-large);
}

.p-dark .info-banner {
  background: var(--gp-surface-dark) !important;
  border: 1px solid var(--gp-border-dark) !important;
  border-left: 4px solid var(--gp-primary) !important;
}

.banner-content {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
}

.banner-icon {
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

.banner-text {
  flex: 1;
}

.banner-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 0.5rem 0;
}

.banner-description {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.4;
}

/* Tab Sections */
.export-import-tabs {
  margin-bottom: 2rem;
}

/* Shared styles used by both tabs */
:deep(.tab-section) {
  padding: 0.5rem 0;
}

:deep(.export-form),
:deep(.import-form) {
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

:deep(.form-section) {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

:deep(.form-section-title) {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0;
}

:deep(.form-section-header) {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

:deep(.form-section-header .form-section-title) {
  margin: 0;
}

:deep(.select-all-button) {
  flex-shrink: 0;
}

:deep(.option-group-header) {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

:deep(.option-group-header .option-label) {
  margin-bottom: 0;
}

:deep(.data-types-grid) {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 1rem;
}

:deep(.data-type-option) {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 1rem;
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-light);
  transition: all 0.2s ease;
}

:deep(.data-type-option:hover) {
  border-color: var(--gp-primary);
  background: var(--gp-surface-hover);
}

:deep(.data-type-checkbox) {
  margin-top: 0.25rem;
}

:deep(.data-type-info) {
  flex: 1;
}

:deep(.data-type-label) {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: 0.25rem;
  cursor: pointer;
}

:deep(.data-type-icon) {
  color: var(--gp-primary);
}

:deep(.data-type-description) {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.4;
}

:deep(.date-range-controls) {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
}

:deep(.date-control) {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

:deep(.date-label) {
  font-weight: 500;
  color: var(--gp-text-primary);
}

:deep(.date-picker) {
  width: 100%;
}

.p-dark :deep(.date-picker .p-datepicker-dropdown .p-icon) {
  color: var(--gp-text-primary) !important;
}

.p-dark :deep(.date-picker .p-datepicker-dropdown) {
  background: var(--gp-surface-dark) !important;
  border-color: var(--gp-border-dark) !important;
}

:deep(.date-range-presets) {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
  margin-top: 0.5rem;
}

:deep(.form-actions) {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--gp-border-light);
}

:deep(.export-button),
:deep(.import-button) {
  min-width: 200px;
}

:deep(.export-info),
:deep(.import-info) {
  text-align: center;
}

:deep(.export-note),
:deep(.import-note) {
  color: var(--gp-text-secondary);
  font-style: italic;
}

:deep(.job-status-card) {
  margin-bottom: 2rem;
}

:deep(.job-status) {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

:deep(.job-header) {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

:deep(.job-title) {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0;
}

:deep(.job-details) {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

:deep(.job-detail) {
  display: flex;
  align-items: center;
  gap: 1rem;
}

:deep(.detail-label) {
  font-weight: 500;
  color: var(--gp-text-secondary);
  min-width: 100px;
}

:deep(.detail-value) {
  color: var(--gp-text-primary);
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

:deep(.job-progress) {
  width: 200px;
}

:deep(.progress-info) {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  align-items: flex-start;
}

:deep(.progress-text) {
  font-size: 0.9rem;
  font-weight: 500;
}

:deep(.progress-message) {
  font-size: 0.8rem;
  color: var(--gp-text-secondary);
  font-style: italic;
}

:deep(.progress-phase) {
  font-size: 0.8rem;
  color: var(--gp-text-secondary);
  font-style: italic;
}

:deep(.job-actions) {
  display: flex;
  gap: 1rem;
  justify-content: flex-start;
}

:deep(.format-options) {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

:deep(.format-option) {
  display: flex;
  align-items: stretch;
  gap: 0;
  padding: 0;
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-light);
  transition: all 0.2s ease;
  cursor: pointer;
}

:deep(.format-option:hover) {
  border-color: var(--gp-primary);
  background: var(--gp-surface-hover);
}

:deep(.format-option.selected) {
  border-color: var(--gp-primary);
  background: var(--gp-primary-50);
}

:deep(.format-radio) {
  margin: 1.25rem 0 0 1rem;
  flex-shrink: 0;
}

:deep(.format-info) {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 1rem 1rem 1rem 0.75rem;
  cursor: pointer;
}

:deep(.format-label) {
  display: block;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: 0.25rem;
  cursor: pointer;
}

:deep(.format-description) {
  font-size: 0.9rem;
  color: var(--gp-text-secondary);
  margin: 0;
  line-height: 1.4;
}

:deep(.timeline-info) {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem;
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-primary-200);
  border-radius: var(--gp-radius-small);
  color: var(--gp-text-primary);
  font-size: 0.9rem;
  margin-bottom: 1rem;
}

:deep(.timeline-info i) {
  color: var(--gp-primary);
  font-size: 1rem;
}

.p-dark :deep(.timeline-info) {
  background: var(--gp-surface-dark) !important;
  border: 1px solid var(--gp-primary-300) !important;
}

:deep(.history-header) {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

:deep(.history-title) {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0;
}

:deep(.empty-state) {
  text-align: center;
  padding: 3rem 1rem;
  color: var(--gp-text-secondary);
}

:deep(.empty-icon) {
  font-size: 3rem;
  margin-bottom: 1rem;
  opacity: 0.5;
}

:deep(.empty-text) {
  font-size: 1.1rem;
  margin: 0;
}

:deep(.option-group) {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

:deep(.checkbox-option) {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

:deep(.checkbox-option .option-label) {
  font-weight: 500;
  color: var(--gp-text-primary);
  cursor: pointer;
  margin: 0;
}

:deep(.option-group > .option-label) {
  font-weight: 500;
  color: var(--gp-text-primary);
  display: block;
  margin-bottom: 0.5rem;
}

:deep(.option-description) {
  color: var(--gp-text-secondary);
  margin-left: 2rem;
  font-size: 0.9rem;
  line-height: 1.4;
}

/* Responsive Design */
@media (max-width: 768px) {
  .data-export-import-page {
    padding: 0 1rem;
  }

  .page-title {
    font-size: 1.5rem;
  }

  .header-content {
    flex-direction: column;
    align-items: stretch;
    gap: 1.5rem;
  }

  :deep(.data-types-grid) {
    grid-template-columns: 1fr;
  }

  :deep(.date-range-controls) {
    grid-template-columns: 1fr;
  }

  :deep(.date-range-presets) {
    justify-content: center;
  }

  :deep(.job-detail) {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }

  :deep(.detail-label) {
    min-width: auto;
    font-size: 0.9rem;
  }

  :deep(.job-progress) {
    width: 100%;
  }

  .banner-content {
    flex-direction: column;
    text-align: center;
    gap: 1rem;
  }
}

@media (max-width: 480px) {
  .data-export-import-page {
    padding: 0 0.75rem;
  }

  .page-title {
    font-size: 1.3rem;
  }

  :deep(.data-type-option) {
    padding: 0.75rem;
  }

  :deep(.form-actions) {
    gap: 0.75rem;
  }

  :deep(.export-button),
  :deep(.import-button) {
    min-width: auto;
    width: 100%;
  }

  :deep(.job-actions) {
    flex-direction: column;
    gap: 0.5rem;
  }

  :deep(.job-actions .p-button) {
    width: 100%;
  }

  :deep(.option-description) {
    margin-left: 0;
    margin-top: 0.25rem;
  }

  :deep(.format-options) {
    grid-template-columns: 1fr;
  }

  :deep(.form-section-header),
  :deep(.option-group-header) {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }

  :deep(.select-all-button) {
    align-self: flex-end;
  }
}
</style>
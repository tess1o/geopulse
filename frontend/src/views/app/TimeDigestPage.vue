<template>
  <AppLayout variant="default">
    <PageContainer title="Rewind" subtitle="Your location story, one period at a time." max-width="large" :loading="isLoading">
      <template #actions>
        <Button label="Export PDF" icon="pi pi-file-pdf" outlined :disabled="!currentDigest" @click="exportDialogVisible = true" />
      </template>

      <DigestHeader v-model:viewMode="viewMode" v-model:year="selectedYear" v-model:month="selectedMonth" @period-changed="handlePeriodChange" />

      <div v-if="isLoading" class="digest-loading"><ProgressSpinner size="large" /><p>Building your rewind…</p></div>

      <div v-else-if="hasError" class="digest-error">
        <BaseCard class="error-card"><i class="pi pi-exclamation-triangle error-icon"></i><h3>Unable to load Rewind</h3><p>{{ errorMessage }}</p><BaseButton label="Try again" icon="pi pi-refresh" variant="gp-primary" @click="loadDigest" /></BaseCard>
      </div>

      <div v-else-if="currentDigest" class="digest-content">
        <DigestMetrics :title="currentDigest.period?.displayName || displayPeriod" :metrics="currentDigest.metrics" :comparison="currentDigest.comparison" :highlights="currentDigest.highlights" />
        <DigestMemories :view-mode="viewMode" :year="selectedYear" :month="selectedMonth" @availability="immichAvailable = $event" />
        <DigestHighlights :highlights="currentDigest.highlights" />
        <div class="digest-feature-grid">
          <DigestTrends :chart-data="currentDigest.activityChart" :view-mode="viewMode" />
          <DigestPlaces :places="currentDigest.topPlaces" :limit="5" />
        </div>
        <DigestMilestones :milestones="currentDigest.milestones" />
        <DigestHeatmap :view-mode="viewMode" :year="selectedYear" :month="selectedMonth" />
      </div>

      <div v-else class="digest-empty"><BaseCard class="empty-card"><i class="pi pi-compass empty-icon"></i><h3>No movement for {{ displayPeriod }}</h3><p>Choose another period or start tracking to create your first rewind.</p></BaseCard></div>
    </PageContainer>

    <Dialog v-model:visible="exportDialogVisible" modal header="Export Rewind" :style="{ width: 'min(92vw, 28rem)' }">
      <p class="export-description">Download a polished report for <b>{{ displayPeriod }}</b>.</p>
      <label class="export-photos-option" :class="{ disabled: !immichAvailable }">
        <Checkbox v-model="includePhotos" binary input-id="rewind-include-photos" :disabled="!immichAvailable" />
        <span><b>Include Immich photos</b><small>{{ immichAvailable ? 'Add up to six recent memories from this period.' : 'Connect Immich to include photos.' }}</small></span>
      </label>
      <template #footer>
        <Button label="Cancel" text @click="exportDialogVisible = false" />
        <Button label="Download PDF" icon="pi pi-download" :loading="exporting" @click="downloadPdf" />
      </template>
    </Dialog>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import Checkbox from 'primevue/checkbox'
import Dialog from 'primevue/dialog'
import ProgressSpinner from 'primevue/progressspinner'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import BaseButton from '@/components/ui/base/BaseButton.vue'
import DigestHeader from '@/components/digest/DigestHeader.vue'
import DigestMetrics from '@/components/digest/DigestMetrics.vue'
import DigestMemories from '@/components/digest/DigestMemories.vue'
import DigestHighlights from '@/components/digest/DigestHighlights.vue'
import DigestPlaces from '@/components/digest/DigestPlaces.vue'
import DigestTrends from '@/components/digest/DigestTrends.vue'
import DigestMilestones from '@/components/digest/DigestMilestones.vue'
import DigestHeatmap from '@/components/digest/DigestHeatmap.vue'
import { useDigestStore } from '@/stores/digest'
import { useTimezone } from '@/composables/useTimezone'
import { useErrorHandler } from '@/composables/useErrorHandler'
import apiService from '@/utils/apiService'

const timezone = useTimezone()
const digestStore = useDigestStore()
const { currentDigest, loading: isLoading, error: errorMessage } = storeToRefs(digestStore)
const { handleError } = useErrorHandler()
const toast = useToast()
const route = useRoute()
const router = useRouter()
const viewMode = ref('monthly')
const selectedYear = ref(timezone.now().year())
const selectedMonth = ref(timezone.now().month() + 1)
const exportDialogVisible = ref(false)
const exporting = ref(false)
const includePhotos = ref(false)
const immichAvailable = ref(false)

const hasError = computed(() => digestStore.hasError)
const displayPeriod = computed(() => currentDigest.value?.period?.displayName || (viewMode.value === 'monthly' ? timezone.create(`${selectedYear.value}-${String(selectedMonth.value).padStart(2, '0')}-01`).format('MMMM YYYY') : String(selectedYear.value)))

const loadDigest = async () => {
  try {
    if (viewMode.value === 'monthly') await digestStore.fetchMonthlyDigest(selectedYear.value, selectedMonth.value)
    else await digestStore.fetchYearlyDigest(selectedYear.value)
  } catch (error) {
    console.error('Error loading digest:', error)
    handleError(error)
  }
}
const updateURL = () => {
  const query = { viewMode: viewMode.value, year: selectedYear.value }
  if (viewMode.value === 'monthly') query.month = selectedMonth.value
  router.push({ query })
}
const handlePeriodChange = async (period) => {
  viewMode.value = period.viewMode
  selectedYear.value = period.year
  selectedMonth.value = period.month || selectedMonth.value
  immichAvailable.value = false
  includePhotos.value = false
  await loadDigest()
  updateURL()
}
const downloadPdf = async () => {
  exporting.value = true
  try {
    const params = { viewMode: viewMode.value, year: selectedYear.value, includePhotos: includePhotos.value }
    if (viewMode.value === 'monthly') params.month = selectedMonth.value
    await apiService.download('/digest/pdf', params)
    exportDialogVisible.value = false
    toast.add({ severity: 'success', summary: 'Rewind exported', detail: 'Your PDF is downloading.', life: 3500 })
  } catch (error) {
    toast.add({ severity: 'error', summary: 'Export failed', detail: error.userMessage || 'Could not generate the PDF.', life: 5000 })
  } finally {
    exporting.value = false
  }
}

watch(immichAvailable, (available) => { includePhotos.value = available })
onMounted(async () => {
  const { viewMode: mode, year, month } = route.query
  if (mode === 'monthly' || mode === 'yearly') viewMode.value = mode
  if (year) selectedYear.value = Number.parseInt(year, 10)
  if (month) selectedMonth.value = Number.parseInt(month, 10)
  await loadDigest()
  updateURL()
})
</script>

<style scoped>
.digest-content { width:100%; margin:0 auto }.digest-feature-grid { display:grid; grid-template-columns:minmax(0,1.45fr) minmax(18rem,.55fr); gap:var(--gp-spacing-xl); align-items:stretch }.digest-loading,.digest-error,.digest-empty { display:grid; place-items:center; min-height:22rem; text-align:center }.digest-loading { gap:var(--gp-spacing-lg); color:var(--gp-text-secondary) }.error-card,.empty-card { max-width:34rem; padding:var(--gp-spacing-xxl) }.error-icon,.empty-icon { font-size:3.25rem; color:var(--gp-primary); margin-bottom:var(--gp-spacing-md) }.error-icon { color:var(--gp-error) }.export-description { margin:0 0 1.25rem; color:var(--gp-text-secondary) }.export-photos-option { display:flex; gap:.75rem; align-items:flex-start; padding:1rem; border:1px solid var(--gp-border-light); border-radius:12px; cursor:pointer }.export-photos-option span { display:grid; gap:.2rem; color:var(--gp-text-primary) }.export-photos-option small { color:var(--gp-text-secondary) }.export-photos-option.disabled { cursor:not-allowed; opacity:.62; background:var(--gp-surface-light) }@media (max-width:960px) { .digest-feature-grid { grid-template-columns:1fr; gap:var(--gp-spacing-lg) } }@media (max-width:640px) { .error-card,.empty-card { padding:var(--gp-spacing-xl) } }
</style>

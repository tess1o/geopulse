<template>
  <Dialog :visible="visible" :header="t('favoritesGeocodingDialogs.reconcile.geocoding.dialogTitleSingle')"
    modal class="gp-dialog-md" :closable="!saving" :close-on-escape="!saving"
    :dismissable-mask="false" @update:visible="close">
    <div class="candidate-content">
      <p>{{ t('geocoding.candidates.current') }}: <strong>{{ result.displayName }}</strong></p>
      <Message severity="info" :closable="false">{{ t('geocoding.candidates.linkedStays') }}</Message>
      <label for="candidate-provider">{{ t('favoritesGeocodingDialogs.reconcile.selectProvider') }}</label>
      <Select id="candidate-provider" v-model="provider" :options="enabledProviders.filter(p => p.enabled)"
        option-label="displayName" option-value="name" :disabled="saving"
        :placeholder="t('favoritesGeocodingDialogs.reconcile.providerPlaceholder')" />
      <Button :label="t('geocoding.candidates.load')" :disabled="!provider || saving" :loading="loading" @click="load" />
      <Message v-if="error" severity="error" :closable="false">{{ error }}</Message>
      <p v-if="preview && !preview.candidates.length" role="status">{{ t('geocoding.candidates.empty') }}</p>
      <p v-if="preview?.candidates.length === 1" role="status">{{ t('geocoding.candidates.single') }}</p>
      <fieldset v-if="preview?.candidates.length" :disabled="saving">
        <legend>{{ t('geocoding.candidates.choose') }}</legend>
        <label v-for="candidate in preview.candidates" :key="candidate.id" class="candidate-option">
          <input v-model="selected" type="radio" name="geocoding-candidate" :value="candidate.id" />
          <span>
            <strong>{{ candidate.displayName }}</strong>
            <small v-if="candidate.category">{{ candidate.category }}</small>
            <small v-if="candidate.address && candidate.address !== candidate.displayName">{{ candidate.address }}</small>
            <small>{{ [candidate.city, candidate.country].filter(Boolean).join(', ') }}</small>
            <small v-if="candidate.distanceMeters != null">{{ t('geocoding.candidates.distance', { distance: Math.round(candidate.distanceMeters) }) }}</small>
          </span>
        </label>
      </fieldset>
    </div>
    <template #footer>
      <Button :label="t('common.cancel')" text :disabled="saving" @click="close" />
      <Button :label="t('geocoding.candidates.save')" :disabled="selected === null || !preview || loading" :loading="saving" @click="save" />
    </template>
  </Dialog>
</template>

<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import Dialog from 'primevue/dialog'
import Select from 'primevue/select'
import Button from 'primevue/button'
import Message from 'primevue/message'
import { useGeocodingStore } from '@/stores/geocoding'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

const props = defineProps({ visible: Boolean, result: { type: Object, required: true }, enabledProviders: { type: Array, default: () => [] } })
const emit = defineEmits(['close', 'saved'])
const { t } = useI18n()
const store = useGeocodingStore()
const provider = ref(null)
const preview = ref(null)
const selected = ref(null)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
let requestId = 0

const reset = () => {
  requestId++
  preview.value = null
  selected.value = null
  error.value = ''
  loading.value = false
}
watch(provider, reset)
watch(() => [props.visible, props.result.id], () => {
  reset()
  provider.value = null
})
onBeforeUnmount(() => requestId++)

const close = () => {
  if (saving.value) return
  reset()
  emit('close')
}
const load = async () => {
  if (!provider.value || saving.value) return
  reset()
  const id = requestId
  loading.value = true
  try {
    const response = await store.fetchReconciliationCandidates(props.result.id, provider.value)
    if (id === requestId) {
      preview.value = response
      selected.value = response.candidates[0]?.id ?? null
    }
  } catch (e) {
    if (id === requestId) error.value = formatApiErrorDetail(e, t('geocoding.candidates.loadFailed'))
  } finally {
    if (id === requestId) loading.value = false
  }
}
const save = async () => {
  if (!preview.value || selected.value === null || saving.value) return
  saving.value = true
  error.value = ''
  try {
    const result = await store.selectReconciliationCandidate(props.result.id, preview.value.previewId, selected.value)
    emit('saved', result)
  } catch (e) {
    reset()
    error.value = formatApiErrorDetail(e, t('geocoding.candidates.saveFailed'))
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.candidate-content { display: flex; flex-direction: column; gap: 1rem; }
fieldset { border: 0; padding: 0; margin: 0; }
.candidate-option { display: flex; gap: 0.75rem; padding: 0.75rem 0; cursor: pointer; }
.candidate-option input { align-self: flex-start; margin-top: 0.3rem; }
.candidate-option span { display: flex; flex-direction: column; gap: 0.25rem; }
</style>

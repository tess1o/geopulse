<template>
  <EditFavoriteDialog
    v-if="selectedFavorite"
    :visible="favoriteVisible"
    :header="'Edit Favorite Location'"
    :favorite-location="selectedFavorite"
    @edit-favorite="$emit('save-favorite', $event)"
    @close="$emit('close-favorite')"
  />

  <GeocodingEditDialog
    :visible="geocodingVisible"
    :geocoding-result="editGeocodingData"
    @save="$emit('save-geocoding', $event)"
    @close="$emit('close-geocoding')"
  />

  <TimelineRegenerationModal
    :visible="regenerationVisible"
    :type="regenerationType"
    :job-id="currentJobId"
    :job-progress="jobProgress"
    @update:visible="$emit('update:regenerationVisible', $event)"
  />
</template>

<script setup>
import EditFavoriteDialog from '@/components/dialogs/EditFavoriteDialog.vue'
import GeocodingEditDialog from '@/components/dialogs/GeocodingEditDialog.vue'
import TimelineRegenerationModal from '@/components/dialogs/TimelineRegenerationModal.vue'

defineProps({
  favoriteVisible: {
    type: Boolean,
    default: false
  },
  selectedFavorite: {
    type: Object,
    default: null
  },
  geocodingVisible: {
    type: Boolean,
    default: false
  },
  editGeocodingData: {
    type: Object,
    default: null
  },
  regenerationVisible: {
    type: Boolean,
    default: false
  },
  regenerationType: {
    type: String,
    default: 'general'
  },
  currentJobId: {
    type: String,
    default: null
  },
  jobProgress: {
    type: Object,
    default: null
  }
})

defineEmits([
  'save-favorite',
  'close-favorite',
  'save-geocoding',
  'close-geocoding',
  'update:regenerationVisible'
])
</script>

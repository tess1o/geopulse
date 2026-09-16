<template>
  <BaseCard :title="`Visited cities in ${countryName}`" class="country-cities-map-card">
    <div class="country-cities-map-container">
      <MapContainer
        ref="mapContainerRef"
        :map-id="mapId"
        :center="mapCenter"
        :zoom="4"
        :map-options="mapOptions"
        :show-controls="false"
        @map-ready="handleMapReady"
      >
        <template #overlays="{ map, isReady }">
          <LocationAnalyticsDotsLayer
            v-if="map && isReady"
            :map="map"
            :places="mapCities"
            :visible="true"
            @open-place-details="emit('city-select', $event)"
          />
        </template>
      </MapContainer>
    </div>
  </BaseCard>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'

import BaseCard from '@/components/ui/base/BaseCard.vue'
import { LocationAnalyticsDotsLayer, MapContainer } from '@/components/maps'

const props = defineProps({
  cities: {
    type: Array,
    default: () => []
  },
  countryName: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['city-select'])
const mapContainerRef = ref(null)
const mapId = `country-cities-map-${Math.random().toString(36).slice(2, 11)}`
const mapOptions = { tap: false }

const mapCities = computed(() => props.cities
  .filter((city) => Number.isFinite(city?.latitude) && Number.isFinite(city?.longitude))
  .map((city) => ({
    ...city,
    id: city.cityName,
    type: 'city',
    locationName: city.cityName,
    city: city.cityName,
    country: props.countryName
  })))

const mapCenter = computed(() => {
  if (mapCities.value.length === 0) return [20, 0]

  const total = mapCities.value.reduce((center, city) => ({
    latitude: center.latitude + city.latitude,
    longitude: center.longitude + city.longitude
  }), { latitude: 0, longitude: 0 })

  return [total.latitude / mapCities.value.length, total.longitude / mapCities.value.length]
})

const fitCities = async () => {
  if (mapCities.value.length === 0) return

  await nextTick()
  if (mapCities.value.length === 1) {
    mapContainerRef.value?.setView?.([mapCities.value[0].latitude, mapCities.value[0].longitude], 10)
    return
  }

  mapContainerRef.value?.fitBounds?.(
    mapCities.value.map((city) => [city.latitude, city.longitude]),
    { padding: [40, 40], maxZoom: 8 }
  )
}

const handleMapReady = () => fitCities()

watch(mapCities, fitCities)
</script>

<style scoped>
.country-cities-map-card {
  margin-bottom: var(--gp-spacing-xl);
}

.country-cities-map-container {
  width: 100%;
  height: 400px;
  border-radius: var(--gp-radius-medium);
  overflow: hidden;
}

@media (max-width: 768px) {
  .country-cities-map-container {
    height: 300px;
  }
}
</style>

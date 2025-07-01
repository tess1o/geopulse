<template>
  <AppLayout variant="app" padding="none">
    <template #navbar>
      <AppNavbarWithDatePicker @date-change="handleDateChange" @navigate="handleNavigate" />
    </template>
    
    <TabContainer
      :tabs="tabItems"
      :activeIndex="activeIndex"
      @tab-change="onTabChange"
    >
      <router-view :key="route.path" />
    </TabContainer>

  </AppLayout>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'

// New Layout Components
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import AppNavbarWithDatePicker from '@/components/ui/layout/AppNavbarWithDatePicker.vue'
import TabContainer from '@/components/ui/layout/TabContainer.vue'

// Utils and Stores
import {
  formatDateMMDDYYYY,
  getTodayRange,
  isValidDataRange,
  isValidDate,
  setToEndOfDay
} from "@/utils/dateHelpers"
import { useDateRangeStore } from '@/stores/dateRange'

// Composables
const route = useRoute()
const router = useRouter()

// Pinia store
const dateRangeStore = useDateRangeStore()
const { dateRange: dates } = storeToRefs(dateRangeStore)

// Reactive state
const activeIndex = ref(0)
const tabItems = ref([
  {
    label: 'Timeline',
    icon: 'pi pi-calendar',
    to: '/app/timeline',
    class: 'timeline-tab',
    'data-tour': 'timeline-tab'
  },
  {
    label: 'Dashboard',
    icon: 'pi pi-home',
    to: '/app/dashboard',
    class: 'dashboard-tab'
  },
/*  {
    label: 'Places',
    icon: 'pi pi-map-marker',
    to: '/app/places',
    disabled: true,
    class: 'places-tab'
  },
  {
    label: 'Stats',
    icon: 'pi pi-chart-line',
    to: '/app/stats',
    disabled: true,
    class: 'stats-tab'
  },*/
])

// Methods
const onTabChange = (e) => {
  const selectedRoute = tabItems.value[e.index].to
  router.push({
    path: selectedRoute,
    query: {
      ...route.query,
    }
  })
}

const handleDateChange = (dateRange) => {
  console.log('Date changed:', dateRange)
  // Additional logic if needed
}

const handleNavigate = (item) => {
  console.log('Navigation:', item)
  // Additional logic if needed
}

const initializeDateRangeFromQuery = () => {
  const todayRange = getTodayRange()

  const startFromQuery = new Date(route.query.start)
  const endFromQuery = new Date(route.query.end)

  const startDate = isValidDate(startFromQuery) ? startFromQuery : todayRange.start
  const endDate = setToEndOfDay(isValidDate(endFromQuery) ? endFromQuery : todayRange.end)

  dateRangeStore.setDateRange([startDate, endDate])
}

// Initialize date range on mount
onMounted(() => {
  initializeDateRangeFromQuery()
})

// Watchers
// Watch for route changes to update active tab
watch(() => route.path, (path) => {
  const index = tabItems.value.findIndex(tab => tab.to === path)
  activeIndex.value = index !== -1 ? index : 0
}, { immediate: true })

// Watch date range changes to update URL
watch(dates, (newValue) => {
  if (newValue && isValidDataRange(newValue)) {
    const [newStartDate, newEndDate] = newValue
    const startDate = newStartDate
    const endDate = setToEndOfDay(newEndDate)

    // Only update URL if the dates are different from current query params
    const currentStart = route.query.start
    const currentEnd = route.query.end
    const newStart = formatDateMMDDYYYY(startDate)
    const newEnd = formatDateMMDDYYYY(endDate)

    if (currentStart !== newStart || currentEnd !== newEnd) {
      router.replace({
        query: {
          ...route.query,
          start: newStart,
          end: newEnd,
        },
      })
    }
  }
})
</script>

<style scoped>
/* Any specific styling for MainAppPageNew if needed */
</style>
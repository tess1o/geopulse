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
import { useTimezone } from '@/composables/useTimezone'

// New Layout Components
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import AppNavbarWithDatePicker from '@/components/ui/layout/AppNavbarWithDatePicker.vue'
import TabContainer from '@/components/ui/layout/TabContainer.vue'

// Stores
import { useDateRangeStore } from '@/stores/dateRange'

// Composables
const route = useRoute()
const router = useRouter()
const timezone = useTimezone()

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
  // Additional logic if needed
}

const handleNavigate = (item) => {
  // Additional logic if needed
}

const initializeDateRangeFromQuery = () => {
  const todayRange = timezone.getTodayRangeUtc();
  const startFromQuery = timezone.parseUrlDate(route.query.start, false);
  const endFromQuery = timezone.parseUrlDate(route.query.end, true);

  const startDate = timezone.isValidDate(startFromQuery) ? startFromQuery : todayRange.start;
  const endDate = timezone.isValidDate(endFromQuery) ? endFromQuery : todayRange.end;

  dateRangeStore.setDateRange([startDate, endDate]);
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
  if (newValue && timezone.isValidDateRange(newValue)) {
    const [newStartDate, newEndDate] = newValue;

    const newStart = timezone.formatDateUS(newStartDate);
    const newEnd = timezone.formatDateUS(newEndDate);

    if (route.query.start !== newStart || route.query.end !== newEnd) {
      router.replace({
        query: {
          ...route.query,
          start: newStart,
          end: newEnd,
        },
      });
    }
  }
}, { deep: true });
</script>

<style scoped>
/* Any specific styling for MainAppPageNew if needed */
</style>
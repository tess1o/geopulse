<template>
  <AppLayout variant="app" padding="none">
    <template #navbar>
      <AppNavbarWithDatePicker @date-change="handleDateChange" @navigate="handleNavigate">
        <template #end-before>
          <!-- Share button - only show on Timeline page -->
          <Button
            v-if="isTimelinePage"
            label="Share"
            icon="pi pi-share-alt"
            @click="showShareDialog"
            outlined
            class="share-btn"
          />
        </template>
      </AppNavbarWithDatePicker>
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
import { ref, watch, onMounted, computed, provide } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useTimezone } from '@/composables/useTimezone'

// New Layout Components
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import AppNavbarWithDatePicker from '@/components/ui/layout/AppNavbarWithDatePicker.vue'
import TabContainer from '@/components/ui/layout/TabContainer.vue'
import Button from 'primevue/button'

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
const shareDialogVisible = ref(false)

const tabItems = ref([
  {
    label: 'Timeline',
    icon: 'pi pi-calendar',
    to: '/app/timeline',
    class: 'timeline-tab',
    'data-tour': 'timeline-tab'
  },
  {
    label: 'Timeline Reports',
    icon: 'pi pi-table',
    to: '/app/timeline-reports',
    class: 'data-tables-tab'
  },
  {
    label: 'Dashboard',
    icon: 'pi pi-home',
    to: '/app/dashboard',
    class: 'dashboard-tab'
  },
])

// Computed
const isTimelinePage = computed(() => route.path === '/app/timeline')

// Provide share dialog state for child components
provide('shareDialogVisible', shareDialogVisible)

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

const showShareDialog = () => {
  // Open share dialog via provided state
  shareDialogVisible.value = true
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
/* Share button styling */
.share-btn {
  flex-shrink: 0;
}

/* Responsive - hide label on mobile */
@media (max-width: 768px) {
  .share-btn :deep(.p-button-label) {
    display: none;
  }

  .share-btn {
    padding: 0.5rem;
  }
}
</style>
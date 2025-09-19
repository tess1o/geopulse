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
import { getUserTimezone } from "@/utils/timezoneUtils"
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
  // Additional logic if needed
}

const handleNavigate = (item) => {
  // Additional logic if needed
}

/**
 * Parse a date string (MM/DD/YYYY) to represent that calendar date in the user's timezone.
 * Creates a Date object that when converted to ISO string will represent the correct day
 * for API calls in the user's preferred timezone.
 */
const parseDateInUserTimezone = (dateString, isEndDate = false) => {
  console.log(`🔍 parseDateInUserTimezone: input="${dateString}", isEndDate=${isEndDate}`)
  
  if (!dateString) return null
  
  try {
    const userTimezone = getUserTimezone()
    console.log(`🌍 User timezone: ${userTimezone}`)
    
    // Parse MM/DD/YYYY format
    const parts = dateString.split('/')
    if (parts.length !== 3) return null
    
    const month = parseInt(parts[0], 10)
    const day = parseInt(parts[1], 10)
    const year = parseInt(parts[2], 10)
    
    if (isNaN(month) || isNaN(day) || isNaN(year)) return null
    
    // Format as ISO date string for the target calendar date
    const dateStr = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
    console.log(`📅 Parsed date components: ${dateStr}`)
    
    let result
    
    if (userTimezone === 'UTC') {
      // For UTC, create the date directly
      const timeStr = isEndDate ? 'T23:59:59.999Z' : 'T00:00:00.000Z'
      result = new Date(`${dateStr}${timeStr}`)
      console.log(`🕐 UTC result: ${result.toISOString()}`)
    } else {
      // For other timezones, we need to calculate the offset
      // Create a temporary date to get the timezone offset for this specific date
      const tempDate = new Date(`${dateStr}T12:00:00.000Z`) // Use noon to avoid DST issues
      const offsetMs = getTimezoneOffsetMs(userTimezone, tempDate)
      console.log(`⏰ Timezone offset: ${offsetMs}ms (${offsetMs / (1000 * 60 * 60)} hours)`)
      
      // Create the date representing the time in the user's timezone
      const baseTime = isEndDate ? 'T23:59:59.999Z' : 'T00:00:00.000Z'
      const utcTime = new Date(`${dateStr}${baseTime}`)
      const localTime = new Date(utcTime.getTime() - offsetMs)
      
      result = localTime
      console.log(`🕐 Non-UTC result: ${result.toISOString()}`)
    }
    
    console.log(`✅ Final result for "${dateString}" (${isEndDate ? 'END' : 'START'}): ${result.toISOString()}`)
    return result
  } catch (error) {
    console.warn('Failed to parse date in user timezone:', dateString, error)
    return null
  }
}

/**
 * Get timezone offset in milliseconds for a specific date and timezone
 */
const getTimezoneOffsetMs = (timezone, date) => {
  const utcTime = new Date(date.toLocaleString('en-US', { timeZone: 'UTC' }))
  const localTime = new Date(date.toLocaleString('en-US', { timeZone: timezone }))
  return localTime.getTime() - utcTime.getTime()
}

/**
 * Format a date as MM/DD/YYYY in the user's timezone
 * This fixes the issue where formatDateMMDDYYYY uses browser's timezone
 */
const formatDateInUserTimezone = (date) => {
  console.log(`🎨 formatDateInUserTimezone: input=${date.toISOString()}`)
  
  const userTimezone = getUserTimezone()
  console.log(`🌍 Formatting in timezone: ${userTimezone}`)
  
  // Use Intl.DateTimeFormat to get the date components in the user's timezone
  const formatter = new Intl.DateTimeFormat('en-US', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    timeZone: userTimezone
  })
  
  const parts = formatter.formatToParts(date)
  const month = parts.find(part => part.type === 'month').value
  const day = parts.find(part => part.type === 'day').value
  const year = parts.find(part => part.type === 'year').value
  
  const result = `${month}/${day}/${year}`
  console.log(`🎨 Formatted result: ${result}`)
  
  return result
}

const initializeDateRangeFromQuery = () => {
  console.log(`🚀 initializeDateRangeFromQuery called`)
  console.log(`🔗 Current URL query:`, route.query)
  
  const todayRange = getTodayRange()
  console.log(`📅 Today range fallback:`, {
    start: todayRange.start.toISOString(),
    end: todayRange.end.toISOString()
  })

  // Parse URL date parameters in the context of user's timezone
  const startFromQuery = parseDateInUserTimezone(route.query.start, false) // Start of day
  const endFromQuery = parseDateInUserTimezone(route.query.end, true)      // End of day

  console.log(`🎯 Parsed from query:`, {
    startFromQuery: startFromQuery?.toISOString(),
    endFromQuery: endFromQuery?.toISOString()
  })

  const startDate = isValidDate(startFromQuery) ? startFromQuery : todayRange.start
  const endDate = isValidDate(endFromQuery) ? endFromQuery : todayRange.end
  
  console.log(`📝 Final dates being set to store:`, {
    startDate: startDate.toISOString(),
    endDate: endDate.toISOString()
  })

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
  console.log(`👁️ Date range watcher triggered:`, newValue?.map(d => d.toISOString()))
  
  if (newValue && isValidDataRange(newValue)) {
    const [newStartDate, newEndDate] = newValue
    const startDate = newStartDate
    const endDate = newEndDate // Don't apply setToEndOfDay again - it's already handled in parsing

    console.log(`📊 Processing dates:`, {
      startDate: startDate.toISOString(),
      endDate: endDate.toISOString()
    })

    // Only update URL if the dates are different from current query params
    const currentStart = route.query.start
    const currentEnd = route.query.end
    const newStart = formatDateInUserTimezone(startDate)
    const newEnd = formatDateInUserTimezone(endDate)

    console.log(`🔄 URL comparison:`, {
      current: { start: currentStart, end: currentEnd },
      new: { start: newStart, end: newEnd },
      different: currentStart !== newStart || currentEnd !== newEnd
    })

    if (currentStart !== newStart || currentEnd !== newEnd) {
      console.log(`🌐 Updating URL to:`, { start: newStart, end: newEnd })
      router.replace({
        query: {
          ...route.query,
          start: newStart,
          end: newEnd,
        },
      })
    } else {
      console.log(`✅ URL already matches, no update needed`)
    }
  }
})
</script>

<style scoped>
/* Any specific styling for MainAppPageNew if needed */
</style>
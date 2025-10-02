<template>
  <div class="digest-header">
    <div class="digest-header-main">
      <!-- Period Type Toggle -->
      <div class="period-toggle">
        <Button
          :label="viewMode === 'monthly' ? 'Monthly' : 'Yearly'"
          :icon="viewMode === 'monthly' ? 'pi pi-calendar' : 'pi pi-calendar-clock'"
          @click="toggleViewMode"
          outlined
          class="toggle-btn"
        />
      </div>

      <!-- Period Navigation -->
      <div class="period-navigation">
        <Button
          icon="pi pi-chevron-left"
          @click="previousPeriod"
          outlined
          size="small"
          class="nav-btn"
        />

        <div class="period-display">
          <i :class="viewMode === 'monthly' ? 'pi pi-calendar' : 'pi pi-calendar-clock'"></i>
          <span>{{ displayPeriod }}</span>
        </div>

        <Button
          icon="pi pi-chevron-right"
          @click="nextPeriod"
          :disabled="isCurrentPeriod"
          outlined
          size="small"
          class="nav-btn"
        />
      </div>

      <!-- Quick Year Jump (Mobile Hidden) -->
      <div class="quick-years" v-if="viewMode === 'monthly'">
        <Button
          v-for="year in recentYears"
          :key="year"
          :label="String(year)"
          @click="jumpToYear(year)"
          :outlined="year !== selectedYear"
          size="small"
          class="year-btn"
        />
      </div>
    </div>

    <!-- Mobile Period Selector -->
    <div class="mobile-period-selector">
      <select v-model="selectedYear" @change="onYearChange" class="period-select">
        <option v-for="year in availableYears" :key="year" :value="year">
          {{ year }}
        </option>
      </select>

      <select
        v-if="viewMode === 'monthly'"
        v-model="selectedMonth"
        @change="onMonthChange"
        class="period-select"
      >
        <option v-for="(monthName, index) in monthNames" :key="index" :value="index + 1">
          {{ monthName }}
        </option>
      </select>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import Button from 'primevue/button'
import { useTimezone } from '@/composables/useTimezone'

const timezone = useTimezone()

const props = defineProps({
  viewMode: {
    type: String,
    default: 'monthly',
    validator: (value) => ['monthly', 'yearly'].includes(value)
  },
  year: {
    type: Number,
    required: true
  },
  month: {
    type: Number,
    default: null
  }
})

const emit = defineEmits(['update:viewMode', 'update:year', 'update:month', 'period-changed'])

const selectedYear = ref(props.year)
const selectedMonth = ref(props.month || timezone.now().month() + 1)

const monthNames = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'
]

const availableYears = computed(() => {
  const currentYear = timezone.now().year()
  const years = []
  for (let year = 2020; year <= currentYear; year++) {
    years.push(year)
  }
  return years.reverse()
})

const recentYears = computed(() => {
  const currentYear = timezone.now().year()
  return [currentYear, currentYear - 1, currentYear - 2].filter(year => year >= 2020)
})

const displayPeriod = computed(() => {
  if (props.viewMode === 'monthly') {
    const monthName = monthNames[selectedMonth.value - 1]
    return `${monthName} ${selectedYear.value}`
  } else {
    return `${selectedYear.value}`
  }
})

const isCurrentPeriod = computed(() => {
  const now = timezone.now()
  if (props.viewMode === 'monthly') {
    return selectedYear.value === now.year() && selectedMonth.value === (now.month() + 1)
  } else {
    return selectedYear.value === now.year()
  }
})

const toggleViewMode = () => {
  const newMode = props.viewMode === 'monthly' ? 'yearly' : 'monthly'
  emit('update:viewMode', newMode)
  emit('period-changed', {
    viewMode: newMode,
    year: selectedYear.value,
    month: newMode === 'monthly' ? selectedMonth.value : null
  })
}

const previousPeriod = () => {
  if (props.viewMode === 'monthly') {
    if (selectedMonth.value === 1) {
      selectedMonth.value = 12
      selectedYear.value--
    } else {
      selectedMonth.value--
    }
  } else {
    selectedYear.value--
  }
  emitChange()
}

const nextPeriod = () => {
  if (!isCurrentPeriod.value) {
    if (props.viewMode === 'monthly') {
      if (selectedMonth.value === 12) {
        selectedMonth.value = 1
        selectedYear.value++
      } else {
        selectedMonth.value++
      }
    } else {
      selectedYear.value++
    }
    emitChange()
  }
}

const jumpToYear = (year) => {
  selectedYear.value = year
  emitChange()
}

const onYearChange = () => {
  emitChange()
}

const onMonthChange = () => {
  emitChange()
}

const emitChange = () => {
  emit('update:year', selectedYear.value)
  emit('update:month', selectedMonth.value)
  emit('period-changed', {
    viewMode: props.viewMode,
    year: selectedYear.value,
    month: props.viewMode === 'monthly' ? selectedMonth.value : null
  })
}

watch(() => props.year, (newYear) => {
  selectedYear.value = newYear
})

watch(() => props.month, (newMonth) => {
  if (newMonth) {
    selectedMonth.value = newMonth
  }
})
</script>

<style scoped>
.digest-header {
  background: var(--gp-surface-white);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-large);
  padding: var(--gp-spacing-lg);
  margin-bottom: var(--gp-spacing-xl);
}

.digest-header-main {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-lg);
  flex-wrap: wrap;
}

.period-toggle {
  flex-shrink: 0;
}

.toggle-btn {
  font-weight: 600;
}

.period-navigation {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-md);
  flex: 1;
  justify-content: center;
}

.nav-btn {
  width: 36px;
  height: 36px;
}

.period-display {
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-sm);
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--gp-text-primary);
  min-width: 180px;
  justify-content: center;
}

.period-display i {
  color: var(--gp-primary);
}

.quick-years {
  display: flex;
  gap: var(--gp-spacing-sm);
  flex-shrink: 0;
}

.year-btn {
  font-weight: 600;
  min-width: 60px;
}

/* Mobile Period Selector */
.mobile-period-selector {
  display: none;
  gap: var(--gp-spacing-sm);
  margin-top: var(--gp-spacing-md);
}

.period-select {
  flex: 1;
  padding: var(--gp-spacing-sm) var(--gp-spacing-md);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  background: var(--gp-surface-white);
  color: var(--gp-text-primary);
  font-size: 1rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.period-select:hover {
  border-color: var(--gp-primary);
}

.period-select:focus {
  outline: none;
  border-color: var(--gp-primary);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

/* Dark Mode */
.p-dark .digest-header {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark .period-display {
  color: var(--gp-text-primary);
}

.p-dark .period-select {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
  color: var(--gp-text-primary);
}

.p-dark .period-select:hover {
  border-color: var(--gp-primary);
}

/* Responsive Design */
@media (max-width: 768px) {
  .digest-header-main {
    gap: var(--gp-spacing-md);
  }

  .period-toggle {
    order: 1;
    width: 100%;
  }

  .toggle-btn {
    width: 100%;
  }

  .period-navigation {
    order: 2;
    width: 100%;
    justify-content: center;
  }

  .period-display {
    font-size: 1.125rem;
    min-width: auto;
  }

  .quick-years {
    display: none;
  }

  .mobile-period-selector {
    display: flex;
  }
}
</style>

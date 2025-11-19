<template>
  <div class="bar-chart">
    <Chart
        type="bar"
        :data="chartData"
        :options="chartOptions"
        class="chart-container"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import Chart from 'primevue/chart'

// Props
const props = defineProps({
  labels: {
    type: Array,
    required: true,
    default: () => []
  },
  data: {
    type: Array,
    default: () => []
  },
  datasets: {
    type: Array,
    default: () => []
  },
  title: {
    type: String,
    default: 'Data'
  },
  color: {
    type: String,
    default: 'primary' // primary, secondary, success, warning, danger
  }
})

// Reactive state for theme updates
const themeColors = ref({
  primary: '',
  primaryLight: '',
  textColor: '',
  textMuted: '',
  borderColor: ''
})

// Color mapping for different chart types
const colorVariants = {
  primary: {
    bg: '--p-primary-color',
    border: '--p-primary-color',
    light: '--p-primary-100'
  },
  secondary: {
    bg: '--p-surface-600',
    border: '--p-surface-700',
    light: '--p-surface-100'
  },
  success: {
    bg: '--p-green-500',
    border: '--p-green-600',
    light: '--p-green-100'
  },
  warning: {
    bg: '--p-orange-500',
    border: '--p-orange-600',
    light: '--p-orange-100'
  },
  danger: {
    bg: '--p-red-500',
    border: '--p-red-600',
    light: '--p-red-100'
  }
}

// Function to get CSS custom property value
const getCSSVariable = (property) => {
  if (typeof window !== 'undefined' && document.documentElement) {
    return getComputedStyle(document.documentElement).getPropertyValue(property).trim()
  }
  return ''
}

// Update theme colors
const updateThemeColors = () => {
  const variant = colorVariants[props.color] || colorVariants.primary
  const isDarkMode = document.documentElement.classList.contains('p-dark')

  themeColors.value = {
    primary: getCSSVariable(variant.bg),
    primaryLight: getCSSVariable(variant.light),
    border: getCSSVariable(variant.border),
    backgroundColor: themeColors.value.primary || '#3B82F6',
    textColor: isDarkMode ? getCSSVariable('--gp-text-primary') : getCSSVariable('--p-text-color'),
    textMuted: isDarkMode ? getCSSVariable('--gp-text-secondary') : getCSSVariable('--p-text-muted-color'),
    borderColor: isDarkMode ? getCSSVariable('--gp-border-dark') : getCSSVariable('--p-content-border-color')
  }
}

// Chart data computed property
const chartData = computed(() => {
  // If datasets prop is provided, use it (for multiple series)
  if (props.datasets && props.datasets.length > 0) {
    const processedDatasets = props.datasets.map((dataset, index) => {
      const colorKey = dataset.color || (index === 0 ? 'primary' : 'secondary')
      const variant = colorVariants[colorKey] || colorVariants.primary
      
      return {
        label: dataset.label || dataset.title || `Series ${index + 1}`,
        backgroundColor: getCSSVariable(variant.bg) || '#3B82F6',
        borderColor: getCSSVariable(variant.border) || '#2563EB',
        borderWidth: 1,
        borderRadius: 4,
        borderSkipped: false,
        data: dataset.data || [],
        // Add hover effects
        hoverBackgroundColor: getCSSVariable(variant.light) || '#DBEAFE',
        hoverBorderColor: getCSSVariable(variant.border) || '#2563EB',
        hoverBorderWidth: 2
      }
    })
    
    return {
      labels: props.labels,
      datasets: processedDatasets
    }
  }
  
  // Fallback to single series (backward compatibility)
  return {
    labels: props.labels,
    datasets: [
      {
        label: props.title,
        backgroundColor: themeColors.value.primary || '#3B82F6',
        borderColor: themeColors.value.border || '#2563EB',
        borderWidth: 1,
        borderRadius: 4,
        borderSkipped: false,
        data: props.data,
        // Add hover effects
        hoverBackgroundColor: themeColors.value.primaryLight || '#DBEAFE',
        hoverBorderColor: themeColors.value.border || '#2563EB',
        hoverBorderWidth: 2
      }
    ]
  }
})

// Chart options computed property
const chartOptions = computed(() => {
  return {
    responsive: true,
    maintainAspectRatio: false,
    aspectRatio: 2.5,

    plugins: {
      legend: {
        display: true,
        position: 'top',
        labels: {
          color: themeColors.value.textColor || (document.documentElement.classList.contains('p-dark') ? '#f1f5f9' : '#374151'),
          font: {
            family: 'Inter, system-ui, sans-serif',
            size: 12,
            weight: '500'
          },
          usePointStyle: true,
          pointStyle: 'rect',
          padding: 20
        }
      },
      tooltip: {
        backgroundColor: '##374151',
        titleColor: '#ffffff',
        bodyColor: '#ffffff',
        borderColor: themeColors.value.borderColor || '#E5E7EB',
        borderWidth: 1,
        cornerRadius: 8,
        displayColors: false,
        titleFont: {
          family: 'Inter, system-ui, sans-serif',
          size: 14,
          weight: '600'
        },
        bodyFont: {
          family: 'Inter, system-ui, sans-serif',
          size: 13,
          weight: '400'
        },
        callbacks: {
          label: function (context) {
            const label = context.dataset.label || '';
            const value = context.parsed.y; // for bar charts
            return `${label}: ${value} km`;
          }
        }
      }
    },

    scales: {
      x: {
        beginAtZero: true,
        ticks: {
          color: themeColors.value.textMuted || (document.documentElement.classList.contains('p-dark') ? '#cbd5e1' : '#6B7280'),
          font: {
            family: 'Inter, system-ui, sans-serif',
            size: 11,
            weight: '500'
          },
          maxRotation: 45,
          minRotation: 0
        },
        grid: {
          display: false,
          drawBorder: false
        },
        border: {
          display: false
        }
      },
      y: {
        beginAtZero: true,
        ticks: {
          color: themeColors.value.textMuted || (document.documentElement.classList.contains('p-dark') ? '#cbd5e1' : '#6B7280'),
          font: {
            family: 'Inter, system-ui, sans-serif',
            size: 11,
            weight: '400'
          },
          // Format numbers nicely
          callback: function(value) {
            if (value >= 1000000) {
              return (value / 1000000).toFixed(1) + 'M'
            } else if (value >= 1000) {
              return (value / 1000).toFixed(1) + 'K'
            }
            return value
          }
        },
        grid: {
          color: themeColors.value.borderColor || (document.documentElement.classList.contains('p-dark') ? 'rgba(255, 255, 255, 0.1)' : '#E5E7EB'),
          drawBorder: false,
          lineWidth: 1
        },
        border: {
          display: false
        }
      }
    },

    // Animation configuration
    animation: {
      duration: 750,
      easing: 'easeInOutQuart'
    },

    // Interaction configuration
    interaction: {
      intersect: false,
      mode: 'index'
    }
  }
})

// Lifecycle hooks
onMounted(() => {
  updateThemeColors()

  // Listen for theme changes
  if (typeof window !== 'undefined') {
    const observer = new MutationObserver(updateThemeColors)
    observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['class', 'data-theme']
    })

    // Store observer for cleanup
    //observer.disconnect = () => observer.disconnect()
    //window.chartThemeObserver = observer
  }
})

onUnmounted(() => {
  // Cleanup theme observer
  if (typeof window !== 'undefined' && window.chartThemeObserver) {
    window.chartThemeObserver.disconnect()
    delete window.chartThemeObserver
  }
})

// Watch for color prop changes
import { watch } from 'vue'
watch(() => props.color, () => {
  updateThemeColors()
}, { immediate: true })
</script>

<style scoped>
.bar-chart {
  width: 100%;
  height: 100%;
  min-height: 400px;
}

.chart-container {
  width: 100%;
  height: 100%;
}

/* Responsive adjustments */
@media (max-width: 640px) {
  .bar-chart {
    min-height: 350px;
  }
}

@media (max-width: 480px) {
  .bar-chart {
    min-height: 300px;
  }
}
</style>
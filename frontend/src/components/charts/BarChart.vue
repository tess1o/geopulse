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
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Chart from 'primevue/chart'
import { t as translate } from '@/locales'
import { useThemeMode } from '@/composables/useThemeMode'
import { readCssToken } from '@/utils/cssTokens'

const { t } = useI18n()
const { isDarkMode } = useThemeMode()

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
    default: () => translate('ui.charts.barChart.defaultTitle')
  },
  color: {
    type: String,
    default: 'primary' // primary, secondary, success, warning, danger, info, contrast
  },
  valueFormatter: {
    type: Function,
    default: null
  },
  yAxisTitle: {
    type: String,
    default: null
  }
})

// Tokens behind each series colour (fill, border, hover fill)
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
  },
  info: {
    bg: '--p-cyan-500',
    border: '--p-cyan-600',
    light: '--p-cyan-100'
  },
  contrast: {
    bg: '--p-purple-500',
    border: '--p-purple-600',
    light: '--p-purple-100'
  }
}

// Chart.js needs resolved colour values, not var() references. They follow the active theme, so they are re-read
// whenever it changes (including OS theme changes in "system" mode).
const readThemeColors = () => ({
  variants: Object.fromEntries(Object.entries(colorVariants).map(([key, tokens]) => [key, {
    bg: readCssToken(tokens.bg),
    border: readCssToken(tokens.border),
    light: readCssToken(tokens.light)
  }])),
  text: readCssToken('--gp-text-primary'),
  textMuted: readCssToken('--gp-text-secondary'),
  border: readCssToken('--gp-border'),
  // Inverted, like PrimeVue tooltips
  tooltipBackground: readCssToken('--gp-surface-inverse'),
  tooltipText: readCssToken('--gp-text-inverse'),
  fontFamily: readCssToken('--gp-font-family')
})

const themeColors = ref(readThemeColors())
watch(isDarkMode, () => {
  themeColors.value = readThemeColors()
})

const toDataset = ({ label, data, variant }) => ({
  label,
  backgroundColor: variant.bg,
  borderColor: variant.border,
  borderWidth: 1,
  borderRadius: 4,
  borderSkipped: false,
  data,
  // Add hover effects
  hoverBackgroundColor: variant.light,
  hoverBorderColor: variant.border,
  hoverBorderWidth: 2
})

const getVariant = (colorKey) => themeColors.value.variants[colorKey] || themeColors.value.variants.primary

// Chart data computed property
const chartData = computed(() => {
  // If datasets prop is provided, use it (for multiple series)
  if (props.datasets && props.datasets.length > 0) {
    return {
      labels: props.labels,
      datasets: props.datasets.map((dataset, index) => toDataset({
        label: dataset.label || dataset.title || t('ui.charts.barChart.seriesFallback', { number: index + 1 }),
        data: dataset.data || [],
        variant: getVariant(dataset.color || (index === 0 ? 'primary' : 'secondary'))
      }))
    }
  }

  // Fallback to single series (backward compatibility)
  return {
    labels: props.labels,
    datasets: [toDataset({ label: props.title, data: props.data, variant: getVariant(props.color) })]
  }
})

// Chart options computed property
const chartOptions = computed(() => {
  const colors = themeColors.value
  const font = (size, weight) => ({ family: colors.fontFamily, size, weight })

  return {
    responsive: true,
    maintainAspectRatio: false,
    aspectRatio: 2.5,

    plugins: {
      legend: {
        display: true,
        position: 'top',
        labels: {
          color: colors.text,
          font: font(12, '500'),
          usePointStyle: true,
          pointStyle: 'rect',
          padding: 20
        }
      },
      tooltip: {
        backgroundColor: colors.tooltipBackground,
        titleColor: colors.tooltipText,
        bodyColor: colors.tooltipText,
        borderColor: colors.border,
        borderWidth: 1,
        cornerRadius: 8,
        displayColors: false,
        titleFont: font(14, '600'),
        bodyFont: font(13, '400'),
        callbacks: {
          label: function (context) {
            const label = context.dataset.label || '';
            const value = context.parsed.y; // for bar charts
            const formattedValue = props.valueFormatter ? props.valueFormatter(value) : t('ui.charts.barChart.valueKm', { value });
            return `${label}: ${formattedValue}`;
          }
        }
      }
    },

    scales: {
      x: {
        beginAtZero: true,
        ticks: {
          color: colors.textMuted,
          font: font(11, '500'),
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
        title: props.yAxisTitle ? {
          display: true,
          text: props.yAxisTitle,
          color: colors.text,
          font: font(12, '600')
        } : undefined,
        ticks: {
          color: colors.textMuted,
          font: font(11, '400'),
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
          color: colors.border,
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
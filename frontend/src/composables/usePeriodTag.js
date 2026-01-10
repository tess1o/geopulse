import { ref, computed } from 'vue'
import { usePeriodTagsStore } from '@/stores/periodTags'

/**
 * Composable for shared period tag functionality
 */
export function usePeriodTag() {
  const store = usePeriodTagsStore()

  // Color palette for period tags
  const COLOR_PALETTE = [
    '#FF6B6B',  // Coral Red
    '#4ECDC4',  // Teal
    '#45B7D1',  // Sky Blue
    '#FFA07A',  // Light Salmon
    '#98D8C8',  // Mint
    '#FFD93D',  // Yellow
    '#A8E6CF',  // Pale Green
    '#C7CEEA',  // Lavender
    '#FF8B94',  // Light Pink
    '#6C5CE7',  // Purple
    '#00B894',  // Green Sea
    '#FDCB6E',  // Orange Yellow
    '#74B9FF',  // Light Blue
    '#A29BFE',  // Periwinkle
    '#FD79A8',  // Pink
    '#00CEC9',  // Cyan
    '#81ECEC',  // Light Cyan
    '#FAB1A0',  // Peach
    '#DFE6E9',  // Light Gray
    '#FF7675'   // Watermelon
  ]

  // Get random color from palette
  const getRandomColor = () => {
    return COLOR_PALETTE[Math.floor(Math.random() * COLOR_PALETTE.length)]
  }

  // Format color with hash prefix
  const formatColorWithHash = (color) => {
    if (!color) return null
    return color.startsWith('#') ? color : `#${color}`
  }

  // Create display color computed property
  const createDisplayColor = (colorRef) => {
    return computed(() => {
      return formatColorWithHash(colorRef.value) || '#FF6B6B'
    })
  }

  // Validation helpers
  const validateTagName = (tagName) => {
    if (!tagName || tagName.trim() === '') {
      return 'Tag name is required'
    }
    return null
  }

  const validateDateRange = (dateRange) => {
    if (!dateRange || !dateRange[0] || !dateRange[1]) {
      return 'Date range is required'
    }
    return null
  }

  return {
    COLOR_PALETTE,
    getRandomColor,
    formatColorWithHash,
    createDisplayColor,
    validateTagName,
    validateDateRange
  }
}

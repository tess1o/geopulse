<template>
  <img
    v-if="(iconInfo.type === 'url' || iconInfo.type === 'local') && !hasError"
    :src="iconInfo.value"
    :alt="alt"
    :class="['provider-icon', `provider-icon--${size}`, customClass]"
    @error="handleImageError"
  />
  <i
    v-else
    :class="[hasError ? DEFAULT_ICON : iconInfo.value, `provider-icon--${size}`, customClass]"
  ></i>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { useProviderIcon } from '@/composables/useProviderIcon';

const props = defineProps({
  provider: {
    type: [Object, String],
    required: true
  },
  size: {
    type: String,
    default: 'medium',
    validator: (value) => ['small', 'medium', 'large'].includes(value)
  },
  alt: {
    type: String,
    default: 'Provider icon'
  },
  customClass: {
    type: String,
    default: ''
  }
});

const { getProviderIconInfo, DEFAULT_ICON } = useProviderIcon();

const hasError = ref(false);

const iconInfo = computed(() => getProviderIconInfo(props.provider));

// Reset error state when provider changes
watch(() => props.provider, () => {
  hasError.value = false;
}, { deep: true });

const handleImageError = () => {
  hasError.value = true;
};
</script>

<style scoped>
.provider-icon {
  object-fit: contain;
  flex-shrink: 0;
}

/* Size variants */
.provider-icon--small {
  width: 16px;
  height: 16px;
  font-size: 1rem;
}

.provider-icon--medium {
  width: 24px;
  height: 24px;
  font-size: 1.25rem;
}

.provider-icon--large {
  width: 32px;
  height: 32px;
  font-size: 1.5rem;
}
</style>

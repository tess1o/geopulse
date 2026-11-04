<template>
  <PathLayer v-if="paths.length" :map="map" :path-data="paths" :visible="visible" :inspection-enabled="false"
    :path-options="{ color: '#8b5cf6', weight: 4, dashArray: '7 6' }" :focus-highlighted-trip="false" />
</template>
<script setup>
import { computed } from 'vue'
import PathLayer from './PathLayer.vue'
const props = defineProps({ map: Object, timeline: { type: Array, default: () => [] }, visible: { type: Boolean, default: true } })
const paths = computed(() => props.timeline.filter(item => item.manualRoute?.length >= 2).map(item => item.manualRoute))
</script>

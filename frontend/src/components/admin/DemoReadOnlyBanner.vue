<template>
  <Message v-if="adminReadOnly" severity="error" :closable="false" class="demo-read-only-banner">
    Demo mode: administration pages are read-only for this account. Create, update, and delete actions are disabled.
  </Message>
</template>

<script setup>
import { storeToRefs } from 'pinia'
import Message from 'primevue/message'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const { adminReadOnly } = storeToRefs(authStore)
</script>

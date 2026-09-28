<template>
  <Message v-if="adminReadOnly" severity="error" :closable="false" class="demo-read-only-banner">
    {{ t('admin.demoReadOnlyBanner.message') }}
  </Message>
</template>

<script setup>
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'
import Message from 'primevue/message'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const authStore = useAuthStore()
const { adminReadOnly } = storeToRefs(authStore)
</script>

<template>
  <Button 
    :icon="isDarkMode ? 'pi pi-sun' : 'pi pi-moon'"
    @click="toggleDarkMode"
    severity="secondary"
    outlined
    v-tooltip.bottom="isDarkMode ? 'Switch to Light Mode' : 'Switch to Dark Mode'"
  />
</template>

<script setup>
import { ref, onMounted } from 'vue'

const isDarkMode = ref(false)

const toggleDarkMode = () => {
  document.documentElement.classList.toggle('p-dark')
  isDarkMode.value = document.documentElement.classList.contains('p-dark')
  localStorage.setItem('darkMode', isDarkMode.value.toString())
}

// Initialize dark mode state
onMounted(() => {
  // Check localStorage first
  const savedMode = localStorage.getItem('darkMode')
  if (savedMode !== null) {
    const shouldBeDark = savedMode === 'true'
    if (shouldBeDark) {
      document.documentElement.classList.add('p-dark')
    } else {
      document.documentElement.classList.remove('p-dark')
    }
  }
  
  // Sync state with current DOM
  isDarkMode.value = document.documentElement.classList.contains('p-dark')
})
</script>
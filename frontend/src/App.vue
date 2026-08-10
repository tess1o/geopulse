<template>
  <div id="app">
    <DemoModeBanner />
    <div class="page-container">
      <div class="main-content">
        <router-view/>
      </div>
    </div>
  </div>
</template>

<script>
import DemoModeBanner from '@/components/demo/DemoModeBanner.vue'

export default {
  name: 'App',
  components: {
    DemoModeBanner
  }
}
</script>

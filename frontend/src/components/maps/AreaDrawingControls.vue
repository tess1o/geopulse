<template>
  <div v-if="state.isDrawing" class="area-drawing-controls" :class="{'area-drawing-controls--inline': inline}" role="group"
       :aria-label="t(state.isAdjusting ? 'favoritesGeocodingDialogs.areaDrawing.adjust' : 'favoritesGeocodingDialogs.areaDrawing.title')" @pointerdown.stop @mousedown.stop @touchstart.stop
       @pointerenter="$emit('preview-pause')" @focusin="$emit('preview-pause')"
       @click.stop @contextmenu.stop>
    <div class="drawing-buttons">
      <template v-if="!state.isAdjusting">
        <Button size="small" :label="t('favoritesGeocodingDialogs.areaDrawing.rectangle')"
                :outlined="state.shape !== 'rectangle'" @click="$emit('shape', 'rectangle')"/>
        <Button size="small" :label="t('favoritesGeocodingDialogs.areaDrawing.polygon')"
                :outlined="state.shape !== 'polygon'" @click="$emit('shape', 'polygon')"/>
        <template v-if="state.shape === 'polygon'">
          <Button size="small" :label="t('favoritesGeocodingDialogs.areaDrawing.undo')" :disabled="!state.vertices.length"
                  @click="$emit('undo')"/>
          <Button size="small" :label="t('favoritesGeocodingDialogs.areaDrawing.finish')"
                  :disabled="state.vertices.length < 3" @click="$emit('finish')"/>
        </template>
      </template>
      <Button v-if="state.isAdjusting" size="small" :label="t('favoritesGeocodingDialogs.areaDrawing.done')" @click="$emit('finish')" />
      <Button size="small" severity="secondary" :label="t('common.cancel')" @click="$emit('cancel')"/>
    </div>
    <p>{{ t(state.isAdjusting ? 'favoritesGeocodingDialogs.areaDrawing.adjustInstruction' : `favoritesGeocodingDialogs.areaDrawing.${state.shape}Instruction`) }}</p>
    <p v-if="state.error" role="alert">{{ t(`favoritesGeocodingDialogs.areaDrawing.errors.${state.error}`) }}</p>
  </div>
</template>
<script setup>
import Button from 'primevue/button'
import {useI18n} from 'vue-i18n'

defineProps({state: {type: Object, required: true}, inline: Boolean})
defineEmits(['shape', 'undo', 'finish', 'cancel', 'preview-pause'])
const {t} = useI18n()
</script>
<style scoped>
.area-drawing-controls {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1100;
  max-width: calc(100% - 24px);
  width: max-content;
  background: var(--gp-surface-card, white);
  color: var(--gp-text-primary);
  padding: 0.75rem;
  border-radius: 8px;
  box-shadow: 0 2px 8px #0003;
}

.area-drawing-controls--inline {
  position: static;
  transform: none;
  width: auto;
  max-width: none;
  margin-bottom: 0.75rem;
  box-shadow: none;
}

.drawing-buttons {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

p {
  margin: 0.5rem 0 0;
  font-size: 0.85rem;
}

[role=alert] {
  color: var(--gp-danger, #b91c1c);
}
</style>

<template>
  <Dialog v-model:visible="internalVisible"
          v-model:header="internalHeader"
          modal
          class="gp-dialog-sm"
          @hide="onDialogHide">
    <div class="dialog-content">
      <InputText v-model="locationName" :placeholder="t('favoritesGeocodingDialogs.addFavorite.locationNamePlaceholder')" class="w-full"/>
    </div>
    <template #footer>
      <Button :label="t('common.cancel')" @click="onDialogHide"/>
      <Button :label="t('favoritesGeocodingDialogs.addFavorite.save')" @click="onSaveButton"/>
    </template>
  </Dialog>
</template>

<script>
import Button from "primevue/button";
import Dialog from "primevue/dialog";
import InputText from "primevue/inputtext";
import { useI18n } from 'vue-i18n';

export default {
  name: "AddFavoriteDialog",
  components: {InputText, Dialog, Button},
  setup() {
    const { t } = useI18n();
    return { t };
  },
  props: {
    visible: Boolean,
    header: String,
    initialName: {
      type: String,
      default: ''
    }
  },
  emits: ['add-to-favorites', 'close'],
  data() {
    return {
      locationName: this.initialName || '',
      internalVisible: this.visible,
      internalHeader: this.header,
    };
  },
  methods: {
    onSaveButton() {
      this.$emit('add-to-favorites', this.locationName);
      this.locationName = ''; // reset input
      this.internalVisible = false;
    },
    onDialogHide() {
      this.internalVisible = false;
    }
  },
  watch: {
    visible(val) {
      this.internalVisible = val;
      if (val) {
        this.locationName = this.initialName || '';
      }
    },
    header(val) {
      this.internalHeader = val;
    },
    initialName(val) {
      if (this.internalVisible) {
        this.locationName = val || '';
      }
    },
    internalVisible(val) {
      if (!val) {
        this.$emit('close');
      }
    }
  },
}
</script>

<style scoped>
.dialog-content {
  padding: 0.5rem 0;
}

.w-full {
  width: 100%;
}
</style>

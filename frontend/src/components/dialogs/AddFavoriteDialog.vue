<template>
  <Dialog v-model:visible="internalVisible"
          v-model:header="internalHeader"
          modal
          class="gp-dialog-sm"
          @hide="onDialogHide">
    <div class="dialog-content">
      <InputText v-model="locationName" placeholder="Location name" class="w-full"/>
    </div>
    <template #footer>
      <Button label="Cancel" @click="onDialogHide"/>
      <Button label="Save" @click="onSaveButton"/>
    </template>
  </Dialog>
</template>

<script>
import Button from "primevue/button";
import Dialog from "primevue/dialog";
import InputText from "primevue/inputtext";

export default {
  name: "AddFavoriteDialog",
  components: {InputText, Dialog, Button},
  props: ['visible', 'header'],
  emits: ['add-to-favorites', 'close'],
  data() {
    return {
      locationName: '',
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
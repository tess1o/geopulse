<template>
  <Dialog v-model:visible="internalVisible"
          v-model:header="internalHeader"
          modal
          @hide="onDialogHide">
    <InputText v-model="locationName" placeholder="Location name"/>
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
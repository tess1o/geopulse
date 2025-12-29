<template>
  <Dialog v-model:visible="internalVisible"
          v-model:header="internalHeader"
          modal
          @hide="onDialogHide">
    <div class="edit-favorite-content">
      <div class="form-field">
        <label for="name" class="field-label">Name</label>
        <InputText
          id="name"
          v-model="favoriteLocation.name"
          placeholder="Enter location name"
          class="w-full"
        />
      </div>

      <div class="form-field">
        <label for="city" class="field-label">City</label>
        <InputText
          id="city"
          v-model="favoriteLocation.city"
          placeholder="Enter city (optional)"
          class="w-full"
        />
      </div>

      <div class="form-field">
        <label for="country" class="field-label">Country</label>
        <InputText
          id="country"
          v-model="favoriteLocation.country"
          placeholder="Enter country (optional)"
          class="w-full"
        />
      </div>
    </div>
    <template #footer>
      <Button
        label="Cancel"
        severity="secondary"
        outlined
        @click="onDialogHide"
      />
      <Button
        label="Save"
        @click="onEditButton"
      />
    </template>
  </Dialog>
</template>

<script>
import Button from "primevue/button";
import Dialog from "primevue/dialog";
import InputText from "primevue/inputtext";

export default {
  name: "EditFavoriteDialog",
  components: {InputText, Dialog, Button},
  props: ['visible', 'header', 'favoriteLocation'],
  emits: ['edit-favorite', 'close'],
  data() {
    return {
      internalVisible: this.visible,
      internalHeader: this.header,
    };
  },
  methods: {
    onEditButton() {
      this.$emit('edit-favorite', {
        id: this.favoriteLocation.id,
        name: this.favoriteLocation.name,
        city: this.favoriteLocation.city,
        country: this.favoriteLocation.country
      });
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
.edit-favorite-content {
  padding: 1rem 0;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.field-label {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--gp-text-primary);
}

/* GeoPulse Dialog Styling */
:deep(.p-dialog) {
  border-radius: var(--gp-radius-large);
  box-shadow: var(--gp-shadow-large);
  border: 1px solid var(--gp-border-medium);
}

:deep(.p-dialog-header) {
  background: var(--gp-surface-white);
  border-bottom: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-large) var(--gp-radius-large) 0 0;
  padding: 1.5rem;
}

:deep(.p-dialog-title) {
  font-weight: 600;
  color: var(--gp-text-primary);
  font-size: 1.25rem;
}

:deep(.p-dialog-content) {
  background: var(--gp-surface-white);
  padding: 0 1.5rem;
  color: var(--gp-text-primary);
}

:deep(.p-dialog-footer) {
  background: var(--gp-surface-white);
  border-top: 1px solid var(--gp-border-light);
  border-radius: 0 0 var(--gp-radius-large) var(--gp-radius-large);
  padding: 1.5rem;
  display: flex;
  justify-content: flex-end;
  gap: 1rem;
}

/* Input Styling */
:deep(.p-inputtext) {
  border-radius: var(--gp-radius-medium);
  border: 1px solid var(--gp-border-medium);
  padding: 0.75rem;
  font-size: 1rem;
  transition: all 0.2s ease;
}

:deep(.p-inputtext:focus) {
  border-color: var(--gp-primary);
  box-shadow: 0 0 0 3px rgba(26, 86, 219, 0.1);
  outline: none;
}

/* Button Styling */
:deep(.p-button) {
  border-radius: var(--gp-radius-medium);
  font-weight: 600;
  padding: 0.75rem 1.5rem;
  transition: all 0.2s ease;
}

:deep(.p-button:not(.p-button-outlined)) {
  background: var(--gp-primary);
  border-color: var(--gp-primary);
}

:deep(.p-button:not(.p-button-outlined):hover) {
  background: var(--gp-primary-hover);
  border-color: var(--gp-primary-hover);
  transform: translateY(-1px);
  box-shadow: var(--gp-shadow-medium);
}

:deep(.p-button.p-button-outlined) {
  border-color: var(--gp-border-medium);
  color: var(--gp-text-secondary);
}

:deep(.p-button.p-button-outlined:hover) {
  background: var(--gp-surface-light);
  border-color: var(--gp-border-dark);
  color: var(--gp-text-primary);
}

/* Dark Mode */
.p-dark :deep(.p-dialog) {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark :deep(.p-dialog-header),
.p-dark :deep(.p-dialog-content),
.p-dark :deep(.p-dialog-footer) {
  background: var(--gp-surface-dark);
  border-color: var(--gp-border-dark);
}

.p-dark :deep(.p-dialog-title) {
  color: var(--gp-text-primary);
}

.p-dark :deep(.p-inputtext) {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
  color: var(--gp-text-primary);
}

.p-dark :deep(.p-inputtext:focus) {
  border-color: var(--gp-primary);
  box-shadow: 0 0 0 3px rgba(26, 86, 219, 0.2);
}

.p-dark :deep(.p-button.p-button-outlined) {
  border-color: var(--gp-border-dark);
  color: var(--gp-text-secondary);
}

.p-dark :deep(.p-button.p-button-outlined:hover) {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-light);
  color: var(--gp-text-primary);
}
</style>
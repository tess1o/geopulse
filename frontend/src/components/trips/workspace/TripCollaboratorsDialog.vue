<template>
  <Dialog
    v-model:visible="dialogVisible"
    modal
    :header="t('trips.workspacePage.collaboratorsDialog.header')"
    class="gp-dialog-md"
    @show="loadCollaboratorsData"
  >
    <div class="collaborators-content">
      <Message severity="info" :closable="false">
        {{ t('trips.workspacePage.collaboratorsDialog.infoMessage') }}
      </Message>

      <div class="collaborator-add-row">
        <Select
          v-model="newCollaboratorFriendId"
          :options="availableFriendOptions"
          optionLabel="label"
          optionValue="value"
          :placeholder="t('trips.workspacePage.collaboratorsDialog.selectFriendPlaceholder')"
          class="w-full"
          filter
        />
        <Select
          v-model="newCollaboratorRole"
          :options="collaboratorRoleOptions"
          optionLabel="label"
          optionValue="value"
          class="collaborator-role-select"
        />
        <Button
          icon="pi pi-plus"
          :label="t('trips.workspacePage.collaboratorsDialog.add')"
          :loading="isSavingCollaborator"
          :disabled="!newCollaboratorFriendId"
          @click="addCollaborator"
        />
      </div>

      <div v-if="collaboratorsLoading" class="collaborators-loading">
        <ProgressSpinner />
      </div>

      <div v-else-if="collaborators.length === 0" class="collaborators-empty">
        {{ t('trips.workspacePage.collaboratorsDialog.empty') }}
      </div>

      <div v-else class="collaborators-list">
        <div
          v-for="collaborator in collaborators"
          :key="collaborator.userId"
          class="collaborator-row"
        >
          <div class="collaborator-main">
            <strong>{{ collaborator.fullName || collaborator.email }}</strong>
            <small>{{ collaborator.email }}</small>
          </div>
          <Select
            :model-value="collaborator.accessRole"
            :options="collaboratorRoleOptions"
            optionLabel="label"
            optionValue="value"
            class="collaborator-role-select"
            @update:model-value="updateCollaboratorRole(collaborator, $event)"
          />
          <Button
            icon="pi pi-trash"
            text
            rounded
            severity="danger"
            :loading="isSavingCollaborator"
            @click="removeCollaborator(collaborator)"
          />
        </div>
      </div>
    </div>
  </Dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Message from 'primevue/message'
import ProgressSpinner from 'primevue/progressspinner'
import Select from 'primevue/select'
import { useTripsStore } from '@/stores/trips'
import { useFriendsStore } from '@/stores/friends'
import { formatApiErrorDetail } from '@/utils/apiErrorDetail'

// Trip owner's dialog for sharing the trip with friends as viewers or editors.
const props = defineProps({
  visible: { type: Boolean, default: false },
  tripId: { type: Number, required: true },
  canManage: { type: Boolean, default: false }
})
const emit = defineEmits(['update:visible'])

const { t } = useI18n()
const toast = useToast()
const tripsStore = useTripsStore()
const friendsStore = useFriendsStore()

const dialogVisible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value)
})

const collaboratorsLoading = ref(false)
const isSavingCollaborator = ref(false)
const collaborators = ref([])
const availableFriends = ref([])
const newCollaboratorFriendId = ref(null)
const newCollaboratorRole = ref('VIEW')

const collaboratorRoleOptions = computed(() => [
  { label: t('trips.managementPage.access.viewer'), value: 'VIEW' },
  { label: t('trips.managementPage.access.editor'), value: 'EDIT' }
])

const availableFriendOptions = computed(() => {
  const selectedIds = new Set((collaborators.value || []).map((item) => String(item.userId)))
  return (availableFriends.value || [])
    .filter((friend) => !selectedIds.has(String(friend.friendId)))
    .map((friend) => ({
      value: friend.friendId,
      label: friend.fullName || friend.email || String(friend.friendId)
    }))
})

const loadCollaboratorsData = async () => {
  if (!props.canManage) return
  collaboratorsLoading.value = true
  try {
    const [loadedCollaborators, loadedFriends] = await Promise.all([
      tripsStore.fetchTripCollaborators(props.tripId),
      friendsStore.fetchFriends()
    ])
    collaborators.value = Array.isArray(loadedCollaborators) ? loadedCollaborators : []
    availableFriends.value = Array.isArray(loadedFriends) ? loadedFriends : []
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('trips.workspacePage.toasts.loadCollaboratorsFailedSummary'),
      detail: formatApiErrorDetail(error, t('trips.workspacePage.toasts.loadCollaboratorsFailedFallback')),
      life: 5000
    })
  } finally {
    collaboratorsLoading.value = false
  }
}

const addCollaborator = async () => {
  if (!newCollaboratorFriendId.value) return
  isSavingCollaborator.value = true
  try {
    await tripsStore.setTripCollaborator(props.tripId, newCollaboratorFriendId.value, newCollaboratorRole.value)
    newCollaboratorFriendId.value = null
    newCollaboratorRole.value = 'VIEW'
    await loadCollaboratorsData()
    toast.add({
      severity: 'success',
      summary: t('trips.workspacePage.toasts.collaboratorAddedSummary'),
      detail: t('trips.workspacePage.toasts.collaboratorAddedDetail'),
      life: 2500
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('trips.workspacePage.toasts.addCollaboratorFailedSummary'),
      detail: formatApiErrorDetail(error, t('trips.workspacePage.toasts.requestFailed')),
      life: 5000
    })
  } finally {
    isSavingCollaborator.value = false
  }
}

const updateCollaboratorRole = async (collaborator, nextRole) => {
  if (!collaborator?.userId || !nextRole) return
  const previousRole = collaborator.accessRole
  collaborator.accessRole = nextRole
  isSavingCollaborator.value = true
  try {
    await tripsStore.setTripCollaborator(props.tripId, collaborator.userId, nextRole)
    toast.add({
      severity: 'success',
      summary: t('trips.workspacePage.toasts.roleUpdatedSummary'),
      detail: t('trips.workspacePage.toasts.roleUpdatedDetail'),
      life: 2200
    })
  } catch (error) {
    collaborator.accessRole = previousRole
    toast.add({
      severity: 'error',
      summary: t('trips.workspacePage.toasts.updateRoleFailedSummary'),
      detail: formatApiErrorDetail(error, t('trips.workspacePage.toasts.requestFailed')),
      life: 5000
    })
  } finally {
    isSavingCollaborator.value = false
  }
}

const removeCollaborator = async (collaborator) => {
  if (!collaborator?.userId) return
  isSavingCollaborator.value = true
  try {
    await tripsStore.removeTripCollaborator(props.tripId, collaborator.userId)
    collaborators.value = collaborators.value.filter((item) => String(item.userId) !== String(collaborator.userId))
    toast.add({
      severity: 'success',
      summary: t('trips.workspacePage.toasts.collaboratorRemovedSummary'),
      detail: t('trips.workspacePage.toasts.collaboratorRemovedDetail'),
      life: 2200
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: t('trips.workspacePage.toasts.removeCollaboratorFailedSummary'),
      detail: formatApiErrorDetail(error, t('trips.workspacePage.toasts.requestFailed')),
      life: 5000
    })
  } finally {
    isSavingCollaborator.value = false
  }
}
</script>

<style scoped>
.collaborators-content {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-md);
}

.collaborator-add-row {
  display: grid;
  grid-template-columns: 1fr 10rem auto;
  gap: var(--gp-spacing-sm);
  align-items: center;
}

.collaborator-role-select {
  min-width: 8rem;
}

.collaborators-loading {
  display: flex;
  justify-content: center;
  padding: var(--gp-spacing-md);
}

.collaborators-empty {
  color: var(--gp-text-secondary);
  text-align: center;
  padding: var(--gp-spacing-md);
}

.collaborators-list {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.collaborator-row {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: var(--gp-spacing-sm);
  align-items: center;
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-small);
  padding: var(--gp-spacing-sm);
}

.collaborator-main {
  display: flex;
  flex-direction: column;
}

.collaborator-main small {
  color: var(--gp-text-secondary);
}

@media (max-width: 768px) {
  .collaborator-add-row {
    grid-template-columns: 1fr;
  }

  .collaborator-row {
    grid-template-columns: 1fr;
  }
}
</style>

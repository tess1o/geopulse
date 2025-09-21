<template>
  <div
      class="bg-surface-0 border border-black/10 dark:border-white/20 dark:bg-surface-950 rounded-3xl overflow-hidden"
      style="min-width: 250px"
      id="sent-invitations">

    <!-- Header -->
    <div class="flex items-center justify-between p-4 border-b border-surface-200 dark:border-surface-700">
      <div class="flex items-center gap-2">
        <i class="pi pi-send text-blue-500"></i>
        <span class="font-bold text-lg text-surface-900 dark:text-surface-100">Sent Invites</span>
        <Badge v-if="sentInvites?.length > 0" :value="sentInvites.length" severity="info" />
      </div>
    </div>

    <!-- Loading State -->
    <div v-if="isLoading" class="p-6 text-center">
      <ProgressSpinner size="small" />
      <p class="text-surface-500 mt-2">Loading sent invites...</p>
    </div>

    <!-- Has Invites -->
    <div v-else-if="sentInvites?.length > 0" class="p-4">
      <div class="space-y-3">
        <div
            v-for="invite in sentInvites"
            :key="invite.id"
            class="flex items-center justify-between p-3 bg-surface-50 dark:bg-surface-800 rounded-lg border border-surface-200 dark:border-surface-600 hover:bg-surface-100 dark:hover:bg-surface-700 transition-colors"
        >
          <div class="flex items-center gap-3 flex-1">
            <!-- Avatar placeholder -->
            <div class="w-8 h-8 bg-blue-100 dark:bg-blue-900 rounded-full flex items-center justify-center">
              <i class="pi pi-user text-blue-600 dark:text-blue-400 text-sm"></i>
            </div>

            <!-- Invite info -->
            <div class="flex-1 min-w-0">
              <p class="text-sm font-medium text-surface-900 dark:text-surface-100 truncate">
                {{ invite.receiverName }}
              </p>
              <div class="flex items-center gap-1 mt-1">
                <i class="pi pi-clock text-xs text-surface-500"></i>
                <p class="text-xs text-surface-500">
                  Sent {{ timezone.timeAgo(invite.sentAt) }}
                </p>
              </div>
            </div>
          </div>

          <!-- Actions -->
          <div class="flex items-center gap-2">
            <!-- Status indicator -->
            <div class="flex items-center gap-1">
              <div class="w-2 h-2 bg-yellow-400 rounded-full animate-pulse"></div>
              <span class="text-xs text-surface-500">Pending</span>
            </div>

            <!-- Cancel button -->
            <Button
                icon="pi pi-times"
                size="small"
                outlined
                severity="secondary"
                class="w-8 h-8"
                v-tooltip.top="'Cancel invitation'"
                :loading="cancellingId === invite.id"
                @click="handleCancelInvite(invite)"
            />
          </div>
        </div>
      </div>

      <!-- Bulk actions (if multiple invites) -->
      <div v-if="sentInvites.length > 1" class="mt-4 pt-3 border-t border-surface-200 dark:border-surface-700">
        <Button
            label="Cancel All Pending"
            icon="pi pi-times-circle"
            size="small"
            outlined
            severity="secondary"
            class="w-full"
            :loading="cancellingAll"
            @click="handleCancelAllInvites"
        />
      </div>
    </div>

    <!-- Empty State -->
    <div v-else class="p-6 text-center">
      <div class="mb-4">
        <div class="w-16 h-16 bg-surface-100 dark:bg-surface-800 rounded-full flex items-center justify-center mx-auto mb-3">
          <i class="pi pi-send text-2xl text-surface-400"></i>
        </div>
        <h3 class="text-lg font-medium text-surface-900 dark:text-surface-100 mb-2">
          No pending invites
        </h3>
        <p class="text-sm text-surface-500 max-w-sm mx-auto">
          When you send friend requests, they'll appear here until accepted or declined.
        </p>
      </div>
    </div>

    <!-- Confirmation Dialog -->
    <Dialog
        v-model:visible="showCancelDialog"
        modal
        header="Cancel Invitation"
        :style="{ width: '25rem' }"
    >
      <div class="flex items-start gap-3 mb-4">
        <i class="pi pi-exclamation-triangle text-yellow-500 text-xl mt-1"></i>
        <div>
          <p class="text-surface-900 dark:text-surface-100 mb-2">
            Cancel invitation to <strong>{{ selectedInvite?.receiverName }}</strong>?
          </p>
          <p class="text-sm text-surface-500">
            This action cannot be undone. You can send a new invitation later.
          </p>
        </div>
      </div>
      <div class="flex justify-end gap-2">
        <Button
            label="Keep Invitation"
            severity="secondary"
            @click="showCancelDialog = false"
        />
        <Button
            label="Cancel Invitation"
            severity="danger"
            :loading="cancellingId !== null"
            @click="confirmCancelInvite"
        />
      </div>
    </Dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useTimezone } from '@/composables/useTimezone'

const timezone = useTimezone()

defineProps(['sentInvites', 'isLoading'])
const emit = defineEmits(['cancel-invite', 'cancel-all-invites'])

// Local state
const showCancelDialog = ref(false)
const selectedInvite = ref(null)
const cancellingId = ref(null)
const cancellingAll = ref(false)

// Methods
const handleCancelInvite = (invite) => {
  selectedInvite.value = invite
  showCancelDialog.value = true
}

const confirmCancelInvite = async () => {
  if (!selectedInvite.value) return

  cancellingId.value = selectedInvite.value.id
  await emit('cancel-invite', selectedInvite.value.id)

  // Reset state
  cancellingId.value = null
  showCancelDialog.value = false
  selectedInvite.value = null
}

const handleCancelAllInvites = async () => {
  cancellingAll.value = true
  emit('cancel-all-invites')
  cancellingAll.value = false
}
</script>

<script>
export default {
  name: "SentInvitesTable"
}
</script>
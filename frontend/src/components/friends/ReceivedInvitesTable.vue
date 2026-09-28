<template>
  <div
      class="bg-surface-0 border border-black/10 dark:border-white/20 dark:bg-surface-950 rounded-3xl overflow-hidden"
      style="min-width: 250px"
      id="received-invitations">

    <!-- Header -->
    <div class="flex items-center justify-between p-4 border-b border-surface-200 dark:border-surface-700">
      <div class="flex items-center gap-2">
        <i class="pi pi-inbox text-green-500"></i>
        <span class="font-bold text-lg text-surface-900 dark:text-surface-100">{{ t('friends.receivedInvites.header') }}</span>
        <Badge v-if="receivedInvites?.length > 0" :value="receivedInvites.length" severity="success" />
      </div>
    </div>

    <!-- Loading State -->
    <div v-if="isLoading" class="p-6 text-center">
      <ProgressSpinner size="small" />
      <p class="text-surface-500 mt-2">{{ t('friends.receivedInvites.loading') }}</p>
    </div>

    <!-- Has Invites -->
    <div v-else-if="receivedInvites?.length > 0" class="p-4">
      <div class="space-y-3">
        <div
            v-for="invite in receivedInvites"
            :key="invite.id"
            class="flex items-center justify-between p-4 bg-surface-50 dark:bg-surface-800 rounded-lg border border-surface-200 dark:border-surface-600 hover:bg-surface-100 dark:hover:bg-surface-700 transition-colors"
        >
          <div class="flex items-center gap-3 flex-1">
            <!-- Avatar placeholder -->
            <div class="w-10 h-10 bg-green-100 dark:bg-green-900 rounded-full flex items-center justify-center">
              <i class="pi pi-user text-green-600 dark:text-green-400"></i>
            </div>

            <!-- Invite info -->
            <div class="flex-1 min-w-0">
              <p class="text-base font-medium text-surface-900 dark:text-surface-100 truncate">
                {{ invite.senderName }}
              </p>
              <p class="text-sm text-surface-500 mt-1">
                {{ t('friends.receivedInvites.wantsToConnect') }}
              </p>
              <div class="flex items-center gap-1 mt-1">
                <i class="pi pi-clock text-xs text-surface-500"></i>
                <p class="text-xs text-surface-500">
                  {{ formatInviteDate(invite.receivedAt) }}
                </p>
              </div>
            </div>
          </div>

          <!-- Actions -->
          <div class="flex items-center gap-2">
            <!-- Accept button -->
            <Button
                icon="pi pi-check"
                size="small"
                severity="success"
                class="w-9 h-9"
                v-tooltip.top="t('friends.receivedInvites.acceptTooltip')"
                :loading="acceptingId === invite.id"
                @click="handleAcceptInvite(invite)"
            />

            <!-- Reject button -->
            <Button
                icon="pi pi-times"
                size="small"
                outlined
                severity="secondary"
                class="w-9 h-9"
                v-tooltip.top="t('friends.receivedInvites.declineTooltip')"
                :loading="rejectingId === invite.id"
                @click="handleRejectInvite(invite)"
            />
          </div>
        </div>
      </div>

      <!-- Bulk actions (if multiple invites) -->
      <div v-if="receivedInvites.length > 1" class="mt-4 pt-3 border-t border-surface-200 dark:border-surface-700">
        <div class="flex gap-2">
          <Button
              :label="t('friends.receivedInvites.acceptAll')"
              icon="pi pi-check-circle"
              size="small"
              severity="success"
              class="flex-1"
              :loading="acceptingAll"
              @click="handleAcceptAllInvites"
          />
          <Button
              :label="t('friends.receivedInvites.declineAll')"
              icon="pi pi-times-circle"
              size="small"
              outlined
              severity="secondary"
              class="flex-1"
              :loading="rejectingAll"
              @click="handleRejectAllInvites"
          />
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-else class="p-6 text-center">
      <div class="mb-4">
        <div class="w-16 h-16 bg-surface-100 dark:bg-surface-800 rounded-full flex items-center justify-center mx-auto mb-3">
          <i class="pi pi-inbox text-2xl text-surface-400"></i>
        </div>
        <h3 class="text-lg font-medium text-surface-900 dark:text-surface-100 mb-2">
          {{ t('friends.receivedInvites.emptyTitle') }}
        </h3>
        <p class="text-sm text-surface-500 max-w-sm mx-auto">
          {{ t('friends.receivedInvites.emptyMessage') }}
        </p>
      </div>
    </div>

    <!-- Accept Confirmation Dialog -->
    <Dialog
        v-model:visible="showAcceptDialog"
        modal
        :header="t('friends.receivedInvites.acceptDialog.header')"
        :style="{ width: '25rem' }"
    >
      <div class="flex items-start gap-3 mb-4">
        <i class="pi pi-user-plus text-green-500 text-xl mt-1"></i>
        <div>
          <p class="text-surface-900 dark:text-surface-100 mb-2">
            <i18n-t keypath="friends.receivedInvites.acceptDialog.message" tag="span">
              <template #name><strong>{{ selectedInvite?.senderName }}</strong></template>
            </i18n-t>
          </p>
          <p class="text-sm text-surface-500">
            {{ t('friends.receivedInvites.acceptDialog.note') }}
          </p>
        </div>
      </div>
      <div class="flex justify-end gap-2">
        <Button
            :label="t('common.cancel')"
            severity="secondary"
            @click="showAcceptDialog = false"
        />
        <Button
            :label="t('friends.receivedInvites.acceptDialog.confirm')"
            severity="success"
            :loading="acceptingId !== null"
            @click="confirmAcceptInvite"
        />
      </div>
    </Dialog>

    <!-- Reject Confirmation Dialog -->
    <Dialog
        v-model:visible="showRejectDialog"
        modal
        :header="t('friends.receivedInvites.rejectDialog.header')"
        :style="{ width: '25rem' }"
    >
      <div class="flex items-start gap-3 mb-4">
        <i class="pi pi-exclamation-triangle text-yellow-500 text-xl mt-1"></i>
        <div>
          <p class="text-surface-900 dark:text-surface-100 mb-2">
            <i18n-t keypath="friends.receivedInvites.rejectDialog.message" tag="span">
              <template #name><strong>{{ selectedInvite?.senderName }}</strong></template>
            </i18n-t>
          </p>
          <p class="text-sm text-surface-500">
            {{ t('friends.receivedInvites.rejectDialog.note') }}
          </p>
        </div>
      </div>
      <div class="flex justify-end gap-2">
        <Button
            :label="t('friends.receivedInvites.rejectDialog.keep')"
            severity="secondary"
            @click="showRejectDialog = false"
        />
        <Button
            :label="t('friends.receivedInvites.rejectDialog.confirm')"
            severity="danger"
            :loading="rejectingId !== null"
            @click="confirmRejectInvite"
        />
      </div>
    </Dialog>

    <!-- Bulk Actions Confirmation -->
    <Dialog
        v-model:visible="showBulkDialog"
        modal
        :header="bulkAction === 'accept' ? t('friends.receivedInvites.bulkDialog.acceptHeader') : t('friends.receivedInvites.bulkDialog.declineHeader')"
        :style="{ width: '25rem' }"
    >
      <div class="flex items-start gap-3 mb-4">
        <i :class="bulkAction === 'accept' ? 'pi pi-users text-green-500' : 'pi pi-exclamation-triangle text-yellow-500'" class="text-xl mt-1"></i>
        <div>
          <p class="text-surface-900 dark:text-surface-100 mb-2">
            {{ t('friends.receivedInvites.bulkDialog.message', {
              action: bulkAction === 'accept' ? t('friends.receivedInvites.bulkDialog.acceptAction') : t('friends.receivedInvites.bulkDialog.declineAction'),
              count: receivedInvites?.length
            }) }}
          </p>
          <p class="text-sm text-surface-500">
            {{ bulkAction === 'accept'
              ? t('friends.receivedInvites.bulkDialog.acceptNote')
              : t('friends.receivedInvites.bulkDialog.declineNote')
            }}
          </p>
        </div>
      </div>
      <div class="flex justify-end gap-2">
        <Button
            :label="t('common.cancel')"
            severity="secondary"
            @click="showBulkDialog = false"
        />
        <Button
            :label="bulkAction === 'accept' ? t('friends.receivedInvites.acceptAll') : t('friends.receivedInvites.declineAll')"
            :severity="bulkAction === 'accept' ? 'success' : 'danger'"
            :loading="acceptingAll || rejectingAll"
            @click="confirmBulkAction"
        />
      </div>
    </Dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useTimezone } from '@/composables/useTimezone';

const { t } = useI18n()

defineProps(['receivedInvites', 'isLoading'])
const emit = defineEmits(['accept-invite', 'reject-invite', 'accept-all-invites', 'reject-all-invites'])

// Local state
const showAcceptDialog = ref(false)
const showRejectDialog = ref(false)
const showBulkDialog = ref(false)
const selectedInvite = ref(null)
const acceptingId = ref(null)
const rejectingId = ref(null)
const acceptingAll = ref(false)
const rejectingAll = ref(false)
const bulkAction = ref(null) // 'accept' or 'reject'

// Methods
const handleAcceptInvite = (invite) => {
  selectedInvite.value = invite
  showAcceptDialog.value = true
}

const handleRejectInvite = (invite) => {
  selectedInvite.value = invite
  showRejectDialog.value = true
}

const confirmAcceptInvite = async () => {
  if (!selectedInvite.value) return

  acceptingId.value = selectedInvite.value.id
  emit('accept-invite', selectedInvite.value.id)

  // Reset state
  acceptingId.value = null
  showAcceptDialog.value = false
  selectedInvite.value = null
}

const confirmRejectInvite = async () => {
  if (!selectedInvite.value) return

  rejectingId.value = selectedInvite.value.id
  emit('reject-invite', selectedInvite.value.id)

  // Reset state
  rejectingId.value = null
  showRejectDialog.value = false
  selectedInvite.value = null
}

const handleAcceptAllInvites = () => {
  bulkAction.value = 'accept'
  showBulkDialog.value = true
}

const handleRejectAllInvites = () => {
  bulkAction.value = 'reject'
  showBulkDialog.value = true
}

const confirmBulkAction = async () => {
  if (bulkAction.value === 'accept') {
    acceptingAll.value = true
    emit('accept-all-invites')
    acceptingAll.value = false
  } else {
    rejectingAll.value = true
    emit('reject-all-invites')
    rejectingAll.value = false
  }

  showBulkDialog.value = false
  bulkAction.value = null
}

const timezone = useTimezone()
const formatInviteDate = (date) => {
  if (!date) return t('friends.receivedInvites.relativeTime.recently')

  const now = timezone.now()
  const inviteDate = timezone.fromUtc(date)
  const diffDays = now.diff(inviteDate, 'day')
  const diffHours = now.diff(inviteDate, 'hour')
  const diffMinutes = now.diff(inviteDate, 'minute')

  if (diffDays > 0) return t('friends.receivedInvites.relativeTime.daysAgo', { count: diffDays }, diffDays)
  if (diffHours > 0) return t('friends.receivedInvites.relativeTime.hoursAgo', { count: diffHours }, diffHours)
  if (diffMinutes > 0) return t('friends.receivedInvites.relativeTime.minutesAgo', { count: diffMinutes }, diffMinutes)
  return t('friends.receivedInvites.relativeTime.justNow')
}
</script>

<script>
export default {
  name: "ReceivedInvitesTable"
}
</script>
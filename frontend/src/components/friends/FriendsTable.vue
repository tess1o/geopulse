<template>
  <div>
    <Dialog closable v-model:visible="inviteDialogVisible" modal :header="t('friends.invite.header')" :style="{ width: '25rem' }">
      <span class="text-surface-500 dark:text-surface-400 block mb-4">{{ t('friends.invite.subtitle') }}</span>
      <div class="flex items-center gap-4 mb-4">
        <label for="username" class="font-semibold w-24">{{ t('friends.invite.nameLabel') }}</label>
        <InputText id="username" v-model="inviteUserName" class="flex-auto" autocomplete="off"/>
      </div>
      <div class="flex justify-end gap-2">
        <Button type="button" :label="t('common.cancel')" severity="secondary" @click="inviteDialogVisible = false"></Button>
        <Button type="button" :label="t('friends.invite.submit')" :loading="submittingInvite" @click="handleInviteSubmit"></Button>
      </div>
    </Dialog>
    <div
        class="bg-surface-0 border border-black/10 dark:border-white/20 dark:bg-surface-950 rounded-3xl flex-none overflow-hidden"
        style="min-width: 300px"
        id="friends-list">
      <DataTable :value="friends"
                 v-if="friends !== null && friends.length !== 0"
                 dataKey="name">
        <template #header>
          <div class="flex flex-wrap gap-2 items-center justify-between">
            <span class="font-bold text-lg text-primary">👥 {{ t('friends.list.header') }}</span>
            <Button :label="t('friends.list.inviteFriend')" icon="pi pi-plus" class="mr-2" @click="inviteDialogVisible = true"/>
          </div>
        </template>

        <Column
            field="fullName"
            :header="t('friends.list.columns.friendName')"
            :class="['min-w-[7rem]', 'md:min-w-[10rem]']"
        >
          <template #body="slotProps">
            <Button
                :label="slotProps.data.fullName"
                link
                class="text-left p-0 font-medium text-primary-600 hover:text-primary-700"
                @click="emit('show-friend-on-map', slotProps.data)"
            />
          </template>
        </Column>

        <Column
            v-if="isMobile"
            field="lastSeen"
            :header="t('friends.list.columns.lastSeen')"
            :class="['min-w-[8rem]', 'max-w-[10rem]']"
        >
          <template #body="slotProps">
            {{ timezone.timeAgo(slotProps.data.lastSeen) }}
          </template>
        </Column>
        <!-- Desktop version (sortable) -->
        <Column
            v-else
            field="lastSeen"
            :header="t('friends.list.columns.lastSeen')"
            sortable
            :class="['min-w-[8rem]', 'max-w-[10rem]']"
        >
          <template #body="slotProps">
            {{ timezone.timeAgo(slotProps.data.lastSeen) }}
          </template>
        </Column>
        <Column field="lastLocation" :header="t('friends.list.columns.lastLocation')"
                style="max-width:16rem; white-space: normal; word-break: break-word;"></Column>
        <Column :header="t('friends.list.columns.remove')" :exportable="false" style="min-width: 6rem">
          <template #body="slotProps">
            <Button icon="pi pi-trash" outlined rounded severity="danger"
                    v-tooltip.top="t('friends.list.removeTooltip')"
                    :loading="removingId === slotProps.data.id"
                    @click="handleRemoveFriend(slotProps.data)"/>
          </template>
        </Column>
      </DataTable>
      <div v-else
           class="flex flex-col justify-center items-center h-full text-center space-y-2 mb-4">
        <p class="text-lg font-medium text-gray-700 dark:text-gray-300 max-w-md">
          {{ t('friends.list.empty') }}
        </p>
        <Button :label="t('friends.list.inviteFriend')" icon="pi pi-plus" @click="inviteDialogVisible = true"/>
      </div>

      <!-- Remove Friend Confirmation Dialog -->
      <Dialog
          v-model:visible="showRemoveDialog"
          modal
          :header="t('friends.list.removeDialog.header')"
          :style="{ width: '25rem' }"
      >
        <div class="flex items-start gap-3 mb-4">
          <i class="pi pi-exclamation-triangle text-yellow-500 text-xl mt-1"></i>
          <div>
            <p class="text-surface-900 dark:text-surface-100 mb-2">
              <i18n-t keypath="friends.list.removeDialog.message" tag="span">
                <template #name><strong>{{ selectedFriend?.name }}</strong></template>
              </i18n-t>
            </p>
            <p class="text-sm text-muted-color">
              {{ t('friends.list.removeDialog.note') }}
            </p>
          </div>
        </div>
        <div class="flex justify-end gap-2">
          <Button
              :label="t('common.cancel')"
              severity="secondary"
              @click="showRemoveDialog = false"
          />
          <Button
              :label="t('friends.list.removeDialog.confirm')"
              severity="danger"
              :loading="removingId !== null"
              @click="confirmRemoveFriend"
          />
        </div>
      </Dialog>
    </div>
  </div>
</template>

<script setup>
import {onMounted, onUnmounted, ref} from 'vue'
import { useI18n } from 'vue-i18n'
import { useTimezone } from '@/composables/useTimezone'

const { t } = useI18n()
const timezone = useTimezone()

const props = defineProps({
  friends: Array,
  onInvite: Function
})

const emit = defineEmits(['invite-friend', 'delete-friend', 'show-friend-on-map'])

// Reactive state
const isMobile = ref(false)
const inviteUserName = ref(null)
const inviteDialogVisible = ref(false)
const submittingInvite = ref(false)
const showRemoveDialog = ref(false)
const selectedFriend = ref(null)
const removingId = ref(null)

// Methods
const handleInviteSubmit = async () => {
  submittingInvite.value = true
  const success = await props.onInvite(inviteUserName.value)
  submittingInvite.value = false

  if (success) {
    inviteDialogVisible.value = false
    inviteUserName.value = ''
  }
}

const handleRemoveFriend = (friend) => {
  selectedFriend.value = friend
  showRemoveDialog.value = true
}

const confirmRemoveFriend = async () => {
  if (!selectedFriend.value) return

  removingId.value = selectedFriend.value.friendId
  emit('delete-friend', selectedFriend.value.friendId)

  // Reset state
  removingId.value = null
  showRemoveDialog.value = false
  selectedFriend.value = null
}

const handleResize = () => {
  isMobile.value = window.innerWidth < 768
}

// Lifecycle
onMounted(() => {
  isMobile.value = window.innerWidth < 768
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<script>
export default {
  name: "FriendsTable"
}
</script>
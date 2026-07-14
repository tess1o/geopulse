<template>
  <AppLayout>
    <PageContainer>
      <div class="notifications-page">
        <div class="page-header">
          <div>
            <h1>Notifications</h1>
            <p>Review timeline, geofence, import, export, and friend notifications.</p>
          </div>
          <div class="page-actions">
            <Button
              label="Refresh"
              icon="pi pi-refresh"
              severity="secondary"
              outlined
              :loading="loading"
              @click="loadNotifications"
            />
            <Button
              label="Mark all seen"
              icon="pi pi-check"
              severity="secondary"
              outlined
              :disabled="unreadCount === 0"
              @click="markAllSeen"
            />
          </div>
        </div>

        <div class="notification-filters">
          <div class="seen-filters">
            <Button
              label="Unread"
              size="small"
              :severity="seenFilter === 'unread' ? 'primary' : 'secondary'"
              :outlined="seenFilter !== 'unread'"
              @click="setSeenFilter('unread')"
            />
            <Button
              label="All"
              size="small"
              :severity="seenFilter === 'all' ? 'primary' : 'secondary'"
              :outlined="seenFilter !== 'all'"
              @click="setSeenFilter('all')"
            />
          </div>
          <Select
            :modelValue="sourceFilter"
            :options="sourceOptions"
            optionLabel="label"
            optionValue="value"
            class="source-filter"
            @update:modelValue="setSourceFilter"
          />
        </div>

        <div class="desktop-only">
          <DataTable
            :value="notifications"
            :loading="loading"
            dataKey="id"
            responsiveLayout="scroll"
            class="notifications-table"
          >
            <Column field="source" header="Source" style="min-width: 150px">
              <template #body="{ data }">
                <Tag
                  :value="notificationDisplay(data).sourceLabel"
                  :severity="notificationDisplay(data).severity"
                  :icon="notificationDisplay(data).icon"
                />
              </template>
            </Column>
            <Column header="Notification" style="min-width: 320px">
              <template #body="{ data }">
                <div class="notification-title">{{ itemTitle(data) }}</div>
                <div v-if="showTypeLabel(data)" class="notification-type">{{ notificationDisplay(data).typeLabel }}</div>
                <div class="notification-message">{{ data.message || 'New notification.' }}</div>
              </template>
            </Column>
            <Column field="occurredAt" header="Time" style="min-width: 180px">
              <template #body="{ data }">
                {{ formatDateTime(data.occurredAt) }}
              </template>
            </Column>
            <Column field="seen" header="Status" style="min-width: 110px">
              <template #body="{ data }">
                <Tag
                  :severity="data.seen ? 'secondary' : 'danger'"
                  :value="data.seen ? 'Seen' : 'Unread'"
                />
              </template>
            </Column>
            <Column header="Actions" :exportable="false" style="min-width: 240px">
              <template #body="{ data }">
                <div class="table-actions">
                  <Button
                    :label="notificationDisplay(data).actionLabel"
                    icon="pi pi-external-link"
                    size="small"
                    text
                    @click="openNotification(data)"
                  />
                  <Button
                    v-if="!data.seen"
                    label="Mark seen"
                    icon="pi pi-check"
                    size="small"
                    text
                    @click="markSeen(data)"
                  />
                </div>
              </template>
            </Column>
            <template #empty>
              <div class="empty-state">{{ emptyMessage }}</div>
            </template>
          </DataTable>
          <Paginator
            v-if="totalCount > pageSize"
            :first="page * pageSize"
            :rows="pageSize"
            :totalRecords="totalCount"
            :rowsPerPageOptions="[10, 25, 50, 100]"
            @page="handlePageChange"
          />
        </div>

        <div class="mobile-only">
          <div v-if="loading" class="mobile-loading">
            <i class="pi pi-spin pi-spinner"></i>
          </div>
          <div v-else-if="notifications.length === 0" class="empty-state">
            {{ emptyMessage }}
          </div>
          <div v-else class="notification-cards">
            <div
              v-for="item in notifications"
              :key="item.id"
              class="notification-card"
              :class="{ 'notification-card--unread': !item.seen }"
            >
              <div class="notification-card-header">
                <Tag
                  :value="notificationDisplay(item).sourceLabel"
                  :severity="notificationDisplay(item).severity"
                  :icon="notificationDisplay(item).icon"
                />
                <Tag
                  :severity="item.seen ? 'secondary' : 'danger'"
                  :value="item.seen ? 'Seen' : 'Unread'"
                />
              </div>
              <div class="notification-title">{{ itemTitle(item) }}</div>
              <div v-if="showTypeLabel(item)" class="notification-type">{{ notificationDisplay(item).typeLabel }}</div>
              <div class="notification-message">{{ item.message || 'New notification.' }}</div>
              <div class="notification-time">{{ formatDateTime(item.occurredAt) }}</div>
              <div class="card-actions">
                <Button
                  :label="notificationDisplay(item).actionLabel"
                  icon="pi pi-external-link"
                  size="small"
                  text
                  @click="openNotification(item)"
                />
                <Button
                  v-if="!item.seen"
                  label="Mark seen"
                  icon="pi pi-check"
                  size="small"
                  text
                  @click="markSeen(item)"
                />
              </div>
            </div>
          </div>
          <Paginator
            v-if="totalCount > pageSize"
            :first="page * pageSize"
            :rows="pageSize"
            :totalRecords="totalCount"
            :rowsPerPageOptions="[10, 25, 50, 100]"
            @page="handlePageChange"
          />
        </div>
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import Paginator from 'primevue/paginator'
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import { useTimezone } from '@/composables/useTimezone'
import { useNotificationsStore } from '@/stores/notifications'

const toast = useToast()
const timezone = useTimezone()
const notificationsStore = useNotificationsStore()
const { unreadCount } = storeToRefs(notificationsStore)

const loading = ref(false)
const notifications = ref([])
const totalCount = ref(0)
const page = ref(0)
const pageSize = ref(25)
const seenFilter = ref('unread')
const sourceFilter = ref('ALL')

const sourceOptions = [
  { label: 'All sources', value: 'ALL' },
  { label: 'Geofence', value: 'GEOFENCE' },
  { label: 'Timeline', value: 'TIMELINE' },
  { label: 'Import', value: 'IMPORT' },
  { label: 'Export', value: 'EXPORT' },
  { label: 'Friends', value: 'FRIEND_INVITE' }
]

const emptyMessage = computed(() => (
  seenFilter.value === 'unread' ? 'No unread notifications.' : 'No notifications found.'
))

const loadNotifications = async () => {
  loading.value = true
  try {
    const response = await notificationsStore.fetchNotificationsPage({
      page: page.value,
      pageSize: pageSize.value,
      seen: seenFilter.value === 'unread' ? false : null,
      source: sourceFilter.value === 'ALL' ? null : sourceFilter.value
    })
    notifications.value = Array.isArray(response.items) ? response.items : []
    totalCount.value = Number(response.totalCount || 0)
    page.value = Number(response.page || 0)
    pageSize.value = Number(response.pageSize || pageSize.value)
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Notification Error',
      detail: extractApiErrorMessage(error, 'Failed to load notifications'),
      life: 5000
    })
  } finally {
    loading.value = false
  }
}

const setSeenFilter = (value) => {
  if (seenFilter.value === value) {
    return
  }
  seenFilter.value = value
  page.value = 0
  loadNotifications()
}

const setSourceFilter = (value) => {
  sourceFilter.value = value || 'ALL'
  page.value = 0
  loadNotifications()
}

const handlePageChange = (event) => {
  page.value = event.page
  pageSize.value = event.rows
  loadNotifications()
}

const markSeen = async (item) => {
  try {
    await notificationsStore.markSeen(item.id)
    await notificationsStore.refresh({
      emitToasts: false,
      emitBrowser: false,
      emitStartupSummary: false
    })
    await loadNotifications()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Notification Error',
      detail: extractApiErrorMessage(error, 'Failed to mark notification as seen'),
      life: 5000
    })
  }
}

const markAllSeen = async () => {
  try {
    await notificationsStore.markAllSeen()
    page.value = 0
    await loadNotifications()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Notification Error',
      detail: extractApiErrorMessage(error, 'Failed to mark all notifications as seen'),
      life: 5000
    })
  }
}

const openNotification = (item) => {
  notificationsStore.openNotification(item)
}

const notificationDisplay = (item) => {
  return notificationsStore.notificationDisplay(item)
}

const itemTitle = (item) => {
  return notificationDisplay(item).title || 'Notification'
}

const showTypeLabel = (item) => {
  const display = notificationDisplay(item)
  const title = String(display.title || '').trim().toLowerCase()
  const typeLabel = String(display.typeLabel || '').trim().toLowerCase()
  return !!typeLabel && typeLabel !== title
}

const formatDateTime = (value) => {
  if (!value) {
    return '-'
  }
  return `${timezone.formatDateDisplay(value)} ${timezone.formatTime(value, { withSeconds: true })}`
}

const extractApiErrorMessage = (error, fallback) => (
  error?.response?.data?.message
  || error?.response?.data?.error
  || error?.response?.data?.data?.message
  || error?.userMessage
  || error?.message
  || fallback
)

onMounted(() => {
  loadNotifications()
})
</script>

<style scoped>
.notifications-page {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  width: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}

.page-header h1 {
  margin: 0;
  font-size: 1.75rem;
  color: var(--gp-text-primary);
}

.page-header p {
  margin: 0.35rem 0 0;
  color: var(--gp-text-secondary);
}

.page-actions,
.notification-filters,
.seen-filters,
.table-actions,
.card-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.page-actions {
  justify-content: flex-end;
}

.notification-filters {
  justify-content: space-between;
  border: 1px solid var(--gp-border-light);
  border-radius: 8px;
  padding: 0.75rem;
  background: var(--gp-surface-white);
}

.source-filter {
  min-width: 180px;
}

.desktop-only {
  display: block;
}

.mobile-only {
  display: none;
}

.notifications-table {
  border: 1px solid var(--gp-border-light);
  border-radius: 8px;
  overflow: hidden;
}

.notification-title {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-weight: 700;
  color: var(--gp-text-primary);
  overflow-wrap: anywhere;
}

.notification-type {
  margin-top: 0.2rem;
  color: var(--gp-text-muted);
  font-size: 0.8rem;
}

.notification-message {
  margin-top: 0.25rem;
  color: var(--gp-text-secondary);
  font-size: 0.9rem;
  overflow-wrap: anywhere;
}

.notification-time {
  color: var(--gp-text-muted);
  font-size: 0.8rem;
}

.empty-state,
.mobile-loading {
  border: 1px dashed var(--gp-border-light);
  border-radius: 8px;
  color: var(--gp-text-secondary);
  padding: 1rem;
  text-align: center;
  background: var(--gp-surface-white);
}

.mobile-loading i {
  font-size: 1.5rem;
}

.notification-cards {
  display: grid;
  gap: 0.75rem;
}

.notification-card {
  border: 1px solid var(--gp-border-light);
  border-radius: 8px;
  padding: 0.85rem;
  background: var(--gp-surface-white);
}

.notification-card--unread {
  border-color: var(--gp-primary);
  background: color-mix(in srgb, var(--gp-primary) 6%, var(--gp-surface-white));
}

.notification-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.7rem;
}

.card-actions {
  justify-content: flex-end;
  margin-top: 0.7rem;
}

:deep(.p-tag) {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-weight: 700;
}

:deep(.p-tag.p-tag-warning) {
  background: var(--gp-warning-light);
  color: var(--gp-warning-dark);
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
  }

  .page-actions,
  .notification-filters {
    width: 100%;
  }

  .page-actions :deep(.p-button),
  .source-filter {
    flex: 1;
  }

  .desktop-only {
    display: none;
  }

  .mobile-only {
    display: block;
  }
}
</style>

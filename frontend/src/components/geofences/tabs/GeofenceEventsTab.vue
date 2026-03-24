<template>
  <div class="tab-panel">
    <BaseCard class="panel-card">
      <div class="table-header">
        <div class="table-header-left">
          <h3>Events</h3>
          <Tag v-if="unreadCount > 0" :value="`${unreadCount} unread`" severity="danger" />
        </div>
        <div class="table-header-actions">
          <div class="inline-toggle">
            <label for="unreadOnlyToggle">Unread only</label>
            <InputSwitch
              inputId="unreadOnlyToggle"
              :modelValue="query.unreadOnly"
              @update:modelValue="onUnreadOnlyChange"
            />
          </div>
          <Button
            icon="pi pi-check"
            label="Mark all seen"
            severity="secondary"
            outlined
            :disabled="unreadCount === 0"
            :loading="markingAllSeen"
            @click="$emit('mark-all-events-seen')"
          />
          <Button
            icon="pi pi-refresh"
            label="Refresh"
            severity="secondary"
            outlined
            :loading="loading"
            @click="$emit('refresh-events')"
          />
        </div>
      </div>

      <div class="filters-row">
        <div class="filter-item filter-item--preset">
          <label>Date</label>
          <SelectButton
            :modelValue="query.datePreset || 'all'"
            :options="datePresetOptions"
            optionLabel="label"
            optionValue="value"
            @update:modelValue="onDatePresetChange"
          />
        </div>

        <div v-if="query.datePreset === 'custom'" class="filter-item filter-item--date-range">
          <label>From</label>
          <DatePicker
            v-model="customDateFrom"
            showTime
            hourFormat="24"
            dateFormat="yy-mm-dd"
            @update:modelValue="onCustomDateChange"
          />
        </div>

        <div v-if="query.datePreset === 'custom'" class="filter-item filter-item--date-range">
          <label>To</label>
          <DatePicker
            v-model="customDateTo"
            showTime
            hourFormat="24"
            dateFormat="yy-mm-dd"
            @update:modelValue="onCustomDateChange"
          />
        </div>

        <div class="filter-item filter-item--subjects">
          <label>Subject</label>
          <MultiSelect
            :modelValue="query.subjectUserIds"
            :options="subjectFilterOptions"
            optionLabel="label"
            optionValue="value"
            placeholder="All subjects"
            display="chip"
            :maxSelectedLabels="2"
            @update:modelValue="onSubjectFilterChange"
          />
        </div>

        <div class="filter-item filter-item--events">
          <label>Event</label>
          <MultiSelect
            :modelValue="query.eventTypes"
            :options="eventTypeOptions"
            optionLabel="label"
            optionValue="value"
            placeholder="All events"
            display="chip"
            :maxSelectedLabels="2"
            @update:modelValue="onEventTypesChange"
          />
        </div>

        <div class="filter-item filter-item--columns">
          <label>Extra columns</label>
          <MultiSelect
            v-model="detailColumns"
            :options="detailColumnOptions"
            optionLabel="label"
            optionValue="value"
            placeholder="Title / Message"
            display="chip"
            :maxSelectedLabels="2"
            @update:modelValue="persistColumnPreference"
          />
        </div>
      </div>

      <div v-if="!isMobile" class="events-desktop">
        <DataTable
          :value="events"
          dataKey="id"
          :lazy="true"
          :loading="loading"
          :paginator="true"
          :rows="query.pageSize"
          :first="query.page * query.pageSize"
          :totalRecords="totalRecords"
          :rowsPerPageOptions="[25, 50, 100]"
          sortMode="single"
          :sortField="query.sortBy"
          :sortOrder="query.sortDir === 'asc' ? 1 : -1"
          v-model:expandedRows="expandedRows"
          @page="onPage"
          @sort="onSort"
        >
          <Column expander style="width: 3rem" />

          <Column field="occurredAt" header="Time" :sortable="true">
            <template #body="slotProps">
              {{ formatDate(slotProps.data.occurredAt) }}
            </template>
          </Column>

          <Column field="subjectDisplayName" header="Subject" :sortable="true" />

          <Column field="eventType" header="Event" :sortable="true">
            <template #body="slotProps">
              <Tag :value="slotProps.data.eventType" :severity="slotProps.data.eventType === 'ENTER' ? 'success' : 'warn'" />
            </template>
          </Column>

          <Column field="ruleName" header="Rule" />

          <Column v-if="showTitleColumn" field="title" header="Title" />

          <Column v-if="showMessageColumn" field="message" header="Message">
            <template #body="slotProps">
              <span class="message-preview">{{ slotProps.data.message || '-' }}</span>
            </template>
          </Column>

          <Column field="deliveryStatus" header="Delivery">
            <template #body="slotProps">
              <Tag :value="slotProps.data.deliveryStatus" :severity="deliverySeverity(slotProps.data.deliveryStatus)" />
            </template>
          </Column>

          <Column field="seenAt" header="Seen">
            <template #body="slotProps">
              <Tag
                :value="slotProps.data.seen ? 'Seen' : 'New'"
                :severity="slotProps.data.seen ? 'secondary' : 'danger'"
              />
            </template>
          </Column>

          <Column header="Actions">
            <template #body="slotProps">
              <Button
                v-if="!slotProps.data.seen"
                icon="pi pi-check"
                label="Mark seen"
                size="small"
                severity="secondary"
                outlined
                :loading="markingEventId === slotProps.data.id"
                @click="$emit('mark-event-seen', slotProps.data)"
              />
            </template>
          </Column>

          <template #expansion="slotProps">
            <div class="event-details-panel">
              <div class="detail-row">
                <strong>Title:</strong>
                <span>{{ slotProps.data.title || '-' }}</span>
              </div>
              <div class="detail-row">
                <strong>Message:</strong>
                <span>{{ slotProps.data.message || '-' }}</span>
              </div>
              <div class="detail-grid">
                <div class="detail-item"><strong>Rule</strong><span>{{ slotProps.data.ruleName || '-' }}</span></div>
                <div class="detail-item"><strong>Subject</strong><span>{{ slotProps.data.subjectDisplayName || '-' }}</span></div>
                <div class="detail-item"><strong>Event</strong><span>{{ slotProps.data.eventType || '-' }}</span></div>
                <div class="detail-item"><strong>Delivery</strong><span>{{ slotProps.data.deliveryStatus || '-' }}</span></div>
                <div class="detail-item"><strong>Occurred</strong><span>{{ formatDate(slotProps.data.occurredAt) }}</span></div>
                <div class="detail-item"><strong>Seen At</strong><span>{{ slotProps.data.seenAt ? formatDate(slotProps.data.seenAt) : 'Not seen' }}</span></div>
                <div class="detail-item"><strong>Point ID</strong><span>{{ slotProps.data.pointId || '-' }}</span></div>
                <div class="detail-item"><strong>Lat / Lon</strong><span>{{ formatLatLon(slotProps.data.pointLat, slotProps.data.pointLon) }}</span></div>
              </div>
              <Button
                v-if="!slotProps.data.seen"
                icon="pi pi-check"
                label="Mark seen"
                size="small"
                severity="secondary"
                outlined
                :loading="markingEventId === slotProps.data.id"
                @click="$emit('mark-event-seen', slotProps.data)"
              />
            </div>
          </template>

          <template #empty>
            <div class="empty-state">
              <p v-if="hasActiveFilters">No events match the current filters.</p>
              <p v-else>No geofence events yet.</p>
            </div>
          </template>
        </DataTable>
      </div>

      <div v-else class="events-mobile">
        <div v-if="loading" class="mobile-loading">
          <ProgressSpinner style="width: 34px; height: 34px" strokeWidth="6" />
        </div>
        <div v-else-if="events.length === 0" class="empty-state">
          <p v-if="hasActiveFilters">No events match the current filters.</p>
          <p v-else>No geofence events yet.</p>
        </div>
        <div v-else class="event-card-list">
          <article v-for="event in events" :key="event.id" class="event-card" :class="{ 'event-card--unseen': !event.seen }">
            <header class="event-card-header">
              <div>
                <h4>{{ event.ruleName || 'Geofence Event' }}</h4>
                <small>{{ formatDate(event.occurredAt) }}</small>
              </div>
              <Tag :value="event.seen ? 'Seen' : 'New'" :severity="event.seen ? 'secondary' : 'danger'" />
            </header>

            <div class="event-card-meta">
              <Tag :value="event.eventType" :severity="event.eventType === 'ENTER' ? 'success' : 'warn'" />
              <Tag :value="event.deliveryStatus" :severity="deliverySeverity(event.deliveryStatus)" />
              <span class="subject-chip">{{ event.subjectDisplayName || '-' }}</span>
            </div>

            <div class="event-card-actions">
              <Button
                :label="expandedCardIds.includes(event.id) ? 'Hide details' : 'View details'"
                severity="secondary"
                outlined
                size="small"
                @click="toggleCardDetails(event.id)"
              />
              <Button
                v-if="!event.seen"
                icon="pi pi-check"
                label="Mark seen"
                size="small"
                severity="secondary"
                outlined
                class="mobile-mark-seen"
                :loading="markingEventId === event.id"
                @click="$emit('mark-event-seen', event)"
              />
            </div>

            <div v-if="expandedCardIds.includes(event.id)" class="event-card-details">
              <p><strong>Title:</strong> {{ event.title || '-' }}</p>
              <p><strong>Message:</strong> {{ event.message || '-' }}</p>
              <p><strong>Subject:</strong> {{ event.subjectDisplayName || '-' }}</p>
              <p><strong>Event:</strong> {{ event.eventType || '-' }}</p>
              <p><strong>Delivery:</strong> {{ event.deliveryStatus || '-' }}</p>
              <p><strong>Seen At:</strong> {{ event.seenAt ? formatDate(event.seenAt) : 'Not seen' }}</p>
              <p><strong>Point ID:</strong> {{ event.pointId || '-' }}</p>
              <p><strong>Lat / Lon:</strong> {{ formatLatLon(event.pointLat, event.pointLon) }}</p>
            </div>
          </article>
        </div>

        <div v-if="events.length > 0" class="mobile-pagination">
          <Button
            icon="pi pi-angle-left"
            label="Prev"
            severity="secondary"
            outlined
            :disabled="query.page === 0 || loading"
            @click="onMobilePageChange(-1)"
          />
          <span>Page {{ query.page + 1 }} / {{ totalPages }}</span>
          <Button
            icon="pi pi-angle-right"
            iconPos="right"
            label="Next"
            severity="secondary"
            outlined
            :disabled="query.page + 1 >= totalPages || loading"
            @click="onMobilePageChange(1)"
          />
        </div>
      </div>
    </BaseCard>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import InputSwitch from 'primevue/inputswitch'
import Button from 'primevue/button'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'
import MultiSelect from 'primevue/multiselect'
import SelectButton from 'primevue/selectbutton'
import DatePicker from 'primevue/datepicker'
import ProgressSpinner from 'primevue/progressspinner'

const props = defineProps({
  events: {
    type: Array,
    required: true
  },
  totalRecords: {
    type: Number,
    required: true
  },
  query: {
    type: Object,
    required: true
  },
  subjectFilterOptions: {
    type: Array,
    default: () => []
  },
  unreadCount: {
    type: Number,
    required: true
  },
  loading: {
    type: Boolean,
    required: true
  },
  markingAllSeen: {
    type: Boolean,
    required: true
  },
  markingEventId: {
    type: Number,
    default: null
  },
  formatDate: {
    type: Function,
    required: true
  },
  deliverySeverity: {
    type: Function,
    required: true
  },
  userId: {
    type: [String, Number],
    default: null
  }
})

const emit = defineEmits([
  'update-query',
  'mark-all-events-seen',
  'refresh-events',
  'mark-event-seen'
])

const datePresetOptions = [
  { label: 'All', value: 'all' },
  { label: '24h', value: '24h' },
  { label: '7d', value: '7d' },
  { label: '30d', value: '30d' },
  { label: 'Custom', value: 'custom' }
]

const eventTypeOptions = [
  { label: 'Enter', value: 'ENTER' },
  { label: 'Leave', value: 'LEAVE' }
]

const detailColumnOptions = [
  { label: 'Title', value: 'title' },
  { label: 'Message', value: 'message' }
]

const expandedRows = ref([])
const expandedCardIds = ref([])
const detailColumns = ref([])
const isMobile = ref(false)
const customDateFrom = ref(null)
const customDateTo = ref(null)

const totalPages = computed(() => {
  const pageSize = Math.max(1, Number(props.query.pageSize || 25))
  return Math.max(1, Math.ceil(Number(props.totalRecords || 0) / pageSize))
})

const hasActiveFilters = computed(() => {
  return Boolean(
    props.query.unreadOnly
      || props.query.dateFrom
      || props.query.dateTo
      || (Array.isArray(props.query.subjectUserIds) && props.query.subjectUserIds.length > 0)
      || (Array.isArray(props.query.eventTypes) && props.query.eventTypes.length > 0)
  )
})

const showTitleColumn = computed(() => detailColumns.value.includes('title'))
const showMessageColumn = computed(() => detailColumns.value.includes('message'))

const columnPreferenceKey = computed(() => `gp.geofence.events.columns.${props.userId || 'anonymous'}`)

function updateMobileFlag() {
  if (typeof window === 'undefined') {
    isMobile.value = false
    return
  }
  isMobile.value = window.innerWidth <= 768
}

function persistColumnPreference() {
  if (typeof window === 'undefined') {
    return
  }
  window.localStorage.setItem(columnPreferenceKey.value, JSON.stringify(detailColumns.value))
}

function loadColumnPreference() {
  if (typeof window === 'undefined') {
    return
  }
  try {
    const raw = window.localStorage.getItem(columnPreferenceKey.value)
    if (!raw) {
      detailColumns.value = []
      return
    }
    const parsed = JSON.parse(raw)
    detailColumns.value = Array.isArray(parsed)
      ? parsed.filter(column => detailColumnOptions.some(option => option.value === column))
      : []
  } catch {
    detailColumns.value = []
  }
}

function onUnreadOnlyChange(value) {
  emit('update-query', {
    unreadOnly: !!value,
    page: 0
  })
}

function onDatePresetChange(value) {
  const now = new Date()

  if (value === 'all') {
    customDateFrom.value = null
    customDateTo.value = null
    emit('update-query', {
      datePreset: 'all',
      dateFrom: null,
      dateTo: null,
      page: 0
    })
    return
  }

  if (value === 'custom') {
    emit('update-query', {
      datePreset: 'custom',
      page: 0
    })
    return
  }

  const from = new Date(now)
  if (value === '24h') {
    from.setHours(from.getHours() - 24)
  }
  if (value === '7d') {
    from.setDate(from.getDate() - 7)
  }
  if (value === '30d') {
    from.setDate(from.getDate() - 30)
  }

  customDateFrom.value = from
  customDateTo.value = now

  emit('update-query', {
    datePreset: value,
    dateFrom: from.toISOString(),
    dateTo: now.toISOString(),
    page: 0
  })
}

function onCustomDateChange() {
  emit('update-query', {
    datePreset: 'custom',
    dateFrom: customDateFrom.value ? new Date(customDateFrom.value).toISOString() : null,
    dateTo: customDateTo.value ? new Date(customDateTo.value).toISOString() : null,
    page: 0
  })
}

function onSubjectFilterChange(values) {
  emit('update-query', {
    subjectUserIds: Array.isArray(values) ? values : [],
    page: 0
  })
}

function onEventTypesChange(values) {
  emit('update-query', {
    eventTypes: Array.isArray(values) ? values : [],
    page: 0
  })
}

function onPage(event) {
  emit('update-query', {
    page: Number(event?.page || 0),
    pageSize: Number(event?.rows || props.query.pageSize || 25)
  })
}

function onSort(event) {
  emit('update-query', {
    sortBy: event.sortField || 'occurredAt',
    sortDir: event.sortOrder === 1 ? 'asc' : 'desc',
    page: 0
  })
}

function toggleCardDetails(eventId) {
  const existing = new Set(expandedCardIds.value)
  if (existing.has(eventId)) {
    existing.delete(eventId)
  } else {
    existing.add(eventId)
  }
  expandedCardIds.value = Array.from(existing)
}

function onMobilePageChange(delta) {
  const nextPage = Math.max(0, Number(props.query.page || 0) + delta)
  if (nextPage === props.query.page) {
    return
  }
  emit('update-query', {
    page: nextPage
  })
}

function formatLatLon(lat, lon) {
  if (lat === null || lat === undefined || lon === null || lon === undefined) {
    return '-'
  }
  return `${Number(lat).toFixed(6)}, ${Number(lon).toFixed(6)}`
}

watch(
  () => [props.query.dateFrom, props.query.dateTo],
  ([dateFrom, dateTo]) => {
    customDateFrom.value = dateFrom ? new Date(dateFrom) : null
    customDateTo.value = dateTo ? new Date(dateTo) : null
  },
  { immediate: true }
)

watch(
  () => props.userId,
  () => {
    loadColumnPreference()
  }
)

onMounted(() => {
  updateMobileFlag()
  loadColumnPreference()
  if (typeof window !== 'undefined') {
    window.addEventListener('resize', updateMobileFlag)
  }
})

onUnmounted(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', updateMobileFlag)
  }
})
</script>

<style scoped>
.tab-panel {
  display: grid;
  gap: 1rem;
  padding: 1rem;
}

.panel-card {
  padding: 1rem;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
  gap: 0.75rem;
}

.table-header-left {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.table-header-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.inline-toggle {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.filters-row {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 0.75rem;
  margin-bottom: 0.9rem;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.filter-item > label {
  font-size: 0.85rem;
  font-weight: 600;
}

.filter-item--preset {
  grid-column: span 4;
}

.filter-item--date-range {
  grid-column: span 2;
}

.filter-item--subjects,
.filter-item--events,
.filter-item--columns {
  grid-column: span 2;
}

:deep(.p-selectbutton) {
  display: flex;
  flex-wrap: wrap;
}

.message-preview {
  display: inline-block;
  max-width: 360px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.event-details-panel {
  padding: 0.5rem 0;
  display: grid;
  gap: 0.75rem;
}

.detail-row {
  display: grid;
  grid-template-columns: 80px 1fr;
  gap: 0.5rem;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.5rem;
}

.detail-item {
  display: grid;
  gap: 0.25rem;
}

.detail-item > strong {
  font-size: 0.8rem;
}

.empty-state {
  text-align: center;
  color: var(--text-color-secondary, #667085);
  padding: 1rem 0.5rem;
}

.event-card-list {
  display: grid;
  gap: 0.75rem;
}

.event-card {
  border: 1px solid var(--surface-border, #d0d5dd);
  border-radius: 0.75rem;
  padding: 0.75rem;
  display: grid;
  gap: 0.6rem;
}

.event-card--unseen {
  border-color: color-mix(in srgb, var(--red-500, #ef4444) 48%, var(--surface-border, #d0d5dd));
}

.event-card-header {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
}

.event-card-header h4 {
  margin: 0;
  font-size: 0.95rem;
}

.event-card-header small {
  color: var(--text-color-secondary, #667085);
}

.event-card-meta {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  flex-wrap: wrap;
}

.subject-chip {
  background: var(--surface-100, #f3f4f6);
  border-radius: 999px;
  padding: 0.12rem 0.5rem;
  font-size: 0.8rem;
}

.event-card-actions {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0.45rem;
}

.mobile-mark-seen :deep(.p-button) {
  width: 100%;
}

.event-card-details {
  border-top: 1px dashed var(--surface-border, #d0d5dd);
  padding-top: 0.5rem;
  display: grid;
  gap: 0.25rem;
  font-size: 0.9rem;
}

.event-card-details p {
  margin: 0;
}

.mobile-pagination {
  margin-top: 0.75rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
}

.mobile-loading {
  display: grid;
  place-items: center;
  padding: 1.25rem 0;
}

@media (max-width: 1024px) {
  .filter-item--preset {
    grid-column: span 12;
  }

  .filter-item--date-range,
  .filter-item--subjects,
  .filter-item--events,
  .filter-item--columns {
    grid-column: span 6;
  }
}

@media (max-width: 768px) {
  .tab-panel {
    padding: 0.5rem;
  }

  .panel-card {
    padding: 0.75rem;
  }

  .table-header {
    flex-direction: column;
    align-items: stretch;
  }

  .table-header-left {
    justify-content: space-between;
  }

  .table-header-actions {
    display: grid;
    grid-template-columns: 1fr;
  }

  .inline-toggle {
    justify-content: space-between;
  }

  .table-header-actions :deep(.p-button) {
    width: 100%;
    justify-content: center;
  }

  .filter-item--date-range,
  .filter-item--subjects,
  .filter-item--events,
  .filter-item--columns {
    grid-column: span 12;
  }
}
</style>

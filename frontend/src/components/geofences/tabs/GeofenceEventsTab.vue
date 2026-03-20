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
              :modelValue="unreadOnly"
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
          <Button icon="pi pi-refresh" label="Refresh" severity="secondary" outlined @click="$emit('refresh-events')" />
        </div>
      </div>
      <DataTable :value="events" dataKey="id" responsiveLayout="stack" breakpoint="768px">
        <Column field="occurredAt" header="Time">
          <template #body="slotProps">
            {{ formatDate(slotProps.data.occurredAt) }}
          </template>
        </Column>
        <Column field="title" header="Title" />
        <Column field="subjectDisplayName" header="Subject" />
        <Column field="ruleName" header="Rule" />
        <Column field="eventType" header="Event" />
        <Column field="message" header="Message" />
        <Column header="Seen">
          <template #body="slotProps">
            <Tag
              :value="slotProps.data.seen ? 'Seen' : 'New'"
              :severity="slotProps.data.seen ? 'secondary' : 'danger'"
            />
          </template>
        </Column>
        <Column field="deliveryStatus" header="Delivery">
          <template #body="slotProps">
            <Tag :value="slotProps.data.deliveryStatus" :severity="deliverySeverity(slotProps.data.deliveryStatus)" />
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
      </DataTable>
    </BaseCard>
  </div>
</template>

<script setup>
import BaseCard from '@/components/ui/base/BaseCard.vue'
import InputSwitch from 'primevue/inputswitch'
import Button from 'primevue/button'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'

defineProps({
  events: {
    type: Array,
    required: true
  },
  unreadCount: {
    type: Number,
    required: true
  },
  unreadOnly: {
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
  }
})

const emit = defineEmits([
  'toggle-unread-only',
  'mark-all-events-seen',
  'refresh-events',
  'mark-event-seen'
])

function onUnreadOnlyChange(value) {
  emit('toggle-unread-only', value)
}
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
    gap: 0.75rem;
  }

  .table-header-left {
    justify-content: space-between;
  }

  .table-header-actions {
    display: grid;
    grid-template-columns: 1fr;
    gap: 0.5rem;
  }

  .inline-toggle {
    justify-content: space-between;
  }

  .table-header-actions :deep(.p-button) {
    width: 100%;
    justify-content: center;
  }

  :deep(.p-datatable.p-datatable-responsive-stack .p-datatable-tbody > tr > td) {
    text-align: left;
    align-items: flex-start;
    gap: 0.5rem;
  }

  :deep(.p-datatable.p-datatable-responsive-stack .p-column-title) {
    font-weight: 600;
  }
}
</style>

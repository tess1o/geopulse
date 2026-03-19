<template>
  <AppLayout variant="default">
    <PageContainer
      title="Geofences"
      subtitle="Monitor enter/leave events for you and friends"
      variant="fullwidth"
    >
      <TabContainer
        :tabs="tabs"
        :activeIndex="activeTabIndex"
        @tab-change="onTabChange"
      >
        <div v-if="activeTab === 'rules'" class="tab-panel">
          <BaseCard class="panel-card">
            <h3>{{ editingRuleId ? 'Edit Rule' : 'Create Rule' }}</h3>
            <div class="form-grid">
              <div class="field">
                <label>Name</label>
                <InputText v-model="ruleForm.name" placeholder="Home area" />
              </div>

              <div class="field">
                <label>Subject</label>
                <Select
                  v-model="ruleForm.subjectUserId"
                  :options="subjectOptions"
                  optionLabel="label"
                  optionValue="value"
                  placeholder="Select subject"
                />
              </div>

              <div class="field wide">
                <label>Area Picker</label>
                <div class="map-picker-toolbar">
                  <Button
                    label="Draw Rectangle on Map"
                    icon="pi pi-pencil"
                    severity="secondary"
                    outlined
                    @click="startRectangleDraw"
                  />
                </div>
                <div class="map-picker">
                  <BaseMap
                    mapId="geofence-rule-map"
                    :center="mapCenter"
                    :zoom="mapZoom"
                    height="260px"
                    width="100%"
                    @map-ready="handleMapReady"
                  />
                </div>
              </div>

              <div class="field">
                <label>North-East Latitude</label>
                <InputNumber v-model="ruleForm.northEastLat" :min="-90" :max="90" :maxFractionDigits="6" />
              </div>

              <div class="field">
                <label>North-East Longitude</label>
                <InputNumber v-model="ruleForm.northEastLon" :min="-180" :max="180" :maxFractionDigits="6" />
              </div>

              <div class="field">
                <label>South-West Latitude</label>
                <InputNumber v-model="ruleForm.southWestLat" :min="-90" :max="90" :maxFractionDigits="6" />
              </div>

              <div class="field">
                <label>South-West Longitude</label>
                <InputNumber v-model="ruleForm.southWestLon" :min="-180" :max="180" :maxFractionDigits="6" />
              </div>

              <div class="field toggle-field">
                <label>Monitor Enter</label>
                <InputSwitch v-model="ruleForm.monitorEnter" />
              </div>

              <div class="field toggle-field">
                <label>Monitor Leave</label>
                <InputSwitch v-model="ruleForm.monitorLeave" />
              </div>

              <div class="field">
                <label>Cooldown (seconds)</label>
                <InputNumber v-model="ruleForm.cooldownSeconds" :min="0" />
              </div>

              <div class="field">
                <label>Enter Template</label>
                <Select
                  v-model="ruleForm.enterTemplateId"
                  :options="templateOptions"
                  optionLabel="label"
                  optionValue="value"
                  placeholder="Default"
                />
              </div>

              <div class="field">
                <label>Leave Template</label>
                <Select
                  v-model="ruleForm.leaveTemplateId"
                  :options="templateOptions"
                  optionLabel="label"
                  optionValue="value"
                  placeholder="Default"
                />
              </div>

              <div class="field">
                <label>Status</label>
                <Select
                  v-model="ruleForm.status"
                  :options="statusOptions"
                  optionLabel="label"
                  optionValue="value"
                />
              </div>
            </div>

            <div class="actions-row">
              <Button
                :label="editingRuleId ? 'Update Rule' : 'Create Rule'"
                icon="pi pi-save"
                @click="saveRule"
                :loading="savingRule"
              />
              <Button
                v-if="editingRuleId"
                label="Cancel"
                severity="secondary"
                outlined
                @click="resetRuleForm"
              />
            </div>
          </BaseCard>

          <BaseCard class="panel-card">
            <div class="table-header">
              <h3>Rules</h3>
              <Button icon="pi pi-refresh" label="Refresh" severity="secondary" outlined @click="loadRules" />
            </div>
            <DataTable :value="rules" dataKey="id" responsiveLayout="scroll">
              <Column field="name" header="Name" />
              <Column field="subjectDisplayName" header="Subject" />
              <Column header="Events">
                <template #body="slotProps">
                  <span>{{ eventSummary(slotProps.data) }}</span>
                </template>
              </Column>
              <Column field="cooldownSeconds" header="Cooldown" />
              <Column field="status" header="Status">
                <template #body="slotProps">
                  <Tag :value="slotProps.data.status" :severity="slotProps.data.status === 'ACTIVE' ? 'success' : 'secondary'" />
                </template>
              </Column>
              <Column header="Actions">
                <template #body="slotProps">
                  <div class="row-actions">
                    <Button icon="pi pi-pencil" text @click="editRule(slotProps.data)" />
                    <Button icon="pi pi-trash" text severity="danger" @click="deleteRule(slotProps.data)" />
                  </div>
                </template>
              </Column>
            </DataTable>
          </BaseCard>
        </div>

        <div v-else-if="activeTab === 'templates'" class="tab-panel">
          <BaseCard class="panel-card">
            <h3>{{ editingTemplateId ? 'Edit Template' : 'Create Template' }}</h3>
            <div class="form-grid">
              <div class="field">
                <label>Name</label>
                <InputText v-model="templateForm.name" placeholder="Telegram Enter Alert" />
              </div>
              <div class="field wide">
                <label>Destination URL(s) (optional)</label>
                <InputText v-model="templateForm.destination" placeholder="tgram://TOKEN/CHAT_ID" />
                <small class="muted-text">Leave empty for in-app notifications only (no Apprise delivery).</small>
              </div>
              <div class="field wide">
                <label>Title Template</label>
                <InputText v-model="templateForm.titleTemplate" placeholder="{{eventType}}: {{geofenceName}}" />
              </div>
              <div class="field wide">
                <label>Body Template</label>
                <Textarea v-model="templateForm.bodyTemplate" rows="4" autoResize />
              </div>
              <div class="field wide">
                <div class="macro-help">
                  <div class="macro-help-title">Available macros</div>
                  <div class="macro-grid">
                    <div v-for="macro in templateMacros" :key="macro.key" class="macro-item">
                      <code>{{ macro.key }}</code>
                      <span>{{ macro.description }}</span>
                    </div>
                  </div>
                </div>
              </div>
              <div class="field toggle-field">
                <label>Default for Enter</label>
                <InputSwitch v-model="templateForm.defaultForEnter" />
              </div>
              <div class="field toggle-field">
                <label>Default for Leave</label>
                <InputSwitch v-model="templateForm.defaultForLeave" />
              </div>
              <div class="field toggle-field">
                <label>Enabled</label>
                <InputSwitch v-model="templateForm.enabled" />
              </div>
            </div>

            <div class="actions-row">
              <Button
                :label="editingTemplateId ? 'Update Template' : 'Create Template'"
                icon="pi pi-save"
                @click="saveTemplate"
                :loading="savingTemplate"
              />
              <Button
                v-if="editingTemplateId"
                label="Cancel"
                severity="secondary"
                outlined
                @click="resetTemplateForm"
              />
            </div>
          </BaseCard>

          <BaseCard class="panel-card">
            <div class="table-header">
              <h3>Templates</h3>
              <Button icon="pi pi-refresh" label="Refresh" severity="secondary" outlined @click="loadTemplates" />
            </div>
            <DataTable :value="templates" dataKey="id" responsiveLayout="scroll">
              <Column field="name" header="Name" />
              <Column header="Destination">
                <template #body="slotProps">
                  <span>{{ formatDestination(slotProps.data.destination) }}</span>
                </template>
              </Column>
              <Column header="Defaults">
                <template #body="slotProps">
                  <span>
                    {{ defaultSummary(slotProps.data) }}
                  </span>
                </template>
              </Column>
              <Column field="enabled" header="Enabled">
                <template #body="slotProps">
                  <Tag :value="slotProps.data.enabled ? 'Yes' : 'No'" :severity="slotProps.data.enabled ? 'success' : 'warning'" />
                </template>
              </Column>
              <Column header="Actions">
                <template #body="slotProps">
                  <div class="row-actions">
                    <Button icon="pi pi-pencil" text @click="editTemplate(slotProps.data)" />
                    <Button icon="pi pi-trash" text severity="danger" @click="deleteTemplate(slotProps.data)" />
                  </div>
                </template>
              </Column>
            </DataTable>
          </BaseCard>
        </div>

        <div v-else class="tab-panel">
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
                    v-model="unreadOnly"
                    @update:modelValue="handleUnreadOnlyToggle"
                  />
                </div>
                <Button
                  icon="pi pi-check"
                  label="Mark all seen"
                  severity="secondary"
                  outlined
                  :disabled="unreadCount === 0"
                  :loading="markingAllSeen"
                  @click="markAllEventsSeen"
                />
                <Button icon="pi pi-refresh" label="Refresh" severity="secondary" outlined @click="refreshEvents" />
              </div>
            </div>
            <DataTable :value="events" dataKey="id" responsiveLayout="scroll">
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
                    @click="markEventSeen(slotProps.data)"
                  />
                </template>
              </Column>
            </DataTable>
          </BaseCard>
        </div>
      </TabContainer>

    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { useToast } from 'primevue/usetoast'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationsStore } from '@/stores/notifications'
import apiService from '@/utils/apiService'
import L from 'leaflet'
import { useRectangleDrawing } from '@/composables/useRectangleDrawing'

import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import TabContainer from '@/components/ui/layout/TabContainer.vue'
import BaseCard from '@/components/ui/base/BaseCard.vue'
import BaseMap from '@/components/maps/BaseMap.vue'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Textarea from 'primevue/textarea'
import Select from 'primevue/select'
import InputSwitch from 'primevue/inputswitch'
import Button from 'primevue/button'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Tag from 'primevue/tag'

const toast = useToast()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const notificationsStore = useNotificationsStore()

const tabs = computed(() => [
  { label: 'Rules', icon: 'pi pi-map', key: 'rules' },
  { label: 'Templates', icon: 'pi pi-envelope', key: 'templates' },
  {
    label: 'Events',
    icon: 'pi pi-bell',
    key: 'events',
    badge: unreadCount.value > 0 ? unreadCount.value : null,
    badgeType: 'danger'
  }
])

const activeTab = ref('rules')
const activeTabIndex = computed(() => tabs.value.findIndex(t => t.key === activeTab.value))

const rules = ref([])
const templates = ref([])
const friends = ref([])

const savingRule = ref(false)
const savingTemplate = ref(false)
const unreadOnly = ref(false)
const markingAllSeen = ref(false)
const markingEventId = ref(null)

const events = computed(() => {
  if (unreadOnly.value) {
    return notificationsStore.items.filter(item => !item.seen)
  }
  return notificationsStore.items
})

const unreadCount = computed(() => notificationsStore.unreadCount)

const editingRuleId = ref(null)
const editingTemplateId = ref(null)
const geofenceMap = ref(null)
const rectangleLayer = ref(null)
const mapCenter = ref([50.4501, 30.5234])
const mapZoom = ref(11)

const statusOptions = [
  { label: 'Active', value: 'ACTIVE' },
  { label: 'Paused', value: 'PAUSED' }
]

const ruleForm = ref(defaultRuleForm())
const templateForm = ref(defaultTemplateForm())
const rectangleDrawing = useRectangleDrawing({
  onRectangleCreated: ({ bounds, layer }) => {
    ruleForm.value.northEastLat = Number(bounds.getNorthEast().lat.toFixed(6))
    ruleForm.value.northEastLon = Number(bounds.getNorthEast().lng.toFixed(6))
    ruleForm.value.southWestLat = Number(bounds.getSouthWest().lat.toFixed(6))
    ruleForm.value.southWestLon = Number(bounds.getSouthWest().lng.toFixed(6))

    if (geofenceMap.value) {
      if (rectangleLayer.value) {
        geofenceMap.value.removeLayer(rectangleLayer.value)
      }
      rectangleLayer.value = layer.addTo(geofenceMap.value)
      geofenceMap.value.fitBounds(bounds, { padding: [20, 20] })
    }
  }
})

const subjectOptions = computed(() => {
  const items = []

  if (authStore.userId) {
    const meLabel = authStore.userName ? `${authStore.userName} (Me)` : `${authStore.userEmail} (Me)`
    items.push({ label: meLabel, value: authStore.userId })
  }

  for (const friend of friends.value) {
    const label = friend.fullName || friend.email
    items.push({ label, value: friend.friendId || friend.userId })
  }

  return items
})

const templateOptions = computed(() => {
  const opts = [{ label: 'Default', value: null }]
  templates.value.forEach(template => {
    opts.push({ label: template.name, value: template.id })
  })
  return opts
})

const templateMacros = [
  { key: '{{subjectName}}', description: 'Tracked subject display name' },
  { key: '{{eventType}}', description: 'Event type (ENTER or LEAVE)' },
  { key: '{{geofenceName}}', description: 'Geofence rule name' },
  { key: '{{timestamp}}', description: 'Event timestamp (UTC ISO-8601)' },
  { key: '{{lat}}', description: 'Point latitude' },
  { key: '{{lon}}', description: 'Point longitude' }
]

function defaultRuleForm() {
  return {
    name: '',
    subjectUserId: authStore.userId || null,
    northEastLat: null,
    northEastLon: null,
    southWestLat: null,
    southWestLon: null,
    monitorEnter: true,
    monitorLeave: true,
    cooldownSeconds: 120,
    enterTemplateId: null,
    leaveTemplateId: null,
    status: 'ACTIVE'
  }
}

function defaultTemplateForm() {
  return {
    name: '',
    destination: '',
    titleTemplate: '',
    bodyTemplate: '',
    defaultForEnter: false,
    defaultForLeave: false,
    enabled: true
  }
}

const availableTabs = new Set(['rules', 'templates', 'events'])

function normalizeTabKey(value) {
  if (typeof value !== 'string') {
    return 'rules'
  }
  return availableTabs.has(value) ? value : 'rules'
}

function syncActiveTabFromRoute(tabValue) {
  activeTab.value = normalizeTabKey(tabValue)
}

function onTabChange(event) {
  const selected = tabs.value[event.index]
  if (!selected) {
    return
  }
  activeTab.value = selected.key

  const nextQuery = { ...route.query }
  if (selected.key === 'rules') {
    delete nextQuery.tab
  } else {
    nextQuery.tab = selected.key
  }
  router.replace({ query: nextQuery }).catch(() => {})
}

function handleMapReady(map) {
  geofenceMap.value = map
  rectangleDrawing.initialize(map)
  syncMapRectangleFromForm()
}

function startRectangleDraw() {
  if (!geofenceMap.value) {
    return
  }
  rectangleDrawing.startDrawing()
}

function syncMapRectangleFromForm(focus = false) {
  if (!geofenceMap.value) {
    return
  }
  const { northEastLat, northEastLon, southWestLat, southWestLon } = ruleForm.value
  if ([northEastLat, northEastLon, southWestLat, southWestLon].some(v => v === null || v === undefined)) {
    return
  }

  const bounds = L.latLngBounds(
    [southWestLat, southWestLon],
    [northEastLat, northEastLon]
  )

  if (rectangleLayer.value) {
    geofenceMap.value.removeLayer(rectangleLayer.value)
  }
  rectangleLayer.value = L.rectangle(bounds, {
    color: '#e91e63',
    weight: 2,
    fill: false
  }).addTo(geofenceMap.value)

  if (focus) {
    geofenceMap.value.fitBounds(bounds, { padding: [20, 20] })
  }
}

async function loadRules() {
  const response = await apiService.get('/geofences/rules')
  rules.value = response?.data || []
}

async function loadTemplates() {
  const response = await apiService.get('/geofences/templates')
  templates.value = response?.data || []
}

async function refreshEvents() {
  await notificationsStore.refresh({
    emitToasts: false,
    emitBrowser: false,
    emitStartupSummary: false
  })
}

async function loadFriends() {
  const response = await apiService.get('/friends')
  const all = response?.data || []
  friends.value = all.filter(friend => friend.friendSharesLiveLocation)
}

async function saveRule() {
  savingRule.value = true
  try {
    const payload = {
      ...ruleForm.value
    }

    if (editingRuleId.value) {
      await apiService.patch(`/geofences/rules/${editingRuleId.value}`, payload)
      toast.add({ severity: 'success', summary: 'Updated', detail: 'Rule updated', life: 3000 })
    } else {
      await apiService.post('/geofences/rules', payload)
      toast.add({ severity: 'success', summary: 'Created', detail: 'Rule created', life: 3000 })
    }

    resetRuleForm()
    await loadRules()
    await refreshEvents()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Rule Error',
      detail: error?.response?.data?.message || error?.message || 'Failed to save rule',
      life: 5000
    })
  } finally {
    savingRule.value = false
  }
}

function editRule(rule) {
  editingRuleId.value = rule.id
  ruleForm.value = {
    name: rule.name,
    subjectUserId: rule.subjectUserId,
    northEastLat: rule.northEastLat,
    northEastLon: rule.northEastLon,
    southWestLat: rule.southWestLat,
    southWestLon: rule.southWestLon,
    monitorEnter: rule.monitorEnter,
    monitorLeave: rule.monitorLeave,
    cooldownSeconds: rule.cooldownSeconds,
    enterTemplateId: rule.enterTemplateId,
    leaveTemplateId: rule.leaveTemplateId,
    status: rule.status
  }
  syncMapRectangleFromForm(true)
}

async function deleteRule(rule) {
  const confirmed = window.confirm(`Delete geofence rule "${rule.name}"?`)
  if (!confirmed) {
    return
  }

  try {
    await apiService.delete(`/geofences/rules/${rule.id}`)
    toast.add({ severity: 'success', summary: 'Deleted', detail: 'Rule deleted', life: 3000 })
    await loadRules()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Delete Error',
      detail: error?.response?.data?.message || error?.message || 'Failed to delete rule',
      life: 5000
    })
  }
}

function resetRuleForm() {
  editingRuleId.value = null
  ruleForm.value = defaultRuleForm()
  if (geofenceMap.value && rectangleLayer.value) {
    geofenceMap.value.removeLayer(rectangleLayer.value)
    rectangleLayer.value = null
  }
}

async function saveTemplate() {
  savingTemplate.value = true
  try {
    const payload = {
      ...templateForm.value
    }

    if (editingTemplateId.value) {
      await apiService.patch(`/geofences/templates/${editingTemplateId.value}`, payload)
      toast.add({ severity: 'success', summary: 'Updated', detail: 'Template updated', life: 3000 })
    } else {
      await apiService.post('/geofences/templates', payload)
      toast.add({ severity: 'success', summary: 'Created', detail: 'Template created', life: 3000 })
    }

    resetTemplateForm()
    await loadTemplates()
    await loadRules()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Template Error',
      detail: error?.response?.data?.message || error?.message || 'Failed to save template',
      life: 5000
    })
  } finally {
    savingTemplate.value = false
  }
}

function editTemplate(template) {
  editingTemplateId.value = template.id
  templateForm.value = {
    name: template.name,
    destination: template.destination || '',
    titleTemplate: template.titleTemplate || '',
    bodyTemplate: template.bodyTemplate || '',
    defaultForEnter: !!template.defaultForEnter,
    defaultForLeave: !!template.defaultForLeave,
    enabled: !!template.enabled
  }
}

async function deleteTemplate(template) {
  const confirmed = window.confirm(`Delete template "${template.name}"?`)
  if (!confirmed) {
    return
  }

  try {
    await apiService.delete(`/geofences/templates/${template.id}`)
    toast.add({ severity: 'success', summary: 'Deleted', detail: 'Template deleted', life: 3000 })
    await loadTemplates()
    await loadRules()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Delete Error',
      detail: error?.response?.data?.message || error?.message || 'Failed to delete template',
      life: 5000
    })
  }
}

function resetTemplateForm() {
  editingTemplateId.value = null
  templateForm.value = defaultTemplateForm()
}

async function markEventSeen(event) {
  if (!event?.id) {
    return
  }

  markingEventId.value = event.id
  try {
    await notificationsStore.markSeen(event.id)
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Event Error',
      detail: error?.response?.data?.message || error?.message || 'Failed to mark event as seen',
      life: 5000
    })
  } finally {
    markingEventId.value = null
  }
}

async function markAllEventsSeen() {
  markingAllSeen.value = true
  try {
    await notificationsStore.markAllSeen()
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Event Error',
      detail: error?.response?.data?.message || error?.message || 'Failed to mark events as seen',
      life: 5000
    })
  } finally {
    markingAllSeen.value = false
  }
}

function handleUnreadOnlyToggle() {
  void refreshEvents()
}

function eventSummary(rule) {
  const events = []
  if (rule.monitorEnter) events.push('Enter')
  if (rule.monitorLeave) events.push('Leave')
  return events.join(' / ')
}

function defaultSummary(template) {
  const flags = []
  if (template.defaultForEnter) flags.push('Enter')
  if (template.defaultForLeave) flags.push('Leave')
  return flags.length ? flags.join(' + ') : 'No'
}

function formatDestination(destination) {
  return destination && destination.trim() ? destination : 'In-app only'
}

function formatDate(value) {
  if (!value) return '-'
  return dayjs(value).format('YYYY-MM-DD HH:mm:ss')
}

function deliverySeverity(status) {
  if (status === 'SENT') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PENDING') return 'info'
  return 'secondary'
}

onMounted(async () => {
  try {
    syncActiveTabFromRoute(route.query.tab)
    await Promise.all([loadFriends(), loadTemplates(), loadRules(), refreshEvents()])
    if (!ruleForm.value.subjectUserId && authStore.userId) {
      ruleForm.value.subjectUserId = authStore.userId
    }
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Load Error',
      detail: error?.message || 'Failed to load geofence data',
      life: 5000
    })
  }
})

watch(
  () => route.query.tab,
  (tab) => {
    syncActiveTabFromRoute(tab)
    if (activeTab.value === 'events') {
      void refreshEvents()
    }
  }
)
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

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 0.75rem;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.field.wide {
  grid-column: 1 / -1;
}

.map-picker-toolbar {
  margin-bottom: 0.5rem;
}

.map-picker {
  width: 100%;
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  overflow: hidden;
}

.field label {
  font-weight: 600;
  font-size: 0.9rem;
}

.muted-text {
  color: var(--text-color-secondary);
  font-size: 0.8rem;
}

.macro-help {
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  padding: 0.75rem;
  background: var(--surface-50);
}

.macro-help-title {
  font-weight: 600;
  margin-bottom: 0.5rem;
}

.macro-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 0.5rem 0.75rem;
}

.macro-item {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.toggle-field {
  align-items: flex-start;
}

.actions-row {
  margin-top: 1rem;
  display: flex;
  gap: 0.5rem;
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

.row-actions {
  display: flex;
  gap: 0.35rem;
}

@media (max-width: 768px) {
  .tab-panel {
    padding: 0.5rem;
  }

  .panel-card {
    padding: 0.75rem;
  }
}
</style>

<template>
  <AppLayout variant="default">
    <ConfirmDialog />
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
              <div class="rules-top-row wide">
                <div class="field">
                  <label>Name</label>
                  <InputText
                    v-model="ruleForm.name"
                    placeholder="Home area"
                    :class="{ 'p-invalid': !!ruleFormErrors.name }"
                  />
                  <small v-if="ruleFormErrors.name" class="error-text">{{ ruleFormErrors.name }}</small>
                </div>

                <div class="field">
                  <label>Subject</label>
                  <Select
                    v-model="ruleForm.subjectUserId"
                    :options="subjectOptions"
                    optionLabel="label"
                    optionValue="value"
                    placeholder="Select subject"
                    :class="{ 'p-invalid': !!ruleFormErrors.subjectUserId }"
                  />
                  <small v-if="ruleFormErrors.subjectUserId" class="error-text">{{ ruleFormErrors.subjectUserId }}</small>
                </div>

                <div class="field area-button-field">
                  <label>Area Picker</label>
                  <Button
                    label="Draw Rectangle on Map"
                    icon="pi pi-pencil"
                    severity="secondary"
                    outlined
                    @click="startRectangleDraw"
                  />
                </div>
              </div>

              <div class="field wide">
                <label>Area Map</label>
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
                <small v-if="selectedAreaSummary" class="muted-text">{{ selectedAreaSummary }}</small>
                <small v-if="ruleFormErrors.area" class="error-text">{{ ruleFormErrors.area }}</small>
              </div>

              <div class="field toggle-field">
                <label>Monitor Enter</label>
                <InputSwitch v-model="ruleForm.monitorEnter" />
              </div>

              <div class="field toggle-field">
                <label>Monitor Leave</label>
                <InputSwitch v-model="ruleForm.monitorLeave" />
              </div>
              <div v-if="ruleFormErrors.monitoring" class="field wide">
                <small class="error-text">{{ ruleFormErrors.monitoring }}</small>
              </div>

              <div class="field">
                <label class="field-label-with-help">
                  <span>Cooldown (seconds)</span>
                  <i
                    class="pi pi-info-circle help-icon"
                    v-tooltip.bottom="'Prevents repeated Enter/Leave notifications for this rule during the cooldown window.'"
                  />
                </label>
                <InputNumber v-model="ruleForm.cooldownSeconds" :min="0" />
                <small class="muted-text">Minimum delay between notifications for this rule.</small>
              </div>

              <div class="field">
                <label>Enter Template</label>
                <Select
                  v-model="ruleForm.enterTemplateId"
                  :options="enterTemplateOptions"
                  optionLabel="label"
                  optionValue="value"
                  :placeholder="hasEnabledDefaultEnterTemplate ? 'Use default enter template' : 'Select enter template'"
                />
                <small v-if="hasEnabledDefaultEnterTemplate" class="muted-text">
                  If empty, your default ENTER template ({{ enabledDefaultEnterTemplate?.name }}) is used.
                </small>
                <small v-else class="muted-text">
                  No default ENTER template configured. Empty value uses built-in in-app message.
                </small>
              </div>

              <div class="field">
                <label>Leave Template</label>
                <Select
                  v-model="ruleForm.leaveTemplateId"
                  :options="leaveTemplateOptions"
                  optionLabel="label"
                  optionValue="value"
                  :placeholder="hasEnabledDefaultLeaveTemplate ? 'Use default leave template' : 'Select leave template'"
                />
                <small v-if="hasEnabledDefaultLeaveTemplate" class="muted-text">
                  If empty, your default LEAVE template ({{ enabledDefaultLeaveTemplate?.name }}) is used.
                </small>
                <small v-else class="muted-text">
                  No default LEAVE template configured. Empty value uses built-in in-app message.
                </small>
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
                <InputText
                  ref="templateNameInput"
                  v-model="templateForm.name"
                  placeholder="Telegram Enter Alert"
                  :class="{ 'p-invalid': !!templateFormErrors.name }"
                />
                <small v-if="templateFormErrors.name" class="error-text">{{ templateFormErrors.name }}</small>
              </div>
              <div class="field wide">
                <label>Destination URL(s) (optional)</label>
                <Textarea
                  ref="templateDestinationInput"
                  v-model="templateForm.destination"
                  rows="3"
                  autoResize
                  placeholder="tgram://TOKEN/CHAT_ID&#10;discord://WEBHOOK_TOKEN"
                  :class="{ 'p-invalid': !!templateFormErrors.destination }"
                />
                <small class="muted-text">One destination URL per line. Leave empty for in-app only notifications.</small>
                <small v-if="templateFormErrors.destination" class="error-text">{{ templateFormErrors.destination }}</small>
              </div>
              <div class="field wide">
                <label>Title Template</label>
                <InputText
                  ref="templateTitleInput"
                  v-model="templateForm.titleTemplate"
                  placeholder="{{subjectName}} {{eventVerb}} {{geofenceName}}"
                  :class="{ 'p-invalid': !!templateFormErrors.titleTemplate }"
                  @focus="setFocusedTemplateField('titleTemplate')"
                />
                <small v-if="templateFormErrors.titleTemplate" class="error-text">{{ templateFormErrors.titleTemplate }}</small>
              </div>
              <div class="field wide">
                <label>Body Template</label>
                <Textarea
                  ref="templateBodyInput"
                  v-model="templateForm.bodyTemplate"
                  rows="4"
                  autoResize
                  :class="{ 'p-invalid': !!templateFormErrors.bodyTemplate }"
                  @focus="setFocusedTemplateField('bodyTemplate')"
                />
                <small v-if="templateFormErrors.bodyTemplate" class="error-text">{{ templateFormErrors.bodyTemplate }}</small>
                <div class="template-preview">
                  <div class="template-preview-title">Preview (sample event)</div>
                  <small class="muted-text">Using sample values and your current date/time format.</small>
                  <div class="preview-row">
                    <span class="preview-label">Title</span>
                    <span class="preview-value">{{ templatePreview.title || '-' }}</span>
                  </div>
                  <div class="preview-row">
                    <span class="preview-label">Body</span>
                    <span class="preview-value">{{ templatePreview.body || '-' }}</span>
                  </div>
                </div>
              </div>
              <div class="field wide">
                <div class="macro-help">
                  <div class="macro-help-header">
                    <div class="macro-help-title">Available macros</div>
                    <small class="muted-text">Click a macro to insert into the focused Title/Body field.</small>
                  </div>
                  <div class="macro-grid">
                    <div v-for="macro in templateMacros" :key="macro.key" class="macro-item">
                      <button
                        type="button"
                        class="macro-chip"
                        @click="insertMacro(macro.key)"
                      >
                        <code>{{ macro.key }}</code>
                      </button>
                      <span class="macro-description">{{ macro.description }}</span>
                      <small class="muted-text">Example: {{ macro.example }}</small>
                    </div>
                  </div>
                </div>
              </div>
              <div class="field toggle-field">
                <label>Default for Enter</label>
                <InputSwitch v-model="templateForm.defaultForEnter" />
                <small class="muted-text">Current default: {{ currentDefaultEnterName }}</small>
                <small v-if="templateFormErrors.defaultForEnter" class="error-text">{{ templateFormErrors.defaultForEnter }}</small>
              </div>
              <div class="field toggle-field">
                <label>Default for Leave</label>
                <InputSwitch v-model="templateForm.defaultForLeave" />
                <small class="muted-text">Current default: {{ currentDefaultLeaveName }}</small>
                <small v-if="templateFormErrors.defaultForLeave" class="error-text">{{ templateFormErrors.defaultForLeave }}</small>
              </div>
              <div class="field toggle-field">
                <label>Enabled</label>
                <InputSwitch v-model="templateForm.enabled" />
              </div>
              <div v-if="templateFormErrors.general" class="field wide">
                <small class="error-text">{{ templateFormErrors.general }}</small>
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
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationsStore } from '@/stores/notifications'
import apiService from '@/utils/apiService'
import L from 'leaflet'
import { useRectangleDrawing } from '@/composables/useRectangleDrawing'
import { useTimezone } from '@/composables/useTimezone'

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
import ConfirmDialog from 'primevue/confirmdialog'

const toast = useToast()
const confirm = useConfirm()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const notificationsStore = useNotificationsStore()
const timezone = useTimezone()

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
const ruleFormErrors = ref({
  name: '',
  subjectUserId: '',
  area: '',
  monitoring: ''
})
const templateFormErrors = ref({
  name: '',
  destination: '',
  titleTemplate: '',
  bodyTemplate: '',
  defaultForEnter: '',
  defaultForLeave: '',
  general: ''
})
const templateNameInput = ref(null)
const templateDestinationInput = ref(null)
const templateTitleInput = ref(null)
const templateBodyInput = ref(null)
const focusedTemplateField = ref('bodyTemplate')
const suppressDefaultToggleWatch = ref(false)
const TEMPLATE_MACRO_PATTERN = /\{\{\s*([a-zA-Z][a-zA-Z0-9]*)\s*}}/g
const DESTINATION_URL_PATTERN = /^[a-zA-Z][a-zA-Z0-9+.-]*:\/\/.+$/
const PREVIEW_TIMESTAMP_UTC = '2026-03-24T00:04:47Z'

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
    ruleFormErrors.value.area = ''
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

const templateOptionItems = computed(() => {
  return templates.value.map(template => ({
    label: template.enabled ? template.name : `${template.name} (Disabled)`,
    value: template.id
  }))
})
const enabledDefaultEnterTemplate = computed(() =>
  templates.value.find(template => template.defaultForEnter && template.enabled) || null
)
const enabledDefaultLeaveTemplate = computed(() =>
  templates.value.find(template => template.defaultForLeave && template.enabled) || null
)
const hasEnabledDefaultEnterTemplate = computed(() => !!enabledDefaultEnterTemplate.value)
const hasEnabledDefaultLeaveTemplate = computed(() => !!enabledDefaultLeaveTemplate.value)

const enterTemplateOptions = computed(() => {
  const options = [...templateOptionItems.value]
  if (enabledDefaultEnterTemplate.value) {
    options.unshift({
      label: `Use default ENTER template (${enabledDefaultEnterTemplate.value.name})`,
      value: null
    })
  }
  return options
})

const leaveTemplateOptions = computed(() => {
  const options = [...templateOptionItems.value]
  if (enabledDefaultLeaveTemplate.value) {
    options.unshift({
      label: `Use default LEAVE template (${enabledDefaultLeaveTemplate.value.name})`,
      value: null
    })
  }
  return options
})

function hasValidAreaBounds(form) {
  return ['northEastLat', 'northEastLon', 'southWestLat', 'southWestLon'].every((key) => {
    const value = form[key]
    return value !== null && value !== undefined && value !== '' && Number.isFinite(Number(value))
  })
}

const selectedAreaSummary = computed(() => {
  const { northEastLat, northEastLon, southWestLat, southWestLon } = ruleForm.value
  if (!hasValidAreaBounds(ruleForm.value)) {
    return ''
  }

  return `Selected area: NE (${Number(northEastLat).toFixed(5)}, ${Number(northEastLon).toFixed(5)}), SW (${Number(southWestLat).toFixed(5)}, ${Number(southWestLon).toFixed(5)})`
})

const templateMacros = [
  {
    key: '{{subjectName}}',
    placeholder: 'subjectName',
    description: 'Tracked subject display name',
    example: 'Peter'
  },
  {
    key: '{{eventCode}}',
    placeholder: 'eventCode',
    description: 'Event code (ENTER or LEAVE)',
    example: 'ENTER'
  },
  {
    key: '{{eventVerb}}',
    placeholder: 'eventVerb',
    description: 'Event verb (entered or left)',
    example: 'entered'
  },
  {
    key: '{{geofenceName}}',
    placeholder: 'geofenceName',
    description: 'Geofence rule name',
    example: 'Home'
  },
  {
    key: '{{timestamp}}',
    placeholder: 'timestamp',
    description: 'Event timestamp in your timezone/date format',
    example: '03/24/2026 02:04:47'
  },
  {
    key: '{{timestampUtc}}',
    placeholder: 'timestampUtc',
    description: 'Event timestamp in UTC ISO-8601',
    example: PREVIEW_TIMESTAMP_UTC
  },
  {
    key: '{{lat}}',
    placeholder: 'lat',
    description: 'Point latitude',
    example: '49.547085'
  },
  {
    key: '{{lon}}',
    placeholder: 'lon',
    description: 'Point longitude',
    example: '25.595918'
  }
]
const allowedTemplateMacroNames = new Set(templateMacros.map(macro => macro.placeholder))
const currentDefaultEnterTemplate = computed(() => templates.value.find(template => template.defaultForEnter) || null)
const currentDefaultLeaveTemplate = computed(() => templates.value.find(template => template.defaultForLeave) || null)
const currentDefaultEnterName = computed(() => currentDefaultEnterTemplate.value?.name || 'None')
const currentDefaultLeaveName = computed(() => currentDefaultLeaveTemplate.value?.name || 'None')
const templatePreviewContext = computed(() => ({
  subjectName: authStore.userName || authStore.userEmail || 'Peter',
  eventCode: 'ENTER',
  eventVerb: 'entered',
  geofenceName: 'Home',
  timestamp: formatDate(PREVIEW_TIMESTAMP_UTC),
  timestampUtc: PREVIEW_TIMESTAMP_UTC,
  lat: '49.547085',
  lon: '25.595918'
}))
const templatePreview = computed(() => {
  return {
    title: renderTemplateWithContext(templateForm.value.titleTemplate, templatePreviewContext.value),
    body: renderTemplateWithContext(templateForm.value.bodyTemplate, templatePreviewContext.value)
  }
})

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

function clearRuleFormErrors() {
  ruleFormErrors.value = {
    name: '',
    subjectUserId: '',
    area: '',
    monitoring: ''
  }
}

function clearTemplateFormErrors() {
  templateFormErrors.value = {
    name: '',
    destination: '',
    titleTemplate: '',
    bodyTemplate: '',
    defaultForEnter: '',
    defaultForLeave: '',
    general: ''
  }
}

function setFocusedTemplateField(field) {
  focusedTemplateField.value = field
}

function resolveInputElement(field) {
  const source = field === 'titleTemplate' ? templateTitleInput.value : templateBodyInput.value
  if (!source) {
    return null
  }

  if (source.$el) {
    return source.$el.querySelector('input, textarea') || source.$el
  }
  if (source.$refs?.input) {
    return source.$refs.input
  }
  return source
}

function splitDestinationLines(destination, allowLegacySeparators = false) {
  const input = typeof destination === 'string' ? destination : ''
  const rawSegments = allowLegacySeparators
    ? input.replace(/;/g, '\n').replace(/,/g, '\n').split(/\r?\n/)
    : input.split(/\r?\n/)

  return rawSegments
    .map(segment => segment.trim())
    .filter(segment => segment.length > 0)
}

function normalizeDestination(destination) {
  return splitDestinationLines(destination).join('\n')
}

function confirmAction({
  message,
  header = 'Confirmation',
  acceptLabel = 'Confirm',
  rejectLabel = 'Cancel',
  acceptClass = 'p-button-danger'
}) {
  return new Promise((resolve) => {
    let settled = false
    const settle = (value) => {
      if (!settled) {
        settled = true
        resolve(value)
      }
    }

    confirm.require({
      message,
      header,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel,
      rejectLabel,
      acceptClass,
      rejectClass: 'p-button-text p-button-secondary',
      accept: () => settle(true),
      reject: () => settle(false),
      onHide: () => settle(false)
    })
  })
}

function validateTemplateSyntax(template, fieldLabel) {
  if (!template || !template.trim()) {
    return ''
  }

  const unknownMacros = new Set()
  let match
  TEMPLATE_MACRO_PATTERN.lastIndex = 0
  while ((match = TEMPLATE_MACRO_PATTERN.exec(template)) !== null) {
    const macroName = match[1]
    if (!allowedTemplateMacroNames.has(macroName)) {
      unknownMacros.add(macroName)
    }
  }

  if (unknownMacros.size > 0) {
    return `${fieldLabel} has unsupported macros: ${Array.from(unknownMacros).join(', ')}.`
  }

  TEMPLATE_MACRO_PATTERN.lastIndex = 0
  const withoutValidMacros = template.replace(TEMPLATE_MACRO_PATTERN, '')
  if (withoutValidMacros.includes('{{') || withoutValidMacros.includes('}}')) {
    return `${fieldLabel} has invalid macro syntax. Use {{macroName}}.`
  }

  return ''
}

function focusFirstTemplateError(errors) {
  const orderedFields = ['name', 'destination', 'titleTemplate', 'bodyTemplate']
  const firstField = orderedFields.find(field => errors[field])
  if (!firstField) {
    return
  }

  nextTick(() => {
    if (firstField === 'name') {
      const target = templateNameInput.value?.$el || templateNameInput.value
      target?.focus?.()
      return
    }
    if (firstField === 'destination') {
      const target = templateDestinationInput.value?.$el || templateDestinationInput.value
      target?.focus?.()
      return
    }
    const input = resolveInputElement(firstField)
    input?.focus?.()
  })
}

function validateTemplateForm() {
  clearTemplateFormErrors()
  const errors = {}
  const form = templateForm.value

  const normalizedName = (form.name || '').trim()
  if (!normalizedName) {
    errors.name = 'Template name is required.'
  } else if (normalizedName.length > 120) {
    errors.name = 'Template name must be 120 characters or less.'
  }

  const destinationLines = splitDestinationLines(form.destination)
  for (let index = 0; index < destinationLines.length; index += 1) {
    const line = destinationLines[index]
    if (line.includes(',') || line.includes(';')) {
      errors.destination = `Line ${index + 1} contains multiple URLs. Use one destination per line.`
      break
    }
    if (!DESTINATION_URL_PATTERN.test(line)) {
      errors.destination = `Line ${index + 1} must be a valid URL (scheme://...).`
      break
    }
  }

  const titleSyntaxError = validateTemplateSyntax(form.titleTemplate, 'Title template')
  if (titleSyntaxError) {
    errors.titleTemplate = titleSyntaxError
  }

  const bodySyntaxError = validateTemplateSyntax(form.bodyTemplate, 'Body template')
  if (bodySyntaxError) {
    errors.bodyTemplate = bodySyntaxError
  }

  templateFormErrors.value = {
    ...templateFormErrors.value,
    ...errors
  }
  if (Object.keys(errors).length > 0) {
    focusFirstTemplateError(errors)
    return false
  }
  return true
}

function renderTemplateWithContext(template, context) {
  if (!template || !template.trim()) {
    return ''
  }
  TEMPLATE_MACRO_PATTERN.lastIndex = 0
  return template.replace(TEMPLATE_MACRO_PATTERN, (_, macroName) => context[macroName] ?? '')
}

function insertMacro(macroKey) {
  const targetField = focusedTemplateField.value === 'titleTemplate' ? 'titleTemplate' : 'bodyTemplate'
  const currentValue = templateForm.value[targetField] || ''
  const input = resolveInputElement(targetField)

  if (input && typeof input.selectionStart === 'number' && typeof input.selectionEnd === 'number') {
    const start = input.selectionStart
    const end = input.selectionEnd
    const updated = `${currentValue.slice(0, start)}${macroKey}${currentValue.slice(end)}`
    templateForm.value[targetField] = updated
    nextTick(() => {
      input.focus()
      const cursor = start + macroKey.length
      input.setSelectionRange(cursor, cursor)
    })
  } else if (!currentValue) {
    templateForm.value[targetField] = macroKey
  } else {
    templateForm.value[targetField] = `${currentValue} ${macroKey}`
  }

  if (templateFormErrors.value[targetField]) {
    templateFormErrors.value[targetField] = ''
  }
}

function extractApiErrorMessage(error, fallback) {
  return error?.response?.data?.message
    || error?.response?.data?.error
    || error?.response?.data?.data?.message
    || error?.userMessage
    || error?.message
    || fallback
}

function validateRuleForm() {
  clearRuleFormErrors()
  const errors = {}
  const form = ruleForm.value

  if (!form.name || !form.name.trim()) {
    errors.name = 'Rule name is required.'
  }

  if (!form.subjectUserId) {
    errors.subjectUserId = 'Please select who this rule tracks.'
  }

  if (!hasValidAreaBounds(form)) {
    errors.area = 'Please draw a rectangle on the map.'
  }

  if (!form.monitorEnter && !form.monitorLeave) {
    errors.monitoring = 'Enable at least one event type (Enter or Leave).'
  }

  ruleFormErrors.value = {
    ...ruleFormErrors.value,
    ...errors
  }
  return Object.keys(errors).length === 0
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
  if (!validateRuleForm()) {
    toast.add({
      severity: 'warn',
      summary: 'Validation Error',
      detail: 'Please fix the highlighted fields before saving.',
      life: 4000
    })
    return
  }

  savingRule.value = true
  try {
    const payload = {
      ...ruleForm.value,
      name: ruleForm.value.name.trim()
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
      detail: extractApiErrorMessage(error, 'Failed to save rule'),
      life: 5000
    })
  } finally {
    savingRule.value = false
  }
}

function editRule(rule) {
  clearRuleFormErrors()
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
  const confirmed = await confirmAction({
    header: 'Delete Rule',
    message: `Delete geofence rule "${rule.name}"?`,
    acceptLabel: 'Delete',
    rejectLabel: 'Cancel',
    acceptClass: 'p-button-danger'
  })
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
      detail: extractApiErrorMessage(error, 'Failed to delete rule'),
      life: 5000
    })
  }
}

function resetRuleForm() {
  editingRuleId.value = null
  ruleForm.value = defaultRuleForm()
  clearRuleFormErrors()
  if (geofenceMap.value && rectangleLayer.value) {
    geofenceMap.value.removeLayer(rectangleLayer.value)
    rectangleLayer.value = null
  }
}

async function saveTemplate() {
  if (!validateTemplateForm()) {
    toast.add({
      severity: 'warn',
      summary: 'Validation Error',
      detail: 'Please fix template form errors before saving.',
      life: 4500
    })
    return
  }

  savingTemplate.value = true
  try {
    const payload = {
      ...templateForm.value,
      name: templateForm.value.name.trim(),
      destination: normalizeDestination(templateForm.value.destination),
      titleTemplate: templateForm.value.titleTemplate?.trim() || '',
      bodyTemplate: templateForm.value.bodyTemplate?.trim() || ''
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
    templateFormErrors.value.general = extractApiErrorMessage(error, 'Failed to save template')
    toast.add({
      severity: 'error',
      summary: 'Template Error',
      detail: templateFormErrors.value.general,
      life: 5000
    })
  } finally {
    savingTemplate.value = false
  }
}

function editTemplate(template) {
  clearTemplateFormErrors()
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
  const usages = rules.value.filter(rule =>
    rule.enterTemplateId === template.id || rule.leaveTemplateId === template.id
  )
  const warning = usages.length > 0
    ? `\n\nWarning: this template is currently used by ${usages.length} rule(s).`
    : ''

  const confirmed = await confirmAction({
    header: 'Delete Template',
    message: `Delete template "${template.name}"?${warning}`,
    acceptLabel: 'Delete',
    rejectLabel: 'Cancel',
    acceptClass: 'p-button-danger'
  })
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
      detail: extractApiErrorMessage(error, 'Failed to delete template'),
      life: 5000
    })
  }
}

function resetTemplateForm() {
  editingTemplateId.value = null
  templateForm.value = defaultTemplateForm()
  clearTemplateFormErrors()
  focusedTemplateField.value = 'bodyTemplate'
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
      detail: extractApiErrorMessage(error, 'Failed to mark event as seen'),
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
      detail: extractApiErrorMessage(error, 'Failed to mark events as seen'),
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
  const destinations = splitDestinationLines(destination, true)
  if (destinations.length === 0) {
    return 'In-app only'
  }
  const firstMasked = maskDestination(destinations[0])
  if (destinations.length === 1) {
    return firstMasked
  }
  return `${firstMasked} (+${destinations.length - 1} more)`
}

function maskDestination(destination) {
  const normalized = String(destination || '').trim()
  if (!normalized) {
    return '***'
  }
  const matched = normalized.match(/^([a-zA-Z][a-zA-Z0-9+.-]*:\/\/).+$/)
  if (matched) {
    return `${matched[1]}***`
  }
  return '***'
}

function formatDate(value) {
  if (!value) return '-'
  return `${timezone.formatDateDisplay(value)} ${timezone.format(value, 'HH:mm:ss')}`
}

function deliverySeverity(status) {
  if (status === 'SENT') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PENDING') return 'info'
  return 'secondary'
}

function handleDefaultToggleChange(type, enabled) {
  if (!enabled || suppressDefaultToggleWatch.value) {
    return
  }

  const currentDefault = type === 'enter' ? currentDefaultEnterTemplate.value : currentDefaultLeaveTemplate.value
  if (!currentDefault) {
    return
  }

  if (editingTemplateId.value && currentDefault.id === editingTemplateId.value) {
    return
  }

  void confirmAction({
    header: 'Set Default Template',
    message: `Set this as default ${type.toUpperCase()} template? Current default "${currentDefault.name}" will be unset.`,
    acceptLabel: 'Set Default',
    rejectLabel: 'Cancel',
    acceptClass: 'p-button-primary'
  }).then((confirmed) => {
    if (confirmed) {
      return
    }
    suppressDefaultToggleWatch.value = true
    if (type === 'enter') {
      templateForm.value.defaultForEnter = false
    } else {
      templateForm.value.defaultForLeave = false
    }
    nextTick(() => {
      suppressDefaultToggleWatch.value = false
    })
  })
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
      detail: extractApiErrorMessage(error, 'Failed to load geofence data'),
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

watch(
  () => ruleForm.value.name,
  (value) => {
    if (ruleFormErrors.value.name && value && value.trim()) {
      ruleFormErrors.value.name = ''
    }
  }
)

watch(
  () => ruleForm.value.subjectUserId,
  (value) => {
    if (ruleFormErrors.value.subjectUserId && value) {
      ruleFormErrors.value.subjectUserId = ''
    }
  }
)

watch(
  () => [ruleForm.value.monitorEnter, ruleForm.value.monitorLeave],
  ([monitorEnter, monitorLeave]) => {
    if (ruleFormErrors.value.monitoring && (monitorEnter || monitorLeave)) {
      ruleFormErrors.value.monitoring = ''
    }
  }
)

watch(
  () => templateForm.value.name,
  (value) => {
    if (templateFormErrors.value.name && value && value.trim()) {
      templateFormErrors.value.name = ''
    }
    if (templateFormErrors.value.general) {
      templateFormErrors.value.general = ''
    }
  }
)

watch(
  () => templateForm.value.destination,
  () => {
    if (templateFormErrors.value.destination) {
      templateFormErrors.value.destination = ''
    }
    if (templateFormErrors.value.general) {
      templateFormErrors.value.general = ''
    }
  }
)

watch(
  () => templateForm.value.titleTemplate,
  () => {
    if (templateFormErrors.value.titleTemplate) {
      templateFormErrors.value.titleTemplate = ''
    }
    if (templateFormErrors.value.general) {
      templateFormErrors.value.general = ''
    }
  }
)

watch(
  () => templateForm.value.bodyTemplate,
  () => {
    if (templateFormErrors.value.bodyTemplate) {
      templateFormErrors.value.bodyTemplate = ''
    }
    if (templateFormErrors.value.general) {
      templateFormErrors.value.general = ''
    }
  }
)

watch(
  () => templateForm.value.defaultForEnter,
  (enabled) => {
    handleDefaultToggleChange('enter', enabled)
    if (templateFormErrors.value.defaultForEnter && enabled) {
      templateFormErrors.value.defaultForEnter = ''
    }
    if (templateFormErrors.value.general) {
      templateFormErrors.value.general = ''
    }
  }
)

watch(
  () => templateForm.value.defaultForLeave,
  (enabled) => {
    handleDefaultToggleChange('leave', enabled)
    if (templateFormErrors.value.defaultForLeave && enabled) {
      templateFormErrors.value.defaultForLeave = ''
    }
    if (templateFormErrors.value.general) {
      templateFormErrors.value.general = ''
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

.rules-top-row {
  display: grid;
  grid-template-columns: minmax(220px, 1.2fr) minmax(220px, 1fr) auto;
  gap: 0.75rem;
  align-items: end;
}

.area-button-field {
  min-width: 220px;
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

.field-label-with-help {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}

.help-icon {
  color: var(--text-color-secondary);
  cursor: help;
  font-size: 0.85rem;
}

.muted-text {
  color: var(--text-color-secondary);
  font-size: 0.8rem;
}

.error-text {
  color: var(--red-500);
  font-size: 0.78rem;
}

.macro-help {
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  padding: 1rem;
  background: var(--surface-50);
}

.macro-help-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.macro-help-title {
  font-weight: 600;
}

.macro-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 0.6rem 0.75rem;
  margin-top: 0.75rem;
}

.macro-item {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  padding: 0.55rem;
  border-radius: 8px;
  border: 1px solid var(--surface-border);
  background: var(--surface-card);
}

.macro-chip {
  align-self: flex-start;
  border: 1px solid var(--primary-300);
  background: var(--surface-50);
  color: var(--text-color);
  border-radius: 999px;
  padding: 0.22rem 0.6rem;
  cursor: pointer;
}

.macro-chip:hover {
  border-color: var(--primary-500);
}

.macro-description {
  font-size: 0.82rem;
}

.template-preview {
  border: 1px solid var(--surface-border);
  border-radius: 8px;
  padding: 0.8rem;
  background: var(--surface-50);
  display: grid;
  gap: 0.5rem;
}

.template-preview-title {
  font-weight: 600;
}

.preview-row {
  display: grid;
  gap: 0.25rem;
}

.preview-label {
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--text-color-secondary);
}

.preview-value {
  font-size: 0.9rem;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
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

  .rules-top-row {
    grid-template-columns: 1fr;
  }
}
</style>

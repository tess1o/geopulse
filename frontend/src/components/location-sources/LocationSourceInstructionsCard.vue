<template>
  <Card class="instructions-card">
    <template #title>
      <div class="flex items-center gap-2">
        <i class="pi pi-book text-blue-500"></i>
        Setup Instructions
      </div>
    </template>
    <template #content>
      <TabContainer
        v-if="tabItems.length > 0"
        :tabs="tabItems"
        :activeIndex="activeTabIndex"
        @tab-change="handleTabChange"
        class="instructions-tabs"
      >
        <div v-if="activeTab === 'owntracks-http' && hasOwnTracksHttp">
          <div class="instruction-content">
            <h3 class="instruction-title">OwnTracks Configuration (HTTP)</h3>
            <div class="instruction-steps">
              <div class="step">
                <div class="step-number">1</div>
                <div class="step-content">
                  <div class="step-title">Server URL</div>
                  <div class="copy-field">
                    <code>{{ owntracksUrl }}</code>
                    <Button icon="pi pi-copy" size="small" outlined @click="emitCopy(owntracksUrl)" />
                  </div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">2</div>
                <div class="step-content">
                  <div class="step-title">Connection Mode</div>
                  <div class="step-value">HTTP</div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">3</div>
                <div class="step-content">
                  <div class="step-title">Authentication</div>
                  <div class="step-value">Use your configured username</div>
                  <div class="step-value">Use your configured password</div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="activeTab === 'owntracks-mqtt' && hasOwnTracksMqtt">
          <div class="instruction-content">
            <h3 class="instruction-title">OwnTracks Configuration (MQTT)</h3>
            <div class="instruction-steps">
              <div class="step">
                <div class="step-number">1</div>
                <div class="step-content">
                  <div class="step-title">Connection Type</div>
                  <div class="step-value">Select <strong>MQTT</strong> in OwnTracks connection settings</div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">2</div>
                <div class="step-content">
                  <div class="step-title">MQTT Broker Host</div>
                  <div class="copy-field">
                    <code>{{ mqttHost }}</code>
                    <Button icon="pi pi-copy" size="small" outlined @click="emitCopy(mqttHost)" />
                  </div>
                  <small class="text-muted">Use your public host or IP address</small>
                </div>
              </div>

              <div class="step">
                <div class="step-number">3</div>
                <div class="step-content">
                  <div class="step-title">MQTT Port</div>
                  <div class="copy-field">
                    <code>1883</code>
                    <Button icon="pi pi-copy" size="small" outlined @click="emitCopy('1883')" />
                  </div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">4</div>
                <div class="step-content">
                  <div class="step-title">Authentication</div>
                  <div class="step-value">Use your configured username</div>
                  <div class="step-value">Use your configured password</div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">5</div>
                <div class="step-content">
                  <div class="step-title">Security Settings</div>
                  <div class="step-value">
                    TLS: <strong>Disabled</strong><br>
                    <small class="text-muted">Leave TLS/SSL settings unchecked</small>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="activeTab === 'overland' && hasOverlandSource">
          <div class="instruction-content">
            <h3 class="instruction-title">Overland Configuration</h3>
            <div class="instruction-steps">
              <div class="step">
                <div class="step-number">1</div>
                <div class="step-content">
                  <div class="step-title">Receiver Endpoint URL</div>
                  <div class="copy-field">
                    <code>{{ overlandUrl }}</code>
                    <Button icon="pi pi-copy" size="small" outlined @click="emitCopy(overlandUrl)" />
                  </div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">2</div>
                <div class="step-content">
                  <div class="step-title">Access Token</div>
                  <div class="copy-field">
                    <code>Your configured token</code>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="activeTab === 'gpslogger' && hasGpsLoggerSource">
          <div class="instruction-content">
            <h3 class="instruction-title">GPSLogger Configuration</h3>
            <div class="instruction-steps">
              <div class="step">
                <div class="step-number">1</div>
                <div class="step-content">
                  <div class="step-title">Enable Custom URL Logging</div>
                  <div class="step-value">
                    In GPSLogger, enable <strong>Log to custom URL</strong>.
                  </div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">2</div>
                <div class="step-content">
                  <div class="step-title">URL</div>
                  <div class="copy-field">
                    <code>{{ gpsLoggerUrl }}</code>
                    <Button icon="pi pi-copy" size="small" outlined @click="emitCopy(gpsLoggerUrl)" />
                  </div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">3</div>
                <div class="step-content">
                  <div class="step-title">HTTP Method</div>
                  <div class="step-value">POST</div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">4</div>
                <div class="step-content">
                  <div class="step-title">HTTP Body (JSON)</div>
                  <div class="copy-field">
                    <pre class="yaml-config">{{ gpsLoggerHttpBody }}</pre>
                    <Button icon="pi pi-copy" size="small" outlined @click="emitCopy(gpsLoggerHttpBody)" />
                  </div>
                  <small class="text-muted">
                    GeoPulse treats GPSLogger speed as m/s and converts it to km/h automatically.
                  </small>
                </div>
              </div>

              <div class="step">
                <div class="step-number">5</div>
                <div class="step-content">
                  <div class="step-title">Headers</div>
                  <div class="step-value">
                    Add <code>Content-Type: application/json</code><br>
                    Optional: <code>X-Limit-D: my-android-phone</code>
                  </div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">6</div>
                <div class="step-content">
                  <div class="step-title">Authentication</div>
                  <div class="step-value">
                    Enable <strong>Basic Authentication</strong> and use the username/password from this source.
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="activeTab === 'dawarich' && hasDawarichSource">
          <div class="instruction-content">
            <h3 class="instruction-title">Dawarich Configuration</h3>
            <div class="instruction-steps">
              <div class="step">
                <div class="step-number">1</div>
                <div class="step-content">
                  <div class="step-title">Server URL</div>
                  <div class="copy-field">
                    <code>{{ dawarichUrl }}</code>
                    <Button icon="pi pi-copy" size="small" outlined @click="emitCopy(dawarichUrl)" />
                  </div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">2</div>
                <div class="step-content">
                  <div class="step-title">API Key</div>
                  <div class="copy-field">
                    <code>Your configured API Key</code>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="activeTab === 'home_assistant' && hasHomeAssistantSource">
          <div class="instruction-content">
            <h3 class="instruction-title">Home Assistant Configuration</h3>
            <div class="instruction-steps">
              <div class="step">
                <div class="step-number">1</div>
                <div class="step-content">
                  <div class="step-title">In configuration.yaml add the following:</div>
                  <div class="copy-field">
                    <pre class="yaml-config">{{ homeAssistantConfigYaml }}</pre>
                    <Button icon="pi pi-copy" size="small" outlined @click="emitCopy(homeAssistantConfigYaml)" />
                  </div>
                  <div class="step-value">
                    <strong>Replace:</strong><br>
                    • iphone_16 with your device_id (can be found in Home Assistant)<br>
                    • YOUR_CONFIGURED_TOKEN with the token you just created in GeoPulse
                  </div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">2</div>
                <div class="step-content">
                  <div class="step-title">In automations.yaml add the following:</div>
                  <div class="copy-field">
                    <pre class="yaml-config">{{ homeAssistantAutomationYaml }}</pre>
                    <Button
                      icon="pi pi-copy"
                      size="small"
                      outlined
                      @click="emitCopy(homeAssistantAutomationYaml)"
                    />
                  </div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">3</div>
                <div class="step-content">
                  <div class="step-title">Restart Home Assistant server to apply the changes.</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </TabContainer>
    </template>
  </Card>
</template>

<script setup>
import { computed } from 'vue'

import TabContainer from '@/components/ui/layout/TabContainer.vue'

const props = defineProps({
  tabItems: {
    type: Array,
    default: () => []
  },
  activeTabIndex: {
    type: Number,
    default: 0
  },
  activeTab: {
    type: String,
    default: ''
  },
  hasOwnTracksHttp: {
    type: Boolean,
    default: false
  },
  hasOwnTracksMqtt: {
    type: Boolean,
    default: false
  },
  hasOverlandSource: {
    type: Boolean,
    default: false
  },
  hasGpsLoggerSource: {
    type: Boolean,
    default: false
  },
  hasDawarichSource: {
    type: Boolean,
    default: false
  },
  hasHomeAssistantSource: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['tab-change', 'copy-text'])

const browserOrigin = computed(() => (
  typeof window !== 'undefined' ? window.location.origin : ''
))

const mqttHost = computed(() => (
  typeof window !== 'undefined' ? window.location.hostname : ''
))

const owntracksUrl = computed(() => `${browserOrigin.value}/api/owntracks`)
const overlandUrl = computed(() => `${browserOrigin.value}/api/overland`)
const gpsLoggerUrl = computed(() => `${browserOrigin.value}/api/gpslogger`)
const dawarichUrl = computed(() => `${browserOrigin.value}/api/dawarich`)

const gpsLoggerHttpBody = computed(() => `{
  "_type": "location",
  "t": "u",
  "acc": "%ACC",
  "alt": "%ALT",
  "batt": "%BATT",
  "bs": "%ISCHARGING",
  "lat": "%LAT",
  "lon": "%LON",
  "tst": "%TIMESTAMP",
  "vel": "%SPD"
}`)

const homeAssistantConfigYaml = computed(() => `rest_command:
  send_gps_data:
    url: "${browserOrigin.value}/api/homeassistant"
    method: POST
    headers:
      content-type: "application/json"
      Authorization: Bearer YOUR_CONFIGURED_TOKEN
    payload: >
      {
        "device_id": "iphone_16",
        "timestamp": "{{ now().isoformat() }}",
        "location": {
          "latitude": {{ state_attr('device_tracker.iphone_16', 'latitude') }},
          "longitude": {{ state_attr('device_tracker.iphone_16', 'longitude') }},
          "accuracy": {{ state_attr('device_tracker.iphone_16', 'gps_accuracy') | default(0, true) }},
          "altitude": {{ state_attr('device_tracker.iphone_16', 'altitude') | default(0, true) }},
          "speed": {{ state_attr('device_tracker.iphone_16', 'speed') | default(0, true) }}
        },
        "battery": {
          "level": {{ state_attr('device_tracker.iphone_16', 'battery_level') | default(states('sensor.iphone_16_battery_level'), true) | default(0, true) }}
        }
      }`)

const homeAssistantAutomationYaml = computed(() => `- alias: Send GPS data to server
  trigger:
    - platform: state
      entity_id: device_tracker.iphone_16
  action:
    - service: rest_command.send_gps_data`)

const handleTabChange = (event) => {
  emit('tab-change', event)
}

const emitCopy = (text) => {
  emit('copy-text', text)
}
</script>

<style scoped>
.instructions-card {
  margin-bottom: 2rem;
}

.instruction-content {
  padding: 1rem 0;
  margin-left: 0.5rem;
  max-width: 100%;
  overflow: hidden;
}

.instruction-title {
  font-size: 1.2rem;
  font-weight: 600;
  color: var(--gp-text-primary);
  margin: 0 0 1.5rem 0;
}

.instruction-steps {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  max-width: 100%;
  overflow: hidden;
}

.step {
  display: flex;
  gap: 1rem;
  align-items: flex-start;
}

.step-number {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  background: var(--gp-primary);
  color: white;
  border-radius: 50%;
  font-weight: 600;
  font-size: 0.9rem;
  flex-shrink: 0;
}

.step-content {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.step-title {
  font-weight: 600;
  color: var(--gp-text-primary);
  margin-bottom: 0.5rem;
}

.step-value {
  color: var(--gp-text-secondary);
  line-height: 1.4;
}

.text-muted {
  color: var(--gp-text-muted, #6b7280);
  font-size: 0.85rem;
  margin-top: 0.25rem;
}

.copy-field {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 0.75rem;
  background: var(--gp-surface-light);
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-small);
  min-width: 0;
  overflow: hidden;
}

.copy-field code {
  flex: 1;
  font-family: var(--font-mono, monospace);
  font-size: 0.9rem;
  color: var(--gp-text-primary);
  word-break: break-all;
  min-width: 0;
  overflow-wrap: anywhere;
}

.yaml-config {
  flex: 1;
  font-family: var(--font-mono, monospace);
  font-size: 0.9rem;
  color: var(--gp-text-primary);
  white-space: pre-wrap;
  margin: 0;
  line-height: 1.4;
  min-width: 0;
  overflow-wrap: anywhere;
  word-break: break-word;
  max-width: 100%;
}

.p-dark .instruction-title {
  color: var(--gp-text-primary);
}

.p-dark .step-title {
  color: var(--gp-text-primary);
}

.p-dark .step-value {
  color: var(--gp-text-secondary);
}

.p-dark .copy-field {
  background: var(--gp-surface-darker);
  border-color: var(--gp-border-dark);
}

.p-dark .copy-field code {
  color: var(--gp-text-primary);
}

.p-dark .yaml-config {
  color: var(--gp-text-primary);
}

.p-dark .text-muted {
  color: var(--gp-text-muted, #9ca3af);
}

.p-dark .instructions-card {
  background: var(--gp-surface-dark) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .instructions-card :deep(.p-card) {
  background: var(--gp-surface-dark) !important;
  border-color: var(--gp-border-dark) !important;
}

.p-dark .instructions-card :deep(.p-card-header),
.p-dark .instructions-card :deep(.p-card-title-section) {
  background: var(--gp-surface-dark) !important;
  border-bottom-color: var(--gp-border-dark) !important;
}

.p-dark .instructions-card :deep(.p-card-title) {
  color: var(--gp-text-primary) !important;
}

.p-dark .instructions-card :deep(.p-card-content),
.p-dark .instructions-card :deep(.p-card-body) {
  background: var(--gp-surface-dark) !important;
  color: var(--gp-text-primary) !important;
}

@media (max-width: 768px) {
  .step {
    flex-direction: column;
    gap: 0.5rem;
  }

  .copy-field {
    flex-direction: column;
    align-items: stretch;
    gap: 0.75rem;
  }

  .step-value {
    word-break: break-word;
    overflow-wrap: anywhere;
  }

  .yaml-config {
    font-size: 0.8rem !important;
    line-height: 1.3;
  }
}
</style>

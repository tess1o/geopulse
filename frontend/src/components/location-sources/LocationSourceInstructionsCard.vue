<template>
  <Card class="instructions-card">
    <template #title>
      <div class="flex items-center gap-2">
        <i class="pi pi-book text-blue-500"></i>
        {{ t('locationSources.instructions.title') }}
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
            <h3 class="instruction-title">{{ t('locationSources.instructions.owntracksHttp.title') }}</h3>
            <div class="instruction-steps">
              <div class="step">
                <div class="step-number">1</div>
                <div class="step-content">
                  <div class="step-title">{{ t('locationSources.instructions.serverUrl') }}</div>
                  <div class="copy-field">
                    <code>{{ owntracksUrl }}</code>
                    <Button icon="pi pi-copy" size="small" outlined @click="emitCopy(owntracksUrl)" />
                  </div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">2</div>
                <div class="step-content">
                  <div class="step-title">{{ t('locationSources.instructions.connectionMode') }}</div>
                  <div class="step-value">{{ t('locationSources.instructions.http') }}</div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">3</div>
                <div class="step-content">
                  <div class="step-title">{{ t('locationSources.instructions.authentication') }}</div>
                  <div class="step-value">{{ t('locationSources.instructions.useUsername') }}</div>
                  <div class="step-value">{{ t('locationSources.instructions.usePassword') }}</div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">4</div>
                <div class="step-content">
                  <div class="step-title">{{ t('locationSources.instructions.payloadEncryption') }}</div>
                  <div class="step-value">
                    <i18n-t keypath="locationSources.instructions.payloadEncryptionHint" tag="span">
                      <template #code><code>encryptionKey</code></template>
                    </i18n-t>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="activeTab === 'owntracks-mqtt' && hasOwnTracksMqtt">
          <div class="instruction-content">
            <h3 class="instruction-title">{{ t('locationSources.instructions.owntracksMqtt.title') }}</h3>
            <div class="instruction-steps">
              <div class="step">
                <div class="step-number">1</div>
                <div class="step-content">
                  <div class="step-title">{{ t('locationSources.instructions.owntracksMqtt.connectionTypeStep') }}</div>
                  <div class="step-value">
                    <i18n-t keypath="locationSources.instructions.owntracksMqtt.selectMqtt" tag="span">
                      <template #mqtt><strong>MQTT</strong></template>
                    </i18n-t>
                  </div>
                  <small v-if="!mqttServiceEnabled" class="text-muted">
                    {{ t('locationSources.instructions.owntracksMqtt.mqttDisabledHint') }}
                  </small>
                </div>
              </div>

              <div class="step">
                <div class="step-number">2</div>
                <div class="step-content">
                  <div class="step-title">{{ t('locationSources.instructions.owntracksMqtt.brokerHost') }}</div>
                  <div class="copy-field">
                    <code>{{ mqttBrokerHost }}</code>
                    <Button
                      icon="pi pi-copy"
                      size="small"
                      outlined
                      :disabled="!canCopyMqttHost"
                      @click="emitCopy(mqttBrokerHost)"
                    />
                  </div>
                  <small class="text-muted">{{ t('locationSources.instructions.owntracksMqtt.brokerHostHint') }}</small>
                </div>
              </div>

              <div class="step">
                <div class="step-number">3</div>
                <div class="step-content">
                  <div class="step-title">{{ t('locationSources.instructions.owntracksMqtt.brokerPort') }}</div>
                  <div class="copy-field">
                    <code>{{ mqttBrokerPort }}</code>
                    <Button
                      icon="pi pi-copy"
                      size="small"
                      outlined
                      :disabled="!canCopyMqttPort"
                      @click="emitCopy(mqttBrokerPort)"
                    />
                  </div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">4</div>
                <div class="step-content">
                  <div class="step-title">{{ t('locationSources.instructions.authentication') }}</div>
                  <div class="step-value">{{ t('locationSources.instructions.useUsername') }}</div>
                  <div class="step-value">{{ t('locationSources.instructions.usePassword') }}</div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">5</div>
                <div class="step-content">
                  <div class="step-title">{{ t('locationSources.instructions.owntracksMqtt.securitySettings') }}</div>
                  <div class="step-value">
                    {{ t('locationSources.instructions.owntracksMqtt.tls') }} <strong>{{ mqttTlsEnabled ? t('locationSources.instructions.owntracksMqtt.tlsEnabled') : t('locationSources.instructions.owntracksMqtt.tlsDisabled') }}</strong><br>
                    <small class="text-muted">{{ mqttTlsHint }}</small>
                  </div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">6</div>
                <div class="step-content">
                  <div class="step-title">{{ t('locationSources.instructions.payloadEncryption') }}</div>
                  <div class="step-value">
                    <i18n-t keypath="locationSources.instructions.payloadEncryptionHint" tag="span">
                      <template #code><code>encryptionKey</code></template>
                    </i18n-t>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="activeTab === 'overland' && hasOverlandSource">
          <div class="instruction-content">
            <h3 class="instruction-title">{{ t('locationSources.instructions.overland.title') }}</h3>
            <div class="instruction-steps">
              <div class="step">
                <div class="step-number">1</div>
                <div class="step-content">
                  <div class="step-title">{{ t('locationSources.instructions.overland.receiverEndpointUrl') }}</div>
                  <div class="copy-field">
                    <code>{{ overlandUrl }}</code>
                    <Button icon="pi pi-copy" size="small" outlined @click="emitCopy(overlandUrl)" />
                  </div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">2</div>
                <div class="step-content">
                  <div class="step-title">{{ t('locationSources.instructions.overland.accessToken') }}</div>
                  <div class="copy-field">
                    <code>{{ t('locationSources.instructions.overland.yourConfiguredToken') }}</code>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="activeTab === 'traccar' && hasTraccarSource">
          <div class="instruction-content">
            <h3 class="instruction-title">{{ t('locationSources.instructions.traccar.title') }}</h3>
            <div class="instruction-steps">
              <div class="step">
                <div class="step-number">1</div>
                <div class="step-content">
                  <div class="step-title">
                    <i18n-t keypath="locationSources.instructions.traccar.updateXmlTitle" tag="span">
                      <template #file><code>traccar.xml</code></template>
                    </i18n-t>
                  </div>
                  <div class="step-value">
                    <i18n-t keypath="locationSources.instructions.traccar.updateXmlValue" tag="span">
                      <template #forwardUrl><code>forward.url</code></template>
                      <template #forwardType><code>forward.type=json</code></template>
                      <template #forwardHeader><code>forward.header</code></template>
                      <template #file><code>traccar.xml</code></template>
                    </i18n-t>
                  </div>
                  <div class="copy-field">
                    <pre class="yaml-config">{{ traccarXmlSnippet }}</pre>
                    <Button icon="pi pi-copy" size="small" outlined @click="emitCopy(traccarXmlSnippet)" />
                  </div>
                  <small class="text-muted">
                    {{ t('locationSources.instructions.traccar.officialDocs') }}
                    <a href="https://www.traccar.org/forward/" target="_blank" rel="noopener noreferrer">
                      https://www.traccar.org/forward/
                    </a>
                  </small>
                </div>
              </div>

              <div class="step">
                <div class="step-number">2</div>
                <div class="step-content">
                  <div class="step-title">{{ t('locationSources.instructions.traccar.sharedTokenTitle') }}</div>
                  <div class="step-value">
                    <i18n-t keypath="locationSources.instructions.traccar.sharedTokenValue" tag="span">
                      <template #deviceUniqueId><code>{{ t('locationSources.instructions.deviceUniqueIdShort') }}</code></template>
                      <template #uniqueId><code>uniqueId</code></template>
                    </i18n-t>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="activeTab === 'gpslogger' && hasGpsLoggerSource">
          <div class="instruction-content">
            <h3 class="instruction-title">{{ t('locationSources.instructions.gpslogger.title') }}</h3>
            <div class="instruction-steps">
              <div class="step">
                <div class="step-number">1</div>
                <div class="step-content">
                  <div class="step-title">{{ t('locationSources.instructions.gpslogger.enableCustomUrlTitle') }}</div>
                  <div class="step-value">
                    <i18n-t keypath="locationSources.instructions.gpslogger.enableCustomUrlValue" tag="span">
                      <template #logToCustomUrl><strong>{{ t('locationSources.instructions.gpslogger.logToCustomUrl') }}</strong></template>
                    </i18n-t>
                  </div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">2</div>
                <div class="step-content">
                  <div class="step-title">{{ t('locationSources.instructions.gpslogger.url') }}</div>
                  <div class="copy-field">
                    <code>{{ gpsLoggerUrl }}</code>
                    <Button icon="pi pi-copy" size="small" outlined @click="emitCopy(gpsLoggerUrl)" />
                  </div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">3</div>
                <div class="step-content">
                  <div class="step-title">{{ t('locationSources.instructions.gpslogger.httpMethod') }}</div>
                  <div class="step-value">{{ t('locationSources.instructions.gpslogger.post') }}</div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">4</div>
                <div class="step-content">
                  <div class="step-title">{{ t('locationSources.instructions.gpslogger.httpBody') }}</div>
                  <div class="copy-field">
                    <pre class="yaml-config">{{ gpsLoggerHttpBody }}</pre>
                    <Button icon="pi pi-copy" size="small" outlined @click="emitCopy(gpsLoggerHttpBody)" />
                  </div>
                  <small class="text-muted">
                    {{ t('locationSources.instructions.gpslogger.httpBodyHint') }}
                  </small>
                </div>
              </div>

              <div class="step">
                <div class="step-number">5</div>
                <div class="step-content">
                  <div class="step-title">{{ t('locationSources.instructions.gpslogger.headers') }}</div>
                  <div class="step-value">
                    {{ t('locationSources.instructions.gpslogger.headersValue') }}
                  </div>
                  <div class="copy-field">
                    <code>Content-Type: application/json</code>
                  </div>
                  <small class="text-muted">
                    {{ t('locationSources.instructions.gpslogger.optionalDeviceIdHeader') }}
                  </small>
                  <div class="copy-field">
                    <code>X-Limit-D: my-android-phone</code>
                  </div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">6</div>
                <div class="step-content">
                  <div class="step-title">{{ t('locationSources.instructions.authentication') }}</div>
                  <div class="step-value">
                    <i18n-t keypath="locationSources.instructions.gpslogger.authenticationValue" tag="span">
                      <template #basicAuth><strong>{{ t('locationSources.instructions.basicAuthentication') }}</strong></template>
                    </i18n-t>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="activeTab === 'dawarich' && hasDawarichSource">
          <div class="instruction-content">
            <h3 class="instruction-title">{{ t('locationSources.instructions.dawarich.title') }}</h3>
            <div class="instruction-steps">
              <div class="step">
                <div class="step-number">1</div>
                <div class="step-content">
                  <div class="step-title">{{ t('locationSources.instructions.serverUrl') }}</div>
                  <div class="copy-field">
                    <code>{{ dawarichUrl }}</code>
                    <Button icon="pi pi-copy" size="small" outlined @click="emitCopy(dawarichUrl)" />
                  </div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">2</div>
                <div class="step-content">
                  <div class="step-title">{{ t('locationSources.instructions.dawarich.apiKey') }}</div>
                  <div class="copy-field">
                    <code>{{ t('locationSources.instructions.dawarich.yourConfiguredApiKey') }}</code>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="activeTab === 'home_assistant' && hasHomeAssistantSource">
          <div class="instruction-content">
            <h3 class="instruction-title">{{ t('locationSources.instructions.homeAssistant.title') }}</h3>
            <div class="instruction-steps">
              <div class="step">
                <div class="step-number">1</div>
                <div class="step-content">
                  <div class="step-title">{{ t('locationSources.instructions.homeAssistant.configYamlTitle') }}</div>
                  <div class="copy-field">
                    <pre class="yaml-config">{{ homeAssistantConfigYaml }}</pre>
                    <Button icon="pi pi-copy" size="small" outlined @click="emitCopy(homeAssistantConfigYaml)" />
                  </div>
                  <div class="step-value">
                    <strong>{{ t('locationSources.instructions.homeAssistant.replaceTitle') }}</strong><br>
                    • {{ t('locationSources.instructions.homeAssistant.replaceDeviceId') }}<br>
                    • {{ t('locationSources.instructions.homeAssistant.replaceToken') }}
                  </div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">2</div>
                <div class="step-content">
                  <div class="step-title">{{ t('locationSources.instructions.homeAssistant.automationYamlTitle') }}</div>
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
                  <div class="step-title">{{ t('locationSources.instructions.homeAssistant.restartTitle') }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div v-if="activeTab === 'colota' && hasColotaSource">
          <div class="instruction-content">
            <h3 class="instruction-title">{{ t('locationSources.instructions.colota.title') }}</h3>
            <div class="instruction-steps">
              <div class="step">
                <div class="step-number">1</div>
                <div class="step-content">
                  <div class="step-title">{{ t('locationSources.instructions.colota.apiEndpoint') }}</div>
                  <div class="copy-field">
                    <code>{{ colotaUrl }}</code>
                    <Button icon="pi pi-copy" size="small" outlined @click="emitCopy(colotaUrl)" />
                  </div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">2</div>
                <div class="step-content">
                  <div class="step-title">{{ t('locationSources.instructions.authentication') }}</div>
                  <div class="step-value">
                    <i18n-t keypath="locationSources.instructions.colota.authenticationValue" tag="span">
                      <template #basicAuth><strong>{{ t('locationSources.instructions.basicAuthentication') }}</strong></template>
                    </i18n-t>
                  </div>
                </div>
              </div>

              <div class="step">
                <div class="step-number">3</div>
                <div class="step-content">
                  <div class="step-title">{{ t('locationSources.instructions.colota.payloadFormat') }}</div>
                  <div class="copy-field">
                    <pre class="yaml-config">{{ colotaPayloadExample }}</pre>
                  </div>
                  <small class="text-muted">
                    {{ t('locationSources.instructions.colota.payloadFieldsHint') }}
                  </small>
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
import { useI18n } from 'vue-i18n'

import TabContainer from '@/components/ui/layout/TabContainer.vue'

const { t } = useI18n()

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
  ownTracksMqttConfig: {
    type: Object,
    default: null
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
  hasTraccarSource: {
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
  },
  hasColotaSource: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['tab-change', 'copy-text'])

const browserOrigin = computed(() => (
  typeof window !== 'undefined' ? window.location.origin : ''
))

const mqttBrokerHost = computed(() => {
  const host = props.ownTracksMqttConfig?.brokerHost
  return typeof host === 'string' && host.trim().length > 0 ? host.trim() : t('locationSources.instructions.owntracksMqtt.notConfigured')
})

const mqttBrokerPort = computed(() => {
  const port = props.ownTracksMqttConfig?.brokerPort
  return Number.isInteger(port) && port > 0 ? String(port) : t('locationSources.instructions.owntracksMqtt.notConfigured')
})

const mqttTlsEnabled = computed(() => Boolean(props.ownTracksMqttConfig?.tlsEnabled))
const mqttServiceEnabled = computed(() => Boolean(props.ownTracksMqttConfig?.mqttEnabled))
const canCopyMqttHost = computed(() => mqttBrokerHost.value !== t('locationSources.instructions.owntracksMqtt.notConfigured'))
const canCopyMqttPort = computed(() => mqttBrokerPort.value !== t('locationSources.instructions.owntracksMqtt.notConfigured'))
const mqttTlsHint = computed(() => (
  mqttTlsEnabled.value
    ? t('locationSources.instructions.owntracksMqtt.tlsHintEnabled')
    : t('locationSources.instructions.owntracksMqtt.tlsHintDisabled')
))

const owntracksUrl = computed(() => `${browserOrigin.value}/api/v1/gps/ingest/owntracks`)
const overlandUrl = computed(() => `${browserOrigin.value}/api/v1/gps/ingest/overland`)
const traccarUrl = computed(() => `${browserOrigin.value}/api/v1/gps/ingest/traccar`)
const gpsLoggerUrl = computed(() => `${browserOrigin.value}/api/v1/gps/ingest/gpslogger`)
const dawarichUrl = computed(() => `${browserOrigin.value}/api/v1/gps/ingest/dawarich`)
const colotaUrl = computed(() => `${browserOrigin.value}/api/v1/gps/ingest/colota`)

const traccarXmlSnippet = computed(() => `<entry key='forward.enable'>true</entry>
<entry key='forward.type'>json</entry>
<entry key='forward.url'>${traccarUrl.value}</entry>
<entry key='forward.header'>Authorization: Bearer YOUR_CONFIGURED_TOKEN</entry>`)

const colotaPayloadExample = computed(() => `{
  "lat": 48.135,
  "lon": 11.582,
  "acc": 12,
  "alt": 519,
  "vel": 0,
  "batt": 85,
  "bs": 2,
  "tst": 1704067200,
  "bear": 180.5
}`)

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
    url: "${browserOrigin.value}/api/v1/gps/ingest/home-assistant"
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
  color: var(--gp-text-muted, #9ca3af);
  font-size: 0.85rem;
  margin-top: 0.25rem;
}

.copy-field {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 0.75rem;
  background: var(--gp-surface-ground);
  border: 1px solid var(--gp-border);
  border-radius: var(--gp-radius-small);
  min-width: 0;
  overflow: hidden;
}

.copy-field code {
  flex: 1;
  font-family: var(--gp-font-mono, monospace);
  font-size: 0.9rem;
  color: var(--gp-text-primary);
  word-break: break-all;
  min-width: 0;
  overflow-wrap: anywhere;
}

.yaml-config {
  flex: 1;
  font-family: var(--gp-font-mono, monospace);
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
    font-size: 0.8rem;
    line-height: 1.3;
  }
}
</style>

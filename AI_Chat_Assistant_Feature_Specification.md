# AI Chat Assistant Feature - Technical Specification

## Overview

The AI Chat Assistant is an **optional** feature that allows users to have natural language conversations with their location data. Users must explicitly enable this feature and configure their own AI provider (OpenAI API key or custom Ollama endpoint) in their profile settings.

## Implementation Architecture Decision

**Selected Approach: Structured Functions/Tools (LangChain4j)**

After analysis of GeoPulse's data model and scale (100-200K+ GPS points), **structured AI functions approach using LangChain4j** is recommended over text-to-SQL for the following reasons:

### Why Functions > Text-to-SQL:
- **Leverages Existing Optimized Services**: Reuses `StatisticsService`, `PlacesAnalysisService`, `StreamingTimelineAggregator`, etc.
- **Handles Complex Analytics**: Queries like "Do I travel more on Sundays vs Saturdays?" are handled reliably through existing analytical services
- **Maintains Security**: Uses existing user permissions, validation, and prevents SQL injection
- **Scales Efficiently**: Leverages existing optimized repositories and spatial indexing
- **Type-Safe & Maintainable**: Java-based tool definitions with compile-time validation
- **Handles Spatial Complexity**: Existing services manage PostGIS spatial queries and complex relationships

### LangChain4j Framework Benefits:
- **Native Java Integration**: Seamless integration with Quarkus/Jakarta EE stack
- **Built-in Function Calling**: Automatic tool definitions using `@Tool` annotations
- **Multi-Provider Support**: OpenAI, Ollama, and custom endpoints
- **Type Safety**: Java method signatures become AI tool parameters
- **Quarkus Optimization**: Native compilation and performance benefits

## Feature Philosophy

### Core Principles
- **Opt-in Only**: Disabled by default, requires explicit user activation
- **User-Controlled**: Users provide their own API keys/endpoints
- **Privacy-First**: Data processing preferences controlled by user
- **Standalone**: Completely independent of other GeoPulse features
- **Configurable**: Support multiple AI providers (OpenAI, Ollama, custom endpoints)

## Feature Placement

**Navigation Location**: Drawer Navigation → Account & Settings section

```
Account & Settings
├── Profile
├── Location Sources  
├── Share Links
├── Export / Import
├── GPS Data
├── AI Assistant        ← NEW FEATURE
└── Preferences
```

**Why Drawer Navigation:**
- Optional feature requiring configuration
- Not needed by all users
- Consistent with other advanced features (Export/Import, GPS Data)
- Keeps main tabs focused on core functionality

## Architecture Overview

```
AI Chat Assistant Feature
├── Profile Settings (Enable/Configure AI)
├── AI Chat Page (Conversation Interface)  
├── Backend AI Tools API (Data Query Functions)
├── AI Provider Integration (OpenAI/Ollama/Custom)
└── Privacy Controls (Data Handling Preferences)
```

## Technical Implementation

### 1. User Profile Settings Integration

```vue
<!-- Update /src/views/app/UserProfilePage.vue to add AI tab -->
<template>
  <PageContainer title="User Profile">
    <TabContainer :tabs="profileTabs" @tab-change="handleTabChange">
      
      <!-- Existing tabs: Account, Privacy, etc. -->
      
      <!-- NEW: AI Assistant Tab -->
      <div v-if="activeTab === 'ai-assistant'" class="ai-assistant-settings">
        <BaseCard title="AI Assistant Configuration" class="ai-config-card">
          
          <!-- Enable/Disable AI Assistant -->
          <div class="setting-section">
            <div class="setting-header">
              <h4 class="setting-title">AI Assistant</h4>
              <ToggleSwitch 
                v-model="aiSettings.enabled"
                @change="handleAIToggle"
              />
            </div>
            <p class="setting-description">
              Enable AI-powered natural language queries for your location data. 
              You must configure your own AI provider below.
            </p>
          </div>

          <!-- AI Provider Configuration (only shown when enabled) -->
          <div v-if="aiSettings.enabled" class="ai-provider-config">
            
            <!-- Provider Selection -->
            <div class="setting-section">
              <h5 class="setting-subtitle">AI Provider</h5>
              <RadioButton 
                v-model="aiSettings.provider" 
                inputId="openai" 
                value="openai" 
              />
              <label for="openai" class="provider-label">
                <span class="provider-name">OpenAI (GPT-4/GPT-3.5)</span>
                <span class="provider-description">Use OpenAI's API with your own API key</span>
              </label>

              <RadioButton 
                v-model="aiSettings.provider" 
                inputId="ollama" 
                value="ollama" 
              />
              <label for="ollama" class="provider-label">
                <span class="provider-name">Ollama (Local)</span>
                <span class="provider-description">Use local Ollama installation for privacy</span>
              </label>

              <RadioButton 
                v-model="aiSettings.provider" 
                inputId="custom" 
                value="custom" 
              />
              <label for="custom" class="provider-label">
                <span class="provider-name">Custom Endpoint</span>
                <span class="provider-description">Use any OpenAI-compatible API endpoint</span>
              </label>
            </div>

            <!-- OpenAI Configuration -->
            <div v-if="aiSettings.provider === 'openai'" class="provider-settings">
              <div class="setting-section">
                <label for="openai-key" class="setting-label">OpenAI API Key</label>
                <div class="api-key-input">
                  <Password 
                    id="openai-key"
                    v-model="aiSettings.openaiApiKey"
                    placeholder="sk-..."
                    :feedback="false"
                    toggleMask
                    class="api-key-field"
                  />
                  <Button 
                    label="Test Connection" 
                    outlined
                    @click="testOpenAIConnection"
                    :loading="testingConnection"
                    class="test-button"
                  />
                </div>
                <small class="setting-note">
                  Your API key is stored securely and only used for AI requests. 
                  <a href="https://platform.openai.com/api-keys" target="_blank">Get your API key</a>
                </small>
              </div>

              <div class="setting-section">
                <label for="openai-model" class="setting-label">Model</label>
                <Dropdown
                  id="openai-model"
                  v-model="aiSettings.openaiModel"
                  :options="openaiModels"
                  optionLabel="label"
                  optionValue="value"
                  placeholder="Select model"
                />
                <small class="setting-note">
                  GPT-4 provides better accuracy but costs more. GPT-3.5-turbo is faster and cheaper.
                </small>
              </div>
            </div>

            <!-- Ollama Configuration -->
            <div v-if="aiSettings.provider === 'ollama'" class="provider-settings">
              <div class="setting-section">
                <label for="ollama-url" class="setting-label">Ollama URL</label>
                <InputText 
                  id="ollama-url"
                  v-model="aiSettings.ollamaUrl"
                  placeholder="http://localhost:11434"
                />
                <small class="setting-note">
                  URL to your Ollama installation. Default: http://localhost:11434
                </small>
              </div>

              <div class="setting-section">
                <label for="ollama-model" class="setting-label">Model</label>
                <div class="model-input">
                  <InputText 
                    id="ollama-model"
                    v-model="aiSettings.ollamaModel"
                    placeholder="llama3.1:8b"
                  />
                  <Button 
                    label="Test Connection" 
                    outlined
                    @click="testOllamaConnection"
                    :loading="testingConnection"
                    class="test-button"
                  />
                </div>
                <small class="setting-note">
                  Model name in Ollama. Example: llama3.1:8b, codellama:13b
                </small>
              </div>
            </div>

            <!-- Custom Endpoint Configuration -->
            <div v-if="aiSettings.provider === 'custom'" class="provider-settings">
              <div class="setting-section">
                <label for="custom-url" class="setting-label">API Endpoint</label>
                <InputText 
                  id="custom-url"
                  v-model="aiSettings.customUrl"
                  placeholder="https://api.your-provider.com/v1"
                />
              </div>

              <div class="setting-section">
                <label for="custom-key" class="setting-label">API Key</label>
                <Password 
                  id="custom-key"
                  v-model="aiSettings.customApiKey"
                  :feedback="false"
                  toggleMask
                />
              </div>

              <div class="setting-section">
                <label for="custom-model" class="setting-label">Model Name</label>
                <InputText 
                  id="custom-model"
                  v-model="aiSettings.customModel"
                  placeholder="gpt-4"
                />
              </div>
            </div>

            <!-- Privacy Settings -->
            <div class="setting-section privacy-settings">
              <h5 class="setting-subtitle">Privacy Controls</h5>
              
              <div class="privacy-option">
                <Checkbox 
                  v-model="aiSettings.allowLocationNames" 
                  inputId="allow-locations"
                  :binary="true"
                />
                <label for="allow-locations" class="privacy-label">
                  Send location names to AI provider
                </label>
                <p class="privacy-description">
                  When enabled, actual location names (restaurants, addresses) are sent to the AI. 
                  When disabled, locations are anonymized (e.g., "Location A", "Restaurant B").
                </p>
              </div>

              <div class="privacy-option">
                <Checkbox 
                  v-model="aiSettings.allowCoordinates" 
                  inputId="allow-coordinates"
                  :binary="true"
                />
                <label for="allow-coordinates" class="privacy-label">
                  Send approximate coordinates to AI provider
                </label>
                <p class="privacy-description">
                  When enabled, rounded coordinates (±100m accuracy) are sent for location context.
                  When disabled, only relative distances and directions are provided.
                </p>
              </div>

              <div class="privacy-option">
                <Checkbox 
                  v-model="aiSettings.saveConversations" 
                  inputId="save-conversations"
                  :binary="true"
                />
                <label for="save-conversations" class="privacy-label">
                  Save conversation history locally
                </label>
                <p class="privacy-description">
                  When enabled, your AI conversations are saved in your GeoPulse account for reference.
                  When disabled, conversations are not persisted after your session ends.
                </p>
              </div>
            </div>

            <!-- Usage Limits -->
            <div class="setting-section">
              <h5 class="setting-subtitle">Usage Limits</h5>
              <div class="usage-limit">
                <label for="daily-limit" class="setting-label">Daily query limit</label>
                <InputNumber 
                  id="daily-limit"
                  v-model="aiSettings.dailyQueryLimit"
                  :min="1"
                  :max="1000"
                  suffix=" queries"
                />
                <small class="setting-note">
                  Prevents unexpected API costs. Resets daily at midnight.
                </small>
              </div>
            </div>

          </div>

          <!-- Save Settings -->
          <div class="settings-actions">
            <Button 
              label="Save AI Settings" 
              @click="saveAISettings"
              :loading="savingSettings"
              :disabled="!isAIConfigValid"
            />
          </div>

        </BaseCard>
      </div>
    </TabContainer>
  </PageContainer>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const toast = useToast()

// Add 'ai-assistant' to existing profile tabs
const profileTabs = ref([
  { label: 'Account', key: 'account' },
  { label: 'Privacy', key: 'privacy' },
  { label: 'AI Assistant', key: 'ai-assistant' }  // NEW TAB
])

// AI Settings State
const aiSettings = ref({
  enabled: false,
  provider: 'openai',
  openaiApiKey: '',
  openaiModel: 'gpt-3.5-turbo',
  ollamaUrl: 'http://localhost:11434',
  ollamaModel: 'llama3.1:8b',
  customUrl: '',
  customApiKey: '',
  customModel: '',
  allowLocationNames: true,
  allowCoordinates: false,
  saveConversations: true,
  dailyQueryLimit: 50
})

const openaiModels = ref([
  { label: 'GPT-4 (Best quality, higher cost)', value: 'gpt-4' },
  { label: 'GPT-3.5 Turbo (Fast, cost-effective)', value: 'gpt-3.5-turbo' }
])

const testingConnection = ref(false)
const savingSettings = ref(false)

// Computed
const isAIConfigValid = computed(() => {
  if (!aiSettings.value.enabled) return true
  
  switch (aiSettings.value.provider) {
    case 'openai':
      return aiSettings.value.openaiApiKey.trim().length > 0
    case 'ollama':
      return aiSettings.value.ollamaUrl.trim().length > 0 && aiSettings.value.ollamaModel.trim().length > 0
    case 'custom':
      return aiSettings.value.customUrl.trim().length > 0 && aiSettings.value.customModel.trim().length > 0
    default:
      return false
  }
})

// Methods
const handleAIToggle = (enabled) => {
  if (!enabled) {
    // When disabling AI, clear sensitive data
    aiSettings.value.openaiApiKey = ''
    aiSettings.value.customApiKey = ''
  }
}

const testOpenAIConnection = async () => {
  testingConnection.value = true
  try {
    const response = await fetch('/api/ai/test-openai', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        apiKey: aiSettings.value.openaiApiKey,
        model: aiSettings.value.openaiModel
      })
    })
    
    if (response.ok) {
      toast.add({
        severity: 'success',
        summary: 'Connection Successful',
        detail: 'OpenAI API connection tested successfully',
        life: 3000
      })
    } else {
      throw new Error('Connection failed')
    }
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Connection Failed',
      detail: 'Could not connect to OpenAI API. Check your API key.',
      life: 5000
    })
  } finally {
    testingConnection.value = false
  }
}

const testOllamaConnection = async () => {
  testingConnection.value = true
  try {
    const response = await fetch('/api/ai/test-ollama', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        url: aiSettings.value.ollamaUrl,
        model: aiSettings.value.ollamaModel
      })
    })
    
    if (response.ok) {
      toast.add({
        severity: 'success',
        summary: 'Connection Successful',
        detail: 'Ollama connection tested successfully',
        life: 3000
      })
    } else {
      throw new Error('Connection failed')
    }
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Connection Failed',
      detail: 'Could not connect to Ollama. Check URL and model name.',
      life: 5000
    })
  } finally {
    testingConnection.value = false
  }
}

const saveAISettings = async () => {
  savingSettings.value = true
  try {
    await userStore.updateAISettings(aiSettings.value)
    toast.add({
      severity: 'success',
      summary: 'Settings Saved',
      detail: 'AI Assistant settings saved successfully',
      life: 3000
    })
  } catch (error) {
    toast.add({
      severity: 'error',
      summary: 'Save Failed',
      detail: 'Could not save AI settings',
      life: 5000
    })
  } finally {
    savingSettings.value = false
  }
}

// Load existing settings on mount
onMounted(async () => {
  try {
    const settings = await userStore.getAISettings()
    if (settings) {
      aiSettings.value = { ...aiSettings.value, ...settings }
    }
  } catch (error) {
    console.error('Failed to load AI settings:', error)
  }
})
</script>
```

### 2. AI Chat Page

```vue
<!-- /src/views/app/AIChatPage.vue -->
<template>
  <PageContainer 
    title="AI Assistant" 
    subtitle="Ask questions about your location data in natural language"
  >
    <div class="ai-chat-page">
      
      <!-- AI Not Configured State -->
      <BaseCard v-if="!isAIConfigured" title="AI Assistant Not Configured" class="config-prompt-card">
        <div class="config-prompt">
          <i class="pi pi-exclamation-triangle config-icon"></i>
          <h4 class="config-title">AI Assistant Requires Configuration</h4>
          <p class="config-message">
            To use the AI Assistant, you need to enable and configure it in your profile settings.
            You'll need to provide your own OpenAI API key or configure a local Ollama installation.
          </p>
          <Button 
            label="Configure AI Settings" 
            icon="pi pi-cog"
            @click="router.push('/app/profile?tab=ai-assistant')"
            class="config-button"
          />
        </div>
      </BaseCard>

      <!-- AI Chat Interface -->
      <div v-else class="chat-interface">
        
        <!-- Chat Messages Area -->
        <BaseCard class="chat-messages-card">
          <div class="chat-messages" ref="messagesContainer">
            
            <!-- Welcome Message -->
            <div v-if="messages.length === 0" class="welcome-message">
              <div class="ai-avatar">
                <i class="pi pi-android"></i>
              </div>
              <div class="welcome-content">
                <h4 class="welcome-title">Hi! I'm your GeoPulse AI Assistant</h4>
                <p class="welcome-text">
                  I can help you explore your location data. Try asking questions like:
                </p>
                <div class="example-queries">
                  <Chip 
                    v-for="example in exampleQueries"
                    :key="example"
                    :label="example"
                    @click="sendExampleQuery(example)"
                    class="example-chip"
                  />
                </div>
              </div>
            </div>

            <!-- Chat Messages -->
            <div 
              v-for="message in messages"
              :key="message.id"
              :class="['chat-message', `message-${message.role}`]"
            >
              <div class="message-avatar">
                <i :class="message.role === 'user' ? 'pi pi-user' : 'pi pi-android'"></i>
              </div>
              <div class="message-content">
                <div class="message-text" v-html="formatMessageText(message.content)"></div>
                
                <!-- Data Attachments (charts, tables, etc.) -->
                <div v-if="message.attachments" class="message-attachments">
                  <div 
                    v-for="attachment in message.attachments"
                    :key="attachment.id"
                    class="attachment"
                  >
                    <!-- Data Table Attachment -->
                    <DataTable 
                      v-if="attachment.type === 'table'"
                      :value="attachment.data"
                      :paginator="attachment.data.length > 10"
                      :rows="10"
                      class="attachment-table"
                    >
                      <Column 
                        v-for="col in attachment.columns"
                        :key="col.field"
                        :field="col.field"
                        :header="col.header"
                      />
                    </DataTable>

                    <!-- Chart Attachment -->
                    <div v-if="attachment.type === 'chart'" class="attachment-chart">
                      <!-- Chart component would go here -->
                      <p>Chart: {{ attachment.title }}</p>
                    </div>

                    <!-- Map Attachment -->
                    <div v-if="attachment.type === 'map'" class="attachment-map">
                      <Button 
                        label="Show on Map" 
                        icon="pi pi-map"
                        @click="showOnMap(attachment.mapData)"
                        outlined
                      />
                    </div>
                  </div>
                </div>

                <!-- Message Actions -->
                <div v-if="message.role === 'assistant'" class="message-actions">
                  <Button 
                    label="Export Data" 
                    icon="pi pi-download"
                    text
                    size="small"
                    @click="exportMessageData(message)"
                    v-if="message.attachments"
                  />
                  <Button 
                    label="Show on Map" 
                    icon="pi pi-map"
                    text
                    size="small"
                    @click="showMessageOnMap(message)"
                    v-if="hasLocationData(message)"
                  />
                </div>

                <div class="message-timestamp">
                  {{ formatTimestamp(message.timestamp) }}
                </div>
              </div>
            </div>

            <!-- Typing Indicator -->
            <div v-if="isAIThinking" class="chat-message message-assistant typing-indicator">
              <div class="message-avatar">
                <i class="pi pi-android"></i>
              </div>
              <div class="message-content">
                <div class="typing-animation">
                  <span></span>
                  <span></span>
                  <span></span>
                </div>
              </div>
            </div>
          </div>
        </BaseCard>

        <!-- Chat Input Area -->
        <BaseCard class="chat-input-card">
          <div class="chat-input-area">
            <div class="input-container">
              <InputText 
                v-model="currentMessage"
                placeholder="Ask me anything about your location data..."
                @keyup.enter="sendMessage"
                :disabled="isAIThinking || dailyLimitReached"
                class="message-input"
              />
              <Button 
                icon="pi pi-send"
                @click="sendMessage"
                :disabled="!currentMessage.trim() || isAIThinking || dailyLimitReached"
                :loading="isAIThinking"
                class="send-button"
              />
            </div>

            <!-- Usage Info -->
            <div class="usage-info">
              <div class="query-count">
                {{ todayQueryCount }}/{{ dailyQueryLimit }} queries today
              </div>
              <div v-if="dailyLimitReached" class="limit-warning">
                <i class="pi pi-exclamation-triangle"></i>
                Daily query limit reached. Resets at midnight.
              </div>
            </div>

            <!-- Quick Actions -->
            <div class="quick-actions">
              <Button 
                label="Clear Chat" 
                icon="pi pi-trash"
                outlined
                size="small"
                @click="clearChat"
                v-if="messages.length > 0"
              />
              <Button 
                label="Export Conversation" 
                icon="pi pi-download"
                outlined
                size="small"
                @click="exportConversation"
                v-if="messages.length > 0"
              />
            </div>
          </div>
        </BaseCard>

      </div>
    </div>
  </PageContainer>
</template>

<script setup>
import { ref, computed, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { useUserStore } from '@/stores/user'
import { useTimezone } from '@/composables/useTimezone'

const router = useRouter()
const toast = useToast()
const userStore = useUserStore()
const timezone = useTimezone()

// State
const messages = ref([])
const currentMessage = ref('')
const isAIThinking = ref(false)
const messagesContainer = ref(null)
const aiSettings = ref(null)
const todayQueryCount = ref(0)

// Example queries to show users
const exampleQueries = ref([
  "When did I last visit Starbucks?",
  "How many times did I go to the gym this month?",
  "What's my most visited restaurant?",
  "Show me data gaps longer than 2 hours",
  "Where did I go for lunch yesterday?"
])

// Computed
const isAIConfigured = computed(() => {
  return aiSettings.value?.enabled && 
         aiSettings.value?.provider && 
         (aiSettings.value?.openaiApiKey || aiSettings.value?.ollamaUrl || aiSettings.value?.customUrl)
})

const dailyQueryLimit = computed(() => aiSettings.value?.dailyQueryLimit || 50)
const dailyLimitReached = computed(() => todayQueryCount.value >= dailyQueryLimit.value)

// Methods
const sendMessage = async () => {
  if (!currentMessage.value.trim() || isAIThinking.value || dailyLimitReached.value) return

  const userMessage = {
    id: Date.now(),
    role: 'user',
    content: currentMessage.value,
    timestamp: new Date()
  }

  messages.value.push(userMessage)
  const query = currentMessage.value
  currentMessage.value = ''
  
  await scrollToBottom()
  
  isAIThinking.value = true

  try {
    const response = await fetch('/api/ai/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        message: query,
        conversationId: getConversationId(),
        settings: aiSettings.value
      })
    })

    if (!response.ok) {
      throw new Error('AI request failed')
    }

    const aiResponse = await response.json()
    
    const assistantMessage = {
      id: Date.now() + 1,
      role: 'assistant',
      content: aiResponse.message,
      attachments: aiResponse.attachments,
      timestamp: new Date()
    }

    messages.value.push(assistantMessage)
    todayQueryCount.value++
    
    await scrollToBottom()

  } catch (error) {
    console.error('AI chat error:', error)
    
    const errorMessage = {
      id: Date.now() + 1,
      role: 'assistant',
      content: "I'm sorry, I encountered an error processing your request. Please check your AI configuration and try again.",
      timestamp: new Date()
    }
    
    messages.value.push(errorMessage)
    
    toast.add({
      severity: 'error',
      summary: 'AI Request Failed',
      detail: 'There was an error processing your request',
      life: 5000
    })
  } finally {
    isAIThinking.value = false
  }
}

const sendExampleQuery = (query) => {
  currentMessage.value = query
  sendMessage()
}

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

const formatMessageText = (text) => {
  // Convert markdown-like formatting to HTML
  return text
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/\n/g, '<br>')
}

const formatTimestamp = (timestamp) => {
  return timezone.format(timestamp, 'HH:mm')
}

const clearChat = () => {
  messages.value = []
}

const exportConversation = () => {
  const conversationData = messages.value.map(msg => ({
    role: msg.role,
    content: msg.content,
    timestamp: msg.timestamp
  }))
  
  const blob = new Blob([JSON.stringify(conversationData, null, 2)], { 
    type: 'application/json' 
  })
  
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `geopulse_ai_conversation_${timezone.format(new Date(), 'YYYY-MM-DD_HH-mm')}.json`
  link.click()
  URL.revokeObjectURL(url)
}

const getConversationId = () => {
  // Generate or retrieve conversation ID for context
  return `conv_${Date.now()}`
}

// Load AI settings and query count on mount
onMounted(async () => {
  try {
    aiSettings.value = await userStore.getAISettings()
    todayQueryCount.value = await userStore.getTodayQueryCount()
  } catch (error) {
    console.error('Failed to load AI settings:', error)
  }
})
</script>

<style scoped>
.ai-chat-page {
  height: calc(100vh - 200px);
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-md);
}

.config-prompt-card {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.config-prompt {
  text-align: center;
  max-width: 500px;
  padding: var(--gp-spacing-xl);
}

.config-icon {
  font-size: 3rem;
  color: var(--gp-warning);
  margin-bottom: var(--gp-spacing-lg);
}

.config-title {
  margin: 0 0 var(--gp-spacing-md) 0;
  color: var(--gp-text-primary);
}

.config-message {
  margin: 0 0 var(--gp-spacing-lg) 0;
  color: var(--gp-text-secondary);
  line-height: 1.5;
}

.chat-interface {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-md);
}

.chat-messages-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: var(--gp-spacing-md);
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-md);
}

.welcome-message {
  display: flex;
  gap: var(--gp-spacing-md);
  padding: var(--gp-spacing-lg);
  background: var(--gp-surface-light);
  border-radius: var(--gp-radius-large);
}

.ai-avatar {
  width: 40px;
  height: 40px;
  background: var(--gp-primary);
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.welcome-content {
  flex: 1;
}

.welcome-title {
  margin: 0 0 var(--gp-spacing-sm) 0;
  color: var(--gp-text-primary);
}

.welcome-text {
  margin: 0 0 var(--gp-spacing-md) 0;
  color: var(--gp-text-secondary);
}

.example-queries {
  display: flex;
  flex-wrap: wrap;
  gap: var(--gp-spacing-xs);
}

.example-chip {
  cursor: pointer;
  transition: all 0.2s ease;
}

.example-chip:hover {
  background: var(--gp-primary-100);
}

.chat-message {
  display: flex;
  gap: var(--gp-spacing-sm);
  max-width: 80%;
}

.message-user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.message-assistant {
  align-self: flex-start;
}

.message-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 0.875rem;
}

.message-user .message-avatar {
  background: var(--gp-primary);
  color: white;
}

.message-assistant .message-avatar {
  background: var(--gp-surface-light);
  color: var(--gp-text-secondary);
}

.message-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.message-text {
  padding: var(--gp-spacing-sm) var(--gp-spacing-md);
  border-radius: var(--gp-radius-medium);
  line-height: 1.5;
}

.message-user .message-text {
  background: var(--gp-primary);
  color: white;
  border-bottom-right-radius: var(--gp-spacing-xs);
}

.message-assistant .message-text {
  background: var(--gp-surface-light);
  color: var(--gp-text-primary);
  border-bottom-left-radius: var(--gp-spacing-xs);
}

.message-attachments {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.attachment {
  border: 1px solid var(--gp-border-light);
  border-radius: var(--gp-radius-medium);
  overflow: hidden;
}

.attachment-table {
  max-height: 300px;
  overflow-y: auto;
}

.message-actions {
  display: flex;
  gap: var(--gp-spacing-xs);
  margin-top: var(--gp-spacing-xs);
}

.message-timestamp {
  font-size: 0.75rem;
  color: var(--gp-text-muted);
  text-align: right;
}

.message-user .message-timestamp {
  text-align: left;
}

.typing-indicator .message-text {
  padding: var(--gp-spacing-md);
}

.typing-animation {
  display: flex;
  gap: 4px;
}

.typing-animation span {
  width: 8px;
  height: 8px;
  background: var(--gp-text-secondary);
  border-radius: 50%;
  animation: typing 1.5s infinite;
}

.typing-animation span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-animation span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.5;
  }
  30% {
    transform: translateY(-10px);
    opacity: 1;
  }
}

.chat-input-card {
  flex-shrink: 0;
}

.chat-input-area {
  display: flex;
  flex-direction: column;
  gap: var(--gp-spacing-sm);
}

.input-container {
  display: flex;
  gap: var(--gp-spacing-sm);
  align-items: center;
}

.message-input {
  flex: 1;
}

.usage-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.875rem;
  color: var(--gp-text-secondary);
}

.limit-warning {
  color: var(--gp-warning);
  display: flex;
  align-items: center;
  gap: var(--gp-spacing-xs);
}

.quick-actions {
  display: flex;
  gap: var(--gp-spacing-sm);
  justify-content: flex-end;
}

/* Mobile responsive */
@media (max-width: 768px) {
  .ai-chat-page {
    height: calc(100vh - 160px);
  }
  
  .chat-message {
    max-width: 95%;
  }
  
  .quick-actions {
    justify-content: center;
  }
  
  .usage-info {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--gp-spacing-xs);
  }
}
</style>
```

### 3. Backend AI Tools Implementation (LangChain4j)

#### AI Tools Definition with LangChain4j

```java
@ApplicationScoped
public class GeoPulseAITools {
    
    @Inject
    StreamingTimelineAggregator timelineAggregator;
    
    @Inject 
    PlacesAnalysisService placesAnalysisService;
    
    @Inject
    StatisticsService statisticsService;
    
    @Inject
    GpsPointService gpsPointService;
    
    @Tool("Query user's timeline data for specific date ranges and analyze stays/trips")
    public TimelineQueryResult queryTimeline(
        @P("Start date in format YYYY-MM-DD or natural language like 'last month'") 
        String startDate,
        @P("End date in format YYYY-MM-DD or natural language like 'yesterday'")
        String endDate,
        @P("Optional filters: 'restaurants', 'work', 'weekends', 'long_stays'")
        String filters) {
        
        DateRange range = naturalLanguageDateParser.parse(startDate, endDate);
        var timeline = timelineAggregator.getTimelineFromDb(
            getCurrentUserId(), range.start(), range.end());
        return TimelineQueryResult.from(timeline, filters);
    }
    
    @Tool("Analyze travel patterns by time periods like day of week, hour, or season")
    public PatternAnalysisResult analyzeTemporalPatterns(
        @P("Pattern type: 'day_of_week', 'hour_of_day', 'month', 'season'")
        String patternType,
        @P("Analysis type: 'compare', 'trend', 'distribution'") 
        String analysisType,
        @P("What to compare, e.g., ['Sunday', 'Saturday'] or ['winter', 'summer']")
        List<String> compareValues,
        @P("Metrics: 'distance', 'trip_count', 'duration', 'unique_places'")
        List<String> metrics,
        @P("Time range: 'last_year', '2024', 'last_6_months'")
        String timeRange) {
        
        return activityAnalysisService.analyzeByPattern(
            getCurrentUserId(), patternType, compareValues, metrics, 
            parseTimeRange(timeRange));
    }
    
    @Tool("Advanced location search with favorites, geocoding, and spatial context")
    public EnhancedLocationResult searchLocations(
        @P("Location name or type: 'Starbucks', 'restaurants', 'gym'")
        String locationQuery,
        @P("Analysis type: 'frequency', 'duration', 'timing', 'recent_visits'")
        String analysisType,
        @P("Search context: 'favorites_only', 'geocoded_only', 'all', 'user_named'")
        String searchContext,
        @P("Time context: 'lunch_time', 'weekends', 'morning'")
        String timeContext,
        @P("Date range for analysis")
        String dateRange,
        @P("Include location resolution metadata (favorites vs geocoded)")
        Boolean includeMetadata) {
        
        return placesAnalysisService.analyzeLocationsByQueryEnhanced(
            getCurrentUserId(), locationQuery, analysisType, searchContext,
            parseTimeContext(timeContext), parseTimeRange(dateRange), includeMetadata);
    }
    
    @Tool("Search and manage user's favorite locations and analyze their usage")
    public FavoritesAnalysisResult queryFavoriteLocations(
        @P("Search query: location name, 'all', 'unused', or 'recent'")
        String query,
        @P("Analysis type: 'list', 'usage_stats', 'nearby', 'coverage_analysis'")
        String analysisType,
        @P("Optional reference coordinates for proximity search")
        String referenceCoordinates,
        @P("Time range for usage analysis")
        String timeRange) {
        
        return favoriteLocationService.analyzeFavoriteLocations(
            getCurrentUserId(), query, analysisType, 
            parseCoordinates(referenceCoordinates), parseTimeRange(timeRange));
    }
    
    @Tool("Analyze spatial relationships and proximity patterns in location data")
    public SpatialAnalysisResult analyzeSpatialPatterns(
        @P("Analysis type: 'nearby_locations', 'coverage_gaps', 'dense_areas', 'travel_patterns'")
        String analysisType,
        @P("Reference location name or coordinates")
        String referenceLocation,
        @P("Distance threshold: '100m', '500m', '1km', '5km'")
        String distanceThreshold,
        @P("Time period for analysis")
        String timeRange,
        @P("Include favorite locations in analysis")
        Boolean includeFavorites) {
        
        return spatialAnalysisService.analyzeLocationPatterns(
            getCurrentUserId(), analysisType, referenceLocation,
            parseDistance(distanceThreshold), parseTimeRange(timeRange), includeFavorites);
    }
    
    @Tool("Analyze location name resolution quality and data sources")
    public LocationNamingResult analyzeLocationNaming(
        @P("Analysis type: 'sources', 'quality', 'patterns', 'resolution_stats', 'provider_comparison'")
        String analysisType,
        @P("Time period for analysis")
        String timeRange,
        @P("Location filter: 'restaurants', 'all', specific location name")
        String locationFilter,
        @P("Quality metrics: 'accuracy', 'completeness', 'consistency'")
        String qualityMetric) {
        
        return locationResolutionAnalysisService.analyzeNamingQuality(
            getCurrentUserId(), analysisType, parseTimeRange(timeRange), 
            locationFilter, qualityMetric);
    }
    
    @Tool("Analyze quality of location resolution from favorites vs geocoding vs external APIs")
    public ResolutionQualityResult analyzeResolutionQuality(
        @P("Quality focus: 'accuracy', 'completeness', 'source_distribution', 'failure_analysis'")
        String qualityFocus,
        @P("Time period for analysis") 
        String timeRange,
        @P("Resolution source filter: 'favorites', 'geocoded', 'external', 'all'")
        String sourceFilter,
        @P("Include detailed provider statistics")
        Boolean includeProviderStats) {
        
        return locationPointResolver.analyzeResolutionQuality(
            getCurrentUserId(), qualityFocus, parseTimeRange(timeRange), 
            sourceFilter, includeProviderStats);
    }
    
    @Tool("Manage and analyze social connections and friend location sharing")
    public SocialAnalysisResult analyzeSocialConnections(
        @P("Analysis type: 'friends_list', 'invitations', 'location_sharing', 'social_patterns'")
        String analysisType,
        @P("Social context: 'active_friends', 'pending_invites', 'recent_connections', 'all'")
        String socialContext,
        @P("Time period for analysis")
        String timeRange,
        @P("Include location-based social insights")
        Boolean includeLocationInsights) {
        
        return friendService.analyzeSocialConnections(
            getCurrentUserId(), analysisType, socialContext, 
            parseTimeRange(timeRange), includeLocationInsights);
    }
    
    @Tool("Manage and analyze GPS source integrations and data collection")
    public IntegrationAnalysisResult analyzeGpsSourceIntegrations(
        @P("Analysis type: 'sources_list', 'connection_status', 'data_quality', 'source_comparison'")
        String analysisType,
        @P("Integration filter: 'active_only', 'all', 'owntracks', 'overland', 'dawarich', 'home_assistant'")
        String integrationFilter,
        @P("Time period for data quality analysis")
        String timeRange,
        @P("Include detailed connection and performance metrics")
        Boolean includeMetrics) {
        
        return gpsSourceService.analyzeIntegrations(
            getCurrentUserId(), analysisType, integrationFilter, 
            parseTimeRange(timeRange), includeMetrics);
    }
    
    @Tool("Analyze user achievements, badges, and progress tracking")
    public AchievementsAnalysisResult analyzeAchievements(
        @P("Analysis type: 'badge_progress', 'earned_badges', 'achievement_suggestions', 'badge_history'")
        String analysisType,
        @P("Badge filter: 'all', 'earned', 'in_progress', 'specific_badge_id'") 
        String badgeFilter,
        @P("Include progress recommendations and achievement tips")
        Boolean includeRecommendations,
        @P("Time period for badge analysis")
        String timeRange) {
        
        return achievementService.analyzeUserAchievements(
            getCurrentUserId(), analysisType, badgeFilter, 
            includeRecommendations, parseTimeRange(timeRange));
    }
    
    @Tool("Manage and analyze shared location links")
    public SharedLinksAnalysisResult analyzeSharedLinks(
        @P("Analysis type: 'active_links', 'link_usage', 'sharing_stats', 'expired_links'")
        String analysisType,
        @P("Include link performance and access statistics")
        Boolean includeStats,
        @P("Time range for sharing analysis")
        String timeRange) {
        
        return sharedLinkService.analyzeUserSharedLinks(
            getCurrentUserId(), analysisType, includeStats, parseTimeRange(timeRange));
    }
    
    @Tool("Compare different activities, locations, or time periods")
    public ComparisonResult compareActivities(
        @P("What to compare: 'days', 'locations', 'routes', 'seasons'")
        String comparisonType,
        @P("Values to compare: ['work', 'home'] or ['morning', 'evening']")
        List<String> compareItems,
        @P("Metrics: 'distance', 'visits', 'duration', 'frequency'")
        List<String> metrics,
        @P("Time period for analysis")
        String timePeriod) {
        
        return statisticsService.compareActivities(
            getCurrentUserId(), comparisonType, compareItems, 
            metrics, parseTimeRange(timePeriod));
    }
    
    @Tool("Get raw GPS points for data quality analysis and detailed path inspection")
    public GpsPointsResult queryGpsPoints(
        @P("Date range for GPS points") 
        String dateRange,
        @P("Filters: 'low_accuracy', 'high_speed', 'stationary', 'moving'") 
        String filters,
        @P("Limit points returned (max 1000 for performance)") 
        Integer limit) {
        
        return gpsPointService.getFilteredGpsPoints(
            getCurrentUserId(), 
            parseTimeRange(dateRange), 
            parseFilters(filters),
            Math.min(limit != null ? limit : 100, 1000));
    }
    
    @Tool("Get travel statistics and insights")
    public StatisticsResult getTravelStatistics(
        @P("Statistics type: 'summary', 'distances', 'places', 'time_analysis'")
        String statsType,
        @P("Time grouping: 'daily', 'weekly', 'monthly', 'yearly'")
        String grouping,
        @P("Date range for statistics")
        String dateRange) {
        
        return statisticsService.getStatistics(
            getCurrentUserId(), 
            parseTimeRange(dateRange),
            ChartGroupMode.valueOf(grouping.toUpperCase()));
    }
    
    @Tool("Find data gaps or missing location data periods")
    public DataGapResult analyzeDataGaps(
        @P("Minimum gap duration: '2 hours', '1 day'")
        String minDuration,
        @P("Date range to check for gaps")
        String dateRange) {
        
        return streamingDataGapService.findDataGaps(
            getCurrentUserId(),
            parseTimeRange(dateRange),
            parseDuration(minDuration));
    }
}
```

#### AI Service Implementation

```java
@ApplicationScoped
public class AIService {
    
    @Inject
    GeoPulseAITools geoPulseTools;
    
    @Inject
    UserAISettingsService aiSettingsService;
    
    private AiServices<AIAssistant> aiService;
    
    @PostConstruct
    void initializeAI() {
        this.aiService = AiServices.builder(AIAssistant.class)
            .chatLanguageModel(this::getChatModel)
            .tools(geoPulseTools)
            .systemMessageProvider(chatId -> getSystemPrompt())
            .build();
    }
    
    @Inject
    AIQueryValidationService queryValidator;
    
    @Inject 
    AIConversationContextService contextService;
    
    @Inject
    AIConversationService conversationService;
    
    public AIChatResponse processUserQuery(String message, UUID conversationId, 
                                          UUID userId, UserAISettings settings) {
        try {
            // Get conversation history for context
            List<AIConversationMessage> history = conversationService.getConversationMessages(conversationId);
            
            // Validate query and check for needed clarification
            QueryValidationResult validation = queryValidator.validateQuery(message, history);
            if (validation.needsClarification()) {
                // Store the user's message even though we're asking for clarification
                conversationService.addMessage(conversationId, "user", message, null);
                conversationService.addMessage(conversationId, "assistant", validation.getClarificationMessage(), null);
                
                return AIChatResponse.builder()
                    .message(validation.getClarificationMessage())
                    .needsClarification(true)
                    .clarificationType(validation.getClarificationType())
                    .conversationId(conversationId.toString())
                    .build();
            }
            
            // Build conversation context
            ConversationContext context = contextService.buildContext(history);
            
            // Resolve contextual references in user message
            String resolvedMessage = contextService.resolveContextualReferences(message, context);
            
            // Set user context for tools
            setCurrentUserContext(userId, settings, context);
            
            // Process with AI (use resolved message but show original to user)
            String response = aiService.chat(conversationId.toString(), resolvedMessage);
            
            // Store both messages in conversation history
            conversationService.addMessage(conversationId, "user", message, null);
            conversationService.addMessage(conversationId, "assistant", response, getLastToolCalls());
            
            return AIChatResponse.builder()
                .message(response)
                .conversationId(conversationId.toString())
                .originalMessage(message)
                .resolvedMessage(!message.equals(resolvedMessage) ? resolvedMessage : null)
                .build();
                
        } catch (Exception e) {
            log.error("AI processing error for user {}: {}", userId, e.getMessage());
            throw new AIProcessingException("Failed to process query", e);
        }
    }
    
    private void setCurrentUserContext(UUID userId, UserAISettings settings, ConversationContext context) {
        // Enhanced context setting with conversation context
        ThreadLocal<UserContext> userContext = getCurrentUserContextThreadLocal();
        UserContext ctx = new UserContext(userId, settings, context);
        userContext.set(ctx);
    }
    
    private List<String> getLastToolCalls() {
        // Extract tool calls from the last AI interaction for conversation storage
        // This would be implemented based on LangChain4j's tool call tracking
        return Collections.emptyList(); // Placeholder
    }

// Enhanced response class
public class AIChatResponse {
    private String message;
    private String conversationId;
    private boolean needsClarification = false;
    private String clarificationType;
    private String originalMessage;
    private String resolvedMessage; // Shows context resolution for debugging
    
    // Builder pattern methods...
}
    
    private ChatLanguageModel getChatModel(UserAISettings settings) {
        return switch (settings.getProvider()) {
            case OPENAI -> OpenAiChatModel.builder()
                .apiKey(settings.getOpenaiApiKey())
                .modelName(settings.getOpenaiModel())
                .temperature(0.2)
                .build();
                
            case OLLAMA -> OllamaChatModel.builder()
                .baseUrl(settings.getOllamaUrl())
                .modelName(settings.getOllamaModel())
                .temperature(0.2)
                .build();
                
            case CUSTOM -> OpenAiChatModel.builder()
                .baseUrl(settings.getCustomUrl())
                .apiKey(settings.getCustomApiKey())
                .modelName(settings.getCustomModel())
                .build();
        };
    }
    
    private String getSystemPrompt() {
        return """
            You are GeoPulse AI Assistant, helping users analyze their location and travel data.
            
            **CRITICAL: Always Ask for Clarification When Needed**
            
            Before executing ANY tool, check if the user's query has sufficient detail:
            - **Vague time references**: "show my trip" → Ask: "Which dates or time period would you like me to analyze?"
            - **Missing locations**: "how long was I there" → Ask: "Which location are you referring to?"
            - **Ambiguous requests**: "my restaurants" → Ask: "Would you like to see all restaurants, or are you looking for a specific one?"
            - **Unclear metrics**: "how far did I travel" → Ask: "For what time period - today, this week, or another specific timeframe?"
            - **Multiple possibilities**: "my Starbucks visits" when user has visited many → Ask: "Which Starbucks location, or would you prefer a summary of all visits?"
            
            **Always ask clarifying questions BEFORE using tools. Examples:**
            - "I'd be happy to help! Could you specify which dates or time period you're interested in?"
            - "To give you accurate information, which location would you like me to analyze?"
            - "Could you tell me the specific timeframe you'd like me to check - today, this week, or another period?"
            
            **ONLY proceed with tool execution once you have sufficient detail.**
            
            **Conversation Context Awareness:**
            - Remember locations, times, places, and topics from previous messages in this conversation
            - Use context for follow-up questions (e.g., "there" refers to previously mentioned location)
            - Reference previous data when relevant: "Based on your earlier question about Starbucks..."
            - When user says "there", "that place", "then", resolve to most recent location/time mentioned
            - Build on previous conversation naturally: "Following up on your Starbucks analysis..."
            
            You have access to these comprehensive capabilities:
            
            **Timeline & Travel Analysis:**
            - Query timeline data for any date range
            - Analyze travel patterns (by day, time, season)  
            - Compare different activities or time periods
            - Get travel statistics and insights
            - Access raw GPS points for data quality analysis
            - Find data gaps in location history
            
            **Location Intelligence (Your Key Strength):**
            - Search locations with favorites, geocoding, and spatial context
            - Analyze user's favorite locations and their usage patterns
            - Spatial proximity analysis and coverage gap detection  
            - Location name resolution quality analysis
            - Compare location data sources (favorites vs geocoding vs external)
            
            **Social & Friends Features:**
            - Manage and analyze friend connections and invitations
            - Location-based social insights and sharing patterns
            - Friend location queries and social travel patterns
            
            **Integration Management:**
            - Analyze GPS source integrations (OwnTracks, Overland, Dawarich, Home Assistant)
            - Monitor connection status and data quality from third-party services
            - Compare data sources and integration performance
            
            **Achievement & Sharing Features:**
            - Analyze user badges and achievement progress
            - Shared location links management and statistics
            
            **GeoPulse's Three-Tier Location System:**
            1. **Favorites**: User-defined locations (Home, Work, etc.) with custom names
            2. **Geocoded Cache**: Cached external geocoding with formatted names
            3. **Spatial Resolution**: Smart priority system with distance-based matching
            
            Guidelines:
            - Always respect user privacy settings
            - Leverage favorites context when users mention personal location names
            - Provide spatial insights using proximity and coverage analysis
            - Use follow-up analyses when relevant
            - For GPS point queries, limit to reasonable amounts for performance
            - Distinguish between user-named favorites and geocoded locations
            - Maintain conversation flow by referencing previous exchanges
            
            Example complex location-aware queries you can handle:
            - "How often do I visit my favorite coffee shops?" → Use queryFavoriteLocations + searchLocations
            - "What geocoded restaurants should I add to favorites?" → Use searchLocations with context analysis
            - "Show me areas near work where I don't have favorite locations" → Use analyzeSpatialPatterns
            - "Compare my GPS accuracy near favorite locations vs random places" → Use analyzeResolutionQuality
            - "Which favorite locations haven't I visited recently?" → Use queryFavoriteLocations with usage_stats
            - "Find location coverage gaps within 1km of home" → Use analyzeSpatialPatterns with proximity
            - "What badges have I earned this year?" → Use analyzeAchievements with earned_badges filter
            - "How close am I to the Globe Trotter achievement?" → Use analyzeAchievements with specific badge analysis
            - "What location links have I shared recently?" → Use analyzeSharedLinks with active_links analysis
            
            **Context-Aware Follow-ups:**
            - User: "Where did I go yesterday?" AI: "You visited Starbucks on Main St at 2pm"
            - User: "How long was I there?" → AI understands "there" = Starbucks on Main St
            - User: "Show me my trip to Paris" AI: "Here's your Paris timeline..."
            - User: "What was my favorite restaurant there?" → AI understands "there" = Paris
            """;
    }
}
```

#### Secure API Key Storage Implementation

**Database Schema Changes:**
```sql
-- Add encrypted columns to users table
ALTER TABLE users ADD COLUMN ai_settings_encrypted TEXT;
ALTER TABLE users ADD COLUMN ai_settings_key_id VARCHAR(50);
```

**AI Encryption Service:**
```java
@ApplicationScoped
public class AIEncryptionService {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12; // GCM recommended IV length
    private static final int TAG_LENGTH = 16; // GCM tag length
    
    private final SecretKeySpec secretKey;
    private final String currentKeyId;
    
    @Inject
    public AIEncryptionService(@ConfigProperty(name = "geopulse.ai.encryption.master-key") String masterKey) {
        this.secretKey = new SecretKeySpec(Base64.getDecoder().decode(masterKey), "AES");
        this.currentKeyId = "v1"; // For key rotation support
    }
    
    public String encrypt(String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            
            // Generate random IV
            byte[] iv = new byte[IV_LENGTH];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH * 8, iv);
            
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV + ciphertext for storage
            byte[] encryptedWithIv = new byte[IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, IV_LENGTH);
            System.arraycopy(cipherText, 0, encryptedWithIv, IV_LENGTH, cipherText.length);
            
            return Base64.getEncoder().encodeToString(encryptedWithIv);
            
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }
    
    public String decrypt(String encryptedData, String keyId) {
        try {
            byte[] decodedData = Base64.getDecoder().decode(encryptedData);
            
            // Extract IV and ciphertext
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText = new byte[decodedData.length - IV_LENGTH];
            System.arraycopy(decodedData, 0, iv, 0, IV_LENGTH);
            System.arraycopy(decodedData, IV_LENGTH, cipherText, 0, cipherText.length);
            
            // Decrypt
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            
            byte[] plainTextBytes = cipher.doFinal(cipherText);
            return new String(plainTextBytes, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
    
    public String getCurrentKeyId() {
        return currentKeyId;
    }
}
```

**Secure AI Settings Service:**
```java
@ApplicationScoped
public class UserAISettingsService {
    
    @Inject
    AIEncryptionService encryptionService;
    
    @Inject
    EntityManager em;
    
    public void saveAISettings(UUID userId, UserAISettings settings) {
        // Create copy for encryption
        UserAISettings settingsToEncrypt = settings.copy();
        
        // Encrypt individual API keys
        if (settingsToEncrypt.getOpenaiApiKey() != null) {
            settingsToEncrypt.setOpenaiApiKey(
                encryptionService.encrypt(settingsToEncrypt.getOpenaiApiKey())
            );
        }
        if (settingsToEncrypt.getCustomApiKey() != null) {
            settingsToEncrypt.setCustomApiKey(
                encryptionService.encrypt(settingsToEncrypt.getCustomApiKey())
            );
        }
        
        // Convert to JSON and encrypt entire blob
        String json = objectMapper.writeValueAsString(settingsToEncrypt);
        String encryptedJson = encryptionService.encrypt(json);
        
        // Store in database
        UserEntity user = em.find(UserEntity.class, userId);
        user.setAiSettingsEncrypted(encryptedJson);
        user.setAiSettingsKeyId(encryptionService.getCurrentKeyId());
        em.merge(user);
    }
    
    public UserAISettings getAISettings(UUID userId) {
        UserEntity user = em.find(UserEntity.class, userId);
        if (user.getAiSettingsEncrypted() == null) {
            return new UserAISettings(); // Default settings
        }
        
        // Decrypt JSON blob
        String decryptedJson = encryptionService.decrypt(
            user.getAiSettingsEncrypted(), 
            user.getAiSettingsKeyId()
        );
        
        UserAISettings settings = objectMapper.readValue(decryptedJson, UserAISettings.class);
        
        // Decrypt individual API keys
        if (settings.getOpenaiApiKey() != null) {
            settings.setOpenaiApiKey(
                encryptionService.decrypt(settings.getOpenaiApiKey(), user.getAiSettingsKeyId())
            );
        }
        if (settings.getCustomApiKey() != null) {
            settings.setCustomApiKey(
                encryptionService.decrypt(settings.getCustomApiKey(), user.getAiSettingsKeyId())
            );
        }
        
        return settings;
    }
}
```

**Configuration Setup:**
```yaml
# application.yml
geopulse:
  ai:
    encryption:
      master-key: ${AI_ENCRYPTION_KEY:} # Set via environment variable
```

**Generate Encryption Key:**
```bash
# Generate 256-bit AES key
openssl rand -base64 32
# Example output: 8fj2k9dK3mN7qR5sT6uV8wX1yZ2aB4cD9eF6gH7iJ0kL
```

**Production Deployment:**
```yaml
# docker-compose.yml
services:
  geopulse-backend:
    environment:
      - AI_ENCRYPTION_KEY=${AI_ENCRYPTION_KEY}
    env_file:
      - .env.production
```

**Security Features:**
- **AES-256-GCM encryption** with authentication
- **Random IV per encryption** (never reused)
- **Master key stored outside database** (environment variable)
- **Key rotation support** with versioning
- **Double encryption**: Individual API keys + entire settings blob
- **Base64 encoding** for database storage

#### Conversation History Storage & UI

**Database Schema for Chat History:**
```sql
-- AI conversation history table
CREATE TABLE ai_conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(200), -- Auto-generated from first message
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    message_count INTEGER DEFAULT 0,
    is_archived BOOLEAN DEFAULT FALSE
);

-- Individual messages in conversations
CREATE TABLE ai_conversation_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES ai_conversations(id) ON DELETE CASCADE,
    message_type VARCHAR(20) NOT NULL, -- 'user' or 'assistant' 
    content TEXT NOT NULL,
    tool_calls JSONB, -- Store function calls made by AI
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    message_order INTEGER NOT NULL -- Order within conversation
);

CREATE INDEX idx_ai_conversations_user_id ON ai_conversations(user_id, created_at DESC);
CREATE INDEX idx_ai_messages_conversation_id ON ai_conversation_messages(conversation_id, message_order);
```

**Conversation History Service:**
```java
@ApplicationScoped
public class AIConversationService {
    
    @Inject
    EntityManager em;
    
    public AIConversation createConversation(UUID userId) {
        AIConversationEntity conversation = AIConversationEntity.builder()
            .userId(userId)
            .title("New Conversation")
            .build();
        em.persist(conversation);
        return toDTO(conversation);
    }
    
    public void addMessage(UUID conversationId, String messageType, String content, 
                          List<String> toolCalls) {
        AIConversationEntity conversation = em.find(AIConversationEntity.class, conversationId);
        
        AIConversationMessageEntity message = AIConversationMessageEntity.builder()
            .conversationId(conversationId)
            .messageType(messageType)
            .content(content)
            .toolCalls(toolCalls)
            .messageOrder(conversation.getMessageCount() + 1)
            .build();
            
        em.persist(message);
        
        // Update conversation
        conversation.setMessageCount(conversation.getMessageCount() + 1);
        conversation.setUpdatedAt(Instant.now());
        
        // Auto-generate title from first user message
        if (conversation.getMessageCount() == 1 && "user".equals(messageType)) {
            String title = generateConversationTitle(content);
            conversation.setTitle(title);
        }
        
        em.merge(conversation);
    }
    
    public List<AIConversation> getUserConversations(UUID userId, int limit) {
        return em.createQuery(
            "SELECT c FROM AIConversationEntity c WHERE c.userId = :userId AND c.isArchived = false ORDER BY c.updatedAt DESC", 
            AIConversationEntity.class)
            .setParameter("userId", userId)
            .setMaxResults(limit)
            .getResultList()
            .stream()
            .map(this::toDTO)
            .toList();
    }
    
    public List<AIConversationMessage> getConversationMessages(UUID conversationId) {
        return em.createQuery(
            "SELECT m FROM AIConversationMessageEntity m WHERE m.conversationId = :conversationId ORDER BY m.messageOrder", 
            AIConversationMessageEntity.class)
            .setParameter("conversationId", conversationId)
            .getResultList()
            .stream()
            .map(this::messageToDTO)
            .toList();
    }
    
    private String generateConversationTitle(String firstMessage) {
        // Simple title generation from first 3-5 words
        String[] words = firstMessage.split("\\s+");
        int titleLength = Math.min(5, words.length);
        String title = String.join(" ", Arrays.copyOf(words, titleLength));
        return title.length() > 50 ? title.substring(0, 47) + "..." : title;
    }
}
```

**Frontend Chat UI with History:**
```vue
<template>
  <div class="ai-chat-page">
    <!-- Chat History Sidebar -->
    <div class="chat-sidebar" v-if="showHistory">
      <div class="sidebar-header">
        <h3>Chat History</h3>
        <Button 
          icon="pi pi-plus" 
          @click="startNewConversation"
          label="New Chat"
          size="small"
        />
      </div>
      
      <div class="conversation-list">
        <div 
          v-for="conversation in conversationHistory" 
          :key="conversation.id"
          :class="['conversation-item', { active: currentConversationId === conversation.id }]"
          @click="loadConversation(conversation.id)"
        >
          <div class="conversation-title">{{ conversation.title }}</div>
          <div class="conversation-meta">
            {{ formatDate(conversation.updatedAt) }} • {{ conversation.messageCount }} messages
          </div>
        </div>
      </div>
    </div>
    
    <!-- Main Chat Area -->
    <div class="chat-main">
      <div class="chat-header">
        <Button 
          icon="pi pi-bars" 
          @click="toggleHistory"
          text
          severity="secondary"
        />
        <h2>AI Assistant</h2>
        <Button 
          icon="pi pi-cog" 
          @click="openSettings"
          text
          severity="secondary"
        />
      </div>
      
      <!-- Chat Messages -->
      <div class="chat-messages" ref="messagesContainer">
        <div 
          v-for="message in currentMessages" 
          :key="message.id"
          :class="['message', message.messageType]"
        >
          <div class="message-content" v-html="formatMessage(message.content)"></div>
          <div class="message-time">{{ formatTime(message.createdAt) }}</div>
          
          <!-- Show tool calls for assistant messages -->
          <div v-if="message.toolCalls?.length" class="tool-calls">
            <small>Used tools: {{ message.toolCalls.join(', ') }}</small>
          </div>
        </div>
        
        <div v-if="isLoading" class="message assistant loading">
          <div class="typing-indicator">AI is thinking...</div>
        </div>
      </div>
      
      <!-- Chat Input -->
      <div class="chat-input-area">
        <div class="chat-input-container">
          <Textarea 
            v-model="currentMessage"
            placeholder="Ask about your location data..."
            @keydown="handleKeyDown"
            :disabled="!aiEnabled"
            rows="3"
            auto-resize
          />
          <Button 
            icon="pi pi-send" 
            @click="sendMessage"
            :disabled="!currentMessage.trim() || isLoading || !aiEnabled"
          />
        </div>
        
        <div v-if="!aiEnabled" class="setup-notice">
          <Message severity="warn" :closable="false">
            AI Assistant is not configured. 
            <router-link to="/app/profile?tab=ai-assistant">Configure AI settings</router-link>
          </Message>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useAIStore } from '@/stores/aiStore'
import { useUserStore } from '@/stores/userStore'

const aiStore = useAIStore()
const userStore = useUserStore()

const showHistory = ref(true)
const currentConversationId = ref(null)
const currentMessage = ref('')
const isLoading = ref(false)
const messagesContainer = ref(null)

const conversationHistory = computed(() => aiStore.conversationHistory)
const currentMessages = computed(() => aiStore.currentMessages)
const aiEnabled = computed(() => userStore.aiSettings?.enabled && userStore.aiSettings?.provider)

onMounted(() => {
  if (aiEnabled.value) {
    aiStore.loadConversationHistory()
    startNewConversation()
  }
})

const startNewConversation = async () => {
  currentConversationId.value = await aiStore.createNewConversation()
}

const loadConversation = async (conversationId) => {
  currentConversationId.value = conversationId
  await aiStore.loadConversationMessages(conversationId)
  scrollToBottom()
}

const sendMessage = async () => {
  if (!currentMessage.value.trim()) return
  
  const message = currentMessage.value
  currentMessage.value = ''
  isLoading.value = true
  
  try {
    await aiStore.sendMessage(currentConversationId.value, message)
    scrollToBottom()
  } catch (error) {
    console.error('Failed to send message:', error)
  } finally {
    isLoading.value = false
  }
}

const scrollToBottom = () => {
  setTimeout(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  }, 100)
}
</script>
```

**Enhanced Privacy Controls:**
```java
public class UserAISettings {
    // Existing fields...
    
    // NEW conversation history controls
    private boolean storeConversationHistory = true;
    private int maxStoredConversations = 50;
    private int conversationRetentionDays = 90; // Auto-delete old conversations
    private boolean allowConversationExport = true;
}
```

#### Enhanced Conversational Intelligence

**Query Validation Service:**
```java
@ApplicationScoped
public class AIQueryValidationService {
    
    public QueryValidationResult validateQuery(String userMessage, List<AIConversationMessage> conversationHistory) {
        QueryValidationResult result = new QueryValidationResult();
        
        // Check for time period ambiguity
        if (containsVagueTimeReference(userMessage)) {
            result.needsClarification("time_period", 
                "I'd be happy to help! Could you specify which dates or time period you're interested in?");
            return result;
        }
        
        // Check for location ambiguity  
        if (containsVagueLocationReference(userMessage)) {
            result.needsClarification("location", 
                "To give you accurate information, which location would you like me to analyze?");
            return result;
        }
        
        // Check for metric ambiguity
        if (containsVagueMetric(userMessage)) {
            result.needsClarification("metric", 
                "Could you tell me what specific measurement you'd like to see? (distance, duration, frequency, etc.)");
            return result;
        }
        
        // Check if context from conversation can resolve ambiguity
        if (hasContextualReferences(userMessage)) {
            ConversationContext context = extractConversationContext(conversationHistory);
            if (!context.canResolveReferences(userMessage)) {
                result.needsClarification("context", 
                    "Could you clarify what you're referring to from our previous conversation?");
                return result;
            }
        }
        
        result.setValid(true);
        return result;
    }
    
    private boolean containsVagueTimeReference(String message) {
        String[] vagueTimeWords = {"trip", "travel", "go", "visit", "my data", "places"};
        String[] specificTimeWords = {"yesterday", "today", "last week", "january", "2024", "monday", "this morning"};
        
        boolean hasVagueTime = Arrays.stream(vagueTimeWords)
            .anyMatch(word -> message.toLowerCase().contains(word));
        boolean hasSpecificTime = Arrays.stream(specificTimeWords)
            .anyMatch(word -> message.toLowerCase().contains(word));
            
        return hasVagueTime && !hasSpecificTime;
    }
    
    private boolean containsVagueLocationReference(String message) {
        String[] vagueLocationWords = {"there", "that place", "restaurant", "where I went"};
        return Arrays.stream(vagueLocationWords)
            .anyMatch(word -> message.toLowerCase().contains(word));
    }
    
    private boolean containsVagueMetric(String message) {
        String[] vagueMetricWords = {"how much", "how far", "how long", "how many"};
        String[] specificMetricWords = {"miles", "km", "minutes", "hours", "times", "visits"};
        
        boolean hasVagueMetric = Arrays.stream(vagueMetricWords)
            .anyMatch(word -> message.toLowerCase().contains(word));
        boolean hasSpecificMetric = Arrays.stream(specificMetricWords)
            .anyMatch(word -> message.toLowerCase().contains(word));
            
        return hasVagueMetric && !hasSpecificMetric;
    }
}

public class QueryValidationResult {
    private boolean valid = false;
    private boolean needsClarification = false;
    private String clarificationType;
    private String clarificationMessage;
    
    public void needsClarification(String type, String message) {
        this.needsClarification = true;
        this.clarificationType = type;
        this.clarificationMessage = message;
    }
    
    // Getters and setters...
}
```

**Conversation Context Service:**
```java
@ApplicationScoped
public class AIConversationContextService {
    
    public ConversationContext buildContext(List<AIConversationMessage> messages) {
        ConversationContext context = new ConversationContext();
        
        // Extract entities from conversation history (last 10 messages for performance)
        List<AIConversationMessage> recentMessages = messages.stream()
            .skip(Math.max(0, messages.size() - 10))
            .collect(Collectors.toList());
        
        for (AIConversationMessage message : recentMessages) {
            if ("assistant".equals(message.getMessageType())) {
                extractEntitiesFromResponse(message.getContent(), context);
            } else if ("user".equals(message.getMessageType())) {
                extractEntitiesFromQuery(message.getContent(), context);
            }
        }
        
        return context;
    }
    
    private void extractEntitiesFromResponse(String content, ConversationContext context) {
        // Extract mentioned locations: "You visited Starbucks on Main Street"
        Pattern locationPattern = Pattern.compile("You visited ([^,\\.]+)");
        Matcher matcher = locationPattern.matcher(content);
        while (matcher.find()) {
            context.addRecentLocation(matcher.group(1).trim());
        }
        
        // Extract time periods: "Yesterday from 2pm to 4pm"
        Pattern timePattern = Pattern.compile("(yesterday|today|on \\w+|last \\w+|this \\w+)");
        matcher = timePattern.matcher(content.toLowerCase());
        while (matcher.find()) {
            context.addRecentTimePeriod(matcher.group(1));
        }
        
        // Extract metrics with values: "You traveled 15.2 miles"
        Pattern metricPattern = Pattern.compile("(\\d+\\.?\\d*) (miles?|km|kilometers?|minutes?|hours?)");
        matcher = metricPattern.matcher(content);
        while (matcher.find()) {
            context.addRecentMetric(matcher.group(2), matcher.group(1));
        }
        
        // Extract friend names mentioned
        Pattern friendPattern = Pattern.compile("your friend ([A-Z][a-z]+)");
        matcher = friendPattern.matcher(content);
        while (matcher.find()) {
            context.addMentionedFriend(matcher.group(1));
        }
    }
    
    private void extractEntitiesFromQuery(String content, ConversationContext context) {
        // Extract locations mentioned by user: "When I was at Starbucks"
        Pattern userLocationPattern = Pattern.compile("at ([A-Z][a-zA-Z\\s]+)");
        Matcher matcher = userLocationPattern.matcher(content);
        while (matcher.find()) {
            context.addRecentLocation(matcher.group(1).trim());
        }
        
        // Extract time references from user: "my trip to Paris last week"  
        Pattern userTimePattern = Pattern.compile("(last \\w+|this \\w+|yesterday|today)");
        matcher = userTimePattern.matcher(content.toLowerCase());
        while (matcher.find()) {
            context.addRecentTimePeriod(matcher.group(1));
        }
    }
    
    public String resolveContextualReferences(String userMessage, ConversationContext context) {
        String resolved = userMessage;
        
        // Resolve "there" to most recent location
        if (resolved.toLowerCase().contains("there") && context.hasRecentLocations()) {
            String location = context.getMostRecentLocation();
            resolved = resolved.replaceAll("(?i)\\bthere\\b", location);
        }
        
        // Resolve "that place" to most recent location  
        if (resolved.toLowerCase().contains("that place") && context.hasRecentLocations()) {
            String location = context.getMostRecentLocation();
            resolved = resolved.replaceAll("(?i)that place", location);
        }
        
        // Resolve "then" to most recent time period
        if (resolved.toLowerCase().contains("then") && context.hasRecentTimePeriods()) {
            String timePeriod = context.getMostRecentTimePeriod();
            resolved = resolved.replaceAll("(?i)\\bthen\\b", timePeriod);
        }
        
        return resolved;
    }
}

public class ConversationContext {
    private List<String> recentLocations = new ArrayList<>();
    private List<String> recentTimePeriods = new ArrayList<>();  
    private Map<String, String> recentMetrics = new HashMap<>();
    private List<String> mentionedFriends = new ArrayList<>();
    
    public void addRecentLocation(String location) {
        // Add to beginning, keep only last 5 for relevance
        recentLocations.add(0, location);
        if (recentLocations.size() > 5) {
            recentLocations = recentLocations.subList(0, 5);
        }
    }
    
    public void addRecentTimePeriod(String timePeriod) {
        recentTimePeriods.add(0, timePeriod);
        if (recentTimePeriods.size() > 3) {
            recentTimePeriods = recentTimePeriods.subList(0, 3);
        }
    }
    
    public void addRecentMetric(String metricType, String value) {
        recentMetrics.put(metricType, value);
    }
    
    public void addMentionedFriend(String friendName) {
        if (!mentionedFriends.contains(friendName)) {
            mentionedFriends.add(friendName);
        }
    }
    
    public String getMostRecentLocation() {
        return recentLocations.isEmpty() ? null : recentLocations.get(0);
    }
    
    public String getMostRecentTimePeriod() {
        return recentTimePeriods.isEmpty() ? null : recentTimePeriods.get(0);
    }
    
    public boolean hasRecentLocations() {
        return !recentLocations.isEmpty();
    }
    
    public boolean hasRecentTimePeriods() {
        return !recentTimePeriods.isEmpty();
    }
    
    public boolean canResolveReferences(String message) {
        String lowerMessage = message.toLowerCase();
        
        if ((lowerMessage.contains("there") || lowerMessage.contains("that place")) && !hasRecentLocations()) {
            return false;
        }
        if ((lowerMessage.contains("then") || lowerMessage.contains("that time")) && !hasRecentTimePeriods()) {
            return false;
        }
        return true; // No ambiguous references found or can resolve them
    }
    
    public List<String> getRecentLocations() { return recentLocations; }
    public List<String> getRecentTimePeriods() { return recentTimePeriods; }
    public Map<String, String> getRecentMetrics() { return recentMetrics; }
    public List<String> getMentionedFriends() { return mentionedFriends; }
}
```

#### Dependencies (Maven)

```xml
<dependencies>
    <!-- LangChain4j Core -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-quarkus</artifactId>
        <version>0.34.0</version>
    </dependency>
    
    <!-- AI Providers -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-open-ai</artifactId>
        <version>0.34.0</version>
    </dependency>
    
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-ollama</artifactId>
        <version>0.34.0</version>
    </dependency>
    
    <!-- Natural Language Date Parsing -->
    <dependency>
        <groupId>com.joestelmach</groupId>
        <artifactId>natty</artifactId>
        <version>0.13</version>
    </dependency>
</dependencies>
```

### 4. Backend API Controller for AI Tools

```java
// Backend API Controller for AI Tools
@RestController
@RequestMapping("/api/ai")
@PreAuthorize("hasRole('USER')")
public class AIController {
    
    @Autowired
    private AIService aiService;
    
    @Autowired
    private UserAISettingsService aiSettingsService;
    
    @PostMapping("/chat")
    public ResponseEntity<AIChatResponse> chat(
            @RequestBody AIChatRequest request,
            Authentication auth) {
        
        String userId = auth.getName();
        
        // Validate user has AI enabled and configured
        UserAISettings settings = aiSettingsService.getUserSettings(userId);
        if (!settings.isEnabled()) {
            return ResponseEntity.badRequest()
                .body(new AIChatResponse("AI Assistant is not enabled for your account"));
        }
        
        // Check daily usage limits
        if (aiSettingsService.isDailyLimitReached(userId)) {
            return ResponseEntity.badRequest()
                .body(new AIChatResponse("Daily query limit reached"));
        }
        
        try {
            AIChatResponse response = aiService.processUserQuery(
                request.getMessage(),
                request.getConversationId(),
                userId,
                settings
            );
            
            // Increment usage counter
            aiSettingsService.incrementUsageCounter(userId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("AI chat error for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(500)
                .body(new AIChatResponse("I encountered an error processing your request"));
        }
    }
    
    @PostMapping("/test-openai")
    public ResponseEntity<Map<String, Object>> testOpenAI(@RequestBody OpenAITestRequest request) {
        try {
            boolean success = aiService.testOpenAIConnection(request.getApiKey(), request.getModel());
            return ResponseEntity.ok(Map.of("success", success));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }
    
    @PostMapping("/test-ollama")
    public ResponseEntity<Map<String, Object>> testOllama(@RequestBody OllamaTestRequest request) {
        try {
            boolean success = aiService.testOllamaConnection(request.getUrl(), request.getModel());
            return ResponseEntity.ok(Map.of("success", success));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}

// AI Tools API for GeoPulse data queries
@RestController
@RequestMapping("/api/ai-tools")
@PreAuthorize("hasRole('USER')")
public class AIToolsController {
    
    @GetMapping("/query-stays")
    public ResponseEntity<List<StayQueryResult>> queryStays(
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String dateRange,
            @RequestParam(required = false) Integer minDurationMinutes,
            Authentication auth) {
        
        String userId = auth.getName();
        
        // Apply privacy filters based on user settings
        UserAISettings settings = aiSettingsService.getUserSettings(userId);
        
        List<StayQueryResult> results = timelineService.queryStays(
            userId, location, dateRange, minDurationMinutes, settings.getPrivacySettings()
        );
        
        return ResponseEntity.ok(results);
    }
    
    @GetMapping("/analyze-location-patterns")
    public ResponseEntity<LocationPatternAnalysis> analyzePatterns(
            @RequestParam String location,
            @RequestParam(required = false) String period,
            Authentication auth) {
        
        String userId = auth.getName();
        UserAISettings settings = aiSettingsService.getUserSettings(userId);
        
        LocationPatternAnalysis analysis = analyticsService.analyzeLocationPatterns(
            userId, location, period, settings.getPrivacySettings()
        );
        
        return ResponseEntity.ok(analysis);
    }
    
    @GetMapping("/search-locations")
    public ResponseEntity<List<LocationMatch>> searchLocations(
            @RequestParam String query,
            @RequestParam(required = false) String context,
            Authentication auth) {
        
        String userId = auth.getName();
        UserAISettings settings = aiSettingsService.getUserSettings(userId);
        
        List<LocationMatch> matches = locationService.fuzzySearchLocations(
            userId, query, context, settings.getPrivacySettings()
        );
        
        return ResponseEntity.ok(matches);
    }
}
```

### 4. Navigation Integration

```javascript
// Update /src/components/ui/layout/AppNavigation.vue
const accountItems = computed(() => [
  {
    label: 'Profile',
    icon: 'pi pi-user',
    to: '/app/profile',
    key: 'profile'
  },
  {
    label: 'Location Sources',
    icon: 'pi pi-mobile', 
    to: '/app/location-sources',
    key: 'location-sources'
  },
  {
    label: 'Share Links',
    icon: 'pi pi-share-alt',
    to: '/app/share-links',
    key: 'share-links'
  },
  {
    label: 'Export / Import',
    icon: 'pi pi-download',
    to: '/app/data-export-import',
    key: 'export'
  },
  {
    label: 'GPS Data',
    icon: 'pi pi-database',
    to: '/app/gps-data',
    key: 'gps-data'
  },
  {
    label: 'AI Assistant',     // NEW ITEM
    icon: 'pi pi-android',
    to: '/app/ai-chat',
    key: 'ai-chat',
    badge: isAIConfigured.value ? null : '!',
    badgeType: 'warning'
  },
  {
    label: 'Preferences',
    icon: 'pi pi-cog',
    to: '/app/timeline/preferences',
    key: 'preferences'
  }
])
```

### 5. Router Configuration

```javascript
// Add to /src/router/index.js
{
  path: '/app/ai-chat',
  name: 'AIChat',
  component: () => import('@/views/app/AIChatPage.vue'),
  meta: { requiresAuth: true }
}
```

## Key Features

### 1. **User-Controlled Configuration**
- **Opt-in Only**: AI disabled by default, requires explicit activation
- **Bring Your Own Key**: Users provide OpenAI API keys or Ollama endpoints
- **Multiple Providers**: Support for OpenAI, Ollama, and custom endpoints
- **Usage Limits**: Daily query limits to prevent unexpected costs

### 2. **Privacy Controls**
- **Location Name Privacy**: Option to anonymize location names
- **Coordinate Privacy**: Option to send only approximate coordinates
- **Conversation Storage**: User choice to save/delete chat history
- **Data Minimization**: Only send necessary data to AI providers

### 3. **Smart Conversation Interface**
- **Natural Language**: Ask questions in plain English
- **Context Awareness**: Understand follow-up questions and references
- **Rich Responses**: Tables, charts, and map integrations in responses
- **Export Capabilities**: Export conversation data and AI results

### 4. **Backend AI Tools Integration**
- **Function Calling**: AI can call specific GeoPulse data query functions
- **Privacy-Safe Responses**: Respect user privacy settings in all responses
- **Performance Optimized**: Efficient data queries for AI processing
- **Error Handling**: Graceful handling of AI service failures

## Privacy & Security Considerations

### 1. **Data Handling**
- **User API Keys**: Encrypted at rest, only used for user's own requests
- **Location Data**: Anonymized/aggregated based on user preferences
- **Conversation Storage**: Optional, user-controlled local storage
- **No Third-Party Sharing**: Data only sent to user-configured AI providers

### 2. **Usage Controls**
- **Daily Limits**: Prevent unexpected API costs
- **Connection Testing**: Validate AI provider connections before use
- **Error Boundaries**: Fail gracefully when AI services are unavailable
- **Audit Logging**: Track AI usage for transparency

## Implementation Summary

### Complete AI Tool Coverage

The LangChain4j implementation provides comprehensive coverage for GeoPulse AI queries:

#### **Complete AI Tool Coverage:**

**Core Timeline & Travel Tools:**
1. **`queryTimeline`** - Timeline data for date ranges ("Where did I go on Sept 15?")
2. **`analyzeTemporalPatterns`** - Complex time-based analysis ("Do I travel more on Sundays vs Saturdays?") 
3. **`compareActivities`** - Comparative analysis ("How many cities did I visit in 2025?")
4. **`queryGpsPoints`** - Raw GPS data for quality analysis ("Show me GPS points with low accuracy")
5. **`getTravelStatistics`** - Statistical summaries and insights
6. **`analyzeDataGaps`** - Find missing data periods ("Show me data gaps longer than 2 hours")

**Location Intelligence Tools:**
7. **`searchLocations`** - Enhanced location search with favorites/geocoding context ("Find all Starbucks visits")
8. **`queryFavoriteLocations`** - Analyze user's favorite locations and usage ("Which favorites haven't I visited?")
9. **`analyzeSpatialPatterns`** - Spatial proximity and coverage analysis ("Show areas near work without favorites")
10. **`analyzeLocationNaming`** - Location name resolution quality analysis ("Compare geocoding providers")
11. **`analyzeResolutionQuality`** - Data source quality comparison ("GPS accuracy near favorites vs random places")

**Social & Integration Tools:**
12. **`analyzeSocialConnections`** - Friend connections and location sharing ("Show my friends list")
13. **`analyzeGpsSourceIntegrations`** - GPS source management and quality ("Which GPS source is most accurate?")

**Achievement & Sharing Tools:**
14. **`analyzeAchievements`** - Badge progress and achievement tracking ("What badges have I earned?")
15. **`analyzeSharedLinks`** - Shared location links management ("What links have I shared?")

#### **Natural Language Processing:**
- **Date parsing**: "last month", "September 15", "weekends"  
- **Location matching**: "Starbucks near downtown", "restaurants"
- **Comparative queries**: "more on Sundays vs Saturdays"
- **Context awareness**: "lunch spots", "work commute", "morning trips"

#### **Privacy & Performance:**
- **User-controlled data filtering** based on privacy settings
- **Performance limits** (GPS points capped at 1000 per query)
- **Existing service integration** - leverages optimized repositories
- **Type-safe parameters** - compile-time validation

#### **Complex Query Examples Supported:**

**Timeline & Travel Analysis:**
- "Do I travel more on Sundays or Saturdays?" → `analyzeTemporalPatterns(day_of_week, compare, [Sunday, Saturday])`
- "How many cities did I visit in 2025?" → `getTravelStatistics(summary, yearly, 2025)`
- "What's my average travel distance on weekdays vs weekends?" → `compareActivities(days, [weekdays, weekends], distance)`
- "Show me GPS points with low accuracy last week" → `queryGpsPoints(last_week, low_accuracy, 100)`
- "When do I have data gaps longer than 2 hours?" → `analyzeDataGaps(2 hours, last_month)`

**Location Intelligence Queries:**
- "What's my most visited restaurant in downtown?" → `searchLocations(restaurants, frequency, downtown, all)`  
- "Which favorite locations haven't I visited recently?" → `queryFavoriteLocations(unused, usage_stats, last_3_months)`
- "Show me areas near work where I don't have favorite locations" → `analyzeSpatialPatterns(coverage_gaps, work, 1km, include_favorites=true)`
- "Compare my GPS accuracy near favorite locations vs random places" → `analyzeResolutionQuality(accuracy, all, favorites, true)`
- "What geocoded restaurants should I add to favorites?" → `searchLocations(restaurants, frequency, geocoded_only, true)`

**Social & Integration Queries:**
- "Show me my friends list and pending invitations" → `analyzeSocialConnections(friends_list, all, last_month, false)`
- "Which GPS source provides the most accurate data?" → `analyzeGpsSourceIntegrations(data_quality, all, last_month, true)`
- "Are all my GPS integrations connected and working?" → `analyzeGpsSourceIntegrations(connection_status, active_only, today, true)`
- "Do I visit similar places as my friends?" → `analyzeSocialConnections(social_patterns, active_friends, last_month, true)`

**Achievement & Sharing Queries:**
- "What badges have I earned this year?" → `analyzeAchievements(earned_badges, earned, true, this_year)`
- "How close am I to the Globe Trotter achievement?" → `analyzeAchievements(badge_progress, globe_trotter_id, true, all_time)`
- "What location links have I shared recently?" → `analyzeSharedLinks(active_links, true, last_month)`
- "Which shared link gets the most views?" → `analyzeSharedLinks(link_usage, true, all_time)`

### **Architecture Benefits:**

✅ **Leverages Existing Services**: Reuses `StatisticsService`, `PlacesAnalysisService`, `StreamingTimelineAggregator`  
✅ **Handles Complex Analytics**: Sophisticated temporal and comparative analysis through existing business logic  
✅ **Type-Safe & Reliable**: Java-based tool definitions with compile-time validation  
✅ **Performance Optimized**: Uses existing optimized queries, spatial indexing, and pagination  
✅ **Privacy-First**: Respects user privacy settings and data anonymization preferences  
✅ **Multi-Provider Support**: OpenAI, Ollama, and custom endpoints through LangChain4j  
✅ **Extensible**: Easy to add new tools as annotated Java methods  
✅ **GPS Points Access**: Raw data access for quality analysis and debugging  
✅ **Natural Language Support**: Handles complex natural language to structured parameters  

### **Implementation Timeline:**

**Phase 1 (1-2 weeks)**: 
- Core AI infrastructure and LangChain4j integration
- Secure API key encryption system
- Basic AI service setup with OpenAI/Ollama support

**Phase 2 (2-3 weeks)**: 
- All 15 AI tools implementation using existing GeoPulse services
- Conversation history database schema and service
- Privacy controls and data filtering

**Phase 3 (1-2 weeks)**: 
- Frontend chat UI with conversation history sidebar
- AI settings integration in user profile
- Navigation and routing setup

**Total: 4-6 weeks** for a fully functional AI assistant covering all GeoPulse features with conversation history and sophisticated analytical capabilities.

This AI Chat Assistant feature provides powerful natural language querying capabilities while maintaining complete user control over configuration, privacy, and usage. It's designed as a completely optional enhancement that doesn't impact core GeoPulse functionality and transforms existing analytical services into an intelligent conversational interface.
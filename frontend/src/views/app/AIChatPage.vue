<template>
  <AppLayout>
    <PageContainer>
      <div class="ai-chat-page">
        <!-- Page Header -->
        <div class="page-header">
          <div class="header-content">
            <div class="header-text">
              <h1 class="page-title">AI Chat Assistant</h1>
              <p class="page-description">
                Ask questions about your location data and get intelligent insights
              </p>
            </div>
          </div>
        </div>

        <!-- Chat Container -->
        <div class="chat-container">
          <Card class="chat-card">
            <template #content>
              <!-- Chat Messages -->
              <div class="chat-messages" ref="messagesContainer">
                <!-- Loading State -->
                <div v-if="checkingAIStatus" class="empty-state">
                  <div class="empty-icon">
                    <i class="pi pi-spin pi-spinner text-4xl text-blue-500"></i>
                  </div>
                  <h3 class="empty-title">Checking AI status...</h3>
                </div>

                <!-- AI Disabled State -->
                <div v-else-if="!isAIAvailable" class="empty-state">
                  <div class="empty-icon">
                    <i class="pi pi-exclamation-triangle text-6xl text-orange-500"></i>
                  </div>
                  <h3 class="empty-title">AI Chat Unavailable</h3>
                  <p class="empty-description">
                    <span v-if="!aiSettings.enabled">
                      AI Assistant is currently disabled.
                    </span>
                    <span v-else-if="!aiSettings.openaiApiKeyConfigured">
                      OpenAI API key is not configured.
                    </span>
                    <span v-else>
                      AI Assistant is not properly configured.
                    </span>
                  </p>
                  <div class="config-actions">
                    <Button
                      label="Configure AI Settings"
                      icon="pi pi-cog"
                      class="p-button-primary"
                      @click="$router.push('/app/profile?tab=ai')"
                    />
                  </div>
                </div>

                <!-- Ready State -->
                <div v-else-if="messages.length === 0" class="empty-state">
                  <div class="empty-icon">
                    <i class="pi pi-sparkles text-6xl text-blue-500"></i>
                  </div>
                  <h3 class="empty-title">Start a conversation</h3>
                  <p class="empty-description">
                    Ask me about your location data. For example:
                  </p>
                  <div class="example-questions">
                    <div class="example-question" @click="sendMessage('Show me my timeline for last week')">
                      "Show me my timeline for last week"
                    </div>
                    <div class="example-question" @click="sendMessage('What are my favorite locations?')">
                      "What are my favorite locations?"
                    </div>
                    <div class="example-question" @click="sendMessage('How much did I travel this month?')">
                      "How much did I travel this month?"
                    </div>
                  </div>
                </div>

                <div v-for="(message, index) in messages" :key="index" class="message-wrapper">
                  <!-- User Message -->
                  <div v-if="message.type === 'user'" class="message user-message">
                    <div class="message-content">
                      <p>{{ message.content }}</p>
                    </div>
                    <div class="message-avatar">
                      <Avatar icon="pi pi-user" class="p-avatar-sm" />
                    </div>
                  </div>

                  <!-- AI Response -->
                  <div v-else class="message ai-message">
                    <div class="message-avatar">
                      <Avatar class="p-avatar-sm ai-avatar">
                        <i class="pi pi-sparkles"></i>
                      </Avatar>
                    </div>
                    <div class="message-content">
                      <p class="whitespace-pre-wrap">{{ message.content }}</p>
                    </div>
                  </div>
                </div>

                <!-- Loading Message -->
                <div v-if="isLoading" class="message ai-message loading-message">
                  <div class="message-avatar">
                    <Avatar class="p-avatar-sm ai-avatar">
                      <i class="pi pi-sparkles"></i>
                    </Avatar>
                  </div>
                  <div class="message-content">
                    <div class="typing-indicator">
                      <span></span>
                      <span></span>
                      <span></span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Loading Progress Bar -->
              <div v-if="isLoading" class="loading-progress">
                <div class="progress-content">
                  <div class="progress-icon">
                    <i class="pi pi-sparkles spinning-icon"></i>
                  </div>
                  <div class="progress-text">
                    <span class="progress-title">AI is thinking...</span>
                    <span class="progress-subtitle">Analyzing your request and location data</span>
                  </div>
                </div>
                <ProgressBar mode="indeterminate" class="ai-progress-bar" />
              </div>

              <!-- Chat Input -->
              <div class="chat-input-container">
                <div class="chat-input">
                  <InputText
                    v-model="currentMessage"
                    :placeholder="isAIAvailable ? 'Ask me about your location data...' : 'AI Assistant is not available'"
                    class="message-input"
                    @keyup.enter="handleSendMessage"
                    :disabled="isLoading || !isAIAvailable || checkingAIStatus"
                  />
                  <Button
                    icon="pi pi-send"
                    class="send-button"
                    @click="handleSendMessage"
                    :disabled="!currentMessage.trim() || isLoading || !isAIAvailable || checkingAIStatus"
                    :loading="isLoading"
                  />
                </div>
                <div v-if="!isAIAvailable && !checkingAIStatus" class="input-warning">
                  <i class="pi pi-exclamation-triangle"></i>
                  <span>
                    <span v-if="!aiSettings.enabled">AI Assistant is disabled.</span>
                    <span v-else-if="!aiSettings.openaiApiKeyConfigured">API key not configured.</span>
                    <span v-else>AI Assistant not available.</span>
                  </span>
                </div>
              </div>
            </template>
          </Card>
        </div>
      </div>
    </PageContainer>
  </AppLayout>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { useToast } from 'primevue/usetoast'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import apiService from '@/utils/apiService.js'

const toast = useToast()

// Reactive data
const messages = ref([])
const currentMessage = ref('')
const isLoading = ref(false)
const messagesContainer = ref(null)
const aiSettings = ref({
  enabled: false,
  openaiApiKeyConfigured: false,
  openaiModel: 'gpt-3.5-turbo'
})
const isAIAvailable = ref(false)
const checkingAIStatus = ref(true)

// Methods
const checkAIAvailability = async () => {
  try {
    const response = await apiService.get('/ai/settings')
    const data = response.data || response
    
    aiSettings.value = {
      enabled: data.enabled === true,
      openaiApiKeyConfigured: data.openaiApiKeyConfigured === true,
      openaiModel: data.openaiModel || 'gpt-3.5-turbo'
    }
    
    isAIAvailable.value = aiSettings.value.enabled && aiSettings.value.openaiApiKeyConfigured
    
  } catch (error) {
    console.warn('Failed to check AI settings:', error)
    isAIAvailable.value = false
  } finally {
    checkingAIStatus.value = false
  }
}

const sendMessage = async (messageText) => {
  const message = messageText || currentMessage.value.trim()
  if (!message || isLoading.value || !isAIAvailable.value) return

  // Add user message
  messages.value.push({
    type: 'user',
    content: message,
    timestamp: new Date()
  })

  // Clear input
  currentMessage.value = ''
  
  // Scroll to bottom
  await nextTick()
  scrollToBottom()

  // Set loading state
  isLoading.value = true

  try {
    // Send to AI
    const { data } = await apiService.post('/ai/chat', { message })

    console.log('AI Response:', data)
    
    // Add AI response
    messages.value.push({
      type: 'ai',
      content: data.response,
      timestamp: new Date()
    })

    // Scroll to bottom
    await nextTick()
    scrollToBottom()

  } catch (error) {
    console.error('Error sending message:', error)
    
    let errorMessage = 'Sorry, I encountered an error while processing your request.'
    
    if (error.response?.status === 400) {
      errorMessage = 'Please check your AI settings in your profile before using the chat assistant.'
    } else if (error.response?.data?.response) {
      errorMessage = error.response.data.response
    }

    messages.value.push({
      type: 'ai',
      content: errorMessage,
      timestamp: new Date()
    })

    toast.add({
      severity: 'error',
      summary: 'Chat Error',
      detail: 'Failed to get AI response. Please check your AI settings.'
    })
  } finally {
    isLoading.value = false
  }
}

const handleSendMessage = () => {
  sendMessage()
}

const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

// Check AI configuration on mount
onMounted(async () => {
  await checkAIAvailability()
  
  if (!isAIAvailable.value) {
    let detail = 'Please configure your AI settings in your profile to use the chat assistant.'
    
    if (!aiSettings.value.enabled) {
      detail = 'AI Assistant is disabled. Please enable it in your profile settings.'
    } else if (!aiSettings.value.openaiApiKeyConfigured) {
      detail = 'Please configure your OpenAI API key in your profile to use the chat assistant.'
    }
    
    toast.add({
      severity: 'warn',
      summary: 'AI Chat Unavailable',
      detail,
      life: 6000
    })
  }
})
</script>

<style scoped>
.ai-chat-page {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.page-header {
  margin-bottom: 1.5rem;
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.page-title {
  font-size: 2rem;
  font-weight: bold;
  color: var(--text-color);
  margin: 0;
}

.page-description {
  color: var(--text-color-secondary);
  margin: 0.5rem 0 0 0;
}

.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.chat-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 250px);
  min-height: 600px;
}

.chat-card :deep(.p-card-body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 0;
}

.chat-card :deep(.p-card-content) {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 1rem;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 1rem 0;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  padding: 2rem;
}

.empty-icon {
  margin-bottom: 1rem;
}

.empty-title {
  font-size: 1.5rem;
  font-weight: bold;
  color: var(--text-color);
  margin: 0 0 0.5rem 0;
}

.empty-description {
  color: var(--text-color-secondary);
  margin-bottom: 1.5rem;
}

.example-questions {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  max-width: 400px;
}

.example-question {
  padding: 0.75rem 1rem;
  background: var(--surface-100);
  border: 1px solid var(--surface-300);
  border-radius: 0.5rem;
  cursor: pointer;
  transition: all 0.2s;
  font-style: italic;
  color: var(--text-color-secondary);
}

.example-question:hover {
  background: var(--surface-200);
  border-color: var(--primary-color);
  color: var(--text-color);
}

.message-wrapper {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.message {
  display: flex;
  gap: 0.75rem;
  max-width: 80%;
}

.user-message {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.ai-message {
  align-self: flex-start;
}

.message-content {
  padding: 0.75rem 1rem;
  border-radius: 1rem;
  word-wrap: break-word;
}

.user-message .message-content {
  background: var(--primary-color);
  color: var(--primary-color-text);
  border-bottom-right-radius: 0.25rem;
}

.ai-message .message-content {
  background: var(--surface-100);
  color: var(--text-color);
  border-bottom-left-radius: 0.25rem;
}

.message-avatar {
  flex-shrink: 0;
}

.ai-avatar {
  background: var(--blue-500);
  color: white;
}

.loading-message .message-content {
  padding: 1rem;
}

.typing-indicator {
  display: flex;
  gap: 0.25rem;
  align-items: center;
}

.typing-indicator span {
  height: 8px;
  width: 8px;
  border-radius: 50%;
  background: var(--text-color-secondary);
  display: inline-block;
  animation: typing 1.4s infinite ease-in-out both;
}

.typing-indicator span:nth-child(1) {
  animation-delay: -0.32s;
}

.typing-indicator span:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes typing {
  0%, 80%, 100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}

.chat-input-container {
  border-top: 1px solid var(--surface-300);
  padding-top: 1rem;
  margin-top: 1rem;
}

.chat-input {
  display: flex;
  gap: 0.75rem;
  align-items: flex-end;
}

.message-input {
  flex: 1;
}

.send-button {
  flex-shrink: 0;
}

.config-actions {
  margin-top: 1.5rem;
}

.input-warning {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.75rem;
  padding: 0.75rem;
  background: var(--orange-50);
  border: 1px solid var(--orange-200);
  border-radius: 0.5rem;
  color: var(--orange-700);
  font-size: 0.9rem;
}

.input-warning i {
  color: var(--orange-500);
}

.loading-progress {
  border-top: 1px solid var(--surface-300);
  border-bottom: 1px solid var(--surface-300);
  background: var(--surface-50);
  padding: 1rem;
  margin: 1rem 0;
}

.progress-content {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 0.75rem;
}

.progress-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem;
  height: 2.5rem;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 50%;
  flex-shrink: 0;
}

.spinning-icon {
  font-size: 1.25rem;
  animation: spin 2s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.progress-text {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.progress-title {
  font-weight: 600;
  color: var(--text-color);
  font-size: 0.95rem;
}

.progress-subtitle {
  color: var(--text-color-secondary);
  font-size: 0.85rem;
}

.ai-progress-bar {
  height: 0.5rem;
  border-radius: 0.25rem;
}

.ai-progress-bar :deep(.p-progressbar) {
  background: var(--surface-200);
  border-radius: 0.25rem;
  overflow: hidden;
}

.ai-progress-bar :deep(.p-progressbar-value) {
  background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
  border-radius: 0.25rem;
}

/* Scrollbar styling */
.chat-messages::-webkit-scrollbar {
  width: 6px;
}

.chat-messages::-webkit-scrollbar-track {
  background: var(--surface-100);
  border-radius: 3px;
}

.chat-messages::-webkit-scrollbar-thumb {
  background: var(--surface-400);
  border-radius: 3px;
}

.chat-messages::-webkit-scrollbar-thumb:hover {
  background: var(--surface-500);
}

/* Responsive */
@media (max-width: 768px) {
  .chat-card {
    height: calc(100vh - 200px);
    min-height: 500px;
  }

  .message {
    max-width: 90%;
  }

  .example-questions {
    max-width: 100%;
  }

  .page-title {
    font-size: 1.5rem;
  }

  .progress-content {
    flex-direction: column;
    text-align: center;
    gap: 0.75rem;
  }

  .progress-text {
    align-items: center;
  }
}
</style>
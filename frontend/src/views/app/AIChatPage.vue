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
              <div class="ai-disclaimer">
                <i class="pi pi-info-circle"></i>
                <span>AI responses are based on data analysis but may contain errors. Please verify important information independently.</span>
              </div>
            </div>
            <div class="header-actions" v-if="hasMessages">
              <Button
                icon="pi pi-trash"
                class="p-button-text p-button-sm clear-history-btn"
                @click="clearMessageHistory"
                :disabled="isLoading"
                v-tooltip="'Clear conversation history'"
                aria-label="Clear conversation history"
              />
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
                <div v-else-if="showExamples" class="empty-state">
                  <div class="empty-icon">
                    <i class="pi pi-sparkles text-6xl text-blue-500"></i>
                  </div>
                  <h3 class="empty-title">Start a conversation</h3>
                  <p class="empty-description">
                    Ask me about your location data. For example:
                  </p>
                  <div class="example-questions">
                    <div class="example-question" @click="sendMessage('Do I walk more or drive more?')">
                      "Do I walk more or drive more?"
                    </div>
                    <div class="example-question" @click="sendMessage('Which day of the week do I travel most?')">
                      "Which day of the week do I travel most?"
                    </div>
                    <div class="example-question" @click="sendMessage('How many different cities did I visit this month?')">
                      "How many different cities did I visit this month?"
                    </div>
                    <div class="example-question" @click="sendMessage('What\'s my most common route?')">
                      "What's my most common route?"
                    </div>
                  </div>
                </div>

                <!-- Expired Conversation State -->
                <div v-else-if="hasExpiredConversation && !hasMessages" class="empty-state">
                  <div class="empty-icon">
                    <i class="pi pi-refresh text-6xl text-orange-500"></i>
                  </div>
                  <h3 class="empty-title">Ready for a new conversation!</h3>
                  <p class="empty-description">
                    Your previous conversation has expired. Ask me about your location data:
                  </p>
                  <div class="example-questions">
                    <div class="example-question" @click="sendMessage('Do I walk more or drive more?')">
                      "Do I walk more or drive more?"
                    </div>
                    <div class="example-question" @click="sendMessage('Which day of the week do I travel most?')">
                      "Which day of the week do I travel most?"
                    </div>
                    <div class="example-question" @click="sendMessage('How many different cities did I visit this month?')">
                      "How many different cities did I visit this month?"
                    </div>
                    <div class="example-question" @click="sendMessage('What\'s my most common route?')">
                      "What's my most common route?"
                    </div>
                  </div>
                </div>

                <div v-for="message in messages" :key="message.id" class="message-wrapper">
                  <!-- User Message -->
                  <div v-if="message.type === 'user'" class="message user-message">
                    <div class="message-content">
                      <p>{{ message.content }}</p>
                      <div class="message-timestamp">
                        {{ formatTimestamp(message.timestamp) }}
                      </div>
                    </div>
                  </div>

                  <!-- AI Response -->
                  <div v-else class="message ai-message" :class="{ 'error-message': isErrorMessage(message.content) }">
                    <div class="message-avatar">
                      <Avatar class="p-avatar-sm" :class="isErrorMessage(message.content) ? 'error-avatar' : 'ai-avatar'">
                        <i :class="isErrorMessage(message.content) ? 'pi pi-exclamation-triangle' : 'pi pi-sparkles'"></i>
                      </Avatar>
                    </div>
                    <div class="message-content">
                      <div v-if="isErrorMessage(message.content)" class="error-indicator">
                        <i class="pi pi-exclamation-triangle"></i>
                        <span>Error</span>
                      </div>
                      <div class="whitespace-pre-wrap" v-html="sanitizeAndFormatMessage(message.content)"></div>
                      <div class="message-timestamp ai-timestamp">
                        {{ formatTimestamp(message.timestamp) }}
                      </div>
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
import { ref, nextTick, onMounted, computed } from 'vue'
import { useToast } from 'primevue/usetoast'
import AppLayout from '@/components/ui/layout/AppLayout.vue'
import PageContainer from '@/components/ui/layout/PageContainer.vue'
import apiService from '@/utils/apiService.js'

const toast = useToast()

// Constants
const MESSAGE_RETENTION_DAYS = 7
const MAX_STORED_MESSAGES = 10
const STORAGE_KEY = 'geopulse-ai-messages'
const EXPIRED_FLAG_KEY = 'geopulse-ai-messages-expired'

// Reactive data
const messages = ref([])
const currentMessage = ref('')
const isLoading = ref(false)
const messagesContainer = ref(null)
const hasExpiredConversation = ref(false)
const aiSettings = ref({
  enabled: false,
  openaiApiKeyConfigured: false,
  openaiModel: 'gpt-3.5-turbo'
})
const isAIAvailable = ref(false)
const checkingAIStatus = ref(true)

// Computed properties
const hasMessages = computed(() => messages.value.length > 0)
const showExamples = computed(() => !hasMessages.value && !hasExpiredConversation.value)

// Utility functions
const generateMessageId = () => {
  return Date.now().toString(36) + Math.random().toString(36).substr(2, 9)
}

const formatTimestamp = (date) => {
  const now = new Date()
  const messageDate = new Date(date)
  const diffMs = now - messageDate
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  
  if (diffMs < 60000) return 'Just now'
  if (diffMs < 3600000) return `${diffMins}m ago`
  
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const msgDate = new Date(messageDate)
  msgDate.setHours(0, 0, 0, 0)
  
  if (msgDate.getTime() === today.getTime()) {
    return messageDate.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  }
  
  const yesterday = new Date(today)
  yesterday.setDate(yesterday.getDate() - 1)
  if (msgDate.getTime() === yesterday.getTime()) {
    return `Yesterday ${messageDate.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`
  }
  
  return messageDate.toLocaleDateString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

const saveMessagesToStorage = (messagesToSave) => {
  try {
    const conversationData = {
      messages: messagesToSave.slice(-MAX_STORED_MESSAGES),
      lastActivity: new Date().toISOString(),
      created: new Date().toISOString()
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(conversationData))
  } catch (error) {
    console.warn('Failed to save messages to localStorage:', error)
  }
}

const loadMessagesFromStorage = () => {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (!stored) return { messages: [], hasExpired: false }
    
    const conversationData = JSON.parse(stored)
    const cutoffDate = new Date(Date.now() - (MESSAGE_RETENTION_DAYS * 24 * 60 * 60 * 1000))
    
    // Check if any message is within retention period
    const hasRecentMessages = conversationData.messages.some(msg => 
      new Date(msg.timestamp) > cutoffDate
    )
    
    if (hasRecentMessages) {
      return { messages: conversationData.messages, hasExpired: false }
    } else {
      // Mark as expired and clear storage
      localStorage.setItem(EXPIRED_FLAG_KEY, 'true')
      localStorage.removeItem(STORAGE_KEY)
      return { messages: [], hasExpired: true }
    }
  } catch (error) {
    console.warn('Failed to load messages from localStorage:', error)
    return { messages: [], hasExpired: false }
  }
}

const clearMessageHistory = () => {
  localStorage.removeItem(STORAGE_KEY)
  localStorage.removeItem(EXPIRED_FLAG_KEY)
  messages.value = []
  hasExpiredConversation.value = false
  
  toast.add({
    severity: 'info',
    summary: 'Chat Cleared',
    detail: 'Your conversation history has been cleared.',
    life: 3000
  })
}

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

  // Clear expired conversation flag if showing
  hasExpiredConversation.value = false

  // Add user message with enhanced structure
  const userMessage = {
    id: generateMessageId(),
    type: 'user',
    content: message,
    timestamp: new Date().toISOString()
  }
  messages.value.push(userMessage)

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
    
    // Add AI response with enhanced structure
    const aiMessage = {
      id: generateMessageId(),
      type: 'ai',
      content: data.response,
      timestamp: new Date().toISOString()
    }
    messages.value.push(aiMessage)
    
    // Save updated conversation to localStorage
    saveMessagesToStorage(messages.value)

    // Scroll to bottom
    await nextTick()
    scrollToBottom()

  } catch (error) {
    console.error('Error sending message:', error)
    
    let errorContent = 'Sorry, I encountered an error while processing your request.'
    
    if (error.response?.status === 400) {
      errorContent = 'Please check your AI settings in your profile before using the chat assistant.'
    } else if (error.response?.data?.response) {
      errorContent = error.response.data.response
    }

    const errorMessage = {
      id: generateMessageId(),
      type: 'ai',
      content: errorContent,
      timestamp: new Date().toISOString()
    }
    messages.value.push(errorMessage)
    
    // Save updated conversation to localStorage
    saveMessagesToStorage(messages.value)

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

const isErrorMessage = (content) => {
  if (!content) return false
  
  const errorPatterns = [
    /I apologize.*error/i,
    /I'm currently experiencing.*rate limit/i,
    /AI Assistant is.*disabled/i,
    /not configured/i,
    /encountered an error/i,
    /check your.*settings/i,
    /^Sorry.*error/i,
    /Please wait.*try again/i,
    /upgrading.*API plan/i
  ]
  
  return errorPatterns.some(pattern => pattern.test(content))
}

const sanitizeAndFormatMessage = (content) => {
  if (!content) return ''
  
  // Escape HTML first to prevent XSS
  let sanitized = content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#x27;')
  
  // Convert markdown to HTML
  sanitized = sanitized
    // Bold text: **text** or __text__
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/__(.*?)__/g, '<strong>$1</strong>')
    
    // Italic text: *text* or _text_
    .replace(/(?<!\*)\*([^*]+)\*(?!\*)/g, '<em>$1</em>')
    .replace(/(?<!_)_([^_]+)_(?!_)/g, '<em>$1</em>')
    
    // Code: `code`
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    
    // Headers: # Header, ## Header, etc.
    .replace(/^### (.*$)/gm, '<h3>$1</h3>')
    .replace(/^## (.*$)/gm, '<h2>$1</h2>')
    .replace(/^# (.*$)/gm, '<h1>$1</h1>')
    
    // Bullet points: - item or * item
    .replace(/^[\s]*[-*]\s+(.*)$/gm, '<li>$1</li>')
    
    // Numbered lists: 1. item
    .replace(/^[\s]*\d+\.\s+(.*)$/gm, '<li>$1</li>')
    
    // Line breaks
    .replace(/\n\n/g, '</p><p>')
    .replace(/\n/g, '<br>')
  
  // Wrap consecutive <li> elements in <ul>
  sanitized = sanitized
    .replace(/(<li>.*?<\/li>)(\s*<li>.*?<\/li>)*/gs, (match) => {
      return '<ul>' + match + '</ul>'
    })
  
  // Wrap in paragraph tags if not already wrapped
  if (!sanitized.includes('<p>') && !sanitized.includes('<h1>') && !sanitized.includes('<h2>') && !sanitized.includes('<ul>')) {
    sanitized = '<p>' + sanitized + '</p>'
  }
  
  return sanitized
}

// Check AI configuration and load saved messages on mount
onMounted(async () => {
  await checkAIAvailability()
  
  // Load saved messages and handle expiration
  const { messages: savedMessages, hasExpired } = loadMessagesFromStorage()
  
  if (savedMessages.length > 0) {
    messages.value = savedMessages
    console.log(`Loaded ${savedMessages.length} messages from localStorage`)
  }
  
  // Check if we had expired messages
  if (hasExpired || localStorage.getItem(EXPIRED_FLAG_KEY)) {
    hasExpiredConversation.value = true
    localStorage.removeItem(EXPIRED_FLAG_KEY)
  }
  
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

.header-actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.clear-history-btn {
  opacity: 0.7;
  transition: opacity 0.2s;
}

.clear-history-btn:hover {
  opacity: 1;
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

.ai-disclaimer {
  margin-top: 1rem;
  padding: 0.75rem 1rem;
  background-color: var(--surface-50);
  border: 1px solid var(--surface-200);
  border-radius: 0.5rem;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  font-size: 0.875rem;
  color: var(--text-color-secondary);
}

.ai-disclaimer i {
  font-size: 1.125rem;
  color: var(--primary-color);
}

.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 100%;
  min-width: 0; /* Allow flex item to shrink below content size */
  overflow: hidden;
  min-height: 0;
}

.chat-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 250px);
  min-height: 500px;
  max-height: calc(100vh - 250px);
  width: 100%;
  max-width: 1200px; /* Set maximum width to prevent excessive expansion */
  min-width: 800px; /* Ensure consistent width from the start */
  margin: 0 auto; /* Center the chat card */
  overflow: hidden;
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
  padding: 1.5rem 0;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  box-sizing: border-box;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  text-align: center;
  padding: 2rem;
  min-height: 0;
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
  background: var(--surface-card);
  border: 1px solid var(--surface-border);
  border-radius: 0.5rem;
  cursor: pointer;
  transition: all 0.2s;
  font-style: italic;
  color: var(--text-color-secondary);
}

.example-question:hover {
  background: var(--surface-hover);
  border-color: var(--primary-color);
  color: var(--text-color);
}

.message-wrapper {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin: 1.25rem 0;
  position: relative;
}

.message-wrapper:first-child {
  margin-top: 0;
}

.message-wrapper:last-child {
  margin-bottom: 0;
}

/* Add subtle separator after user messages */
.message-wrapper:has(.user-message):not(:last-child)::after {
  content: '';
  position: absolute;
  bottom: -0.75rem;
  left: 15%;
  right: 15%;
  height: 2px;
  background: linear-gradient(90deg, transparent 0%, rgba(59, 130, 246, 0.3) 50%, transparent 100%);
  border-radius: 1px;
}

/* Alternative fallback for browsers without :has() support */
.user-message-wrapper::after {
  content: '';
  position: absolute;
  bottom: -0.75rem;
  left: 15%;
  right: 15%;
  height: 2px;
  background: linear-gradient(90deg, transparent 0%, rgba(59, 130, 246, 0.3) 50%, transparent 100%);
  border-radius: 1px;
}

.message {
  display: flex;
  gap: 0.75rem;
  max-width: 80%;
  width: fit-content;
  overflow-wrap: break-word;
  word-wrap: break-word;
  word-break: break-word;
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
  overflow-wrap: break-word;
  word-break: break-word;
  max-width: 100%;
  min-width: 0;
  box-sizing: border-box;
}

.user-message .message-content {
  background: var(--primary-color);
  color: var(--primary-color-text);
  border-bottom-right-radius: 0.25rem;
  border: 2px solid var(--primary-600);
  box-shadow: 0 4px 16px rgba(59, 130, 246, 0.25);
}

.ai-message .message-content {
  background: var(--surface-card);
  color: var(--text-color);
  border-bottom-left-radius: 0.25rem;
  border: 2px solid var(--surface-border);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  position: relative;
  /* Ensure better contrast in dark mode */
  filter: contrast(1.1) brightness(1.05);
}

.error-message .message-content {
  background: var(--red-50);
  border: 2px solid var(--red-200);
  box-shadow: 0 4px 16px rgba(239, 68, 68, 0.15);
}

.error-message .message-content::before {
  background: linear-gradient(135deg, rgba(239, 68, 68, 0.1) 0%, rgba(220, 38, 38, 0.1) 100%);
}

.error-indicator {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
  padding: 0.5rem 0.75rem;
  background: var(--red-100);
  border: 1px solid var(--red-200);
  border-radius: 0.5rem;
  color: var(--red-700);
  font-size: 0.875rem;
  font-weight: 600;
}

.error-indicator i {
  color: var(--red-500);
  font-size: 1rem;
}

.ai-message .message-content::before {
  content: '';
  position: absolute;
  top: -2px;
  left: -2px;
  right: -2px;
  bottom: -2px;
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.1) 0%, rgba(147, 51, 234, 0.1) 100%);
  border-radius: 1rem;
  z-index: -1;
  opacity: 0.8;
}

/* AI Message HTML Formatting */
.ai-message .message-content h1,
.ai-message .message-content h2,
.ai-message .message-content h3 {
  font-weight: bold;
  color: var(--text-color);
  margin: 0.5rem 0 0.25rem 0;
  line-height: 1.3;
}

.ai-message .message-content h1 {
  font-size: 1.25rem;
}

.ai-message .message-content h2 {
  font-size: 1.1rem;
}

.ai-message .message-content h3 {
  font-size: 1rem;
}

.ai-message .message-content strong {
  font-weight: 600;
  color: var(--text-color);
}

.ai-message .message-content em {
  font-style: italic;
  color: var(--text-color-secondary);
}

.ai-message .message-content code {
  background: var(--surface-100);
  color: var(--primary-color);
  padding: 0.125rem 0.25rem;
  border-radius: 0.25rem;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 0.875rem;
  border: 1px solid var(--surface-border);
}

.ai-message .message-content ul {
  margin: 0.5rem 0;
  padding-left: 1.25rem;
}

.ai-message .message-content li {
  margin: 0.25rem 0;
  list-style-type: disc;
  color: var(--text-color);
}

.ai-message .message-content p {
  margin: 0.5rem 0;
  line-height: 1.5;
}

.ai-message .message-content p:first-child {
  margin-top: 0;
}

.ai-message .message-content p:last-child {
  margin-bottom: 0;
}

.message-timestamp {
  font-size: 0.75rem;
  color: var(--text-color-secondary);
  margin-top: 0.375rem;
  opacity: 0.8;
  font-weight: 400;
}

.user-message .message-timestamp {
  text-align: right;
  color: var(--text-color-secondary);
  opacity: 0.8;
}

.ai-timestamp {
  text-align: left;
  margin-top: 0.5rem;
}

.message-avatar {
  flex-shrink: 0;
  filter: drop-shadow(0 2px 4px var(--surface-300));
}

.user-message .message-avatar {
  background: var(--primary-color) !important;
  color: var(--primary-color-text) !important;
  border: 2px solid var(--primary-600) !important;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3) !important;
  overflow: hidden;
}

.user-message .message-avatar :deep(.p-avatar) {
  background: var(--primary-color) !important;
  border: none !important;
  border-radius: 50% !important;
}

.user-message .message-avatar :deep(.p-avatar-icon) {
  font-size: 1rem !important;
  line-height: 1 !important;
  border: none !important;
}

.ai-avatar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%) !important;
  color: white !important;
  border: 2px solid rgba(102, 126, 234, 0.5) !important;
  animation: subtle-pulse 3s ease-in-out infinite;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4) !important;
  overflow: hidden;
}

.error-avatar {
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%) !important;
  color: white !important;
  border: 2px solid rgba(239, 68, 68, 0.5) !important;
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.4) !important;
  overflow: hidden;
}

.error-avatar :deep(.p-avatar) {
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%) !important;
  border: none !important;
  border-radius: 50% !important;
}

.error-avatar i {
  font-size: 1rem !important;
  line-height: 1 !important;
  border: none !important;
}

.ai-avatar :deep(.p-avatar) {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%) !important;
  border: none !important;
  border-radius: 50% !important;
}

.ai-avatar i {
  font-size: 1rem !important;
  line-height: 1 !important;
  border: none !important;
}

@keyframes subtle-pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.02); }
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
  margin-top: auto;
  flex-shrink: 0;
}

.chat-input {
  display: flex;
  gap: 0.75rem;
  align-items: flex-end;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
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
  .ai-chat-page {
    padding: 0;
    height: 100vh;
    height: 100dvh; /* Use dynamic viewport height for mobile browsers */
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }
  
  .page-header {
    margin-bottom: 0.5rem;
    flex-shrink: 0;
  }
  
  .page-title {
    font-size: 1.5rem;
    margin: 0;
  }
  
  .page-description {
    margin: 0.25rem 0 0 0;
    font-size: 0.9rem;
  }
  
  .ai-disclaimer {
    margin-top: 0.75rem;
    padding: 0.5rem 0.75rem;
    font-size: 0.8rem;
  }
  
  .chat-container {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }
  
  .chat-card {
    height: 100%;
    max-height: none;
    min-height: 0;
    min-width: 0;
    flex: 1;
    display: flex;
    flex-direction: column;
  }
  
  .chat-card :deep(.p-card-content) {
    padding: 0.75rem;
    flex: 1;
    display: flex;
    flex-direction: column;
    min-height: 0;
  }
  
  .chat-messages {
    flex: 0 1 auto;
    min-height: 0;
    padding: 0.5rem 0;
    gap: 1rem;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
  }
  
  .chat-messages::after {
    content: '';
    flex: 1;
    min-height: 2rem;
    max-height: 4rem;
  }
  
  .empty-state {
    padding: 0.5rem;
    flex: 0 1 auto;
    min-height: 0;
    justify-content: flex-start;
    padding-top: 1rem;
    max-height: fit-content;
  }
  
  .empty-title {
    font-size: 1.25rem;
    margin-bottom: 0.5rem;
  }
  
  .empty-description {
    margin-bottom: 1rem;
    font-size: 0.9rem;
  }
  
  .example-questions {
    gap: 0.5rem;
  }
  
  .example-question {
    padding: 0.5rem 0.75rem;
    font-size: 0.875rem;
  }
  
  .chat-input-container {
    padding-top: 0.75rem;
    margin-top: 0;
    flex-shrink: 0;
    border-top: 1px solid var(--surface-300);
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
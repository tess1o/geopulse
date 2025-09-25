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
                  </div>

                  <!-- AI Response -->
                  <div v-else class="message ai-message">
                    <div class="message-avatar">
                      <Avatar class="p-avatar-sm ai-avatar">
                        <i class="pi pi-sparkles"></i>
                      </Avatar>
                    </div>
                    <div class="message-content">
                      <div class="whitespace-pre-wrap" v-html="sanitizeAndFormatMessage(message.content)"></div>
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
  width: 100%;
  max-width: 100%;
  min-width: 0; /* Allow flex item to shrink below content size */
  overflow: hidden;
}

.chat-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 250px);
  min-height: 600px;
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
  margin-top: 1rem;
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
  .chat-card {
    height: calc(100vh - 200px);
    min-height: 500px;
    min-width: 0; /* Remove min-width on mobile */
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
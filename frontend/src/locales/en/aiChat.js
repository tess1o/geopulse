export default {
    page: {
        title: 'AI Chat Assistant',
        description: 'Ask questions about your location data and get intelligent insights',
        disclaimer: 'AI responses are based on data analysis but may contain errors. Please verify important information independently.',
        clearHistoryTooltip: 'Clear conversation history',
        clearHistoryAriaLabel: 'Clear conversation history'
    },
    status: {
        checking: 'Checking AI status...',
        unavailable: {
            title: 'AI Chat Unavailable',
            disabled: 'AI Assistant is currently disabled.',
            apiKeyMissing: 'OpenAI API key is not configured.',
            notConfigured: 'AI Assistant is not properly configured.',
            configureButton: 'Configure AI Settings',
            demoDisabledTooltip: 'AI settings are read-only in demo mode',
            demoDisabledText: 'AI settings are read-only in demo mode, so this configuration action is disabled.'
        }
    },
    examples: {
        startTitle: 'Start a conversation',
        startDescription: 'Ask me about your location data. For example:',
        expiredTitle: 'Ready for a new conversation!',
        expiredDescription: 'Your previous conversation has expired. Ask me about your location data:',
        walkOrDrive: 'Do I walk more or drive more?',
        busiestDay: 'Which day of the week do I travel most?',
        citiesVisited: 'How many different cities did I visit this month?',
        commonRoute: "What's my most common route?"
    },
    message: {
        error: 'Error'
    },
    loading: {
        title: 'AI is thinking...',
        subtitle: 'Analyzing your request and location data'
    },
    input: {
        placeholder: 'Ask me about your location data...',
        placeholderUnavailable: 'AI Assistant is not available',
        warningDisabled: 'AI Assistant is disabled.',
        warningApiKeyMissing: 'API key not configured.',
        warningUnavailable: 'AI Assistant not available.'
    },
    timestamp: {
        justNow: 'Just now',
        minutesAgo: '{count}m ago',
        yesterday: 'Yesterday {time}'
    },
    toasts: {
        chatClearedSummary: 'Chat Cleared',
        chatClearedDetail: 'Your conversation history has been cleared.',
        chatErrorSummary: 'Chat Error',
        genericError: 'Sorry, I encountered an error while processing your request.',
        aiDisabledError: 'AI Assistant is disabled. Enable it in your profile settings.',
        apiKeyRequiredError: 'Add your AI provider API key in your profile settings.',
        contextTooLargeError: 'The conversation or data is too large. Try a narrower question or clear the chat history.',
        rateLimitedError: 'The AI provider rate limit was reached. Please try again later.',
        authFailedError: 'The AI provider rejected the configured credentials.',
        invalidRequestError: 'The AI provider rejected this request.',
        unavailableSummary: 'AI Chat Unavailable',
        unavailableDemoDetail: 'AI settings are read-only in demo mode, so they cannot be configured from this demo account.',
        unavailableDisabledDetail: 'AI Assistant is disabled. Please enable it in your profile settings.',
        unavailableApiKeyDetail: 'Please configure your OpenAI API key in your profile to use the chat assistant.',
        unavailableDefaultDetail: 'Please configure your AI settings in your profile to use the chat assistant.'
    }
}

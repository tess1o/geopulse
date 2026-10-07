/**
 * Connected apps tab shell: the tab strip, per-app status chips, and the three panels.
 *
 * 'Immich' and 'Memos' are product names and stay untranslated. 'Enabled', 'Connected' and
 * 'Not configured' are asserted verbatim by ConnectedAppsTab.test.js.
 *
 * Every value under `ai`, `immich` and `memos` is the literal that used to sit in the matching panel
 * (AIAssistantTab, ImmichTab, MemosTab) or in its status/validation tables, copied verbatim --
 * profileTabsDirtyState.test.js matches some of them ('Test Connection', 'Reset', 'Timeline
 * defaults', 'Filtering & performance', 'Refresh provider models'), so none may be reworded.
 *
 * `messages.assetsAvailable` and `messages.memoCount` are the only values that are not a straight
 * literal: the panels built them with a JS template string, so the count is a named parameter here.
 * Both carry proper plural forms -- the originals read "1 assets available." and "1 memo(s)", which
 * Ukrainian's three required forms made impossible to preserve anyway.
 */
export default {
    title: 'Connected apps',
    description: 'Configure services that add AI, photos, and notes to GeoPulse.',
    listAria: 'Connected apps',
    apps: {
        ai: 'AI Assistant',
        immich: 'Immich',
        memos: 'Memos'
    },
    status: {
        enabled: 'Enabled',
        notEnabled: 'Not enabled',
        connected: 'Connected',
        notConfigured: 'Not configured'
    },
    ai: {
        availability: {
            heading: 'Assistant availability',
            description: 'Control whether AI chat is available in GeoPulse.',
            enabled: {
                title: 'Enable AI Assistant',
                description: 'Allow AI-powered chat and timeline assistance.'
            }
        },
        provider: {
            heading: 'Provider',
            description: 'Configure the OpenAI or OpenAI-compatible service used by the assistant.',
            apiKeyRequired: {
                title: 'API key required',
                description: 'Turn off only when the provider accepts unauthenticated requests.'
            },
            apiKey: {
                title: 'API key',
                description: 'Enter a new key only when adding or replacing credentials.',
                placeholder: 'Enter your OpenAI API key',
                placeholderConfigured: 'API key is configured (enter new key to replace)',
                ariaLabel: 'OpenAI API key',
                configuredNote: 'API key is configured. Leave empty to keep it.',
                missingNote: 'Enter an API key to enable authenticated requests.'
            },
            // The base URL field's aria-label is the same sentence as its title, so it reuses
            // `baseUrl.title` rather than duplicating the value -- matching the GPS-health channel.
            baseUrl: {
                title: 'API base URL',
                description: 'Use OpenAI’s endpoint or another compatible service.'
            },
            model: {
                title: 'Model',
                description: 'Choose a listed model or enter its identifier.',
                placeholder: 'Select or enter model name',
                ariaLabel: 'AI model'
            },
            refreshModels: {
                ariaLabel: 'Refresh provider models',
                tooltip: 'Fetch models from server'
            }
        },
        behavior: {
            heading: 'Assistant behavior',
            description: 'Customize the instruction sent with every conversation.',
            systemMessage: {
                title: 'System message',
                description: 'Clear the message to restore the server default.',
                details: 'The system message guides the assistant’s tone and behavior.',
                placeholder: 'Loading system message...',
                ariaLabel: 'AI system message'
            }
        },
        test: {
            success: 'Connection successful!',
            failure: 'Connection failed. Check URL and API key.'
        },
        testConnection: 'Test Connection',
        saveSettings: 'Save AI Settings'
    },
    immich: {
        availability: {
            heading: 'Photo integration',
            description: 'Control whether Immich photos appear on your timeline.',
            enabled: {
                title: 'Enable Immich',
                description: 'Sync timeline photos from your Immich server.',
                ariaLabel: 'Enable Immich integration'
            }
        },
        connection: {
            heading: 'Connection',
            description: 'Provide the server address and credentials used to access Immich.',
            serverUrl: {
                title: 'Server URL',
                description: 'Enter the full address of your Immich server.',
                ariaLabel: 'Immich server URL'
            },
            apiKey: {
                title: 'API key',
                description: 'Create an API key in your Immich server settings.',
                placeholder: 'Enter your Immich API key',
                placeholderConfigured: 'API key is set (enter new key to replace)',
                ariaLabel: 'Immich API key',
                configuredNote: 'API key is configured. Leave empty to keep it.'
            }
        },
        // Backend status code -> sentence. The codes are the keys the store returns and are never
        // translated; see the matching table in ImmichTab.vue.
        messages: {
            connected: 'Successfully connected to Immich server. ',
            userNotFound: 'The configured Immich user could not be found',
            apiKeyRequired: 'An Immich API key is required',
            authenticationFailed: 'Immich rejected the API key',
            serverNotFound: 'The Immich server could not be found',
            connectionTimeout: 'The Immich connection timed out',
            connectionFailed: 'Failed to connect to the Immich server',
            testFailed: 'Failed to test connection',
            testError: 'Connection test failed',
            unexpected: 'An unexpected error occurred',
            assetsAvailable: '{count} asset available. | {count} assets available.'
        },
        errors: {
            serverUrlRequired: 'Server URL is required when integration is enabled',
            serverUrlInvalid: 'Please enter a valid URL (e.g., https://photos.example.com)',
            apiKeyRequired: 'API Key is required when integration is enabled'
        },
        testConnection: 'Test Connection',
        reset: 'Reset',
        saveSettings: 'Save Settings'
    },
    memos: {
        availability: {
            heading: 'Notes integration',
            description: 'Control whether timestamped Memos notes appear on your timeline.',
            enabled: {
                title: 'Enable Memos',
                description: 'Fetch timeline notes from your Memos server.',
                ariaLabel: 'Enable Memos integration'
            }
        },
        connection: {
            heading: 'Connection',
            description: 'Provide the server address and credentials used to access Memos.',
            serverUrl: {
                title: 'Server URL',
                description: 'Enter the full address of your Memos server.',
                ariaLabel: 'Memos server URL'
            },
            apiKey: {
                title: 'API key',
                description: 'Create an API token in your Memos settings.',
                placeholder: 'Enter your Memos API key',
                placeholderConfigured: 'API key is set (enter new key to replace)',
                ariaLabel: 'Memos API key',
                configuredNote: 'API key is configured. Leave empty to keep it.'
            }
        },
        defaults: {
            heading: 'Timeline defaults',
            description: 'Choose how notes created from GeoPulse are stored in Memos.',
            destination: {
                title: 'Default save destination',
                description: 'Choose where new notes are saved by default.',
                ariaLabel: 'Default save destination',
                // Labels only: the Select's optionValue keeps the backend enum strings.
                options: {
                    geopulse: 'GeoPulse',
                    memos: 'Memos'
                }
            },
            visibility: {
                title: 'Default visibility',
                description: 'Choose the initial Memos visibility for new notes.',
                ariaLabel: 'Default Memos visibility',
                options: {
                    private: 'Private',
                    protected: 'Protected',
                    public: 'Public'
                }
            }
        },
        filtering: {
            heading: 'Filtering & performance',
            description: 'Control cached searches and which tagged notes appear on the timeline.',
            searchCache: {
                title: 'Search cache',
                description: 'Reuse recent searches for faster timeline note loading.',
                ariaLabel: 'Enable Memos search cache'
            },
            includeTags: {
                title: 'Include tags',
                description: 'Only load notes containing at least one of these tags.',
                placeholder: 'Add a tag and press Enter'
            },
            excludeTags: {
                title: 'Exclude tags',
                description: 'Hide notes containing any of these tags.',
                placeholder: 'Add a tag and press Enter'
            }
        },
        // Backend status code -> sentence. The codes are the keys the store returns and are never
        // translated; see the matching table in MemosTab.vue.
        messages: {
            connected: 'Successfully connected to Memos server',
            userNotFound: 'User not found',
            apiKeyRequired: 'API key is required',
            connectionFailed: 'Connection failed',
            testError: 'Connection test failed',
            memoCount: 'Server returned {count} memo | Server returned {count} memos'
        },
        errors: {
            serverUrlRequired: 'Server URL is required when integration is enabled',
            serverUrlInvalid: 'Please enter a valid URL',
            apiKeyRequired: 'API key is required when integration is enabled'
        },
        testConnection: 'Test Connection',
        reset: 'Reset',
        saveSettings: 'Save Settings'
    }
}

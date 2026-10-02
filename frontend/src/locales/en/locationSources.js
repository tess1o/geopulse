/**
 * Location data sources: the sources list page, the add/edit dialog, the quick-setup guide, and the
 * per-provider setup instructions card.
 *
 * EN values are copied verbatim from the literals they replace: the test suite is pinned to English
 * and asserts exact copy, so these files are a snapshot rather than a chance to reword. Provider
 * brand names (OwnTracks, GPSLogger, Overland, Traccar, Dawarich, Home Assistant, Colota) are never
 * translated.
 */
export default {
    meta: {
        descriptionOwntracks: 'Open-source location tracking with HTTP or MQTT connections',
        descriptionGpslogger: 'Android GPSLogger app via HTTP + Basic Auth (OwnTracks-compatible payload)',
        descriptionOverland: 'Simple HTTP endpoint with token-based authentication',
        descriptionTraccar: 'Traccar Position Forwarding (JSON) with Bearer token authentication',
        descriptionDawarich: 'Privacy-focused location tracking with API key authentication',
        descriptionHomeAssistant: 'Integrate with Home Assistant automation for automatic location tracking',
        descriptionColota: 'Privacy-focused GPS tracker with batch sync and smart tracking',
        noUsername: 'No username',
        noToken: 'No token',
        noApiKey: 'No API key',
        tokenPrefix: 'Token: {token}...',
        apiKeyPrefix: 'API Key: {token}...',
        devicePrefix: 'Device: {deviceId}',
        allDevices: 'All devices',
        unknownType: 'Unknown type: {type}'
    },
    page: {
        title: 'Location Data Sources',
        description: 'Configure how GeoPulse receives your location data from different tracking apps. Set up OwnTracks, GPSLogger, Overland, Traccar, Dawarich, Home Assistant, or Colota to automatically sync your location history.',
        addNewSource: 'Add New Source',
        tabOwnTracksHttpShort: 'OT-HTTP',
        tabOwnTracksHttp: 'OwnTracks (HTTP)',
        tabOwnTracksMqttShort: 'OT-MQTT',
        tabOwnTracksMqtt: 'OwnTracks (MQTT)',
        tabOwnTracks: 'OwnTracks',
        tabOverland: 'Overland',
        tabTraccar: 'Traccar',
        tabGpsLogger: 'GPSLogger',
        tabDawarich: 'Dawarich',
        tabHomeAssistantShort: 'HA',
        tabHomeAssistant: 'Home Assistant',
        tabColota: 'Colota',
        confirmDeleteMessage: 'Are you sure you want to delete this {name} source?',
        confirmDeleteHeader: 'Confirm Delete',
        cancel: 'Cancel',
        delete: 'Delete',
        toasts: {
            sourceUpdatedSummary: 'Source Updated',
            sourceUpdatedDetail: 'Location source has been updated successfully',
            sourceAddedSummary: 'Source Added',
            sourceAddedDetail: 'Location source has been added successfully',
            updateFailedSummary: 'Update Failed',
            addFailedSummary: 'Add Failed',
            saveFailedFallback: 'Failed to save location source',
            statusUpdatedSummary: 'Status Updated',
            statusUpdatedDetailEnabled: 'Source enabled successfully',
            statusUpdatedDetailDisabled: 'Source disabled successfully',
            statusUpdateFailedSummary: 'Status Update Failed',
            statusUpdateFailedFallback: 'Failed to update source status',
            sourceDeletedSummary: 'Source Deleted',
            sourceDeletedDetail: 'Location source has been deleted successfully',
            deleteFailedSummary: 'Delete Failed',
            deleteFailedFallback: 'Failed to delete location source',
            copiedSummary: 'Copied',
            copiedDetail: 'Text copied to clipboard',
            copyFailedSummary: 'Copy Failed',
            copyFailedDetail: 'Failed to copy to clipboard',
            loadingFailedSummary: 'Loading Failed',
            loadingFailedDetail: 'Failed to load location sources'
        }
    },
    quickSetupGuide: {
        title: 'Quick Setup Guide',
        setupButton: 'Setup {name}'
    },
    list: {
        configuredSources: 'Configured Sources',
        active: 'Active',
        inactive: 'Inactive',
        encrypted: 'Encrypted',
        enabled: 'Enabled',
        disabled: 'Disabled',
        instructions: 'Instructions',
        edit: 'Edit'
    },
    dialog: {
        editHeader: 'Edit Location Source',
        addHeader: 'Add Location Source',
        stepChooseSource: 'Choose Source',
        stepConfigure: 'Configure',
        chooseSourceType: 'Choose Source Type',
        chooseSourceHint: 'Pick the source first. Configuration fields appear in the next step.',
        source: 'Source',
        selectedSource: 'Selected Source',
        changeSource: 'Change Source',
        sourceTypeCannotChange: 'Source type cannot be changed while editing. Create a new source to switch type.',
        connectionType: 'Connection Type',
        connectionTypeHttpName: 'HTTP',
        connectionTypeHttpDescription: 'Standard HTTP endpoint',
        connectionTypeMqttName: 'MQTT',
        connectionTypeMqttDescription: 'MQTT broker connection',
        gpsLoggerHttpOnlyNote: 'GPSLogger uses HTTP only and sends an OwnTracks-compatible payload.',
        username: 'Username',
        usernamePlaceholder: 'Enter username',
        password: 'Password',
        passwordPlaceholderEdit: 'Enter new password (leave empty to keep current)',
        passwordPlaceholderAdd: 'Enter password',
        payloadEncryptionSecret: 'Payload Encryption Secret',
        payloadEncryptionSecretPlaceholderEdit: 'Enter new secret (leave empty to keep current)',
        payloadEncryptionSecretPlaceholderAdd: 'Optional OwnTracks encryptionKey',
        payloadEncryptionSecretHint: 'Optional. Must match OwnTracks encryptionKey and be 32 UTF-8 bytes or fewer.',
        clearPayloadEncryptionSecret: 'Clear payload encryption secret',
        accessToken: 'Access Token',
        accessTokenPlaceholder: 'Enter access token',
        forwardingToken: 'Forwarding Token',
        forwardingTokenPlaceholder: 'Enter forwarding token',
        deviceUniqueId: 'Device Unique ID (optional)',
        deviceUniqueIdPlaceholder: 'Leave empty to accept all devices',
        deviceUniqueIdHint: 'Matches Traccar {code}. Use one source per device when sharing a token.',
        apiKeyLabel: 'API Key',
        apiKeyPlaceholder: 'Enter API key',
        token: 'Token',
        tokenPlaceholder: 'Enter token',
        cancel: 'Cancel',
        continue: 'Continue',
        back: 'Back',
        saveChanges: 'Save Changes',
        addSource: 'Add Source',
        validation: {
            usernameRequired: 'Username is required',
            passwordRequired: 'Password is required',
            secretTooLong: 'Secret must be 32 UTF-8 bytes or fewer',
            accessTokenRequired: 'Access token is required',
            forwardingTokenRequired: 'Forwarding token is required',
            apiKeyRequired: 'API key is required',
            tokenRequired: 'Token is required'
        }
    },
    instructions: {
        title: 'Setup Instructions',
        serverUrl: 'Server URL',
        connectionMode: 'Connection Mode',
        http: 'HTTP',
        authentication: 'Authentication',
        basicAuthentication: 'Basic Authentication',
        deviceUniqueIdShort: 'Device Unique ID',
        useUsername: 'Use your configured username',
        usePassword: 'Use your configured password',
        payloadEncryption: 'Payload Encryption',
        payloadEncryptionHint: 'Optional: set OwnTracks {code} to the payload encryption secret configured on this source.',
        owntracksHttp: {
            title: 'OwnTracks Configuration (HTTP)'
        },
        owntracksMqtt: {
            title: 'OwnTracks Configuration (MQTT)',
            connectionTypeStep: 'Connection Type',
            selectMqtt: 'Select {mqtt} in OwnTracks connection settings',
            mqttDisabledHint: 'MQTT integration is currently disabled on this server. Ask your admin to enable it.',
            brokerHost: 'MQTT Broker Host',
            brokerHostHint: 'Configured on GeoPulse server by admin',
            brokerPort: 'MQTT Port',
            notConfigured: 'Not configured',
            securitySettings: 'Security Settings',
            tls: 'TLS:',
            tlsEnabled: 'Enabled',
            tlsDisabled: 'Disabled',
            tlsHintEnabled: 'Enable TLS/SSL in OwnTracks connection settings',
            tlsHintDisabled: 'Leave TLS/SSL settings unchecked'
        },
        overland: {
            title: 'Overland Configuration',
            receiverEndpointUrl: 'Receiver Endpoint URL',
            accessToken: 'Access Token',
            yourConfiguredToken: 'Your configured token'
        },
        traccar: {
            title: 'Traccar Position Forwarding (JSON)',
            updateXmlTitle: 'Update {file} Position Forwarding settings',
            updateXmlValue: 'Set {forwardUrl}, {forwardType}, and {forwardHeader} (Bearer token) in {file}:',
            officialDocs: 'Official docs:',
            sharedTokenTitle: 'Shared Traccar token setup',
            sharedTokenValue: 'If multiple people share one Traccar server/token, create one GeoPulse Traccar source per device and set {deviceUniqueId} to that device\'s {uniqueId}.'
        },
        gpslogger: {
            title: 'GPSLogger Configuration',
            enableCustomUrlTitle: 'Enable Custom URL Logging',
            enableCustomUrlValue: 'In GPSLogger, enable {logToCustomUrl}.',
            logToCustomUrl: 'Log to custom URL',
            url: 'URL',
            httpMethod: 'HTTP Method',
            post: 'POST',
            httpBody: 'HTTP Body (JSON)',
            httpBodyHint: 'GeoPulse treats GPSLogger speed as m/s and converts it to km/h automatically.',
            headers: 'Headers',
            headersValue: 'Add this required header:',
            optionalDeviceIdHeader: 'Optional device ID header:',
            authenticationValue: 'Enable {basicAuth} and use the username/password from this source.'
        },
        dawarich: {
            title: 'Dawarich Configuration',
            apiKey: 'API Key',
            yourConfiguredApiKey: 'Your configured API Key'
        },
        homeAssistant: {
            title: 'Home Assistant Configuration',
            configYamlTitle: 'In configuration.yaml add the following:',
            replaceTitle: 'Replace:',
            replaceDeviceId: 'iphone_16 with your device_id (can be found in Home Assistant)',
            replaceToken: 'YOUR_CONFIGURED_TOKEN with the token you just created in GeoPulse',
            automationYamlTitle: 'In automations.yaml add the following:',
            restartTitle: 'Restart Home Assistant server to apply the changes.'
        },
        colota: {
            title: 'Colota Configuration',
            apiEndpoint: 'API Endpoint',
            authenticationValue: 'Uses {basicAuth} with the username and password configured in your source.',
            payloadFormat: 'Payload Format (JSON)',
            payloadFieldsHint: 'Fields: lat, lon, acc (meters), alt (meters), vel (m/s), batt (%), bs (battery status), tst (Unix timestamp), bear (bearing degrees).'
        }
    }
}

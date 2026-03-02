// @ts-check

/**
 * Creating a sidebar enables you to:
 - create an ordered group of docs
 - render a sidebar for each doc of that group
 - provide next/previous navigation

 The sidebars can be generated from the filesystem, or explicitly defined here.

 Create as many sidebars as you want.

 @type {import('@docusaurus/plugin-content-docs').SidebarsConfig}
 */
const sidebars = {
    docsSidebar: [
        {
            type: 'category',
            label: 'Start Here',
            items: [
                'getting-started/introduction',
                'getting-started/quick-start',
                'getting-started/architecture-overview',
            ],
        },
        {
            type: 'category',
            label: 'Install & Upgrade',
            items: [
                {
                    type: 'doc',
                    id: 'getting-started/deployment/docker-compose',
                    label: 'Docker Compose',
                },
                {
                    type: 'doc',
                    id: 'getting-started/deployment/kubernetes-helm',
                    label: 'Kubernetes Quick Install',
                },
                {
                    type: 'doc',
                    id: 'getting-started/deployment/helm-deployment',
                    label: 'Helm Values Reference',
                },
                {
                    type: 'doc',
                    id: 'getting-started/deployment/manual-installation',
                    label: 'Manual Installation (Advanced)',
                },
                {
                    type: 'doc',
                    id: 'system-administration/maintenance/updating',
                    label: 'Upgrading GeoPulse',
                },
            ],
        },
        {
            type: 'category',
            label: 'Connect Sources',
            items: [
                'user-guide/gps-sources/overview',
                'user-guide/gps-sources/owntracks',
                'user-guide/gps-sources/overland',
                'user-guide/gps-sources/home_assistant',
                'user-guide/gps-sources/gps_logger',
                'user-guide/gps-sources/dawarich',
                'user-guide/gps-sources/data-mirroring',
            ],
        },
        {
            type: 'category',
            label: 'Use GeoPulse',
            items: [
                {
                    type: 'doc',
                    id: 'user-guide/interacting-with-data/import-export',
                    label: 'Import/Export Data',
                },
                {
                    type: 'category',
                    label: 'Timeline',
                    items: [
                        {
                            type: 'doc',
                            id: 'user-guide/core-features/timeline',
                            label: 'Timeline Overview',
                        },
                        'user-guide/timeline/stay_detection',
                        'user-guide/timeline/trip_detection',
                        'user-guide/timeline/travel_classification',
                        {
                            type: 'doc',
                            id: 'user-guide/timeline/data_gaps',
                            label: 'Data Gaps & Inference',
                        },
                        {
                            type: 'doc',
                            id: 'user-guide/core-features/period-tags',
                            label: 'Period Tags',
                        },
                    ],
                },
                {
                    type: 'category',
                    label: 'Views & Insights',
                    items: [
                        {
                            type: 'doc',
                            id: 'user-guide/core-features/dashboard',
                            label: 'Dashboard Overview',
                        },
                        'user-guide/core-features/journey-insights',
                        'user-guide/core-features/rewind',
                    ],
                },
                {
                    type: 'doc',
                    id: 'user-guide/core-features/managing-places',
                    label: 'Managing Places',
                },
                {
                    type: 'doc',
                    id: 'user-guide/using-geopulse/ai-assistant',
                    label: 'AI Assistant',
                },
                {
                    type: 'category',
                    label: 'Personal Settings',
                    items: [
                        {
                            type: 'doc',
                            id: 'user-guide/personalization/profile-settings',
                            label: 'Profile Settings',
                        },
                        'user-guide/personalization/ai-assistant-settings',
                        'user-guide/personalization/custom-map-tiles',
                        'user-guide/personalization/immich-integration',
                        'user-guide/personalization/measurement-units',
                    ],
                },
            ],
        },
        {
            type: 'category',
            label: 'Admin & Operations',
            items: [
                'system-administration/initial-setup',
                {
                    type: 'category',
                    label: 'Access & Authentication',
                    items: [
                        'system-administration/configuration/authentication',
                        'system-administration/configuration/user-registration',
                        'system-administration/configuration/login-control',
                        'system-administration/configuration/oidc-sso',
                    ],
                },
                {
                    type: 'category',
                    label: 'System Configuration',
                    items: [
                        'system-administration/configuration/admin-panel',
                        'system-administration/configuration/timeline-global-config',
                        'system-administration/configuration/reverse-geocoding',
                        'system-administration/configuration/import',
                        'system-administration/configuration/gps-data-filtering',
                        'system-administration/configuration/owntracks-additional-config',
                        'system-administration/configuration/location-sharing',
                        'system-administration/configuration/frontend',
                        {
                            type: 'doc',
                            id: 'system-administration/configuration/ai-assistant',
                            label: 'AI Assistant (Admin)',
                        },
                    ],
                },
                {
                    type: 'category',
                    label: 'Monitoring',
                    items: [
                        'system-administration/monitoring/prometheus',
                        'system-administration/monitoring/grafana',
                    ],
                },
                {
                    type: 'doc',
                    id: 'system-administration/maintenance/backup-restore',
                    label: 'Backup & Restore',
                },
            ],
        },
        {
            type: 'category',
            label: 'Reference & Troubleshooting',
            items: [
                {
                    type: 'category',
                    label: 'Documentation In Progress',
                    items: [
                        {
                            type: 'doc',
                            id: 'user-guide/social-and-sharing/friends',
                            label: 'Friends (Coming Soon)',
                        },
                        {
                            type: 'doc',
                            id: 'user-guide/social-and-sharing/public-links',
                            label: 'Public Links (Coming Soon)',
                        },
                    ],
                },
            ],
        },
        {
            type: 'doc',
            id: 'faq',
            label: 'FAQ',
        },
    ],
};

export default sidebars;

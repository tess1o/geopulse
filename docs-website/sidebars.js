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
            label: 'Getting Started',
            items: [
                'getting-started/introduction',
                'getting-started/architecture-overview',
                {
                    type: 'category',
                    label: 'Deployment',
                    items: [
                        'getting-started/deployment/docker-compose',
                        'getting-started/deployment/kubernetes-helm',
                        'getting-started/deployment/helm',
                    ],
                },
            ],
        },
        {
            type: 'category',
            label: 'Configuration & Administration',
            items: [
                'system-administration/initial-setup',
                {
                    type: 'category',
                    label: 'Configuration Reference',
                    items: [
                        'system-administration/configuration/authentication',
                        'system-administration/configuration/user-registration',
                        'system-administration/configuration/oidc-sso',
                        'system-administration/configuration/reverse-geocoding',
                        'system-administration/configuration/ai-assistant',
                        'system-administration/configuration/timeline-global-config',
                        'system-administration/configuration/location-sharing',
                        'system-administration/configuration/frontend',
                        'system-administration/configuration/gps-data-filtering',
                        'system-administration/configuration/owntracks-additional-config',
                    ],
                },
                {
                    type: 'category',
                    label: 'Monitoring',
                    items: [
                        'system-administration/monitoring/prometheus',
                    ],
                },
                {
                    type: 'category',
                    label: 'Maintenance',
                    items: [
                        'system-administration/maintenance/updating',
                        'system-administration/maintenance/backup-restore',
                    ],
                },
            ],
        },
        {
            type: 'category',
            label: 'User Guide',
            items: [
                {
                    type: 'category',
                    label: 'Connecting GPS Sources',
                    items: [
                        'user-guide/gps-sources/overview',
                        'user-guide/gps-sources/owntracks',
                        'user-guide/gps-sources/overland',
                        'user-guide/gps-sources/home_assistant',
                        'user-guide/gps-sources/gps_logger',
                        'user-guide/gps-sources/dawarich',
                    ],
                },
                {
                    type: 'category',
                    label: 'Core Features',
                    items: [
                        'user-guide/core-features/timeline',
                        'user-guide/core-features/dashboard',
                        'user-guide/core-features/journey-insights',
                        'user-guide/core-features/rewind',
                        'user-guide/core-features/managing-places',
                    ],
                },
                {
                    type: 'category',
                    label: 'Timeline Deep Dive',
                    items: [
                        'user-guide/timeline/stay_detection',
                        'user-guide/timeline/trip_detection',
                        'user-guide/timeline/travel_classification',
                        'user-guide/timeline/data_gaps',
                    ],
                },
                {
                    type: 'category',
                    label: 'Interacting with Your Data',
                    items: [
                        'user-guide/interacting-with-data/import-export',
                        'user-guide/interacting-with-data/ai-assistant',
                    ],
                },
                {
                    type: 'category',
                    label: 'Social & Sharing',
                    items: [
                        'user-guide/social-and-sharing/friends',
                        'user-guide/social-and-sharing/public-links',
                    ],
                },
                {
                    type: 'category',
                    label: 'Personalization & Settings',
                    items: [
                        'user-guide/personalization/profile-settings',
                        'user-guide/personalization/custom-map-tiles',
                        'user-guide/personalization/immich-integration',
                        'user-guide/personalization/measurement-units',
                        'user-guide/personalization/ai-assistant-settings',
                    ],
                },
            ],
        },
        {
            type: 'category',
            label: 'Developer Guide',
            items: [
                'developer-guide/contributing',
                'developer-guide/api-reference',
                'developer-guide/roadmap',
            ],
        },
    ],
};

export default sidebars;

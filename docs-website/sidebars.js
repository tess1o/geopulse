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
                        'getting-started/deployment/helm-deployment',
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
                        'system-administration/configuration/admin-panel',
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
                        {
                            type: 'doc',
                            id: 'system-administration/maintenance/updating',
                            label: 'Updating GeoPulse',
                        },
                        {
                            type: 'doc',
                            id: 'system-administration/maintenance/backup-restore',
                            label: 'Backup & Restore',
                        },
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
                    label: 'GPS Data Sources',
                    items: [
                        'user-guide/gps-sources/overview',
                        'user-guide/gps-sources/owntracks',
                        'user-guide/gps-sources/overland',
                        'user-guide/gps-sources/home_assistant',
                        'user-guide/gps-sources/gps_logger',
                        'user-guide/gps-sources/dawarich',
                        'user-guide/gps-sources/data-mirroring',
                        {
                            type: 'doc',
                            id: 'user-guide/interacting-with-data/import-export',
                            label: 'Import/Export (Coming Soon)',
                        },
                    ],
                },
                {
                    type: 'category',
                    label: 'Using GeoPulse',
                    items: [
                        'user-guide/core-features/dashboard',
                        'user-guide/core-features/timeline',
                        'user-guide/timeline/stay_detection',
                        'user-guide/timeline/trip_detection',
                        'user-guide/timeline/travel_classification',
                        'user-guide/timeline/data_gaps',
                        'user-guide/core-features/rewind',
                        'user-guide/core-features/journey-insights',
                        'user-guide/core-features/managing-places',
                        'user-guide/using-geopulse/ai-assistant',
                    ],
                },
                {
                    type: 'category',
                    label: 'Sharing & Collaboration',
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
                {
                    type: 'category',
                    label: 'Settings & Personalization',
                    items: [
                        {
                            type: 'doc',
                            id: 'user-guide/personalization/profile-settings',
                            label: 'Profile Settings (Coming Soon)',
                        },
                        'user-guide/personalization/ai-assistant-settings',
                        'user-guide/personalization/custom-map-tiles',
                        'user-guide/personalization/immich-integration',
                        'user-guide/personalization/measurement-units',
                    ],
                },
            ],
        },
    ],
};

export default sidebars;

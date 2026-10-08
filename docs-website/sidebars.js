/**
 * Creating a sidebar enables you to:
 - create an ordered group of docs
 - render a sidebar for each doc of that group
 - provide next/previous navigation

 The sidebars can be generated from the filesystem, or explicitly defined here.

 Create as many sidebars as you want.

 @type {import('@docusaurus/plugin-content-docs').SidebarsConfig}
 */
const path = require('path');
const fs = require('fs');

const apiReferenceDir = path.join(process.cwd(), 'docs', 'api', 'reference');
const hasGeneratedApiReference = fs.existsSync(path.join(apiReferenceDir, 'sidebar.ts'))
    && fs.readdirSync(apiReferenceDir).some((fileName) => fileName.endsWith('.mdx'));

let generatedApiSidebar = [];
if (hasGeneratedApiReference) {
    const sidebarModule = require('./docs/api/reference/sidebar.ts');
    generatedApiSidebar = sidebarModule.default || sidebarModule;
}

// The generated sidebar is grouped by the x-tagGroups of the OpenAPI document (see ApiTagCatalog in the backend).
// Only its categories are used; the generated info page is replaced by the hand-written API overview. In tagGroup
// mode the plugin also prepends that info page to every group, so it is removed from the group items as well.
const GENERATED_INFO_DOC_ID = 'api/reference/geopulse-api';
const generatedApiCategories = generatedApiSidebar
    .filter((item) => item.type === 'category')
    .map((group) => ({
        ...group,
        items: group.items.filter((item) => !(item.type === 'doc' && item.id === GENERATED_INFO_DOC_ID)),
    }));

const restApiItems = [
    {
        type: 'doc',
        id: 'api/intro',
        label: 'API Overview',
    },
    {
        type: 'doc',
        id: 'api/api-tokens',
        label: 'API Tokens',
    },
    {
        type: 'doc',
        id: 'api/examples',
        label: 'API Examples',
    },
    {
        type: 'doc',
        id: 'api/mcp',
        label: 'MCP Server',
    },
    ...generatedApiCategories,
];

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
            link: {
                type: 'doc',
                id: 'getting-started/deployment/overview',
            },
            items: [
                {
                    type: 'doc',
                    id: 'getting-started/deployment/docker-compose',
                    label: 'Docker Compose',
                },
                {
                    type: 'doc',
                    id: 'getting-started/deployment/unraid',
                    label: 'Unraid',
                },
                {
                    type: 'doc',
                    id: 'getting-started/deployment/proxmox-lxc',
                    label: 'Proxmox VE LXC',
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
                    id: 'getting-started/deployment/environment-variables',
                    label: 'Environment Variables Reference',
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
                {
                    type: 'doc',
                    id: 'system-administration/maintenance/postgresql-18-upgrade',
                    label: 'Upgrading to PostgreSQL 18',
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
                'user-guide/gps-sources/traccar',
                'user-guide/gps-sources/home_assistant',
                'user-guide/gps-sources/gps_logger',
                'user-guide/gps-sources/dawarich',
                'user-guide/gps-sources/colota',
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
                            id: 'user-guide/timeline/manual-trip-split',
                            label: 'Split a Trip with a Stay',
                        },
                        {
                            type: 'doc',
                            id: 'user-guide/timeline/map-matching',
                            label: 'Map Matching',
                        },
                        'user-guide/timeline/boat-setup',
                        {
                            type: 'doc',
                            id: 'user-guide/timeline/data_gaps',
                            label: 'Data Gaps & Inference',
                        },
                        {
                            type: 'doc',
                            id: 'user-guide/core-features/timeline-labels',
                            label: 'Timeline Labels',
                        },
                        {
                            type: 'doc',
                            id: 'user-guide/core-features/trip-plans',
                            label: 'Trip Plans',
                        },
                        {
                            type: 'doc',
                            id: 'user-guide/core-features/trip-workspace',
                            label: 'Trip Workspace',
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
                    id: 'user-guide/core-features/geofences',
                    label: 'Geofences',
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
                        {
                            type: 'category',
                            label: 'OIDC Authentication',
                            link: {
                                type: 'doc',
                                id: 'system-administration/configuration/oidc-sso',
                            },
                            items: [
                                'system-administration/configuration/authelia-oidc',
                            ],
                        },
                    ],
                },
                {
                    type: 'category',
                    label: 'System Configuration',
                    items: [
                        'system-administration/configuration/admin-panel',
                        'system-administration/configuration/timeline-global-config',
                        {
                            type: 'doc',
                            id: 'system-administration/configuration/valhalla-map-matching',
                            label: 'Valhalla Map Matching',
                        },
                        'system-administration/configuration/panoramax',
                        'system-administration/configuration/reverse-geocoding',
                        'system-administration/configuration/weather',
                        'system-administration/configuration/import',
                        'system-administration/configuration/gps-data-filtering',
                        'system-administration/configuration/owntracks-additional-config',
                        'system-administration/configuration/owntracks-mqtt-external-tls',
                        'system-administration/configuration/location-sharing',
                        'system-administration/configuration/apprise-notifications',
                        'system-administration/configuration/frontend',
                        {
                            type: 'doc',
                            id: 'system-administration/configuration/ai-assistant',
                            label: 'AI Assistant (Admin)',
                        },
                    ],
                },
                {
                    type: 'doc',
                    id: 'system-administration/maintenance/timeline-regeneration-campaigns',
                    label: 'Timeline Regeneration',
                },
                {
                    type: 'category',
                    label: 'Monitoring',
                    items: [
                        'system-administration/monitoring/prometheus',
                        'system-administration/monitoring/grafana',
                        'system-administration/monitoring/logging',
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
            label: 'REST API',
            items: restApiItems,
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

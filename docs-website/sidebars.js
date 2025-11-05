// @ts-check

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

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
        'intro',
        {
            type: 'category',
            label: 'Deployment',
            items: ['deployment/docker-deployment', 'deployment/kubernetes-deployment', 'deployment/helm'],
        },
        {
            type: 'category',
            label: 'System Configuration',
            items: [
                'system-configuration/overview',
                'system-configuration/authentication-config',
                'system-configuration/user-registration',
                'system-configuration/oidc-config',
                'system-configuration/reverse-geocoding-config',
                'system-configuration/frontend-nginx-config',
                'system-configuration/sharing-config',
                'system-configuration/ai-config'],
        },
        {
            type: 'category',
            label: 'User Settings',
            items: [
                'user-settings/custom-map-tiles',
                'user-settings/measure-unit',
                'user-settings/ai-settings',
                'user-settings/immich-settings',
            ],
        },
        {
            type: 'category',
            label: 'Favorites and Reverse Geocoding',
            items: [
                'favorites-and-geocoding/favorite-locations',
                'favorites-and-geocoding/reverse-geocoding'
            ],
        },
    ],
};

export default sidebars;

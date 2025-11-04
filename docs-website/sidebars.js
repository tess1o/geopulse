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
        {
            type: 'category',
            label: 'Deployment',
            items: ['docker-deployment', 'kubernetes-deployment'],
        },
        {
            type: 'category',
            label: 'System Configuration',
            items: ['authentification-config', 'user-registration', 'oidc-config', 'reverse-geocoding-config', 'frontend-nginx-config', 'sharing-config', 'ai-config'],
        },
        {
            type: 'category',
            label: 'Personal configuration',
            items: ['custom-map-tiles'],
        },
    ],
};

export default sidebars;

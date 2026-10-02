export default {
    page: {
        title: 'Help & Support',
        description: 'Get help, access documentation, and report issues',
        issues: {
            title: 'Having Issues?',
            description: "If you're experiencing problems with timeline generation, trip classification, or other features, you can export anonymized debug data to help us troubleshoot.",
            debugExport: {
                title: 'Export Debug Data',
                description: 'Export your GPS data with privacy-preserving coordinate shifts. All coordinates are anonymized by shifting them by a random offset, making it safe to share for troubleshooting.',
                bullets: [
                    'Coordinates are shifted to protect your privacy',
                    'Location names are anonymized',
                    'Includes timeline configuration for analysis'
                ],
                demoDisabledText: 'Debug data export is disabled in demo mode to protect the shared demo dataset.',
                demoDisabledTooltip: 'Debug data export is disabled in demo mode',
                button: 'Export Debug Data'
            },
            reportIssue: {
                title: 'Report an Issue',
                description: 'Found a bug or have a feature request? Report it on GitHub.',
                stepsTitle: 'How to report effectively:',
                steps: [
                    'Export debug data using the button above',
                    'Create a new issue on GitHub',
                    'Attach the debug export ZIP file',
                    'Describe the problem and expected behavior'
                ],
                button: 'Create GitHub Issue'
            },
            demoModeToast: 'Debug data export is disabled in demo mode.'
        },
        docs: {
            title: 'Documentation',
            description: 'Comprehensive guides and documentation for all GeoPulse features',
            gettingStarted: {
                title: 'Getting Started',
                description: 'Quick start guide and initial setup'
            },
            timelineFeatures: {
                title: 'Timeline Features',
                description: 'How timeline generation and processing works'
            },
            tripClassification: {
                title: 'Trip Classification',
                description: 'Understanding how trips are classified'
            },
            locationSources: {
                title: 'Location Sources',
                description: 'Supported GPS tracking sources and setup'
            },
            faq: {
                title: 'FAQ',
                description: 'Frequently asked questions'
            },
            fullDocumentation: {
                title: 'Full Documentation',
                description: 'Complete documentation with search'
            }
        },
        about: {
            title: 'About GeoPulse',
            description: 'Application information and credits',
            version: 'Version',
            loading: 'Loading...',
            license: 'License',
            licenseLinkText: 'BSL 1.1 License',
            repository: 'Repository',
            repositoryLinkText: 'GitHub',
            author: 'Author',
            authorLinkText: 'tess1o',
            builtWith: 'Built With'
        }
    }
}

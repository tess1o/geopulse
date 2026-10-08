import clsx from 'clsx';
import {useEffect, useState} from 'react';
import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';
import Screenshot from '@site/src/components/Screenshot';
import styles from './index.module.css';

const DEFAULT_GITHUB_STATS = {stars: 1394, forks: 57};
const githubCountFormatter = new Intl.NumberFormat('en-US', {notation: 'compact', maximumFractionDigits: 1});
const githubForkFormatter = new Intl.NumberFormat('en-US');
const AUTO_ROTATE_MS = 6000;

function githubStat(value, fallback) {
    return Number.isSafeInteger(value) && value >= 0 ? value : fallback;
}

// Screenshots live in static/img/screenshots/{light,dark} (see src/components/Screenshot). A feature without a shot
// renders as text only.
const shots = {
    hero: {name: 'timeline', width: 1600, height: 735},
    timeline: {name: 'timeline_trip_selected', width: 1600, height: 737},
    insights: {name: 'dashboard-card', width: 1600, height: 1000},
    places: {name: 'city_page-card', width: 1600, height: 1000},
    rewind: {name: 'rewind-card', width: 1600, height: 1000},
    tripPlans: {name: 'trip_workspace', width: 1600, height: 791},
    sources: {name: 'location_sources', width: 1600, height: 828},
    mobile: {name: 'mobile_timeline', width: 752, height: 1280, portrait: true},
};

const IMPORT_DOCS = '/docs/user-guide/interacting-with-data/import-export';

const sourceColumns = [
    {
        title: 'Live tracking',
        icon: 'send',
        items: [
            {name: 'OwnTracks', detail: 'HTTP · MQTT', link: '/docs/user-guide/gps-sources/owntracks'},
            {name: 'Overland', detail: 'HTTP', link: '/docs/user-guide/gps-sources/overland'},
            {name: 'GPSLogger', detail: 'HTTP', link: '/docs/user-guide/gps-sources/gps_logger'},
            {name: 'Home Assistant', detail: 'HTTP', link: '/docs/user-guide/gps-sources/home_assistant'},
            {name: 'Traccar', detail: 'Position forwarding', link: '/docs/user-guide/gps-sources/traccar'},
            {name: 'Dawarich', detail: 'HTTP', link: '/docs/user-guide/gps-sources/dawarich'},
            {name: 'Colota', detail: 'HTTP', link: '/docs/user-guide/gps-sources/colota'},
        ],
    },
    {
        title: 'Import',
        icon: 'download',
        items: [
            {name: 'Google Timeline', detail: 'Takeout · phone export', link: IMPORT_DOCS},
            {name: 'GPX', detail: '.gpx · .zip', link: IMPORT_DOCS},
            {name: 'GeoJSON', detail: 'Points · LineStrings', link: IMPORT_DOCS},
            {name: 'CSV', detail: 'Structured CSV', link: IMPORT_DOCS},
            {name: 'OwnTracks', detail: 'JSON export', link: IMPORT_DOCS},
            {name: 'GeoPulse', detail: 'Full backup .zip', link: IMPORT_DOCS},
        ],
    },
    {
        title: 'Integrations',
        icon: 'plug',
        items: [
            {name: 'Immich', detail: 'Photos on the map', link: '/docs/user-guide/personalization/immich-integration'},
            {name: 'Memos', detail: 'Notes on the timeline'},
            {name: 'Weather', detail: 'Open-Meteo · Pirate Weather', link: '/docs/system-administration/configuration/weather'},
            {name: 'Valhalla', detail: 'Map matching', link: '/docs/user-guide/timeline/map-matching'},
            {name: 'Panoramax', detail: 'Street imagery', link: '/docs/system-administration/configuration/panoramax'},
            {name: 'Apprise', detail: 'Geofence alerts', link: '/docs/system-administration/configuration/apprise-notifications'},
            {name: 'OIDC / SSO', detail: 'Sign-in', link: '/docs/system-administration/configuration/oidc-sso'},
        ],
    },
];

const walkthroughSteps = [
    {
        title: 'Points arrive',
        text: 'Your tracker app sends GPS points over HTTP or MQTT, or you import files. Per-source filtering drops inaccurate fixes before they reach the timeline.',
        linkLabel: 'Connect a source',
        link: '/docs/user-guide/gps-sources/overview',
        art: 'points',
    },
    {
        title: 'Stays, trips, and gaps appear',
        text: 'GeoPulse groups points into stays, trips with a travel mode, and data gaps. Detection thresholds can be tuned in Timeline Preferences.',
        linkLabel: 'How detection works',
        link: '/docs/user-guide/timeline/stay_detection',
        art: 'timeline',
    },
    {
        title: 'Correct and enrich',
        text: 'Split a missed stop, change a travel mode, and let map matching follow the roads. Photos, notes, and weather attach to the stays they belong to.',
        linkLabel: 'Correcting the timeline',
        link: '/docs/user-guide/timeline/manual-trip-split',
        art: 'enriched',
    },
];

// Shared geometry for the "How it works" illustrations (viewBox 0 0 320 160).
const ART_ROAD = 'M20 130 C70 130 90 70 150 70 S240 40 300 40';
const ART_ROUTE = 'M20 130 C70 130 90 70 150 70';
const ART_GAP = 'M150 70 C210 70 240 40 300 40';
const ART_TRIP = '22,128 38,126 50,121 57,119 65,111 74,105 88,97 98,88 107,83 118,76 132,75 150,70';
const ART_STAYS = [[22, 128], [150, 70], [300, 40]];
const ART_OUTLIERS = [[112, 142], [45, 70]];
// Points cluster at the three stays and follow the first trip; nothing arrives between the second and third stay,
// which is the data gap shown in step 2.
const ART_DOTS = [
    [18, 129], [24, 125], [26, 132], [39, 121], [48, 121], [52, 118], [62, 110], [71, 98], [85, 101], [91, 85],
    [107, 89], [118, 75], [135, 66], [147, 67], [153, 64], [150, 73], [155, 70], [293, 45], [299, 37], [304, 42],
    [297, 41],
];

const INSTALL_SCRIPT = `# 1. Download the configuration
mkdir geopulse && cd geopulse
curl -L -o .env \\
  https://raw.githubusercontent.com/tess1o/GeoPulse/main/.env.example
curl -L -o docker-compose.yml \\
  https://raw.githubusercontent.com/tess1o/GeoPulse/main/docker-compose.yml

# 2. Start GeoPulse
docker compose up -d

# 3. Open http://localhost:5555`;

const deployOptions = [
    {label: 'Docker Compose', link: '/docs/getting-started/deployment/docker-compose'},
    {label: 'Unraid', link: '/docs/getting-started/deployment/unraid'},
    {label: 'Proxmox LXC', link: '/docs/getting-started/deployment/proxmox-lxc'},
    {label: 'Kubernetes / Helm', link: '/docs/getting-started/deployment/kubernetes-helm'},
    {label: 'Manual install', link: '/docs/getting-started/deployment/manual-installation'},
];

const quickDocs = [
    {
        title: 'Quick Start',
        text: 'Finish first login and connect your first location source.',
        link: '/docs/getting-started/quick-start',
    },
    {
        title: 'Deployment',
        text: 'Choose Docker Compose, Unraid, Proxmox, Kubernetes, Helm, or manual install.',
        link: '/docs/getting-started/deployment',
    },
    {
        title: 'Administration',
        text: 'Authentication, registration, geocoding, weather, backups, and Prometheus/Grafana monitoring.',
        link: '/docs/system-administration/initial-setup',
    },
    {
        title: 'REST API, MCP & AI',
        text: 'API tokens, generated reference docs, the MCP server, and the AI Assistant.',
        link: '/docs/api/intro',
    },
];

const exploreFeatures = [
    {
        id: 'timeline',
        tabLabel: 'Timeline',
        title: 'Your Day as Stays, Trips, and Gaps',
        description: 'GeoPulse turns raw points into a readable timeline, then lets you replay the route and fix what GPS got wrong.',
        highlights: [
            'Automatic stay/trip/gap detection',
            'Configurable travel modes from walking and cycling to motorcycle, train, flight, and boat',
            'Split a missed stop into Trip → Stay → Trip; the correction survives regeneration',
            'Optional Valhalla Map Matching follows roads while raw GPS remains authoritative',
        ],
        link: '/docs/user-guide/core-features/timeline',
        shot: shots.timeline,
        accent: 'chipTeal',
        icon: 'calendar',
    },
    {
        id: 'insights',
        tabLabel: 'Insights',
        title: 'Dashboard & Journey Insights',
        description: 'See how you move: distance, places, travel modes, and milestones for any period.',
        highlights: [
            'Selected period + 7-day + 30-day overviews',
            'Top places, route analysis, and activity breakdowns',
            'Countries, cities, travel milestones, and badges in Journey Insights',
        ],
        link: '/docs/user-guide/core-features/journey-insights',
        shot: shots.insights,
        accent: 'chipPurple',
        icon: 'chart',
    },
    {
        id: 'places',
        tabLabel: 'Places',
        title: 'Places, Cities, and Coverage',
        description: 'Browse every place you have been, from a single café to whole countries.',
        highlights: [
            'Location Analytics lists places, cities, and countries with their visit history',
            'Coverage Explorer shows the streets and areas you have already explored',
            'Favorites and reverse geocoding keep place names readable',
        ],
        link: '/docs/user-guide/core-features/managing-places',
        shot: shots.places,
        accent: 'chipBlue',
        icon: 'map',
    },
    {
        id: 'rewind',
        tabLabel: 'Rewind',
        title: 'Rewind Your Months and Years',
        description: 'Look back at a month or a year as a digest of places, trips, and highlights.',
        highlights: [
            'Monthly and yearly views for any period with data',
            'Top places, distance, and activity trends at a glance',
            'An in-app notification tells you when a new Rewind is ready',
        ],
        link: '/docs/user-guide/core-features/rewind',
        shot: shots.rewind,
        accent: 'chipOrange',
        icon: 'clock',
    },
    {
        id: 'trip-plans',
        tabLabel: 'Trip Plans',
        title: 'Plan a Trip, Then Compare It With Reality',
        description: 'Plan stops before you leave and see what you actually visited when you are back.',
        highlights: [
            'Planned stops with a travel mode and routed legs between them',
            'Discover notable places nearby, with photos from Wikimedia Commons',
            'Plan vs Actual matches planned stops to timeline stays',
        ],
        link: '/docs/user-guide/core-features/trip-plans',
        shot: shots.tripPlans,
        accent: 'chipSky',
        icon: 'route',
    },
    {
        id: 'friends',
        tabLabel: 'Friends',
        title: 'Friends & Sharing Controls',
        description: 'Connect with friends and control exactly what you share.',
        highlights: [
            'Invite, accept, reject, and cancel friend requests',
            'Separate permissions for live location and timeline history',
            'Guest links with optional password and expiry, revocable at any time',
        ],
        link: '/docs/user-guide/social-and-sharing/friends',
        accent: 'chipRose',
        icon: 'users',
    },
    {
        id: 'geofences',
        tabLabel: 'Geofences',
        title: 'Geofence Rules & Events',
        description: 'Create enter/leave rules with in-app notifications and optional external delivery.',
        highlights: [
            'Track selected subjects with configurable rule conditions',
            'Template-based notifications with macro support',
            'Events tab with unread filtering and seen management',
        ],
        link: '/docs/user-guide/core-features/geofences',
        accent: 'chipIndigo',
        icon: 'pin',
    },
    {
        id: 'live-tracking',
        tabLabel: 'Live Tracking',
        title: 'Bring Your Own Tracker',
        description: 'Connect OwnTracks, Overland, Traccar, GPSLogger, Dawarich, Home Assistant, and Colota into one timeline.',
        highlights: [
            'Supports HTTP and MQTT ingestion across supported trackers',
            'Per-source GPS filtering improves timeline quality',
            'Combine multiple devices/sources into a single history',
        ],
        link: '/docs/user-guide/gps-sources/overview',
        shot: shots.sources,
        accent: 'chipSky',
        icon: 'send',
    },
    {
        id: 'imports',
        tabLabel: 'Imports',
        title: 'Import & Migrate History',
        description: 'Bring years of history with you. Imports run in the background and rebuild your timeline.',
        highlights: [
            'Google Timeline from Takeout or the newer export from your phone',
            'GeoPulse, OwnTracks, GPX, GeoJSON, and CSV files',
            'Date-range filtering for partial imports',
            'Replace-or-merge workflow for safe reimports',
        ],
        link: '/docs/user-guide/interacting-with-data/import-export',
        accent: 'chipBlue',
        icon: 'download',
    },
    {
        id: 'mobile',
        tabLabel: 'Mobile',
        title: 'Made for Your Phone Too',
        description: 'The web app works on small screens and installs as a PWA, so checking yesterday takes one tap.',
        highlights: [
            'Install it to your home screen as a Progressive Web App',
            'Timeline, maps, friends, and shared views without a separate interface',
            'Tracking runs through the phone app you already use',
        ],
        shot: shots.mobile,
        accent: 'chipSlate',
        icon: 'phone',
    },
];

const privacyFacts = [
    {
        title: 'Stored in your PostgreSQL',
        text: 'GPS points, timeline, places, and settings live in your own database. Integration secrets such as AI API keys are stored encrypted.',
        icon: 'database',
    },
    {
        title: 'No telemetry',
        text: 'GeoPulse sends no usage analytics or tracking beacons. There is no GeoPulse account and no cloud service to depend on.',
        icon: 'eyeOff',
    },
    {
        title: 'No lock-in',
        text: 'Export your history to GeoPulse, GPX, GeoJSON, OwnTracks, or CSV at any time and take it to another tool.',
        icon: 'download',
    },
    {
        title: 'Sharing is opt-in',
        text: 'Friends see only what you allow: live location and timeline history are separate permissions. Guest links can have a password and an expiry date.',
        icon: 'users',
    },
    {
        title: 'Ready for a household',
        text: 'Multiple users with invitations, roles, an admin audit log, and optional OIDC sign-in.',
        icon: 'lock',
    },
];

const outboundServices = [
    {name: 'Map tiles', detail: 'OpenStreetMap by default', control: 'Swappable', tone: 'swap'},
    {name: 'Reverse geocoding', detail: 'Nominatim by default; Photon and others', control: 'Self-hostable', tone: 'swap'},
    {name: 'Weather', detail: 'Open-Meteo by default', control: 'Can be turned off', tone: 'off'},
    {name: 'Street imagery', detail: 'Panoramax on Timeline maps', control: 'Can be turned off', tone: 'off'},
    {name: 'Release check', detail: 'Latest GeoPulse version from the GitHub API', control: 'Always on', tone: 'always'},
    {name: 'Trip Plans discovery', detail: 'Wikidata and Wikimedia Commons, when you explore an area', control: 'On demand', tone: 'off'},
    {name: 'Immich, Memos, AI, Valhalla, Apprise', detail: 'Only the services you set up', control: 'Opt-in', tone: 'swap'},
];

const landingFaqs = [
    {
        question: 'What is GeoPulse?',
        answer: 'GeoPulse is a self-hosted app that turns raw GPS data into a timeline of stays, trips, and data gaps, with maps, analytics, and controlled sharing. Everything runs on your own server.',
    },
    {
        question: 'Can I import my Google Timeline history?',
        answer: 'Yes. GeoPulse reads both the Google Takeout Location History export and the newer Timeline export from your phone. Large files are processed in the background, and you can import only a date range.',
    },
    {
        question: 'Is there a mobile app?',
        answer: 'The GeoPulse web app is built for phones and installs as a PWA. Location tracking comes from an existing tracker app: OwnTracks or Home Assistant on iPhone and Android, Overland on iPhone, and GPSLogger or Colota on Android. Traccar and Dawarich are supported too.',
    },
    {
        question: 'Can I try GeoPulse without giving up my current setup?',
        answer: 'Yes. Import your existing history, and use a reverse proxy to mirror live OwnTracks traffic so the same points reach both GeoPulse and your current service, such as Dawarich. Switch over when you are ready.',
    },
    {
        question: 'Does GeoPulse send my data anywhere?',
        answer: 'By default: map tiles (OpenStreetMap), reverse geocoding (Nominatim), weather (Open-Meteo), Panoramax imagery on maps, and a GitHub release check. Trip Plans place discovery uses Wikidata. Tiles and geocoding can point to self-hosted servers, and weather and Panoramax can be turned off. Immich, Memos, AI, Valhalla, and Apprise are contacted only if you set them up.',
    },
    {
        question: 'Can my family use one server?',
        answer: 'Yes. One instance supports many users with invitations and roles. Friends can share live location and timeline history with each other, and each permission is granted separately.',
    },
    {
        question: 'Is GeoPulse free?',
        answer: 'GeoPulse is source-available under the Business Source License 1.1 and free for personal, non-commercial use. Commercial use, including running it as a service for others, needs a separate license.',
    },
    {
        question: 'How accurate is the timeline?',
        answer: 'Accuracy depends on GPS signal quality, tracking frequency, environment, and your timeline preferences. GeoPulse uses accuracy metrics and configurable thresholds, and you can correct the result by splitting missed stops, changing travel modes, or enabling map matching.',
    },
    {
        question: 'What server resources does GeoPulse need?',
        answer: 'In typical self-hosted usage GeoPulse uses about 50-100 MB of memory and stays below 1% CPU outside imports, timeline regeneration, and other background jobs. Images are available for amd64 and arm64, and PostgreSQL with PostGIS is required.',
    },
    {
        question: 'Do I need AI?',
        answer: 'No. Timeline generation, maps, and insights work without it. The AI Assistant is optional and uses your own OpenAI-compatible key, which can point to a local model such as Ollama.',
    },
];

const strokeProps = {
    fill: 'none',
    stroke: 'currentColor',
    strokeLinecap: 'round',
    strokeLinejoin: 'round',
    strokeWidth: 2,
};

function Icon({name, className}) {
    const common = {
        className: clsx(styles.svgIcon, className),
        'aria-hidden': 'true',
        focusable: 'false',
        viewBox: '0 0 24 24',
    };

    switch (name) {
        case 'github':
            return (
                <svg {...common}>
                    <path
                        fill="currentColor"
                        d="M12 .5C5.65.5.5 5.65.5 12c0 5.08 3.29 9.39 7.86 10.91.58.11.79-.25.79-.56v-2.02c-3.2.7-3.87-1.37-3.87-1.37-.52-1.34-1.28-1.7-1.28-1.7-1.05-.72.08-.71.08-.71 1.16.08 1.77 1.19 1.77 1.19 1.03 1.76 2.7 1.25 3.36.96.1-.75.4-1.25.73-1.54-2.55-.29-5.24-1.28-5.24-5.69 0-1.26.45-2.28 1.19-3.09-.12-.29-.52-1.46.11-3.04 0 0 .97-.31 3.17 1.18.92-.26 1.91-.38 2.89-.39.98 0 1.97.13 2.89.39 2.2-1.49 3.17-1.18 3.17-1.18.63 1.58.23 2.75.11 3.04.74.81 1.19 1.83 1.19 3.09 0 4.42-2.69 5.39-5.25 5.68.41.36.78 1.06.78 2.14v3.16c0 .31.21.67.79.56A11.51 11.51 0 0 0 23.5 12C23.5 5.65 18.35.5 12 .5Z"
                    />
                </svg>
            );
        case 'star':
            return (
                <svg {...common}>
                    <path
                        fill="currentColor"
                        d="m12 2.2 2.96 6 6.62.96-4.79 4.67 1.13 6.59L12 17.31l-5.92 3.11 1.13-6.59-4.79-4.67 6.62-.96L12 2.2Z"
                    />
                </svg>
            );
        case 'fork':
            return (
                <svg {...common}>
                    <circle cx="18" cy="5" r="3" fill="none" stroke="currentColor" strokeWidth="2" />
                    <circle cx="6" cy="12" r="3" fill="none" stroke="currentColor" strokeWidth="2" />
                    <circle cx="18" cy="19" r="3" fill="none" stroke="currentColor" strokeWidth="2" />
                    <path d="m8.7 10.7 6.6-3.4M8.7 13.3l6.6 3.4" fill="none" stroke="currentColor" strokeLinecap="round" strokeWidth="2" />
                </svg>
            );
        case 'send':
            return (
                <svg {...common}>
                    <path d="M22 2 11 13" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
                    <path d="m22 2-7 20-4-9-9-4 20-7Z" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
                </svg>
            );
        case 'download':
            return (
                <svg {...common}>
                    <path d="M12 3v11" fill="none" stroke="currentColor" strokeLinecap="round" strokeWidth="2" />
                    <path d="m7 10 5 5 5-5" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
                    <path d="M5 20h14" fill="none" stroke="currentColor" strokeLinecap="round" strokeWidth="2" />
                </svg>
            );
        case 'calendar':
            return (
                <svg {...common}>
                    <path d="M7 3v4M17 3v4M4 9h16M6 5h12a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Z" fill="none" stroke="currentColor" strokeLinecap="round" strokeWidth="2" />
                </svg>
            );
        case 'chart':
            return (
                <svg {...common}>
                    <path d="M4 19h16M6 16l4-4 3 3 5-7" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
                    <path d="M18 8h-4M18 8v4" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
                </svg>
            );
        case 'users':
            return (
                <svg {...common}>
                    <path d="M16 20c0-2.2-1.8-4-4-4H8c-2.2 0-4 1.8-4 4" fill="none" stroke="currentColor" strokeLinecap="round" strokeWidth="2" />
                    <circle cx="10" cy="8" r="4" fill="none" stroke="currentColor" strokeWidth="2" />
                    <path d="M20 20c0-1.9-1.3-3.5-3-3.9M17 4.4a3.5 3.5 0 0 1 0 6.8" fill="none" stroke="currentColor" strokeLinecap="round" strokeWidth="2" />
                </svg>
            );
        case 'pin':
            return (
                <svg {...common}>
                    <path d="M20 10c0 5-8 12-8 12S4 15 4 10a8 8 0 1 1 16 0Z" fill="none" stroke="currentColor" strokeWidth="2" />
                    <circle cx="12" cy="10" r="3" fill="none" stroke="currentColor" strokeWidth="2" />
                </svg>
            );
        case 'image':
            return (
                <svg {...common}>
                    <rect x="3" y="5" width="18" height="14" rx="2" fill="none" stroke="currentColor" strokeWidth="2" />
                    <circle cx="8" cy="10" r="1.5" fill="currentColor" />
                    <path d="m21 16-5-5L5 19" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
                </svg>
            );
        case 'cloud':
            return (
                <svg {...common}>
                    <path d="M7 18h10a4 4 0 0 0 .8-7.9A5.5 5.5 0 0 0 7.2 8.7 4.5 4.5 0 0 0 7 18Z" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
                </svg>
            );
        case 'sparkles':
            return (
                <svg {...common}>
                    <path d="m12 3 1.5 4.5L18 9l-4.5 1.5L12 15l-1.5-4.5L6 9l4.5-1.5L12 3Z" fill="none" stroke="currentColor" strokeLinejoin="round" strokeWidth="2" />
                    <path d="m5 14 .8 2.2L8 17l-2.2.8L5 20l-.8-2.2L2 17l2.2-.8L5 14ZM19 14l.7 1.8 1.8.7-1.8.7L19 19l-.7-1.8-1.8-.7 1.8-.7L19 14Z" fill="none" stroke="currentColor" strokeLinejoin="round" strokeWidth="2" />
                </svg>
            );
        case 'map':
            return (
                <svg {...common}>
                    <g {...strokeProps}>
                        <path d="M9 4 3 6v14l6-2 6 2 6-2V4l-6 2-6-2Z" />
                        <path d="M9 4v14M15 6v14" />
                    </g>
                </svg>
            );
        case 'clock':
            return (
                <svg {...common}>
                    <g {...strokeProps}>
                        <circle cx="12" cy="12" r="9" />
                        <path d="M12 7v5l3 2" />
                    </g>
                </svg>
            );
        case 'route':
            return (
                <svg {...common}>
                    <g {...strokeProps}>
                        <circle cx="6" cy="19" r="2" />
                        <circle cx="18" cy="5" r="2" />
                        <path d="M8 19h8.5a3.5 3.5 0 0 0 0-7h-9a3.5 3.5 0 0 1 0-7H16" />
                    </g>
                </svg>
            );
        case 'phone':
            return (
                <svg {...common}>
                    <g {...strokeProps}>
                        <rect x="7" y="2" width="10" height="20" rx="2" />
                        <path d="M11 18h2" />
                    </g>
                </svg>
            );
        case 'lock':
            return (
                <svg {...common}>
                    <g {...strokeProps}>
                        <rect x="5" y="11" width="14" height="10" rx="2" />
                        <path d="M8 11V7a4 4 0 1 1 8 0v4" />
                    </g>
                </svg>
            );
        case 'database':
            return (
                <svg {...common}>
                    <g {...strokeProps}>
                        <ellipse cx="12" cy="5" rx="8" ry="3" />
                        <path d="M4 5v14c0 1.7 3.6 3 8 3s8-1.3 8-3V5M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3" />
                    </g>
                </svg>
            );
        case 'eyeOff':
            return (
                <svg {...common}>
                    <g {...strokeProps}>
                        <path d="m3 3 18 18" />
                        <path d="M10.6 5.1A10 10 0 0 1 12 5c5 0 9 4.5 10 7-.4 1-1.2 2.3-2.4 3.5M6.6 6.6C4.4 8 2.8 10.2 2 12c1 2.5 5 7 10 7 1.7 0 3.3-.5 4.6-1.3" />
                        <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" />
                    </g>
                </svg>
            );
        case 'globe':
            return (
                <svg {...common}>
                    <g {...strokeProps}>
                        <circle cx="12" cy="12" r="9" />
                        <path d="M3 12h18M12 3c2.5 2.6 3.8 5.6 3.8 9s-1.3 6.4-3.8 9c-2.5-2.6-3.8-5.6-3.8-9S9.5 5.6 12 3Z" />
                    </g>
                </svg>
            );
        case 'plug':
            return (
                <svg {...common}>
                    <g {...strokeProps}>
                        <path d="M9 3v5M15 3v5M6 8h12v3a6 6 0 0 1-12 0V8Z" />
                        <path d="M12 17v4" />
                    </g>
                </svg>
            );
        case 'copy':
            return (
                <svg {...common}>
                    <g {...strokeProps}>
                        <rect x="9" y="9" width="11" height="11" rx="2" />
                        <path d="M5 15V6a2 2 0 0 1 2-2h9" />
                    </g>
                </svg>
            );
        case 'arrowRight':
            return (
                <svg {...common}>
                    <path d="M5 12h14M13 6l6 6-6 6" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
                </svg>
            );
        case 'checkCircle':
            return (
                <svg {...common}>
                    <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" strokeWidth="2" />
                    <path d="m8 12 2.7 2.7L16.5 9" fill="none" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
                </svg>
            );
        default:
            return null;
    }
}

function FeatureIcon({feature}) {
    return (
        <span className={clsx(styles.featureIcon, styles[feature.accent])}>
            <Icon name={feature.icon} />
        </span>
    );
}

function DocsLink({to, children}) {
    return (
        <Link className={styles.learnMoreLink} to={to}>
            <span>{children}</span>
            <Icon name="arrowRight" />
        </Link>
    );
}

function Hero() {
    const {siteConfig} = useDocusaurusContext();
    const configuredStats = siteConfig.customFields?.githubStats;
    const stars = githubStat(configuredStats?.stars, DEFAULT_GITHUB_STATS.stars);
    const forks = githubStat(configuredStats?.forks, DEFAULT_GITHUB_STATS.forks);

    return (
        <section className={styles.heroSection}>
            <div className={clsx('container', styles.heroContainer)}>
                <div className={styles.heroContent}>
                    <p className={styles.heroEyebrow}>Self-hosted Google Timeline alternative</p>
                    <Heading as="h1" className={styles.heroTitle}>
                        Own Your Location Timeline
                    </Heading>
                    <p className={styles.heroSubtitle}>
                        GeoPulse turns GPS points from your phone or tracker into a timeline of stays, trips, maps, and
                        insights. Bring your Google Timeline history with you and keep all of it on your own server.
                    </p>
                    <div className={styles.heroActions}>
                        <Link className={clsx('button', styles.heroPrimary)} to="/docs/getting-started/deployment">
                            <span>Install GeoPulse</span>
                            <Icon name="arrowRight" className={styles.actionArrow} />
                        </Link>
                        <Link className={clsx('button', styles.heroSecondary)} to="/docs/getting-started/introduction">
                            Documentation
                        </Link>
                    </div>
                    <Link className={styles.githubBadge} to="https://github.com/tess1o/geopulse">
                        <span className={styles.githubMark}>
                            <Icon name="github" />
                        </span>
                        <span className={styles.githubMetric}>
                            <Icon name="star" className={styles.starIcon} />
                            <span>{githubCountFormatter.format(stars)} Stars</span>
                        </span>
                        <span className={styles.badgeSeparator}>.</span>
                        <span className={styles.githubMetric}>
                            <Icon name="fork" className={styles.forkIcon} />
                            <span>{githubForkFormatter.format(forks)} Forks</span>
                        </span>
                    </Link>
                    <p className={styles.socialProofText}>Source available on GitHub · Free for personal use</p>
                </div>
                <HeroScreenshot />
            </div>
        </section>
    );
}

function HeroScreenshot() {
    return (
        <div className={styles.heroVisual}>
            <div className={styles.browserFrame}>
                <div className={styles.browserBar} aria-hidden="true">
                    <span className={styles.browserDot} />
                    <span className={styles.browserDot} />
                    <span className={styles.browserDot} />
                    <span className={styles.browserUrl}>geopulse.local/app/timeline</span>
                </div>
                <Screenshot {...shots.hero} alt="GeoPulse Timeline with a route map and stay and trip cards" eager />
            </div>
        </div>
    );
}

function SourcesSection() {
    return (
        <section className={styles.sourcesSection} aria-labelledby="location-sources">
            <div className={clsx('container', styles.sectionContainer)}>
                <div className={styles.sectionHeader}>
                    <p className={styles.sectionEyebrow}>Bring your location data</p>
                    <Heading as="h2" id="location-sources" className={styles.sectionTitle}>
                        Location sources and integrations.
                    </Heading>
                    <p className={styles.sectionLead}>
                        Track live with the apps you already use, import years of history, and add context from your self-hosted stack.
                    </p>
                </div>
                <div className={styles.sourcesTable}>
                    {sourceColumns.map((column) => (
                        <div className={styles.sourcesColumn} key={column.title}>
                            <Heading as="h3" className={styles.sourcesHeading}>
                                <span className={styles.integrationIcon}>
                                    <Icon name={column.icon} />
                                </span>
                                <span>{column.title}</span>
                            </Heading>
                            <ul className={styles.sourcesList}>
                                {column.items.map((item) => {
                                    const content = (
                                        <>
                                            <span className={styles.sourceName}>{item.name}</span>
                                            <span className={styles.sourceDetail}>{item.detail}</span>
                                        </>
                                    );
                                    return (
                                        <li key={item.name}>
                                            {item.link ? (
                                                <Link className={styles.sourceRow} to={item.link}>
                                                    {content}
                                                </Link>
                                            ) : (
                                                <span className={styles.sourceRow}>{content}</span>
                                            )}
                                        </li>
                                    );
                                })}
                            </ul>
                        </div>
                    ))}
                </div>
            </div>
        </section>
    );
}

function WalkIllustration({art}) {
    return (
        <svg className={styles.walkArt} viewBox="0 0 320 160" aria-hidden="true" focusable="false">
            <path className={art === 'enriched' ? styles.artRoadWide : styles.artRoad} d={ART_ROAD} />
            {art === 'points' && (
                <>
                    <g className={styles.artDevice}>
                        <rect x="8" y="8" width="16" height="26" rx="3" />
                        <path d="M14 29h4M30 15a8 8 0 0 1 0 12M35 10a14 14 0 0 1 0 22" />
                    </g>
                    {ART_DOTS.map(([x, y]) => (
                        <circle className={styles.artPoint} cx={x} cy={y} r="3.2" key={`${x}-${y}`} />
                    ))}
                    {ART_OUTLIERS.map(([x, y]) => (
                        <g className={styles.artOutlier} key={`${x}-${y}`}>
                            <circle cx={x} cy={y} r="3.2" />
                            <path d={`M${x - 6} ${y - 6}l12 12M${x + 6} ${y - 6}l-12 12`} />
                        </g>
                    ))}
                    <text className={styles.artLabel} x="55" y="74">
                        filtered
                    </text>
                    <text className={styles.artMutedLabel} x="224" y="76" textAnchor="middle">
                        no data
                    </text>
                </>
            )}
            {art === 'timeline' && (
                <>
                    <polyline className={styles.artTrip} points={ART_TRIP} />
                    <path className={styles.artGap} d={ART_GAP} />
                    {ART_STAYS.map(([x, y]) => (
                        <g key={`${x}-${y}`}>
                            <circle className={styles.artStay} cx={x} cy={y} r="12" />
                            <circle className={styles.artStayDot} cx={x} cy={y} r="3.5" />
                            <text className={styles.artLabel} x={x} y={y - 18} textAnchor="middle">
                                Stay
                            </text>
                        </g>
                    ))}
                    <text className={styles.artLabel} x="84" y="124">
                        Trip
                    </text>
                    <text className={styles.artLabel} x="224" y="76" textAnchor="middle">
                        Gap
                    </text>
                </>
            )}
            {art === 'enriched' && (
                <>
                    {ART_DOTS.map(([x, y]) => (
                        <circle className={styles.artPointFaint} cx={x} cy={y} r="2.4" key={`${x}-${y}`} />
                    ))}
                    <path className={styles.artRoute} d={ART_ROUTE} />
                    <path className={styles.artGap} d={ART_GAP} />
                    {ART_STAYS.map(([x, y]) => (
                        <g key={`${x}-${y}`}>
                            <circle className={styles.artStay} cx={x} cy={y} r="7" />
                            <circle className={styles.artStayDot} cx={x} cy={y} r="2.5" />
                        </g>
                    ))}
                    <path className={styles.artLink} d="M22 112v8M150 46v16M300 60v-12" />
                    <g transform="translate(8 88)">
                        <rect className={styles.artBadge} width="30" height="24" rx="6" />
                        <path className={styles.artBadgeIcon} d="M6 18l6-7 5 5 3-3 5 5" />
                        <circle className={styles.artBadgeIconFill} cx="22" cy="8" r="2.5" />
                    </g>
                    <g transform="translate(135 22)">
                        <rect className={styles.artBadge} width="30" height="24" rx="6" />
                        <path className={styles.artBadgeIcon} d="M8 8h14M8 12h14M8 16h9" />
                    </g>
                    <g transform="translate(285 60)">
                        <rect className={styles.artBadge} width="30" height="24" rx="6" />
                        <circle className={styles.artBadgeIcon} cx="15" cy="12" r="4" />
                        <path className={styles.artBadgeIcon} d="M15 3.5v2M15 18.5v2M6.5 12h2M21.5 12h2" />
                    </g>
                </>
            )}
        </svg>
    );
}

function WalkthroughStep({step, index}) {
    return (
        <li className={styles.walkCard}>
            <WalkIllustration art={step.art} />
            <div className={styles.walkBody}>
                <span className={styles.walkStep}>{index + 1}</span>
                <Heading as="h3">{step.title}</Heading>
                <p>{step.text}</p>
                <DocsLink to={step.link}>{step.linkLabel}</DocsLink>
            </div>
        </li>
    );
}

function Walkthrough() {
    return (
        <section className={styles.previewSection} aria-labelledby="walkthrough">
            <div className={clsx('container', styles.sectionContainer)}>
                <div className={styles.sectionHeader}>
                    <p className={styles.sectionEyebrow}>How GeoPulse works</p>
                    <Heading as="h2" id="walkthrough" className={styles.sectionTitle}>
                        From raw GPS to a readable day.
                    </Heading>
                    <p className={styles.sectionLead}>
                        No manual check-ins. GeoPulse builds the timeline from the points your devices already
                        collect, and you stay in control of the result.
                    </p>
                </div>
                <ol className={styles.walkGrid}>
                    {walkthroughSteps.map((step, index) => (
                        <WalkthroughStep step={step} index={index} key={step.title} />
                    ))}
                </ol>
            </div>
        </section>
    );
}

function InstallTerminal() {
    const [copied, setCopied] = useState(false);

    useEffect(() => {
        if (!copied) {
            return undefined;
        }
        const timeoutId = window.setTimeout(() => setCopied(false), 2000);
        return () => window.clearTimeout(timeoutId);
    }, [copied]);

    const handleCopy = () => {
        navigator.clipboard?.writeText(INSTALL_SCRIPT).then(() => setCopied(true), () => {});
    };

    return (
        <div className={styles.terminal}>
            <div className={styles.terminalBar}>
                <span className={styles.browserDot} />
                <span className={styles.browserDot} />
                <span className={styles.browserDot} />
                <span className={styles.terminalTitle}>Docker Compose quick install</span>
                <button type="button" className={styles.copyButton} onClick={handleCopy}>
                    <Icon name={copied ? 'checkCircle' : 'copy'} />
                    <span>{copied ? 'Copied' : 'Copy'}</span>
                </button>
            </div>
            <pre>
                <code>
                    {INSTALL_SCRIPT.split('\n').map((line, index) => (
                        <span className={line.startsWith('#') ? styles.codeComment : undefined} key={index}>
                            {line}
                            {'\n'}
                        </span>
                    ))}
                </code>
            </pre>
        </div>
    );
}

function InstallSection() {
    return (
        <section className={styles.installSection} aria-labelledby="install">
            <div className={clsx('container', styles.installLayout)}>
                <div>
                    <p className={styles.sectionEyebrow}>Deploy in minutes</p>
                    <Heading as="h2" id="install" className={styles.sectionTitle}>
                        Up and running with Docker Compose.
                    </Heading>
                    <p className={styles.sectionLead}>
                        Download two files, start the containers, and open GeoPulse in your browser. Review the
                        security settings in <code>.env</code> before you expose it to the internet.
                    </p>
                    <p className={styles.installMeta}>Runs on amd64 and arm64 · Typically 50-100 MB RAM</p>
                    <p className={styles.deployLabel}>All ways to install</p>
                    <ul className={styles.deployList}>
                        {deployOptions.map((option) => (
                            <li key={option.label}>
                                <Link className={styles.deployChip} to={option.link}>
                                    {option.label}
                                </Link>
                            </li>
                        ))}
                    </ul>
                </div>
                <InstallTerminal />
            </div>
        </section>
    );
}

function ExplorePanel() {
    const [activeFeatureId, setActiveFeatureId] = useState(exploreFeatures[0].id);
    const [isAutoRotating, setIsAutoRotating] = useState(true);
    const [isPaused, setIsPaused] = useState(false);
    const [prefersReducedMotion, setPrefersReducedMotion] = useState(false);
    const [contentAnimationToken, setContentAnimationToken] = useState(0);
    const activeFeature = exploreFeatures.find((feature) => feature.id === activeFeatureId) || exploreFeatures[0];
    const activeShot = activeFeature.shot;

    useEffect(() => {
        const query = window.matchMedia('(prefers-reduced-motion: reduce)');
        const update = () => setPrefersReducedMotion(query.matches);
        update();
        query.addEventListener('change', update);
        return () => query.removeEventListener('change', update);
    }, []);

    useEffect(() => {
        if (!isAutoRotating || isPaused || prefersReducedMotion) {
            return undefined;
        }

        const timeoutId = window.setTimeout(() => {
            const currentIndex = exploreFeatures.findIndex((feature) => feature.id === activeFeatureId);
            const nextIndex = currentIndex === -1 ? 0 : (currentIndex + 1) % exploreFeatures.length;
            setActiveFeatureId(exploreFeatures[nextIndex].id);
            setContentAnimationToken((token) => token + 1);
        }, AUTO_ROTATE_MS);

        return () => window.clearTimeout(timeoutId);
    }, [activeFeatureId, isAutoRotating, isPaused, prefersReducedMotion]);

    const handleFeatureClick = (featureId) => {
        if (featureId !== activeFeatureId) {
            setContentAnimationToken((token) => token + 1);
        }
        setActiveFeatureId(featureId);
        setIsAutoRotating(false);
    };

    return (
        <section
            className={clsx(styles.explorePanel, styles[activeFeature.accent])}
            aria-labelledby="explore-geopulse"
            onMouseEnter={() => setIsPaused(true)}
            onMouseLeave={() => setIsPaused(false)}
            onFocus={() => setIsPaused(true)}
            onBlur={() => setIsPaused(false)}>
            <div className={styles.exploreHeader}>
                <p className={styles.panelKicker} id="explore-geopulse">
                    Explore GeoPulse
                </p>
                <div className={styles.featureTabs} role="tablist" aria-label="GeoPulse features">
                    {exploreFeatures.map((feature) => (
                        <button
                            type="button"
                            className={clsx(styles.featureTab, activeFeature.id === feature.id && styles.activeTab)}
                            aria-selected={activeFeature.id === feature.id}
                            role="tab"
                            key={feature.id}
                            onClick={() => handleFeatureClick(feature.id)}>
                            <FeatureIcon feature={feature} />
                            <span>{feature.tabLabel}</span>
                        </button>
                    ))}
                </div>
            </div>
            <div
                className={clsx(styles.exploreBody, activeShot && styles.exploreBodyWithShot)}
                role="tabpanel"
                key={`${activeFeature.id}-${contentAnimationToken}`}>
                <div className={styles.exploreCopy}>
                    <Heading as="h2" className={styles.exploreTitle}>
                        {activeFeature.title}
                    </Heading>
                    <p className={styles.exploreDescription}>{activeFeature.description}</p>
                    <ul className={styles.highlightList}>
                        {activeFeature.highlights.map((point) => (
                            <li key={point}>
                                <Icon name="checkCircle" className={styles.highlightIcon} />
                                <span>{point}</span>
                            </li>
                        ))}
                    </ul>
                    {activeFeature.link && (
                        <div className={styles.featurePanelActions}>
                            <DocsLink to={activeFeature.link}>Read docs</DocsLink>
                        </div>
                    )}
                </div>
                {activeShot && (
                    <div
                        className={clsx(
                            styles.exploreShot,
                            styles.screenshotFrame,
                            activeShot.portrait && styles.portraitFrame,
                        )}>
                        <Screenshot {...activeShot} alt={`GeoPulse ${activeFeature.tabLabel} screenshot`} />
                    </div>
                )}
            </div>
        </section>
    );
}

function ShowcasePanels() {
    return (
        <div className={clsx('container', styles.showcasePanels)}>
            <ExplorePanel />
        </div>
    );
}

function PrivacySection() {
    return (
        <section className={styles.privacySection} aria-labelledby="privacy">
            <div className={clsx('container', styles.sectionContainer)}>
                <div className={styles.sectionHeader}>
                    <p className={styles.sectionEyebrow}>Your data, your server</p>
                    <Heading as="h2" id="privacy" className={styles.sectionTitle}>
                        Where your location history goes.
                    </Heading>
                    <p className={styles.sectionLead}>
                        Nowhere you did not choose. These are the facts, not a slogan.
                    </p>
                </div>
                <div className={styles.privacyGrid}>
                    {privacyFacts.map((fact) => (
                        <div className={styles.privacyTile} key={fact.title}>
                            <span className={styles.integrationIcon}>
                                <Icon name={fact.icon} />
                            </span>
                            <Heading as="h3">{fact.title}</Heading>
                            <p>{fact.text}</p>
                        </div>
                    ))}
                    <div className={clsx(styles.privacyTile, styles.outboundTile)}>
                        <span className={styles.integrationIcon}>
                            <Icon name="globe" />
                        </span>
                        <Heading as="h3">Every outside service, listed</Heading>
                        <ul className={styles.outboundList}>
                            {outboundServices.map((service) => (
                                <li className={styles.outboundRow} key={service.name}>
                                    <div>
                                        <span className={styles.outboundName}>{service.name}</span>
                                        <span className={styles.outboundDetail}>{service.detail}</span>
                                    </div>
                                    <span className={clsx(styles.controlPill, styles[`control-${service.tone}`])}>
                                        {service.control}
                                    </span>
                                </li>
                            ))}
                        </ul>
                    </div>
                </div>
            </div>
        </section>
    );
}

function FaqSection() {
    return (
        <section className={styles.faqSection} aria-labelledby="faq">
            <div className={clsx('container', styles.faqContainer)}>
                <div className={styles.sectionHeader}>
                    <p className={styles.sectionEyebrow}>FAQ</p>
                    <Heading as="h2" id="faq" className={styles.sectionTitle}>
                        Common questions before you self-host.
                    </Heading>
                    <p className={styles.sectionLead}>
                        Short answers pulled from the documentation FAQ, with the full page available when you want more
                        detail.
                    </p>
                </div>
                <div className={styles.faqGrid}>
                    {landingFaqs.map((item) => (
                        <details className={styles.faqItem} key={item.question}>
                            <summary>
                                <span>{item.question}</span>
                                <span className={styles.faqToggle} aria-hidden="true">
                                    +
                                </span>
                            </summary>
                            <p>{item.answer}</p>
                        </details>
                    ))}
                </div>
                <Link className={styles.faqMoreLink} to="/docs/faq">
                    <span>Read the full FAQ</span>
                    <Icon name="arrowRight" />
                </Link>
            </div>
        </section>
    );
}

function DocsGateway() {
    return (
        <section className={styles.docsSection} aria-labelledby="docs-gateway">
            <div className={clsx('container', styles.docsContainer)}>
                <div className={styles.sectionHeader}>
                    <p className={styles.sectionEyebrow}>Documentation</p>
                    <Heading as="h2" id="docs-gateway" className={styles.sectionTitle}>
                        Where to go next.
                    </Heading>
                </div>
                <div className={styles.docsGrid}>
                    {quickDocs.map((doc) => (
                        <Link className={styles.docCard} to={doc.link} key={doc.title}>
                            <Heading as="h3">{doc.title}</Heading>
                            <p>{doc.text}</p>
                        </Link>
                    ))}
                </div>
            </div>
        </section>
    );
}

export default function Home() {
    return (
        <Layout
            title="GeoPulse"
            description="GeoPulse is a self-hosted Google Timeline alternative: GPS timelines, map replay, analytics, and controlled sharing on your own server.">
            <main className={styles.landingPage}>
                <Hero />
                <ShowcasePanels />
                <InstallSection />
                <Walkthrough />
                <SourcesSection />
                <PrivacySection />
                <FaqSection />
                <DocsGateway />
            </main>
        </Layout>
    );
}

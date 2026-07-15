import clsx from 'clsx';
import {useEffect, useState} from 'react';
import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';
import styles from './index.module.css';

const exploreFeatures = [
    {
        id: 'live-tracking',
        tabLabel: 'Live Tracking',
        title: 'Real-time Source Integrations',
        description: 'Connect OwnTracks, Overland, Traccar, GPSLogger, Dawarich, Home Assistant, and Colota into one timeline.',
        highlights: [
            'Supports HTTP and MQTT ingestion across supported trackers',
            'Per-source GPS filtering improves timeline quality',
            'Combine multiple devices/sources into a single history',
        ],
        link: '/docs/user-guide/gps-sources/overview',
        accent: 'chipSky',
        icon: 'send',
    },
    {
        id: 'imports',
        tabLabel: 'Imports',
        title: 'Import & Migrate History',
        description: 'Import legacy and backup data with background processing and timeline regeneration.',
        highlights: [
            'Supports GeoPulse, OwnTracks, Google Timeline, GPX, GeoJSON, and CSV',
            'Date-range filtering for partial imports',
            'Replace-or-merge workflow for safe reimports',
        ],
        link: '/docs/user-guide/interacting-with-data/import-export',
        accent: 'chipBlue',
        icon: 'download',
    },
    {
        id: 'timeline',
        tabLabel: 'Timeline',
        title: 'Stays, Trips, and Gaps',
        description: 'GeoPulse converts raw points into timeline events with configurable detection logic.',
        highlights: [
            'Automatic stay/trip/gap detection',
            'Travel classification tuning (walk/car/bicycle/train/flight)',
            'Manual movement-type overrides with rebuild-safe reapply',
        ],
        link: '/docs/user-guide/core-features/timeline',
        accent: 'chipTeal',
        icon: 'calendar',
    },
    {
        id: 'insight',
        tabLabel: 'Insights',
        title: 'Dashboard & Journey Insights && Location Analytics  ',
        description: 'Track movement patterns, places, routes, and achievements across multiple time windows.',
        highlights: [
            'Selected period + 7-day + 30-day overviews',
            'Top places, route analysis, and activity breakdowns',
            'Journey Insights for countries/cities/travel milestones',
        ],
        accent: 'chipPurple',
        icon: 'chart',
    },
    {
        id: 'friends',
        tabLabel: 'Friends',
        title: 'Friends & Sharing Controls',
        description: 'Connect with friends and control exactly what you share.',
        highlights: [
            'Invite, accept, reject, and cancel friend requests',
            'Separate permissions for live location and timeline history',
            'Live map and shared timeline views for permitted friends',
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
        id: 'immich',
        tabLabel: 'Immich',
        title: 'Immich Photo Overlay',
        description: 'Display Immich photos directly on the timeline map for the selected date range.',
        highlights: [
            'Configure URL + API key in Profile',
            'Toggle photo layer on Timeline map',
            'Open photos in-app and download originals',
        ],
        link: '/docs/user-guide/personalization/immich-integration',
        accent: 'chipOrange',
        icon: 'image',
    },
    {
        id: 'ai',
        tabLabel: 'AI',
        title: 'AI Assistant (Optional)',
        description: 'Ask natural-language questions about your personal movement history.',
        highlights: [
            'Bring your own OpenAI-compatible API key/model',
            'Query stays, trips, places, and travel patterns',
            'Per-user configuration and encrypted key storage',
        ],
        link: '/docs/user-guide/using-geopulse/ai-assistant',
        accent: 'chipTeal',
        icon: 'sparkles',
    },
];

const orbitChips = [
    {label: 'Live Tracking', className: 'orbitChipOne', accent: 'chipSky', icon: 'send'},
    {label: 'Smart Import', className: 'orbitChipTwo', accent: 'chipBlue', icon: 'download'},
    {label: 'Auto-Timeline', className: 'orbitChipThree', accent: 'chipTeal', icon: 'calendar'},
    {label: 'Deep Insights', className: 'orbitChipFour', accent: 'chipPurple', icon: 'chart'},
    {label: 'Immich Integration', className: 'orbitChipFive', accent: 'chipOrange', icon: 'image'},
    {label: 'Friends', className: 'orbitChipSix', accent: 'chipRose', icon: 'users'},
    {label: 'Geofences', className: 'orbitChipSeven', accent: 'chipIndigo', icon: 'pin'},
    {label: 'AI', className: 'orbitChipEight', accent: 'chipSlate', icon: 'sparkles'},
];

const quickDocs = [
    {
        title: 'Quick Start',
        text: 'Install GeoPulse and reach the first login flow.',
        link: '/docs/getting-started/quick-start',
    },
    {
        title: 'GPS Sources',
        text: 'Connect trackers and import historical exports.',
        link: '/docs/user-guide/gps-sources/overview',
    },
    {
        title: 'Deployment',
        text: 'Choose Docker Compose, Proxmox, Kubernetes, Helm, or manual install.',
        link: '/docs/getting-started/deployment/docker-compose',
    },
    {
        title: 'REST API',
        text: 'Use API tokens and generated reference docs for automation.',
        link: '/docs/api/api-tokens',
    },
];

const landingFaqs = [
    {
        question: 'What is GeoPulse?',
        answer: 'GeoPulse is a self-hosted location tracking and analysis platform that turns raw GPS data into timelines, trip classification, maps, and analytics while keeping your data on your own server.',
    },
    {
        question: 'What location sources are supported?',
        answer: 'GeoPulse supports OwnTracks, Overland, Dawarich, GPSLogger, Home Assistant, Colota, and file imports such as GPX, GeoJSON, Google Timeline, CSV, and OwnTracks exports.',
    },
    {
        question: 'Can I self-host GeoPulse?',
        answer: 'Yes. Self-hosting is the primary way to use GeoPulse, with Docker Compose for a quick start and Kubernetes or Helm for larger deployments.',
    },
    {
        question: 'Who can see my location data?',
        answer: 'Your location data is stored on your self-hosted server. External services are only used when you configure them, such as map tiles, reverse geocoding providers, or optional AI features.',
    },
    {
        question: 'How accurate is the timeline?',
        answer: 'Timeline accuracy depends on GPS signal quality, tracking frequency, environment, and your timeline preferences. GeoPulse uses accuracy metrics and configurable thresholds to improve results.',
    },
    {
        question: 'What server resources does GeoPulse need?',
        answer: 'GeoPulse is optimized to be very fast and lightweight. In typical self-hosted usage, the app usually uses about 50-100MB of memory and stays below 1% CPU outside heavier background jobs.',
    },
];

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
        case 'sparkles':
            return (
                <svg {...common}>
                    <path d="m12 3 1.5 4.5L18 9l-4.5 1.5L12 15l-1.5-4.5L6 9l4.5-1.5L12 3Z" fill="none" stroke="currentColor" strokeLinejoin="round" strokeWidth="2" />
                    <path d="m5 14 .8 2.2L8 17l-2.2.8L5 20l-.8-2.2L2 17l2.2-.8L5 14ZM19 14l.7 1.8 1.8.7-1.8.7L19 19l-.7-1.8-1.8-.7 1.8-.7L19 14Z" fill="none" stroke="currentColor" strokeLinejoin="round" strokeWidth="2" />
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

function HeroOrbit() {
    const logoUrl = useBaseUrl('/img/geopulse-logo.svg');

    return (
        <div className={styles.heroVisual} aria-label="GeoPulse feature orbit">
            <div className={styles.visualShowcase}>
                <div className={clsx(styles.orbitRing, styles.ringOne)} />
                <div className={clsx(styles.orbitRing, styles.ringTwo)} />
                <img src={logoUrl} alt="GeoPulse logo" className={styles.massiveLogo} />
                {orbitChips.map((chip) => (
                    <div className={clsx(styles.featureChip, styles[chip.className])} key={chip.label}>
                        <FeatureIcon feature={chip} />
                        <span>{chip.label}</span>
                    </div>
                ))}
            </div>
        </div>
    );
}

function Hero() {
    return (
        <section className={styles.heroSection}>
            <div className={clsx('container', styles.heroContainer)}>
                <div className={styles.heroContent}>
                    <p className={styles.heroEyebrow}>Privacy-first Google Timeline alternative</p>
                    <Heading as="h1" className={styles.heroTitle}>
                        Your Timeline, Self-Hosted
                    </Heading>
                    <p className={styles.heroSubtitle}>
                        GeoPulse turns raw GPS points into a private location timeline with stays, trips, maps, and
                        insights, all under your control.
                    </p>
                    <div className={styles.heroActions}>
                        <Link className={clsx('button', styles.heroPrimary)} to="/docs/getting-started/quick-start">
                            <span>Start Your Journey</span>
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
                            <span>1.3K Stars</span>
                        </span>
                        <span className={styles.badgeSeparator}>.</span>
                        <span className={styles.githubMetric}>
                            <Icon name="fork" className={styles.forkIcon} />
                            <span>50 Forks</span>
                        </span>
                    </Link>
                    <p className={styles.socialProofText}>The privacy-first, open-source alternative to Google Timeline.</p>
                </div>
                <HeroOrbit />
            </div>
        </section>
    );
}

function ExplorePanel() {
    const [activeFeatureId, setActiveFeatureId] = useState(exploreFeatures[0].id);
    const [isAutoRotating, setIsAutoRotating] = useState(true);
    const [contentAnimationToken, setContentAnimationToken] = useState(0);
    const activeFeature = exploreFeatures.find((feature) => feature.id === activeFeatureId) || exploreFeatures[0];

    useEffect(() => {
        if (!isAutoRotating) {
            return undefined;
        }

        const timeoutId = window.setTimeout(() => {
            const currentIndex = exploreFeatures.findIndex((feature) => feature.id === activeFeatureId);
            const nextIndex = currentIndex === -1 ? 0 : (currentIndex + 1) % exploreFeatures.length;
            setActiveFeatureId(exploreFeatures[nextIndex].id);
            setContentAnimationToken((token) => token + 1);
        }, 4500);

        return () => window.clearTimeout(timeoutId);
    }, [activeFeatureId, isAutoRotating]);

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
            aria-labelledby="explore-geopulse">
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
            <div className={styles.exploreBody}>
                <div className={styles.exploreCopy} key={`${activeFeature.id}-${contentAnimationToken}`}>
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
                            <Link className={styles.learnMoreLink} to={activeFeature.link}>
                                <span>Read docs</span>
                                <Icon name="arrowRight" />
                            </Link>
                        </div>
                    )}
                </div>
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

function TimelinePreview() {
    const screenshotUrl = useBaseUrl('/img/geopulse-app-timeline.png');

    return (
        <section className={styles.previewSection} aria-labelledby="timeline-preview">
            <div className={clsx('container', styles.previewLayout)}>
                <div>
                    <p className={styles.sectionEyebrow}>Timeline workspace</p>
                    <Heading as="h2" id="timeline-preview" className={styles.sectionTitle}>
                        Map replay, event cards, and analysis in one workspace.
                    </Heading>
                    <p className={styles.sectionLead}>
                        Review a day as a route, inspect stays and transitions, replay movement, and jump from timeline
                        events into places, reports, dashboard views, or data correction workflows.
                    </p>
                    <div className={styles.previewActions}>
                        <Link className={clsx('button', styles.heroPrimary)} to="/docs/user-guide/core-features/timeline">
                            Timeline Docs
                        </Link>
                        <Link className={clsx('button', styles.heroSecondary)} to="/docs/user-guide/core-features/dashboard">
                            Dashboard Docs
                        </Link>
                    </div>
                </div>
                <div className={styles.screenshotFrame}>
                    <img src={screenshotUrl} alt="GeoPulse timeline map and event cards" />
                </div>
            </div>
        </section>
    );
}

function InstallPreview() {
    return (
        <section className={styles.installSection} aria-labelledby="install">
            <div className={clsx('container', styles.installLayout)}>
                <div>
                    <p className={styles.sectionEyebrow}>Deploy your way</p>
                    <Heading as="h2" id="install" className={styles.sectionTitle}>
                        Start small, then scale into your preferred self-hosted setup.
                    </Heading>
                    <p className={styles.sectionLead}>
                        Docker Compose is the fastest path for a single server. Kubernetes and Helm are available when
                        you want charted releases, values, and production-style operations.
                    </p>
                    <div className={styles.previewActions}>
                        <Link className={clsx('button', styles.heroPrimary)} to="/docs/getting-started/deployment/docker-compose">
                            Docker Compose
                        </Link>
                        <Link className={clsx('button', styles.heroSecondary)} to="/docs/getting-started/deployment/kubernetes-helm">
                            Kubernetes / Helm
                        </Link>
                    </div>
                </div>
                <div className={styles.codePanel} aria-label="Docker Compose quick start">
                    <span className={styles.codeLabel}>Quick install</span>
                    <pre>
                        <code>{`mkdir geopulse && cd geopulse
curl -L -o .env \\
  https://raw.githubusercontent.com/tess1o/GeoPulse/main/.env.example
curl -L -o docker-compose.yml \\
  https://raw.githubusercontent.com/tess1o/GeoPulse/main/docker-compose.yml
docker compose up -d`}</code>
                    </pre>
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
                    <p className={styles.sectionEyebrow}>Documentation gateway</p>
                    <Heading as="h2" id="docs-gateway" className={styles.sectionTitle}>
                        Landing page at the front, full documentation underneath.
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
            description="GeoPulse is a privacy-first, self-hosted Google Timeline alternative for GPS timelines, map replay, analytics, and controlled sharing.">
            <main className={styles.landingPage}>
                <Hero />
                <ShowcasePanels />
                <TimelinePreview />
                <InstallPreview />
                <FaqSection />
                <DocsGateway />
            </main>
        </Layout>
    );
}

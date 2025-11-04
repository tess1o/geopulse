import clsx from 'clsx';
import Heading from '@theme/Heading';
import styles from './styles.module.css';

const FeatureList = [
    {
        title: 'GPS Data Integration',
        description: (
            <>
                Works with OwnTracks (HTTP or MQTT), Overland, Dawarich, GPSLogger, and HomeAssistant tracking apps.
                Supports real-time sync and manual import from Google Timeline, GPX, GeoJSON, or OwnTracks JSON files.
            </>
        ),
    },
    {
        title: 'Timeline and Maps',
        description: (
            <>
                Automatically categorizes GPS data into stays, trips, and data gaps.
                Includes interactive maps with Immich photo integration and flexible date range viewing.
            </>
        ),
    },
    {
        title: 'Analytics',
        description: (
            <>
                Dashboard with travel distance and visit statistics, journey insights by country and city,
                movement pattern analysis, and AI-powered location insights.
            </>
        ),
    },
    {
        title: 'AI Chat Assistant',
        description: (
            <>
                Ask natural language questions about your travel patterns and get intelligent insights.
                Supports any OpenAI-compatible API service using your personal API key.
            </>
        ),
    },
    {
        title: 'Social and Sharing',
        description: (
            <>
                Connect with friends for real-time location sharing with full privacy controls.
                Share public or time-limited links with password protection.
            </>
        ),
    },
    {
        title: 'Places & Customization',
        description: (
            <>
                Manage favorite places directly on the map with reverse geocoding via Nominatim, Google Maps, or Mapbox.
                Customize map tiles, adjust timeline sensitivity, and export your data in multiple formats.
            </>
        ),
    },
];

function Feature({ title, description }) {
    return (
        <div className={clsx('col col--4')}>
            <div className="text--center padding-horiz--md">
                <Heading as="h3">{title}</Heading>
                <p>{description}</p>
            </div>
        </div>
    );
}

export default function HomepageFeatures() {
    return (
        <section className={styles.features}>
            <div className="container">
                <div className="text--center margin-bottom--lg">
                    <Heading as="h2">Transform Your GPS Data into Insights</Heading>
                    <p>
                        GeoPulse turns raw GPS data from OwnTracks, Overland, Dawarich, or HomeAssistant
                        into organized timelines and analytics — all hosted securely on your own server.
                    </p>
                </div>
                <div className="row">
                    {FeatureList.map((props, idx) => (
                        <Feature key={idx} {...props} />
                    ))}
                </div>
            </div>
        </section>
    );
}
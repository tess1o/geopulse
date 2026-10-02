// Non-translatable metadata for the "Explore GeoPulse" home panel. The `id` here doubles as the
// key segment under `ui.exploreFeatures.<id>` in the locale catalogs (tabLabel/title/description/
// highlights) -- see ExploreGeoPulsePanel.vue, which merges this metadata with the translated copy.
export const EXPLORE_FEATURES_META = [
  {
    id: 'liveTracking',
    icon: 'pi pi-send',
    colorClass: 'chip-1-color',
    learnMoreUrl: 'https://geopulse.cc/docs/user-guide/gps-sources/overview',
    learnMoreType: 'docs',
  },
  {
    id: 'imports',
    icon: 'pi pi-download',
    colorClass: 'chip-2-color',
    learnMoreUrl: 'https://geopulse.cc/docs/user-guide/interacting-with-data/import-export',
    learnMoreType: 'docs',
  },
  {
    id: 'timeline',
    icon: 'pi pi-calendar',
    colorClass: 'chip-3-color',
    learnMoreUrl: 'https://geopulse.cc/docs/user-guide/core-features/timeline',
    learnMoreType: 'docs',
  },
  {
    id: 'insight',
    icon: 'pi pi-chart-line',
    colorClass: 'chip-4-color',
  },
  {
    id: 'friends',
    icon: 'pi pi-users',
    colorClass: 'chip-6-color',
    learnMoreUrl: '/app/friends',
    learnMoreType: 'app',
  },
  {
    id: 'geofences',
    icon: 'pi pi-map-marker',
    colorClass: 'chip-7-color',
    learnMoreUrl: 'https://geopulse.cc/docs/user-guide/core-features/geofences',
    learnMoreType: 'docs',
  },
  {
    id: 'immich',
    icon: 'pi pi-images',
    colorClass: 'chip-5-color',
    learnMoreUrl: 'https://geopulse.cc/docs/user-guide/personalization/immich-integration',
    learnMoreType: 'docs',
  },
  {
    id: 'weather',
    icon: 'pi pi-cloud',
    colorClass: 'chip-1-color',
    learnMoreUrl: 'https://geopulse.cc/docs/system-administration/configuration/weather',
    learnMoreType: 'docs',
  },
  {
    id: 'ai',
    icon: 'pi pi-sparkles',
    colorClass: 'chip-8-color',
    learnMoreUrl: 'https://geopulse.cc/docs/api/mcp',
    learnMoreType: 'docs',
  },
]

export default EXPLORE_FEATURES_META

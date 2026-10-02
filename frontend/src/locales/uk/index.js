import nav from './nav.js'
import common from './common.js'
import errors from './errors.js'
import insights from './insights.js'
import badges from './badges.js'
import movementTypes from './movementTypes.js'
import profile from './profile/index.js'
import settings from './settings.js'
import weather from './weather.js'
import auth from './auth.js'
import timeline from './timeline.js'
import trips from './trips.js'
import data from './data.js'
import sharing from './sharing.js'
import admin from './admin.js'
import adminSettings from './adminSettings.js'
import adminProviderSettings from './adminProviderSettings.js'
import adminAuditInvitations from './adminAuditInvitations.js'
import adminCampaignsAndUsers from './adminCampaignsAndUsers.js'
import analytics from './analytics.js'
import digest from './digest.js'
import maps from './maps.js'
import ui from './ui.js'
import notifications from './notifications.js'
import friends from './friends.js'
import place from './place.js'
import geofences from './geofences.js'
import locationAnalytics from './locationAnalytics.js'
import locationSources from './locationSources.js'
import timelineJobs from './timelineJobs.js'
import help from './help.js'
import aiChat from './aiChat.js'
import geocoding from './geocoding.js'
import timelinePreferences from './timelinePreferences.js'
import technicalData from './technicalData.js'
import classification from './classification.js'
import photoViewer from './photoViewer.js'
import favoritesGeocodingDialogs from './favoritesGeocodingDialogs.js'
import tripDialogs from './tripDialogs.js'
import miscDialogs from './miscDialogs.js'
import imports from './imports.js'
import exports_ from './exports.js'

/**
 * Ukrainian catalog.
 *
 * DRAFT: machine-drafted translations, pending native-speaker review.
 *
 * Loaded on demand by `locales/index.js` via dynamic import, which Vite emits as a JS chunk -- and
 * because the PWA precache glob already covers `**\/*.js`, it is available offline.
 *
 * Key set must stay identical to en/index.js; `locales.test.js` enforces that.
 */
export default {
    nav,
    common,
    errors,
    insights,
    badges,
    movementTypes,
    profile,
    settings,
    weather,
    auth,
    timeline,
    trips,
    data,
    sharing,
    admin,
    adminSettings,
    adminProviderSettings,
    adminAuditInvitations,
    adminCampaignsAndUsers,
    analytics,
    digest,
    maps,
    ui,
    notifications,
    friends,
    place,
    geofences,
    locationAnalytics,
    locationSources,
    timelineJobs,
    help,
    aiChat,
    geocoding,
    timelinePreferences,
    technicalData,
    classification,
    photoViewer,
    favoritesGeocodingDialogs,
    tripDialogs,
    miscDialogs,
    imports,
    exports: exports_,
}

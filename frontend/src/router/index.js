import {createRouter, createWebHistory} from 'vue-router'
import LoginPage from '../views/LoginPage.vue'
import RegisterPage from '../views/RegisterPage.vue'
import Home from "@/views/Home.vue";
import FriendsPage from "@/views/app/FriendsPage.vue";
import MainAppPage from "@/views/app/MainAppPage.vue";
import TimelinePage from "@/views/app/TimelinePage.vue";
import DashboardPage from "@/views/app/DashboardPage.vue";
import JourneyInsights from "@/views/app/JourneyInsights.vue";
import LocationSourcesPage from "@/views/app/LocationSourcesPage.vue";
import TimelinePreferencesPage from "@/views/app/TimelinePreferencesPage.vue";
import UserProfilePage from "@/views/app/UserProfilePage.vue";
import ShareLinksPage from "@/views/app/ShareLinksPage.vue";
import DataExportImportPage from "@/views/app/DataExportImportPage.vue";
import DebugExportPage from "@/views/app/DebugExportPage.vue";
import DebugImportPage from "@/views/app/DebugImportPage.vue";
import HelpPage from "@/views/app/HelpPage.vue";
import TimelineReportsPage from "@/views/app/TimelineReportsPage.vue";
import TechnicalDataPage from "@/views/app/TechnicalDataPage.vue";
import GeocodingManagementPage from "@/views/app/GeocodingManagementPage.vue";
import FavoritesManagementPage from "@/views/app/FavoritesManagementPage.vue";
import GeofencesPage from "@/views/app/GeofencesPage.vue";
import TimelineLabelsManagementPage from "@/views/app/TimelineLabelsManagementPage.vue";
import TripsManagementPage from "@/views/app/TripsManagementPage.vue";
import TripWorkspacePage from "@/views/app/TripWorkspacePage.vue";
import CoverageExplorerPage from "@/views/app/CoverageExplorerPage.vue";
import AIChatPage from "@/views/app/AIChatPage.vue";
import TimeDigestPage from "@/views/app/TimeDigestPage.vue";
import PlaceDetailsPage from "@/views/app/PlaceDetailsPage.vue";
import SharedLocationPage from "@/views/SharedLocationPage.vue";
import SharedTimelinePage from "@/views/SharedTimelinePage.vue";
import ErrorPage from "@/views/ErrorPage.vue";
import NotFoundPage from "@/views/NotFoundPage.vue";
import { useAuthStore } from '@/stores/auth'
import { maintenance, refreshMaintenance } from '@/stores/maintenance'
import { applyDocumentTitle } from '@/utils/documentTitle'

// Auth guard function
const requireAuth = async (to, from, next) => {
    const authStore = useAuthStore()

    try {
        // Check auth on first navigation
        if (!authStore.user) {
            await authStore.checkAuth()
        }

        // If still not authenticated after check, redirect to login
        if (!authStore.isAuthenticated) {
            next('/login')
        } else {
            next()
        }
    } catch (error) {
        // If authentication check fails, clear auth data and redirect to login
        console.log('Authentication check failed, redirecting to login')
        authStore.clearUser()
        next('/login')
    }
}

// Guest guard function (for login/register pages)
const requireGuest = async (to, from, next) => {
    const authStore = useAuthStore()

    try {
        // Check auth on first navigation
        if (!authStore.user) {
            await authStore.checkAuth()
        }

        // If authenticated, redirect away from login/register
        if (authStore.isAuthenticated) {
            const redirectUrl = authStore.defaultRedirectUrl || '/app/timeline'
            next(redirectUrl)
        } else {
            next()
        }
    } catch (error) {
        console.log('Guest check failed, proceeding to login/register')
        authStore.clearUser()
        next()
    }
}

// Admin visibility guard function. Real admin writes are still enforced server-side.
const requireAdmin = async (to, from, next) => {
    const authStore = useAuthStore()

    try {
        // Check auth on first navigation
        if (!authStore.user) {
            await authStore.checkAuth()
        }

        // If not authenticated, redirect to login
        if (!authStore.isAuthenticated) {
            next('/login')
        } else if (to.meta.requiresRealAdmin && !authStore.isAdmin) {
            next('/app/admin')
        } else if (!authStore.canViewAdmin) {
            // If not admin, redirect to timeline
            next('/app/timeline')
        } else {
            next()
        }
    } catch (error) {
        console.log('Admin check failed, redirecting to login')
        authStore.clearUser()
        next('/login')
    }
}

const routes = [
    {
        path: '/app',
        component: MainAppPage,
        children: [
            {path: '', redirect: '/app/timeline'},
            {path: 'timeline', component: TimelinePage, meta: {title: 'Timeline', titleKey: 'nav.items.timeline'}},
            {path: 'timeline-reports', component: TimelineReportsPage, meta: {title: 'Timeline Reports', titleKey: 'timeline.reports.page.title'}},
            {path: 'dashboard', component: DashboardPage, meta: {title: 'Dashboard', titleKey: 'nav.items.dashboard'}},
        ],
        beforeEnter: requireAuth,
    },

    {
        path: '/',
        name: 'Home',
        component: Home,
        meta: {title: 'Home', titleKey: 'nav.pageTitles.home'},
        beforeEnter: async (to, from, next) => {
            const authStore = useAuthStore()
            to.meta.homeResolvedAuthStatus = null

            try {
                // Check auth if not already authenticated
                if (!authStore.user) {
                    await authStore.checkAuth()
                }

                // If authenticated, redirect to the user's landing page or the app default
                if (authStore.isAuthenticated) {
                    next(authStore.defaultRedirectUrl || '/app/timeline')
                    return
                }

                // For guests, optionally redirect from / to /login based on global auth setting
                if (!authStore.isAuthenticated) {
                    const authStatus = await authStore.getAuthStatus()
                    to.meta.homeResolvedAuthStatus = authStatus || null
                    if (authStatus?.guestRootRedirectToLoginEnabled) {
                        next('/login')
                        return
                    }
                }

                // Show home page for guests by default
                next()
            } catch (error) {
                // On error, show home page
                next()
            }
        }
    },
    {
        path: '/login',
        name: 'Login',
        component: LoginPage,
        meta: {title: 'Welcome Back', titleKey: 'auth.login.title'},
        beforeEnter: requireGuest
    },
    {
        path: '/register',
        name: 'Register',
        component: RegisterPage,
        meta: {title: 'Create Account', titleKey: 'auth.register.title'},
        beforeEnter: requireGuest
    },
    {
        path: '/register/invite/:token',
        name: 'Invitation Register',
        component: () => import('@/views/InvitationRegisterPage.vue'),
        meta: {title: 'Complete your registration', titleKey: 'auth.invitation.title'}
    },
    {
        path: '/oidc/callback',
        name: 'OidcCallback',
        component: () => import('@/views/OidcCallback.vue'),
        meta: {title: 'Completing authentication...', titleKey: 'auth.callback.processingTitle'}
    },
    {
        path: '/app/mobile',
        name: 'Mobile',
        component: () => import('@/views/MobilePage.vue'),
        meta: {title: 'Mobile', titleKey: 'ui.mobileAuth.title'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/friends/:tab?',
        name: 'Friends',
        component: FriendsPage,
        meta: {title: 'Friends', titleKey: 'nav.items.friends'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/profile',
        name: 'User Profile',
        component: UserProfilePage,
        meta: {title: 'Profile', titleKey: 'nav.items.profile'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/notifications',
        name: 'Notifications',
        component: () => import('@/views/app/NotificationsPage.vue'),
        meta: {title: 'Notifications', titleKey: 'nav.items.notifications'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/location-sources',
        name: 'Location Sources',
        component: LocationSourcesPage,
        meta: {title: 'Location Sources', titleKey: 'nav.items.location-sources'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/timeline/preferences',
        name: 'Timeline Preferences',
        component: TimelinePreferencesPage,
        meta: {title: 'Timeline Preferences', titleKey: 'nav.items.preferences'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/timeline/jobs',
        name: 'Timeline Jobs',
        component: () => import('@/views/app/TimelineJobsListPage.vue'),
        meta: {title: 'Timeline Generation Jobs', titleKey: 'timelineJobs.listPage.title'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/timeline/jobs/:jobId',
        name: 'Timeline Job Details',
        component: () => import('@/views/app/TimelineJobDetailsPage.vue'),
        meta: {title: 'Timeline Generation Progress', titleKey: 'timelineJobs.detailsPage.title'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/share-links',
        name: 'Share Links',
        component: ShareLinksPage,
        meta: {title: 'Share Links', titleKey: 'nav.items.share-links'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/data-export-import',
        name: 'Data Export & Import',
        component: DataExportImportPage,
        meta: {title: 'Data Export & Import', titleKey: 'data.exportImportPage.pageTitle'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/debug-export',
        name: 'Debug Export',
        component: DebugExportPage,
        meta: {title: 'Debug Data Export', titleKey: 'data.debugExport.pageTitle'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/debug-import',
        name: 'Debug Import',
        component: DebugImportPage,
        meta: {title: 'Import Debug Data', titleKey: 'data.debugImport.pageTitle'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/help',
        name: 'Help & Support',
        component: HelpPage,
        meta: {title: 'Help & Support', titleKey: 'nav.items.help'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/journey-insights',
        name: 'Journey Insights',
        component: JourneyInsights,
        meta: {title: 'Journey Insights', titleKey: 'nav.items.journey-insights'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/rewind',
        name: 'Rewind',
        component: TimeDigestPage,
        meta: {title: 'Rewind', titleKey: 'nav.items.rewind'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/gps-data',
        name: 'GPS Data',
        component: TechnicalDataPage,
        meta: {title: 'GPS Data', titleKey: 'nav.items.gps-data'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/geocoding-management',
        name: 'Geocoding Management',
        component: GeocodingManagementPage,
        meta: {title: 'Reverse Geocoding Management', titleKey: 'geocoding.page.title'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/favorites-management',
        name: 'Favorites Management',
        component: FavoritesManagementPage,
        meta: {title: 'Favorites', titleKey: 'nav.items.favorites-management'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/geofences',
        name: 'Geofences',
        component: GeofencesPage,
        meta: {title: 'Geofences', titleKey: 'nav.items.geofences'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/timeline-labels',
        name: 'Timeline Labels',
        component: TimelineLabelsManagementPage,
        meta: {title: 'Timeline Labels', titleKey: 'nav.items.timeline-labels'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/trips',
        name: 'Trip Plans',
        component: TripsManagementPage,
        meta: {title: 'Trip Plans', titleKey: 'nav.items.trips'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/trips/:tripId',
        name: 'Trip Planner',
        component: TripWorkspacePage,
        meta: {title: 'Trip Planner', titleKey: 'trips.workspacePage.defaultTitle'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/ai/chat',
        name: 'AI Assistant',
        component: AIChatPage,
        meta: {title: 'AI Assistant', titleKey: 'nav.items.ai-chat'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/place-details/:type/:id',
        name: 'Place Details',
        component: PlaceDetailsPage,
        meta: {title: 'Place Details', titleKey: 'place.detailsPage.pageTitleFallback'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/location-analytics',
        name: 'Location Analytics',
        component: () => import('@/views/app/LocationAnalyticsPage.vue'),
        meta: {title: 'Location Analytics', titleKey: 'nav.items.location-analytics'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/coverage',
        name: 'Coverage Explorer',
        component: CoverageExplorerPage,
        meta: {title: 'Coverage Explorer', titleKey: 'nav.items.coverage-explorer'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/location-analytics/city/:name',
        name: 'City Details',
        component: () => import('@/views/app/CityDetailsPage.vue'),
        meta: {title: 'City Details', titleKey: 'nav.pageTitles.cityDetails'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/location-analytics/country/:name',
        name: 'Country Details',
        component: () => import('@/views/app/CountryDetailsPage.vue'),
        meta: {title: 'Country Details', titleKey: 'nav.pageTitles.countryDetails'},
        beforeEnter: requireAuth
    },
    {
        path: '/shared/:linkId',
        name: 'Shared Location',
        component: SharedLocationPage,
        meta: {title: 'Shared Location', titleKey: 'nav.pageTitles.sharedLocation'}
    },
    {
        path: '/shared-timeline/:linkId',
        name: 'Shared Timeline',
        component: SharedTimelinePage,
        meta: {title: 'Shared Timeline', titleKey: 'nav.pageTitles.sharedTimeline'}
    },
    {
        path: '/error',
        name: 'Error',
        component: ErrorPage,
        meta: {title: 'Error', titleKey: 'nav.pageTitles.error'},
        props: route => ({
          errorType: route.query.type || 'generic',
          title: route.query.title,
          message: route.query.message
        })
    },
    // Admin routes
    {
        path: '/app/admin',
        name: 'Admin Dashboard',
        component: () => import('@/views/app/admin/AdminDashboardPage.vue'),
        meta: {title: 'Overview', titleKey: 'nav.items.admin-dashboard'},
        beforeEnter: requireAdmin
    },
    {
        path: '/app/admin/settings',
        name: 'Admin Settings',
        component: () => import('@/views/app/admin/AdminSettingsPage.vue'),
        meta: {title: 'System Settings', titleKey: 'nav.items.admin-settings'},
        beforeEnter: requireAdmin
    },
    {
        path: '/app/admin/backups',
        name: 'Admin Backups',
        component: () => import('@/views/app/admin/AdminBackupsPage.vue'),
        meta: {title: 'Backups & Restore', titleKey: 'nav.items.admin-backups'},
        beforeEnter: requireAdmin
    },
    {
        path: '/app/admin/users',
        name: 'Admin Users',
        component: () => import('@/views/app/admin/AdminUsersPage.vue'),
        meta: {title: 'Manage Users', titleKey: 'nav.items.admin-users'},
        beforeEnter: requireAdmin
    },
    {
        path: '/app/admin/users/:id',
        name: 'Admin User Details',
        component: () => import('@/views/app/admin/AdminUserDetailsPage.vue'),
        meta: {title: 'User Details', titleKey: 'adminCampaignsAndUsers.userDetailsPage.title'},
        beforeEnter: requireAdmin
    },
    {
        path: '/app/admin/invitations',
        name: 'Admin Invitations',
        component: () => import('@/views/app/admin/AdminInvitationsPage.vue'),
        meta: {title: 'Invitations', titleKey: 'nav.items.admin-invitations'},
        beforeEnter: requireAdmin
    },
    {
        path: '/app/admin/oidc-providers',
        name: 'Admin OIDC Providers',
        component: () => import('@/views/app/admin/AdminOidcProvidersPage.vue'),
        meta: {title: 'OIDC Providers', titleKey: 'nav.items.admin-oidc-providers'},
        beforeEnter: requireAdmin
    },
    {
        path: '/app/admin/audit-logs',
        name: 'Admin Audit Logs',
        component: () => import('@/views/app/admin/AdminAuditLogsPage.vue'),
        meta: {title: 'Audit Logs', titleKey: 'nav.items.admin-audit-logs', requiresRealAdmin: true},
        beforeEnter: requireAdmin
    },
    {
        path: '/app/admin/timeline-regeneration-campaigns',
        name: 'Admin Timeline Regeneration',
        component: () => import('@/views/app/admin/AdminTimelineRegenerationCampaignsPage.vue'),
        meta: {title: 'Timeline Regeneration Campaigns', titleKey: 'nav.items.admin-timeline-regeneration'},
        beforeEnter: requireAdmin
    },
    {
        path: '/:pathMatch(.*)*',
        name: 'NotFound',
        component: NotFoundPage,
        meta: {title: 'Page Not Found', titleKey: 'nav.pageTitles.pageNotFound'}
    }
]

const router = createRouter({
    history: createWebHistory('/'),
    routes
})

// Update document title based on route meta
router.afterEach((to) => {
    applyDocumentTitle(to.meta)
})

router.beforeEach(async () => {
    if (!maintenance.initialized) await refreshMaintenance()
    if (maintenance.blocked || maintenance.unavailable || maintenance.activated) return false
})

export default router

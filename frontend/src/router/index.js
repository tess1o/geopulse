import {createRouter, createWebHistory} from 'vue-router'
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
        component: () => import('@/views/app/MainAppPage.vue'),
        children: [
            {path: '', redirect: '/app/timeline'},
            {path: 'timeline', component: () => import('@/views/app/TimelinePage.vue'), meta: {title: 'Timeline', titleKey: 'nav.items.timeline'}},
            {path: 'timeline-reports', component: () => import('@/views/app/TimelineReportsPage.vue'), meta: {title: 'Timeline Reports', titleKey: 'timeline.reports.page.title'}},
            {path: 'dashboard', component: () => import('@/views/app/DashboardPage.vue'), meta: {title: 'Dashboard', titleKey: 'nav.items.dashboard'}},
        ],
        beforeEnter: requireAuth,
    },

    {
        path: '/',
        name: 'Home',
        component: () => import('@/views/Home.vue'),
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
        component: () => import('@/views/LoginPage.vue'),
        meta: {title: 'Welcome Back', titleKey: 'auth.login.title'},
        beforeEnter: requireGuest
    },
    {
        path: '/register',
        name: 'Register',
        component: () => import('@/views/RegisterPage.vue'),
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
        component: () => import('@/views/app/FriendsPage.vue'),
        meta: {title: 'Friends', titleKey: 'nav.items.friends'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/profile',
        name: 'User Profile',
        component: () => import('@/views/app/UserProfilePage.vue'),
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
        component: () => import('@/views/app/LocationSourcesPage.vue'),
        meta: {title: 'Location Sources', titleKey: 'nav.items.location-sources'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/timeline/preferences',
        name: 'Timeline Preferences',
        component: () => import('@/views/app/TimelinePreferencesPage.vue'),
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
        component: () => import('@/views/app/ShareLinksPage.vue'),
        meta: {title: 'Share Links', titleKey: 'nav.items.share-links'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/data-export-import',
        name: 'Data Export & Import',
        component: () => import('@/views/app/DataExportImportPage.vue'),
        meta: {title: 'Data Export & Import', titleKey: 'data.exportImportPage.pageTitle'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/debug-export',
        name: 'Debug Export',
        component: () => import('@/views/app/DebugExportPage.vue'),
        meta: {title: 'Debug Data Export', titleKey: 'data.debugExport.pageTitle'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/debug-import',
        name: 'Debug Import',
        component: () => import('@/views/app/DebugImportPage.vue'),
        meta: {title: 'Import Debug Data', titleKey: 'data.debugImport.pageTitle'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/help',
        name: 'Help & Support',
        component: () => import('@/views/app/HelpPage.vue'),
        meta: {title: 'Help & Support', titleKey: 'nav.items.help'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/journey-insights',
        name: 'Journey Insights',
        component: () => import('@/views/app/JourneyInsights.vue'),
        meta: {title: 'Journey Insights', titleKey: 'nav.items.journey-insights'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/rewind',
        name: 'Rewind',
        component: () => import('@/views/app/TimeDigestPage.vue'),
        meta: {title: 'Rewind', titleKey: 'nav.items.rewind'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/gps-data',
        name: 'GPS Data',
        component: () => import('@/views/app/TechnicalDataPage.vue'),
        meta: {title: 'GPS Data', titleKey: 'nav.items.gps-data'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/geocoding-management',
        name: 'Geocoding Management',
        component: () => import('@/views/app/GeocodingManagementPage.vue'),
        meta: {title: 'Reverse Geocoding Management', titleKey: 'geocoding.page.title'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/favorites-management',
        name: 'Favorites Management',
        component: () => import('@/views/app/FavoritesManagementPage.vue'),
        meta: {title: 'Favorites', titleKey: 'nav.items.favorites-management'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/geofences',
        name: 'Geofences',
        component: () => import('@/views/app/GeofencesPage.vue'),
        meta: {title: 'Geofences', titleKey: 'nav.items.geofences'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/timeline-labels',
        name: 'Timeline Labels',
        component: () => import('@/views/app/TimelineLabelsManagementPage.vue'),
        meta: {title: 'Timeline Labels', titleKey: 'nav.items.timeline-labels'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/trips',
        name: 'Trip Plans',
        component: () => import('@/views/app/TripsManagementPage.vue'),
        meta: {title: 'Trip Plans', titleKey: 'nav.items.trips'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/trips/:tripId',
        name: 'Trip Planner',
        component: () => import('@/views/app/TripWorkspacePage.vue'),
        meta: {title: 'Trip Planner', titleKey: 'trips.workspacePage.defaultTitle'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/ai/chat',
        name: 'AI Assistant',
        component: () => import('@/views/app/AIChatPage.vue'),
        meta: {title: 'AI Assistant', titleKey: 'nav.items.ai-chat'},
        beforeEnter: requireAuth
    },
    {
        path: '/app/place-details/:type/:id',
        name: 'Place Details',
        component: () => import('@/views/app/PlaceDetailsPage.vue'),
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
        component: () => import('@/views/app/CoverageExplorerPage.vue'),
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
        component: () => import('@/views/SharedLocationPage.vue'),
        meta: {title: 'Shared Location', titleKey: 'nav.pageTitles.sharedLocation'}
    },
    {
        path: '/shared-timeline/:linkId',
        name: 'Shared Timeline',
        component: () => import('@/views/SharedTimelinePage.vue'),
        meta: {title: 'Shared Timeline', titleKey: 'nav.pageTitles.sharedTimeline'}
    },
    {
        path: '/error',
        name: 'Error',
        component: () => import('@/views/ErrorPage.vue'),
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
        component: () => import('@/views/NotFoundPage.vue'),
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

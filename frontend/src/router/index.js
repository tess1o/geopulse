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
import TimelineReportsPage from "@/views/app/TimelineReportsPage.vue";
import TechnicalDataPage from "@/views/app/TechnicalDataPage.vue";
import GeocodingManagementPage from "@/views/app/GeocodingManagementPage.vue";
import FavoritesManagementPage from "@/views/app/FavoritesManagementPage.vue";
import AIChatPage from "@/views/app/AIChatPage.vue";
import TimeDigestPage from "@/views/app/TimeDigestPage.vue";
import PlaceDetailsPage from "@/views/app/PlaceDetailsPage.vue";
import SharedLocationPage from "@/views/SharedLocationPage.vue";
import SharedTimelinePage from "@/views/SharedTimelinePage.vue";
import ErrorPage from "@/views/ErrorPage.vue";
import NotFoundPage from "@/views/NotFoundPage.vue";
import { useAuthStore } from '@/stores/auth'

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

// Admin guard function
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
        } else if (!authStore.isAdmin) {
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
        component: MainAppPage, // New layout with AppLayout and new components
        children: [
            {path: '', redirect: '/app/timeline'},
            {path: 'timeline', component: TimelinePage},
            {path: 'timeline-reports', component: TimelineReportsPage},
            {path: 'dashboard', component: DashboardPage},
        ],
        beforeEnter: requireAuth,
    },

    {
        path: '/',
        name: 'Home',
        component: Home,
        beforeEnter: async (to, from, next) => {
            const authStore = useAuthStore()

            try {
                // Check auth if not already authenticated
                if (!authStore.user) {
                    await authStore.checkAuth()
                }

                // If authenticated and has default redirect URL, redirect there
                if (authStore.isAuthenticated && authStore.defaultRedirectUrl) {
                    next(authStore.defaultRedirectUrl)
                } else {
                    // Show home page for non-authenticated users or authenticated users without redirect URL
                    next()
                }
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
        beforeEnter: requireGuest
    },
    {
        path: '/register',
        name: 'Register',
        component: RegisterPage,
        beforeEnter: requireGuest
    },
    {
        path: '/register/invite/:token',
        name: 'Invitation Register',
        component: () => import('@/views/InvitationRegisterPage.vue')
    },
    {
        path: '/oidc/callback',
        name: 'OidcCallback',
        component: () => import('@/views/OidcCallback.vue')
    },
    {
        path: '/app/friends/:tab?',
        name: 'Friends',
        component: FriendsPage,
        beforeEnter: requireAuth
    },
    {
        path: '/app/profile',
        name: 'User Profile',
        component: UserProfilePage,
        beforeEnter: requireAuth
    },
    {
        path: '/app/location-sources',
        name: 'Location Sources',
        component: LocationSourcesPage,
        beforeEnter: requireAuth
    },
    {
        path: '/app/timeline/preferences',
        name: 'Timeline Preferences',
        component: TimelinePreferencesPage,
        beforeEnter: requireAuth
    },
    {
        path: '/app/timeline/jobs',
        name: 'Timeline Jobs',
        component: () => import('@/views/app/TimelineJobsListPage.vue'),
        beforeEnter: requireAuth
    },
    {
        path: '/app/timeline/jobs/:jobId',
        name: 'Timeline Job Details',
        component: () => import('@/views/app/TimelineJobDetailsPage.vue'),
        beforeEnter: requireAuth
    },
    {
        path: '/app/share-links',
        name: 'Share Links',
        component: ShareLinksPage,
        beforeEnter: requireAuth
    },
    {
        path: '/app/data-export-import',
        name: 'Data Export & Import',
        component: DataExportImportPage,
        beforeEnter: requireAuth
    },
    {
        path: '/app/debug-export',
        name: 'Debug Export',
        component: DebugExportPage,
        beforeEnter: requireAuth
    },
    {
        path: '/app/debug-import',
        name: 'Debug Import',
        component: DebugImportPage,
        beforeEnter: requireAuth
    },
    {
        path: '/app/journey-insights',
        name: 'Journey Insights',
        component: JourneyInsights,
        beforeEnter: requireAuth
    },
    {
        path: '/app/rewind',
        name: 'Rewind',
        component: TimeDigestPage,
        beforeEnter: requireAuth
    },
    {
        path: '/app/gps-data',
        name: 'GPS Data',
        component: TechnicalDataPage,
        beforeEnter: requireAuth
    },
    {
        path: '/app/geocoding-management',
        name: 'Geocoding Management',
        component: GeocodingManagementPage,
        beforeEnter: requireAuth
    },
    {
        path: '/app/favorites-management',
        name: 'Favorites Management',
        component: FavoritesManagementPage,
        beforeEnter: requireAuth
    },
    {
        path: '/app/ai/chat',
        name: 'AI Assistant',
        component: AIChatPage,
        beforeEnter: requireAuth
    },
    {
        path: '/app/place-details/:type/:id',
        name: 'Place Details',
        component: PlaceDetailsPage,
        beforeEnter: requireAuth
    },
    {
        path: '/shared/:linkId',
        name: 'Shared Location',
        component: SharedLocationPage
    },
    {
        path: '/shared-timeline/:linkId',
        name: 'Shared Timeline',
        component: SharedTimelinePage
    },
    {
        path: '/error',
        name: 'Error',
        component: ErrorPage,
        props: route => ({
          errorType: route.query.type || 'generic',
          title: route.query.title,
          message: route.query.message,
          details: route.query.details
        })
    },
    // Admin routes
    {
        path: '/app/admin',
        name: 'Admin Dashboard',
        component: () => import('@/views/app/admin/AdminDashboardPage.vue'),
        beforeEnter: requireAdmin
    },
    {
        path: '/app/admin/settings',
        name: 'Admin Settings',
        component: () => import('@/views/app/admin/AdminSettingsPage.vue'),
        beforeEnter: requireAdmin
    },
    {
        path: '/app/admin/users',
        name: 'Admin Users',
        component: () => import('@/views/app/admin/AdminUsersPage.vue'),
        beforeEnter: requireAdmin
    },
    {
        path: '/app/admin/users/:id',
        name: 'Admin User Details',
        component: () => import('@/views/app/admin/AdminUserDetailsPage.vue'),
        beforeEnter: requireAdmin
    },
    {
        path: '/app/admin/invitations',
        name: 'Admin Invitations',
        component: () => import('@/views/app/admin/AdminInvitationsPage.vue'),
        beforeEnter: requireAdmin
    },
    {
        path: '/app/admin/oidc-providers',
        name: 'Admin OIDC Providers',
        component: () => import('@/views/app/admin/AdminOidcProvidersPage.vue'),
        beforeEnter: requireAdmin
    },
    {
        path: '/app/admin/audit-logs',
        name: 'Admin Audit Logs',
        component: () => import('@/views/app/admin/AdminAuditLogsPage.vue'),
        beforeEnter: requireAdmin
    },
    {
        path: '/:pathMatch(.*)*',
        name: 'NotFound',
        component: NotFoundPage
    }
]

const router = createRouter({
    history: createWebHistory('/'),
    routes
})

export default router
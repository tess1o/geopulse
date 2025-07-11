import {createRouter, createWebHistory} from 'vue-router'
import LoginPage from '../views/LoginPage.vue'
import RegisterPage from '../views/RegisterPage.vue'
import Home from "@/views/Home.vue";
import FriendsPage from "@/views/app/FriendsPage.vue";
import StatsPage from "@/views/app/StatsPage.vue";
import MainAppPage from "@/views/app/MainAppPage.vue";
import TimelinePage from "@/views/app/TimelinePage.vue";
import PlacesPage from "@/views/app/PlacesPage.vue";
import DashboardPage from "@/views/app/DashboardPage.vue";
import JourneyInsights from "@/views/app/JourneyInsights.vue";
import LocationSourcesPage from "@/views/app/LocationSourcesPage.vue";
import TimelinePreferencesPage from "@/views/app/TimelinePreferencesPage.vue";
import UserProfilePage from "@/views/app/UserProfilePage.vue";
import ShareLinksPage from "@/views/app/ShareLinksPage.vue";
import DataExportImportPage from "@/views/app/DataExportImportPage.vue";
import SharedLocationPage from "@/views/SharedLocationPage.vue";
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
const requireGuest = (to, from, next) => {
    const authData = localStorage.getItem('authData')
    if (authData) {
        next('/app/timeline')
    } else {
        next()
    }
}

const routes = [
    {
        path: '/app',
        component: MainAppPage, // New layout with AppLayout and new components
        children: [
            {path: '', redirect: '/app/timeline'},
            {path: 'timeline', component: TimelinePage},
            {path: 'dashboard', component: DashboardPage},
            {path: 'places', component: PlacesPage},
            {path: 'stats', component: StatsPage},
        ],
        beforeEnter: requireAuth,
    },

    {
        path: '/',
        name: 'Home',
        component: Home,
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
        path: '/app/friends',
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
        path: '/app/journey-insights',
        name: 'Journey Insights',
        component: JourneyInsights,
        beforeEnter: requireAuth
    },
    {
        path: '/shared/:linkId',
        name: 'Shared Location',
        component: SharedLocationPage
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
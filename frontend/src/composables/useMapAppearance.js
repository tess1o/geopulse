import { computed, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { readCachedUserProfile } from '@/utils/userProfileCache'
import {
  MAP_COLOR_SCHEMES,
  SHARED_APPEARANCE_CHOICES,
  isAppearanceCustomized,
  resolveMapAppearance,
  resolveSharedAppearanceChoice,
  sharedAppearancePreferences
} from '@/maps/shared/mapAppearance'

const SHARED_CHOICE_STORAGE_KEY = 'gp-shared-map-colors'

const readStoredChoice = () => {
  try {
    return window.localStorage.getItem(SHARED_CHOICE_STORAGE_KEY) || null
  } catch {
    return null
  }
}

// Set while a shared link is open: the link owner's appearance preferences, and its owner's display name.
const sharedOwner = ref(null)
// The viewer's pick on shared pages, remembered across links in this browser.
const storedSharedChoice = ref(readStoredChoice())
// Shared pages are public routes and never load the session, so a signed-in viewer is recognized from
// the cached profile. It is only used to offer "My settings"; no request is made on their behalf.
const cachedViewer = ref(null)

const readCachedViewer = () => {
  const profile = readCachedUserProfile()
  return profile?.id ? profile : null
}

const resolveViewerPrefs = (authStore) => authStore.user || cachedViewer.value || null

/**
 * The resolved map appearance (path colors, width, outline, speed bands, heatmap) every map renders with.
 * Normally the signed-in user's own preferences. On a shared link it is the viewer's pick between the
 * owner's look, their own profile and the presets (see useSharedMapAppearance).
 */
export function useMapAppearance() {
  const authStore = useAuthStore()
  return computed(() => {
    if (!sharedOwner.value) {
      return resolveMapAppearance(authStore.user || {})
    }

    const context = { ownerPrefs: sharedOwner.value.preferences, viewerPrefs: resolveViewerPrefs(authStore) }
    const choice = resolveSharedAppearanceChoice({ storedChoice: storedSharedChoice.value, ...context })
    return resolveMapAppearance(sharedAppearancePreferences(choice, context))
  })
}

/** Shared-link pages: publish the owner's appearance and let the viewer choose what to see. */
export function useSharedMapAppearance() {
  const authStore = useAuthStore()
  const viewerPrefs = computed(() => resolveViewerPrefs(authStore))
  const ownerPrefs = computed(() => sharedOwner.value?.preferences || null)

  const choice = computed(() => resolveSharedAppearanceChoice({
    storedChoice: storedSharedChoice.value,
    ownerPrefs: ownerPrefs.value,
    viewerPrefs: viewerPrefs.value
  }))

  // The owner row is pointless when it would look exactly like "Default".
  const options = computed(() => [
    ...(isAppearanceCustomized(ownerPrefs.value) ? [SHARED_APPEARANCE_CHOICES.OWNER] : []),
    ...(viewerPrefs.value ? [SHARED_APPEARANCE_CHOICES.MINE] : []),
    ...MAP_COLOR_SCHEMES
  ])

  const appearanceFor = (option) => resolveMapAppearance(sharedAppearancePreferences(option, {
    ownerPrefs: ownerPrefs.value,
    viewerPrefs: viewerPrefs.value
  }))

  const setChoice = (nextChoice) => {
    storedSharedChoice.value = nextChoice
    try {
      window.localStorage.setItem(SHARED_CHOICE_STORAGE_KEY, nextChoice)
    } catch {
      // Private mode or blocked storage: the choice still applies for this visit.
    }
  }

  const setSharedOwner = (preferences, name = '') => {
    cachedViewer.value = readCachedViewer()
    sharedOwner.value = { preferences: preferences || {}, name }
  }

  const clearSharedOwner = () => {
    sharedOwner.value = null
    cachedViewer.value = null
  }

  return {
    choice,
    options,
    ownerName: computed(() => sharedOwner.value?.name || ''),
    appearanceFor,
    setChoice,
    setSharedOwner,
    clearSharedOwner
  }
}

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useFavoritesStore } from './favorites'
import apiService from '@/utils/apiService'

vi.mock('@/utils/apiService', () => ({ default: { post: vi.fn(), get: vi.fn() } }))

const point = { id: 1, name: 'Home', lat: 50, lon: 30, type: 'POINT' }
const area = { id: 2, name: 'Office', type: 'AREA', northEastLat: 51, northEastLon: 31, southWestLat: 50, southWestLon: 30 }
let store
beforeEach(() => {
    vi.resetAllMocks()
    setActivePinia(createPinia())
    store = useFavoritesStore()
    store.setFavoritePlaces({ points: [point], areas: [area] })
})

describe('queued favorite changes', () => {
    it('previews repeated edits without changing saved favorites or calling the server', () => {
        store.queueUpdate({ ...point, name: 'First' })
        store.queueUpdate({ ...point, name: 'Second' })
        expect(store.previewFavorites[0].name).toBe('Second')
        expect(store.getFavoritePoints[0].name).toBe('Home')
        expect(store.pendingCount).toBe(1)
        expect(apiService.post).not.toHaveBeenCalled()
    })

    it('replaces an edit with a deletion and restores the edit on undo', () => {
        store.queueUpdate({ ...area, northEastLat: 52 })
        store.queueDelete(store.previewFavorites.find(item => item.id === 2))
        expect(store.previewFavorites).toHaveLength(1)
        expect(store.getAllPending).toMatchObject([{ operation: 'delete' }])
        store.removeFromPending('delete-2')
        expect(store.previewFavorites[1].northEastLat).toBe(52)
        expect(store.getAllPending).toMatchObject([{ operation: 'update' }])
        store.removeFromPending('update-2')
        expect(store.previewFavorites[1].northEastLat).toBe(51)
    })

    it('merges selected-row metadata edits with pending name and boundary edits', () => {
        store.queueUpdate({ ...area, name: 'New Office', northEastLat: 52 })
        store.queueBulkUpdate([1, 2], true, 'Kyiv', false, null)
        expect(store.previewFavorites[1]).toMatchObject({ name: 'New Office', northEastLat: 52, city: 'Kyiv' })
        expect(store.pendingCount).toBe(2)
        expect(apiService.post).not.toHaveBeenCalled()
    })

    it('discards additions, edits and deletions together', () => {
        store.addPointToPending('New', 50, 30)
        store.queueUpdate({ ...point, name: 'Changed' })
        store.queueDelete(area)
        store.clearPending()
        expect(store.pendingCount).toBe(0)
        expect(store.hasPendingFavorites).toBe(false)
        expect(store.previewFavorites).toEqual([point, area])
    })

    it('submits a mixed batch once and clears it without a fallible refresh', async () => {
        store.addPointToPending('New', 50, 30)
        store.queueUpdate({ ...point, name: 'Changed' })
        store.queueDelete(area)
        apiService.post.mockResolvedValue({ jobId: 'job' })
        expect(await store.applyPendingChanges()).toEqual({ jobId: 'job' })
        expect(apiService.post).toHaveBeenCalledExactlyOnceWith('/favorites/bulk-apply', {
            points: [{ name: 'New', lat: 50, lon: 30 }], areas: [],
            updates: [{ favoriteId: 1, changes: { name: 'Changed', city: undefined, country: undefined } }],
            deleteIds: [2]
        })
        expect(store.pendingCount).toBe(0)
        expect(apiService.get).not.toHaveBeenCalled()
    })

    it('supports deletion-only batches and excludes superseded edits', async () => {
        store.queueUpdate({ ...point, name: 'Changed' })
        store.queueDelete(point)
        apiService.post.mockResolvedValue({ jobId: null })
        await store.applyPendingChanges()
        expect(apiService.post).toHaveBeenCalledWith('/favorites/bulk-apply', {
            points: [], areas: [], updates: [], deleteIds: [1]
        })
    })

    it('preserves the whole queue when the request fails', async () => {
        store.addAreaToPending('New', 51, 31, 50, 30)
        store.queueUpdate({ ...point, name: 'Changed' })
        store.queueDelete(area)
        apiService.post.mockRejectedValue(new Error('Invalid change'))
        await expect(store.applyPendingChanges()).rejects.toBeDefined()
        expect(store.pendingCount).toBe(3)
        expect(store.hasPendingFavorites).toBe(true)
    })
})

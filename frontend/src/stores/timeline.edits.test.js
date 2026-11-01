import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import apiService from '@/utils/apiService'
import { useTimelineStore } from './timeline'

vi.mock('@/utils/apiService', () => ({ default: { get: vi.fn(), post: vi.fn() } }))

const edit = { kind: 'SET_VISIT_NAME', anchorStart: '2026-10-01T10:00:00Z', anchorEnd: '2026-10-01T11:00:00Z', payload: { visitName: 'Home' } }
const actions = [
  ['fetchTimelineEdits', 'get', ['/timeline/edits'], [], 'Failed to load timeline corrections'],
  ['previewTimelineEdit', 'post', ['/timeline/edits/preview', edit], [edit], 'Failed to preview timeline correction'],
  ['createTimelineEdit', 'post', ['/timeline/edits', edit], [edit], 'Failed to save timeline correction'],
  ['revertTimelineEdit', 'post', ['/timeline/edits/42/revert'], [42], 'Failed to revert timeline correction'],
  ['restoreTimelineEdit', 'post', ['/timeline/edits/42/restore'], [42], 'Failed to restore timeline correction']
]

beforeEach(() => { setActivePinia(createPinia()); vi.resetAllMocks() })

describe.each(actions)('%s', (action, method, request, args, fallback) => {
  it('sends the exact request and returns the response', async () => {
    const store = useTimelineStore()
    const response = action === 'fetchTimelineEdits' ? [{ id: 42 }] : { edit: { id: 42 }, timelineJobId: 'job' }
    apiService[method].mockResolvedValue(response)
    expect(await store[action](...args)).toBe(response)
    expect(apiService[method].mock.calls).toEqual([request])
    expect(store.error).toBeNull()
  })

  it('stores and rejects with the normalized API problem', async () => {
    const store = useTimelineStore()
    apiService[method].mockRejectedValue({ response: { status: 400, data: { code: 'INVALID_TIMELINE_REQUEST', detail: 'Invalid interval', errorId: 'problem-42' } } })
    const error = await store[action](...args).catch(error => error)
    expect(error).toEqual({ isApiError: true, status: 400, code: 'INVALID_TIMELINE_REQUEST', detail: 'Invalid interval', errorId: 'problem-42', parameters: {}, violations: [] })
    expect(store.error).toBe(error)
  })

  it('uses the action fallback when the failure has no detail', async () => {
    const store = useTimelineStore()
    apiService[method].mockRejectedValue({})
    await expect(store[action](...args)).rejects.toMatchObject({ isApiError: true, detail: fallback })
    expect(store.error.detail).toBe(fallback)
  })
})

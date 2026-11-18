import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import apiService from '@/utils/apiService'
import { useTimelineStore } from './timeline'

vi.mock('@/utils/apiService', () => ({ default: { get: vi.fn(), post: vi.fn() } }))

const correction = { op: 'SET_LABEL', anchorStart: '2026-10-01T10:00:00Z', anchorEnd: '2026-10-01T11:00:00Z', payload: { visitName: 'Home' } }
const actions = [
  ['fetchCorrections', 'get', ['/timeline/corrections', {}], [], 'Failed to load timeline corrections'],
  ['previewCorrections', 'post', ['/timeline/corrections/preview', correction], [correction], 'Failed to preview timeline corrections'],
  ['saveCorrections', 'post', ['/timeline/corrections', correction, { redirectOnServerError: false }], [correction], 'Failed to save timeline corrections'],
  ['undoCorrection', 'post', ['/timeline/corrections/group-1/undo', {}, { redirectOnServerError: false }], ['group-1'], 'Failed to undo timeline correction'],
  ['undoCorrection', 'post', ['/timeline/corrections/group-1/undo?cascade=true', {}, { redirectOnServerError: false }], ['group-1', true], 'Failed to undo timeline correction'],
  ['restoreCorrection', 'post', ['/timeline/corrections/group-1/restore', {}, { redirectOnServerError: false }], ['group-1'], 'Failed to restore timeline correction']
]

beforeEach(() => { setActivePinia(createPinia()); vi.resetAllMocks() })

it('orders a zero-duration connector before the stay starting at the same instant', async () => {
  const store = useTimelineStore()
  apiService.get.mockResolvedValue({
    stays: [{ id: 1, timestamp: correction.anchorStart, stayDuration: 3600 }],
    trips: [{ id: 2, timestamp: correction.anchorStart, tripDuration: 0 }]
  })
  await store.fetchMovementTimeline(correction.anchorStart, correction.anchorEnd)
  expect(store.timelineData.map(item => item.id)).toEqual([2, 1])
})

describe.each(actions)('%s', (action, method, request, args, fallback) => {
  it('sends the exact request and returns the response', async () => {
    const store = useTimelineStore()
    const response = action === 'fetchCorrections' ? [{ id: 42 }] : { groupId: 'group-1', edits: [{ id: 42 }], timelineJobId: 'job' }
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

it('saves movement-type edits through a token-protected correction group', async () => {
  const store = useTimelineStore()
  const trip = { id: 7, timestamp: correction.anchorStart, tripDuration: 3600 }
  apiService.post.mockResolvedValueOnce({ token: 'reviewed' }).mockResolvedValueOnce({ groupId: 'type-group', timelineJobId: 'job' })
  const result = await store.updateTripMovementType(trip, 'TRAIN')
  const request = { op: 'SET_TRIP_TYPE', anchorStart: correction.anchorStart, anchorEnd: new Date(correction.anchorEnd).toISOString(), payload: { movementType: 'TRAIN' } }
  expect(apiService.post.mock.calls).toEqual([
    ['/timeline/corrections/preview', request], ['/timeline/corrections', { ...request, previewToken: 'reviewed' }, { redirectOnServerError: false }]
  ])
  expect(result).toMatchObject({ groupId: 'type-group', timelineJobId: 'job', tripId: 7, movementType: 'TRAIN' })
})

it('resets only the current trip-type correction and returns its regeneration job', async () => {
  const store = useTimelineStore()
  apiService.get.mockResolvedValue([
    { op: 'SET_TRIP_TYPE', status: 'IN_EFFECT', groupId: 'other', target: { type: 'TRIP', id: 99 } },
    { op: 'SET_TRIP_TYPE', status: 'IN_EFFECT', groupId: 'mine', target: { type: 'TRIP', id: 7 } }
  ])
  apiService.post.mockResolvedValue({ groupId: 'mine', timelineJobId: 'reset-job' })
  expect(await store.resetTripMovementType({ id: 7 })).toMatchObject({ groupId: 'mine', timelineJobId: 'reset-job', operation: 'undo' })
  expect(apiService.post.mock.calls).toEqual([['/timeline/corrections/mine/undo', {}, { redirectOnServerError: false }]])
})

it('resets a trip-type correction whose secondary effect is the trip', async () => {
  const store = useTimelineStore()
  apiService.get.mockResolvedValue([
    { op: 'SET_TRIP_TYPE', status: 'IN_EFFECT', groupId: 'split', target: { type: 'TRIP', id: 5 }, effects: [{ type: 'TRIP', id: 5 }, { type: 'TRIP', id: 7 }] }
  ])
  apiService.post.mockResolvedValue({ groupId: 'split', timelineJobId: 'reset-job' })
  expect(await store.resetTripMovementType({ id: 7 })).toMatchObject({ groupId: 'split', operation: 'undo' })
})

it('does not undo an entire fill to reset its embedded movement type', async () => {
  const store = useTimelineStore()
  apiService.get.mockResolvedValue([{ op: 'PLACE_TRIP', status: 'IN_EFFECT', groupId: 'fill', target: { type: 'TRIP', id: 7 } }])
  await expect(store.resetTripMovementType({ id: 7 })).rejects.toThrow('no trip-type correction')
  expect(apiService.post).not.toHaveBeenCalled()
})

it('keeps a lost correction response local so the editor can retain its draft', async () => {
  apiService.post.mockRejectedValue(new Error('Network Error'))
  const store = useTimelineStore()
  await expect(store.saveCorrections(correction)).rejects.toMatchObject({ detail: 'Network Error' })
  expect(apiService.post).toHaveBeenCalledWith('/timeline/corrections', correction, { redirectOnServerError: false })
})

import { describe, expect, it } from 'vitest'
import { useTripReconstructionSegments } from '@/composables/useTripReconstructionSegments'

const CONTEXT_START = new Date('2026-10-24T08:00:00Z')
const THIRTY_MINUTES_MS = 30 * 60 * 1000

const createTimezone = () => ({
  now: () => ({ toDate: () => new Date('2026-10-24T08:00:00Z') }),
  createDateTimeFromPicker: (value) => value
})

const setup = () => {
  const state = useTripReconstructionSegments({
    timezone: createTimezone(),
    getContextRangeDates: () => ({
      start: CONTEXT_START,
      end: new Date('2026-10-24T18:00:00Z')
    })
  })
  state.resetDialogState()
  return state
}

describe('useTripReconstructionSegments end-time ownership', () => {
  it('moves an untouched end time with the start time', () => {
    const { segments, updateSegmentField } = setup()
    const nextStart = new Date('2026-10-24T10:00:00Z')

    updateSegmentField(0, 'startTime', nextStart)

    expect(segments.value[0].endTime.getTime()).toBe(nextStart.getTime() + THIRTY_MINUTES_MS)
  })

  it('keeps a user-set end time when the start time changes', () => {
    const { segments, updateSegmentField } = setup()
    const userEnd = new Date('2026-10-24T11:00:00Z')

    updateSegmentField(0, 'endTime', userEnd)
    updateSegmentField(0, 'startTime', new Date('2026-10-24T09:00:00Z'))

    expect(segments.value[0].endTime.getTime()).toBe(userEnd.getTime())
  })

  it('keeps a user-set end time even when it becomes invalid, and validation reports it', () => {
    const { segments, updateSegmentField, validateSegments } = setup()
    const userEnd = new Date('2026-10-24T09:00:00Z')

    updateSegmentField(0, 'endTime', userEnd)
    updateSegmentField(0, 'startTime', new Date('2026-10-24T10:00:00Z'))

    expect(segments.value[0].endTime.getTime()).toBe(userEnd.getTime())
    expect(validateSegments()).toBe('Segment 1: end time must be after start time.')
  })

  it('does not restore a cleared end time when the start time changes', () => {
    const { segments, updateSegmentField } = setup()

    updateSegmentField(0, 'endTime', null)
    updateSegmentField(0, 'startTime', new Date('2026-10-24T10:00:00Z'))

    expect(segments.value[0].endTime).toBeNull()
  })

  it('starts a fresh segment with an untouched end time', () => {
    const { segments } = setup()

    expect(segments.value[0].endTimeEdited).toBe(false)
  })
})

import { describe, expect, it } from 'vitest'
import { isTimelineJobDialogClosable, normalizeTimelineJobStatus } from './timelineJobDialogState'

describe('timeline job dialog state', () => {
  it('normalizes the job status', () => {
    expect(normalizeTimelineJobStatus({ status: 'running' })).toBe('RUNNING')
    expect(normalizeTimelineJobStatus(null)).toBe('')
  })

  it('stays open while a tracked job is queued or running', () => {
    expect(isTimelineJobDialogClosable({ jobId: 'j1', progress: { status: 'QUEUED' } })).toBe(false)
    expect(isTimelineJobDialogClosable({ jobId: 'j1', progress: { status: 'RUNNING' } })).toBe(false)
    expect(isTimelineJobDialogClosable({ jobId: 'j1', progress: null })).toBe(false)
  })

  it('can close once the job ends, fails to load, or there is no job', () => {
    expect(isTimelineJobDialogClosable({ jobId: 'j1', progress: { status: 'COMPLETED' } })).toBe(true)
    expect(isTimelineJobDialogClosable({ jobId: 'j1', progress: { status: 'FAILED' } })).toBe(true)
    expect(isTimelineJobDialogClosable({ jobId: 'j1', progress: { status: 'RUNNING' }, error: 'lost' })).toBe(true)
    expect(isTimelineJobDialogClosable({ jobId: null, progress: null })).toBe(true)
  })
})

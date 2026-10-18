import { afterEach, describe, expect, it, vi } from 'vitest'
import { pollJob } from './jobPolling'

describe('pollJob', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('polls until the job completes and reports every response', async () => {
    vi.useFakeTimers()
    const responses = [{ status: 'processing', progress: 10 }, { status: 'processing', progress: 60 }, { status: 'completed' }]
    const fetchJob = vi.fn(async () => responses.shift())
    const onUpdate = vi.fn()

    const { promise } = pollJob({ fetchJob, onUpdate, intervalMs: 1000 })
    await vi.runAllTimersAsync()

    await expect(promise).resolves.toEqual({ status: 'completed' })
    expect(fetchJob).toHaveBeenCalledTimes(3)
    expect(onUpdate).toHaveBeenCalledTimes(3)
  })

  it('reads the status where the response keeps it', async () => {
    const { promise } = pollJob({
      fetchJob: async () => ({ job: { status: 'failed' } }),
      statusOf: response => response.job.status
    })
    await expect(promise).resolves.toEqual({ job: { status: 'failed' } })
  })

  it('stops when cancelled', async () => {
    vi.useFakeTimers()
    const fetchJob = vi.fn(async () => ({ status: 'processing' }))

    const polling = pollJob({ fetchJob, intervalMs: 1000 })
    await vi.advanceTimersByTimeAsync(0)
    polling.cancel()
    await vi.advanceTimersByTimeAsync(5000)

    await expect(polling.promise).resolves.toBeNull()
    expect(fetchJob).toHaveBeenCalledTimes(1)
  })

  it('rejects when fetching fails', async () => {
    const { promise } = pollJob({ fetchJob: async () => { throw new Error('offline') } })
    await expect(promise).rejects.toThrow('offline')
  })
})

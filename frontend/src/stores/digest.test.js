import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import apiService from '@/utils/apiService'
import { useDigestStore } from './digest'

vi.mock('@/utils/apiService', () => ({ default: { get: vi.fn() } }))

describe('Rewind replay caching', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
  })

  it.each([null, 3])('caches repeated requests for month %s', async (month) => {
    const store = useDigestStore()
    const replay = { year: 2026, month, trips: [{ id: 1 }] }
    apiService.get.mockResolvedValue(replay)
    const fetch = () => month === null ? store.fetchYearlyReplay(2026) : store.fetchMonthlyReplay(2026, month)

    expect(await fetch()).toEqual(replay)
    expect(await fetch()).toEqual(replay)
    expect(apiService.get).toHaveBeenCalledTimes(1)
    expect(apiService.get).toHaveBeenCalledWith(month === null ? '/digests/yearly-replay' : '/digests/monthly-replay',
      month === null ? { year: 2026 } : { year: 2026, month })
  })

  it.each(['clearCache', '$reset'])('does not refill a replaced cache after %s', async (reset) => {
    const store = useDigestStore()
    let finishOldRequest
    apiService.get.mockImplementationOnce(() => new Promise(resolve => { finishOldRequest = resolve }))
    const oldRequest = store.fetchYearlyReplay(2026)
    store[reset]()
    const currentReplay = { year: 2026, trips: [{ id: 2 }] }
    apiService.get.mockResolvedValueOnce(currentReplay)
    await store.fetchYearlyReplay(2026)

    finishOldRequest({ year: 2026, trips: [{ id: 1 }] })
    await oldRequest

    expect(await store.fetchYearlyReplay(2026)).toEqual(currentReplay)
    expect(apiService.get).toHaveBeenCalledTimes(2)
  })
})

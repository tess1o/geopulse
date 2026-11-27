import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { isReactive } from 'vue'
import apiService from '@/utils/apiService'
import { useCoverageStore } from './coverage'

vi.mock('@/utils/apiService', () => ({ default: { get: vi.fn() } }))

describe('coverage cells response', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
  })

  it('keeps the grid and truncation with frozen, non-reactive tuple cells', async () => {
    const store = useCoverageStore()
    const response = { gridMeters: 250, truncated: true, cells: [[1, 2, 3]] }
    apiService.get.mockResolvedValue(response)

    expect(await store.fetchCoverageCells('1,2,3,4', 250)).toBe(response)
    expect(apiService.get).toHaveBeenCalledWith('/coverage/cells', { bbox: '1,2,3,4', grid: 250 })
    expect(store.cells).toBe(response.cells)
    expect(store.cellsGrid).toBe(250)
    expect(store.cellsTruncated).toBe(true)
    expect(Object.isFrozen(store.cells)).toBe(true)
    expect(isReactive(store.cells)).toBe(false)
    expect(isReactive(store.cells[0])).toBe(false)

    store.clearCells()
    expect(store.cells).toEqual([])
    expect(store.cellsTruncated).toBe(false)
    expect(Object.isFrozen(store.cells)).toBe(true)
  })

  it('does not let an older response replace any part of the current result', async () => {
    const store = useCoverageStore()
    let finishOldRequest
    apiService.get.mockImplementationOnce(() => new Promise(resolve => { finishOldRequest = resolve }))
    const oldRequest = store.fetchCoverageCells('1,2,3,4', 20)
    const current = { gridMeters: 50, truncated: false, cells: [[5, 6, 7]] }
    apiService.get.mockResolvedValueOnce(current)
    await store.fetchCoverageCells('1,2,3,4', 50)
    finishOldRequest({ gridMeters: 20, truncated: true, cells: [[1, 2, 3]] })

    expect(await oldRequest).toBeNull()
    expect(store.cells).toBe(current.cells)
    expect(store.cellsGrid).toBe(50)
    expect(store.cellsTruncated).toBe(false)
  })

  it('returns a response without replacing drawn cells when shouldCommit rejects it', async () => {
    const store = useCoverageStore()
    const current = { gridMeters: 250, truncated: false, cells: [[5, 6, 7]] }
    const intermediate = { gridMeters: 20, truncated: true, cells: [[1, 2, 3]] }
    apiService.get.mockResolvedValueOnce(current).mockResolvedValueOnce(intermediate)
    await store.fetchCoverageCells('1,2,3,4', 250)
    const shouldCommit = vi.fn(() => false)

    expect(await store.fetchCoverageCells('1,2,3,4', 20, { shouldCommit })).toBe(intermediate)
    expect(shouldCommit).toHaveBeenCalledExactlyOnceWith(intermediate)
    expect(store.cells).toBe(current.cells)
    expect(store.cellsGrid).toBe(250)
    expect(store.cellsTruncated).toBe(false)
    expect(store.loadingCells).toBe(false)
  })

  it('does not evaluate a deferred commit after the request has been cancelled', async () => {
    const store = useCoverageStore()
    let finishRequest
    apiService.get.mockImplementationOnce(() => new Promise(resolve => { finishRequest = resolve }))
    const shouldCommit = vi.fn(() => true)
    const request = store.fetchCoverageCells('1,2,3,4', 20, { shouldCommit })
    store.cancelCoverageCellsRequests()
    finishRequest({ gridMeters: 20, truncated: false, cells: [[1, 2, 3]] })

    expect(await request).toBeNull()
    expect(shouldCommit).not.toHaveBeenCalled()
    expect(store.cells).toEqual([])
  })

  it('preserves the last valid cells when the API response is malformed', async () => {
    const store = useCoverageStore()
    const response = { gridMeters: 20, truncated: false, cells: [[1, 2, 3]] }
    apiService.get.mockResolvedValueOnce(response).mockResolvedValueOnce([])
    await store.fetchCoverageCells('1,2,3,4', 20)
    const log = vi.spyOn(console, 'error').mockImplementation(() => {})
    try {
      expect(await store.fetchCoverageCells('1,2,3,4', 50)).toBeNull()
      expect(store.cells).toBe(response.cells)
      expect(store.cellsGrid).toBe(20)
      expect(store.cellsError).toBeTruthy()
    } finally {
      log.mockRestore()
    }
  })
})

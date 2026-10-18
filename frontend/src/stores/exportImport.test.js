vi.hoisted(() => {
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: {
      getItem: vi.fn(),
      setItem: vi.fn(),
      removeItem: vi.fn(),
      clear: vi.fn()
    }
  })

  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    value: globalThis.localStorage
  })
})

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import apiService from '../utils/apiService'
import { isExportJobExpired, selectRestorableExportJob, useExportImportStore } from './exportImport'

vi.mock('../utils/apiService', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    download: vi.fn(),
    downloadViaBrowser: vi.fn(),
    delete: vi.fn()
  }
}))

const importJob = (overrides = {}) => ({
  success: true,
  importJobId: 'import-1',
  status: 'processing',
  uploadedFileName: 'locations.json',
  progress: 95,
  progressMessage: 'Recalculating coverage...',
  ...overrides
})

describe('exportImport store import status handling', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('does not regress a completed import when a stale processing response arrives', async () => {
    const store = useExportImportStore()
    const completedJob = importJob({
      status: 'completed',
      progress: 100,
      progressMessage: 'Import completed successfully'
    })
    store.setCurrentImportJob(completedJob)
    store.addImportJob(completedJob)
    apiService.get.mockResolvedValue(importJob())

    await store.fetchImportStatus('import-1')

    expect(store.currentImportJob).toMatchObject({
      importJobId: 'import-1',
      status: 'completed',
      progress: 100,
      progressMessage: 'Import completed successfully'
    })
    expect(store.importJobs[0]).toMatchObject({
      importJobId: 'import-1',
      status: 'completed',
      progress: 100
    })
  })

  it('allows a different import job to replace a terminal current job', () => {
    const store = useExportImportStore()
    store.setCurrentImportJob(importJob({
      status: 'completed',
      progress: 100
    }))

    store.setCurrentImportJob(importJob({
      importJobId: 'import-2',
      status: 'processing',
      progress: 25
    }))

    expect(store.currentImportJob).toMatchObject({
      importJobId: 'import-2',
      status: 'processing',
      progress: 25
    })
  })
})

describe('exportImport store export restore and download', () => {
  const NOW = Date.parse('2026-10-18T12:00:00Z')
  const exportJob = (overrides = {}) => ({
    exportJobId: 'export-1',
    status: 'completed',
    createdAt: '2026-10-18T10:00:00Z',
    expiresAt: '2026-10-19T10:00:00Z',
    ...overrides
  })

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('prefers the newest running export over a newer finished one', () => {
    const jobs = [
      exportJob({ exportJobId: 'done-new', createdAt: '2026-10-18T11:00:00Z' }),
      exportJob({ exportJobId: 'running', status: 'processing', createdAt: '2026-10-18T09:00:00Z' })
    ]

    expect(selectRestorableExportJob(jobs, NOW).exportJobId).toBe('running')
  })

  it('falls back to the newest downloadable export and skips failed or expired ones', () => {
    const jobs = [
      exportJob({ exportJobId: 'failed', status: 'failed', createdAt: '2026-10-18T11:30:00Z' }),
      exportJob({ exportJobId: 'expired', createdAt: '2026-10-17T11:00:00Z', expiresAt: '2026-10-18T11:00:00Z' }),
      exportJob({ exportJobId: 'older', createdAt: '2026-10-18T08:00:00Z' }),
      exportJob({ exportJobId: 'newer', createdAt: '2026-10-18T10:00:00Z' })
    ]

    expect(selectRestorableExportJob(jobs, NOW).exportJobId).toBe('newer')
    expect(selectRestorableExportJob([jobs[0], jobs[1]], NOW)).toBeNull()
    expect(isExportJobExpired(jobs[1], NOW)).toBe(true)
  })

  it('restores the job named by a notification link even when it is not the default choice', async () => {
    const store = useExportImportStore()
    apiService.get.mockResolvedValue({
      items: [
        exportJob({ exportJobId: 'newest', createdAt: '2026-10-18T11:00:00Z' }),
        exportJob({ exportJobId: 'linked', createdAt: '2026-10-18T09:00:00Z' })
      ]
    })

    const job = await store.restoreCurrentExportJob('linked')

    expect(apiService.get).toHaveBeenCalledWith('/exports', { page: 0, size: 10 })
    expect(job.exportJobId).toBe('linked')
    expect(store.currentExportJob.exportJobId).toBe('linked')
  })

  it('checks the job, then hands the download to the browser and remembers it', async () => {
    const store = useExportImportStore()
    apiService.get.mockResolvedValue(exportJob({ expiresAt: '2999-01-01T00:00:00Z' }))

    await store.downloadExportFile('export-1')

    expect(apiService.get).toHaveBeenCalledWith('/exports/export-1')
    expect(apiService.downloadViaBrowser).toHaveBeenCalledWith('/exports/export-1/content')
    expect(apiService.download).not.toHaveBeenCalled()
    expect(store.isExportDownloaded('export-1')).toBe(true)
    expect(localStorage.setItem).toHaveBeenCalledWith('gp.exports.downloaded', JSON.stringify(['export-1']))
  })

  it('does not start a browser download for an expired export', async () => {
    const store = useExportImportStore()
    apiService.get.mockResolvedValue(exportJob({ expiresAt: '2000-01-01T00:00:00Z' }))

    await expect(store.downloadExportFile('export-1')).rejects.toMatchObject({ isApiError: true })

    expect(apiService.downloadViaBrowser).not.toHaveBeenCalled()
    expect(store.isExportDownloaded('export-1')).toBe(false)
  })
})

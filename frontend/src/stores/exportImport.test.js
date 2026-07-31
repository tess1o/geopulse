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
import { useExportImportStore } from './exportImport'

vi.mock('../utils/apiService', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    download: vi.fn(),
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

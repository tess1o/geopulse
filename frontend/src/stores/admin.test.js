import { createPinia, setActivePinia } from 'pinia'
import apiService from '@/utils/apiService'
import { useAdminStore } from './admin'

vi.mock('@/utils/apiService', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    download: vi.fn(),
    downloadViaBrowser: vi.fn()
  }
}))

describe('admin store backups', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('streams full backups through the browser instead of buffering a blob', () => {
    useAdminStore().downloadFullBackup()

    expect(apiService.downloadViaBrowser).toHaveBeenCalledWith('/admin/backups/download')
    expect(apiService.download).not.toHaveBeenCalled()
  })

  it('streams local backup downloads and encodes the file name', () => {
    useAdminStore().downloadLocalBackup('backup one.gpb')

    expect(apiService.downloadViaBrowser).toHaveBeenCalledWith('/admin/backups/files/backup%20one.gpb')
  })

  it('saves the backup password through the dedicated endpoint', async () => {
    apiService.put.mockResolvedValue({ passwordConfigured: true })

    const result = await useAdminStore().updateBackupPassword('twelve-chars!')

    expect(apiService.put).toHaveBeenCalledWith('/admin/backups/password', { password: 'twelve-chars!' })
    expect(result).toEqual({ passwordConfigured: true })
  })

  it('keeps the settings JSON export on the blob download path', async () => {
    apiService.download.mockResolvedValue(true)

    await useAdminStore().exportAdminSettingsBackup()

    expect(apiService.download).toHaveBeenCalledWith('/admin/backups/settings/exports')
    expect(apiService.downloadViaBrowser).not.toHaveBeenCalled()
  })
})

describe('admin store user deletion', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('starts a deletion and returns its progress', async () => {
    apiService.delete.mockResolvedValue({ status: 'QUEUED', phase: 'WAITING', deletedGpsPoints: 0 })

    const deletion = await useAdminStore().deleteUser('user-1')

    expect(apiService.delete).toHaveBeenCalledWith('/admin/users/user-1')
    expect(deletion.status).toBe('QUEUED')
  })

  it('reads and retries a deletion', async () => {
    apiService.get.mockResolvedValue({ status: 'FAILED' })
    apiService.post.mockResolvedValue({ status: 'RUNNING' })
    const store = useAdminStore()

    expect((await store.getUserDeletion('user-1')).status).toBe('FAILED')
    expect(apiService.get).toHaveBeenCalledWith('/admin/users/user-1/deletion')
    expect((await store.retryUserDeletion('user-1')).status).toBe('RUNNING')
    expect(apiService.post).toHaveBeenCalledWith('/admin/users/user-1/deletion/retries', {})
  })
})

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

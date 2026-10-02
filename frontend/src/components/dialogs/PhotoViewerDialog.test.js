import { flushPromises, mount } from '@vue/test-utils'
import PhotoViewerDialog from './PhotoViewerDialog.vue'
import { imageService } from '@/utils/imageService'

vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: vi.fn() }) }))
vi.mock('@/utils/imageService', () => ({ imageService: { loadAuthenticatedImage: vi.fn(), revokeBlobUrl: vi.fn() } }))
vi.mock('@/composables/useTimezone', () => ({
  useTimezone: () => ({ formatDateDisplay: () => '2026-08-01', formatTime: () => '12:00' })
}))

const photos = Array.from({ length: 50 }, (_, index) => ({
  id: String(index),
  thumbnailUrl: `/thumb/${index}`,
  previewUrl: `/preview/${index}`
}))

describe('PhotoViewerDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.matchMedia = vi.fn(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() }))
    HTMLElement.prototype.scrollBy = vi.fn()
    imageService.loadAuthenticatedImage.mockImplementation((url) => Promise.resolve(`blob:${url}`))
  })

  it('preloads only the thumbnails near the active photo', async () => {
    const wrapper = mount(PhotoViewerDialog, {
      props: { visible: false, photos },
      global: { stubs: { Dialog: { props: ['visible'], template: '<div v-if="visible"><slot /></div>' }, ProgressSpinner: true } }
    })

    await wrapper.setProps({ visible: true })
    await flushPromises()

    expect(imageService.loadAuthenticatedImage).toHaveBeenCalledTimes(7)
    await wrapper.get('[aria-label="Next photo"]').trigger('click')
    await flushPromises()
    expect(imageService.loadAuthenticatedImage.mock.calls.length).toBeLessThan(20)
  })
})

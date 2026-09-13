import { flushPromises, mount } from '@vue/test-utils'
import ImmichLatestPhotosSection from './ImmichLatestPhotosSection.vue'
import apiService from '@/utils/apiService'
import { imageService } from '@/utils/imageService'

const fetchConfig = vi.fn().mockResolvedValue()
let configured = true
vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: vi.fn() }) }))
vi.mock('@/stores/immich', () => ({ useImmichStore: () => ({ fetchConfig, get isConfigured () { return configured } }) }))
vi.mock('@/utils/apiService', () => ({ default: { get: vi.fn() } }))
vi.mock('@/utils/imageService', () => ({ imageService: { loadAuthenticatedImage: vi.fn(), revokeBlobUrl: vi.fn() } }))
vi.mock('@/composables/useTimezone', () => ({
  useTimezone: () => ({
    format: (value) => value.slice(0, 10),
    formatDateDisplay: (value) => value.slice(0, 10),
    formatDateShort: (value) => value.slice(5, 10),
    formatTime: () => '12:00'
  })
}))
vi.mock('@/components/ui/base/BaseCard.vue', () => ({ default: { template: '<section><slot /></section>' } }))
vi.mock('@/components/dialogs/PhotoViewerDialog.vue', () => ({ default: { template: '<div />' } }))

const photos = Array.from({ length: 90 }, (_, index) => ({
  id: String(index),
  thumbnailUrl: `/thumb/${index}`,
  takenAt: `2026-08-${String(90 - index).padStart(2, '0')}T12:00:00Z`
}))

const mountSection = (presentation = 'rewind') => mount(ImmichLatestPhotosSection, {
  props: {
    title: 'Memories along the way',
    presentation,
    loadMapMarkers: false,
    searchParams: { startDate: 'start', endDate: 'end' }
  },
  global: {
    stubs: {
      Button: { props: ['label'], template: '<button @click="$emit(\'click\')">{{ label }}</button>' },
      Dialog: { props: ['visible'], template: '<div v-if="visible"><slot /></div>' },
      Paginator: { template: '<button class="paginator" @click="$emit(\'page\', { first: 60, rows: 60 })" />' },
      ProgressSpinner: true
    }
  }
})

const findButton = (wrapper, label) => wrapper.findAll('button').find((button) => button.text() === label)

describe('ImmichLatestPhotosSection Rewind presentation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    configured = true
    imageService.loadAuthenticatedImage.mockImplementation((url) => Promise.resolve(`blob:${url}`))
    apiService.get.mockResolvedValue({ data: { totalCount: 90, photos } })
  })

  it('renders six time-spread moments without a mapped-count headline', async () => {
    const wrapper = mountSection()
    await flushPromises()

    expect(apiService.get).toHaveBeenCalledWith('/users/me/immich/photos/search', {
      startDate: 'start', endDate: 'end'
    })
    expect(wrapper.findAll('.rewind-justified-grid .immich-photo-tile')).toHaveLength(6)
    expect(wrapper.text()).toContain('Photo moments')
    expect(wrapper.text()).not.toContain('mapped')
    expect(wrapper.text()).not.toContain('90 /')
  })

  it('uses the shared period gallery and loads thumbnails one page at a time', async () => {
    const wrapper = mountSection()
    await flushPromises()
    const momentThumbnailCount = imageService.loadAuthenticatedImage.mock.calls.length

    await findButton(wrapper, 'Browse all photos').trigger('click')
    await flushPromises()

    expect(apiService.get).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('90 photos from this period')
    expect(imageService.loadAuthenticatedImage.mock.calls.length).toBeLessThanOrEqual(momentThumbnailCount + 60)

    const firstPageThumbnailCount = imageService.loadAuthenticatedImage.mock.calls.length
    await wrapper.get('.paginator').trigger('click')
    await flushPromises()

    expect(imageService.loadAuthenticatedImage.mock.calls.length).toBeLessThanOrEqual(firstPageThumbnailCount + 30)
  })

  it('shows a fresh random selection without repeating the period search', async () => {
    const random = vi.spyOn(Math, 'random').mockReturnValue(0)
    const wrapper = mountSection()
    await flushPromises()
    const initialThumbnailCount = imageService.loadAuthenticatedImage.mock.calls.length

    await findButton(wrapper, 'Show random memories').trigger('click')
    await flushPromises()
    const firstSelection = wrapper.findAll('.rewind-justified-tile img').map((image) => image.attributes('src'))

    await findButton(wrapper, 'Show random memories').trigger('click')
    await flushPromises()
    const secondSelection = wrapper.findAll('.rewind-justified-tile img').map((image) => image.attributes('src'))

    expect(apiService.get).toHaveBeenCalledTimes(1)
    expect(imageService.loadAuthenticatedImage.mock.calls.length).toBeGreaterThan(initialThumbnailCount)
    expect(secondSelection.some((src) => firstSelection.includes(src))).toBe(false)
    random.mockRestore()
  })

  it('does not render an empty Rewind section when Immich is disabled, empty, or unavailable', async () => {
    configured = false
    const disabled = mountSection()
    await flushPromises()
    expect(disabled.find('.immich-photos-card').exists()).toBe(false)

    configured = true
    apiService.get.mockResolvedValueOnce({ data: { totalCount: 0, photos: [] } })
    const empty = mountSection()
    await flushPromises()
    expect(empty.find('.immich-photos-card').exists()).toBe(false)

    apiService.get.mockRejectedValueOnce(new Error('Immich unavailable'))
    const unavailable = mountSection()
    await flushPromises()
    expect(unavailable.find('.immich-photos-card').exists()).toBe(false)
  })

  it('keeps standard shared galleries on the existing photo-search contract', async () => {
    const wrapper = mountSection('default')
    await flushPromises()

    expect(apiService.get).toHaveBeenCalledWith('/users/me/immich/photos/search', { startDate: 'start', endDate: 'end', limit: 20 })
    wrapper.unmount()
  })
})

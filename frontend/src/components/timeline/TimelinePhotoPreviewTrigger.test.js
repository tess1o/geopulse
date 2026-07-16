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

import { flushPromises, mount } from '@vue/test-utils'
import TimelinePhotoPreviewTrigger from './TimelinePhotoPreviewTrigger.vue'
import { getPhotoThumbnailBlobUrl, hasPhotoThumbnail } from '@/utils/immichPhotoThumbnails'

vi.mock('@/components/dialogs/PhotoViewerDialog.vue', () => ({
  default: {
    name: 'PhotoViewerDialog',
    props: [
      'visible',
      'photos',
      'initialPhotoIndex',
      'allowShowOnMap',
      'preloadedBlobUrlResolver'
    ],
    template: '<div v-if="visible" data-testid="photo-viewer"></div>'
  }
}))

vi.mock('@/utils/immichPhotoThumbnails', () => ({
  getPhotoThumbnailBlobUrl: vi.fn(),
  hasPhotoThumbnail: vi.fn()
}))

const mountTrigger = (props) => mount(TimelinePhotoPreviewTrigger, { props })

describe('TimelinePhotoPreviewTrigger', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    hasPhotoThumbnail.mockImplementation((photo) => Boolean(photo?.thumbnailUrl))
  })

  it('shows a clickable thumbnail for one photo', async () => {
    const photo = {
      id: 'photo-1',
      originalFileName: 'Lunch.jpg',
      thumbnailUrl: '/users/me/immich/photos/photo-1/thumbnail'
    }
    getPhotoThumbnailBlobUrl.mockResolvedValue('blob:photo-1')

    const wrapper = mountTrigger({ photos: [photo] })
    await flushPromises()

    const image = wrapper.find('.photo-trigger-thumbnail')
    expect(image.exists()).toBe(true)
    expect(image.attributes('src')).toBe('blob:photo-1')
    expect(wrapper.find('.pi-camera').exists()).toBe(false)

    await wrapper.find('button').trigger('click')
    expect(wrapper.find('[data-testid="photo-viewer"]').exists()).toBe(true)

    const dialog = wrapper.findComponent({ name: 'PhotoViewerDialog' })
    expect(dialog.props('preloadedBlobUrlResolver')('photo-1')).toBe('blob:photo-1')
  })

  it('keeps camera and count for multiple photos', async () => {
    const wrapper = mountTrigger({
      photos: [
        { id: 'photo-1', thumbnailUrl: '/one' },
        { id: 'photo-2', thumbnailUrl: '/two' }
      ]
    })
    await flushPromises()

    expect(wrapper.find('.photo-trigger-thumbnail').exists()).toBe(false)
    expect(wrapper.find('.pi-camera').exists()).toBe(true)
    expect(wrapper.text()).toContain('2')
    expect(getPhotoThumbnailBlobUrl).not.toHaveBeenCalled()
  })

  it('falls back to camera and count when the single thumbnail cannot load', async () => {
    getPhotoThumbnailBlobUrl.mockRejectedValue(new Error('thumbnail failed'))

    const wrapper = mountTrigger({
      photos: [{
        id: 'photo-1',
        thumbnailUrl: '/users/me/immich/photos/photo-1/thumbnail'
      }]
    })
    await flushPromises()

    expect(wrapper.find('.photo-trigger-thumbnail').exists()).toBe(false)
    expect(wrapper.find('.pi-camera').exists()).toBe(true)
    expect(wrapper.text()).toContain('1')
  })
})

import { mount } from '@vue/test-utils'
import DigestMemories from './DigestMemories.vue'

vi.mock('@/composables/useTimezone', () => ({
  useTimezone: () => ({
    create: (value) => ({ value, endOf: () => ({ value: `${value}:end` }) }),
    startOfDayUtc: (value) => `start:${value.value}`,
    endOfDayUtc: (value) => `end:${value.value}`
  })
}))

vi.mock('@/components/location-analytics/ImmichLatestPhotosSection.vue', () => ({
  default: {
    props: ['presentation', 'searchParams', 'showOnMapEnabled', 'loadMapMarkers'],
    emits: ['latest-photos-change'],
    template: '<div class="shared-gallery" />'
  }
}))

describe('DigestMemories', () => {
  it('configures the shared gallery for timezone-aware Rewind moments', () => {
    const wrapper = mount(DigestMemories, { props: { viewMode: 'monthly', year: 2026, month: 8 } })
    const gallery = wrapper.findComponent({ name: 'ImmichLatestPhotosSection' })

    expect(gallery.props()).toMatchObject({
      presentation: 'rewind',
      searchParams: { startDate: 'start:2026-08-01', endDate: 'end:2026-08-01:end' },
      showOnMapEnabled: false,
      loadMapMarkers: false
    })
  })

  it('uses shared gallery photos to control PDF photo availability', async () => {
    const wrapper = mount(DigestMemories, { props: { viewMode: 'yearly', year: 2026 } })
    const gallery = wrapper.findComponent({ name: 'ImmichLatestPhotosSection' })

    gallery.vm.$emit('latest-photos-change', [{ id: 'one' }])
    gallery.vm.$emit('latest-photos-change', [])
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('availability')).toEqual([[true], [false]])
  })
})

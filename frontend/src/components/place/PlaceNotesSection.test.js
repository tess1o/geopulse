import { flushPromises, mount } from '@vue/test-utils'
import PlaceNotesSection from './PlaceNotesSection.vue'
import apiService from '@/utils/apiService'

vi.mock('@/utils/apiService', () => ({
  default: {
    get: vi.fn()
  }
}))

vi.mock('@/composables/useTimezone', () => ({
  useTimezone: () => ({
    formatDateTimeDisplay: (value) => `formatted ${value}`
  })
}))

vi.mock('@/components/timeline/NotesViewerDialog.vue', () => ({
  default: {
    name: 'NotesViewerDialog',
    props: ['visible', 'notes', 'canManage'],
    emits: ['update:visible', 'note-updated', 'note-deleted'],
    template: '<div v-if="visible" data-testid="notes-viewer"></div>'
  }
}))

const BaseCardStub = {
  template: '<section class="base-card-stub"><slot /></section>'
}

const ButtonStub = {
  props: ['label', 'loading', 'disabled', 'ariaLabel'],
  emits: ['click'],
  template: `
    <button :aria-label="ariaLabel" :disabled="disabled || loading" @click="$emit('click')">
      {{ label }}<slot />
    </button>
  `
}

const ProgressSpinnerStub = {
  template: '<div data-testid="spinner"></div>'
}

const mountSection = (props = {}) => mount(PlaceNotesSection, {
  props: {
    title: 'Notes near Test Place',
    searchParams: {
      startTime: '2026-07-01T00:00:00Z',
      endTime: '2026-07-03T00:00:00Z',
      includeExternal: true,
      limit: 5000,
      latitude: 50,
      longitude: 30,
      radiusMeters: 100
    },
    ...props
  },
  global: {
    stubs: {
      BaseCard: BaseCardStub,
      Button: ButtonStub,
      ProgressSpinner: ProgressSpinnerStub
    },
    directives: {
      tooltip: {}
    }
  }
})

const latestNote = {
  id: 2,
  source: 'MEMOS',
  externalId: 'memos-2',
  externalUrl: 'https://memos.example/m/2',
  title: 'Later note',
  contentMarkdown: 'Later body',
  eventTime: '2026-07-02T12:00:00Z',
  latitude: 50.0002,
  longitude: 30.0002,
  locationSource: 'EXPLICIT'
}

const olderNote = {
  id: 1,
  source: 'GEOPULSE',
  title: 'Earlier note',
  contentMarkdown: 'Earlier body',
  eventTime: '2026-07-01T12:00:00Z',
  latitude: 50,
  longitude: 30,
  locationSource: 'DERIVED_STAY'
}

describe('PlaceNotesSection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('calls notes search with place params and renders geotagged notes sorted by event time', async () => {
    const searchParams = {
      startTime: '2026-07-01T00:00:00Z',
      endTime: '2026-07-03T00:00:00Z',
      includeExternal: true,
      limit: 5000,
      latitude: 50,
      longitude: 30,
      radiusMeters: 100
    }
    apiService.get.mockResolvedValue({
      status: 'success',
      data: {
        notes: [olderNote, latestNote]
      }
    })

    const wrapper = mountSection({ searchParams })
    await flushPromises()

    expect(apiService.get).toHaveBeenCalledWith('/notes/search', searchParams)
    const noteCards = wrapper.findAll('.place-note-item')
    expect(noteCards).toHaveLength(2)
    expect(noteCards[0].text()).toContain('Later note')
    expect(noteCards[1].text()).toContain('Earlier note')

    const emittedNotes = wrapper.emitted('notes-change').at(-1)[0]
    expect(emittedNotes.map((note) => note.id)).toEqual([2, 1])
  })

  it('renders an empty state when no place notes are returned', async () => {
    apiService.get.mockResolvedValue({
      status: 'success',
      data: {
        notes: []
      }
    })

    const wrapper = mountSection({ emptyMessage: 'No nearby notes found for this place.' })
    await flushPromises()

    expect(wrapper.text()).toContain('No nearby notes found for this place.')
    expect(wrapper.findAll('.place-note-item')).toHaveLength(0)
  })

  it('renders an error state and clears emitted notes when the search fails', async () => {
    apiService.get.mockRejectedValue(new Error('network down'))

    const wrapper = mountSection()
    await flushPromises()

    expect(wrapper.text()).toContain('network down')
    const emittedNotes = wrapper.emitted('notes-change').at(-1)[0]
    expect(emittedNotes).toEqual([])
  })

  it('excludes ungeotagged notes and applies the provided area filter', async () => {
    const insideArea = {
      ...olderNote,
      id: 3,
      latitude: 50.1,
      longitude: 30.1
    }
    const outsideArea = {
      ...latestNote,
      id: 4,
      latitude: 51,
      longitude: 31
    }
    const withoutCoordinates = {
      id: 5,
      source: 'GEOPULSE',
      title: 'No coordinates',
      contentMarkdown: 'No coordinates body',
      eventTime: '2026-07-02T08:00:00Z'
    }
    const inMemoryFilter = vi.fn((note) => (
      Number(note.latitude) >= 50 &&
      Number(note.latitude) <= 50.2 &&
      Number(note.longitude) >= 30 &&
      Number(note.longitude) <= 30.2
    ))
    const areaSearchParams = {
      startTime: '2026-07-01T00:00:00Z',
      endTime: '2026-07-03T00:00:00Z',
      includeExternal: true,
      limit: 5000
    }
    apiService.get.mockResolvedValue({
      status: 'success',
      data: {
        notes: [insideArea, outsideArea, withoutCoordinates]
      }
    })

    const wrapper = mountSection({
      searchParams: areaSearchParams,
      inMemoryFilter,
      inMemoryFilterCacheKey: 'area:50.2:30.2:50:30'
    })
    await flushPromises()

    expect(apiService.get).toHaveBeenCalledWith('/notes/search', areaSearchParams)
    expect(inMemoryFilter).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('Earlier note')
    expect(wrapper.text()).not.toContain('Later note')
    expect(wrapper.text()).not.toContain('No coordinates')

    const emittedNotes = wrapper.emitted('notes-change').at(-1)[0]
    expect(emittedNotes.map((note) => note.id)).toEqual([3])
  })
})

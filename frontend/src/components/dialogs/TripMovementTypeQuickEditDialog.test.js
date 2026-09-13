import { shallowMount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: vi.fn() }) }))
vi.mock('@/stores/timeline', () => ({ useTimelineStore: () => ({}) }))
vi.mock('@/composables/useTimezone', () => ({
  useTimezone: () => ({ formatDateDisplay: () => '2026-01-01', formatTime: () => '12:00' })
}))

import TripMovementTypeQuickEditDialog from './TripMovementTypeQuickEditDialog.vue'

describe('TripMovementTypeQuickEditDialog', () => {
  it('offers Public Transportation for manual trip overrides', () => {
    const wrapper = shallowMount(TripMovementTypeQuickEditDialog, {
      props: {
        visible: true,
        trip: { id: 1, movementType: 'CAR', timestamp: '2026-01-01T12:00:00Z' }
      },
      global: {
        stubs: {
          Dialog: { template: '<section><slot /><slot name="footer" /></section>' },
          Select: { props: ['options'], template: '<div>{{ options.map((option) => option.label).join(", ") }}</div>' },
          Button: true,
          Tag: true,
          Message: true
        }
      }
    })

    expect(wrapper.text()).toContain('Public Transportation')
  })
})

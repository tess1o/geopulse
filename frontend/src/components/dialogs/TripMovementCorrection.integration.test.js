import { beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import apiService from '@/utils/apiService'
import QuickEdit from './TripMovementTypeQuickEditDialog.vue'
import Classification from './TripClassificationDialog.vue'

vi.mock('@/utils/apiService', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
vi.mock('primevue/usetoast', () => ({ useToast: () => ({ add: vi.fn() }) }))
vi.mock('@/composables/useTimezone', () => ({ useTimezone: () => ({ formatDateTimeDisplayAt: () => '12:00', formatDateTimeDisplay: () => '12:00' }) }))

const trip = () => ({ id: 7, timestamp: '2026-10-01T10:00:00Z', tripDuration: 3600, movementType: 'CAR', movementTypeSource: 'MANUAL' })
const details = { timestamp: '2026-10-01T10:00:00Z', currentClassification: 'CAR', algorithmClassification: 'CAR', movementTypeSource: 'MANUAL', tripDurationSeconds: 3600, distanceMeters: 1000, statistics: {}, thresholds: {}, classificationSteps: [] }
const stubs = {
  Dialog: { template: '<section><slot/><slot name="footer"/></section>' },
  Button: { props: ['label', 'disabled'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')">{{ label }}</button>' },
  Select: { props: ['modelValue', 'options', 'disabled'], emits: ['update:modelValue'], template: '<select :value="modelValue" :disabled="disabled" @change="$emit(\'update:modelValue\', $event.target.value)"><option v-for="o in options" :value="o.value">{{ o.label }}</option></select>' },
  Message: true, Tag: true, DetailItem: true, StatCard: true, ClassificationStepCard: true
}
beforeEach(() => {
  vi.resetAllMocks()
  apiService.get.mockImplementation(path => Promise.resolve(path.endsWith('/classification') ? details : [
    { op: 'SET_TRIP_TYPE', status: 'IN_EFFECT', groupId: 'type-group', target: { type: 'TRIP', id: 7 } }
  ]))
  apiService.post.mockImplementation(path => Promise.resolve(path.endsWith('/preview') ? { token: 'review-token' } : { groupId: 'type-group', timelineJobId: 'job' }))
})

describe.each([['quick edit', QuickEdit], ['classification', Classification]])('%s with the real correction store', (name, Component) => {
  async function open(readOnly = false) {
    const wrapper = shallowMount(Component, { props: { visible: true, trip: trip(), readOnly }, global: { plugins: [createPinia()], stubs } })
    await flushPromises()
    return wrapper
  }
  it('previews and saves the selected type, and forwards the background job', async () => {
    const wrapper = await open()
    await wrapper.find('select').setValue('TRAIN')
    await wrapper.findAll('button').find(b => /save/i.test(b.text())).trigger('click')
    await flushPromises()
    expect(apiService.post.mock.calls.map(call => call[0])).toEqual(['/timeline/corrections/preview', '/timeline/corrections'])
    expect(apiService.post.mock.calls[1][1]).toMatchObject({ op: 'SET_TRIP_TYPE', payload: { movementType: 'TRAIN' }, previewToken: 'review-token' })
    expect(wrapper.emitted('movement-updated')[0][0]).toMatchObject({ groupId: 'type-group', timelineJobId: 'job' })
  })
  it('resets through the correction group without calling a legacy endpoint', async () => {
    const wrapper = await open()
    await wrapper.findAll('button').find(b => /reset/i.test(b.text())).trigger('click')
    await flushPromises()
    expect(apiService.post.mock.calls.map(call => call[0])).toEqual(['/timeline/corrections/type-group/undo'])
    expect(wrapper.emitted('movement-updated')[0][0]).toMatchObject({ operation: 'undo', timelineJobId: 'job' })
  })
  it('keeps the read-only dialog from writing', async () => {
    const wrapper = await open(true)
    expect(wrapper.find('select').attributes('disabled')).toBeDefined()
    for (const button of wrapper.findAll('button').filter(b => /save|reset/i.test(b.text()))) {
      expect(button.attributes('disabled')).toBeDefined()
      await button.trigger('click')
    }
    expect(apiService.post).not.toHaveBeenCalled()
  })
})

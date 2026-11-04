import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import GeocodingCandidateDialog from './GeocodingCandidateDialog.vue'
import { useGeocodingStore } from '@/stores/geocoding'

const mountDialog = () => mount(GeocodingCandidateDialog, {
  props: { visible: true, result: { id: 7, displayName: 'Old place' }, enabledProviders: [
    { name: 'Photon', displayName: 'Photon', enabled: true }, { name: 'Nominatim', displayName: 'Nominatim', enabled: true }
  ] },
  global: { stubs: {
    Dialog: { template: '<div><slot /><slot name="footer" /></div>' },
    Message: { template: '<div><slot /></div>' },
    Button: { props: ['label', 'disabled', 'loading'], template: '<button :disabled="disabled || loading">{{ label }}</button>' },
    Select: { props: ['modelValue', 'disabled', 'options'], emits: ['update:modelValue'], template: '<select :disabled="disabled" @change="$emit(\'update:modelValue\', $event.target.value)"><option></option><option>Photon</option><option>Nominatim</option></select>' }
  } }
})
const preview = { previewId: 'preview-1', candidates: [
  { id: 0, displayName: 'First' }, { id: 1, displayName: 'Second', category: 'cafe', distanceMeters: 12 }
] }
const button = (wrapper, label) => wrapper.findAll('button').find(b => b.text() === label)
const start = async wrapper => {
  await wrapper.find('select').setValue('Photon')
  await button(wrapper, 'Load candidates').trigger('click')
  await flushPromises()
}

beforeEach(() => setActivePinia(createPinia()))
describe('Geocoding candidate selection', () => {
  it('selects the first candidate by default and allows choosing the second', async () => {
    vi.spyOn(useGeocodingStore(), 'fetchReconciliationCandidates').mockResolvedValue(preview)
    const save = vi.spyOn(useGeocodingStore(), 'selectReconciliationCandidate').mockResolvedValue({ id: 9 })
    const wrapper = mountDialog()
    await start(wrapper)
    expect(wrapper.findAll('input[type=radio]')[0].element.checked).toBe(true)
    expect(button(wrapper, 'Save').element.disabled).toBe(false)
    expect(wrapper.text()).toContain('all your stays linked')
    await wrapper.findAll('input[type=radio]')[1].setValue()
    await button(wrapper, 'Save').trigger('click')
    await flushPromises()
    expect(save).toHaveBeenCalledWith(7, 'preview-1', 1)
    expect(wrapper.emitted('saved')).toEqual([[{ id: 9 }]])
  })

  it('saves the default first candidate without another selection', async () => {
    vi.spyOn(useGeocodingStore(), 'fetchReconciliationCandidates').mockResolvedValue(preview)
    const save = vi.spyOn(useGeocodingStore(), 'selectReconciliationCandidate').mockResolvedValue({ id: 7 })
    const wrapper = mountDialog()
    await start(wrapper)
    await button(wrapper, 'Save').trigger('click')
    await flushPromises()
    expect(save).toHaveBeenCalledWith(7, 'preview-1', 0)
  })

  it('clears selection when switching provider and handles one or zero candidates', async () => {
    const fetch = vi.spyOn(useGeocodingStore(), 'fetchReconciliationCandidates').mockResolvedValue({ ...preview, candidates: [preview.candidates[0]] })
    const wrapper = mountDialog()
    await start(wrapper)
    expect(wrapper.text()).toContain('one candidate')
    expect(wrapper.find('input').element.checked).toBe(true)
    await wrapper.find('select').setValue('Nominatim')
    expect(wrapper.findAll('input')).toHaveLength(0)
    expect(button(wrapper, 'Save').element.disabled).toBe(true)
    fetch.mockResolvedValue({ previewId: 'empty', candidates: [] })
    await button(wrapper, 'Load candidates').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('No candidates returned')
    expect(button(wrapper, 'Save').element.disabled).toBe(true)
  })

  it('ignores a response arriving after cancellation', async () => {
    let resolve
    vi.spyOn(useGeocodingStore(), 'fetchReconciliationCandidates').mockImplementation(() => new Promise(r => { resolve = r }))
    const save = vi.spyOn(useGeocodingStore(), 'selectReconciliationCandidate')
    const wrapper = mountDialog()
    await start(wrapper)
    await button(wrapper, 'Cancel').trigger('click')
    resolve(preview)
    await flushPromises()
    expect(wrapper.findAll('input')).toHaveLength(0)
    expect(save).not.toHaveBeenCalled()
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('allows retry after load failure and requires reloading after save failure', async () => {
    const fetch = vi.spyOn(useGeocodingStore(), 'fetchReconciliationCandidates').mockRejectedValue(new Error('offline'))
    const wrapper = mountDialog()
    await start(wrapper)
    expect(wrapper.findAll('input')).toHaveLength(0)
    fetch.mockResolvedValue(preview)
    await button(wrapper, 'Load candidates').trigger('click')
    await flushPromises()
    vi.spyOn(useGeocodingStore(), 'selectReconciliationCandidate').mockRejectedValue(new Error('expired'))
    await wrapper.findAll('input')[1].setValue()
    await button(wrapper, 'Save').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('input')).toHaveLength(0)
    expect(button(wrapper, 'Save').element.disabled).toBe(true)
  })
})

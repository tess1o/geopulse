import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import Field from './ActivityDateTimeField.vue'
import { useTimezone } from '@/composables/useTimezone'
const picker = { name: 'DatePicker', props: ['modelValue', 'ariaLabel'], emits: ['update:modelValue', 'input'], template: '<input :aria-label="ariaLabel" />' }
beforeEach(() => useTimezone().setTimezone('UTC'))
describe('Activity date and time input', () => {
  it('opens the calendar on the selected timeline day, leaving the time unguessed', () => {
    const wrapper = mount(Field, { props: { contextDate: '2026-10-16T00:00:00Z', label: 'From' }, global: { stubs: { DatePicker: picker } } })
    const fields = wrapper.findAllComponents(picker)
    expect(fields[0].props('modelValue').getFullYear()).toBe(2026)
    expect(fields[0].props('modelValue').getMonth()).toBe(9)
    expect(fields[0].props('modelValue').getDate()).toBe(16)
    expect(fields[1].props('modelValue')).toBeNull()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })
  it('combines the selected day with the time instead of using the time picker’s date', async () => {
    const wrapper = mount(Field, { props: { contextDate: '2026-10-16T00:00:00Z', label: 'To' }, global: { stubs: { DatePicker: picker } } })
    wrapper.findAllComponents(picker)[1].vm.$emit('update:modelValue', new Date(2030, 0, 1, 14, 27, 0))
    await wrapper.vm.$nextTick()
    const value = wrapper.emitted('update:modelValue').at(-1)[0]
    expect([value.getFullYear(), value.getMonth(), value.getDate(), value.getHours(), value.getMinutes()]).toEqual([2026, 9, 16, 14, 27])
  })
})

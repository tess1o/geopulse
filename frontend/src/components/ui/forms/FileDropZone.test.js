import { shallowMount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import FileDropZone from './FileDropZone.vue'

const global = {
  stubs: {
    Button: {
      props: ['label'],
      emits: ['click'],
      template: '<button class="remove-stub" @click="$emit(\'click\', $event)">{{ label }}</button>'
    }
  }
}

const mountZone = (props = {}) => shallowMount(FileDropZone, {
  props: {
    accept: '.gpx,.zip',
    promptText: 'Drop a file',
    hintText: 'GPX or ZIP',
    removeLabel: 'Remove',
    ...props
  },
  global
})

const dropFile = (wrapper, name) => wrapper.trigger('drop', {
  dataTransfer: { files: [new File(['x'], name)] }
})

describe('FileDropZone', () => {
  it('accepts a dropped file whose extension matches, case-insensitively', async () => {
    const wrapper = mountZone()

    await dropFile(wrapper, 'Track.GPX')

    const [[file]] = wrapper.emitted('update:modelValue')
    expect(file.name).toBe('Track.GPX')
    expect(wrapper.emitted('invalid')).toBeUndefined()
  })

  it('rejects a dropped file with another extension', async () => {
    const wrapper = mountZone()

    await dropFile(wrapper, 'points.csv')

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.emitted('invalid')[0][0].name).toBe('points.csv')
  })

  it('ignores drops while disabled', async () => {
    const wrapper = mountZone({ disabled: true })

    await dropFile(wrapper, 'track.gpx')

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.emitted('invalid')).toBeUndefined()
  })

  it('highlights while a file is dragged over, including over child elements', async () => {
    const wrapper = mountZone()

    await wrapper.trigger('dragenter')
    await wrapper.trigger('dragenter') // pointer moved onto a child
    await wrapper.trigger('dragleave') // ...and left the child
    expect(wrapper.classes()).toContain('is-dragging')

    await wrapper.trigger('dragleave')
    expect(wrapper.classes()).not.toContain('is-dragging')
  })

  it('shows the selected file and clears it on remove', async () => {
    const wrapper = mountZone({ modelValue: new File(['abc'], 'track.gpx') })

    expect(wrapper.text()).toContain('track.gpx')
    expect(wrapper.text()).toContain('3 B')

    await wrapper.get('.remove-stub').trigger('click')

    expect(wrapper.emitted('update:modelValue')).toEqual([[null]])
  })
})

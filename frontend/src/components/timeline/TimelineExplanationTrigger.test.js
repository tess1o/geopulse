import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TimelineExplanationTrigger from './TimelineExplanationTrigger.vue'

const mountTrigger = (props) => mount({
  components: { TimelineExplanationTrigger },
  props: ['kind'],
  emits: ['card-click', 'show-explanation'],
  template: `<div @click="$emit('card-click')">
    <TimelineExplanationTrigger :kind="kind" @show-explanation="$emit('show-explanation')" />
  </div>`
}, { props, global: { directives: { tooltip: {} } } })

describe('TimelineExplanationTrigger', () => {
  it('says what it opens, for each kind of event', () => {
    expect(mountTrigger({ kind: 'stay' }).find('button').attributes('aria-label'))
      .toBe('Why this stay? See its GPS points and how it was decided')
    expect(mountTrigger({ kind: 'dataGap' }).find('button').attributes('aria-label')).toContain('Why a data gap?')
  })

  it('opens the dialog without selecting the card', async () => {
    const wrapper = mountTrigger({ kind: 'trip' })

    await wrapper.find('button').trigger('click')

    expect(wrapper.emitted('show-explanation')).toHaveLength(1)
    expect(wrapper.emitted('card-click')).toBeUndefined()
  })
})

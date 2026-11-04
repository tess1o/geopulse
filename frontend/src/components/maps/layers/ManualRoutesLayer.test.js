import { shallowMount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import Layer from './ManualRoutesLayer.vue'
it('renders manual geometry separately without exposing it as inspectable GPS points', () => {
  const route = [{ latitude: 49, longitude: 24 }, { latitude: 50, longitude: 25 }]
  const wrapper = shallowMount(Layer, { props: { map: {}, timeline: [{ type: 'trip', manualRoute: route }, { type: 'trip' }] } })
  const path = wrapper.findComponent({ name: 'PathLayer' })
  expect(path.props('pathData')).toEqual([route])
  expect(path.props('inspectionEnabled')).toBe(false)
  expect(path.props('pathOptions').dashArray).toBeTruthy()
})

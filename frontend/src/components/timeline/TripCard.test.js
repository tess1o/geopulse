import { shallowMount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { describe, expect, it } from 'vitest'
import TripCard from './TripCard.vue'

const mountCard = (props = {}) => shallowMount(TripCard, {
  props: { tripItem: tripItem(), ...props },
  global: {
    plugins: [createPinia()],
    stubs: {
      ContextMenu: {
        name: 'ContextMenu',
        props: ['model'],
        template: '<div />'
      }
    }
  }
})

const menuItems = (wrapper) => wrapper.findComponent({ name: 'ContextMenu' }).props('model')

const mapMatchingDetailsItem = (wrapper) =>
  menuItems(wrapper).find(item => item.label === 'Map matching details...')

describe('TripCard map matching details menu item', () => {
  it('stays hidden when map matching has nothing to say about the trip', () => {
    expect(mapMatchingDetailsItem(mountCard())).toBeUndefined()
  })

  it('appears with a warning icon for a failed trip', () => {
    const trip = tripItem()
    const wrapper = mountCard({
      tripItem: trip,
      mapMatchingInfo: {
        status: 'FAILED',
        targetId: 11,
        error: 'Valhalla trace_route failed with HTTP 400'
      }
    })

    const item = mapMatchingDetailsItem(wrapper)
    expect(item).toBeTruthy()
    expect(item.icon).toBe('pi pi-exclamation-triangle')

    item.command()

    expect(wrapper.emitted('show-map-matching-details')).toEqual([[trip]])
  })

  it('appears with an informational icon for a refined trip', () => {
    const wrapper = mountCard({
      mapMatchingInfo: { status: 'COMPLETED', targetId: 11, completedAt: '2026-09-16T20:51:32Z' }
    })

    expect(mapMatchingDetailsItem(wrapper).icon).toBe('pi pi-info-circle')
  })
})

const tripItem = () => ({
  id: 42,
  type: 'trip',
  timestamp: '2026-09-16T04:45:00Z',
  tripDuration: 900,
  distanceMeters: 12_000,
  movementType: 'CAR',
  latitude: 47.3,
  longitude: 8.3,
  endLatitude: 47.4,
  endLongitude: 8.4,
  origin: { locationName: 'Home' },
  destination: { locationName: 'Work' }
})

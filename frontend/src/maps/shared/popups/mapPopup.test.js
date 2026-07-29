import { nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import MapInfoPopup from './MapInfoPopup.vue'
import { mountMapPopup } from './mountMapPopup'
import { buildLocationAnalyticsPlacePopupModel, buildSharedLocationPopupModel } from './locationPopupModels'
import { buildWeatherPopupModel } from './weatherPopupModel'
import { buildFavoriteManagementPopupModel } from './favoritePopupModel'
import { buildFriendLocationPopupModel } from './friendPopupModel'
import {
  buildFriendTimelineTripPopupModel,
  buildHighlightedTripPopupModel,
  buildTimelineItemPopupModel
} from './timelinePopupModels'

const timezone = {
  formatDateDisplay: () => '07/24/2026',
  formatTime: () => '14:30',
  timeAgo: () => '5 minutes ago'
}

describe('map popup mounting', () => {
  it('renders text safely and updates props', async () => {
    const popupMount = mountMapPopup(MapInfoPopup, {
      title: '<img src=x onerror=alert(1)>',
      rows: [{ label: 'Value', value: '<script>alert(1)</script>' }]
    })

    expect(popupMount.element.textContent).toContain('<img src=x onerror=alert(1)>')
    expect(popupMount.element.textContent).toContain('<script>alert(1)</script>')
    expect(popupMount.element.querySelector('img')).toBeNull()
    expect(popupMount.element.querySelector('script')).toBeNull()

    popupMount.updateProps({
      title: 'Updated',
      rows: [{ label: 'Status', value: 'Ready' }]
    })
    await nextTick()

    expect(popupMount.element.textContent).toContain('Updated')
    expect(popupMount.element.textContent).toContain('Ready')
    expect(popupMount.element.textContent).not.toContain('alert(1)')

    popupMount.unmount()
    expect(popupMount.element.textContent).toBe('')
  })

  it('stops interaction events from bubbling to map containers', () => {
    const parent = document.createElement('div')
    const parentClick = vi.fn()
    parent.addEventListener('click', parentClick)

    const popupMount = mountMapPopup(MapInfoPopup, {
      title: 'Popup'
    })
    parent.appendChild(popupMount.element)

    popupMount.element.dispatchEvent(new MouseEvent('click', { bubbles: true }))

    expect(parentClick).not.toHaveBeenCalled()
    popupMount.unmount()
  })
})

describe('map popup models', () => {
  it('builds weather rows with optional precipitation', () => {
    const model = buildWeatherPopupModel({
      weatherCode: 61,
      observedAt: '2026-07-24T11:30:00Z',
      temperature: 22.4,
      precipitation: 3.2,
      windSpeed: 17.9
    }, {
      unit: 'METRIC',
      timezone
    })

    expect(model.title).toBe('Rain')
    expect(model.iconClass).toBe('fas fa-cloud-showers-heavy')
    expect(model.rows).toEqual([
      { label: 'Observed', value: '07/24/2026 14:30' },
      { label: 'Temperature', value: '22°C' },
      { label: 'Precipitation', value: '3.2 mm' },
      { label: 'Wind', value: '18 km/h' }
    ])

    const dryModel = buildWeatherPopupModel({
      weatherCode: 0,
      observedAt: '2026-07-24T11:30:00Z',
      temperature: 22,
      precipitation: 0,
      windSpeed: 4
    }, {
      unit: 'METRIC',
      timezone
    })

    expect(dryModel.rows.map((row) => row.label)).not.toContain('Precipitation')
  })

  it('builds shared location telemetry sections', () => {
    const model = buildSharedLocationPopupModel({
      sharedBy: 'Ada',
      description: 'On the way',
      sharedAt: '2026-07-24T11:30:00Z',
      telemetry: [
        { label: 'Battery', value: 84, unit: '%' },
        { label: 'Speed', value: 12, unit: 'km/h' }
      ]
    }, { timezone })

    expect(model.title).toBe('Ada')
    expect(model.description).toBe('On the way')
    expect(model.rows).toEqual([{ label: 'Last seen', value: '5 minutes ago' }])
    expect(model.sections[0].rows).toEqual([
      { label: 'Battery', value: '84%' },
      { label: 'Speed', value: '12 km/h' }
    ])
  })

  it('builds a place details action callback', () => {
    const onOpenPlaceDetails = vi.fn()
    const model = buildLocationAnalyticsPlacePopupModel({
      locationName: 'Station',
      city: 'Kyiv',
      country: 'Ukraine',
      visitCount: 3,
      lastVisit: '2026-07-24T11:30:00Z'
    }, {
      timezone,
      onOpenPlaceDetails
    })

    expect(model.title).toBe('Station')
    expect(model.subtitle).toBe('Kyiv, Ukraine')
    expect(model.rows).toEqual([
      { label: 'Visits', value: '3' },
      { label: 'Last visit', value: '07/24/2026 14:30' }
    ])

    model.actions[0].onClick()
    expect(onOpenPlaceDetails).toHaveBeenCalledOnce()
  })

  it('builds friend location rows and external map actions', () => {
    const model = buildFriendLocationPopupModel({
      fullName: 'Grace Hopper',
      username: 'grace',
      latitude: 50.45,
      longitude: 30.52,
      lastSeen: '2026-07-24T11:30:00Z',
      latestActivityType: 'STAY',
      latestActivityDurationSeconds: 3600,
      lastBattery: 91
    }, { timezone })

    expect(model.title).toBe('Grace Hopper')
    expect(model.subtitle).toBe('@grace')
    expect(model.rows).toEqual([
      { label: 'Last seen', value: '5 minutes ago' },
      { label: 'Activity', value: 'At current position for 1 hour' },
      { label: 'Battery', value: '91%' }
    ])
    expect(model.actions[0].href).toBe('https://www.google.com/maps?q=50.45,30.52')
  })

  it('omits friend battery row when the latest point has no battery value', () => {
    const model = buildFriendLocationPopupModel({
      fullName: 'Grace Hopper',
      lastBattery: null
    }, { timezone })

    expect(model.rows.some((row) => row.label === 'Battery')).toBe(false)
  })

  it('builds favorite management model variants', () => {
    const model = buildFavoriteManagementPopupModel({
      name: 'Home',
      category: 'Personal',
      address: 'Main Street'
    }, {
      pending: true,
      isArea: true
    })

    expect(model.title).toBe('Home')
    expect(model.subtitle).toBe('Pending area')
    expect(model.rows).toEqual([
      { label: 'Category', value: 'Personal' },
      { label: 'Address', value: 'Main Street' }
    ])
  })

  it('builds timeline and highlighted trip models', () => {
    const stayModel = buildTimelineItemPopupModel({
      type: 'stay',
      locationName: 'Office',
      timestamp: '2026-07-24T11:30:00Z',
      stayDuration: 7200
    }, {
      timezone
    })

    expect(stayModel.title).toBe('Office')
    expect(stayModel.subtitle).toBe('07/24/2026 14:30')
    expect(stayModel.rows).toEqual([{ label: 'Duration', value: '2 hours' }])

    const tripModel = buildHighlightedTripPopupModel({
      movementType: 'CAR',
      timestamp: '2026-07-24T10:00:00Z',
      tripDuration: 3600,
      distanceMeters: 12000
    }, {
      timezone
    })

    expect(tripModel.title).toBe('CAR Trip')
    expect(tripModel.rows.map((row) => row.label)).toContain('Distance')

    const friendTripModel = buildFriendTimelineTripPopupModel({
      userFullName: 'Ada',
      movementType: 'Walk',
      tripDuration: 300,
      distanceMeters: 450
    })

    expect(friendTripModel.title).toBe('Ada')
    expect(friendTripModel.rows).toEqual([
      { label: 'Duration', value: '5 minutes' },
      { label: 'Distance', value: '450 m' }
    ])
  })
})

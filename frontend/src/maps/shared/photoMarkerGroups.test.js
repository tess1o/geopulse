import { describe, expect, it } from 'vitest'
import {
  buildPhotoMarkerClickPayload,
  getPhotoMarkerCount,
  normalizePhotoMarkerGroups
} from './photoMarkerGroups'

describe('photo marker group normalization', () => {
  it('uses singlePhoto as the photo payload for one-photo marker groups', () => {
    const singlePhoto = {
      id: 'photo-1',
      latitude: 50.45,
      longitude: 30.52,
      thumbnailUrl: '/users/me/immich/photos/photo-1/thumbnail'
    }

    const [group] = normalizePhotoMarkerGroups([{
      latitude: 50.45,
      longitude: 30.52,
      count: 1,
      markerKey: '50.45,30.52',
      singlePhoto
    }])

    expect(getPhotoMarkerCount(group)).toBe(1)
    expect(group.photos).toEqual([singlePhoto])
    expect(buildPhotoMarkerClickPayload(group)).toMatchObject({
      photos: [singlePhoto],
      initialIndex: 0,
      markerGroup: {
        count: 1,
        markerKey: '50.45,30.52'
      }
    })
  })

  it('keeps grouped marker summaries count-only even when the backend sends no photos', () => {
    const [group] = normalizePhotoMarkerGroups([{
      latitude: 50.45,
      longitude: 30.52,
      count: 3,
      markerKey: '50.45,30.52'
    }])

    expect(getPhotoMarkerCount(group)).toBe(3)
    expect(group.photos).toEqual([])
    expect(buildPhotoMarkerClickPayload(group)).toEqual({
      markerGroup: {
        latitude: 50.45,
        longitude: 30.52,
        markerKey: '50.45,30.52',
        count: 3,
        childMarkerGroups: [{
          latitude: 50.45,
          longitude: 30.52,
          count: 3,
          markerKey: '50.45,30.52'
        }]
      }
    })
  })

  it('merges nearby single-photo summaries into a grouped marker', () => {
    const photoA = { id: 'photo-a', latitude: 50.45, longitude: 30.52, thumbnailUrl: '/a' }
    const photoB = { id: 'photo-b', latitude: 50.45001, longitude: 30.52001, thumbnailUrl: '/b' }

    const [group] = normalizePhotoMarkerGroups([
      {
        latitude: 50.45,
        longitude: 30.52,
        count: 1,
        markerKey: 'a',
        singlePhoto: photoA
      },
      {
        latitude: 50.45001,
        longitude: 30.52001,
        count: 1,
        markerKey: 'b',
        singlePhoto: photoB
      }
    ])

    expect(getPhotoMarkerCount(group)).toBe(2)
    expect(group.photos).toEqual([photoA, photoB])
  })
})

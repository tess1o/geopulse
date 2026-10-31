import {
  getPhotoQualityScore,
  selectPlacePhotos,
  selectTimeSpreadPhotoMemories,
  selectTripPhotos,
  shufflePhotoMemories
} from './photoMemoryCuration'

const formatDay = (takenAt) => takenAt.slice(0, 10)

describe('photo memory curation', () => {
  it('prefers favorites and rated photos while retaining non-face photos', () => {
    const ordinary = { id: 'ordinary', takenAt: '2026-08-01T12:00:00Z', width: 1600, height: 1200 }
    const favorite = { id: 'favorite', takenAt: '2026-08-01T14:00:00Z', width: 1600, height: 1200, isFavorite: true, rating: 3 }
    const landscape = { id: 'landscape', takenAt: '2026-08-02T12:00:00Z', width: 1600, height: 1200 }

    expect(getPhotoQualityScore(favorite)).toBeGreaterThan(getPhotoQualityScore(ordinary))
    expect(selectTimeSpreadPhotoMemories([ordinary, favorite, landscape], formatDay)).toEqual([favorite, landscape])
  })

  it('removes tiny, extreme, and extreme-close-up candidates when enough alternatives exist', () => {
    const normal = (id, day) => ({ id, takenAt: `2026-08-${day}T12:00:00Z`, width: 1600, height: 1200 })
    const tiny = { id: 'tiny', takenAt: '2026-08-02T13:00:00Z', width: 200, height: 150 }
    const panorama = { id: 'panorama', takenAt: '2026-08-03T13:00:00Z', width: 3000, height: 500 }
    const closeUp = {
      id: 'close-up', takenAt: '2026-08-04T13:00:00Z', width: 1600, height: 1200,
      faces: [{ boundingBoxX1: 0, boundingBoxY1: 0, boundingBoxX2: 1500, boundingBoxY2: 1100 }]
    }

    expect(getPhotoQualityScore(tiny)).toBe(Number.NEGATIVE_INFINITY)
    expect(getPhotoQualityScore(panorama)).toBe(Number.NEGATIVE_INFINITY)
    expect(getPhotoQualityScore(closeUp)).toBeLessThan(getPhotoQualityScore(normal('normal', '04')))
    expect(selectTimeSpreadPhotoMemories([
      normal('one', '01'), normal('two', '02'), tiny, normal('three', '03'), panorama,
      normal('four', '04'), closeUp, normal('five', '05'), normal('six', '06')
    ], formatDay))
      .not.toContain(tiny)
  })

  it('shuffles every eligible photo instead of restricting random memories to a daily shortlist', () => {
    const photos = Array.from({ length: 12 }, (_, index) => ({
      id: String(index), takenAt: '2026-08-01T12:00:00Z', width: 1600, height: 1200
    }))

    expect(shufflePhotoMemories(photos).map((photo) => photo.id).sort()).toEqual(photos.map((photo) => photo.id).sort())
  })

  describe('trip photos', () => {
    // Kraków → Sofia by car, 10 March 08:00–16:00 UTC.
    const trip = {
      startTime: '2026-03-10T08:00:00Z',
      endTime: '2026-03-10T16:00:00Z',
      path: [{ latitude: 50.06, longitude: 19.94 }, { latitude: 42.696, longitude: 23.31 }]
    }
    const photo = (id, takenAt, extra = {}) => ({ id, takenAt, width: 1600, height: 1200, ...extra })
    const inSofia = { latitude: 42.6962, longitude: 23.3101 }
    const ids = (photos) => photos.map((entry) => entry.id)

    it('takes the photos of the visit, not only of the drive: destination photos from the next 48 h count', () => {
      const photos = [
        photo('arrival-evening', '2026-03-10T17:40:00Z', inSofia),
        photo('next-morning', '2026-03-11T06:05:00Z', inSofia),
        photo('day-two-evening', '2026-03-11T20:05:00Z', inSofia),
        photo('too-late', '2026-03-12T17:00:00Z', inSofia),
        photo('back-in-krakow', '2026-03-11T10:00:00Z', { latitude: 50.06, longitude: 19.94 }),
        photo('no-geotag-after', '2026-03-10T18:00:00Z'),
        photo('before-leaving', '2026-03-10T07:00:00Z', inSofia)
      ]

      expect(ids(selectTripPhotos(trip, photos))).toEqual(['arrival-evening', 'next-morning', 'day-two-evening'])
    })

    it('includes photos taken during the trip, geotagged or not', () => {
      const photos = [photo('from-the-passenger-seat', '2026-03-10T11:00:00Z'), photo('arrival', '2026-03-10T17:00:00Z', inSofia)]

      expect(ids(selectTripPhotos(trip, photos))).toEqual(['from-the-passenger-seat', 'arrival'])
    })

    it('picks four spread over the photographed time rather than one burst, in the order they were taken', () => {
      // A burst of six shots on arrival, then one photo each on the next day.
      const burst = Array.from({ length: 6 }, (_, index) => photo(`burst-${index}`, `2026-03-10T16:3${index}:00Z`, inSofia))
      const photos = [
        ...burst,
        photo('morning', '2026-03-11T06:00:00Z', { ...inSofia, isFavorite: true }),
        photo('noon', '2026-03-11T12:00:00Z', inSofia),
        photo('evening', '2026-03-11T20:00:00Z', inSofia)
      ]

      expect(ids(selectTripPhotos(trip, photos))).toEqual(['burst-0', 'morning', 'noon', 'evening'])
    })

    it('never takes photos from elsewhere in the month, and skips photos used by another trip', () => {
      const photos = [
        photo('day-before', '2026-03-09T12:00:00Z', inSofia),
        photo('weeks-later', '2026-03-25T12:00:00Z', inSofia),
        photo('used', '2026-03-10T17:00:00Z', inSofia)
      ]

      expect(selectTripPhotos(trip, photos, new Set(['used']))).toEqual([])
    })
  })

  describe('place photos', () => {
    const place = { name: 'Kyiv', coordinates: [50.45, 30.52] }
    const near = (id, day, extra = {}) => ({
      id, takenAt: `2026-08-${String(day).padStart(2, '0')}T12:00:00Z`, width: 1600, height: 1200,
      latitude: 50.4510, longitude: 30.5210, ...extra
    })

    it('takes up to four photos geotagged within 500 m, spread over the period', () => {
      const photos = [1, 2, 3, 10, 11, 20, 28].map((day) => near(`day-${day}`, day))
      photos.push(near('far', 5, { latitude: 50.47 }), near('untagged', 6, { latitude: null, longitude: null }))

      const picked = selectPlacePhotos(place, photos).map((photo) => photo.id)

      expect(picked).toHaveLength(4)
      expect(picked).not.toContain('far')
      expect(picked).not.toContain('untagged')
      expect(picked[0]).toBe('day-1')
      expect(picked.at(-1)).toBe('day-28')
    })

    it('skips photos used by another scene and places without coordinates', () => {
      expect(selectPlacePhotos(place, [near('used', 1)], new Set(['used']))).toEqual([])
      expect(selectPlacePhotos({ name: 'Nowhere' }, [near('one', 1)])).toEqual([])
    })
  })
})

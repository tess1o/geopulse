import { getPhotoQualityScore, selectTimeSpreadPhotoMemories, shufflePhotoMemories } from './photoMemoryCuration'

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
})

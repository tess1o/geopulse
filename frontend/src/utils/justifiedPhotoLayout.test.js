import { buildJustifiedPhotoRows } from './justifiedPhotoLayout'

describe('justified photo layout', () => {
  it('fills each row using native ratios without cropping', () => {
    const rows = buildJustifiedPhotoRows([
      { id: 'landscape', width: 1600, height: 900 },
      { id: 'portrait', width: 900, height: 1600 },
      { id: 'square', width: 1000, height: 1000 },
      { id: 'fallback' }
    ], 1200, { targetRowHeight: 240, gap: 8 })

    rows.forEach((row) => {
      const totalWidth = row.items.reduce((sum, item) => sum + item.width, 0) + (row.items.length - 1) * 8
      expect(totalWidth).toBeCloseTo(1200, 4)
      row.items.forEach((item) => expect(item.width / row.height).toBeCloseTo(item.aspectRatio, 4))
    })
    expect(rows.flatMap((row) => row.items).find((item) => item.photo.id === 'fallback').aspectRatio).toBeCloseTo(4 / 3)
  })
})

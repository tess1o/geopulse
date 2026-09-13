import { getPhotoAspectRatio } from './photoMemoryCuration'

export const buildJustifiedPhotoRows = (photos, containerWidth, { targetRowHeight = 240, gap = 8 } = {}) => {
  const items = (Array.isArray(photos) ? photos : []).map((photo, index) => ({
    photo,
    index,
    aspectRatio: getPhotoAspectRatio(photo)
  }))
  if (items.length === 0) return []

  const totalRatio = items.reduce((sum, item) => sum + item.aspectRatio, 0)
  const width = Number(containerWidth) > 0 ? Number(containerWidth) : totalRatio * targetRowHeight + gap * (items.length - 1)
  const rowCount = Math.max(1, Math.ceil(totalRatio * targetRowHeight / width))
  const ratioPerRow = totalRatio / rowCount
  const rows = []
  let row = []
  let rowRatio = 0

  items.forEach((item, index) => {
    const remainingItems = items.length - index - 1
    const remainingRows = rowCount - rows.length - 1
    row.push(item)
    rowRatio += item.aspectRatio
    if (rows.length < rowCount - 1 && rowRatio >= ratioPerRow && remainingItems >= remainingRows) {
      rows.push(row)
      row = []
      rowRatio = 0
    }
  })
  if (row.length) rows.push(row)

  return rows.map((rowItems) => {
    const ratio = rowItems.reduce((sum, item) => sum + item.aspectRatio, 0)
    const height = Math.max(1, (width - gap * (rowItems.length - 1)) / ratio)
    return {
      height,
      items: rowItems.map((item) => ({ ...item, width: item.aspectRatio * height }))
    }
  })
}

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, reactive, ref } from 'vue'
import { flushPromises } from '@vue/test-utils'
import { useTimelineMapHeatmap } from './useTimelineMapHeatmap'

let digest, dateRange, scope, available, heatmap
vi.mock('@/stores/digest', () => ({ useDigestStore: () => digest }))
vi.mock('@/stores/dateRange', () => ({ useDateRangeStore: () => dateRange }))

describe('useTimelineMapHeatmap', () => {
  beforeEach(() => {
    digest = { heatmapData: reactive({}), fetchHeatmapRangeData: vi.fn().mockResolvedValue([{ weight: 1 }]) }
    dateRange = reactive({ getCurrentDateRange: ['start-a', 'end-a'] })
    available = ref(true)
    scope = effectScope()
    heatmap = scope.run(() => useTimelineMapHeatmap({ available }))
  })
  afterEach(() => scope.stop())

  it('makes no requests while disabled, including on range and layer changes', async () => {
    dateRange.getCurrentDateRange = ['start-b', 'end-b']
    heatmap.setHeatmapLayer('trips')
    await nextTick()
    expect(digest.fetchHeatmapRangeData).not.toHaveBeenCalled()
    heatmap.toggleHeatmap(true)
    await flushPromises()
    expect(digest.fetchHeatmapRangeData).toHaveBeenCalledExactlyOnceWith(
      'start-b', 'end-b', 'trips', { silent: true })
    expect(heatmap.heatmapPoints.value).toEqual([{ weight: 1 }])
  })

  it('reuses cached data and loads only the selected uncached layer', async () => {
    digest.heatmapData['range:start-a:end-a:stays'] = [{ weight: 2 }]
    heatmap.toggleHeatmap(true)
    await nextTick()
    expect(digest.fetchHeatmapRangeData).not.toHaveBeenCalled()
    expect(heatmap.heatmapPoints.value).toEqual([{ weight: 2 }])
    heatmap.setHeatmapLayer('trips')
    await flushPromises()
    expect(digest.fetchHeatmapRangeData).toHaveBeenCalledExactlyOnceWith(
      'start-a', 'end-a', 'trips', { silent: true })
  })

  it('deduplicates an in-flight request when switched off and back on', async () => {
    let resolve
    digest.fetchHeatmapRangeData.mockReturnValue(new Promise(r => { resolve = r }))
    heatmap.toggleHeatmap(true)
    await nextTick()
    heatmap.toggleHeatmap(false)
    await nextTick()
    heatmap.toggleHeatmap(true)
    await nextTick()
    expect(digest.fetchHeatmapRangeData).toHaveBeenCalledTimes(1)
    resolve([{ weight: 3 }])
    await flushPromises()
    expect(heatmap.heatmapPoints.value).toEqual([{ weight: 3 }])
  })

  it('ignores an old response after switching to a cached range', async () => {
    let resolve
    digest.fetchHeatmapRangeData.mockReturnValue(new Promise(r => { resolve = r }))
    heatmap.toggleHeatmap(true)
    await nextTick()
    digest.heatmapData['range:start-b:end-b:stays'] = [{ weight: 4 }]
    dateRange.getCurrentDateRange = ['start-b', 'end-b']
    await nextTick()
    resolve([{ weight: 99 }])
    await flushPromises()
    expect(heatmap.heatmapPoints.value).toEqual([{ weight: 4 }])
    available.value = false
    await nextTick()
    expect(heatmap.heatmapPoints.value).toEqual([])
  })
})

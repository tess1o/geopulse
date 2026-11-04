import { describe, expect, it, vi } from 'vitest'
import { fitMapToBounds } from './tripReconstructionViewport'

const map = mode => ({ __gpEngineMode: mode, setView: vi.fn(), fitBounds: vi.fn() })
describe('reconstruction viewport', () => {
  for (const mode of ['RASTER', 'VECTOR']) {
    it(`keeps identical and nearby endpoints at street level on ${mode}`, () => {
      for (const points of [[[50, 30], [50, 30]], [[50.0001, 30.0001], [50, 30]]]) {
        const instance = map(mode)
        fitMapToBounds(instance, points, { maxZoom: 16 })
        expect(instance.setView).toHaveBeenCalledWith(expect.any(Array), 16, {})
        expect(instance.fitBounds).not.toHaveBeenCalled()
      }
    })
    it(`frames every route point regardless of selection order on ${mode}`, () => {
      const instance = map(mode)
      fitMapToBounds(instance, [[51, 31], [52, 32], [50, 30]], { padding: [40, 40], maxZoom: 16 })
      expect(instance.fitBounds).toHaveBeenCalledWith(mode === 'VECTOR' ? [[30, 50], [32, 52]] : [[50, 30], [52, 32]],
        { padding: mode === 'VECTOR' ? { left: 40, right: 40, top: 40, bottom: 40 } : [40, 40], maxZoom: 16 })
    })
  }
})

import { expect } from '@playwright/test'

export const MAP_HOST_SELECTOR = '[data-testid="map-host-raster"], [data-testid="map-host-vector"]'
export const MAP_POPUP_CONTENT_SELECTOR = '.leaflet-popup-content, .maplibregl-popup-content'

const MAP_LOADING_SPINNER_SELECTOR = '.loading-messages .p-progress-spinner'

const clampToContainer = (value, containerSize) => {
  if (!Number.isFinite(value)) {
    return Math.max(8, Math.floor(containerSize / 2))
  }

  return Math.max(8, Math.min(Math.floor(value), Math.max(8, Math.floor(containerSize - 8))))
}

export class MapEngineHarness {
  constructor(page) {
    this.page = page
  }

  getMapHostLocator({ rootSelector = null, mapId = null } = {}) {
    if (mapId) {
      return this.page
        .locator(`#${mapId}[data-testid="map-host-raster"], #${mapId}[data-testid="map-host-vector"]`)
        .first()
    }

    if (rootSelector) {
      return this.page
        .locator(`${rootSelector} [data-testid="map-host-raster"], ${rootSelector} [data-testid="map-host-vector"]`)
        .first()
    }

    return this.page.locator(MAP_HOST_SELECTOR).first()
  }

  getPopupContentLocator() {
    return this.page.locator(MAP_POPUP_CONTENT_SELECTOR).first()
  }

  async getActiveMode(options = {}) {
    const host = this.getMapHostLocator(options)
    const testId = await host.getAttribute('data-testid')

    if (testId === 'map-host-vector') {
      return 'VECTOR'
    }

    return 'RASTER'
  }

  async waitForMapReady({ timeout = 15000, settleMs = 1000, ...options } = {}) {
    const host = this.getMapHostLocator(options)
    await expect(host).toBeVisible({ timeout })

    await this.page.waitForSelector(MAP_LOADING_SPINNER_SELECTOR, {
      state: 'hidden',
      timeout
    }).catch(() => {})

    if (settleMs > 0) {
      await this.page.waitForTimeout(settleMs)
    }

    return this.getActiveMode(options)
  }

  async rightClickOnMap(x, y, options = {}) {
    const host = this.getMapHostLocator(options)
    await expect(host).toBeVisible({ timeout: 10000 })

    const box = await host.boundingBox()
    if (!box) {
      throw new Error('Map host is not visible for right-click interaction')
    }

    const safeX = clampToContainer(x, box.width)
    const safeY = clampToContainer(y, box.height)

    await host.click({
      position: { x: safeX, y: safeY },
      button: 'right',
      force: true
    })
  }

  async drawRectangle(startX, startY, endX, endY, options = {}) {
    const host = this.getMapHostLocator(options)
    await expect(host).toBeVisible({ timeout: 10000 })

    const box = await host.boundingBox()
    if (!box) {
      throw new Error('Map host is not visible for rectangle drawing')
    }

    const startAbsX = box.x + clampToContainer(startX, box.width)
    const startAbsY = box.y + clampToContainer(startY, box.height)
    const endAbsX = box.x + clampToContainer(endX, box.width)
    const endAbsY = box.y + clampToContainer(endY, box.height)

    await this.page.mouse.move(startAbsX, startAbsY)
    await this.page.waitForTimeout(100)
    await this.page.mouse.down()
    await this.page.waitForTimeout(120)

    const steps = 10
    for (let step = 1; step <= steps; step += 1) {
      const x = startAbsX + ((endAbsX - startAbsX) * step / steps)
      const y = startAbsY + ((endAbsY - startAbsY) * step / steps)
      await this.page.mouse.move(x, y)
      await this.page.waitForTimeout(20)
    }

    await this.page.waitForTimeout(80)
    await this.page.mouse.up()
    await this.page.waitForTimeout(400)
  }

  async getRegisteredMapSummary(options = {}) {
    return this.page.evaluate(({ rootSelector, mapId }) => {
      const registry = window.__GP_E2E_MAPS || {}

      const isVisible = (element) => {
        if (!element) return false
        const rect = element.getBoundingClientRect()
        return rect.width > 0 && rect.height > 0
      }

      const resolveHost = () => {
        if (mapId) {
          return document.getElementById(mapId)
        }

        if (rootSelector) {
          const root = document.querySelector(rootSelector)
          if (root) {
            const scoped = root.querySelector('[data-testid="map-host-vector"], [data-testid="map-host-raster"]')
            if (scoped) return scoped
          }
        }

        const hosts = [...document.querySelectorAll('[data-testid="map-host-vector"], [data-testid="map-host-raster"]')]
        return hosts.find(isVisible) || hosts[0] || null
      }

      const host = resolveHost()
      const resolvedMapId = host?.id || null
      const testId = host?.dataset?.testid || null
      const mode = testId === 'map-host-vector' ? 'VECTOR' : 'RASTER'
      const map = resolvedMapId ? registry[resolvedMapId] : null

      return {
        mapId: resolvedMapId,
        mode,
        isRegistered: Boolean(map),
        isLoaded: typeof map?.loaded === 'function' ? map.loaded() : null
      }
    }, options)
  }

  async countVectorSourceFeatures({ sourceIncludes = [], ...options } = {}) {
    return this.page.evaluate(({ sourceIncludes, rootSelector, mapId }) => {
      const registry = window.__GP_E2E_MAPS || {}

      const host = mapId
        ? document.getElementById(mapId)
        : (() => {
            if (rootSelector) {
              const root = document.querySelector(rootSelector)
              const scoped = root?.querySelector('[data-testid="map-host-vector"]')
              if (scoped) return scoped
            }
            const hosts = [...document.querySelectorAll('[data-testid="map-host-vector"]')]
            return hosts.find((entry) => {
              const rect = entry.getBoundingClientRect()
              return rect.width > 0 && rect.height > 0
            }) || hosts[0] || null
          })()

      const resolvedMapId = host?.id || null
      const map = resolvedMapId ? registry[resolvedMapId] : null
      if (!map || typeof map.getStyle !== 'function') {
        return { count: 0, sourceIds: [], mapId: resolvedMapId }
      }

      const style = map.getStyle() || {}
      const sourceIds = Object.keys(style.sources || {}).filter((sourceId) => {
        if (!Array.isArray(sourceIncludes) || sourceIncludes.length === 0) {
          return true
        }

        return sourceIncludes.some((token) => sourceId.includes(token))
      })

      let count = 0
      sourceIds.forEach((sourceId) => {
        const source = map.getSource(sourceId)
        const rawData = source?._data || source?._options?.data || null
        // Newer MapLibre wraps the GeoJSON as { geojson } inside the source.
        const data = rawData?.geojson || rawData
        if (data?.type === 'FeatureCollection' && Array.isArray(data.features)) {
          count += data.features.length
        }
      })

      return { count, sourceIds, mapId: resolvedMapId }
    }, { ...options, sourceIncludes })
  }

  async countVectorRenderedFeatures({ layerIncludes = [], ...options } = {}) {
    return this.page.evaluate(({ layerIncludes, rootSelector, mapId }) => {
      const registry = window.__GP_E2E_MAPS || {}

      const host = mapId
        ? document.getElementById(mapId)
        : (() => {
            if (rootSelector) {
              const root = document.querySelector(rootSelector)
              const scoped = root?.querySelector('[data-testid="map-host-vector"]')
              if (scoped) return scoped
            }
            const hosts = [...document.querySelectorAll('[data-testid="map-host-vector"]')]
            return hosts.find((entry) => {
              const rect = entry.getBoundingClientRect()
              return rect.width > 0 && rect.height > 0
            }) || hosts[0] || null
          })()

      const resolvedMapId = host?.id || null
      const map = resolvedMapId ? registry[resolvedMapId] : null
      if (!map || typeof map.getStyle !== 'function' || typeof map.queryRenderedFeatures !== 'function') {
        return { count: 0, layerIds: [], mapId: resolvedMapId }
      }

      const style = map.getStyle() || {}
      const layerIds = (style.layers || [])
        .map((layer) => layer?.id)
        .filter((layerId) => {
          if (!layerId) return false
          if (!Array.isArray(layerIncludes) || layerIncludes.length === 0) {
            return true
          }
          return layerIncludes.some((token) => layerId.includes(token))
        })

      if (layerIds.length === 0) {
        return { count: 0, layerIds, mapId: resolvedMapId }
      }

      const rendered = map.queryRenderedFeatures(undefined, { layers: layerIds }) || []
      return { count: rendered.length, layerIds, mapId: resolvedMapId }
    }, { ...options, layerIncludes })
  }

  /**
   * Locate a rendered vector feature (a point, else a cluster) on screen, in page coordinates.
   * Native MapLibre layers have no DOM nodes to click, so tests must click where the feature is drawn.
   */
  async getVectorRenderedFeatureScreenPoint({ layerIncludes = [], ...options } = {}) {
    return this.page.evaluate(({ layerIncludes, rootSelector, mapId }) => {
      const registry = window.__GP_E2E_MAPS || {}

      const host = mapId
        ? document.getElementById(mapId)
        : (() => {
            if (rootSelector) {
              const root = document.querySelector(rootSelector)
              const scoped = root?.querySelector('[data-testid="map-host-vector"]')
              if (scoped) return scoped
            }
            const hosts = [...document.querySelectorAll('[data-testid="map-host-vector"]')]
            return hosts.find((entry) => {
              const rect = entry.getBoundingClientRect()
              return rect.width > 0 && rect.height > 0
            }) || hosts[0] || null
          })()

      const map = host?.id ? registry[host.id] : null
      if (!map || typeof map.getStyle !== 'function' || typeof map.queryRenderedFeatures !== 'function') {
        return null
      }

      const layerIds = (map.getStyle()?.layers || [])
        .map((layer) => layer?.id)
        .filter((layerId) => layerId && (layerIncludes.length === 0 || layerIncludes.some((token) => layerId.includes(token))))
      if (layerIds.length === 0) {
        return null
      }

      const features = map.queryRenderedFeatures(undefined, { layers: layerIds }) || []
      const isCluster = (feature) => feature?.properties?.cluster_id !== undefined && feature?.properties?.cluster_id !== null
      const feature = features.find((entry) => !isCluster(entry)) || features[0]
      const coordinates = feature?.geometry?.coordinates
      if (!Array.isArray(coordinates)) {
        return null
      }

      const projected = map.project(coordinates)
      const canvasRect = map.getCanvas().getBoundingClientRect()
      return {
        x: canvasRect.left + projected.x,
        y: canvasRect.top + projected.y,
        isCluster: isCluster(feature)
      }
    }, { ...options, layerIncludes })
  }

  /** Click the first rendered vector feature of the given layers. Returns false when none is drawn yet. */
  async clickVectorRenderedFeature(options = {}) {
    await this.getMapHostLocator(options).scrollIntoViewIfNeeded().catch(() => {})

    let point = await this.getVectorRenderedFeatureScreenPoint(options)
    if (!point) {
      return false
    }

    // mouse.click uses viewport coordinates, so bring the feature on screen first.
    const viewportHeight = await this.page.evaluate(() => window.innerHeight)
    if (point.y < 0 || point.y > viewportHeight) {
      await this.page.evaluate((deltaY) => window.scrollBy(0, deltaY), point.y - viewportHeight / 2)
      point = await this.getVectorRenderedFeatureScreenPoint(options)
      if (!point) {
        return false
      }
    }

    await this.page.mouse.click(point.x, point.y)
    return true
  }
}

/**
 * Read the colors the timeline path layers are drawn with, on either map engine.
 * Returns { normalColor, highlightedColors } where highlightedColors lists every color used by the
 * highlighted trip (several for a car trip drawn in speed bands), lower-cased.
 */
export async function readTimelinePathColors(page, { rootSelector = null } = {}) {
  return page.evaluate(({ rootSelector, hostSelector }) => {
    const registry = window.__GP_E2E_MAPS || {}
    const root = rootSelector ? document.querySelector(rootSelector) : document
    const hosts = [...(root || document).querySelectorAll(hostSelector)]
    const host = hosts.find((entry) => {
      const rect = entry.getBoundingClientRect()
      return rect.width > 0 && rect.height > 0
    }) || hosts[0] || null
    const map = host?.id ? registry[host.id] : null
    if (!map) {
      return { normalColor: null, highlightedColors: [] }
    }

    const normalize = (color) => String(color || '').toLowerCase()

    // Vector (MapLibre): read paint properties of the gp-path layers.
    if (typeof map.getStyle === 'function') {
      const layers = (map.getStyle()?.layers || []).filter((layer) => layer.id.startsWith('gp-path'))
      const normalLayer = layers.find((layer) => layer.id.endsWith('-line') && !layer.id.includes('highlighted'))
      const highlightedLayer = layers.find((layer) => layer.id.endsWith('-highlighted-line'))
      const highlightedPaint = highlightedLayer ? map.getPaintProperty(highlightedLayer.id, 'line-color') : null
      const highlightedColors = Array.isArray(highlightedPaint)
        ? highlightedPaint.filter((entry) => typeof entry === 'string' && entry.startsWith('#'))
        : (highlightedPaint ? [highlightedPaint] : [])

      return {
        normalColor: normalLayer ? normalize(map.getPaintProperty(normalLayer.id, 'line-color')) : null,
        highlightedColors: [...new Set(highlightedColors.map(normalize))]
      }
    }

    // Raster (Leaflet): path groups carry a pathId; highlighted visuals are non-interactive, visible polylines.
    let normalColor = null
    const highlightedColors = new Set()
    const visit = (layer) => {
      if (typeof layer.eachLayer === 'function') {
        layer.eachLayer(visit)
        return
      }
      const options = layer?.options
      if (!options || typeof layer.getLatLngs !== 'function') {
        return
      }
      if (options.pathId !== undefined) {
        normalColor = normalColor || normalize(options.color)
      } else if (options.interactive === false && options.opacity !== 0) {
        highlightedColors.add(normalize(options.color))
      }
    }
    map.eachLayer?.(visit)

    return { normalColor, highlightedColors: [...highlightedColors] }
  }, { rootSelector, hostSelector: MAP_HOST_SELECTOR })
}

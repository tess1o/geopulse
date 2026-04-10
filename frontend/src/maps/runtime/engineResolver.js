import { normalizeMapRenderMode } from '@/maps/contracts/mapContracts'

const ENGINE_MODULE_LOADERS = {
  RASTER: () => import('@/maps/raster/RasterMapHost.vue'),
  VECTOR: () => import('@/maps/vector/VectorMapHost.vue')
}

const componentCache = new Map()

export async function resolveMapEngineComponent(mode) {
  const normalizedMode = normalizeMapRenderMode(mode)

  if (componentCache.has(normalizedMode)) {
    return componentCache.get(normalizedMode)
  }

  const loader = ENGINE_MODULE_LOADERS[normalizedMode] || ENGINE_MODULE_LOADERS.VECTOR
  const module = await loader()
  const component = module.default

  componentCache.set(normalizedMode, component)
  return component
}

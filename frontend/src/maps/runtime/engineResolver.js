import { normalizeMapRenderMode } from '@/maps/contracts/mapContracts'
import { loadVectorEngine } from '@/maps/runtime/vectorEngineRegistry'

const ENGINE_MODULE_LOADERS = {
  RASTER: () => import('@/maps/raster/RasterMapHost.vue'),
  // The vector layers/adapters must be ready before VectorMapHost creates a map that they will be asked to render on.
  VECTOR: () => Promise.all([import('@/maps/vector/VectorMapHost.vue'), loadVectorEngine()]).then(([module]) => module)
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

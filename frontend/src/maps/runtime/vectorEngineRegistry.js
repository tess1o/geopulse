// Gatekeeper for MapLibre-backed code. Shared map components must not import vector implementations
// statically: that would pull maplibre-gl (~1 MB) into raster-only pages. The vector engine module is loaded
// together with VectorMapHost (see engineResolver), and a MapLibre map instance can only exist after that, so
// code that has a vector map in hand can read the engine synchronously.

let vectorEngine = null
let pendingLoad = null

export const loadVectorEngine = () => {
  if (vectorEngine) {
    return Promise.resolve(vectorEngine)
  }

  pendingLoad ||= import('@/maps/vector/vectorEngine')
    .then((module) => {
      vectorEngine = module
      return module
    })
    .catch((error) => {
      pendingLoad = null
      throw error
    })

  return pendingLoad
}

export const getVectorEngine = () => {
  if (!vectorEngine) {
    throw new Error('Vector map engine is not loaded yet; call loadVectorEngine() before using vector map code')
  }
  return vectorEngine
}

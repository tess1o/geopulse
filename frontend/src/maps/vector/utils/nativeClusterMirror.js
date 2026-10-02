import Supercluster from 'supercluster'

// MapLibre's GeoJSON source clusters with supercluster in tile units:
// radius = clusterRadius * (EXTENT / tileSize), EXTENT = 8192, tileSize = 512.
const MAPLIBRE_TILE_EXTENT = 8192
const MAPLIBRE_GEOJSON_TILE_SIZE = 512
const WORLD_BBOX = [-180, -85, 180, 85]

/**
 * Synchronous replica of a MapLibre clustered GeoJSON source. Given the same
 * input features (in the same order) and options, it reports exactly which
 * groups MapLibre currently renders standalone and which it merges into a
 * cluster - without querying the source, so there is no tile-loading race.
 *
 * MapLibre clusters per integer tile zoom (floor of the camera zoom) and keeps
 * clustering up to and including clusterMaxZoom; both are mirrored here.
 */
export const createNativeClusterMirror = ({ clusterRadius, clusterMaxZoom }) => {
  let indexedGroups = null
  let index = null

  const ensureIndex = (groups) => {
    if (groups === indexedGroups && index) {
      return index
    }

    indexedGroups = groups
    index = new Supercluster({
      radius: clusterRadius * (MAPLIBRE_TILE_EXTENT / MAPLIBRE_GEOJSON_TILE_SIZE),
      extent: MAPLIBRE_TILE_EXTENT,
      maxZoom: clusterMaxZoom,
      minPoints: 2
    })
    index.load(groups.map((group, groupIndex) => ({
      type: 'Feature',
      geometry: { type: 'Point', coordinates: [group.longitude, group.latitude] },
      properties: { groupIndex }
    })))
    return index
  }

  /**
   * Returns what the native source renders at `zoom`, as
   * [{ indices: number[], latitude, longitude, isCluster }].
   */
  const getRenderedEntities = (groups, zoom) => {
    if (!Array.isArray(groups) || groups.length === 0 || !Number.isFinite(zoom)) {
      return []
    }

    const clusterIndex = ensureIndex(groups)
    return clusterIndex.getClusters(WORLD_BBOX, Math.floor(zoom)).map((feature) => {
      const [longitude, latitude] = feature.geometry.coordinates
      if (!feature.properties.cluster) {
        return { indices: [feature.properties.groupIndex], latitude, longitude, isCluster: false }
      }

      const indices = clusterIndex
        .getLeaves(feature.properties.cluster_id, Infinity)
        .map((leaf) => leaf.properties.groupIndex)
      return { indices, latitude, longitude, isCluster: true }
    })
  }

  return { getRenderedEntities }
}

// Points carry their group index; clusters aggregate the smallest leaf index,
// which identifies them because every leaf belongs to exactly one cluster.
export const MIN_GROUP_INDEX_CLUSTER_PROPERTY = {
  min_group_index: ['min', ['get', 'groupIndex']]
}

const withGroupExclusion = (filter, excludedIndices) => (
  excludedIndices.size === 0
    ? filter
    : ['all', filter, ['!', ['in',
        ['coalesce', ['get', 'min_group_index'], ['get', 'groupIndex']],
        ['literal', [...excludedIndices]]
      ]]]
)

/**
 * Hides excluded groups (and clusters whose leaves are excluded) with layer
 * filters. The source data is left untouched on purpose: removing features
 * would make MapLibre re-cluster the rest, invalidating the mirror above.
 * `layerFilters` maps layer id -> that layer's base filter.
 */
export const applyGroupExclusionFilters = (mapInstance, layerFilters, excludedIndices) => {
  Object.entries(layerFilters).forEach(([layerId, filter]) => {
    if (mapInstance.getLayer?.(layerId)) {
      mapInstance.setFilter(layerId, withGroupExclusion(filter, excludedIndices))
    }
  })
}

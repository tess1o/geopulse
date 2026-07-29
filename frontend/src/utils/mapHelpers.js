/**
 * Map-related utility functions for Leaflet integration
 * Handles marker creation, styling, and map configuration
 */

import L from 'leaflet'
import { getTripMovementIconClass } from '@/utils/timelineIconUtils'

/**
 * Fix Leaflet default marker images import issues in Vite/Webpack
 */
export function fixLeafletMarkerImages() {
    delete L.Icon.Default.prototype._getIconUrl

    L.Icon.Default.mergeOptions({
        iconRetinaUrl: new URL('leaflet/dist/images/marker-icon-2x.png', import.meta.url).href,
        iconUrl: new URL('leaflet/dist/images/marker-icon.png', import.meta.url).href,
        shadowUrl: new URL('leaflet/dist/images/marker-shadow.png', import.meta.url).href
    })
}

/**
 * Fix Leaflet tooltip positioning issues
 * Adds retry logic for tooltip positioning when map container is not ready
 */
export function fixLeafLetTooltip() {
    L.Tooltip.prototype._updatePosition = function (retryCount = 10) {
        if (!this._map || !this._map._container) {
            if (retryCount > 0) {
                setTimeout(() => {
                    this._updatePosition(retryCount - 1)
                }, 200)
            }
            return
        }

        try {
            const pos = this._map.latLngToLayerPoint(this._latlng)
            this._setPosition(pos)
        } catch (e) {
            console.warn('Tooltip position update failed:', e)
        }
    }
}

/**
 * Fix Leaflet marker animation issues during zoom
 * Prevents _latLngToNewLayerPoint errors when map container is not ready
 */
export function fixLeafletMarkerAnimation() {
    const PATCH_VERSION = 2
    if (L.__geoPulseZoomGuardsPatchVersion === PATCH_VERSION) {
        return
    }
    L.__geoPulseZoomGuardsPatchVersion = PATCH_VERSION

    // Enhanced zoom animation fix with scaling prevention
    const originalAnimateZoom = L.Marker.prototype._animateZoom
    L.Marker.prototype._animateZoom = function (opt) {
        // More comprehensive null checks
        if (!this._map ||
            !this._map._container ||
            !this._map._latLngToNewLayerPoint ||
            !this._latlng ||
            !opt ||
            typeof opt.zoom === 'undefined' ||
            !opt.center) {
            return this
        }

        try {
            const pos = this._map._latLngToNewLayerPoint(this._latlng, opt.zoom, opt.center)

            // Additional pos validation
            if (this._icon && pos && typeof pos.x === 'number' && typeof pos.y === 'number') {
                // Disable CSS transitions during zoom
                L.DomUtil.addClass(this._icon, 'leaflet-zoom-anim')

                // Use setPosition instead of setTransform to avoid scaling issues
                L.DomUtil.setPosition(this._icon, pos)

                // Re-enable transitions after zoom animation
                setTimeout(() => {
                    if (this._icon) {
                        L.DomUtil.removeClass(this._icon, 'leaflet-zoom-anim')
                    }
                }, 250) // Match Leaflet's default zoom animation duration
            }

            if (this._shadow && pos && typeof pos.x === 'number' && typeof pos.y === 'number') {
                L.DomUtil.setPosition(this._shadow, pos)
            }

            return this
        } catch (e) {
            // Silently fail and fall back to original behavior
            try {
                if (originalAnimateZoom) {
                    return originalAnimateZoom.call(this, opt)
                }
            } catch (fallbackError) {
                // If even the original fails, just return this
            }
            return this
        }
    }

    // Fix zoom end to ensure clean state
    const originalOnZoomEnd = L.Marker.prototype._onZoomEnd
    L.Marker.prototype._onZoomEnd = function () {
        // Clean up any zoom animation classes
        if (this._icon) {
            L.DomUtil.removeClass(this._icon, 'leaflet-zoom-anim')
        }

        try {
            if (originalOnZoomEnd) {
                return originalOnZoomEnd.call(this)
            }
        } catch (e) {
            // Silently handle zoom end errors
        }
    }

    // Enhanced fix for DivIcon markers with better positioning
    L.DivIcon.prototype._setIconStyles = function (img, name) {
        const options = this.options
        let sizeOption = options[name + 'Size']

        if (typeof sizeOption === 'number') {
            sizeOption = [sizeOption, sizeOption]
        }

        const size = L.point(sizeOption)
        const anchor = L.point(
            (name === 'shadow' && options.shadowAnchor) ||
            options.iconAnchor ||
            (size && size.divideBy(2, true))
        )

        // Enhanced positioning with better anchor handling
        if (name === 'icon' && anchor) {
            img.style.marginLeft = (-anchor.x) + 'px'
            img.style.marginTop = (-anchor.y) + 'px'

            // Ensure consistent sizing regardless of zoom
            img.style.position = 'absolute'
            img.style.transform = 'none' // Prevent transform conflicts
        }

        if (size) {
            img.style.width = size.x + 'px'
            img.style.height = size.y + 'px'
        }
    }

    // Fix for marker positioning during zoom
    const originalSetLatLng = L.Marker.prototype.setLatLng
    L.Marker.prototype.setLatLng = function (latlng) {
        if (!this._map || !this._map._container || !latlng) {
            this._pendingLatLng = latlng
            return this
        }

        try {
            return originalSetLatLng.call(this, latlng)
        } catch (e) {
            // Silently handle setLatLng errors
            return this
        }
    }

    // Fix for DivIcon positioning
    const originalUpdate = L.Marker.prototype.update
    L.Marker.prototype.update = function () {
        if (!this._map || !this._map._container) {
            return this
        }

        if (this._pendingLatLng) {
            this._latlng = this._pendingLatLng
            delete this._pendingLatLng
        }

        try {
            return originalUpdate.call(this)
        } catch (e) {
            // Silently handle update errors
            return this
        }
    }

    // Fix for layer groups
    const originalLayerGroupAnimateZoom = L.LayerGroup.prototype._animateZoom
    L.LayerGroup.prototype._animateZoom = function (opt) {
        if (!this._map || !this._map._container || !opt) {
            return this
        }

        try {
            return originalLayerGroupAnimateZoom.call(this, opt)
        } catch (e) {
            // Silently handle layer group zoom errors
            return this
        }
    }

    // Popup/Tooltip each override DivOverlay._animateZoom, so guard the
    // concrete classes directly.
    const wrapOverlayAnimateZoom = (OverlayClass) => {
        if (!OverlayClass?.prototype?._animateZoom) return

        const original = OverlayClass.prototype._animateZoom
        OverlayClass.prototype._animateZoom = function (opt) {
            if (!this._map ||
                !this._map._container ||
                !this._map._latLngToNewLayerPoint ||
                !this._latlng ||
                !opt ||
                typeof opt.zoom === 'undefined' ||
                !opt.center) {
                return this
            }

            try {
                return original.call(this, opt)
            } catch (e) {
                return this
            }
        }
    }

    wrapOverlayAnimateZoom(L.Popup)
    wrapOverlayAnimateZoom(L.Tooltip)

    // Guard vector renderer zoom callbacks for the same race when polylines are
    // removed during an in-flight zoom animation.
    if (L.Renderer?.prototype?._animateZoom) {
        const originalRendererAnimateZoom = L.Renderer.prototype._animateZoom
        L.Renderer.prototype._animateZoom = function (opt) {
            if (!this._map ||
                !this._map._container ||
                !this._map._latLngToNewLayerPoint ||
                !opt ||
                typeof opt.zoom === 'undefined' ||
                !opt.center) {
                return this
            }

            try {
                return originalRendererAnimateZoom.call(this, opt)
            } catch (e) {
                return this
            }
        }
    }
}

/**
 * Guard leaflet.heat zoom callbacks against transient null map/layer state
 * during animated zooms and view resets.
 */
export function fixLeafletHeatLayerAnimation() {
    const PATCH_VERSION = 1
    if (L.__geoPulseHeatLayerGuardsPatchVersion === PATCH_VERSION) {
        return
    }

    const HeatLayer = L.HeatLayer
    if (!HeatLayer?.prototype) {
        return
    }

    L.__geoPulseHeatLayerGuardsPatchVersion = PATCH_VERSION

    if (HeatLayer.prototype._animateZoom) {
        const originalAnimateZoom = HeatLayer.prototype._animateZoom
        HeatLayer.prototype._animateZoom = function (e) {
            if (!this ||
                !this._map ||
                !this._heat ||
                typeof this._map.getZoomScale !== 'function' ||
                !e ||
                typeof e.zoom === 'undefined' ||
                !e.center) {
                return this
            }

            try {
                return originalAnimateZoom.call(this, e)
            } catch (err) {
                return this
            }
        }
    }

    if (HeatLayer.prototype._reset) {
        const originalReset = HeatLayer.prototype._reset
        HeatLayer.prototype._reset = function () {
            if (!this ||
                !this._map ||
                !this._heat ||
                typeof this._map.containerPointToLayerPoint !== 'function') {
                return this
            }

            try {
                return originalReset.call(this)
            } catch (err) {
                return this
            }
        }
    }
}

// Color scheme for different marker types
const MARKER_COLORS = {
    STAY: '#607D8B',           // Blue Grey - for stay points
    PATH: '#4A90E2',           // Light Blue - for path lines
    TRANSIT: '#003366',        // Dark Blue - for transit points
    DATA_GAP: '#D97706',       // Amber - for inferred/unknown timeline gaps
    HIGHLIGHT_START: '#2ECC71', // Green - for trip start points
    HIGHLIGHT_END: '#D84315',   // Red Orange - for trip end points
    FRIEND: '#FF9800',         // Orange - for friend locations
    CURRENT: '#00BCD4',        // Cyan - for current/last location
    FAVORITE: '#E91E63'        // Pink - for favorite locations
}

// Size configurations for different marker types
const MARKER_SIZES = {
    SMALL: {
        SIZE: 16,
        ANCHOR: 8,
        BORDER: 2
    },
    STANDARD: {
        SIZE: 24,
        ANCHOR: 12,
        BORDER: 2
    },
    LARGE: {
        SIZE: 32,
        ANCHOR: 16,
        BORDER: 3
    },
    HIGHLIGHT: {
        SIZE: 40,
        ANCHOR: 20,
        BORDER: 3
    }
}

const DESKTOP_TIMELINE_BREAKPOINT = 1024
const DESKTOP_TIMELINE_MARKER_SIZE = {
    ...MARKER_SIZES.STANDARD,
    SIZE: 28,
    ANCHOR: 14
}
const DESKTOP_TIMELINE_HIGHLIGHT_MARKER_SIZE = {
    ...MARKER_SIZES.HIGHLIGHT,
    SIZE: 44,
    ANCHOR: 22
}

const TIMELINE_MARKER_DIMMED_STYLE = {
    opacity: '0.28',
    filter: 'grayscale(0.35) saturate(0.7)'
}

/**
 * Create a custom div icon with icon and styling
 * @param {Object} config - Configuration object
 * @param {string} config.color - Background color
 * @param {string} config.icon - FontAwesome icon class or emoji
 * @param {Object} config.size - Size configuration from MARKER_SIZES
 * @param {string} config.className - Additional CSS class
 * @param {Object} config.customStyle - Additional inline styles
 * @param {string} config.shape - Shape type: 'circle', 'pin', 'square'
 * @returns {L.DivIcon} - Configured div icon
 */
function createCustomDivIcon({
                                        color,
                                        icon = '',
                                        size = MARKER_SIZES.STANDARD,
                                        className = 'custom-marker',
                                        customStyle = {},
                                        shape = 'circle'
                                    }) {
    const totalSize = size.SIZE + (size.BORDER * 2)

    let baseStyle = {
        backgroundColor: color,
        width: `${size.SIZE}px`,
        height: `${size.SIZE}px`,
        border: `${size.BORDER}px solid white`,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        position: 'relative',
        ...customStyle
    }

    // Apply shape-specific styles
    switch (shape) {
        case 'pin':
            baseStyle = {
                ...baseStyle,
                borderRadius: '50% 50% 50% 0',
                transform: 'rotate(-45deg)',
                transformOrigin: 'center center'
            }
            break
        case 'square':
            baseStyle = {
                ...baseStyle,
                borderRadius: '15%'
            }
            break
        case 'circle':
        default:
            baseStyle = {
                ...baseStyle,
                borderRadius: '50%'
            }
            break
    }

    const styleString = Object.entries(baseStyle)
        .map(([key, value]) => `${key.replace(/([A-Z])/g, '-$1').toLowerCase()}: ${value}`)
        .join('; ')

    // Create icon content
    let iconContent = ''
    if (icon) {
        const iconSize = Math.floor(size.SIZE * 0.7) // Increased from 0.5 to 0.7
        const isCssIconClass = icon.startsWith('pi ') ||
            icon.startsWith('fas ') ||
            icon.startsWith('far ') ||
            icon.startsWith('fal ') ||
            icon.startsWith('fab ') ||
            icon.startsWith('fa-')

        if (isCssIconClass) {
            // CSS class icon (PrimeIcons/FontAwesome)
            iconContent = `<i class="${icon}" style="color: white; font-size: ${iconSize}px; font-weight: bold; ${shape === 'pin' ? 'transform: rotate(45deg);' : ''}"></i>`
        } else {
            // Emoji or text - make emojis even bigger
            const emojiSize = Math.floor(size.SIZE * 0.8)
            iconContent = `<span style="font-size: ${emojiSize}px; line-height: 1; ${shape === 'pin' ? 'transform: rotate(45deg);' : ''}">${icon}</span>`
        }
    }

    // Add shadow for pin shape
    const shadowStyle = shape === 'pin' ?
        'box-shadow: 2px 2px 4px rgba(0,0,0,0.3); filter: drop-shadow(2px 2px 4px rgba(0,0,0,0.2));' :
        'box-shadow: 0 2px 4px rgba(0,0,0,0.3);'

    return L.divIcon({
        className: `${className} marker-${shape}`,
        html: `<div style="${styleString}; ${shadowStyle}">${iconContent}</div>`,
        iconSize: [totalSize, totalSize],
        iconAnchor: getIconAnchor(shape, totalSize, size),
        popupAnchor: getPopupAnchor(shape, totalSize),
        // Add these properties for better positioning stability
        tooltipAnchor: getPopupAnchor(shape, totalSize),
        shadowSize: [0, 0], // Disable shadow to prevent positioning issues
        shadowAnchor: [0, 0]
    })
}

/**
 * Calculate the correct icon anchor based on shape
 * @param {string} shape - Shape type
 * @param {number} totalSize - Total size including borders
 * @param {Object} size - Size configuration
 * @returns {Array} - [x, y] anchor coordinates
 */
function getIconAnchor(shape, totalSize, size) {
    const centerX = totalSize / 2
    const centerY = totalSize / 2

    switch (shape) {
        case 'pin':
            // Pin shape should anchor at the bottom center point
            // Account for the rotation by adjusting the anchor
            return [centerX, totalSize * 0.85] // Slightly above bottom for better positioning
        case 'circle':
        case 'square':
        default:
            // Circle and square anchor at exact center
            return [centerX, centerY]
    }
}

/**
 * Calculate the correct popup anchor based on shape
 * @param {string} shape - Shape type
 * @param {number} totalSize - Total size including borders
 * @returns {Array} - [x, y] popup anchor coordinates
 */
function getPopupAnchor(shape, totalSize) {
    switch (shape) {
        case 'pin':
            // Popup appears above the pin tip
            return [0, -totalSize * 0.8]
        case 'circle':
        case 'square':
        default:
            // Popup appears above the center
            return [0, -totalSize * 0.6]
    }
}


/**
 * Create marker for highlighted trip start point
 * @param {number} latitude - Latitude coordinate
 * @param {number} longitude - Longitude coordinate
 * @param {boolean} instant - Skip animation for instant appearance
 * @returns {L.Marker} - Configured marker
 */
export function createHighlightedPathStartMarker(latitude, longitude, instant = false, styleOverrides = {}) {
    return L.marker([latitude, longitude], {
        icon: createCustomDivIcon({
            //color: MARKER_COLORS.HIGHLIGHT_START,
            icon: 'fas fa-play', // Play icon for start
            size: MARKER_SIZES.HIGHLIGHT,
            className: `custom-marker highlight-start-marker${instant ? ' instant' : ''}`,
            shape: 'circle',
            customStyle: {
                background: `linear-gradient(135deg, ${MARKER_COLORS.HIGHLIGHT_START}, #27AE60)`,
                ...styleOverrides
            }
        }),
        zoomAnimation: false, // Disable zoom animation to prevent marker jumping with clusters
        markerZoomAnimation: false
    })
}

/**
 * Create marker for highlighted trip end point
 * @param {number} latitude - Latitude coordinate
 * @param {number} longitude - Longitude coordinate
 * @param {boolean} instant - Skip animation for instant appearance
 * @returns {L.Marker} - Configured marker
 */
export function createHighlightedPathEndMarker(latitude, longitude, instant = false, styleOverrides = {}) {
    return L.marker([latitude, longitude], {
        icon: createCustomDivIcon({
            color: MARKER_COLORS.HIGHLIGHT_END,
            icon: 'fas fa-stop', // Stop icon for end
            size: MARKER_SIZES.HIGHLIGHT,
            className: `custom-marker highlight-end-marker${instant ? ' instant' : ''}`,
            shape: 'square',
            customStyle: {
                background: `linear-gradient(135deg, ${MARKER_COLORS.HIGHLIGHT_END}, #C0392B)`,
                ...styleOverrides
            }
        }),
        zoomAnimation: false, // Disable zoom animation to prevent marker jumping with clusters
        markerZoomAnimation: false
    })
}

/**
 * Creates a custom div icon using an avatar image
 * @param {Object} options - Options for the avatar icon
 * @param {string} options.avatarPath - Path to the avatar image
 * @param {number} options.size - Size of the marker
 * @returns {L.DivIcon} A Leaflet DivIcon configured with the avatar
 */
export function createAvatarDivIcon({avatarPath, size}) {
    // Create the HTML for the avatar icon with all styles inlined
    const html = `
    <div style="
      width: ${size}px; 
      height: ${size}px;
      border-radius: 50%;
      border: 3px solid white;
      overflow: hidden;
      background-color: #ffffff;
      box-shadow: 0 3px 14px rgba(0,0,0,0.4);
    ">
      <img 
        src="${avatarPath}" 
        alt="User avatar" 
        style="
          width: 100%;
          height: 100%;
          object-fit: cover;
          border-radius: 50%;
          display: block;
        "
        onerror="this.onerror=null; this.src='/avatars/avatar1.png';"
      />
    </div>
  `;

    // Create a div icon with explicit settings
    return L.divIcon({
        html,
        className: 'avatar-marker', // Minimal class name
        iconSize: [size, size],
        iconAnchor: [size / 2, size / 2] // Center of the icon (change if needed)
    });
}

/**
 * Create icon for timeline markers
 * @param {Object|null} item - Timeline item (stay/trip/dataGap)
 * @returns {L.DivIcon} - Configured timeline icon
 */
export function createTimelineIcon(item = null, { dimmed = false } = {}) {
    const markerVisual = resolveTimelineMarkerVisual(item)

    return createCustomDivIcon({
        color: markerVisual.color,
        icon: markerVisual.icon,
        size: getResponsiveTimelineMarkerSize(false),
        className: 'custom-marker timeline-marker',
        shape: 'circle',
        customStyle: dimmed ? TIMELINE_MARKER_DIMMED_STYLE : {}
    })
}

/**
 * Create highlighted icon for timeline markers
 * @param {Object|null} item - Timeline item (stay/trip/dataGap)
 * @returns {L.DivIcon} - Configured highlighted timeline icon
 */
export function createHighlightedTimelineIcon(item = null) {
    const markerVisual = resolveTimelineMarkerVisual(item)

    return createCustomDivIcon({
        color: markerVisual.color,
        icon: markerVisual.icon,
        size: getResponsiveTimelineMarkerSize(true),
        className: 'custom-marker timeline-marker highlighted',
        shape: 'circle',
        customStyle: {
            boxShadow: `0 0 0 4px ${markerVisual.highlightRingColor}, 0 4px 10px rgba(15, 23, 42, 0.25)`
        }
    })
}

/**
 * Resolve the timeline marker visual based on timeline item type.
 * Mirrors timeline list semantics: stays use location pin, trips use transport.
 * @param {Object|null} item - Timeline item
 * @returns {{color: string, icon: string, highlightRingColor: string}}
 */
function resolveTimelineMarkerVisual(item) {
    const itemType = item?.type
    if (itemType === 'trip') {
        return {
            color: '#10B981',
            icon: getTripMovementIconClass(item?.movementType),
            highlightRingColor: 'rgba(52, 211, 153, 0.55)'
        }
    }

    if (itemType === 'dataGap') {
        return {
            color: '#F59E0B',
            icon: 'pi pi-question',
            highlightRingColor: 'rgba(251, 191, 36, 0.55)'
        }
    }

    return {
        color: '#1A56DB',
        icon: 'pi pi-map-marker',
        highlightRingColor: 'rgba(96, 165, 250, 0.55)'
    }
}

function getResponsiveTimelineMarkerSize(highlighted = false) {
    const isDesktop = typeof window !== 'undefined' && window.innerWidth >= DESKTOP_TIMELINE_BREAKPOINT
    if (!isDesktop) {
        return highlighted ? MARKER_SIZES.HIGHLIGHT : MARKER_SIZES.STANDARD
    }

    return highlighted ? DESKTOP_TIMELINE_HIGHLIGHT_MARKER_SIZE : DESKTOP_TIMELINE_MARKER_SIZE
}

/**
 * Create icon for friend markers
 * @returns {L.DivIcon} - Configured friend icon
 */
export function createFriendIcon({
  color = MARKER_COLORS.FRIEND,
  gradientEnd = '#F39C12',
  customStyle = {},
  icon = 'fas fa-user',
  ...rest
} = {}) {
    return createCustomDivIcon({
        color,
        icon,
        size: MARKER_SIZES.LARGE,
        className: rest.className || 'custom-marker friend-marker',
        shape: 'circle',
        customStyle: {
            background: `linear-gradient(135deg, ${color}, ${gradientEnd})`,
            border: '3px solid white',
            ...(customStyle || {})
        },
        ...rest
    })
}

/**
 * Create icon for favorite location markers
 * @returns {L.DivIcon} - Configured favorite icon
 */
export function createFavoriteIcon() {
    return createCustomDivIcon({
        color: MARKER_COLORS.FAVORITE,
        icon: 'fas fa-star', // Star icon for favorites
        size: MARKER_SIZES.LARGE,
        className: 'custom-marker favorite-marker',
        shape: 'pin',
        customStyle: {
            background: `linear-gradient(135deg, ${MARKER_COLORS.FAVORITE}, #AD1457)`,
            border: '3px solid white',
            boxShadow: '0 0 15px rgba(233, 30, 99, 0.6)'
        }
    })
}

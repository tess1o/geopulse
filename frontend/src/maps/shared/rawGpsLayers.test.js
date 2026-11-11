import {mount} from '@vue/test-utils'
import {nextTick} from 'vue'
import {createPinia, setActivePinia} from 'pinia'
import {beforeEach, describe, expect, it, vi} from 'vitest'

const mocks = vi.hoisted(() => ({raster: [], vector: [], popups: [], collection: null, visibleCollection: null, clusters: []}))
vi.mock('@/composables/useTimezone', () => ({
    useTimezone: () => ({
        formatDateDisplay: value => value.slice(0, 10), formatTime: value => value.slice(11, 19)
    })
}))
vi.mock('leaflet.markercluster', () => ({}))
vi.mock('leaflet', () => ({
    default: {
        divIcon: options => options, point: (x, y) => [x, y],
        marker: (coordinates, options) => {
            const element = document.createElement('div')
            // RasterMapHost's DivIcon patch omits icon classes.
            element.className = ''
            const marker = {
                coordinates, options, handlers: {}, element,
                on(event, callback) {
                    this.handlers[event] = callback;
                    return this
                },
                addTo() {
                    return this
                }, getElement: () => element,
                setLatLng(coordinates) { this.coordinates = coordinates; return this },
                setZIndexOffset: vi.fn(), remove: vi.fn()
            }
            mocks.raster.push(marker)
            return marker
        },
        popup: () => {
            const popup = {
                on(event, callback) {
                    this[`${event}Event`] = callback;
                    return this
                },
                setLatLng() {
                    return this
                },
                setContent(element) {
                    this.element = element;
                    return this
                },
                openOn() {
                    return this
                }, update: vi.fn(),
                remove() {
                    this.removeEvent?.()
                }
            }
            mocks.popups.push(popup)
            return popup
        },
        markerClusterGroup: () => {
            const group = {addLayer: vi.fn(), removeLayer: vi.fn(), clearLayers: vi.fn()}
            mocks.clusters.push(group)
            return group
        }
    }
}))
vi.mock('@/components/maps/layers/BaseLayer.vue', () => ({
    default: {
        name: 'BaseLayer', template: '<div />', emits: ['layer-ready'],
        mounted() {
            this.$emit('layer-ready')
        }
    }
}))
vi.mock('maplibre-gl', () => ({
    Marker: class {
        constructor({element}) {
            this.element = element;
            this.remove = vi.fn();
            mocks.vector.push(this)
        }

        setLngLat(coordinates) {
            this.coordinates = coordinates;
            return this
        }

        addTo() {
            return this
        }

        getElement() {
            return this.element
        }
    },
    Popup: class {
        constructor() {
            mocks.popups.push(this)
        }

        setLngLat() {
            return this
        }

        setDOMContent(element) {
            this.element = element;
            return this
        }

        addTo() {
            return this
        }

        on(event, callback) {
            this[event] = callback;
            return this
        }

        remove() {
            this.close?.()
        }

        isOpen() {
            return true
        }
    }
}))
vi.mock('@/maps/vector/utils/maplibreLayerUtils', () => ({
    createFeatureCollection: features => ({features}),
    ensureClusterSource: (_map, _id, collection) => {
        mocks.collection = collection
    },
    ensureLayer: vi.fn(), getMapLibreSource: () => ({setData: collection => { mocks.visibleCollection = collection }}), hasMapLibreLayer: () => true,
    isMapLibreMap: map => Boolean(map), nextLayerToken: prefix => prefix,
    removeLayers: vi.fn(), removeSources: vi.fn(), setLayerVisibility: vi.fn()
}))

import RasterLayer from '@/maps/raster/layers/RasterRawGpsPointsLayer.vue'
import VectorLayer from '@/maps/vector/layers/VectorRawGpsPointsLayer.vue'

const points = [
    {id: 1, latitude: 1, longitude: 2, timestamp: '2026-11-06T10:00:01Z'},
    {id: 2, latitude: 1.00005, longitude: 2.00004, timestamp: '2026-11-06T10:00:59Z'},
    {id: 4, latitude: 1, longitude: 2, timestamp: '2026-11-06T10:01:00Z'},
    {id: 3, latitude: 1.01, longitude: 2.01, timestamp: '2026-11-06T10:01:30Z'}
]
const createMap = () => ({
    getZoom: vi.fn(() => 18), getMaxZoom: () => 22, setView: vi.fn(), easeTo: vi.fn(),
    on: vi.fn(), off: vi.fn(), addLayer: vi.fn(), removeLayer: vi.fn(), hasLayer: () => true,
    project: () => ({x: 300, y: 100}),
    getCanvas: () => ({style: {}}), getContainer: () => ({clientWidth: 800, clientHeight: 500})
})

beforeEach(() => {
    setActivePinia(createPinia())
    mocks.raster = [];
    mocks.vector = [];
    mocks.popups = [];
    mocks.collection = null
    mocks.visibleCollection = null
    mocks.clusters = []
})

for (const engine of ['raster', 'vector']) {
    describe(`${engine} raw GPS inspection`, () => {
        const setup = async (extraProps = {}) => {
            const map = createMap(), select = vi.fn()
            const wrapper = mount(engine === 'raster' ? RasterLayer : VectorLayer, {
                props: {
                    map,
                    points,
                    selectPoint: select,
                    ...extraProps
                }
            })
            await nextTick()
            const openGroup = index => {
                if (engine === 'raster') mocks.raster.filter(marker => marker.options.rawGpsGroup)[index].handlers.click()
                else {
                    const handler = map.on.mock.calls.find(([event, layer]) => event === 'click' && layer === 'gp-raw-gps-stacks')[2]
                    handler({preventDefault: vi.fn(), features: [mocks.collection.features[index]]})
                }
            }
            const samples = () => engine === 'raster'
                ? mocks.raster.filter(marker => !marker.options.rawGpsGroup)
                : mocks.vector
            const clickSample = marker => engine === 'raster' ? marker.handlers.click() : marker.element.click()
            return {wrapper, map, select, openGroup, samples, clickSample}
        }

        it('keeps a selected cluster in place and splits it into clickable samples when zoomed in', async () => {
            const {wrapper, map, select, openGroup, samples, clickSample} = await setup()
            const liveSamples = () => samples().filter(marker => !marker.remove.mock.calls.length)
            openGroup(0)
            expect(liveSamples()).toHaveLength(1)
            const originalPosition = [...liveSamples()[0].coordinates]
            const popup = mocks.popups.at(-1)
            popup.element.querySelectorAll('.raw-gps-stack-row')[1].click()
            await nextTick()
            expect(liveSamples()).toHaveLength(1)
            expect(liveSamples()[0].coordinates).toEqual(originalPosition)
            expect(liveSamples()[0].element.classList.contains('is-selected')).toBe(true)
            expect(select).not.toHaveBeenCalled()
            clickSample(liveSamples()[0])
            expect(engine === 'raster' ? map.setView : map.easeTo).toHaveBeenCalled()
            map.getZoom.mockReturnValue(22)
            map.on.mock.calls.find(([event]) => event === 'zoomend')[1]()
            expect(liveSamples()).toHaveLength(2)
            const selected = liveSamples().find(marker => marker.element.classList.contains('is-selected'))
            expect(selected.coordinates).toEqual(engine === 'raster' ? [1.00005, 2.00004] : [2.00004, 1.00005])
            clickSample(liveSamples().find(marker => !marker.element.classList.contains('is-selected')))
            // Identical coordinates remain grouped; selecting either timestamp keeps its cluster highlighted.
            popup.element.querySelectorAll('.raw-gps-stack-row')[2].click()
            await nextTick()
            const overlap = liveSamples().find(marker => marker.element.classList.contains('is-selected'))
            expect(overlap.coordinates).toEqual(engine === 'raster' ? [1, 2] : [2, 1])
            clickSample(liveSamples().find(marker => !marker.element.classList.contains('is-selected')))
            await nextTick()
            expect(popup.element.querySelector('[data-point-index="1"]').classList.contains('is-selected')).toBe(true)
            expect(select).not.toHaveBeenCalled()
            Array.from(popup.element.querySelectorAll('button')).find(button => button.textContent === 'Select this time').click()
            expect(select).toHaveBeenCalledWith(expect.objectContaining({timestamp: '2026-11-06T10:00:59Z'}))
            popup.remove()
            clickSample(liveSamples().find(marker => marker.element.classList.contains('is-selected')))
            await nextTick()
            expect(mocks.popups.at(-1).element.querySelector('[data-point-index="1"]').classList.contains('is-selected')).toBe(true)
            wrapper.unmount()
            expect(samples().every(marker => marker.remove.mock.calls.length > 0)).toBe(true)
            expect(map.off).toHaveBeenCalledWith('zoomend', expect.any(Function))
        })

        it('keeps editor details in an external panel without a geographic popup', async () => {
            const inspectorContainer = document.createElement('div')
            const {wrapper, select, openGroup, samples, clickSample} = await setup({inspectorContainer})
            openGroup(0)
            await nextTick()
            expect(mocks.popups).toHaveLength(0)
            const card = inspectorContainer.firstElementChild
            expect(card.textContent).toContain('3 GPS points here')
            card.querySelector('[data-point-index="1"]').click()
            await nextTick()
            expect(inspectorContainer.firstElementChild).toBe(card)
            expect(card.querySelector('[data-point-index="1"]').classList.contains('is-selected')).toBe(true)
            expect(select).not.toHaveBeenCalled()
            Array.from(card.querySelectorAll('button')).find(button => button.textContent === 'Select this time').click()
            expect(select).toHaveBeenCalledWith(expect.objectContaining({timestamp: '2026-11-06T10:00:59Z'}))
            openGroup(1)
            await nextTick()
            expect(inspectorContainer.children).toHaveLength(1)
            expect(card.isConnected).toBe(false)
            expect(inspectorContainer.textContent).toContain('10:01:30')
            await wrapper.setProps({visible: false})
            expect(inspectorContainer.children).toHaveLength(0)
            await wrapper.setProps({visible: true})
            openGroup(0)
            wrapper.unmount()
            expect(inspectorContainer.children).toHaveLength(0)
        })

        if (engine === 'raster') it('does not leave duplicate GPS groups when the base layer reports ready twice', async () => {
            const {wrapper} = await setup()
            wrapper.findComponent({name: 'BaseLayer'}).vm.$emit('layer-ready')
            await nextTick()
            expect(mocks.clusters).toHaveLength(1)
            wrapper.unmount()
        })

        if (engine === 'raster') it('does not restore markers into a map being destroyed on Cancel', async () => {
            const {wrapper, openGroup} = await setup()
            openGroup(0)
            mocks.clusters[0].addLayer.mockClear()
            wrapper.unmount()
            expect(mocks.clusters[0].addLayer).not.toHaveBeenCalled()
        })

        it('replaces only the inspected overview badge and restores it when inspection ends', async () => {
            const {wrapper, openGroup, samples} = await setup({inspectorContainer: document.createElement('div')})
            openGroup(0)
            expect(samples()).toHaveLength(1)
            if (engine === 'raster') expect(mocks.clusters[0].removeLayer).toHaveBeenCalledWith(mocks.raster[0])
            else {
                expect(mocks.visibleCollection.features).toHaveLength(1)
                expect(mocks.visibleCollection.features[0].properties.count).toBe(1)
            }
            await wrapper.setProps({visible: false})
            if (engine === 'raster') expect(mocks.clusters[0].addLayer).toHaveBeenLastCalledWith(mocks.raster[0])
            else expect(mocks.visibleCollection.features).toHaveLength(2)
            wrapper.unmount()
        })

        it('cleans up selected markers on switching groups, hiding, changing data, and unmounting', async () => {
            const {wrapper, openGroup, samples} = await setup()
            openGroup(0)
            const first = [...samples()]
            openGroup(1)
            expect(first.every(marker => marker.remove.mock.calls.length > 0)).toBe(true)
            const second = samples().at(-1)
            await wrapper.setProps({visible: false})
            expect(second.remove).toHaveBeenCalled()
            await wrapper.setProps({visible: true})
            openGroup(0)
            const third = samples().at(-1)
            await wrapper.setProps({points: [...points, {...points[3], id: 5}]})
            expect(third.remove).toHaveBeenCalled()
            openGroup(0)
            const last = samples().at(-1)
            wrapper.unmount()
            expect(last.remove).toHaveBeenCalled()
            expect(mocks.popups.every(popup => popup.element.textContent === '')).toBe(true)
        })
    })
}
